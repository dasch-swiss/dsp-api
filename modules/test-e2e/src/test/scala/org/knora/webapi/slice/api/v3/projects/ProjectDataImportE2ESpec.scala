/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.*
import zio.*
import zio.json.ast.Json
import zio.test.*

import java.nio.charset.StandardCharsets

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.`export`.domain.DataTaskStatus
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.testservices.TestApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class ProjectDataImportE2ESpec extends E2EZSpec {

  // The incunabula project with its ontology but WITHOUT any project data — the data graph must not exist yet
  // for the create-only import to succeed.
  override def rdfDataObjects: List[RdfDataObject] = List(incunabulaRdfOntology)

  private val projectIri  = incunabulaProjectIri.value
  private val jsonLdType  = MediaType.unsafeApply("application", "ld+json")
  private val resourceIri = ResourceIri.makeNew(Shortcode.unsafeFrom("0803"))
  private val valueIri    = ValueIri.makeNew(resourceIri)
  private val titleText   = "An imported title"

  private val onto     = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#"
  private val knoraApi = "http://api.knora.org/ontology/knora-api/v2#"

  /** A minimal knora-api data graph: one incunabula book with a title value. */
  private val dataGraphJsonLd =
    s"""
       |[{
       |    "@id": "$resourceIri",
       |    "@type": "${onto}book",
       |    "rdfs:label": "Imported book",
       |    "${onto}title": {
       |      "@id": "$valueIri",
       |      "@type": "${knoraApi}TextValue",
       |      "${knoraApi}valueAsString": "$titleText"
       |    },
       |    "@context": {
       |       "rdfs": "http://www.w3.org/2000/01/rdf-schema#"
       |    }
       |}]""".stripMargin

  // The on-behalf-of project user: a member of incunabula, not a system admin, active, with project-wide create rights.
  private val memberUsername = incunabulaMemberUser.username
  private val memberEmail    = incunabulaMemberUser.email

  override val e2eSpec: Spec[env, Any] = suite("Project Data Import E2E")(
    test("reject a non-sysadmin caller with Forbidden") {
      for {
        triggerResponse <- TestApiClient.postBinary[Json](
                             uri"/v3/projects/$projectIri/data-imports?onBehalfOfUser=$memberUsername",
                             dataGraphJsonLd.getBytes(StandardCharsets.UTF_8),
                             jsonLdType,
                             incunabulaProjectAdminUser,
                           )
        fakeId          = "AAAAAAAAAAAAAAAAAAAAAA"
        statusResponse <-
          TestApiClient
            .getJson[Json](uri"/v3/projects/$projectIri/data-imports/$fakeId", incunabulaProjectAdminUser)
        deleteResponse <-
          TestApiClient
            .deleteJson[Json](uri"/v3/projects/$projectIri/data-imports/$fakeId", incunabulaProjectAdminUser)
      } yield assertTrue(
        triggerResponse.code == StatusCode.Forbidden,
        statusResponse.code == StatusCode.Forbidden,
        deleteResponse.code == StatusCode.Forbidden,
      )
    },
    test("reject a request that omits the required onBehalfOfUser query parameter") {
      for {
        response <- TestApiClient.postBinary[Json](
                      uri"/v3/projects/$projectIri/data-imports",
                      dataGraphJsonLd.getBytes(StandardCharsets.UTF_8),
                      jsonLdType,
                      rootUser,
                    )
      } yield assertTrue(response.code == StatusCode.BadRequest)
    },
    test("reject a system-admin on-behalf-of user (REQ-2.4)") {
      for {
        response <- triggerAs(rootUser.username)
      } yield assertTrue(
        response.code == StatusCode.BadRequest,
        bodyContains(response, "on_behalf_of_user_ineligible"),
        bodyContains(response, "is_system_admin"),
      )
    },
    test("reject an on-behalf-of user who is not a member of the project (REQ-2.5)") {
      for {
        response <- triggerAs(anythingUser1.username)
      } yield assertTrue(
        response.code == StatusCode.BadRequest,
        bodyContains(response, "not_project_member"),
      )
    },
    test("reject an unknown on-behalf-of email with 404") {
      for {
        response <- triggerAs("nobody@example.org")
      } yield assertTrue(
        response.code == StatusCode.NotFound,
        bodyContains(response, "on_behalf_of_user_not_found"),
      )
    },
    test("reject an unknown on-behalf-of username with 404") {
      for {
        response <- triggerAs("nonexistentuser")
      } yield assertTrue(
        response.code == StatusCode.NotFound,
        bodyContains(response, "on_behalf_of_user_not_found"),
      )
    },
    test("reject a malformed on-behalf-of identifier with 400 (malformed_identifier)") {
      for {
        response <- triggerAs("ab")
      } yield assertTrue(
        response.code == StatusCode.BadRequest,
        bodyContains(response, "malformed_identifier"),
      )
    },
    test("attribute imported data to the on-behalf-of user, expose it on status, read back, and clean up") {
      for {
        // Trigger the import on behalf of the member, and poll until completed.
        triggerResponse <- TestApiClient.postBinary[DataTaskStatusResponse](
                             uri"/v3/projects/$projectIri/data-imports?onBehalfOfUser=$memberUsername",
                             dataGraphJsonLd.getBytes(StandardCharsets.UTF_8),
                             jsonLdType,
                             rootUser,
                           )
        importStatus <- ZIO.fromEither(triggerResponse.body).mapError(new RuntimeException(_))
        importId      = importStatus.id
        pollResult   <- pollImportUntilDone(importId).retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

        // The imported resource and value are readable through the v2 API.
        resourceResponse <- TestApiClient.getJsonLd(uri"/v2/resources/${resourceIri.toString}", rootUser)
        valueResponse    <-
          TestApiClient.getJsonLd(uri"/v2/values/${resourceIri.toString}/${valueIri.valueId.value}", rootUser)

        // Delete the completed task, then confirm it is gone. The project data graph itself remains.
        deleteResponse <- TestApiClient
                            .deleteJson[Json](uri"/v3/projects/$projectIri/data-imports/${importId.value}", rootUser)
        statusAfterDelete <- TestApiClient
                               .getJson[Json](uri"/v3/projects/$projectIri/data-imports/${importId.value}", rootUser)
      } yield assertTrue(
        triggerResponse.code == StatusCode.Accepted,
        // The status response records the subject (on-behalf-of user) alongside the actor (createdBy = the admin).
        importStatus.onBehalfOf.contains(incunabulaMemberUser.userIri),
        importStatus.createdBy == rootUser.userIri,
        pollResult.status == DataTaskStatus.Completed,
        resourceResponse.code == StatusCode.Ok,
        resourceResponse.body.exists(b => b.contains(valueIri.toString) && b.contains(titleText)),
        // The imported resource is attached to the on-behalf-of user, not the triggering admin.
        resourceResponse.body.exists(_.contains(incunabulaMemberUser.userIri.value)),
        valueResponse.code == StatusCode.Ok,
        valueResponse.body.exists(b => b.contains(valueIri.toString) && b.contains(titleText)),
        deleteResponse.code == StatusCode.NoContent,
        statusAfterDelete.code == StatusCode.NotFound,
      )
    },
    test("resolve the on-behalf-of user by email, then reject the second import (create-only)") {
      // The previous test created the project data graph (the suite runs sequentially). A well-formed member email
      // resolves past eligibility and reaches the create-only check, which fails 409 — proving the email branch
      // resolves an eligible user.
      for {
        response <- TestApiClient.postBinary[Json](
                      uri"/v3/projects/$projectIri/data-imports?onBehalfOfUser=$memberEmail",
                      dataGraphJsonLd.getBytes(StandardCharsets.UTF_8),
                      jsonLdType,
                      rootUser,
                    )
      } yield assertTrue(
        response.code == StatusCode.Conflict,
        bodyContains(response, "data_graph_exists"),
      )
    },
  ) @@ TestAspect.sequential

  private def triggerAs(onBehalfOf: String) =
    TestApiClient.postBinary[Json](
      uri"/v3/projects/$projectIri/data-imports?onBehalfOfUser=$onBehalfOf",
      dataGraphJsonLd.getBytes(StandardCharsets.UTF_8),
      jsonLdType,
      rootUser,
    )

  private def bodyContains(response: Response[Either[String, Json]], text: String): Boolean =
    response.body.exists(_.toString.contains(text))

  private def pollImportUntilDone(importId: DataTaskId) =
    TestApiClient
      .getJson[DataTaskStatusResponse](uri"/v3/projects/$projectIri/data-imports/${importId.value}", rootUser)
      .flatMap(r => ZIO.fromEither(r.body).mapError(new RuntimeException(_)))
      .flatMap { status =>
        status.status match {
          case DataTaskStatus.Completed => ZIO.succeed(status)
          case DataTaskStatus.Failed    => ZIO.succeed(status)
          case _                        => ZIO.fail(new RuntimeException("Import still in progress"))
        }
      }
}
