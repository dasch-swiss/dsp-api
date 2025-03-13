/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api.service
import sttp.model.MediaType
import zio.*

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.ontology.api.LastModificationDate
import org.knora.webapi.slice.ontology.api.OntologyV2RequestParser
import org.knora.webapi.slice.ontology.domain.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.api.model.IriDto

final case class OntologiesRestService(
  private val auth: AuthorizationRestService,
  private val iriConverter: IriConverter,
  private val ontologiesRepo: OntologyRepo,
  private val ontologyResponder: OntologyResponderV2,
  private val requestParser: OntologyV2RequestParser,
  private val renderer: KnoraResponseRenderer,
) {

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
    uuid <- Random.nextUUID()
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
    result      <- ontologyResponder.deletePropertyComment(propertyIri, lastModificationDate.value, uuid, user)
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

  def getProperties(user: User)(
    propertyIris: List[String],
    allLanguages: Boolean,
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = for {
    propertyIris <- ZIO.foreach(propertyIris.toSet)(iriConverter.asPropertyIri).mapError(BadRequestException.apply)
    _ <- ZIO
           .fail(BadRequestException(s"Only one ontology may be queried at once"))
           .when(propertyIris.map(_.ontologyIri).size != 1)
    schemasFromProperties = propertyIris.flatMap(_.smartIri.getOntologySchema)
    formatOptions <- ZIO
                       .fail(BadRequestException(s"Only one ontology schema may be queried at once"))
                       .when(schemasFromProperties.size != 1)
                       .as(formatOptions.copy(schema = schemasFromProperties.head.asInstanceOf[ApiV2Schema]))
    result   <- ontologyResponder.getPropertiesFromOntologyV2(propertyIris, allLanguages, user)
    response <- renderer.render(result, formatOptions)
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
  val layer = ZLayer.derive[OntologiesRestService]
}
