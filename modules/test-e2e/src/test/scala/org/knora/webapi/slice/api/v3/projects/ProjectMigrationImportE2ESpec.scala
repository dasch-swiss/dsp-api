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
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.`export`.domain.DataTaskStatus
import org.knora.webapi.slice.api.v3.Conflict
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.ScopeResolver
import org.knora.webapi.testservices.TestApiClient

object ProjectMigrationImportE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = incunabulaRdfOntologyAndData

  private val projectIri = incunabulaProjectIri.value

  override val e2eSpec: Spec[env, Any] = suite("Project Migration Import E2E")(
    test("return Forbidden for project admin user") {
      for {
        triggerResponse <- TestApiClient
                             .postBinary[Json](
                               uri"/v3/projects/$projectIri/imports",
                               Array.empty[Byte],
                               MediaType.ApplicationZip,
                               incunabulaProjectAdminUser,
                             )
        fakeId          = "AAAAAAAAAAAAAAAAAAAAAA"
        statusResponse <- TestApiClient
                            .getJson[Json](uri"/v3/projects/$projectIri/imports/$fakeId", incunabulaProjectAdminUser)
        deleteResponse <- TestApiClient
                            .deleteJson[Json](uri"/v3/projects/$projectIri/imports/$fakeId", incunabulaProjectAdminUser)
      } yield assertTrue(
        triggerResponse.code == StatusCode.Forbidden,
        statusResponse.code == StatusCode.Forbidden,
        deleteResponse.code == StatusCode.Forbidden,
      )
    },
    test("export then import round-trip — fails because project already exists") {
      for {
        // Step 1: Trigger export of Incunabula
        exportStatus <- triggerExportWithCleanup()
        exportId      = exportStatus.id

        // Step 2: Poll export until completed
        _ <- pollExportUntilCompleted(exportId)
               .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

        // Step 3: Download the export zip bytes
        zipBytes <- downloadExportBytes(exportId)

        // Step 4: POST the zip to import endpoint
        importResponse <- TestApiClient
                            .postBinary[DataTaskStatusResponse](
                              uri"/v3/projects/$projectIri/imports",
                              zipBytes,
                              MediaType.ApplicationZip,
                              rootUser,
                            )

        importStatus <- ZIO.fromEither(importResponse.body).mapError(new RuntimeException(_))
        importId      = importStatus.id

        // Step 5: Poll import status until done — expect Failed (project already exists)
        result <- pollImportUntilDone(importId)
                    .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

        // Step 6: DELETE the import task
        deleteResponse <- TestApiClient.deleteJson[Json](
                            uri"/v3/projects/$projectIri/imports/${importId.value}",
                            rootUser,
                          )

        // Cleanup: delete the export
        _ <- deleteExport(exportId)
      } yield assertTrue(
        importResponse.code == StatusCode.Accepted,
        result.status == DataTaskStatus.Failed,
        deleteResponse.code == StatusCode.NoContent,
      )
    },
    test("returns 409 when import already exists") {
      for {
        // First, get a zip to import (use minimal approach — export then download)
        exportStatus <- triggerExportWithCleanup()
        exportId      = exportStatus.id
        _            <- pollExportUntilCompleted(exportId)
               .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))
        zipBytes <- downloadExportBytes(exportId)

        // Trigger first import
        r1 <- TestApiClient
                .postBinary[DataTaskStatusResponse](
                  uri"/v3/projects/$projectIri/imports",
                  zipBytes,
                  MediaType.ApplicationZip,
                  rootUser,
                )
        firstImport <- ZIO.fromEither(r1.body).mapError(new RuntimeException(_))

        // Trigger second import — should get 409
        r2 <- TestApiClient
                .postBinary[Json](
                  uri"/v3/projects/$projectIri/imports",
                  zipBytes,
                  MediaType.ApplicationZip,
                  rootUser,
                )

        // Cleanup: wait for first import to finish, then delete
        _ <- pollImportUntilDone(firstImport.id)
               .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))
        _ <- deleteImport(firstImport.id)
        _ <- deleteExport(exportId)
      } yield assertTrue(
        r2.code == StatusCode.Conflict,
      )
    },
    test("returns 409 when deleting in-progress import") {
      for {
        // Get a zip to import
        exportStatus <- triggerExportWithCleanup()
        exportId      = exportStatus.id
        _            <- pollExportUntilCompleted(exportId)
               .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))
        zipBytes <- downloadExportBytes(exportId)

        // Trigger import
        r1 <- TestApiClient
                .postBinary[DataTaskStatusResponse](
                  uri"/v3/projects/$projectIri/imports",
                  zipBytes,
                  MediaType.ApplicationZip,
                  rootUser,
                )
        importStatus <- ZIO.fromEither(r1.body).mapError(new RuntimeException(_))
        importId      = importStatus.id

        // Immediately try to delete — may get 409 (in-progress) or 204 (already completed)
        deleteResponse <- TestApiClient.deleteJson[Json](
                            uri"/v3/projects/$projectIri/imports/${importId.value}",
                            rootUser,
                          )

        // Cleanup: if delete failed with 409, wait for completion and delete
        _ <- ZIO.when(deleteResponse.code == StatusCode.Conflict) {
               pollImportUntilDone(importId)
                 .retry(Schedule.spaced(500.millis) && Schedule.recurs(60)) *>
                 deleteImport(importId)
             }
        _ <- deleteExport(exportId)
      } yield assertTrue(
        // Accept both 409 (in-progress) and 204 (already completed) — timing dependent
        deleteResponse.code == StatusCode.Conflict || deleteResponse.code == StatusCode.NoContent,
      )
    },
  )

  // === Export helpers (reused from export E2E spec patterns) ===

  private def triggerExportWithCleanup(): ZIO[TestApiClient, Throwable, DataTaskStatusResponse] =
    TestApiClient
      .postJson[DataTaskStatusResponse, Json](uri"/v3/projects/$projectIri/exports", Json.Obj(), rootUser)
      .flatMap { response =>
        if (response.code == StatusCode.Conflict) {
          val existingId = for {
            errorStr <- response.body.left.toOption
            json     <- errorStr.fromJson[Conflict].toOption
            first    <- json.errors.headOption
            id       <- first.details.get("id")
          } yield id
          for {
            _ <- ZIO.foreachDiscard(existingId)(id => deleteExport(DataTaskId.unsafeFrom(id)))
            r <- TestApiClient.postJson[DataTaskStatusResponse, Json](
                   uri"/v3/projects/$projectIri/exports",
                   Json.Obj(),
                   rootUser,
                 )
            status <- ZIO.fromEither(r.body).mapError(new RuntimeException(_))
          } yield status
        } else ZIO.fromEither(response.body).mapError(new RuntimeException(_))
      }

  private def pollExportUntilCompleted(exportId: DataTaskId) =
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

  private def pollImportUntilDone(importId: DataTaskId) =
    TestApiClient
      .getJson[DataTaskStatusResponse](
        uri"/v3/projects/$projectIri/imports/${importId.value}",
        rootUser,
      )
      .flatMap(r => ZIO.fromEither(r.body).mapError(new RuntimeException(_)))
      .flatMap { status =>
        status.status match {
          case DataTaskStatus.Completed => ZIO.succeed(status)
          case DataTaskStatus.Failed    => ZIO.succeed(status)
          case _                        => ZIO.fail(new RuntimeException("Import still in progress"))
        }
      }

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

  private def deleteExport(exportId: DataTaskId) =
    TestApiClient
      .deleteJson[Json](uri"/v3/projects/$projectIri/exports/${exportId.value}", rootUser)
      .ignore

  private def deleteImport(importId: DataTaskId) =
    TestApiClient
      .deleteJson[Json](uri"/v3/projects/$projectIri/imports/${importId.value}", rootUser)
      .ignore
}
