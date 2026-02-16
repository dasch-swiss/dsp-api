/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo.service

import org.apache.jena.rdf.model.Resource
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri
import zio.*

import java.time.Instant
import java.util.UUID
import scala.language.implicitConversions

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.twirl.SparqlTemplateLinkUpdate
import org.knora.webapi.messages.v2.responder.valuemessages.ValueContentV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.jena.JenaConversions.given_Conversion_String_Property
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase as KB
import org.knora.webapi.slice.resources.repo.service.value.queries.InsertValueQueryBuilder
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

sealed trait ValueModel {
  def iri: ValueIri
  def valueClass: InternalIri
  def lastModificationDate: Option[Instant]
}
final case class ActiveValue(
  iri: ValueIri,
  valueClass: InternalIri,
  previousValue: Option[ValueIri],
  lastModificationDate: Option[Instant],
) extends ValueModel
final case class DeletedValue(
  iri: ValueIri,
  valueClass: InternalIri,
  previousValue: Option[ValueIri],
  lastModificationDate: Option[Instant],
) extends ValueModel

final case class ValueRepo(triplestore: TriplestoreService)(implicit val sf: StringFormatter)
    extends QueryBuilderHelper {
  import org.knora.webapi.messages.IriConversions.ConvertibleIri

  def findActiveById(iri: ValueIri): Task[Option[ActiveValue]] =
    findById(iri).map(_.collect { case v: ActiveValue => v })

  def findDeletedById(iri: ValueIri): Task[Option[DeletedValue]] =
    findById(iri).map(_.collect { case v: DeletedValue => v })

  def findByIds(iris: Seq[ValueIri]) = ZIO.foreach(iris)(findById)

  def findById(iri: ValueIri): Task[Option[ValueModel]] =
    val id                                                      = Rdf.iri(iri.toString)
    val (clazz, isDeleted, previousValue, lastModificationDate) =
      (variable("valueClass"), variable("isDeleted"), variable("previousValue"), variable("lastModificationDate"))

    val graphP = id
      .isA(clazz)
      .andHas(KB.lastModificationDate, lastModificationDate)
      .andHas(KB.isDeleted, isDeleted)
      .andHas(KB.previousValue, previousValue)

    val whereP = id
      .isA(clazz)
      .and(clazz.has(RDFS.SUBCLASSOF, KB.Value))
      .and(id.has(KB.previousValue, previousValue).optional())
      .and(id.has(KB.isDeleted, isDeleted).optional())
      .and(id.has(KB.lastModificationDate, lastModificationDate).optional())

    val query = Queries.CONSTRUCT(graphP).where(whereP)
    triplestore
      .queryRdfModel(Construct(query))
      .flatMap(_.getResource(iri.toString).map(_.map(_.res)))
      .flatMap(mapResult)

  private def mapResult(maybe: Option[Resource]): Task[Option[ValueModel]] =
    maybe match
      case None           => ZIO.none
      case Some(resource) =>
        ZIO
          .fromEither(
            for {
              valueIri      <- resource.uri.fold(Left("Value IRI not found"))(s => ValueIri.from(s.toSmartIri))
              lastModified  <- resource.objectInstantOption(KnoraBase.LastModificationDate)
              isDeleted     <- resource.objectBooleanOption(KnoraBase.IsDeleted).map(_.getOrElse(false))
              valueClassIri <- resource.rdfType.fold(Left("Value class IRI not found"))(s => Right(InternalIri(s)))
              previousValue <- resource.objectUriOption(KnoraBase.PreviousValue, s => ValueIri.from(s.toSmartIri))
            } yield
              if isDeleted then Some(DeletedValue(valueIri, valueClassIri, previousValue, lastModified))
              else Some(ActiveValue(valueIri, valueClassIri, previousValue, lastModified)),
          )
          .mapError(s => InconsistentRepositoryDataException(s))

  def findAllPrevious(valueIri: ValueIri): Task[Seq[ValueIri]] = {
    def loop(valueIri: ValueIri, acc: Seq[ValueIri]): Task[Seq[ValueIri]] =
      findPreviousValue(valueIri).flatMap {
        case Some(previousValueIri) => loop(previousValueIri, acc :+ previousValueIri)
        case None                   => ZIO.succeed(acc)
      }
    loop(valueIri, Seq.empty)
  }

  def findPreviousValue(valueIri: ValueIri): Task[Option[ValueIri]] = {
    val previous = variable("previous")
    val where    = iri(valueIri.toString).has(KB.previousValue, previous)
    val query    = Queries.SELECT(previous).where(where)
    triplestore
      .query(Select(query))
      .map(_.getFirstRow)
      .map(_.flatMap(row => row.rowMap.get("previous").map(_.toSmartIri).map(ValueIri.unsafeFrom)))
  }

  def eraseValue(project: KnoraProject)(valueIri: ValueIri): Task[Unit] = {
    val value         = iri(valueIri.toString)
    val (p, o)        = (variable("p"), variable("o"))
    val (s, oo)       = (variable("s"), variable("oo"))
    val delete        = value.has(p, o)
    val deleteReverse = s.has(oo, value)

    val (soLink, soP, soO) = (variable("standoffLink"), variable("standoffProp"), variable("standoffObj"))
    val standoffLinked     = value.has(KB.valueHasStandoff, soLink)
    val standoffValues     = soLink.has(soP, soO)

    val projectDataGraph = Rdf.iri(ProjectService.projectDataNamedGraphV2(project).value)
    val queryStandoff    = Queries
      .DELETE(standoffValues)
      .`with`(projectDataGraph)
      .where(delete, deleteReverse, standoffLinked, standoffValues)
    val query = Queries
      .DELETE(delete, deleteReverse)
      .`with`(projectDataGraph)
      .where(delete, deleteReverse)

    triplestore.query(Update(queryStandoff)) *> triplestore.query(Update(query))
  }

  /* Deletes the subject/predicate/object triple pointed to by a LinkValue. */
  def eraseValueDirectLink(project: KnoraProject)(valueIri: ValueIri): Task[Unit] = {
    val value            = iri(valueIri.toString)
    val (s, p, o)        = spo
    val delete           = s.has(p, o)
    val projectDataGraph = Rdf.iri(ProjectService.projectDataNamedGraphV2(project).value)
    val query            = Queries
      .DELETE(delete)
      .`with`(projectDataGraph)
      .where(
        value.has(iri(OntologyConstants.Rdf.Subject), s),
        value.has(iri(OntologyConstants.Rdf.Predicate), p),
        value.has(iri(OntologyConstants.Rdf.Object), o),
      )
    triplestore.query(Update(query))
  }

  def updateValuePermissions(
    projectDataGraph: InternalIri,
    resourceIri: InternalIri,
    valueIri: ValueIri,
    newPermissions: String,
    currentTime: Instant,
  ): Task[Unit] = {
    val resource = iri(resourceIri.value)
    val value    = iri(valueIri.toString)

    val (resourceLastModDate, currentValuePerms) =
      (variable("resourceLastModificationDate"), variable("currentValuePermissions"))

    val query = Queries
      .MODIFY()
      .`with`(Rdf.iri(projectDataGraph.value))
      .delete(
        resource.has(KB.lastModificationDate, resourceLastModDate),
        value.has(KB.hasPermissions, currentValuePerms),
      )
      .insert(
        resource.has(KB.lastModificationDate, Rdf.literalOfType(currentTime.toString, XSD.DATETIME)),
        value.has(KB.hasPermissions, Rdf.literalOf(newPermissions)),
      )
      .where(
        value
          .has(KB.hasPermissions, currentValuePerms)
          .and(resource.has(KB.lastModificationDate, resourceLastModDate).optional()),
      )

    triplestore.query(Update(query))
  }

  def reorderValues(
    projectDataGraph: InternalIri,
    resourceIri: InternalIri,
    orderedValueIris: List[ValueIri],
    currentTime: Instant,
  ): Task[Unit] = {
    val valuesClause = orderedValueIris.zipWithIndex.map { case (valueIri, idx) =>
      s"    (<${valueIri.toString}> $idx)"
    }
      .mkString("\n")

    val valueHasOrder        = KnoraBase.ValueHasOrder
    val lastModificationDate = KnoraBase.LastModificationDate

    val query = s"""
                   |WITH <${projectDataGraph.value}>
                   |DELETE {
                   |  ?item <$valueHasOrder> ?oldOrder .
                   |  <${resourceIri.value}> <$lastModificationDate> ?resourceLastModDate .
                   |}
                   |INSERT {
                   |  ?item <$valueHasOrder> ?newOrder .
                   |  <${resourceIri.value}> <$lastModificationDate> "${currentTime.toString}"^^<${XSD.DATETIME}> .
                   |}
                   |WHERE {
                   |  VALUES (?item ?newOrder) {
                   |$valuesClause
                   |  }
                   |  OPTIONAL { ?item <$valueHasOrder> ?oldOrder }
                   |  OPTIONAL { <${resourceIri.value}> <$lastModificationDate> ?resourceLastModDate }
                   |}
                   |""".stripMargin

    triplestore.query(Update(query))
  }

  def createValue(
    dataNamedGraph: InternalIri,
    resourceIri: InternalIri,
    propertyIri: SmartIri,
    newValueIri: InternalIri,
    newValueUUID: UUID,
    value: ValueContentV2,
    linkUpdates: Seq[SparqlTemplateLinkUpdate],
    valueCreator: InternalIri,
    valuePermissions: String,
    creationDate: Instant,
  ): Task[Unit] =
    triplestore.query(
      InsertValueQueryBuilder.createValueQuery(
        dataNamedGraph,
        resourceIri,
        propertyIri,
        newValueIri,
        Left(newValueUUID),
        value,
        linkUpdates,
        valueCreator,
        valuePermissions,
        creationDate,
      ),
    )

  def updateValue(
    dataNamedGraph: InternalIri,
    resourceIri: InternalIri,
    propertyIri: SmartIri,
    currentValueIri: InternalIri,
    newValueIri: InternalIri,
    value: ValueContentV2,
    valueCreator: InternalIri,
    valuePermissions: String,
    linkUpdates: Seq[SparqlTemplateLinkUpdate],
    creationDate: Instant,
  ): Task[Unit] =
    triplestore
      .query(
        InsertValueQueryBuilder.createValueQuery(
          dataNamedGraph,
          resourceIri,
          propertyIri,
          newValueIri,
          Right(currentValueIri),
          value,
          linkUpdates,
          valueCreator,
          valuePermissions,
          creationDate,
        ),
      )
}

object ValueRepo {
  val layer = ZLayer.derive[ValueRepo]
}
