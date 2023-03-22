/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.Done
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.headers.ContentDispositionTypes
import akka.http.scaladsl.model.headers.`Content-Disposition`
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.stream.IOResult
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Source
import akka.util.ByteString
import zio._
import zio.prelude.Validation

import java.nio.file.Files
import java.util.UUID
import scala.concurrent.Future
import scala.util.Try

import dsp.errors.BadRequestException
import dsp.errors.ValidationException
import dsp.valueobjects.Iri.ProjectIri
import dsp.valueobjects.Project._
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

final case class ProjectsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator]
) extends KnoraRoute(routeData, runtime)
    with ProjectsADMJsonProtocol {

  private val projectsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "projects")

  /**
   * Returns the route.
   */
  override def makeRoute: Route =
    getProjects() ~
      addProject() ~
      getKeywords() ~
      getProjectKeywords() ~
      getProjectByIri() ~
      getProjectByShortname() ~
      getProjectByShortcode() ~
      changeProject() ~
      deleteProject() ~
      getProjectMembersByIri() ~
      getProjectMembersByShortname() ~
      getProjectMembersByShortcode() ~
      getProjectAdminMembersByIri() ~
      getProjectAdminMembersByShortname() ~
      getProjectAdminMembersByShortcode() ~
      getProjectRestrictedViewSettingsByIri() ~
      getProjectRestrictedViewSettingsByShortname() ~
      getProjectRestrictedViewSettingsByShortcode() ~
      getProjectData()

  /**
   * Returns all projects.
   */
  private def getProjects(): Route = path(projectsBasePath) {
    get { requestContext =>
      RouteUtilADM.runJsonRoute(
        requestMessageF = FastFuture.successful(ProjectsGetRequestADM()),
        requestContext = requestContext,
        appActor = appActor,
        log = log
      )
    }
  }

  /**
   * Creates a new project.
   */
  private def addProject(): Route = path(projectsBasePath) {
    post {
      entity(as[CreateProjectApiRequestADM]) { apiRequest => requestContext =>
        val id: Validation[Throwable, Option[ProjectIri]]          = ProjectIri.make(apiRequest.id)
        val shortname: Validation[Throwable, ShortName]            = ShortName.make(apiRequest.shortname)
        val shortcode: Validation[Throwable, ShortCode]            = ShortCode.make(apiRequest.shortcode)
        val longname: Validation[Throwable, Option[Name]]          = Name.make(apiRequest.longname)
        val description: Validation[Throwable, ProjectDescription] = ProjectDescription.make(apiRequest.description)
        val keywords: Validation[Throwable, Keywords]              = Keywords.make(apiRequest.keywords)
        val logo: Validation[Throwable, Option[Logo]]              = Logo.make(apiRequest.logo)
        val status: Validation[Throwable, ProjectStatus]           = ProjectStatus.make(apiRequest.status)
        val selfjoin: Validation[Throwable, ProjectSelfJoin]       = ProjectSelfJoin.make(apiRequest.selfjoin)

        val projectCreatePayload: Validation[Throwable, ProjectCreatePayloadADM] =
          Validation.validateWith(id, shortname, shortcode, longname, description, keywords, logo, status, selfjoin)(
            ProjectCreatePayloadADM.apply
          )

        val requestMessage: Future[ProjectCreateRequestADM] = for {
          projectCreatePayload <- toFuture(projectCreatePayload)
          requestingUser       <- getUserADM(requestContext)
        } yield ProjectCreateRequestADM(
          createRequest = projectCreatePayload,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }
  }

  /**
   * Returns all unique keywords for all projects as a list.
   */
  private def getKeywords(): Route = path(projectsBasePath / "Keywords") {
    get { requestContext =>
      RouteUtilADM.runJsonRoute(
        requestMessageF = FastFuture.successful(ProjectsKeywordsGetRequestADM()),
        requestContext = requestContext,
        appActor = appActor,
        log = log
      )
    }
  }

  /**
   * Returns all keywords for a single project.
   */
  private def getProjectKeywords(): Route =
    path(projectsBasePath / "iri" / Segment / "Keywords") { value =>
      get { requestContext =>
        val projectIri =
          ProjectIri
            .make(value)
            .getOrElse(throw BadRequestException(s"Invalid project IRI $value"))
        val requestMessage: Future[ProjectKeywordsGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext)
        } yield ProjectKeywordsGetRequestADM(
          projectIri = projectIri
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns a single project identified through the IRI.
   */
  private def getProjectByIri(): Route =
    path(projectsBasePath / "iri" / Segment) { value =>
      get { requestContext =>
        val requestMessage = ProjectGetRequestADM(
          IriIdentifier.fromString(value).getOrElseWith(e => throw BadRequestException(e.head.getMessage))
        )
        RouteUtilADM.runJsonRoute(
          requestMessageF = FastFuture.successful(requestMessage),
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns a single project identified through the shortname.
   */
  private def getProjectByShortname(): Route =
    path(projectsBasePath / "shortname" / Segment) { value =>
      get { requestContext =>
        val requestMessage = Future {
          ProjectGetRequestADM(identifier =
            ShortnameIdentifier
              .fromString(value)
              .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
          )
        }

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns a single project identified through the shortcode.
   */
  private def getProjectByShortcode(): Route =
    path(projectsBasePath / "shortcode" / Segment) { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectGetRequestADM] = Future {
          ProjectGetRequestADM(identifier =
            ShortcodeIdentifier
              .fromString(value)
              .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
          )
        }

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Updates a project identified by the IRI.
   */
  private def changeProject(): Route =
    path(projectsBasePath / "iri" / Segment) { value =>
      put {
        entity(as[ChangeProjectApiRequestADM]) { apiRequest => requestContext =>
          val checkedProjectIri =
            stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))
          val projectIri: ProjectIri =
            ProjectIri.make(checkedProjectIri).getOrElse(throw BadRequestException(s"Invalid Project IRI"))

          val shortname: Validation[ValidationException, Option[ShortName]] = ShortName.make(apiRequest.shortname)
          val longname: Validation[Throwable, Option[Name]]                 = Name.make(apiRequest.longname)
          val description: Validation[Throwable, Option[ProjectDescription]] =
            ProjectDescription.make(apiRequest.description)
          val keywords: Validation[Throwable, Option[Keywords]]        = Keywords.make(apiRequest.keywords)
          val logo: Validation[Throwable, Option[Logo]]                = Logo.make(apiRequest.logo)
          val status: Validation[Throwable, Option[ProjectStatus]]     = ProjectStatus.make(apiRequest.status)
          val selfjoin: Validation[Throwable, Option[ProjectSelfJoin]] = ProjectSelfJoin.make(apiRequest.selfjoin)

          val projectUpdatePayloadValidation: Validation[Throwable, ProjectUpdatePayloadADM] =
            Validation.validateWith(shortname, longname, description, keywords, logo, status, selfjoin)(
              ProjectUpdatePayloadADM.apply
            )

          /* the api request is already checked at time of creation. see case class. */

          val requestMessage: Future[ProjectChangeRequestADM] = for {
            projectUpdatePayload <- toFuture(projectUpdatePayloadValidation)
            requestingUser       <- getUserADM(requestContext)
          } yield ProjectChangeRequestADM(
            projectIri = projectIri,
            projectUpdatePayload = projectUpdatePayload,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            appActor = appActor,
            log = log
          )
        }
      }
    }

  /**
   * Updates project status to false.
   */
  private def deleteProject(): Route =
    path(projectsBasePath / "iri" / Segment) { value =>
      delete { requestContext =>
        val projectIri = ProjectIri.make(value).getOrElse(throw BadRequestException(s"Invalid Project IRI $value"))
        val projectStatus =
          ProjectStatus.make(false).getOrElse(throw BadRequestException(s"Invalid Project Status"))
        val requestMessage: Future[ProjectChangeRequestADM] = for {
          requestingUser <- getUserADM(requestContext)
        } yield ProjectChangeRequestADM(
          projectIri = projectIri,
          projectUpdatePayload = ProjectUpdatePayloadADM(status = Some(projectStatus)),
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns all members of a project identified through the IRI.
   */
  private def getProjectMembersByIri(): Route =
    path(projectsBasePath / "iri" / Segment / "members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectMembersGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext)

        } yield ProjectMembersGetRequestADM(
          identifier = IriIdentifier
            .fromString(value)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns all members of a project identified through the shortname.
   */
  private def getProjectMembersByShortname(): Route =
    path(projectsBasePath / "shortname" / Segment / "members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectMembersGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext)

        } yield ProjectMembersGetRequestADM(
          identifier = ShortnameIdentifier
            .fromString(value)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns all members of a project identified through the shortcode.
   */
  private def getProjectMembersByShortcode(): Route =
    path(projectsBasePath / "shortcode" / Segment / "members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectMembersGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext)

        } yield ProjectMembersGetRequestADM(
          identifier = ShortcodeIdentifier
            .fromString(value)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns all admin members of a project identified through the IRI.
   */
  private def getProjectAdminMembersByIri(): Route =
    path(projectsBasePath / "iri" / Segment / "admin-members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext)

        } yield ProjectAdminMembersGetRequestADM(
          identifier = IriIdentifier
            .fromString(value)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns all admin members of a project identified through the shortname.
   */
  private def getProjectAdminMembersByShortname(): Route =
    path(projectsBasePath / "shortname" / Segment / "admin-members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext)

        } yield ProjectAdminMembersGetRequestADM(
          identifier = ShortnameIdentifier
            .fromString(value)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns all admin members of a project identified through shortcode.
   */
  private def getProjectAdminMembersByShortcode(): Route =
    path(projectsBasePath / "shortcode" / Segment / "admin-members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext)

        } yield ProjectAdminMembersGetRequestADM(
          identifier = ShortcodeIdentifier
            .fromString(value)
            .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns the project's restricted view settings identified through the IRI.
   */
  private def getProjectRestrictedViewSettingsByIri(): Route =
    path(projectsBasePath / "iri" / Segment / "RestrictedViewSettings") { value: String =>
      get { requestContext =>
        val requestMessage = FastFuture.successful(
          ProjectRestrictedViewSettingsGetRequestADM(
            identifier = IriIdentifier
              .fromString(value)
              .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
          )
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns the project's restricted view settings identified through the shortname.
   */
  private def getProjectRestrictedViewSettingsByShortname(): Route =
    path(projectsBasePath / "shortname" / Segment / "RestrictedViewSettings") { value: String =>
      get { requestContext =>
        val requestMessage = FastFuture.successful(
          ProjectRestrictedViewSettingsGetRequestADM(
            identifier = ShortnameIdentifier
              .fromString(value)
              .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
          )
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  /**
   * Returns the project's restricted view settings identified through shortcode.
   */
  private def getProjectRestrictedViewSettingsByShortcode(): Route =
    path(projectsBasePath / "shortcode" / Segment / "RestrictedViewSettings") { value: String =>
      get { requestContext =>
        val requestMessage = FastFuture.successful(
          ProjectRestrictedViewSettingsGetRequestADM(
            identifier = ShortcodeIdentifier
              .fromString(value)
              .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
          )
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }

  private val projectDataHeader =
    `Content-Disposition`(ContentDispositionTypes.attachment, Map(("filename", "project-data.trig")))

  /**
   * Returns all ontologies, data, and configuration belonging to a project.
   */
  private def getProjectData(): Route =
    path(projectsBasePath / "iri" / Segment / "AllData") { projectIri: IRI =>
      get {
        respondWithHeaders(projectDataHeader) {
          getProjectDataEntity(
            projectIri = projectIri
          )
        }
      }
    }

  private def getProjectDataEntity(projectIri: IRI): Route = { requestContext =>
    val httpEntityFuture: Future[HttpEntity.Chunked] = for {
      requestingUser <- getUserADM(requestContext)

      requestMessage = ProjectDataGetRequestADM(
                         projectIdentifier = IriIdentifier
                           .fromString(projectIri)
                           .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
                         requestingUser = requestingUser
                       )

      responseMessage <- appActor.ask(requestMessage).mapTo[ProjectDataGetResponseADM]

      // Stream the output file back to the client, then delete the file.

      source: Source[ByteString, Unit] = FileIO.fromPath(responseMessage.projectDataFile).watchTermination() {
                                           case (_: Future[IOResult], result: Future[Done]) =>
                                             result.onComplete((_: Try[Done]) =>
                                               Files.delete(responseMessage.projectDataFile)
                                             )
                                         }

      httpEntity = HttpEntity(ContentTypes.`application/octet-stream`, source)
    } yield httpEntity

    requestContext.complete(httpEntityFuture)
  }
}
