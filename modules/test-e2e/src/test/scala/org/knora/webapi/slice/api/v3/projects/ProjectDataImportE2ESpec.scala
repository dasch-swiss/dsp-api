/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import sttp.client4.*
import sttp.model.*
import zio.*
import zio.json.ast.Json
import zio.test.*

import java.nio.charset.StandardCharsets

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.`export`.domain.DataTaskStatus
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.testservices.TestApiClient

object ProjectDataImportE2ESpec extends E2EZSpec {

  // The incunabula project with its ontology but WITHOUT any project data — the data graph must not exist yet
  // for the create-only import to succeed.
  override def rdfDataObjects: List[RdfDataObject] = List(incunabulaRdfOntology)

  private val projectIri  = incunabulaProjectIri.value
  private val jsonLdType  = MediaType.unsafeApply("application", "ld+json")
  private val resourceIri = ResourceIri.makeNew(Shortcode.unsafeFrom("0803"))

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
       |      "@id": "$resourceIri/values/${java.util.UUID.randomUUID()}",
       |      "@type": "${knoraApi}TextValue",
       |      "${knoraApi}valueAsString": "An imported title"
       |    },
       |    "@context": {
       |       "rdfs": "http://www.w3.org/2000/01/rdf-schema#"
       |    }
       |}]""".stripMargin

  override val e2eSpec: Spec[env, Any] = suite("Project Data Import E2E")(
    test("return Forbidden for project admin user") {
      for {
        triggerResponse <- TestApiClient.postBinary[Json](
                             uri"/v3/projects/$projectIri/data-imports",
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
    test("import a data graph, reject a second import, and clean up the task") {
      for {
        // Step 1: Trigger the import
        triggerResponse <- TestApiClient.postBinary[DataTaskStatusResponse](
                             uri"/v3/projects/$projectIri/data-imports",
                             dataGraphJsonLd.getBytes(StandardCharsets.UTF_8),
                             jsonLdType,
                             rootUser,
                           )
        importStatus <- ZIO.fromEither(triggerResponse.body).mapError(new RuntimeException(_))
        importId      = importStatus.id

        // Step 2: Poll until completed
        pollResult <- pollImportUntilDone(importId).retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

        // Step 3: The imported resource is visible through the normal API
        resourceResponse <- TestApiClient.getJsonLd(uri"/v2/resources/${resourceIri.toString}", rootUser)

        // Step 4: A second import is rejected — the project already has a data graph
        secondResponse <- TestApiClient.postBinary[Json](
                            uri"/v3/projects/$projectIri/data-imports",
                            dataGraphJsonLd.getBytes(StandardCharsets.UTF_8),
                            jsonLdType,
                            rootUser,
                          )

        // Step 5: Delete the completed task
        deleteResponse <- TestApiClient
                            .deleteJson[Json](uri"/v3/projects/$projectIri/data-imports/${importId.value}", rootUser)

        // Step 6: The deleted task is gone
        statusAfterDelete <- TestApiClient
                               .getJson[Json](uri"/v3/projects/$projectIri/data-imports/${importId.value}", rootUser)
      } yield assertTrue(
        triggerResponse.code == StatusCode.Accepted,
        pollResult.status == DataTaskStatus.Completed,
        resourceResponse.code == StatusCode.Ok,
        resourceResponse.body.exists(_.contains("An imported title")),
        secondResponse.code == StatusCode.Conflict,
        secondResponse.body.exists(_.toString.contains("data_graph_exists")),
        deleteResponse.code == StatusCode.NoContent,
        statusAfterDelete.code == StatusCode.NotFound,
      )
    },
  ) @@ TestAspect.sequential

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
