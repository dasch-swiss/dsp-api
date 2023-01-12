/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio._
import zio.mock._
import zio.prelude.Validation
import zio.test._

import java.util.UUID

import dsp.valueobjects.Iri._
import dsp.valueobjects.Project.ShortCode
import dsp.valueobjects.Project._
import dsp.valueobjects.V2
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectCreatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectCreateRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectOperationResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetResponseADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.ActorToZioBridge
import org.knora.webapi.responders.ActorToZioBridgeMock
import org.knora.webapi.responders.admin.ProjectsService

object ProjectsServiceLiveSpec extends ZIOSpecDefault {

  private val expectNoInteraction = ActorToZioBridgeMock.empty

  def nextUuid: UIO[UUID] = for {
    random <- ZIO.random
    _      <- random.setSeed(0) // makes the random deterministic: bb20b45f-d4d9-4138-bd93-cb799b3970be
    uuid   <- random.nextUUID
  } yield uuid

  val layers = ZLayer.makeSome[ActorToZioBridge, ProjectsService](ProjectsService.live)

  /**
   * Creates a [[ProjectADM]] with empty content or optionally with a given ID.
   */
  private def getProjectADM(id: String = "") =
    ProjectADM(
      id = id,
      shortname = "",
      shortcode = "",
      longname = None,
      description = Seq(StringLiteralV2("")),
      keywords = Seq.empty,
      logo = None,
      ontologies = Seq.empty,
      status = true,
      selfjoin = false
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("RestProjectsService")(
      test("get all projects") {
        val expectedResponse = ProjectsGetResponseADM(Seq(getProjectADM()))
        val projectsService =
          ZIO
            .serviceWithZIO[ProjectsService](_.getProjectsADMRequest())
            .provideSome[ActorToZioBridge](layers)
        val bridge = ActorToZioBridgeMock.AskAppActor
          .of[ProjectsGetResponseADM]
          .apply(assertion = Assertion.equalTo(ProjectsGetRequestADM()), result = Expectation.value(expectedResponse))
          .toLayer
        for {
          _ <- projectsService.provide(bridge)
        } yield assertTrue(true)
      },
      suite("get project by identifier")(
        test("get project by shortcode") {
          val identifier = ProjectIdentifierADM.ShortcodeIdentifier
            .fromString("0001")
            .getOrElse(throw new Exception("invalid shortcode"))
          val projectsService =
            ZIO
              .serviceWithZIO[ProjectsService](_.getSingleProjectADMRequest(identifier))
              .provideSome[ActorToZioBridge](layers)
          val bridge = ActorToZioBridgeMock.AskAppActor
            .of[ProjectGetResponseADM]
            .apply(
              assertion = Assertion.equalTo(ProjectGetRequestADM(identifier)),
              result = Expectation.value(ProjectGetResponseADM(getProjectADM()))
            )
            .toLayer
          for {
            _ <- projectsService.provide(bridge)
          } yield assertTrue(true)
        },
        test("get project by shortname") {
          val identifier = ProjectIdentifierADM.ShortnameIdentifier
            .fromString("someProject")
            .getOrElse(throw new Exception("invalid shortname"))
          val projectsService =
            ZIO
              .serviceWithZIO[ProjectsService](_.getSingleProjectADMRequest(identifier))
              .provideSome[ActorToZioBridge](layers)
          val bridge = ActorToZioBridgeMock.AskAppActor
            .of[ProjectGetResponseADM]
            .apply(
              assertion = Assertion.equalTo(ProjectGetRequestADM(identifier)),
              result = Expectation.value(ProjectGetResponseADM(getProjectADM()))
            )
            .toLayer
          for {
            _ <- projectsService.provide(bridge)
          } yield assertTrue(true)
        },
        test("get project by IRI") {
          val identifier = ProjectIdentifierADM.IriIdentifier
            .fromString("http://rdfh.ch/projects/0001")
            .getOrElse(throw new Exception("invalid IRI"))
          val projectsService =
            ZIO
              .serviceWithZIO[ProjectsService](_.getSingleProjectADMRequest(identifier))
              .provideSome[ActorToZioBridge](layers)
          val bridge = ActorToZioBridgeMock.AskAppActor
            .of[ProjectGetResponseADM]
            .apply(
              assertion = Assertion.equalTo(ProjectGetRequestADM(identifier)),
              result = Expectation.value(ProjectGetResponseADM(getProjectADM()))
            )
            .toLayer
          for {
            _ <- projectsService.provide(bridge)
          } yield assertTrue(true)
        }
      ),
      test("create a project") {
        val payload: ProjectCreatePayloadADM =
          Validation
            .validateWith(
              ProjectIri.make(None),
              ShortName.make("newproject"),
              ShortCode.make("3333"),
              Name.make(Some("project longname")),
              ProjectDescription.make(Seq(V2.StringLiteralV2("project description", Some("en")))),
              Keywords.make(Seq("test project")),
              Logo.make(None),
              ProjectStatus.make(true),
              ProjectSelfJoin.make(false)
            )(ProjectCreatePayloadADM.apply)
            .getOrElse(throw new Exception("Invalid Payload"))
        val requestingUser = KnoraSystemInstances.Users.SystemUser
        val projectsService =
          ZIO
            .serviceWithZIO[ProjectsService](_.createProjectADMRequest(payload, requestingUser))
            .provideSome[ActorToZioBridge](layers)
        for {
          uuid <- nextUuid
          bridge =
            ActorToZioBridgeMock.AskAppActor
              .of[ProjectOperationResponseADM]
              .apply(
                assertion = Assertion.equalTo(ProjectCreateRequestADM(payload, requestingUser, uuid)),
                result = Expectation.value(ProjectOperationResponseADM(getProjectADM()))
              )
              .toLayer
          _ <- projectsService.provide(bridge)
        } yield assertTrue(true)
      }
    )
}
