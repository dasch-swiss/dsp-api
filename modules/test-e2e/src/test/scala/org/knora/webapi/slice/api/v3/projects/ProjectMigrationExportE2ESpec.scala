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
import zio.nio.file.Files
import zio.test.*

import java.time.LocalDate

import org.knora.bagit.BagIt
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.KnoraBaseVersion
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.e2ez.ProjectExportTestSupport.*
import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.`export`.domain.DataTaskStatus
import org.knora.webapi.testservices.TestApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class ProjectMigrationExportE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = incunabulaRdfOntologyAndData

  private val projectIri = incunabulaProjectIri.value

  override val e2eSpec: Spec[env, Any] = suite("Project Migration Export E2E")(
    test("return Forbidden for project admin user") {
      for {
        triggerResponse <-
          TestApiClient
            .postJson[Json](uri"/v3/projects/$projectIri/exports", incunabulaProjectAdminUser)
        fakeId          = "AAAAAAAAAAAAAAAAAAAAAA"
        statusResponse <- TestApiClient
                            .getJson[Json](uri"/v3/projects/$projectIri/exports/$fakeId", incunabulaProjectAdminUser)
        deleteResponse <- TestApiClient
                            .deleteJson[Json](uri"/v3/projects/$projectIri/exports/$fakeId", incunabulaProjectAdminUser)
      } yield assertTrue(
        triggerResponse.code == StatusCode.Forbidden,
        statusResponse.code == StatusCode.Forbidden,
        deleteResponse.code == StatusCode.Forbidden,
      )
    },
    test("trigger export, poll until completed, download and validate BagIt zip") {
      for {
        // Trigger export (cleaning up any existing export from a previous run, since state is now persistent)
        taskStatus <- triggerExport(projectIri)
        exportId    = taskStatus.id

        // Poll status until completed (max 30 seconds)
        completed <- pollUntilCompleted(projectIri, exportId)
                       .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

        // Download the zip
        zipBytes <- downloadExport(projectIri, exportId)

        // Validate BagIt and check payload files
        bag <- ZIO.scoped {
                 for {
                   tempDir <- Files.createTempDirectoryScoped(Some("bagit-e2e-test"), Seq.empty)
                   zipPath  = tempDir / "export.zip"
                   _       <- Files.writeBytes(zipPath, Chunk.fromArray(zipBytes))
                   result  <- BagIt.readAndValidateZip(zipPath)
                 } yield result._1
               }

        payloadPaths = bag.payloadFiles.map(_.value)
        bagInfo      = bag.bagInfo
        additional   = bagInfo.map(_.additionalFields.toMap).getOrElse(Map.empty)

        apiConfig <- ZIO.service[KnoraApi]

        // Cleanup
        _ <- deleteExport(projectIri, exportId)
      } yield assertTrue(
        // status
        completed.status == DataTaskStatus.Completed,
        // payload files
        payloadPaths.exists(p => p.startsWith("rdf/ontology") && p.endsWith(".nq")),
        payloadPaths.contains("rdf/data.nq"),
        payloadPaths.contains("rdf/admin.nq"),
        payloadPaths.contains("rdf/permission.nq"),
        // bag-info metadata
        bagInfo.flatMap(_.sourceOrganization).contains("DaSCH Service Platform"),
        bagInfo.flatMap(_.externalIdentifier).contains(projectIri),
        bagInfo.flatMap(_.baggingDate).contains(LocalDate.now()),
        additional("KnoraBase-Version") == s"$KnoraBaseVersion",
        additional("Dsp-Api-Version") == BuildInfo.version,
        additional("Source-Server") == apiConfig.externalHost,
      )
    },
    test("serve and delete an export through a project IRI that does not resolve (DEV-6766)") {
      // The export slot is server-wide and status/delete/download resolve the task by its id alone, so an
      // unresolvable project IRI in the path must not block them. Gating them on project existence made the export
      // of an erased project unreachable, wedging the slot for every project. A never-existing IRI reproduces the
      // erased-project condition without mutating the shared incunabula fixture.
      val ghostProjectIri = "http://rdfh.ch/projects/0000000000000000000000"
      for {
        taskStatus <- triggerExport(projectIri)
        exportId    = taskStatus.id
        _          <- pollUntilCompleted(projectIri, exportId).retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

        statusResponse <- TestApiClient.getJson[DataTaskStatusResponse](
                            uri"/v3/projects/$ghostProjectIri/exports/${exportId.value}",
                            rootUser,
                          )
        downloadedBytes <- downloadExport(ghostProjectIri, exportId)
        deleteResponse  <- deleteExportResponse(ghostProjectIri, exportId)

        // The slot is empty again: the deleted task is gone, and a new export can be created.
        statusAfterDelete <- TestApiClient.getJson[Json](
                               uri"/v3/projects/$ghostProjectIri/exports/${exportId.value}",
                               rootUser,
                             )
        reTriggered <- TestApiClient.postJson[DataTaskStatusResponse](
                         uri"/v3/projects/$projectIri/exports?skipAssets=true",
                         rootUser,
                       )
        _ <- ZIO.foreachDiscard(reTriggered.body.toOption) { s =>
               pollUntilCompleted(projectIri, s.id).retry(Schedule.spaced(500.millis) && Schedule.recurs(60)) *>
                 deleteExport(projectIri, s.id)
             }
      } yield assertTrue(
        statusResponse.code == StatusCode.Ok,
        statusResponse.body.map(_.id) == Right(exportId),
        downloadedBytes.nonEmpty,
        deleteResponse.code == StatusCode.NoContent,
        statusAfterDelete.code == StatusCode.NotFound,
        reTriggered.code == StatusCode.Accepted,
      )
    },
  )
}
