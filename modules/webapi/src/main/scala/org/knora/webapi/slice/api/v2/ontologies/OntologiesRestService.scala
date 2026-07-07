/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.ontologies

import sttp.model.MediaType
import zio.*

import scala.annotation.unused

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.api.v2.ontologies.LastModificationDate
import org.knora.webapi.slice.api.v2.ontologies.OntologyV2RequestParser
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyCacheHelpers
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo

final class OntologiesRestService(
  auth: AuthorizationRestService,
  iriConverter: IriConverter,
  ontologiesRepo: OntologyRepo,
  ontologyResponder: OntologyResponderV2,
  ontologyCacheHelpers: OntologyCacheHelpers,
  restCardinalityService: RestCardinalityService,
  requestParser: OntologyV2RequestParser,
  renderer: KnoraResponseRenderer,
  sf: StringFormatter,
  appConfig: AppConfig,
) {

  def dereferenceOntologyIri(user: User)(
    @unused ignored: List[String],
    allLanguages: Boolean,
    formatOptions: FormatOptions,
    serverUri: sttp.model.Uri,
  ): Task[(RenderedResponse, MediaType)] = {
    val urlPath = serverUri.pathToString
    for {
      iri <- if (sf.isBuiltInApiV2OntologyUrlPath(urlPath)) {
               ZIO.succeed(OntologyConstants.KnoraApi.ApiOntologyHostname + urlPath)
             } else if (sf.isProjectSpecificApiV2OntologyUrlPath(urlPath)) {
               ZIO.succeed("http://" + appConfig.knoraApi.externalOntologyIriHostAndPort + urlPath)
             } else {
               ZIO.fail(BadRequestException(s"Invalid or unknown URL path for external ontology: $urlPath"))
             }
      ontologyIri  <- iriConverter.asOntologyIri(iri).mapError(BadRequestException.apply)
      targetSchema <-
        ZIO
          .fromOption(ontologyIri.smartIri.getOntologySchema.collect { case schema: ApiV2Schema => schema })
          .orElseFail(BadRequestException(s"Invalid external ontology IRI: ${serverUri.toString}"))
      result   <- ontologyResponder.getOntologyEntitiesV2(ontologyIri, allLanguages, user)
      response <- renderer.render(result, formatOptions.copy(schema = targetSchema))
    } yield response
  }

  def changeOntologyMetadata(user: User)(
    jsonLd: String,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    uuid   <- Random.nextUUID()
    req    <- requestParser.changeOntologyMetadataRequestV2(jsonLd, uuid, user).mapError(BadRequestException.apply)
    result <- ontologyResponder.changeOntologyMetadata(
                req.ontologyIri,
                req.label,
                req.comment,
                req.lastModificationDate,
                uuid,
                user,
              )
    response <- renderer.render(result, formatOptions)
  } yield response

  def getOntologyMetadataByProjectOption(
    projectIri: Option[ProjectIri],
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] =
    getOntologyMetadataBy(projectIri.toSet, formatOptions)

  def getOntologyMetadataByProjects(
    projectIris: List[String],
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = ZIO
    .foreach(projectIris.toSet)(iri => ZIO.fromEither(ProjectIri.from(iri)).mapError(BadRequestException.apply))
    .flatMap(projectIris => getOntologyMetadataBy(projectIris, formatOptions))

  private def getOntologyMetadataBy(projectIris: Set[ProjectIri], formatOptions: FormatOptions) = for {
    result   <- ontologyResponder.getOntologyMetadataForProjects(projectIris)
    response <- renderer.render(result, formatOptions)
  } yield response

  def getOntologyEntities(user: User)(
    ontologyIriDto: IriDto,
    allLanguages: Boolean,
    opts: FormatOptions,
  ): Task[(String, MediaType)] = for {
    ontologyIri <-
      iriConverter
        .asOntologyIri(ontologyIriDto.value)
        .mapError(BadRequestException.apply)
        .filterOrFail(_.isExternal)(BadRequestException(s"Invalid external ontology IRI: ${ontologyIriDto.value}"))
    targetSchema <- ZIO
                      .fromOption(ontologyIri.smartIri.getOntologySchema.collect { case schema: ApiV2Schema => schema })
                      .orElseFail(BadRequestException(s"Invalid external ontology IRI: ${ontologyIriDto.value}"))
    result  <- ontologyResponder.getOntologyEntitiesV2(ontologyIri, allLanguages, user)
    response = result.format(opts.copy(schema = targetSchema), appConfig)
  } yield (response, MediaType("application", "ld+json"))

  def createClass(user: User)(
    jsonLd: String,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    uuid      <- Random.nextUUID()
    createReq <- requestParser.createClassRequestV2(jsonLd, uuid, user).mapError(BadRequestException.apply)
    result    <- ontologyResponder.createClass(createReq)
    response  <- renderer.render(result, formatOptions)
  } yield response

  def changeClassLabelsOrComments(user: User)(
    jsonLd: String,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    uuid      <- Random.nextUUID()
    updateReq <-
      requestParser.changeClassLabelsOrCommentsRequestV2(jsonLd, uuid, user).mapError(BadRequestException.apply)
    result   <- ontologyResponder.changeClassLabelsOrComments(updateReq)
    response <- renderer.render(result, formatOptions)
  } yield response

  def deleteClassComment(user: User)(
    classIri: IriDto,
    lastModificationDate: LastModificationDate,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    classIri <- iriConverter.asResourceClassIri(classIri.value).mapError(BadRequestException.apply)
    uuid     <- Random.nextUUID()
    result   <- ontologyResponder.deleteClassComment(classIri, lastModificationDate, uuid, user)
    response <- renderer.render(result, formatOptions)
  } yield response

  def addCardinalities(user: User)(
    jsonLd: String,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    uuid      <- Random.nextUUID()
    createReq <- requestParser.addCardinalitiesToClassRequestV2(jsonLd, uuid, user).mapError(BadRequestException.apply)
    result    <- ontologyResponder.addCardinalitiesToClass(createReq)
    response  <- renderer.render(result, formatOptions)
  } yield response

  def canChangeCardinality(
    user: User,
  )(
    classIri: IriDto,
    propertyIri: Option[IriDto],
    newCardinality: Option[String],
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    result <-
      restCardinalityService.canChangeCardinality(classIri.value, user, propertyIri.map(_.value), newCardinality)
    response <- renderer.render(result, formatOptions)
  } yield response

  def replaceCardinalities(
    user: User,
  )(jsonLd: String, formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] = for {
    uuid      <- Random.nextUUID()
    updateReq <-
      requestParser.replaceClassCardinalitiesRequestV2(jsonLd, uuid, user).mapError(BadRequestException.apply)
    result   <- ontologyResponder.replaceClassCardinalities(updateReq)
    response <- renderer.render(result, formatOptions)
  } yield response

  def canDeleteCardinalitiesFromClass(
    user: User,
  )(jsonLd: String, formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] = for {
    uuid      <- Random.nextUUID()
    updateReq <-
      requestParser.canDeleteCardinalitiesFromClassRequestV2(jsonLd, uuid, user).mapError(BadRequestException.apply)
    result   <- ontologyResponder.canDeleteCardinalitiesFromClass(updateReq)
    response <- renderer.render(result, formatOptions)
  } yield response

  def deleteCardinalitiesFromClass(
    user: User,
  )(jsonLd: String, formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] = for {
    uuid      <- Random.nextUUID()
    updateReq <-
      requestParser.deleteCardinalitiesFromClassRequestV2(jsonLd, uuid, user).mapError(BadRequestException.apply)
    result   <- ontologyResponder.deleteCardinalitiesFromClass(updateReq)
    response <- renderer.render(result, formatOptions)
  } yield response

  def changeGuiOrder(user: User)(jsonLd: String, formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] =
    for {
      uuid      <- Random.nextUUID()
      updateReq <- requestParser.changeGuiOrderRequestV2(jsonLd, uuid, user).mapError(BadRequestException.apply)
      result    <- ontologyResponder.changeGuiOrder(updateReq)
      response  <- renderer.render(result, formatOptions)
    } yield response

  def findClassByIri(user: User)(
    classIriDto: IriDto,
    allLanguages: Boolean,
    formatOptions: FormatOptions,
  ) = for {
    classIri <- iriConverter.asResourceClassIri(classIriDto.value).mapError(BadRequestException.apply)
    schema   <- ZIO
                .fromOption(classIri.ontologySchema.collect { case s: ApiV2Schema => s })
                .orElseFail(BadRequestException(s"Class IRI must have an API V2 schema: $classIriDto"))
    result   <- ontologyCacheHelpers.getClassAsReadOntologyV2(classIri, allLanguages, user)
    response <- renderer.render(result, formatOptions.copy(schema = schema))
  } yield response

  def canDeleteClass(user: User)(
    resourceClassIri: IriDto,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    classIri <- iriConverter.asResourceClassIriApiV2Complex(resourceClassIri.value).mapError(BadRequestException.apply)
    result   <- ontologyResponder.canDeleteClass(classIri, user)
    response <- renderer.render(result, formatOptions)
  } yield response

  def deleteClass(user: User)(
    resourceClassIri: IriDto,
    lastModificationDate: LastModificationDate,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    classIri <-
      iriConverter
        .asResourceClassIriApiV2Complex(resourceClassIri.value)
        .mapError(BadRequestException.apply)
        .filterOrFail(_.ontologyIri.isExternal)(BadRequestException("Only external ontologies can be modified"))
    uuid     <- Random.nextUUID()
    result   <- ontologyResponder.deleteClass(classIri, lastModificationDate.value, uuid, user)
    response <- renderer.render(result, formatOptions)
  } yield response

  def deleteOntologyComment(user: User)(
    ontologyIri: IriDto,
    lastModificationDate: LastModificationDate,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    uuid        <- Random.nextUUID()
    ontologyIri <- iriConverter
                     .asOntologyIriApiV2Complex(ontologyIri.value)
                     .mapError(BadRequestException.apply)
                     .filterOrFail(_.isExternal)(BadRequestException("Only external ontologies can have comments"))
    result   <- ontologyResponder.deleteOntologyComment(ontologyIri, lastModificationDate.value, uuid, user)
    response <- renderer.render(result, formatOptions)
  } yield response

  def createProperty(user: User)(
    jsonLd: String,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    uuid      <- Random.nextUUID()
    createReq <- requestParser.createPropertyRequestV2(jsonLd, uuid, user).mapError(BadRequestException.apply)
    result    <- ontologyResponder.createProperty(createReq)
    response  <- renderer.render(result, formatOptions)
  } yield response

  def changePropertyLabelsOrComments(user: User)(
    jsonLd: String,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    uuid      <- Random.nextUUID()
    updateReq <- requestParser
                   .changePropertyLabelsOrCommentsRequestV2(jsonLd, uuid, user)
                   .mapError(BadRequestException.apply)
    result   <- ontologyResponder.changePropertyLabelsOrComments(updateReq)
    response <- renderer.render(result, formatOptions)
  } yield response

  def deletePropertyComment(user: User)(
    propertyIri: IriDto,
    lastModificationDate: LastModificationDate,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    propertyIri <- iriConverter.asPropertyIri(propertyIri.value).mapError(BadRequestException.apply)
    uuid        <- Random.nextUUID()
    result      <- ontologyResponder.deletePropertyComment(propertyIri, lastModificationDate, uuid, user)
    response    <- renderer.render(result, formatOptions)
  } yield response

  def changePropertyGuiElement(
    user: User,
  )(jsonLd: String, formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] = for {
    uuid      <- Random.nextUUID()
    updateReq <- requestParser.changePropertyGuiElementRequest(jsonLd, uuid, user).mapError(BadRequestException.apply)
    result    <- ontologyResponder.changePropertyGuiElement(updateReq)
    response  <- renderer.render(result, formatOptions)
  } yield response

  def findPropertyByIri(user: User)(
    propertyIri: IriDto,
    allLanguages: Boolean,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    propertyIri <- iriConverter.asPropertyIri(propertyIri.value).mapError(BadRequestException.apply)
    schema      <- ZIO
                .fromOption(propertyIri.ontologySchema.collect { case s: ApiV2Schema => s })
                .orElseFail(BadRequestException(s"Property IRI must have an API V2 schema: $propertyIri"))
    result   <- ontologyResponder.getPropertyFromOntologyV2(propertyIri, allLanguages, user)
    response <- renderer.render(result, formatOptions.copy(schema = schema))
  } yield response

  def canDeleteProperty(
    user: User,
  )(propertyIri: IriDto, formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] = for {
    propertyIri <- iriConverter.asPropertyIri(propertyIri.value).mapError(BadRequestException.apply)
    result      <- ontologyResponder.canDeleteProperty(propertyIri, user)
    response    <- renderer.render(result, formatOptions)
  } yield response

  def deleteProperty(user: User)(
    propertyIri: IriDto,
    formatOptions: FormatOptions,
    lastModificationDate: LastModificationDate,
  ): Task[(RenderedResponse, MediaType)] = for {
    propertyIri <- iriConverter.asPropertyIri(propertyIri.value).mapError(BadRequestException.apply)
    uuid        <- Random.nextUUID()
    result      <- ontologyResponder.deleteProperty(propertyIri, lastModificationDate.value, uuid, user)
    response    <- renderer.render(result, formatOptions)
  } yield response

  def createOntology(user: User)(jsonLd: String, formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] =
    for {
      uuid      <- Random.nextUUID()
      createReq <- requestParser.createOntologyRequestV2(jsonLd, uuid, user).mapError(BadRequestException.apply)
      _         <- auth.ensureSystemAdminOrProjectAdminById(user, createReq.projectIri)
      result    <- ontologyResponder.createOntology(createReq)
      response  <- renderer.render(result, formatOptions)
    } yield response

  def deleteOntology(user: User)(
    ontologyIri: IriDto,
    formatOptions: FormatOptions,
    lastModificationDate: LastModificationDate,
  ): Task[(RenderedResponse, MediaType)] = for {
    onto     <- ensureOntologyExists(ontologyIri)
    _        <- ensureProjectAdmin(onto, user)
    uuid     <- Random.nextUUID()
    result   <- ontologyResponder.deleteOntology(onto.ontologyIri, lastModificationDate.value, uuid)
    response <- renderer.render(result, formatOptions)
  } yield response

  def canDeleteOntology(
    user: User,
  )(ontologyIri: IriDto, formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] = for {
    ontologyIri <- asOntologyIri(ontologyIri)
    result      <- ontologyResponder.canDeleteOntology(ontologyIri, user)
    response    <- renderer.render(result, formatOptions)
  } yield response

  private def ensureOntologyExists(ontologyIri: IriDto) = for {
    ontologyIri <- iriConverter.asOntologyIri(ontologyIri.value).mapError(BadRequestException.apply)
    onto        <- ontologiesRepo.findById(ontologyIri).someOrFail(NotFoundException.notfound(ontologyIri))
  } yield onto

  private def asOntologyIri(iri: IriDto) = iriConverter.asOntologyIri(iri.value).mapError(BadRequestException.apply)

  private def ensureProjectAdmin(onto: ReadOntologyV2, user: User) = for {
    projectIri <- ZIO.fromOption(onto.projectIri).orElseFail(NotFoundException.notfound(onto.ontologyIri))
    _          <- auth.ensureSystemAdminOrProjectAdminById(user, projectIri)
  } yield ()
}

object OntologiesRestService {
  private[ontologies] val layer = ZLayer.derive[OntologiesRestService]
}
