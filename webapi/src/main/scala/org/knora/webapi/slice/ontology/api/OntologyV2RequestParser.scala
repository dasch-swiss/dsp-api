/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api

import org.apache.jena.rdf.model.*
import org.apache.jena.vocabulary.OWL
import org.apache.jena.vocabulary.RDFS
import zio.*

import java.time.Instant
import java.util.UUID
import scala.jdk.CollectionConverters.*
import scala.language.implicitConversions

import dsp.constants.SalsahGui
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.v2.responder.ontologymessages.ChangeOntologyMetadataRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ClassInfoContentV2
import org.knora.webapi.messages.v2.responder.ontologymessages.CreateClassRequestV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.owlCardinality2KnoraCardinality
import org.knora.webapi.messages.v2.responder.ontologymessages.PredicateInfoV2
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
      lastModificationDate <- ZIO.fromEither(r.objectInstant(LastModificationDate))
    } yield OntologyMetadata(ontologyIri, label, comment, lastModificationDate)

  private def uriAsOntologyIri(r: Resource): ZIO[Scope, String, OntologyIri] = ZIO
    .fromOption(r.uri)
    .orElseFail("No IRI found")
    .flatMap(iriConverter.asSmartIri(_).mapError(_.getMessage))
    .flatMap(sIri => ZIO.fromEither(OntologyIri.fromApiV2Complex(sIri)))

  def createClassRequestV2(jsonLd: String, apiRequestId: UUID, requestingUser: User): IO[String, CreateClassRequestV2] =
    ZIO.scoped {
      for {
        ds         <- DatasetOps.fromJsonLd(jsonLd)
        meta       <- extractOntologyMetadata(ds.defaultModel)
        classModel <- ZIO.fromOption(ds.namedModel(meta.ontologyIri.toString)).orElseFail("No class definition found")
        classInfo  <- extractClassInfo(classModel)
      } yield CreateClassRequestV2(
        classInfo,
        meta.lastModificationDate,
        apiRequestId,
        requestingUser,
      )
    }

  private def extractClassInfo(model: Model): ZIO[Scope, String, ClassInfoContentV2] =
    for {
      r             <- ZIO.fromEither(model.singleRootResource)
      classIri      <- extractClassIri(r)
      predicates     = Map.empty[SmartIri, PredicateInfoV2]
      cardinalities <- extractCardinalities(r)
      datatypeInfo   = None
      subClasses    <- extractSubClasses(r).map(_.map(_.smartIri))
    } yield ClassInfoContentV2(classIri.smartIri, predicates, cardinalities, datatypeInfo, subClasses, ApiV2Complex)

  private def extractClassIri(r: Resource): ZIO[Scope, String, ResourceClassIri] =
    ZIO.fromOption(r.uri).orElseFail("No class IRI found").flatMap(str => iriConverter.asResourceClassIri(str))

  private def extractSubClasses(r: Resource): ZIO[Scope, String, Set[ResourceClassIri]] = {
    val iter       = r.listProperties(RDFS.subClassOf).asScala
    val subclasses = iter.filter(stmt => !stmt.getSubject.isAnon).map(_.getSubject.uri).toSet.flatten
    iriConverter.asResourceClassIris(subclasses)
  }

  private def extractCardinalities(r: Resource): ZIO[Scope, String, Map[SmartIri, KnoraCardinalityInfo]] = {
    val iter                                                    = r.listProperties(RDFS.subClassOf).asScala
    val zero: Either[String, Map[String, KnoraCardinalityInfo]] = Right(Map.empty)
    val cardinalities: Either[String, Map[String, KnoraCardinalityInfo]] =
      iter.flatMap { stmt =>
        val obj = stmt.getObject
        if (obj.isAnon && obj.asResource().hasProperty(OWL.onProperty)) { Some(obj.asResource()) }
        else { None }
      }.map { res =>
        val minMax: Either[String, (Option[Int], Option[Int])] = for {
          max  <- res.objectIntOption(OWL.maxCardinality)
          min  <- res.objectIntOption(OWL.minCardinality)
          card <- res.objectIntOption(OWL.cardinality)
        } yield (min.orElse(card), max.orElse(card))
        val cardinality: Either[String, Cardinality] = minMax.flatMap { case (min, max) =>
          Cardinality.from(min, max).toRight(s"Invalid cardinality for ${r.uri}")
        }
        val guiOrder: Either[String, Option[RuntimeFlags]] = res.objectIntOption(SalsahGui.External.GuiOrder)
        val onProperty                                     = res.objectUri(OWL.onProperty)

        val foo: Either[String, (String, KnoraCardinalityInfo)] = for {
          prop <- onProperty
          card <- cardinality
          gui  <- guiOrder
        } yield (prop, KnoraCardinalityInfo(card, gui))
        foo
      }
        .foldLeft(zero)(
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

}

object OntologyV2RequestParser {
  val layer = ZLayer.derive[OntologyV2RequestParser]
}
