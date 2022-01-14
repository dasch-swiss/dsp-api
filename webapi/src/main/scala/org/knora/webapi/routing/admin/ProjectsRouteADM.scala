/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.Done
import akka.http.scaladsl.model.headers.{ContentDispositionTypes, `Content-Disposition`}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.pattern._
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import io.swagger.annotations._
import org.knora.webapi.IRI
import org.knora.webapi.annotation.ApiMayChange
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.valueObjects._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import zio.prelude.Validation

import java.nio.file.Files
import java.util.UUID
import javax.ws.rs.Path
import scala.concurrent.Future
import scala.util.Try

object ProjectsRouteADM {
  val ProjectsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "projects")
}

@Api(value = "projects", produces = "application/json")
@Path("/admin/projects")
class ProjectsRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with ProjectsADMJsonProtocol {

  import ProjectsRouteADM._

  /**
   * Returns the route.
   */
  override def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    getProjects(featureFactoryConfig) ~
      addProject(featureFactoryConfig) ~
      getKeywords(featureFactoryConfig) ~
      getProjectKeywords(featureFactoryConfig) ~
      getProjectByIri(featureFactoryConfig) ~
      getProjectByShortname(featureFactoryConfig) ~
      getProjectByShortcode(featureFactoryConfig) ~
      changeProject(featureFactoryConfig) ~
      deleteProject(featureFactoryConfig) ~
      getProjectMembersByIri(featureFactoryConfig) ~
      getProjectMembersByShortname(featureFactoryConfig) ~
      getProjectMembersByShortcode(featureFactoryConfig) ~
      getProjectAdminMembersByIri(featureFactoryConfig) ~
      getProjectAdminMembersByShortname(featureFactoryConfig) ~
      getProjectAdminMembersByShortcode(featureFactoryConfig) ~
      getProjectRestrictedViewSettingsByIri(featureFactoryConfig) ~
      getProjectRestrictedViewSettingsByShortname(featureFactoryConfig) ~
      getProjectRestrictedViewSettingsByShortcode(featureFactoryConfig) ~
      getProjectData(featureFactoryConfig)

