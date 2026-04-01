/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.ontology

import zio.*

import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadPropertyInfoV2
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.api.v3.*
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.model.OntologyMappingExternalIri
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.AddMappingQuery
import org.knora.webapi.slice.ontology.repo.MappingPredicate
import org.knora.webapi.slice.ontology.repo.RemoveMappingQuery
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

  private val rdfsLabel   = Rdfs.Label.toSmartIri
  private val rdfsComment = Rdfs.Comment.toSmartIri

  /** PUT class mapping */
  def putClassMapping(
    user: User,
  )(
    ontologyIriDto: IriDto,
    classIriDto: IriDto,
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
      ontologyIri <- iriConverter.asOntologyIriApiV2Complex(ontologyIriDto.value).mapError(BadRequest(_))
      classIri    <- iriConverter.asResourceClassIriApiV2Complex(classIriDto.value).mapError(BadRequest(_))
      ontology    <- lookupOntology(ontologyIri)
      _           <- authorizeByProject(user, ontology)
      _           <- ZIO.fail(classNotFound(classIri, ontologyIri)).unless(classIri.ontologyIri == ontologyIri)
      // lookupClass and IRI validation all run inside the lock so
      // the entity-existence check and validation snapshot are atomic with the write.
      response <- withOntologyLock(for
                    _        <- lookupClass(classIri, ontologyIri)
                    mappings <- ZIO
                                  .validate(request.mappings)(validateExternalIri)
                                  .mapError(errors =>
                                    BadRequest(
                                      s"${errors.size} mapping IRI(s) failed validation.",
                                      Chunk.fromIterable(errors),
                                    ),
                                  )
                    update <-
                      AddMappingQuery.build(ontologyIri, classIri.smartIri, MappingPredicate.SubClassOf, mappings)
                    _ <- (triplestore.query(update) *> ontologyCache.refreshCache())
                           .tapError(e =>
                             ZIO.logError(
                               s"PUT class mapping failed for $classIri in $ontologyIri: ${e.getMessage}",
                             ),
                           )
                           .orDie
                    r <- buildClassResponse(ontologyIri, classIri)
                  yield r)
    } yield response

  /** DELETE class mapping */
  def deleteClassMapping(
    user: User,
  )(ontologyIriDto: IriDto, classIriDto: IriDto, mappingIriDto: IriDto): IO[V3ErrorInfo, ClassMappingResponse] =
    for {
      ontologyIri <- iriConverter.asOntologyIriApiV2Complex(ontologyIriDto.value).mapError(BadRequest(_))
      classIri    <- iriConverter.asResourceClassIriApiV2Complex(classIriDto.value).mapError(BadRequest(_))
      ontology    <- lookupOntology(ontologyIri)
      _           <- authorizeByProject(user, ontology)
      _           <- ZIO.fail(classNotFound(classIri, ontologyIri)).unless(classIri.ontologyIri == ontologyIri)
      // lookupClass and IRI validation all run inside the lock so
      // the entity-existence check and validation snapshot are atomic with the write.
      response <- withOntologyLock(for
                    _      <- lookupClass(classIri, ontologyIri)
                    extIri <- validateExternalIri(mappingIriDto.value)
                                .mapError(detail => BadRequest(detail.code, detail.message, detail.details))
                    update <-
                      RemoveMappingQuery.build(ontologyIri, classIri.smartIri, MappingPredicate.SubClassOf, extIri)
                    _ <- (triplestore.query(update) *> ontologyCache.refreshCache())
                           .tapError(e =>
                             ZIO.logError(
                               s"DELETE class mapping failed for $classIri in $ontologyIri: ${e.getMessage}",
                             ),
                           )
                           .orDie
                    r <- buildClassResponse(ontologyIri, classIri)
                  yield r)
    } yield response

  /** PUT property mapping */
  def putPropertyMapping(
    user: User,
  )(
    ontologyIriDto: IriDto,
    propertyIriDto: IriDto,
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
      ontologyIri <- iriConverter.asOntologyIriApiV2Complex(ontologyIriDto.value).mapError(BadRequest(_))
      propertyIri <- iriConverter.asPropertyIriApiV2Complex(propertyIriDto.value).mapError(BadRequest(_))
      ontology    <- lookupOntology(ontologyIri)
      _           <- authorizeByProject(user, ontology)
      _           <- ZIO.fail(propertyNotFound(propertyIri, ontologyIri)).unless(propertyIri.ontologyIri == ontologyIri)
      // lookupProperty and IRI validation all run inside the lock so
      // the entity-existence check and validation snapshot are atomic with the write.
      response <- withOntologyLock(for
                    _        <- lookupProperty(propertyIri, ontologyIri)
                    mappings <- ZIO
                                  .validate(request.mappings)(validateExternalIri)
                                  .mapError(errors =>
                                    BadRequest(
                                      s"${errors.size} mapping IRI(s) failed validation.",
                                      Chunk.fromIterable(errors),
                                    ),
                                  )
                    update <-
                      AddMappingQuery.build(ontologyIri, propertyIri.smartIri, MappingPredicate.SubPropertyOf, mappings)
                    _ <- (triplestore.query(update) *> ontologyCache.refreshCache())
                           .tapError(e =>
                             ZIO.logError(
                               s"PUT property mapping failed for $propertyIri in $ontologyIri: ${e.getMessage}",
                             ),
                           )
                           .orDie
                    r <- buildPropertyResponse(ontologyIri, propertyIri)
                  yield r)
    } yield response

  /** DELETE property mapping */
  def deletePropertyMapping(
    user: User,
  )(
    ontologyIriDto: IriDto,
    propertyIriDto: IriDto,
    mappingIriDto: IriDto,
  ): IO[V3ErrorInfo, PropertyMappingResponse] =
    for {
      ontologyIri <- iriConverter.asOntologyIriApiV2Complex(ontologyIriDto.value).mapError(BadRequest(_))
      propertyIri <- iriConverter.asPropertyIriApiV2Complex(propertyIriDto.value).mapError(BadRequest(_))
      ontology    <- lookupOntology(ontologyIri)
      _           <- authorizeByProject(user, ontology)
      _           <- ZIO.fail(propertyNotFound(propertyIri, ontologyIri)).unless(propertyIri.ontologyIri == ontologyIri)
      // lookupProperty and IRI validation all run inside the lock so
      // the entity-existence check and validation snapshot are atomic with the write.
      response <- withOntologyLock(for
                    _      <- lookupProperty(propertyIri, ontologyIri)
                    extIri <- validateExternalIri(mappingIriDto.value)
                                .mapError(detail => BadRequest(detail.code, detail.message, detail.details))
                    update <-
                      RemoveMappingQuery
                        .build(ontologyIri, propertyIri.smartIri, MappingPredicate.SubPropertyOf, extIri)
                    _ <- (triplestore.query(update) *> ontologyCache.refreshCache())
                           .tapError(e =>
                             ZIO.logError(
                               s"DELETE property mapping failed for $propertyIri in $ontologyIri: ${e.getMessage}",
                             ),
                           )
                           .orDie
                    r <- buildPropertyResponse(ontologyIri, propertyIri)
                  yield r)
    } yield response

  /** Runs [[action]] inside the ontology-cache lock, escalating lock failures to defects. */
  private def withOntologyLock[A](action: IO[V3ErrorInfo, A]): IO[V3ErrorInfo, A] =
    Random.nextUUID.flatMap { requestId =>
      IriLocker
        .runWithIriLock(requestId, ONTOLOGY_CACHE_LOCK_IRI)(action.either)
        .orDie
        .flatMap(ZIO.fromEither(_))
    }

  private def validateExternalIri(iriStr: String): IO[ErrorDetail, OntologyMappingExternalIri] =
    ZIO
      .fromEither(OntologyMappingExternalIri.from(iriStr))
      .mapError(msg => ErrorDetail(V3ErrorCode.invalid_ontology_mapping_iri, msg, Map("iri" -> iriStr)))

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

  /** Loads the ontology from the cache and resolves its [[lastModificationDate]], defaulting to EPOCH if absent. */
  private def resolveOntologyAndLmd(ontologyIri: OntologyIri): UIO[java.time.Instant] =
    ontologyRepo
      .findById(ontologyIri)
      .someOrFail(new RuntimeException(s"Ontology $ontologyIri missing from cache after update"))
      .orDie
      .flatMap { ontology =>
        val lmdOpt = ontology.ontologyMetadata.lastModificationDate
        ZIO
          .logWarning(s"Ontology $ontologyIri has no lastModificationDate; returning Instant.EPOCH")
          .unless(lmdOpt.isDefined)
          .as(lmdOpt.getOrElse(java.time.Instant.EPOCH))
      }

  private def buildClassResponse(ontologyIri: OntologyIri, classIri: ResourceClassIri): UIO[ClassMappingResponse] =
    for {
      lmd       <- resolveOntologyAndLmd(ontologyIri)
      classInfo <- ontologyRepo
                     .findClassBy(classIri)
                     .someOrFail(new RuntimeException(s"Class $classIri missing from cache after update"))
                     .orDie
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
      lmd          <- resolveOntologyAndLmd(ontologyIri)
      propertyInfo <- ontologyRepo
                        .findProperty(propertyIri)
                        .someOrFail(new RuntimeException(s"Property $propertyIri missing from cache after update"))
                        .orDie
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
