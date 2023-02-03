/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio._
import zio.mock._
import zio.prelude.Validation
import zio.test._

import java.nio.file

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri._
import dsp.valueobjects.Project.ShortCode
import dsp.valueobjects.Project._
import dsp.valueobjects.V2._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectChangeRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectCreatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectCreateRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectDataGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectDataGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectOperationResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectUpdatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsKeywordsGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsKeywordsGetResponseADM
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.ActorToZioBridge
import org.knora.webapi.responders.ActorToZioBridgeMock
import org.knora.webapi.responders.admin.ProjectsService

object ProjectsServiceLiveSpec extends ZIOSpecDefault {

  private val expectNoInteraction = ActorToZioBridgeMock.empty

  val layers = ZLayer.makeSome[ActorToZioBridge, ProjectsService](ProjectsService.live)

  /**
   * Creates a [[ProjectADM]] with empty content or optionally with a given ID.
   */
  val projectADM =
    ProjectADM(
      id = "",
      shortname = "",
      shortcode = "",
      longname = None,
      description = Seq(StringLiteralV2("", None)),
      keywords = Seq.empty,
      logo = None,
      ontologies = Seq.empty,
      status = true,
      selfjoin = false
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ProjectsService")(
      getAllProjectsSpec,
      getProjectByIdSpec,
      createProjectSpec,
      deleteProjectSpec,
      updateProjectSpec,
      getAllProjectDataSpec,
      getKeywordsSpec
    ).provide(StringFormatter.test)

  val getAllProjectsSpec = test("get all projects") {
    val expectedResponse = ProjectsGetResponseADM(Seq(projectADM))
    val projectsService =
      ZIO
        .serviceWithZIO[ProjectsService](_.getProjectsADMRequest())
        .provideSome[ActorToZioBridge](layers)
    val actorToZioBridge = ActorToZioBridgeMock.AskAppActor
      .of[ProjectsGetResponseADM]
      .apply(assertion = Assertion.equalTo(ProjectsGetRequestADM()), result = Expectation.value(expectedResponse))
      .toLayer
    for {
      _ <- projectsService.provide(actorToZioBridge)
    } yield assertTrue(true)
  }

  val getProjectByIdSpec = suite("get project by identifier")(
    test("get project by shortcode") {
      val identifier = ProjectIdentifierADM.ShortcodeIdentifier
        .fromString("0001")
        .getOrElse(throw BadRequestException("invalid shortcode"))
      val projectsService =
        ZIO
          .serviceWithZIO[ProjectsService](_.getSingleProjectADMRequest(identifier))
          .provideSome[ActorToZioBridge](layers)
      val actorToZioBridge = ActorToZioBridgeMock.AskAppActor
        .of[ProjectGetResponseADM]
        .apply(
          assertion = Assertion.equalTo(ProjectGetRequestADM(identifier)),
          result = Expectation.value(ProjectGetResponseADM(projectADM))
        )
        .toLayer
      for {
        _ <- projectsService.provide(actorToZioBridge)
      } yield assertTrue(true)
    },
    test("get project by shortname") {
      val identifier = ProjectIdentifierADM.ShortnameIdentifier
        .fromString("someProject")
        .getOrElse(throw BadRequestException("invalid shortname"))
      val projectsService =
        ZIO
          .serviceWithZIO[ProjectsService](_.getSingleProjectADMRequest(identifier))
          .provideSome[ActorToZioBridge](layers)
      val actorToZioBridge = ActorToZioBridgeMock.AskAppActor
        .of[ProjectGetResponseADM]
        .apply(
          assertion = Assertion.equalTo(ProjectGetRequestADM(identifier)),
          result = Expectation.value(ProjectGetResponseADM(projectADM))
        )
        .toLayer
      for {
        _ <- projectsService.provide(actorToZioBridge)
      } yield assertTrue(true)
    },
    test("get project by IRI") {
      val identifier = ProjectIdentifierADM.IriIdentifier
        .fromString("http://rdfh.ch/projects/0001")
        .getOrElse(throw BadRequestException("invalid IRI"))
      val projectsService =
        ZIO
          .serviceWithZIO[ProjectsService](_.getSingleProjectADMRequest(identifier))
          .provideSome[ActorToZioBridge](layers)
      val actorToZioBridge = ActorToZioBridgeMock.AskAppActor
        .of[ProjectGetResponseADM]
        .apply(
          assertion = Assertion.equalTo(ProjectGetRequestADM(identifier)),
          result = Expectation.value(ProjectGetResponseADM(projectADM))
        )
        .toLayer
      for {
        _ <- projectsService.provide(actorToZioBridge)
      } yield assertTrue(true)
    }
  )

  val createProjectSpec = test("create a project") {
    val payload: ProjectCreatePayloadADM =
      Validation
        .validateWith(
          ProjectIri.make(None),
          ShortName.make("newproject"),
          ShortCode.make("3333"),
          Name.make(Some("project longname")),
          ProjectDescription.make(Seq(StringLiteralV2("project description", Some("en")))),
          Keywords.make(Seq("test project")),
          Logo.make(None),
          ProjectStatus.make(true),
          ProjectSelfJoin.make(false)
        )(ProjectCreatePayloadADM.apply)
        .getOrElse(throw BadRequestException("Invalid Payload"))
    val requestingUser = KnoraSystemInstances.Users.SystemUser
    val projectsService =
      ZIO
        .serviceWithZIO[ProjectsService](_.createProjectADMRequest(payload, requestingUser))
        .provideSome[ActorToZioBridge](layers)
    for {
      uuid   <- ZIO.random.flatMap(_.nextUUID)
      _      <- TestRandom.feedUUIDs(uuid)
      request = ProjectCreateRequestADM(payload, requestingUser, uuid)
      actorToZioBridge =
        ActorToZioBridgeMock.AskAppActor
          .of[ProjectOperationResponseADM]
          .apply(
            assertion = Assertion.equalTo(request),
            result = Expectation.value(ProjectOperationResponseADM(projectADM))
          )
          .toLayer
      _ <- projectsService.provide(actorToZioBridge)
    } yield assertTrue(true)
  }

