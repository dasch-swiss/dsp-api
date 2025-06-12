/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import sttp.model.StatusCode
import zio.Chunk
import zio.NonEmptyChunk
import zio.ZIO
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
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.DefaultObjectAccessPermissionPart
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat.Group
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
import org.knora.webapi.slice.admin.domain.service.DefaultObjectAccessPermissionService
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestAdminApiClient

object ProjectEraseIT extends E2EZSpec {

  private val users    = ZIO.serviceWithZIO[KnoraUserService]
  private val projects = ZIO.serviceWithZIO[KnoraProjectService]
  private val groups   = ZIO.serviceWithZIO[KnoraGroupService]
  private val db       = ZIO.serviceWithZIO[TriplestoreService]
  private val aps      = ZIO.serviceWithZIO[AdministrativePermissionService]
  private val doaps    = ZIO.serviceWithZIO[DefaultObjectAccessPermissionService]

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
    suiteAll(s"The project erase endpoint /admin/projects/:shortcode/erase") {

      suite("given project to delete is not present")(
        test("when called as root then it responds with Not Found") {
          for {
            resp <- TestAdminApiClient.eraseProject(shortcode, rootUser)
          } yield assertTrue(resp.code == StatusCode.NotFound)
        },
      )

      suite("given project to delete is present")(
        test("when called as root then it should delete the project and respond with Ok") {
          for {
            before <- TestAdminApiClient.getProject(shortcode, rootUser)
            erased <- TestAdminApiClient.eraseProject(shortcode, rootUser)
            after  <- TestAdminApiClient.getProject(shortcode, rootUser)
          } yield assertTrue(
            before.code == StatusCode.Ok,
            erased.code == StatusCode.Ok,
            after.code == StatusCode.NotFound,
          )
        },
        test("when called as root then it should delete user memberships and groups and respond with Ok") {
          for {
            // given
            userAndGroup <- createUserWithMemberships
            (user, group) = userAndGroup
            project      <- getProject

            // when
            erased <- TestAdminApiClient.eraseProject(shortcode, rootUser)

            // then
            user            <- getUser(user.id)
            groupWasDeleted <- groups(_.findById(group.id)).map(_.isEmpty)
          } yield assertTrue(
            erased.code == StatusCode.Ok,
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
            erased <- TestAdminApiClient.eraseProject(shortcode, rootUser)

            // then
            graphDeleted <- doesGraphExist(graphName).negate
          } yield assertTrue(
            erased.code == StatusCode.Ok,
            graphExisted,
            graphDeleted,
          )
        },
        test("when called as root then it should delete the ontology graph") {
          for {
            project <- getProject
            req = CreateOntologyRequestV2(
                    "test",
                    project.id,
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
            _ <- TestAdminApiClient.eraseProject(shortcode, rootUser).flatMap(_.assert200)

            // then
            graphDeleted <- doesGraphExist(ontologyGraphName).negate
          } yield assertTrue(ontologyGraphExists, graphDeleted)
        },
        test("when called as root then it should delete administrative permissions") {
          for {
            project <- getProject
            group   <- createGroup(project)
            perms = Chunk(
                      AdministrativePermissionPart.Simple
                        .from(ProjectResourceCreateAll)
                        .getOrElse(throw Exception("should not happen")),
                    )
            ap         <- aps(_.create(project, group, perms))
            wasPresent <- aps(_.findByGroupAndProject(group.id, project.id)).map(_.nonEmpty)

            // when
            erased <- TestAdminApiClient.eraseProject(shortcode, rootUser)

            // then
            wasDeleted <- aps(_.findByGroupAndProject(group.id, project.id)).map(_.isEmpty)
          } yield assertTrue(wasPresent, wasDeleted)
        },
        test("when called as root then it should delete the default object access permissions") {
          for {
            project <- getProject
            group   <- createGroup(project)
            perms = Chunk(
                      DefaultObjectAccessPermissionPart(Permission.ObjectAccess.View, NonEmptyChunk(group.id)),
                      DefaultObjectAccessPermissionPart(Permission.ObjectAccess.Modify, NonEmptyChunk(group.id)),
                    )
            doap       <- doaps(_.create(project, Group(group.id), perms))
            wasPresent <- doaps(_.findByProject(project.id)).map(_.nonEmpty)

            // when
            erased <- TestAdminApiClient.eraseProject(shortcode, rootUser)

            // then
            wasDeleted <- doaps(_.findByProject(project.id)).map(_.isEmpty)
          } yield assertTrue(wasPresent, wasDeleted)
        },
      ) @@ TestAspect.before(createProject)
    }
}
