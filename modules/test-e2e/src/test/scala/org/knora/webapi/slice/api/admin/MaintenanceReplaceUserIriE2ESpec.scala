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

object MaintenanceReplaceUserIriE2ESpec extends E2EZSpec {

  private val endpoint   = uri"/admin/maintenance/users/replace-iri"
  private val adminGraph = AdminConstants.adminDataNamedGraph.value

  // A built-in user IRI — forbidden by the service
  private val builtInUserIri = "http://www.knora.org/ontology/knora-admin#SystemUser"

  private def insertUserInAdminGraph(iri: UserIri): ZIO[TriplestoreService, Throwable, Unit] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(Update(s"""INSERT DATA {
                        |  GRAPH <$adminGraph> {
                        |    <${iri.value}> <http://www.knora.org/ontology/knora-admin#email> "test@example.com" .
                        |  }
                        |}""".stripMargin)),
    )

  private def existsInAdminGraph(iri: UserIri): ZIO[TriplestoreService, Throwable, Boolean] =
    ZIO.serviceWithZIO[TriplestoreService](
      _.query(Ask(s"ASK { GRAPH <$adminGraph> { <${iri.value}> ?p ?o . } }")),
    )

  private def replaceBody(oldIri: String, newIri: String): Json =
    Json.Obj("oldIri" -> Json.Str(oldIri), "newIri" -> Json.Str(newIri))

  val e2eSpec: Spec[env, Any] = suite("POST /admin/maintenance/users/replace-iri")(
    test("returns 401 when no authentication is provided") {
      TestApiClient
        .postJson[Json, Json](endpoint, replaceBody(normalUser.id, "http://rdfh.ch/users/new-iri"))
        .map(response => assertTrue(response.code == StatusCode.Unauthorized))
    },
    test("returns 403 when the authenticated user is not SystemAdmin") {
      TestApiClient
        .postJson[Json, Json](endpoint, replaceBody(normalUser.id, "http://rdfh.ch/users/new-iri"), normalUser)
        .map(response => assertTrue(response.code == StatusCode.Forbidden))
    },
    test("returns 400 when oldIri is a built-in user IRI") {
      TestApiClient
        .postJson[Json, Json](endpoint, replaceBody(builtInUserIri, "http://rdfh.ch/users/new-iri"), rootUser)
        .map(response => assertTrue(response.code == StatusCode.BadRequest))
    },
    test("returns 400 when newIri is a built-in user IRI") {
      TestApiClient
        .postJson[Json, Json](endpoint, replaceBody(normalUser.id, builtInUserIri), rootUser)
        .map(response => assertTrue(response.code == StatusCode.BadRequest))
    },
    test("returns 400 when oldIri and newIri are equal") {
      TestApiClient
        .postJson[Json, Json](endpoint, replaceBody(normalUser.id, normalUser.id), rootUser)
        .map(response => assertTrue(response.code == StatusCode.BadRequest))
    },
    test("returns 400 when oldIri is the requester's own IRI") {
      TestApiClient
        .postJson[Json, Json](endpoint, replaceBody(rootUser.id, "http://rdfh.ch/users/new-iri"), rootUser)
        .map(response => assertTrue(response.code == StatusCode.BadRequest))
    },
    test("returns 400 when the IRI is malformed") {
      TestApiClient
        .postJson[Json, Json](endpoint, replaceBody("not-a-valid-iri", "http://rdfh.ch/users/new-iri"), rootUser)
        .map(response => assertTrue(response.code == StatusCode.BadRequest))
    },
    test("returns 404 when oldIri is not present in the admin named graph") {
      val nonExistentIri = "http://rdfh.ch/users/does-not-exist-for-maintenance-test"
      TestApiClient
        .postJson[Json, Json](endpoint, replaceBody(nonExistentIri, "http://rdfh.ch/users/new-iri"), rootUser)
        .map(response => assertTrue(response.code == StatusCode.NotFound))
    },
    test("returns 409 when newIri already exists in the admin named graph") {
      val oldIri = UserIri.unsafeFrom("http://rdfh.ch/users/maintenance-test-409-old")
      for {
        _        <- insertUserInAdminGraph(oldIri)
        response <- TestApiClient.postJson[Json, Json](
                      endpoint,
                      replaceBody(oldIri.value, normalUser.id),
                      rootUser,
                    )
      } yield assertTrue(response.code == StatusCode.Conflict)
    },
    test("returns 204 and atomically replaces the IRI across all graphs") {
      val oldIri = UserIri.unsafeFrom("http://rdfh.ch/users/maintenance-test-204-old")
      val newIri = UserIri.unsafeFrom("http://rdfh.ch/users/maintenance-test-204-new")
      for {
        _         <- insertUserInAdminGraph(oldIri)
        response  <- TestApiClient.postJson[Json, Json](endpoint, replaceBody(oldIri.value, newIri.value), rootUser)
        oldExists <- existsInAdminGraph(oldIri)
        newExists <- existsInAdminGraph(newIri)
      } yield assertTrue(
        response.code == StatusCode.NoContent,
        !oldExists,
        newExists,
      )
    },
  )
}