  /* return all projects */
  @ApiOperation(
    value = "Get projects",
    nickname = "getProjects",
    httpMethod = "GET",
    response = classOf[ProjectsGetResponseADM]
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def getProjects(featureFactoryConfig: FeatureFactoryConfig): Route = path(ProjectsBasePath) {
    get { requestContext =>
      val requestMessage: Future[ProjectsGetRequestADM] = for {
        requestingUser <- getUserADM(
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig
        )
      } yield ProjectsGetRequestADM(
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )

      RouteUtilADM.runJsonRoute(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        featureFactoryConfig = featureFactoryConfig,
        settings = settings,
        responderManager = responderManager,
        log = log
      )
    }
  }

  /* create a new project */
  @ApiOperation(
    value = "Add new project",
    nickname = "addProject",
    httpMethod = "POST",
    response = classOf[ProjectOperationResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"project\" to create",
        required = true,
        dataTypeClass = classOf[CreateProjectApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def addProject(featureFactoryConfig: FeatureFactoryConfig): Route = path(ProjectsBasePath) {
    post {
      entity(as[CreateProjectApiRequestADM]) { apiRequest => requestContext =>
        // zio prelude: validation
        val id: Validation[Throwable, Option[ProjectIRI]] = ProjectIRI.make(apiRequest.id)
        val shortname: Validation[Throwable, Shortname] = Shortname.make(apiRequest.shortname)
        val shortcode: Validation[Throwable, Shortcode] = Shortcode.make(apiRequest.shortcode)
        val longname: Validation[Throwable, Option[Longname]] = Longname.make(apiRequest.longname)
        val description: Validation[Throwable, ProjectDescription] = ProjectDescription.make(apiRequest.description)
        val keywords: Validation[Throwable, Keywords] = Keywords.make(apiRequest.keywords)
        val logo: Validation[Throwable, Option[Logo]] = Logo.make(apiRequest.logo)
        val status: Validation[Throwable, ProjectStatus] = ProjectStatus.make(apiRequest.status)
        val selfjoin: Validation[Throwable, ProjectSelfJoin] = ProjectSelfJoin.make(apiRequest.selfjoin)

        val projectCreatePayload: Validation[Throwable, ProjectCreatePayloadADM] =
          Validation.validateWith(id, shortname, shortcode, longname, description, keywords, logo, status, selfjoin)(
            ProjectCreatePayloadADM
          )

        val requestMessage: Future[ProjectCreateRequestADM] = for {
          projectCreatePayload <- toFuture(projectCreatePayload)
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)
        } yield ProjectCreateRequestADM(
          createRequest = projectCreatePayload,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }
  }

  /* returns all unique keywords for all projects as a list */
  private def getKeywords(featureFactoryConfig: FeatureFactoryConfig): Route = path(ProjectsBasePath / "Keywords") {
    get { requestContext =>
      val requestMessage: Future[ProjectsKeywordsGetRequestADM] = for {
        requestingUser <- getUserADM(
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig
        )
      } yield ProjectsKeywordsGetRequestADM(
        featureFactoryConfig = featureFactoryConfig,
        requestingUser = requestingUser
      )

      RouteUtilADM.runJsonRoute(
        requestMessageF = requestMessage,
        requestContext = requestContext,
        featureFactoryConfig = featureFactoryConfig,
        settings = settings,
        responderManager = responderManager,
        log = log
      )
    }
  }

  /* returns all keywords for a single project */
  private def getProjectKeywords(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "iri" / Segment / "Keywords") { value =>
      get { requestContext =>
        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

        val requestMessage: Future[ProjectKeywordsGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield ProjectKeywordsGetRequestADM(
          projectIri = checkedProjectIri,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * returns a single project identified through iri
   */
  private def getProjectByIri(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "iri" / Segment) { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          checkedProjectIri =
            stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

        } yield ProjectGetRequestADM(
          identifier = ProjectIdentifierADM(maybeIri = Some(checkedProjectIri)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * returns a single project identified through shortname.
   */
  private def getProjectByShortname(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "shortname" / Segment) { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          shortNameDec = stringFormatter.validateAndEscapeProjectShortname(
            value,
            throw BadRequestException(s"Invalid project shortname $value")
          )

        } yield ProjectGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortname = Some(shortNameDec)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * returns a single project identified through shortcode.
   */
  private def getProjectByShortcode(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "shortcode" / Segment) { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          checkedShortcode = stringFormatter.validateAndEscapeProjectShortcode(
            value,
            throw BadRequestException(s"Invalid project shortcode $value")
          )

        } yield ProjectGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortcode = Some(checkedShortcode)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * update a project identified by iri
   */
  private def changeProject(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "iri" / Segment) { value =>
      put {
        entity(as[ChangeProjectApiRequestADM]) { apiRequest => requestContext =>
          val checkedProjectIri =
            stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

          /* the api request is already checked at time of creation. see case class. */

          val requestMessage: Future[ProjectChangeRequestADM] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
          } yield ProjectChangeRequestADM(
            projectIri = checkedProjectIri,
            changeProjectRequest = apiRequest.validateAndEscape,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
        }
      }
    }

  /**
   * API MAY CHANGE: update project status to false
   */
  @ApiMayChange
  private def deleteProject(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "iri" / Segment) { value =>
      delete { requestContext =>
        val checkedProjectIri =
          stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

        val requestMessage: Future[ProjectChangeRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield ProjectChangeRequestADM(
          projectIri = checkedProjectIri,
          changeProjectRequest = ChangeProjectApiRequestADM(status = Some(false)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: returns all members part of a project identified through iri
   */
  @ApiMayChange
  private def getProjectMembersByIri(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "iri" / Segment / "members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectMembersGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          checkedProjectIri =
            stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

        } yield ProjectMembersGetRequestADM(
          identifier = ProjectIdentifierADM(maybeIri = Some(checkedProjectIri)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: returns all members part of a project identified through shortname
   */
  @ApiMayChange
  private def getProjectMembersByShortname(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "shortname" / Segment / "members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectMembersGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          shortNameDec = stringFormatter.validateAndEscapeProjectShortname(
            value,
            throw BadRequestException(s"Invalid project shortname $value")
          )

        } yield ProjectMembersGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortname = Some(shortNameDec)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: returns all members part of a project identified through shortcode
   */
  @ApiMayChange
  private def getProjectMembersByShortcode(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "shortcode" / Segment / "members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectMembersGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          checkedShortcode = stringFormatter.validateAndEscapeProjectShortcode(
            value,
            throw BadRequestException(s"Invalid project shortcode $value")
          )

        } yield ProjectMembersGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortcode = Some(checkedShortcode)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: returns all admin members part of a project identified through iri
   */
  @ApiMayChange
  private def getProjectAdminMembersByIri(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "iri" / Segment / "admin-members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          checkedProjectIri =
            stringFormatter.validateAndEscapeProjectIri(value, throw BadRequestException(s"Invalid project IRI $value"))

        } yield ProjectAdminMembersGetRequestADM(
          identifier = ProjectIdentifierADM(maybeIri = Some(checkedProjectIri)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: returns all admin members part of a project identified through shortname
   */
  @ApiMayChange
  private def getProjectAdminMembersByShortname(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "shortname" / Segment / "admin-members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          checkedShortname = stringFormatter.validateAndEscapeProjectShortname(
            value,
            throw BadRequestException(s"Invalid project shortname $value")
          )

        } yield ProjectAdminMembersGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortname = Some(checkedShortname)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * API MAY CHANGE: returns all admin members part of a project identified through shortcode
   */
  @ApiMayChange
  private def getProjectAdminMembersByShortcode(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "shortcode" / Segment / "admin-members") { value =>
      get { requestContext =>
        val requestMessage: Future[ProjectAdminMembersGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          checkedShortcode = stringFormatter.validateProjectShortcode(
            value,
            throw BadRequestException(s"Invalid project shortcode $value")
          )

        } yield ProjectAdminMembersGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortcode = Some(checkedShortcode)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * Returns the project's restricted view settings identified through IRI.
   */
  @ApiMayChange
  private def getProjectRestrictedViewSettingsByIri(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "iri" / Segment / "RestrictedViewSettings") { value: String =>
      get { requestContext =>
        val requestMessage: Future[ProjectRestrictedViewSettingsGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )

        } yield ProjectRestrictedViewSettingsGetRequestADM(
          identifier = ProjectIdentifierADM(maybeIri = Some(value)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * Returns the project's restricted view settings identified through shortname.
   */
  @ApiMayChange
  private def getProjectRestrictedViewSettingsByShortname(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "shortname" / Segment / "RestrictedViewSettings") { value: String =>
      get { requestContext =>
        val requestMessage: Future[ProjectRestrictedViewSettingsGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
          shortNameDec = java.net.URLDecoder.decode(value, "utf-8")

        } yield ProjectRestrictedViewSettingsGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortname = Some(shortNameDec)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  /**
   * Returns the project's restricted view settings identified through shortcode.
   */
  @ApiMayChange
  private def getProjectRestrictedViewSettingsByShortcode(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "shortcode" / Segment / "RestrictedViewSettings") { value: String =>
      get { requestContext =>
        val requestMessage: Future[ProjectRestrictedViewSettingsGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
        } yield ProjectRestrictedViewSettingsGetRequestADM(
          identifier = ProjectIdentifierADM(maybeShortcode = Some(value)),
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig,
          settings = settings,
          responderManager = responderManager,
          log = log
        )
      }
    }

  private val projectDataHeader =
    `Content-Disposition`(ContentDispositionTypes.attachment, Map(("filename", "project-data.trig")))

  /**
   * Returns all ontologies, data, and configuration belonging to a project.
   */
  private def getProjectData(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ProjectsBasePath / "iri" / Segment / "AllData") { projectIri: IRI =>
      get {
        featureFactoryConfig.makeHttpResponseHeader match {
          case Some(featureToggleHeader) =>
            respondWithHeaders(projectDataHeader, featureToggleHeader) {
              getProjectDataEntity(
                projectIri = projectIri,
                featureFactoryConfig = featureFactoryConfig
              )
            }

          case None =>
            respondWithHeaders(projectDataHeader) {
              getProjectDataEntity(
                projectIri = projectIri,
                featureFactoryConfig = featureFactoryConfig
              )
            }
        }

      }
    }

  private def getProjectDataEntity(projectIri: IRI, featureFactoryConfig: FeatureFactoryConfig): Route = {
    requestContext =>
      val projectIdentifier = ProjectIdentifierADM(maybeIri = Some(projectIri))

      val httpEntityFuture: Future[HttpEntity.Chunked] = for {
        requestingUser <- getUserADM(
          requestContext = requestContext,
          featureFactoryConfig = featureFactoryConfig
        )

        requestMessage = ProjectDataGetRequestADM(
          projectIdentifier = projectIdentifier,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
        )

        responseMessage <- (responderManager ? requestMessage).mapTo[ProjectDataGetResponseADM]

        // Stream the output file back to the client, then delete the file.

        source: Source[ByteString, Unit] = FileIO.fromPath(responseMessage.projectDataFile).watchTermination() {
          case (_: Future[IOResult], result: Future[Done]) =>
            result.onComplete((_: Try[Done]) => Files.delete(responseMessage.projectDataFile))
        }

        httpEntity = HttpEntity(ContentTypes.`application/octet-stream`, source)
      } yield httpEntity

      requestContext.complete(httpEntityFuture)
  }
}
