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
import dsp.errors.InternalServerException
import dsp.errors.RequestRejectedException
import dsp.valueobjects.Iri._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.http.handler.ExceptionHandlerZ
import org.knora.webapi.http.middleware.AuthenticationMiddleware
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectCreatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectUpdatePayloadADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.routing.RouteUtilZ
import org.knora.webapi.slice.admin.api.service.ProjectADMREstService

final case class ProjectsRouteZ(
  appConfig: AppConfig,
  projectsService: ProjectADMREstService,
  authenticationMiddleware: AuthenticationMiddleware
) {

  lazy val route: HttpApp[Any, Nothing] = projectRoutes @@ authenticationMiddleware.authenticationMiddleware

  private val projectRoutes: Http[Any, Nothing, (Request, UserADM), Response] =
    Http
      .collectZIO[(Request, UserADM)] {
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
        case (Method.GET -> !! / "admin" / "projects" / "Keywords", _) => getKeywords()
        case (Method.GET -> !! / "admin" / "projects" / "iri" / iriUrlEncoded / "Keywords", _) =>
          getKeywordsByProjectIri(iriUrlEncoded)
        case (Method.GET -> !! / "admin" / "projects" / "iri" / iriUrlEncoded / "RestrictedViewSettings", _) =>
          getRestrictedViewSettingsByProjectIri(iriUrlEncoded)
        case (Method.GET -> !! / "admin" / "projects" / "shortname" / shortname / "RestrictedViewSettings", _) =>
          getRestrictedViewSettingsByShortname(shortname)
        case (Method.GET -> !! / "admin" / "projects" / "shortcode" / shortcode / "RestrictedViewSettings", _) =>
          getRestrictedViewSettingsByShortcode(shortcode)
      }
      .catchAll {
        case RequestRejectedException(e) => ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
        case InternalServerException(e)  => ExceptionHandlerZ.exceptionToJsonHttpResponseZ(e, appConfig)
      }

  private def getProjects(): Task[Response] =
    for {
      r <- projectsService.getProjectsADMRequest()
    } yield Response.json(r.toJsValue.toString)

  private def getProjectByIri(iriUrlEncoded: String): Task[Response] =
    for {
      iriDecoded    <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      iriIdentifier <- IriIdentifier.fromString(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      r             <- projectsService.getSingleProjectADMRequest(iriIdentifier)
    } yield Response.json(r.toJsValue.toString)

  private def getProjectByShortname(shortname: String): Task[Response] =
    for {
      shortnameIdentifier <- ShortnameIdentifier.fromString(shortname).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.getSingleProjectADMRequest(shortnameIdentifier)
    } yield Response.json(r.toJsValue.toString)

  private def getProjectByShortcode(shortcode: String): Task[Response] =
    for {
      shortcodeIdentifier <- ShortcodeIdentifier.fromString(shortcode).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.getSingleProjectADMRequest(shortcodeIdentifier)
    } yield Response.json(r.toJsValue.toString())

  private def createProject(request: Request, requestingUser: UserADM): Task[Response] =
    for {
      body    <- request.body.asString
      payload <- ZIO.fromEither(body.fromJson[ProjectCreatePayloadADM]).mapError(e => BadRequestException(e))
      r       <- projectsService.createProjectADMRequest(payload, requestingUser)
    } yield Response.json(r.toJsValue.toString)

  private def deleteProject(iriUrlEncoded: String, requestingUser: UserADM): Task[Response] =
    for {
      iriDecoded <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      projectIri <- ProjectIri.make(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      r          <- projectsService.deleteProject(projectIri, requestingUser)
    } yield Response.json(r.toJsValue.toString())

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

  private def getProjectMembersByIri(iriUrlEncoded: String, requestingUser: UserADM): Task[Response] =
    for {
      iriDecoded    <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      iriIdentifier <- IriIdentifier.fromString(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      r             <- projectsService.getProjectMembers(iriIdentifier, requestingUser)
    } yield Response.json(r.toJsValue.toString)

  private def getProjectMembersByShortname(shortname: String, requestingUser: UserADM): Task[Response] =
    for {
      shortnameIdentifier <- ShortnameIdentifier.fromString(shortname).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.getProjectMembers(shortnameIdentifier, requestingUser)
    } yield Response.json(r.toJsValue.toString)

  private def getProjectMembersByShortcode(shortcode: String, requestingUser: UserADM): Task[Response] =
    for {
      shortcodeIdentifier <- ShortcodeIdentifier.fromString(shortcode).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.getProjectMembers(shortcodeIdentifier, requestingUser)
    } yield Response.json(r.toJsValue.toString())

  private def getProjectAdminsByIri(iriUrlEncoded: String, requestingUser: UserADM): Task[Response] =
    for {
      iriDecoded    <- RouteUtilZ.urlDecode(iriUrlEncoded, s"Failed to URL decode IRI parameter $iriUrlEncoded.")
      iriIdentifier <- IriIdentifier.fromString(iriDecoded).toZIO.mapError(e => BadRequestException(e.msg))
      r             <- projectsService.getProjectAdmins(iriIdentifier, requestingUser)
    } yield Response.json(r.toJsValue.toString)

  private def getProjectAdminsByShortname(shortname: String, requestingUser: UserADM): Task[Response] =
    for {
      shortnameIdentifier <- ShortnameIdentifier.fromString(shortname).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.getProjectAdmins(shortnameIdentifier, requestingUser)
    } yield Response.json(r.toJsValue.toString)

  private def getProjectAdminsByShortcode(shortcode: String, requestingUser: UserADM): Task[Response] =
    for {
      shortcodeIdentifier <- ShortcodeIdentifier.fromString(shortcode).toZIO.mapError(e => BadRequestException(e.msg))
      r                   <- projectsService.getProjectAdmins(shortcodeIdentifier, requestingUser)
    } yield Response.json(r.toJsValue.toString())

  private def getKeywords(): Task[Response] =
    for {
      r <- projectsService.getKeywords()
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

}

object ProjectsRouteZ {
  val layer: URLayer[AppConfig with ProjectADMREstService with AuthenticationMiddleware, ProjectsRouteZ] =
    ZLayer.fromFunction(ProjectsRouteZ.apply _)
}
