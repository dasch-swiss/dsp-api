/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.ontology

import zio.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v3.*
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.AddClassMappingQuery
import org.knora.webapi.slice.ontology.repo.AddPropertyMappingQuery
import org.knora.webapi.slice.ontology.repo.RemoveClassMappingQuery
import org.knora.webapi.slice.ontology.repo.RemovePropertyMappingQuery
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

class OntologyMappingRestService(
  private val auth: V3Authorizer,
  private val iriConverter: IriConverter,
  private val ontologyRepo: OntologyRepo,
  private val ontologyCache: OntologyCache,
  private val triplestore: TriplestoreService,
  private val projectService: KnoraProjectService,
)(implicit sf: StringFormatter) {

  private val MaxMappingsPerRequest = 100

  private val rdfsLabel   = Rdfs.Label.toSmartIri
  private val rdfsComment = Rdfs.Comment.toSmartIri

  /** F1 — PUT class mapping */
  def putClassMapping(
    user: User,
  )(
    ontologyIriStr: String,
    classIriStr: String,
    request: AddClassMappingsRequest,
  ): IO[V3ErrorInfo, ClassMappingResponse] =
    for {
      _           <- ZIO
                       .fail(BadRequest("'mappings' must contain at least one IRI."))
                       .unless(request.mappings.nonEmpty)
      _           <- ZIO
                       .fail(BadRequest(s"'mappings' must contain at most $MaxMappingsPerRequest IRIs (got ${request.mappings.size})."))
                       .unless(request.mappings.size <= MaxMappingsPerRequest)
      ontologyIri <- iriConverter.asOntologyIri(ontologyIriStr).mapError(BadRequest(_))
      classIri    <- iriConverter.asResourceClassIri(classIriStr).mapError(BadRequest(_))
      projectIris <- projectOntologyIris
      mappings    <- ZIO
                       .validate(request.mappings)(validateExternalIri(projectIris, _))
                       .mapError(errors => BadRequest(errors.mkString(", ")))
      ontology    <- lookupOntology(ontologyIri)
      _           <- lookupClass(classIri, ontologyIri)
      _           <- authorizeByProject(user, ontology)
      update      <- AddClassMappingQuery.build(ontologyIri, classIri.smartIri, mappings)
      _           <- (triplestore.query(update) *> ontologyCache.refreshCache()).orDie
      response    <- buildClassResponse(ontologyIri, classIri)
    } yield response

  /** F2 — DELETE class mapping */
  def deleteClassMapping(
    user: User,
  )(ontologyIriStr: String, classIriStr: String, mappingOpt: Option[String]): IO[V3ErrorInfo, ClassMappingResponse] =
    for {
      mappingStr  <- ZIO.fromOption(mappingOpt).mapError(_ => BadRequest("Missing required query parameter 'mapping'."))
      ontologyIri <- iriConverter.asOntologyIri(ontologyIriStr).mapError(BadRequest(_))
      classIri    <- iriConverter.asResourceClassIri(classIriStr).mapError(BadRequest(_))
      projectIris <- projectOntologyIris
      extIri      <- validateExternalIri(projectIris, mappingStr).mapError(BadRequest(_))
      ontology    <- lookupOntology(ontologyIri)
      _           <- lookupClass(classIri, ontologyIri)
      _           <- authorizeByProject(user, ontology)
      update      <- RemoveClassMappingQuery.build(ontologyIri, classIri.smartIri, extIri)
      _           <- (triplestore.query(update) *> ontologyCache.refreshCache()).orDie
      response    <- buildClassResponse(ontologyIri, classIri)
    } yield response

  /** F3 — PUT property mapping */
  def putPropertyMapping(
    user: User,
  )(
    ontologyIriStr: String,
    propertyIriStr: String,
    request: AddPropertyMappingsRequest,
  ): IO[V3ErrorInfo, PropertyMappingResponse] =
    for {
      _           <- ZIO
                       .fail(BadRequest("'mappings' must contain at least one IRI."))
                       .unless(request.mappings.nonEmpty)
      _           <- ZIO
                       .fail(BadRequest(s"'mappings' must contain at most $MaxMappingsPerRequest IRIs (got ${request.mappings.size})."))
                       .unless(request.mappings.size <= MaxMappingsPerRequest)
      ontologyIri <- iriConverter.asOntologyIri(ontologyIriStr).mapError(BadRequest(_))
      propertyIri <- iriConverter.asPropertyIri(propertyIriStr).mapError(BadRequest(_))
      projectIris <- projectOntologyIris
      mappings    <- ZIO
                       .validate(request.mappings)(validateExternalIri(projectIris, _))
                       .mapError(errors => BadRequest(errors.mkString(", ")))
      ontology    <- lookupOntology(ontologyIri)
      _           <- lookupProperty(propertyIri, ontologyIri)
      _           <- authorizeByProject(user, ontology)
      update      <- AddPropertyMappingQuery.build(ontologyIri, propertyIri.smartIri, mappings)
      _           <- (triplestore.query(update) *> ontologyCache.refreshCache()).orDie
      response    <- buildPropertyResponse(ontologyIri, propertyIri)
    } yield response
    // TODO (future): validate OWL DL property type compatibility (ObjectProperty vs DatatypeProperty via rdfs:subPropertyOf).
    // OWL DL disallows mapping an ObjectProperty to a DatatypeProperty. Deferred per PRD "same contract as F1".

  /** F4 — DELETE property mapping */
  def deletePropertyMapping(
    user: User,
  )(ontologyIriStr: String, propertyIriStr: String, mappingOpt: Option[String]): IO[V3ErrorInfo, PropertyMappingResponse] =
    for {
      mappingStr  <- ZIO.fromOption(mappingOpt).mapError(_ => BadRequest("Missing required query parameter 'mapping'."))
      ontologyIri <- iriConverter.asOntologyIri(ontologyIriStr).mapError(BadRequest(_))
      propertyIri <- iriConverter.asPropertyIri(propertyIriStr).mapError(BadRequest(_))
      projectIris <- projectOntologyIris
      extIri      <- validateExternalIri(projectIris, mappingStr).mapError(BadRequest(_))
      ontology    <- lookupOntology(ontologyIri)
      _           <- lookupProperty(propertyIri, ontologyIri)
      _           <- authorizeByProject(user, ontology)
      update      <- RemovePropertyMappingQuery.build(ontologyIri, propertyIri.smartIri, extIri)
      _           <- (triplestore.query(update) *> ontologyCache.refreshCache()).orDie
      response    <- buildPropertyResponse(ontologyIri, propertyIri)
    } yield response

  private def projectOntologyIris: UIO[Set[SmartIri]] =
    ontologyCache.getCacheData.map { cacheData =>
      cacheData.ontologies.keySet.filter(iri => !iri.isKnoraBuiltInDefinitionIri && !iri.isKnoraSharedDefinitionIri)
    }

  private def validateExternalIri(projectOntologyIris: Set[SmartIri], iriStr: String): IO[String, SmartIri] =
    iriConverter.asSmartIri(iriStr).mapError(_.getMessage).flatMap { iri =>
      if iri.isKnoraOntologyIri || iri.isKnoraEntityIri then
        ZIO.fail(s"Mapping IRI must be an external IRI, not a Knora-managed IRI: $iriStr")
      else if isProjectOntologyIri(iri, projectOntologyIris) then
        ZIO.fail(s"Mapping IRI belongs to a DSP project ontology and cannot be used as a mapping: $iriStr")
      else ZIO.succeed(iri)
    }

  private def isProjectOntologyIri(iri: SmartIri, projectOntologyIris: Set[SmartIri]): Boolean = {
    val iriStr = iri.toIri
    projectOntologyIris.exists { ontIri =>
      val base = ontIri.toIri.stripSuffix("/")
      iriStr == base || iriStr.startsWith(base + "/") || iriStr.startsWith(base + "#")
    }
  }

  private def lookupOntology(ontologyIri: OntologyIri): IO[NotFound, ReadOntologyV2] =
    ontologyRepo.findById(ontologyIri).orDie.someOrFail(NotFound(ontologyIri))

  private def lookupClass(classIri: ResourceClassIri, ontologyIri: OntologyIri): IO[NotFound, ReadClassInfoV2] =
    ontologyRepo.findClassBy(classIri).orDie.someOrFail(classNotFound(classIri, ontologyIri))

  private def lookupProperty(propertyIri: PropertyIri, ontologyIri: OntologyIri): IO[NotFound, ReadPropertyInfoV2] =
    ontologyRepo.findProperty(propertyIri).orDie.someOrFail(propertyNotFound(propertyIri, ontologyIri))

  private def authorizeByProject(user: User, ontology: ReadOntologyV2): IO[V3ErrorInfo, Unit] =
    ontology.projectIri match {
      case None => ZIO.fail(Forbidden("Cannot modify a system ontology."))
      case Some(projectIri) =>
        projectService.findById(projectIri).orDie.flatMap {
          case None          => ZIO.fail(Forbidden(s"Project $projectIri not found."))
          case Some(project) => auth.ensureSystemAdminOrProjectAdmin(user, project)
        }
    }

  private def classNotFound(classIri: ResourceClassIri, ontologyIri: OntologyIri): NotFound = {
    val code: V3ErrorCode.NotFounds = V3ErrorCode.class_not_found
    NotFound(
      code,
      code.template.replace("{id}", classIri.toString).replace("{ontologyIri}", ontologyIri.toString),
      Map("id" -> classIri.toString, "ontologyIri" -> ontologyIri.toString),
    )
  }

  private def propertyNotFound(propertyIri: PropertyIri, ontologyIri: OntologyIri): NotFound = {
    val code: V3ErrorCode.NotFounds = V3ErrorCode.property_not_found
    NotFound(
      code,
      code.template.replace("{id}", propertyIri.toString).replace("{ontologyIri}", ontologyIri.toString),
      Map("id" -> propertyIri.toString, "ontologyIri" -> ontologyIri.toString),
    )
  }

  private def buildClassResponse(ontologyIri: OntologyIri, classIri: ResourceClassIri): UIO[ClassMappingResponse] =
    for {
      ontology <- ontologyRepo
                    .findById(ontologyIri)
                    .orDie
                    .someOrFail(new RuntimeException(s"Ontology $ontologyIri missing from cache after update"))
                    .orDie
      classInfo <- ontologyRepo
                     .findClassBy(classIri)
                     .orDie
                     .someOrFail(new RuntimeException(s"Class $classIri missing from cache after update"))
                     .orDie
      lmd = ontology.ontologyMetadata.lastModificationDate.getOrElse(java.time.Instant.EPOCH)
    } yield ClassMappingResponse(
      classIri = classIri.toComplexSchema.toIri,
      ontologyIri = ontologyIri.toComplexSchema.toIri,
      subClassOf = classInfo.entityInfoContent.subClassOf.toList.map(_.toComplexSchema.toIri),
      label = classInfo.entityInfoContent.predicates.get(rdfsLabel).flatMap(LanguageStringDto.from).toList,
      comment = classInfo.entityInfoContent.predicates.get(rdfsComment).flatMap(LanguageStringDto.from).toList,
      lastModificationDate = lmd,
    )

  private def buildPropertyResponse(
    ontologyIri: OntologyIri,
    propertyIri: PropertyIri,
  ): UIO[PropertyMappingResponse] =
    for {
      ontology <- ontologyRepo
                    .findById(ontologyIri)
                    .orDie
                    .someOrFail(new RuntimeException(s"Ontology $ontologyIri missing from cache after update"))
                    .orDie
      propertyInfo <- ontologyRepo
                        .findProperty(propertyIri)
                        .orDie
                        .someOrFail(new RuntimeException(s"Property $propertyIri missing from cache after update"))
                        .orDie
      lmd = ontology.ontologyMetadata.lastModificationDate.getOrElse(java.time.Instant.EPOCH)
    } yield PropertyMappingResponse(
      propertyIri = propertyIri.toComplexSchema.toIri,
      ontologyIri = ontologyIri.toComplexSchema.toIri,
      subPropertyOf = propertyInfo.entityInfoContent.subPropertyOf.toList.map(_.toComplexSchema.toIri),
      label = propertyInfo.entityInfoContent.predicates.get(rdfsLabel).flatMap(LanguageStringDto.from).toList,
      comment = propertyInfo.entityInfoContent.predicates.get(rdfsComment).flatMap(LanguageStringDto.from).toList,
      lastModificationDate = lmd,
    )
}

object OntologyMappingRestService {
  val layer = ZLayer.derive[OntologyMappingRestService]
}
