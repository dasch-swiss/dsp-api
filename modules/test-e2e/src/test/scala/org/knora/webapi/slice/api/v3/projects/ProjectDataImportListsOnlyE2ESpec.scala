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
class ProjectDataImportListsOnlyE2ESpec extends E2EZSpec {

  // Incunabula with its ontology AND a data graph pre-seeded with ONLY list nodes. The create-only precondition
  // excludes list-node subjects, so a lists-only graph counts as absent and the import is permitted (REQ-9.2).
  override def rdfDataObjects: List[RdfDataObject] = List(
    incunabulaRdfOntology,
    RdfDataObject("test_data/project_data/incunabula-lists-only-data.ttl", "http://www.knora.org/data/0803/incunabula"),
  )

  private val projectIri  = incunabulaProjectIri.value
  private val jsonLdType  = MediaType.unsafeApply("application", "ld+json")
  private val resourceIri = ResourceIri.makeNew(Shortcode.unsafeFrom("0803"))
  private val valueIri    = ValueIri.makeNew(resourceIri)

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
       |      "${knoraApi}valueAsString": "An imported title"
       |    },
       |    "@context": {
       |       "rdfs": "http://www.w3.org/2000/01/rdf-schema#"
       |    }
       |}]""".stripMargin

  // The on-behalf-of project user: a member of incunabula, not a system admin, active, with project-wide create rights.
  private val memberUsername = incunabulaMemberUser.username

  override val e2eSpec: Spec[env, Any] = suite("Project Data Import - lists-only precondition E2E")(
    test("import into a project whose data graph holds only list nodes (REQ-9.2)") {
      for {
        triggerResponse <- TestApiClient.postBinary[DataTaskStatusResponse](
                             uri"/v3/projects/$projectIri/data-imports?onBehalfOfUser=$memberUsername",
                             dataGraphJsonLd.getBytes(StandardCharsets.UTF_8),
                             jsonLdType,
                             rootUser,
                           )
        importStatus <- ZIO.fromEither(triggerResponse.body).mapError(new RuntimeException(_))
        pollResult   <- pollImportUntilDone(importStatus.id).retry(Schedule.spaced(500.millis) && Schedule.recurs(60))
        // Delete the completed task: the single-task import mutex is per-JVM, so a leftover task would block other
        // import specs sharing the test JVM.
        deleteResponse <-
          TestApiClient
            .deleteJson[Json](uri"/v3/projects/$projectIri/data-imports/${importStatus.id.value}", rootUser)
      } yield assertTrue(
        triggerResponse.code == StatusCode.Accepted,
        pollResult.status == DataTaskStatus.Completed,
        deleteResponse.code == StatusCode.NoContent,
      )
    },
  )

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
