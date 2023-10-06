/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import zio._
import zio.http._
import zio.http.model._
import zio.json._
import zio.stream.ZStream

import java.nio.file.Files

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri._
import dsp.valueobjects.RestrictedViewSize
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.handler.ExceptionHandlerZ
import org.knora.webapi.http.middleware.AuthenticationMiddleware
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectCreatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectSetRestrictedViewSizePayload
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectUpdatePayloadADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService

final case class ProjectsRouteZ(
  appConfig: AppConfig,
  projectsService: ProjectADMRestService,
  authenticationMiddleware: AuthenticationMiddleware
) {

  lazy val route: HttpApp[Any, Nothing] = projectRoutes @@ authenticationMiddleware.authenticationMiddleware

  private val projectRoutes: Http[Any, Nothing, (Request, UserADM), Response] =
    Http
      .collectZIO[(Request, UserADM)] {
        // project crud
        case (Method.GET -> !! / "admin" / "projects", _)                           => getProjects()
        case (Method.GET -> !! / "admin" / "projects" / "iri" / iriUrlEncoded, _)   => getProjectByIri(iriUrlEncoded)
        case (Method.GET -> !! / "admin" / "projects" / "shortname" / shortname, _) => getProjectByShortname(shortname)
        case (Method.GET -> !! / "admin" / "projects" / "shortcode" / shortcode, _) => getProjectByShortcode(shortcode)
        case (request @ Method.POST -> !! / "admin" / "projects", requestingUser) =>
          createProject(request, requestingUser)
        case (Method.DELETE -> !! / "admin" / "projects" / "iri" / iriUrlEncoded, requestingUser) =>
          deleteProject(iriUrlEncoded, requestingUser)
        case (request @ Method.PUT -> !! / "admin" / "projects" / "iri" / iriUrlEncoded, requestingUser) =>
          updateProject(iriUrlEncoded, request, requestingUser)
        case (Method.GET -> !! / "admin" / "projects" / "iri" / iriUrlEncoded / "AllData", requestingUser) =>
          getAllProjectData(iriUrlEncoded, requestingUser)

        // export/import endpoints
        case (Method.GET -> !! / "admin" / "projects" / "exports", requestingUser) =>
          getProjectExports(requestingUser)
        case (Method.POST -> !! / "admin" / "projects" / "shortcode" / shortcode / "import", requestingUser) =>
          postImportProject(shortcode, requestingUser)
        case (Method.POST -> !! / "admin" / "projects" / "shortcode" / shortcode / "export", requestingUser) =>
          postExportProject(shortcode, requestingUser)

        // project members
        case (Method.GET -> !! / "admin" / "projects" / "iri" / iriUrlEncoded / "members", requestingUser) =>
          getProjectMembersByIri(iriUrlEncoded, requestingUser)
        case (Method.GET -> !! / "admin" / "projects" / "shortname" / shortname / "members", requestingUser) =>
          getProjectMembersByShortname(shortname, requestingUser)
        case (Method.GET -> !! / "admin" / "projects" / "shortcode" / shortcode / "members", requestingUser) =>
          getProjectMembersByShortcode(shortcode, requestingUser)
        case (Method.GET -> !! / "admin" / "projects" / "iri" / iriUrlEncoded / "admin-members", requestingUser) =>
          getProjectAdminsByIri(iriUrlEncoded, requestingUser)
        case (Method.GET -> !! / "admin" / "projects" / "shortname" / shortname / "admin-members", requestingUser) =>
          getProjectAdminsByShortname(shortname, requestingUser)
        case (Method.GET -> !! / "admin" / "projects" / "shortcode" / shortcode / "admin-members", requestingUser) =>
          getProjectAdminsByShortcode(shortcode, requestingUser)

        // keywords
        case (Method.GET -> !! / "admin" / "projects" / "Keywords", _) => getKeywords()
        case (Method.GET -> !! / "admin" / "projects" / "iri" / iriUrlEncoded / "Keywords", _) =>
          getKeywordsByProjectIri(iriUrlEncoded)

        // view settings
        case (Method.GET -> !! / "admin" / "projects" / "iri" / iriUrlEncoded / "RestrictedViewSettings", _) =>
          getRestrictedViewSettingsByProjectIri(iriUrlEncoded)
        case (Method.GET -> !! / "admin" / "projects" / "shortname" / shortname / "RestrictedViewSettings", _) =>
          getRestrictedViewSettingsByShortname(shortname)
        case (Method.GET -> !! / "admin" / "projects" / "shortcode" / shortcode / "RestrictedViewSettings", _) =>
          getRestrictedViewSettingsByShortcode(shortcode)
        case (
              request @ Method.POST -> !! / "admin" / "projects" / "iri" / iri / "RestrictedViewSettings",
              requestingUser
            ) =>
          setProjectRestrictedViewSizeByIri(iri, request.body, requestingUser)
        case (
              request @ Method.POST -> !! / "admin" / "projects" / "shortcode" / shortcode / "RestrictedViewSettings",
              requstingUser
            ) =>
          setProjectRestrictedViewSizeByShortcode(shortcode, request.body, requstingUser)
      }
      .catchAll(ExceptionHandlerZ.exceptionToJsonHttpResponseZ(_, appConfig))

  private def getProjects(): Task[Response] =
    for {
      r <- projectsService.listAllProjects()
    } yield Response.json(r.toJsValue.toString)

  private def getProjectByIri(iriUrlEncoded: String): Task[Response] =
    for {
      iriDecoded    <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      iriIdentifier <- IriIdentifier.fromString(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      r             <- projectsService.findProject(iriIdentifier)
    } yield Response.json(r.toJsValue.toString)

  private def getProjectByShortname(shortname: String): Task[Response] =
    for {
      shortnameIdentifier <- ShortnameIdentifier.fromString(shortname).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.findProject(shortnameIdentifier)
    } yield Response.json(r.toJsValue.toString)

  private def getProjectByShortcode(shortcode: String): Task[Response] =
    for {
      shortcodeIdentifier <- ShortcodeIdentifier.fromString(shortcode).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.findProject(shortcodeIdentifier)
    } yield Response.json(r.toJsValue.toString())

  private def createProject(request: Request, requestingUser: UserADM): Task[Response] =
    for {
      body    <- request.body.asString
      payload <- ZIO.fromEither(body.fromJson[ProjectCreatePayloadADM]).mapError(e => BadRequestException(e))
      r       <- projectsService.createProject(payload, requestingUser)
    } yield Response.json(r.toJsValue.toString)

  private def deleteProject(iriUrlEncoded: String, requestingUser: UserADM): Task[Response] =
    for {
      iriDecoded <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      id         <- IriIdentifier.fromString(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      response   <- projectsService.deleteProject(id, requestingUser)
    } yield Response.json(response.toJsValue.toString())

  private def updateProject(iriUrlEncoded: String, request: Request, requestingUser: UserADM): Task[Response] =
    for {
      iriDecoded <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      projectIri <- ProjectIri.make(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      body       <- request.body.asString
      payload    <- ZIO.fromEither(body.fromJson[ProjectUpdatePayloadADM]).mapError(e => BadRequestException(e))
      r          <- projectsService.updateProject(projectIri, payload, requestingUser)
    } yield Response.json(r.toJsValue.toString)

  private def getAllProjectData(iriUrlEncoded: String, requestingUser: UserADM): Task[Response] =
    for {
      iriDecoded             <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      iriIdentifier          <- IriIdentifier.fromString(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      projectDataGetResponse <- projectsService.getAllProjectData(iriIdentifier, requestingUser)
      filePath                = projectDataGetResponse.projectDataFile
      fileStream = ZStream
                     .fromPath(filePath)
                     .ensuring(
                       ZIO
                         .attempt(Files.deleteIfExists(filePath))
                         .orDie
                         .logError(s"File couldn't be deleted: ${filePath.toString()}")
                     )
      r = Response(
            headers = Headers.contentType("application/trig"),
            body = Body.fromStream(fileStream)
          )
    } yield r

  private def postExportProject(shortcode: String, requestingUser: UserADM): Task[Response] =
    projectsService
      .exportProject(shortcode, requestingUser)
      .as(Response.text("work in progress").setStatus(Status.Accepted))

  private def postImportProject(shortcode: String, requestingUser: UserADM): Task[Response] =
    projectsService.importProject(shortcode, requestingUser).map(_.toJson).map(Response.json(_))

  private def getProjectExports(requestingUser: UserADM): Task[Response] =
    projectsService.listExports(requestingUser).map(_.toJson).map(Response.json(_))

  private def getProjectMembersByIri(iriUrlEncoded: String, requestingUser: UserADM): Task[Response] =
    for {
      iriDecoded    <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      iriIdentifier <- IriIdentifier.fromString(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      r             <- projectsService.getProjectMembers(requestingUser, iriIdentifier)
    } yield Response.json(r.toJsValue.toString)

  private def getProjectMembersByShortname(shortname: String, requestingUser: UserADM): Task[Response] =
    for {
      shortnameIdentifier <- ShortnameIdentifier.fromString(shortname).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.getProjectMembers(requestingUser, shortnameIdentifier)
    } yield Response.json(r.toJsValue.toString)

  private def getProjectMembersByShortcode(shortcode: String, requestingUser: UserADM): Task[Response] =
    for {
      shortcodeIdentifier <- ShortcodeIdentifier.fromString(shortcode).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.getProjectMembers(requestingUser, shortcodeIdentifier)
    } yield Response.json(r.toJsValue.toString())

  private def getProjectAdminsByIri(iriUrlEncoded: String, requestingUser: UserADM): Task[Response] =
    for {
      iriDecoded    <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      iriIdentifier <- IriIdentifier.fromString(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      r             <- projectsService.getProjectAdminMembers(requestingUser, iriIdentifier)
    } yield Response.json(r.toJsValue.toString)

  private def getProjectAdminsByShortname(shortname: String, requestingUser: UserADM): Task[Response] =
    for {
      shortnameIdentifier <- ShortnameIdentifier.fromString(shortname).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.getProjectAdminMembers(requestingUser, shortnameIdentifier)
    } yield Response.json(r.toJsValue.toString)

  private def getProjectAdminsByShortcode(shortcode: String, requestingUser: UserADM): Task[Response] =
    for {
      shortcodeIdentifier <- ShortcodeIdentifier.fromString(shortcode).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.getProjectAdminMembers(requestingUser, shortcodeIdentifier)
    } yield Response.json(r.toJsValue.toString())

  private def getKeywords(): Task[Response] =
    for {
      r <- projectsService.listAllKeywords()
    } yield Response.json(r.toJsValue.toString)

  private def getKeywordsByProjectIri(iriUrlEncoded: String): Task[Response] =
    for {
      iriDecoded <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      projectIri <- ProjectIri.make(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      r          <- projectsService.getKeywordsByProjectIri(projectIri)
    } yield Response.json(r.toJsValue.toString)

  private def getRestrictedViewSettingsByProjectIri(iriUrlEncoded: String): Task[Response] =
    for {
      iriDecoded    <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      iriIdentifier <- IriIdentifier.fromString(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      r             <- projectsService.getProjectRestrictedViewSettings(iriIdentifier)
    } yield Response.json(r.toJsValue.toString)

  private def getRestrictedViewSettingsByShortname(shortname: String): Task[Response] =
    for {
      shortnameIdentifier <- ShortnameIdentifier.fromString(shortname).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.getProjectRestrictedViewSettings(shortnameIdentifier)
    } yield Response.json(r.toJsValue.toString)

  private def getRestrictedViewSettingsByShortcode(shortcode: String): Task[Response] =
    for {
      shortcodeIdentifier <- ShortcodeIdentifier.fromString(shortcode).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.getProjectRestrictedViewSettings(shortcodeIdentifier)
    } yield Response.json(r.toJsValue.toString)

  private def setProjectRestrictedViewSizeByIri(iri: IRI, body: Body, user: UserADM): Task[Response] =
    for {
      iriDecoded <- RouteUtilZ.urlDecode(iri, s"Failed to URL decode IRI parameter $iri.")
      id         <- IriIdentifier.fromString(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      result     <- handleRestrictedViewSizeRequest(id, body, user)
    } yield result

  private def setProjectRestrictedViewSizeByShortcode(shortcode: String, body: Body, user: UserADM): Task[Response] =
    for {
      id     <- ShortcodeIdentifier.fromString(shortcode).toZIO.mapError(e => BadRequestException(e.msg))
      result <- handleRestrictedViewSizeRequest(id, body, user)
    } yield result

  private def handleRestrictedViewSizeRequest(id: ProjectIdentifierADM, body: Body, user: UserADM) =
    for {
      body     <- body.asString
      payload  <- ZIO.fromEither(body.fromJson[ProjectSetRestrictedViewSizePayload]).mapError(BadRequestException(_))
      size     <- ZIO.fromEither(RestrictedViewSize.make(payload.size)).mapError(BadRequestException(_))
      response <- projectsService.updateProjectRestrictedViewSettings(id, user, size)
    } yield Response.json(response.toJson)
}

object ProjectsRouteZ {
  val layer: URLayer[AppConfig with ProjectADMRestService with AuthenticationMiddleware, ProjectsRouteZ] =
    ZLayer.fromFunction(ProjectsRouteZ.apply _)
}
