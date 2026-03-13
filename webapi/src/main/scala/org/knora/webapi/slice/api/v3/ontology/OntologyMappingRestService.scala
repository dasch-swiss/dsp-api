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
import org.knora.webapi.responders.IriLocker
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
import org.knora.webapi.slice.ontology.repo.service.OntologyCache.ONTOLOGY_CACHE_LOCK_IRI
import org.knora.webapi.store.triplestore.api.TriplestoreService

final class OntologyMappingRestService(
  auth: V3Authorizer,
  iriConverter: IriConverter,
  ontologyRepo: OntologyRepo,
  ontologyCache: OntologyCache,
  triplestore: TriplestoreService,
  projectService: KnoraProjectService,
)(implicit val sf: StringFormatter) {

  private val MaxMappingsPerRequest = 100

  // Characters forbidden in SPARQL 1.1 IRIREF production (RFC 3987 + SPARQL restrictions).
  // '}' closes DELETE/INSERT/GRAPH blocks; '{' opens them — both are the primary injection vectors.
  private val sparqlIriRefForbidden = Set('{', '}', '"', '<', '>', '\\', '^', '`', ' ', '\n', '\r', '\t')

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
      // Validation precedes auth intentionally: malformed requests are rejected cheaply before a DB lookup.
      // Authenticated non-admins can observe the 100-IRI limit via HTTP 400; this is accepted low-severity behaviour.
      _ <- ZIO
             .fail(BadRequest("'mappings' must contain at least one IRI."))
             .unless(request.mappings.nonEmpty)
      _ <-
        ZIO
          .fail(
            BadRequest(s"'mappings' must contain at most $MaxMappingsPerRequest IRIs (got ${request.mappings.size})."),
          )
          .unless(request.mappings.size <= MaxMappingsPerRequest)
      ontologyIri <- iriConverter.asOntologyIri(ontologyIriStr).mapError(BadRequest(_))
      classIri    <- iriConverter.asResourceClassIri(classIriStr).mapError(BadRequest(_))
      ontology    <- lookupOntology(ontologyIri)
      _           <- authorizeByProject(user, ontology)
      _           <- ZIO.fail(classNotFound(classIri, ontologyIri)).unless(classIri.ontologyIri == ontologyIri)
      _           <- lookupClass(classIri, ontologyIri)
      projectIris <- projectOntologyIris
      mappings    <- ZIO
                    .validate(request.mappings)(validateExternalIri(projectIris, _))
                    .mapError(errors =>
                      BadRequest(s"${errors.size} mapping IRI(s) failed validation: ${errors.mkString("; ")}"),
                    )
      update    <- AddClassMappingQuery.build(ontologyIri, classIri.smartIri, mappings)
      requestId <- Random.nextUUID
      // Infrastructure failures after the SPARQL commit are not recoverable within this request.
      // orDie escalates to a fiber defect; ZIO HTTP's global handler returns HTTP 500 without
      // exposing internals. Clients must treat PUT as idempotent on retry (re-inserting a triple is a no-op).
      // buildClassResponse is inside the lock so the cache read is guaranteed to reflect this write.
      response <- IriLocker
                    .runWithIriLock(requestId, ONTOLOGY_CACHE_LOCK_IRI)(
                      triplestore.query(update) *> ontologyCache.refreshCache() *> buildClassResponse(ontologyIri, classIri),
                    )
                    .tapError(e => ZIO.logError(s"PUT class mapping failed for $classIri in $ontologyIri: ${e.getMessage}"))
                    .orDie
    } yield response

  /** F2 — DELETE class mapping */
  def deleteClassMapping(
    user: User,
  )(ontologyIriStr: String, classIriStr: String, mappingOpt: Option[String]): IO[V3ErrorInfo, ClassMappingResponse] =
    for {
      mappingStr  <- ZIO.fromOption(mappingOpt).mapError(_ => BadRequest("Missing required query parameter 'mapping'."))
      ontologyIri <- iriConverter.asOntologyIri(ontologyIriStr).mapError(BadRequest(_))
      classIri    <- iriConverter.asResourceClassIri(classIriStr).mapError(BadRequest(_))
      ontology    <- lookupOntology(ontologyIri)
      _           <- authorizeByProject(user, ontology)
      _           <- ZIO.fail(classNotFound(classIri, ontologyIri)).unless(classIri.ontologyIri == ontologyIri)
      _           <- lookupClass(classIri, ontologyIri)
      projectIris <- projectOntologyIris
      extIri      <- validateExternalIri(projectIris, mappingStr).mapError(BadRequest(_))
      update      <- RemoveClassMappingQuery.build(ontologyIri, classIri.smartIri, extIri)
      requestId   <- Random.nextUUID
      // Infrastructure failures after the SPARQL commit are not recoverable within this request.
      // orDie escalates to a fiber defect; ZIO HTTP's global handler returns HTTP 500 without
      // exposing internals. Clients must treat DELETE as idempotent on retry (removing an absent triple is a no-op).
      // buildClassResponse is inside the lock so the cache read is guaranteed to reflect this write.
      response <- IriLocker
                    .runWithIriLock(requestId, ONTOLOGY_CACHE_LOCK_IRI)(
                      triplestore.query(update) *> ontologyCache.refreshCache() *> buildClassResponse(ontologyIri, classIri),
                    )
                    .tapError(e => ZIO.logError(s"DELETE class mapping failed for $classIri in $ontologyIri: ${e.getMessage}"))
                    .orDie
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
      // Validation precedes auth intentionally: malformed requests are rejected cheaply before a DB lookup.
      // Authenticated non-admins can observe the 100-IRI limit via HTTP 400; this is accepted low-severity behaviour.
      _ <- ZIO
             .fail(BadRequest("'mappings' must contain at least one IRI."))
             .unless(request.mappings.nonEmpty)
      _ <-
        ZIO
          .fail(
            BadRequest(s"'mappings' must contain at most $MaxMappingsPerRequest IRIs (got ${request.mappings.size})."),
          )
          .unless(request.mappings.size <= MaxMappingsPerRequest)
      ontologyIri <- iriConverter.asOntologyIri(ontologyIriStr).mapError(BadRequest(_))
      propertyIri <- iriConverter.asPropertyIri(propertyIriStr).mapError(BadRequest(_))
      ontology    <- lookupOntology(ontologyIri)
      _           <- authorizeByProject(user, ontology)
      _           <- ZIO.fail(propertyNotFound(propertyIri, ontologyIri)).unless(propertyIri.ontologyIri == ontologyIri)
      _           <- lookupProperty(propertyIri, ontologyIri)
      projectIris <- projectOntologyIris
      mappings    <- ZIO
                    .validate(request.mappings)(validateExternalIri(projectIris, _))
                    .mapError(errors =>
                      BadRequest(s"${errors.size} mapping IRI(s) failed validation: ${errors.mkString("; ")}"),
                    )
      // TODO (future): validate OWL DL property type compatibility (ObjectProperty vs DatatypeProperty).
      // OWL DL disallows mapping an ObjectProperty to a DatatypeProperty. Deferred per PRD "same contract as F1".
      update    <- AddPropertyMappingQuery.build(ontologyIri, propertyIri.smartIri, mappings)
      requestId <- Random.nextUUID
      // Infrastructure failures after the SPARQL commit are not recoverable within this request.
      // orDie escalates to a fiber defect; ZIO HTTP's global handler returns HTTP 500 without
      // exposing internals. Clients must treat PUT as idempotent on retry (re-inserting a triple is a no-op).
      // buildPropertyResponse is inside the lock so the cache read is guaranteed to reflect this write.
      response <-
        IriLocker
          .runWithIriLock(requestId, ONTOLOGY_CACHE_LOCK_IRI)(
            triplestore.query(update) *> ontologyCache.refreshCache() *> buildPropertyResponse(ontologyIri, propertyIri),
          )
          .tapError(e => ZIO.logError(s"PUT property mapping failed for $propertyIri in $ontologyIri: ${e.getMessage}"))
          .orDie
    } yield response

  /** F4 — DELETE property mapping */
  def deletePropertyMapping(
    user: User,
  )(
    ontologyIriStr: String,
    propertyIriStr: String,
    mappingOpt: Option[String],
  ): IO[V3ErrorInfo, PropertyMappingResponse] =
    for {
      mappingStr  <- ZIO.fromOption(mappingOpt).mapError(_ => BadRequest("Missing required query parameter 'mapping'."))
      ontologyIri <- iriConverter.asOntologyIri(ontologyIriStr).mapError(BadRequest(_))
      propertyIri <- iriConverter.asPropertyIri(propertyIriStr).mapError(BadRequest(_))
      ontology    <- lookupOntology(ontologyIri)
      _           <- authorizeByProject(user, ontology)
      _           <- ZIO.fail(propertyNotFound(propertyIri, ontologyIri)).unless(propertyIri.ontologyIri == ontologyIri)
      _           <- lookupProperty(propertyIri, ontologyIri)
      projectIris <- projectOntologyIris
      extIri      <- validateExternalIri(projectIris, mappingStr).mapError(BadRequest(_))
      update      <- RemovePropertyMappingQuery.build(ontologyIri, propertyIri.smartIri, extIri)
      requestId   <- Random.nextUUID
      // Infrastructure failures after the SPARQL commit are not recoverable within this request.
      // orDie escalates to a fiber defect; ZIO HTTP's global handler returns HTTP 500 without
      // exposing internals. Clients must treat DELETE as idempotent on retry (removing an absent triple is a no-op).
      // buildPropertyResponse is inside the lock so the cache read is guaranteed to reflect this write.
      response <- IriLocker
                    .runWithIriLock(requestId, ONTOLOGY_CACHE_LOCK_IRI)(
                      triplestore.query(update) *> ontologyCache.refreshCache() *> buildPropertyResponse(ontologyIri, propertyIri),
                    )
                    .tapError(e =>
                      ZIO.logError(s"DELETE property mapping failed for $propertyIri in $ontologyIri: ${e.getMessage}"),
                    )
                    .orDie
    } yield response

  private def projectOntologyIris: UIO[Set[SmartIri]] =
    ontologyCache.getCacheData.map { cacheData =>
      cacheData.ontologies.keySet.filter(iri => !iri.isKnoraBuiltInDefinitionIri && !iri.isKnoraSharedDefinitionIri)
    }

  private def validateExternalIri(projectOntologyIris: Set[SmartIri], iriStr: String): IO[String, SmartIri] = {
    // Explicit IRIREF guard: reject characters that are illegal in SPARQL 1.1 IRIREF positions
    // regardless of where they appear (path, query, fragment). The UrlValidator path-component
    // regex blocks '{'/'}' in path segments but not in query components.
    val forbiddenChar = iriStr.find(sparqlIriRefForbidden.contains)
    if forbiddenChar.isDefined then
      ZIO.fail(s"Mapping IRI contains a forbidden character '${forbiddenChar.get}': $iriStr")
    else {
      // Pre-compute once per validateExternalIri call; callers invoke this inside ZIO.validate
      // so this allocation happens once per IRI, not once per IRI×ontology.
      val bases = projectOntologyIris.map(_.toIri.stripSuffix("/"))
      iriConverter.asSmartIri(iriStr).mapError(_.getMessage).flatMap { iri =>
        if iri.isKnoraOntologyIri || iri.isKnoraEntityIri then
          ZIO.fail(s"Mapping IRI must be an external IRI, not a Knora-managed IRI: $iriStr")
        else if isProjectOntologyIri(iri, bases) then
          ZIO.fail(s"Mapping IRI belongs to a DSP project ontology and cannot be used as a mapping: $iriStr")
        else ZIO.succeed(iri)
      }
    }
  }

  private def isProjectOntologyIri(iri: SmartIri, projectOntologyBases: Set[String]): Boolean = {
    val iriStr = iri.toIri
    projectOntologyBases.exists(base => iriStr == base || iriStr.startsWith(base + "/") || iriStr.startsWith(base + "#"))
  }

  private def lookupOntology(ontologyIri: OntologyIri): IO[NotFound, ReadOntologyV2] =
    // Repository failures are infrastructure errors; escalate to fiber defect (HTTP 500).
    ontologyRepo.findById(ontologyIri).orDie.someOrFail(NotFound(ontologyIri))

  private def lookupClass(classIri: ResourceClassIri, ontologyIri: OntologyIri): IO[NotFound, ReadClassInfoV2] =
    // Repository failures are infrastructure errors; escalate to fiber defect (HTTP 500).
    ontologyRepo.findClassBy(classIri).orDie.someOrFail(classNotFound(classIri, ontologyIri))

  private def lookupProperty(propertyIri: PropertyIri, ontologyIri: OntologyIri): IO[NotFound, ReadPropertyInfoV2] =
    // Repository failures are infrastructure errors; escalate to fiber defect (HTTP 500).
    ontologyRepo.findProperty(propertyIri).orDie.someOrFail(propertyNotFound(propertyIri, ontologyIri))

  private def authorizeByProject(user: User, ontology: ReadOntologyV2): IO[V3ErrorInfo, Unit] =
    ontology.projectIri match {
      case None             => ZIO.fail(Forbidden("Cannot modify a system ontology."))
      case Some(projectIri) =>
        projectService.findById(projectIri).orDie.flatMap {
          case None          => ZIO.fail(Forbidden("Cannot modify this ontology."))
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

  private def resolveLmd(ontologyIri: OntologyIri, ontology: ReadOntologyV2): UIO[java.time.Instant] = {
    val lmdOpt = ontology.ontologyMetadata.lastModificationDate
    ZIO
      .logWarning(s"Ontology $ontologyIri has no lastModificationDate; returning Instant.EPOCH")
      .unless(lmdOpt.isDefined)
      .as(lmdOpt.getOrElse(java.time.Instant.EPOCH))
  }

  private def buildClassResponse(ontologyIri: OntologyIri, classIri: ResourceClassIri): UIO[ClassMappingResponse] =
    for {
      ontology <- ontologyRepo
                    .findById(ontologyIri)
                    .someOrFail(new RuntimeException(s"Ontology $ontologyIri missing from cache after update"))
                    .orDie
      classInfo <- ontologyRepo
                     .findClassBy(classIri)
                     .someOrFail(new RuntimeException(s"Class $classIri missing from cache after update"))
                     .orDie
      lmd <- resolveLmd(ontologyIri, ontology)
    } yield ClassMappingResponse(
      classIri = classIri.toComplexSchema.toIri,
      ontologyIri = ontologyIri.toComplexSchema.toIri,
      subClassOf = classInfo.entityInfoContent.subClassOf.toList.map(_.toComplexSchema.toIri).sorted,
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
                    .someOrFail(new RuntimeException(s"Ontology $ontologyIri missing from cache after update"))
                    .orDie
      propertyInfo <- ontologyRepo
                        .findProperty(propertyIri)
                        .someOrFail(new RuntimeException(s"Property $propertyIri missing from cache after update"))
                        .orDie
      lmd <- resolveLmd(ontologyIri, ontology)
    } yield PropertyMappingResponse(
      propertyIri = propertyIri.toComplexSchema.toIri,
      ontologyIri = ontologyIri.toComplexSchema.toIri,
      subPropertyOf = propertyInfo.entityInfoContent.subPropertyOf.toList.map(_.toComplexSchema.toIri).sorted,
      label = propertyInfo.entityInfoContent.predicates.get(rdfsLabel).flatMap(LanguageStringDto.from).toList,
      comment = propertyInfo.entityInfoContent.predicates.get(rdfsComment).flatMap(LanguageStringDto.from).toList,
      lastModificationDate = lmd,
    )
}

object OntologyMappingRestService {
  val layer = ZLayer.derive[OntologyMappingRestService]
}
