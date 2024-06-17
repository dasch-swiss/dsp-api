/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi
import zio.Chunk
import zio.ZIO
import zio.http.Status
import zio.test.Spec
import zio.test.TestAspect
import zio.test.assertTrue

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import dsp.valueobjects.LanguageCode
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.ontologymessages.CreateOntologyRequestV2
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.UserCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionPart
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.GroupDescriptions
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.GroupSelfJoin
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Description
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.Password
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative.ProjectResourceCreateAll
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.AdministrativePermissionService
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

object ProjectEraseIT extends E2EZSpec {

  private val users    = ZIO.serviceWithZIO[KnoraUserService]
  private val projects = ZIO.serviceWithZIO[KnoraProjectService]
  private val groups   = ZIO.serviceWithZIO[KnoraGroupService]
  private val db       = ZIO.serviceWithZIO[TriplestoreService]
  private val aps      = ZIO.serviceWithZIO[AdministrativePermissionService]

  private def getUser(userIri: UserIri) = users(_.findById(userIri)).someOrFail(Exception(s"Must be present $userIri"))
  private val shortcode                 = Shortcode.unsafeFrom("9999")
  private val getProject                = projects(_.findByShortcode(shortcode)).someOrFail(Exception(s"Must be present $shortcode"))

  private val createProject = projects(
    _.createProject(
      ProjectCreateRequest(
        None,
        Shortname.unsafeFrom("TestPrj"),
        Shortcode.unsafeFrom("9999"),
        None,
        List(Description.unsafeFrom("description", None)),
        List.empty,
        None,
        KnoraProject.Status.Active,
        KnoraProject.SelfJoin.CanJoin,
      ),
    ),
  ).orDie

  private val createUser = users(
    _.createNewUser(
      UserCreateRequest(
        None,
        Username.unsafeFrom("donald.duck"),
        Email.unsafeFrom("donald.duck@example.com"),
        GivenName.unsafeFrom("Donald"),
        FamilyName.unsafeFrom("Duck"),
        Password.unsafeFrom("test"),
        UserStatus.from(true),
        LanguageCode.en,
        SystemAdmin.IsNotSystemAdmin,
      ),
    ),
  )

  private val groupNr: AtomicInteger = new AtomicInteger()
  private def createGroup(project: KnoraProject) = {
    val nr = groupNr.getAndIncrement()
    groups(
      _.createGroup(
        GroupCreateRequest(
          None,
          GroupName.unsafeFrom("group" + nr),
          GroupDescriptions.unsafeFrom(
            Seq(StringLiteralV2.unsafeFrom("group description: " + nr, None)),
          ),
          project.id,
          GroupStatus.active,
          GroupSelfJoin.enabled,
        ),
        project,
      ),
    )
  }

  private val createUserWithMemberships = for {
    user    <- createUser
    project <- getProject
    group   <- createGroup(project)
    user    <- users(_.addUserToGroup(user, group))
    user    <- users(_.addUserToProject(user, project))
    user    <- users(_.addUserToProjectAsAdmin(user, project))
  } yield (user, group)

  private def doesGraphExist(graphName: InternalIri) = db(_.query(Ask(s"ASK { GRAPH <${graphName.value}> {} }")))

  override def e2eSpec: Spec[ProjectEraseIT.env, Any] =
    suiteAll(s"The erasing project endpoint ${AdminApiRestClient.projectsShortcodeErasePath}") {

      suite("given project to delete is not present")(
        test("when called as root then it responds with Not Found") {
          for {
            resp <- AdminApiRestClient.eraseProjectAsRoot(shortcode)
          } yield assertTrue(resp.status == Status.NotFound)
        },
      )

      suite("given project to delete is present")(
        test("when called as root then it should delete the project and respond with Ok") {
          for {
            before <- AdminApiRestClient.getProjectAsRoot(shortcode)
            erased <- AdminApiRestClient.eraseProjectAsRoot(shortcode)
            after  <- AdminApiRestClient.getProjectAsRoot(shortcode)
          } yield assertTrue(
            before.status == Status.Ok,
            erased.status == Status.Ok,
            after.status == Status.NotFound,
          )
        },
        test("when called as root then it should delete user memberships and groups and respond with Ok") {
          for {
            // given
            userAndGroup <- createUserWithMemberships
            (user, group) = userAndGroup
            project      <- getProject

            // when
            erased <- AdminApiRestClient.eraseProjectAsRoot(shortcode)

            // then
            user            <- getUser(user.id)
            groupWasDeleted <- groups(_.findById(group.id)).map(_.isEmpty)
          } yield assertTrue(
            erased.status == Status.Ok,
            !user.isInProject.contains(project.id),
            !user.isInProjectAdminGroup.contains(project.id),
            !user.isInGroup.contains(group),
            groupWasDeleted,
          )
        },
        test("when called as root then it should delete the project graph") {
          for {
            // given
            project  <- getProject
            graphName = ProjectService.projectDataNamedGraphV2(project)
            _ <- // insert something into the project graph, otherwise it does not exist
              db(_.query(Update(s"""
                                   |INSERT DATA {
                                   |  GRAPH <${graphName.value}> {
                                   |    <http://example.org/resource> <http://example.org/property> "value".
                                   |  }
                                   |}""".stripMargin)))
            graphExisted <- doesGraphExist(graphName)

            // when
            erased <- AdminApiRestClient.eraseProjectAsRoot(shortcode)

            // then
            graphDeleted <- doesGraphExist(graphName).negate
          } yield assertTrue(
            erased.status == Status.Ok,
            graphExisted,
            graphDeleted,
          )
        },
        test("when called as root then it should delete the ontology graph") {
          for {
            project         <- getProject
            projectSmartIri <- ZIO.serviceWithZIO[IriConverter](_.asSmartIri(project.id.value))
            req = CreateOntologyRequestV2(
                    "test",
                    projectSmartIri,
                    false,
                    "some label",
                    None,
                    UUID.randomUUID(),
                    KnoraSystemInstances.Users.SystemUser,
                  )
            onto                <- ZIO.serviceWithZIO[OntologyResponderV2](_.createOntology(req))
            ontologyGraphName    = onto.ontologies.head.ontologyIri.toInternalIri
            ontologyGraphExists <- doesGraphExist(ontologyGraphName)

            // when
            erased <- AdminApiRestClient.eraseProjectAsRoot(shortcode)

            // then
            graphDeleted <- doesGraphExist(ontologyGraphName).negate
          } yield assertTrue(ontologyGraphExists, graphDeleted)
        },
        test("when called as root then it should delete administrative permissions") {
          val perm = AdministrativePermissionPart.Simple
            .from(ProjectResourceCreateAll)
            .getOrElse(throw Exception("should not happen"))
          for {
            project    <- getProject
            group      <- createGroup(project)
            ap         <- aps(_.create(project, group, Chunk(perm)))
            wasPresent <- aps(_.findByGroupAndProject(group.id, project.id)).map(_.nonEmpty)

            // when
            erased <- AdminApiRestClient.eraseProjectAsRoot(shortcode)

            // then
            wasDeleted <- aps(_.findByGroupAndProject(group.id, project.id)).map(_.isEmpty)
          } yield assertTrue(wasPresent, wasDeleted)
        },
      ) @@ TestAspect.before(createProject)
    }
}
