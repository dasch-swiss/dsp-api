/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import zio._
import zio.macros.accessible

import org.knora.webapi.IRI
import org.knora.webapi.InternalSchema
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.util.search.BindPattern
import org.knora.webapi.messages.util.search.Entity
import org.knora.webapi.messages.util.search.FilterNotExistsPattern
import org.knora.webapi.messages.util.search.IriRef
import org.knora.webapi.messages.util.search.LuceneQueryPattern
import org.knora.webapi.messages.util.search.MinusPattern
import org.knora.webapi.messages.util.search.OptionalPattern
import org.knora.webapi.messages.util.search.QueryPattern
import org.knora.webapi.messages.util.search.StatementPattern
import org.knora.webapi.messages.util.search.UnionPattern
import org.knora.webapi.messages.util.search.ValuesPattern
import org.knora.webapi.messages.util.search.WhereClause
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

@accessible
trait InferenceOptimizationService {

  /**
   * Extracts all ontologies that are relevant to a gravsearch query, in order to allow optimized cache-based inference simulation.
   *
   * @param whereClause  the WHERE-clause of a gravsearch query.
   * @return a set of ontology IRIs relevant to the query, or or a set of all existing ontologies,
   *         if no meaningful result could be produced.
   */
  def getOntologiesRelevantForInference(whereClause: WhereClause): Task[Set[IRI]]
}

final case class InferenceOptimizationServiceLive(
  private val messageRelay: MessageRelay,
  private val ontologyCache: OntologyCache,
  implicit private val stringFormatter: StringFormatter
) extends InferenceOptimizationService {

  /**
   * Helper method that analyzed an RDF Entity and returns a sequence of Ontology IRIs that are being referenced by the entity.
   * If an IRI appears that can not be resolved by the ontology cache, it will check if the IRI points to project data;
   * if so, all ontologies defined by the project to which the data belongs, will be included in the results.
   *
   * @param entity       an RDF entity.
   * @param map          a map of entity IRIs to the IRIs of the ontology where they are defined.
   * @return a sequence of ontology IRIs which relate to the input RDF entity.
   */
  private def resolveEntity(entity: Entity, map: Map[SmartIri, SmartIri]): Task[Seq[SmartIri]] =
    entity match {
      case IriRef(iri, _) =>
        val internal     = iri.toOntologySchema(InternalSchema)
        val maybeOntoIri = map.get(internal)
        maybeOntoIri match {
          // if the map contains an ontology IRI corresponding to the entity IRI, then this can be returned
          case Some(iri) => ZIO.succeed(Seq(iri))
          case None      =>
            // if the map doesn't contain a corresponding ontology IRI, then the entity IRI points to a resource or value
            // in that case, all ontologies of the project, to which the entity belongs, should be returned.
            resolveEntityByShortcode(internal)
        }
      case _ => ZIO.succeed(Seq.empty)
    }

  /**
   * Attempts to retrieve all ontologies of the project, to which the entity IRI (resource or property IRI) belongs.
   */
  private def resolveEntityByShortcode(entityIri: SmartIri): Task[Seq[SmartIri]] = {
    val shortcode = entityIri.getProjectCode
    shortcode match {
      case None => ZIO.succeed(Seq.empty)
      case Some(value) =>
        for {
          shortcode    <- ProjectIdentifierADM.ShortcodeIdentifier.fromString(value).toZIO
          projectMaybe <- messageRelay.ask[Option[ProjectADM]](ProjectGetADM(shortcode))
          projectOntologies =
            projectMaybe match {
              case None          => Seq.empty
              case Some(project) => project.ontologies.map(stringFormatter.toSmartIri(_))
            }
        } yield projectOntologies
    }
  }

  override def getOntologiesRelevantForInference(
    whereClause: WhereClause
  ): Task[Set[IRI]] = {
    // gets a sequence of [[QueryPattern]] and returns the set of entities that the patterns consist of
    def getEntities(patterns: Seq[QueryPattern]): Seq[Entity] =
      patterns.flatMap { pattern =>
        pattern match {
          case ValuesPattern(_, values)            => values.toSeq
          case BindPattern(_, expression)          => List(expression.asInstanceOf[Entity])
          case UnionPattern(blocks)                => blocks.flatMap(block => getEntities(block))
          case StatementPattern(subj, pred, obj)   => List(subj, pred, obj)
          case LuceneQueryPattern(subj, obj, _, _) => List(subj, obj)
          case FilterNotExistsPattern(patterns)    => getEntities(patterns)
          case MinusPattern(patterns)              => getEntities(patterns)
          case OptionalPattern(patterns)           => getEntities(patterns)
          case _                                   => List.empty
        }
      }

    // get the entities for all patterns in the WHERE clause
    val entities = getEntities(whereClause.patterns)

    for {
      ontoCache <- ontologyCache.getCacheData
      // from the cache, get the map from entity to the ontology where the entity is defined
      entityMap = ontoCache.entityDefinedInOntology
      // resolve all entities from the WHERE clause to the ontology where they are defined
      relevantOntologies   <- ZIO.foreach(entities)(resolveEntity(_, entityMap))
      relevantOntologiesSet = relevantOntologies.flatten.toSet
      res = if (
              relevantOntologiesSet.isEmpty || relevantOntologiesSet == Set(
                stringFormatter.toSmartIri(OntologyConstants.KnoraBase.KnoraBaseOntologyIri)
              )
            ) ontoCache.ontologies.keySet
            else relevantOntologiesSet + stringFormatter.toSmartIri(OntologyConstants.KnoraBase.KnoraBaseOntologyIri)
    } yield res.map(_.toIri)
  }
}

object InferenceOptimizationService {
  val layer: URLayer[MessageRelay with OntologyCache with StringFormatter, InferenceOptimizationServiceLive] =
    ZLayer.fromFunction(InferenceOptimizationServiceLive.apply _)
}