  // needs to have the StringFormatter in the environment because ChangeProjectApiRequestADM needs it
  val deleteProjectSpec: Spec[StringFormatter, Throwable] = test("delete a project") {
    val projectIri: ProjectIri =
      ProjectIri.make("http://rdfh.ch/projects/0001").getOrElse(throw BadRequestException("Invalid project IRI"))
    val projectStatus        = Some(ProjectStatus.make(false).getOrElse(throw BadRequestException("Invalid project status")))
    val projectUpdatePayload = ProjectUpdatePayloadADM(status = projectStatus)
    val requestingUser       = KnoraSystemInstances.Users.SystemUser
    val projectsService = ZIO
      .serviceWithZIO[ProjectsService](_.deleteProject(projectIri, requestingUser))
      .provideSome[ActorToZioBridge](layers)
    for {
      uuid   <- ZIO.random.flatMap(_.nextUUID)
      _      <- TestRandom.feedUUIDs(uuid)
      request = ProjectChangeRequestADM(projectIri, projectUpdatePayload, requestingUser, uuid)
      actorToZioBridge =
        ActorToZioBridgeMock.AskAppActor
          .of[ProjectOperationResponseADM]
          .apply(
            assertion = Assertion.equalTo(request),
            result = Expectation.value(ProjectOperationResponseADM(projectADM))
          )
          .toLayer
      _ <- projectsService.provide(actorToZioBridge)
    } yield assertTrue(true)
  }

  val updateProjectSpec = test("update a project") {
    val projectIri =
      ProjectIri.make("http://rdfh.ch/projects/0001").getOrElse(throw BadRequestException("Invalid Project IRI"))
    val projectUpdatePayload: ProjectUpdatePayloadADM =
      Validation
        .validateWith(
          ShortName.make(Some("usn")),
          Name.make(Some("updated project longname")),
          ProjectDescription.make(Some(Seq(StringLiteralV2("updated project description", Some("en"))))),
          Keywords.make(Some(Seq("updated", "project"))),
          Logo.make(Some("../logo.png")),
          ProjectStatus.make(Some(true)),
          ProjectSelfJoin.make(Some(true))
        )(ProjectUpdatePayloadADM.apply)
        .getOrElse(throw BadRequestException("Invalid Payload"))
    val requestingUser = KnoraSystemInstances.Users.SystemUser
    val projectsService =
      ZIO
        .serviceWithZIO[ProjectsService](_.updateProject(projectIri, projectUpdatePayload, requestingUser))
        .provideSome[ActorToZioBridge](layers)
    for {
      uuid   <- ZIO.random.flatMap(_.nextUUID)
      _      <- TestRandom.feedUUIDs(uuid)
      request = ProjectChangeRequestADM(projectIri, projectUpdatePayload, requestingUser, uuid)
      actorToZioBridge =
        ActorToZioBridgeMock.AskAppActor
          .of[ProjectOperationResponseADM]
          .apply(
            assertion = Assertion.equalTo(request),
            result = Expectation.value(ProjectOperationResponseADM(projectADM))
          )
          .toLayer
      _ <- projectsService.provide(actorToZioBridge)
    } yield assertTrue(true)
  }

  val getAllProjectDataSpec = test("get all project data") {
    val identifier = ProjectIdentifierADM.IriIdentifier
      .fromString("http://rdfh.ch/projects/0001")
      .getOrElse(throw BadRequestException("Invalid project IRI"))
    val requestingUser = KnoraSystemInstances.Users.SystemUser
    val path           = file.Paths.get("...")
    val projectsService =
      ZIO
        .serviceWithZIO[ProjectsService](_.getAllProjectData(identifier, requestingUser))
        .provideSome[ActorToZioBridge](layers)
    val actorToZioBridge = ActorToZioBridgeMock.AskAppActor
      .of[ProjectDataGetResponseADM]
      .apply(
        assertion = Assertion.equalTo(ProjectDataGetRequestADM(identifier, requestingUser)),
        result = Expectation.value(ProjectDataGetResponseADM(path))
      )
      .toLayer
    for {
      _ <- projectsService.provide(actorToZioBridge)
    } yield assertTrue(true)
  }

  val getKeywordsSpec = test("get keywords of all projects") {
    val projectsService =
      ZIO
        .serviceWithZIO[ProjectsService](_.getKeywords())
        .provideSome[ActorToZioBridge](layers)
    val actorToZioBridge = ActorToZioBridgeMock.AskAppActor
      .of[ProjectsKeywordsGetResponseADM]
      .apply(
        assertion = Assertion.equalTo(ProjectsKeywordsGetRequestADM()),
        result = Expectation.value(ProjectsKeywordsGetResponseADM(Seq.empty[String]))
      )
      .toLayer
    for {
      _ <- projectsService.provide(actorToZioBridge)
    } yield assertCompletes
  }
}
