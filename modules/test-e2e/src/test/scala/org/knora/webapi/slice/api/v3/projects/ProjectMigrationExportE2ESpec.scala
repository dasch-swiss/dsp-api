/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.*
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.nio.file.Files
import zio.test.*

import java.time.LocalDate

import org.knora.bagit.BagIt
import org.knora.webapi.E2EZSpec
import org.knora.webapi.KnoraBaseVersion
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.`export`.domain.DataTaskStatus
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.ScopeResolver
import org.knora.webapi.testservices.TestApiClient

object ProjectMigrationExportE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = incunabulaRdfOntologyAndData

  private val projectIri = incunabulaProjectIri.value

  override val e2eSpec: Spec[env, Any] = suite("Project Migration Export E2E")(
    test("trigger export, poll until completed, download and validate BagIt zip") {
      for {
        // Trigger export
        triggerResponse <- TestApiClient.postJson[DataTaskStatusResponse, Json](
                             uri"/v3/projects/$projectIri/exports",
                             Json.Obj(),
                             rootUser,
                           )
        _          <- assertTrue(triggerResponse.code == StatusCode.Accepted)
        taskStatus <- ZIO.fromEither(triggerResponse.body).mapError(new RuntimeException(_))
        exportId    = taskStatus.id

        // Poll status until completed (max 30 seconds)
        completed <- pollUntilCompleted(exportId)
                       .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

        // Download the zip
        zipBytes <- downloadExportBytes(exportId)

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
        bagInfo      = bag.bagInfo.get
        additional   = bagInfo.additionalFields.toMap

        apiConfig <- ZIO.service[KnoraApi]

        // Cleanup
        _ <- deleteExport(exportId)
      } yield assertTrue(
        // status
        completed.status == DataTaskStatus.Completed,
        // payload files
        payloadPaths.exists(p => p.startsWith("rdf/ontology") && p.endsWith(".nq")),
        payloadPaths.contains("rdf/data.nq"),
        payloadPaths.contains("rdf/admin.nq"),
        payloadPaths.contains("rdf/permissions.nq"),
        // bag-info metadata
        bagInfo.sourceOrganization.contains("DaSCH Service Platform"),
        bagInfo.externalIdentifier.contains(projectIri),
        bagInfo.baggingDate.contains(LocalDate.now()),
        additional("KnoraBase-Version") == s"$KnoraBaseVersion",
        additional("Dsp-Api-Version") == BuildInfo.version,
        additional("Source-Server") == apiConfig.externalHost,
      )
    },
  )

  private def pollUntilCompleted(exportId: DataTaskId) =
    TestApiClient
      .getJson[DataTaskStatusResponse](
        uri"/v3/projects/$projectIri/exports/${exportId.value}",
        rootUser,
      )
      .flatMap(r => ZIO.fromEither(r.body).mapError(new RuntimeException(_)))
      .flatMap { status =>
        status.status match {
          case DataTaskStatus.Completed => ZIO.succeed(status)
          case DataTaskStatus.Failed    => ZIO.die(new RuntimeException("Export failed"))
          case _                        => ZIO.fail(new RuntimeException("Export still in progress"))
        }
      }

  private def deleteExport(exportId: DataTaskId, prjIri: String = projectIri) =
    TestApiClient
      .deleteJson[Json](uri"/v3/projects/$prjIri/exports/${exportId.value}", rootUser)
      .ignore

  private def downloadExportBytes(exportId: DataTaskId) =
    for {
      apiConfig     <- ZIO.service[KnoraApi]
      jwtService    <- ZIO.service[JwtService]
      scopeResolver <- ZIO.service[ScopeResolver]
      scope         <- scopeResolver.resolve(rootUser)
      jwt           <- jwtService.createJwt(rootUser.userIri, scope)
      be            <- HttpClientZioBackend()
      downloadUrl    =
        uri"${apiConfig.externalKnoraApiBaseUrl}/v3/projects/$projectIri/exports/${exportId.value}/download"
      response <- basicRequest
                    .get(downloadUrl)
                    .auth
                    .bearer(jwt.jwtString)
                    .response(asByteArray)
                    .send(be)
      bytes <- ZIO.fromEither(response.body).mapError(new RuntimeException(_))
    } yield bytes
}
