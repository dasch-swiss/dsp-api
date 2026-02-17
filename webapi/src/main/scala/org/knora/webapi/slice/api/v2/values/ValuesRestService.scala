/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import sttp.model.MediaType
import zio.Clock
import zio.Random
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.*
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.responders.v2.ResourceUtilV2
import org.knora.webapi.responders.v2.ValuesResponderV2
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.api.v2.ValueUuid
import org.knora.webapi.slice.api.v2.VersionDate
import org.knora.webapi.slice.common.ApiComplexV2JsonLdRequestParser
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.resources.repo.service.ValueRepo
import org.knora.webapi.slice.resources.service.ReadResourcesService

final class ValuesRestService(
  auth: AuthorizationRestService,
  valuesService: ValuesResponderV2,
  readResources: ReadResourcesService,
  requestParser: ApiComplexV2JsonLdRequestParser,
  renderer: KnoraResponseRenderer,
  knoraProjectService: KnoraProjectService,
  resourceUtilV2: ResourceUtilV2,
  valueRepo: ValueRepo,
)(implicit private val stringFormatter: StringFormatter) {

  def getValue(user: User)(
    resourceIri: String,
    valueUuid: ValueUuid,
    versionDate: Option[VersionDate],
    formatOptions: FormatOptions,
  ): Task[(RenderedResponse, MediaType)] =
    render(
      readResources.getResourcesWithDeletedResource(
        Seq(resourceIri),
        None,
        Some(valueUuid.value),
        versionDate,
        withDeleted = true,
        showDeletedValues = false,
        formatOptions.schema,
        formatOptions.rendering,
        user,
      ),
      formatOptions,
    )

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
      validated                             <- validateReorderRequest(request)
      (propertySmartIri, requestedValueIris) = validated

      // Fetch as system user so we see ALL values for canonical verification.
      // This is safe: only value IRIs are compared (verifyCanonicalValues), no content is leaked to the caller.
      resourcesSeq <- readResources.getResources(
                        Seq(request.resourceIri),
                        targetSchema = ApiV2Complex,
                        schemaOptions = Set.empty,
                        requestingUser = KnoraSystemInstances.Users.SystemUser,
                      )
      resourceInfo <- ZIO
                        .fromOption(resourcesSeq.resources.headOption)
                        .orElseFail(NotFoundException(s"Resource <${request.resourceIri}> not found."))

      // Check that the user has modify permission on the resource
      _ <- resourceUtilV2.checkResourcePermission(resourceInfo, Permission.ObjectAccess.Modify, user)

      // Verify requested value IRIs match the canonical non-deleted values for this property
      _ <- verifyCanonicalValues(request, propertySmartIri, requestedValueIris, resourceInfo)

      // Derive project data graph and execute the reorder
      projectDataGraph = ProjectService.projectDataNamedGraphV2(resourceInfo.projectADM)
      now             <- Clock.instant
      _               <- valueRepo.reorderValues(projectDataGraph, InternalIri(request.resourceIri), requestedValueIris, now)
    } yield ReorderValuesResponse(
      resourceIri = request.resourceIri,
      propertyIri = request.propertyIri,
      valuesReordered = requestedValueIris.size,
    )

  private def validateReorderRequest(request: ReorderValuesRequest) =
    for {
      _ <- ZIO
             .attempt(request.resourceIri.toSmartIri)
             .mapError(_ => BadRequestException(s"Invalid resource IRI: <${request.resourceIri}>"))
      propertySmartIri <- ZIO
                            .attempt(request.propertyIri.toSmartIri)
                            .mapError(_ => BadRequestException(s"Invalid property IRI: <${request.propertyIri}>"))
      _ <- ZIO.when(request.orderedValueIris.isEmpty)(
             ZIO.fail(BadRequestException("Value list must not be empty.")),
           )
      _ <- ZIO.when(request.orderedValueIris.distinct.size != request.orderedValueIris.size)(
             ZIO.fail(BadRequestException("Duplicate value IRIs in request.")),
           )
      requestedValueIris <- ZIO.foreach(request.orderedValueIris) { iriStr =>
                              ZIO
                                .fromEither(ValueIri.from(iriStr.toSmartIri))
                                .mapError(e => BadRequestException(s"Invalid value IRI: $e"))
                            }
    } yield (propertySmartIri, requestedValueIris)

  private def verifyCanonicalValues(
    request: ReorderValuesRequest,
    propertySmartIri: SmartIri,
    requestedValueIris: List[ValueIri],
    resourceInfo: ReadResourceV2,
  ): Task[Unit] = {
    // ReadResourceV2.values uses internal ontology IRIs as keys
    val propertyInternalIri = propertySmartIri.toOntologySchema(InternalSchema)
    val canonicalValues     = resourceInfo.values.getOrElse(propertyInternalIri, Seq.empty).filter(_.deletionInfo.isEmpty)
    val canonicalIris       = canonicalValues.map(_.valueIri).toSet
    val requestedIris       = requestedValueIris.map(_.toString).toSet
    ZIO
      .when(canonicalIris != requestedIris)(
        ZIO.fail(
          BadRequestException(
            s"The provided value IRIs do not match the existing values for property <${request.propertyIri}> on resource <${request.resourceIri}>. " +
              s"Expected ${canonicalIris.size} value(s), got ${requestedIris.size}. " +
              s"Missing: ${(canonicalIris -- requestedIris).mkString(", ")}. " +
              s"Extra: ${(requestedIris -- canonicalIris).mkString(", ")}.",
          ),
        ),
      )
      .unit
  }

  private def render(task: Task[KnoraResponseV2], formatOptions: FormatOptions): Task[(RenderedResponse, MediaType)] =
    task.flatMap(renderer.render(_, formatOptions))

  private def render(resp: KnoraResponseV2): Task[(RenderedResponse, MediaType)] =
    renderer.render(resp, FormatOptions.default)

  def eraseValue(user: User)(jsonLd: String): Task[(RenderedResponse, MediaType)] =
    for {
      eraseReq <- requestParser.eraseValueV2FromJsonLd(jsonLd).mapError(BadRequestException.apply)
      _        <- auth.ensureSystemAdmin(user)
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
      _        <- auth.ensureSystemAdmin(user)
      project  <- knoraProjectService
                   .findByShortcode(eraseReq.shortcode)
                   .orDie
                   .someOrFail(NotFoundException(s"Project with shortcode ${eraseReq.shortcode.value} not found."))
      knoraResponse <- valuesService.eraseValueHistory(eraseReq, user, project)
      response      <- render(knoraResponse)
    } yield response
}

object ValuesRestService {
  val layer = ZLayer.derive[ValuesRestService]
}
