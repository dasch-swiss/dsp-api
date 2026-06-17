/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.client4.UriContext
import sttp.model.StatusCode
import zio.ZIO
import zio.json.ast.Json
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.testservices.TestApiClient

object MaintenanceReplaceUserIriInProjectE2ESpec extends E2EZSpec {

  private val adminGraph           = AdminConstants.adminDataNamedGraph.value
  private val anythingShortcode    = "0001"
  private val anythingProjectGraph = "http://www.knora.org/data/0001/anything"
  private val builtInUserIri       = "http://www.knora.org/ontology/knora-admin#SystemUser"

  private def endpointFor(shortcode: String) =
    uri"/admin/maintenance/projects/$shortcode/replace-user-iri"

  private def replaceBody(oldIri: String, newIri: String): Json =
    Json.Obj("oldIri" -> Json.Str(oldIri), "newIri" -> Json.Str(newIri))

  private def insertUserInAdminGraph(iri: UserIri): ZIO[TriplestoreService, Throwable, Unit] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(Update(s"""INSERT DATA {
                        |  GRAPH <$adminGraph> {
                        |    <${iri.value}> <http://www.knora.org/ontology/knora-admin#email> "e2e-test@example.com" .
                        |  }
                        |}""".stripMargin)),
    )

  private def addProjectMembership(userIri: UserIri, projectIri: String): ZIO[TriplestoreService, Throwable, Unit] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(Update(s"""INSERT DATA {
                        |  GRAPH <$adminGraph> {
                        |    <${userIri.value}> <http://www.knora.org/ontology/knora-admin#isInProject> <$projectIri> .
                        |  }
                        |}""".stripMargin)),
    )

  private def insertRefInProjectGraph(
    userIri: UserIri,
    projectGraph: String,
  ): ZIO[TriplestoreService, Throwable, Unit] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(
        Update(
          s"""INSERT DATA {
             |  GRAPH <$projectGraph> {
             |    <http://rdfh.ch/e2e-test-resource> <http://www.knora.org/ontology/knora-base#attachedToUser> <${userIri.value}> .
             |  }
             |}""".stripMargin,
        ),
      ),
    )

  private def existsAsObjectInGraph(iri: UserIri, graph: String): ZIO[TriplestoreService, Throwable, Boolean] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(Ask(s"ASK { GRAPH <$graph> { ?s ?p <${iri.value}> . } }")),
    )

  private def existsInAdminGraph(iri: UserIri): ZIO[TriplestoreService, Throwable, Boolean] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(Ask(s"ASK { GRAPH <$adminGraph> { <${iri.value}> ?p ?o . } }")),
    )

  val e2eSpec: Spec[env, Any] = suite("POST /admin/maintenance/projects/{shortcode}/replace-user-iri")(
    test("returns 401 when no authentication is provided") {
      TestApiClient
        .postJson[Json, Json](
          endpointFor(anythingShortcode),
          replaceBody(normalUser.id, "http://rdfh.ch/users/e2e-new"),
        )
        .map(response => assertTrue(response.code == StatusCode.Unauthorized))
    },
    test("returns 403 when the authenticated user is not SystemAdmin") {
      TestApiClient
        .postJson[Json, Json](
          endpointFor(anythingShortcode),
          replaceBody(normalUser.id, "http://rdfh.ch/users/e2e-new"),
          normalUser,
        )
        .map(response => assertTrue(response.code == StatusCode.Forbidden))
    },
    test("does not reject a built-in user IRI as oldIri (re-attributing SystemUser refs is valid)") {
      // No isBuiltInUser guard is applied at the RestService level for
      // this endpoint. A built-in oldIri is intentionally accepted - re-attributing references
      // stamped under SystemUser/AnonymousUser to a real project member is a valid use case.
      // The call proceeds to domain validation, so the response is not 400 BadRequest.
      TestApiClient
        .postJson[Json, Json](
          endpointFor(anythingShortcode),
          replaceBody(builtInUserIri, "http://rdfh.ch/users/e2e-new"),
          rootUser,
        )
        .map(response => assertTrue(response.code != StatusCode.BadRequest))
    },
    test("returns 204 when oldIri is a built-in user IRI with references in the project graph") {
      val newIri          = UserIri.unsafeFrom("http://rdfh.ch/users/e2e-proj-builtin-new")
      val systemUserIri   = UserIri.unsafeFrom(builtInUserIri)
      val anythingProject = "http://rdfh.ch/projects/0001"
      for {
        _        <- insertUserInAdminGraph(newIri)
        _        <- addProjectMembership(newIri, anythingProject)
        _        <- insertRefInProjectGraph(systemUserIri, anythingProjectGraph)
        response <- TestApiClient.postJson[Json, Json](
                      endpointFor(anythingShortcode),
                      replaceBody(builtInUserIri, newIri.value),
                      rootUser,
                    )
        oldGone  <- existsAsObjectInGraph(systemUserIri, anythingProjectGraph)
        newThere <- existsAsObjectInGraph(newIri, anythingProjectGraph)
      } yield assertTrue(
        response.code == StatusCode.NoContent,
        !oldGone,
        newThere,
      )
    },
    test("returns 400 when oldIri and newIri are equal") {
      TestApiClient
        .postJson[Json, Json](
          endpointFor(anythingShortcode),
          replaceBody(normalUser.id, normalUser.id),
          rootUser,
        )
        .map(response => assertTrue(response.code == StatusCode.BadRequest))
    },
    test("returns 400 when oldIri is malformed") {
      TestApiClient
        .postJson[Json, Json](
          endpointFor(anythingShortcode),
          replaceBody("not-a-valid-iri", "http://rdfh.ch/users/e2e-new"),
          rootUser,
        )
        .map(response => assertTrue(response.code == StatusCode.BadRequest))
    },
    test("returns 404 when the project shortcode is not found") {
      TestApiClient
        .postJson[Json, Json](
          endpointFor("FFFF"),
          replaceBody(normalUser.id, "http://rdfh.ch/users/e2e-new"),
          rootUser,
        )
        .map(response => assertTrue(response.code == StatusCode.NotFound))
    },
    test("returns 404 when oldIri is not in the admin named graph") {
      val nonExistentIri = "http://rdfh.ch/users/e2e-project-nonexistent-old"
      TestApiClient
        .postJson[Json, Json](
          endpointFor(anythingShortcode),
          replaceBody(nonExistentIri, "http://rdfh.ch/users/e2e-new"),
          rootUser,
        )
        .map(response => assertTrue(response.code == StatusCode.NotFound))
    },
    test("returns 404 when newIri is not in the admin named graph") {
      val existingIri    = UserIri.unsafeFrom("http://rdfh.ch/users/e2e-proj-old-404-new")
      val nonExistentNew = "http://rdfh.ch/users/e2e-proj-nonexistent-new"
      for {
        _        <- insertUserInAdminGraph(existingIri)
        response <- TestApiClient.postJson[Json, Json](
                      endpointFor(anythingShortcode),
                      replaceBody(existingIri.value, nonExistentNew),
                      rootUser,
                    )
      } yield assertTrue(response.code == StatusCode.NotFound)
    },
    test("returns 400 when newIri is not a member of the project") {
      val oldIri = UserIri.unsafeFrom("http://rdfh.ch/users/e2e-proj-old-not-member")
      val newIri = UserIri.unsafeFrom("http://rdfh.ch/users/e2e-proj-new-not-member")
      for {
        _        <- insertUserInAdminGraph(oldIri)
        _        <- insertUserInAdminGraph(newIri)
        response <- TestApiClient.postJson[Json, Json](
                      endpointFor(anythingShortcode),
                      replaceBody(oldIri.value, newIri.value),
                      rootUser,
                    )
      } yield assertTrue(response.code == StatusCode.BadRequest)
    },
    test("returns 404 when oldIri has no references in the project data graph") {
      val oldIri          = UserIri.unsafeFrom("http://rdfh.ch/users/e2e-proj-old-no-refs")
      val anythingProject = "http://rdfh.ch/projects/0001"
      for {
        _        <- insertUserInAdminGraph(oldIri)
        _        <- insertUserInAdminGraph(anythingUser1.userIri)
        _        <- addProjectMembership(anythingUser1.userIri, anythingProject)
        response <- TestApiClient.postJson[Json, Json](
                      endpointFor(anythingShortcode),
                      replaceBody(oldIri.value, anythingUser1.id),
                      rootUser,
                    )
      } yield assertTrue(response.code == StatusCode.NotFound)
    },
    test("returns 204 and replaces references only in the target project graph") {
      val oldIri            = UserIri.unsafeFrom("http://rdfh.ch/users/e2e-proj-old-204")
      val newIri            = UserIri.unsafeFrom("http://rdfh.ch/users/e2e-proj-new-204")
      val anythingProject   = "http://rdfh.ch/projects/0001"
      val otherProjectGraph = "http://www.knora.org/data/0803/images"
      for {
        _        <- insertUserInAdminGraph(oldIri)
        _        <- insertUserInAdminGraph(newIri)
        _        <- addProjectMembership(newIri, anythingProject)
        _        <- insertRefInProjectGraph(oldIri, anythingProjectGraph)
        _        <- insertRefInProjectGraph(oldIri, otherProjectGraph)
        response <- TestApiClient.postJson[Json, Json](
                      endpointFor(anythingShortcode),
                      replaceBody(oldIri.value, newIri.value),
                      rootUser,
                    )
        oldInProject    <- existsAsObjectInGraph(oldIri, anythingProjectGraph)
        newInProject    <- existsAsObjectInGraph(newIri, anythingProjectGraph)
        oldInAdmin      <- existsInAdminGraph(oldIri)
        oldInOtherGraph <- existsAsObjectInGraph(oldIri, otherProjectGraph)
      } yield assertTrue(
        response.code == StatusCode.NoContent,
        !oldInProject,
        newInProject,
        oldInAdmin,
        oldInOtherGraph,
      )
    },
  )
}
