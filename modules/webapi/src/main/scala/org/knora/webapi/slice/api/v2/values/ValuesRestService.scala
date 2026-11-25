/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import sttp.model.MediaType
import zio.IO
import zio.Random
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v2.StoredXmlServing
import org.knora.webapi.slice.api.v2.ValueUuid
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.resources.service.ReadResourcesService

final class ValuesRestService(
  valuesService: ValuesResponderV2,
  readResources: ReadResourcesService,
  requestParser: ApiComplexV2JsonLdRequestParser,
  renderer: KnoraResponseRenderer,
  knoraProjectService: KnoraProjectService,
  storedXmlServing: StoredXmlServing,
)(implicit private val stringFormatter: StringFormatter) {

  def getValue(user: User)(
    resourceIri: String,
    valueUuid: ValueUuid,
    versionDate: Option[VersionDate],
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] = {
    val readOptions = storedXmlServing(formatOptions)
    for {
      resIri   <- parseResourceIri(resourceIri)
      response <- render(
                    readResources.getResourcesWithDeletedResource(
                      Seq(resIri),
                      None,
                      Some(valueUuid.value),
                      versionDate,
                      withDeleted = true,
                      showDeletedValues = false,
                      readOptions.schema,
                      readOptions.rendering,
                      user,
                    ),
                    formatOptions,
                  )
    } yield response
  }

  def createValue(user: User)(jsonLd: String): Task[(RenderedResponse, MediaType)] =
    for {
      valueToCreate <- requestParser.createValueV2FromJsonLd(jsonLd).mapError(BadRequestException.apply)
      apiRequestId  <- Random.nextUUID
      knoraResponse <- valuesService.createValueV2(valueToCreate, user, apiRequestId)
      response      <- render(knoraResponse)
    } yield response

  def updateValue(user: User)(jsonLd: String): Task[(RenderedResponse, MediaType)] =
    for {
      valueToUpdate <- requestParser.updateValueV2fromJsonLd(jsonLd).mapError(BadRequestException.apply)
      apiRequestId  <- Random.nextUUID
      knoraResponse <- valuesService.updateValueV2(valueToUpdate, user, apiRequestId)
      response      <- render(knoraResponse)
    } yield response

  def deleteValue(user: User)(jsonLd: String): Task[(RenderedResponse, MediaType)] =
    for {
      valueToDelete <- requestParser.deleteValueV2FromJsonLd(jsonLd).mapError(BadRequestException.apply)
      knoraResponse <- valuesService.deleteValueV2(valueToDelete, user)
      response      <- render(knoraResponse)
    } yield response

  def reorderValues(user: User)(request: ReorderValuesRequest): Task[ReorderValuesResponse] =
    for {
      resIri                        <- parseResourceIri(request.resourceIri)
      validated                     <- validateReorderRequest(request)
      (property, requestedValueIris) = validated
      apiRequestId                  <- Random.nextUUID
      reordered                     <- valuesService.reorderValues(resIri, property, requestedValueIris, user, apiRequestId)
    } yield ReorderValuesResponse(
      resourceIri = resIri.value,
      propertyIri = request.propertyIri,
      valuesReordered = reordered,
    )

  private def validateReorderRequest(request: ReorderValuesRequest) =
    for {
      property <- ZIO
                    .attempt(request.propertyIri.toSmartIri)
                    .flatMap(iri => ZIO.fromEither(PropertyIri.fromApiV2Complex(iri)))
                    .mapError(_ => BadRequestException(s"Invalid property IRI: <${request.propertyIri}>"))
      _ <- ZIO.when(request.orderedValueIris.isEmpty)(
             ZIO.fail(BadRequestException("Value list must not be empty.")),
           )
      _ <- ZIO.when(request.orderedValueIris.distinct.size != request.orderedValueIris.size)(
             ZIO.fail(BadRequestException("Duplicate value IRIs in request.")),
           )
      requestedValueIris <- ZIO.foreach(request.orderedValueIris) { iriStr =>
                              ZIO
                                .fromEither(ValueIri.from(iriStr))
                                .mapError(e => BadRequestException(s"Invalid value IRI: $e"))
                            }
    } yield (property, requestedValueIris)

  private def render(task: Task[KnoraResponseV2], formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] =
    task.flatMap(renderer.render(_, formatOptions))

  private def render(resp: KnoraResponseV2): Task[(RenderedResponse, MediaType)] =
    renderer.render(resp, FormatOptions.default)

  def eraseValue(user: User)(jsonLd: String): Task[(RenderedResponse, MediaType)] =
    for {
      eraseReq <- requestParser.eraseValueV2FromJsonLd(jsonLd).mapError(BadRequestException.apply)
      project  <- knoraProjectService
                   .findByShortcode(eraseReq.shortcode)
                   .orDie
                   .someOrFail(NotFoundException(s"Project with shortcode ${eraseReq.shortcode.value} not found."))
      knoraResponse <- valuesService.eraseValue(eraseReq, user, project)
      response      <- render(knoraResponse)
    } yield response

  def eraseValueHistory(user: User)(jsonLd: String): Task[(RenderedResponse, MediaType)] =
    for {
      eraseReq <- requestParser.eraseValueHistoryV2FromJsonLd(jsonLd).mapError(BadRequestException.apply)
      project  <- knoraProjectService
                   .findByShortcode(eraseReq.shortcode)
                   .orDie
                   .someOrFail(NotFoundException(s"Project with shortcode ${eraseReq.shortcode.value} not found."))
      knoraResponse <- valuesService.eraseValueHistory(eraseReq, user, project)
      response      <- render(knoraResponse)
    } yield response

  private def parseResourceIri(iri: String): IO[BadRequestException, ResourceIri] =
    ZIO.fromEither(ResourceIri.from(iri)).mapError(BadRequestException.apply)
}

object ValuesRestService {
  val layer = ZLayer.derive[ValuesRestService]
}
