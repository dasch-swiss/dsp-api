/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio._
import zio.mock._
import zio.test._

import java.nio.file

import dsp.valueobjects.V2._
import org.knora.webapi.TestDataFactory
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
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
      getProjectMembers
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
      val shortcode  = "0001"
      val identifier = TestDataFactory.projectShortcodeIdentifier(shortcode)
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
      val shortname  = "someProject"
      val identifier = TestDataFactory.projectShortnameIdentifier(shortname)
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
      val iri        = "http://rdfh.ch/projects/0001"
      val identifier = TestDataFactory.projectIriIdentifier(iri)
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
    val payload = ProjectCreatePayloadADM(
      None,
      TestDataFactory.projectShortName("newproject"),
      TestDataFactory.projectShortCode("3333"),
      Some(TestDataFactory.projectName("project longname")),
      TestDataFactory.projectDescription(Seq(StringLiteralV2("updated project description", Some("en")))),
      TestDataFactory.projectKeywords(Seq("test", "kewords")),
      None,
      TestDataFactory.projectStatus(true),
      TestDataFactory.projectSelfJoin(true)
    )

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
    val iri                  = "http://rdfh.ch/projects/0001"
    val projectIri           = TestDataFactory.projectIri(iri)
    val projectStatus        = Some(TestDataFactory.projectStatus(false))
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
    val iri        = "http://rdfh.ch/projects/0001"
    val projectIri = TestDataFactory.projectIri(iri)
    val projectUpdatePayload = ProjectUpdatePayloadADM(
      Some(TestDataFactory.projectShortName("usn")),
      Some(TestDataFactory.projectName("updated project longname")),
      Some(TestDataFactory.projectDescription(Seq(StringLiteralV2("updated project description", Some("en"))))),
      Some(TestDataFactory.projectKeywords(Seq("updated", "kewords"))),
      Some(TestDataFactory.projectLogo("../updatedlogo.png")),
      Some(TestDataFactory.projectStatus(true)),
      Some(TestDataFactory.projectSelfJoin(true))
    )
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
    val iri            = "http://rdfh.ch/projects/0001"
    val identifier     = TestDataFactory.projectIriIdentifier(iri)
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

  val getProjectMembers = suite("get all members of a project")(
    test("get members by project shortcode") {
      val shortcode      = "0001"
      val identifier     = TestDataFactory.projectShortcodeIdentifier(shortcode)
      val requestingUser = KnoraSystemInstances.Users.SystemUser
      val projectsService =
        ZIO
          .serviceWithZIO[ProjectsService](_.getProjectMembers(identifier, requestingUser))
          .provideSome[ActorToZioBridge](layers)
      val actorToZioBridge = ActorToZioBridgeMock.AskAppActor
        .of[ProjectMembersGetResponseADM]
        .apply(
          assertion = Assertion.equalTo(ProjectMembersGetRequestADM(identifier, requestingUser)),
          result = Expectation.value(ProjectMembersGetResponseADM(Seq.empty[UserADM]))
        )
        .toLayer
      for {
        _ <- projectsService.provide(actorToZioBridge)
      } yield assertTrue(true)
    },
    test("get members by project shortname") {
      val shortname      = "shortname"
      val identifier     = TestDataFactory.projectShortnameIdentifier(shortname)
      val requestingUser = KnoraSystemInstances.Users.SystemUser
      val projectsService =
        ZIO
          .serviceWithZIO[ProjectsService](_.getProjectMembers(identifier, requestingUser))
          .provideSome[ActorToZioBridge](layers)
      val actorToZioBridge = ActorToZioBridgeMock.AskAppActor
        .of[ProjectMembersGetResponseADM]
        .apply(
          assertion = Assertion.equalTo(ProjectMembersGetRequestADM(identifier, requestingUser)),
          result = Expectation.value(ProjectMembersGetResponseADM(Seq.empty[UserADM]))
        )
        .toLayer
      for {
        _ <- projectsService.provide(actorToZioBridge)
      } yield assertTrue(true)
    },
    test("get members by project IRI") {
      val iri            = "http://rdfh.ch/projects/0001"
      val identifier     = TestDataFactory.projectIriIdentifier(iri)
      val requestingUser = KnoraSystemInstances.Users.SystemUser
      val projectsService =
        ZIO
          .serviceWithZIO[ProjectsService](_.getProjectMembers(identifier, requestingUser))
          .provideSome[ActorToZioBridge](layers)
      val actorToZioBridge = ActorToZioBridgeMock.AskAppActor
        .of[ProjectMembersGetResponseADM]
        .apply(
          assertion = Assertion.equalTo(ProjectMembersGetRequestADM(identifier, requestingUser)),
          result = Expectation.value(ProjectMembersGetResponseADM(Seq.empty[UserADM]))
        )
        .toLayer
      for {
        _ <- projectsService.provide(actorToZioBridge)
      } yield assertTrue(true)
    }
  )

}
