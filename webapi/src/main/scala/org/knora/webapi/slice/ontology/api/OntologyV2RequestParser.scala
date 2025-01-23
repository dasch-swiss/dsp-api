/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api

import org.apache.jena.rdf.model.*
import org.apache.jena.vocabulary.OWL2 as OWL
import org.apache.jena.vocabulary.RDFS
import zio.*

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

import dsp.constants.SalsahGui
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.store.triplestoremessages.BooleanLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.OntologyLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ChangeOntologyMetadataRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.CreateClassRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.CreateOntologyRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.jena.DatasetOps
import org.knora.webapi.slice.common.jena.DatasetOps.*
import org.knora.webapi.slice.common.jena.JenaConversions.given_Conversion_String_Property
import org.knora.webapi.slice.common.jena.ModelOps
import org.knora.webapi.slice.common.jena.ModelOps.*
import org.knora.webapi.slice.common.jena.ResourceOps.*
import org.knora.webapi.slice.common.jena.StatementOps.*
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

final case class OntologyV2RequestParser(iriConverter: IriConverter) {

  private final case class OntologyMetadata(
    ontologyIri: OntologyIri,
    label: Option[String],
    comment: Option[String],
    lastModificationDate: Instant,
  )

  def changeOntologyMetadataRequestV2(
    jsonLd: String,
    apiRequestId: UUID,
    requestingUser: User,
  ): IO[String, ChangeOntologyMetadataRequestV2] = ZIO.scoped {
    for {
      model <- ModelOps.fromJsonLd(jsonLd)
      meta  <- extractOntologyMetadata(model)
    } yield ChangeOntologyMetadataRequestV2(
      meta.ontologyIri.smartIri,
      meta.label,
      meta.comment,
      meta.lastModificationDate,
      apiRequestId,
      requestingUser,
    )
  }

  private def extractOntologyMetadata(m: Model): ZIO[Scope, String, OntologyMetadata] =
    for {
      r                    <- ZIO.fromEither(m.singleRootResource).orElseFail("No root resource found")
      ontologyIri          <- uriAsOntologyIri(r)
      label                <- ZIO.fromEither(r.objectStringOption(RDFS.label))
      comment              <- ZIO.fromEither(r.objectStringOption(RDFS.comment))
      lastModificationDate <- ZIO.fromEither(r.objectInstant(KA.LastModificationDate))
    } yield OntologyMetadata(ontologyIri, label, comment, lastModificationDate)

  private def uriAsOntologyIri(r: Resource): ZIO[Scope, String, OntologyIri] = ZIO
    .fromOption(r.uri)
    .orElseFail("No IRI found")
    .flatMap(iriConverter.asSmartIri(_).mapError(_.getMessage))
    .flatMap(sIri => ZIO.fromEither(OntologyIri.fromApiV2Complex(sIri)))

  def createOntologyRequestV2(
    jsonLd: String,
    apiRequestId: UUID,
    requestingUser: User,
  ): IO[String, CreateOntologyRequestV2] =
    import ModelOps.*
    ZIO.scoped {
      ModelOps.fromJsonLd(jsonLd).flatMap { m =>
        ZIO.fromEither {
          for {
            r          <- m.singleRootResource
            name       <- r.objectString(KA.OntologyName)
            projectIri <- r.objectUri(KA.AttachedToProject, ProjectIri.from)
            isShared   <- r.objectBoolean(KA.IsShared)
            label      <- r.objectString(RDFS.label)
            comment    <- r.objectStringOption(RDFS.comment)
          } yield CreateOntologyRequestV2(
            name,
            projectIri,
            isShared,
            label,
            comment,
            apiRequestId,
            requestingUser,
          )
        }
      }
    }

  def createClassRequestV2(jsonLd: String, apiRequestId: UUID, requestingUser: User): IO[String, CreateClassRequestV2] =
    ZIO.scoped {
      for {
        ds         <- DatasetOps.fromJsonLd(jsonLd)
        meta       <- extractOntologyMetadata(ds.defaultModel)
        classModel <- ZIO.fromOption(ds.namedModel(meta.ontologyIri.toString)).orElseFail("No class definition found")
        classInfo  <- extractClassInfo(classModel)
        _ <-
          ZIO
            .fail(
              s"Ontology for class '${classInfo.classIri.toString}' does not match ontology ${meta.ontologyIri.toString}",
            )
            .unless(meta.ontologyIri.smartIri == classInfo.classIri.getOntologyFromEntity)
      } yield CreateClassRequestV2(
        classInfo,
        meta.lastModificationDate,
        apiRequestId,
        requestingUser,
      )
    }

  private def extractClassInfo(classModel: Model): ZIO[Scope, String, ClassInfoContentV2] =
    for {
      r             <- ZIO.fromEither(classModel.singleRootResource)
      classIri      <- extractClassIri(r)
      predicates    <- extractPredicates(r)
      cardinalities <- extractCardinalities(r)
      datatypeInfo   = None
      subClasses    <- extractSubClasses(r).map(_.map(_.smartIri))
    } yield ClassInfoContentV2(classIri.smartIri, predicates, cardinalities, datatypeInfo, subClasses, ApiV2Complex)

  private def extractClassIri(r: Resource): ZIO[Scope, String, ResourceClassIri] =
    ZIO.fromOption(r.uri).orElseFail("No class IRI found").flatMap(str => iriConverter.asResourceClassIri(str))

  private def extractPredicates(r: Resource): ZIO[Scope, String, Map[SmartIri, PredicateInfoV2]] =
    val propertyIter = r
      .listProperties()
      .asScala
      .filterNot(_.predicateUri == null)
      .filterNot(_.predicateUri == RDFS.subPropertyOf.toString)
      .filterNot(_.predicateUri == RDFS.subClassOf.toString)
      .toList
    ZIO.foreach(propertyIter)(extractPredicateInfo).map(_.toMap).logError

  private def extractPredicateInfo(stmt: Statement): ZIO[Scope, String, (SmartIri, PredicateInfoV2)] =
    for {
      propertyIri <- iriConverter.asSmartIri(stmt.predicateUri).mapError(_.getMessage)
      objects     <- asPredicateInfoV2(stmt.getObject)
    } yield (propertyIri, PredicateInfoV2(propertyIri, List(objects)))

  private def asPredicateInfoV2(node: RDFNode): ZIO[Scope, String, OntologyLiteralV2] =
    node match
      case res: Resource => iriConverter.asSmartIri(res.getURI).mapBoth(_.getMessage, SmartIriLiteralV2.apply)
      case literal: Literal => {
        literal.getValue match
          case str: String          => ZIO.succeed(StringLiteralV2.from(str, Option(literal.getLanguage)))
          case b: java.lang.Boolean => ZIO.succeed(BooleanLiteralV2(b))
          case _                    => ZIO.fail(s"Unsupported literal type: ${literal.getValue.getClass}")
      }

  private def extractSubClasses(r: Resource): ZIO[Scope, String, Set[ResourceClassIri]] = {
    val subclasses: Set[String] = r.listProperties(RDFS.subClassOf).asScala.flatMap(_.objectAsUri.toOption).toSet
    iriConverter.asResourceClassIris(subclasses)
  }

  private def extractCardinalities(r: Resource): ZIO[Scope, String, Map[SmartIri, KnoraCardinalityInfo]] = {
    val cardinalities: Either[String, Map[String, KnoraCardinalityInfo]] =
      r.listProperties(RDFS.subClassOf)
        .asScala
        .flatMap(asKnoraCardinalityResource)
        .map { res =>
          for {
            prop <- res.objectUri(OWL.onProperty)
            card <- asKnoraCardinalityInfo(res)
          } yield (prop, card)
        }
        .foldLeft(Right(Map.empty))(
          (
            acc: Either[String, Map[String, KnoraCardinalityInfo]],
            elem: Either[String, (String, KnoraCardinalityInfo)],
          ) =>
            (acc, elem) match
              case (Right(accMap), Right(elemTuple)) => Right(accMap + elemTuple)
              case (Left(err), Left(err2))           => Left(err + "," + err2)
              case (Left(err), _)                    => Left(err)
              case (_, Left(err))                    => Left(err),
        )

    ZIO
      .fromEither(cardinalities)
      .flatMap(ZIO.foreach(_) { case (key, value) => iriConverter.asPropertyIri(key).map(p => (p.smartIri, value)) })
  }

  private def asKnoraCardinalityResource(stmt: Statement): Option[Resource] =
    def isKnoraCardinality(res: Resource): Boolean =
      res.isAnon && res.hasProperty(OWL.onProperty) && res.objectRdfClass().contains(OWL.Restriction.toString)
    stmt.getObject match
      case res: Resource if isKnoraCardinality(res) => Some(res)
      case _                                        => None

  private def asKnoraCardinalityInfo(bNode: Resource): Either[String, KnoraCardinalityInfo] = {
    val minMaxEither: Either[String, (Int, Option[Int])] = for {
      max  <- bNode.objectIntOption(OWL.maxCardinality)
      min  <- bNode.objectIntOption(OWL.minCardinality)
      card <- bNode.objectIntOption(OWL.cardinality)
    } yield (card.orElse(min).getOrElse(0), card.orElse(max))

    for {
      minMax      <- minMaxEither
      cardinality <- Cardinality.from.tupled.apply(minMax)
      guiOrder    <- bNode.objectIntOption(SalsahGui.External.GuiOrder)
    } yield KnoraCardinalityInfo(cardinality, guiOrder)
  }
}

object OntologyV2RequestParser {
  val layer = ZLayer.derive[OntologyV2RequestParser]
}
