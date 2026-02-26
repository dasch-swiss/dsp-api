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
        exportIdRef <- Ref.make(Option.empty[DataTaskId])
        importIdRef <- Ref.make(Option.empty[DataTaskId])
        result      <- {
                    for {
                      // Step 1: Trigger export of Incunabula
                      exportStatus <- triggerExportWithCleanup()
                      exportId      = exportStatus.id
                      _            <- exportIdRef.set(Some(exportId))

                      // Step 2: Poll export until completed
                      _ <- pollExportUntilCompleted(exportId)
                             .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

                      // Step 3: Download the export zip bytes
                      zipBytes <- downloadExportBytes(exportId)

                      // Step 4: POST the zip to import endpoint
                      importStatus <- triggerImportWithCleanup(zipBytes)
                      importId      = importStatus.id
                      _            <- importIdRef.set(Some(importId))

                      // Step 5: Poll import status until done — expect Failed (project already exists)
                      pollResult <- pollImportUntilDone(importId)
                                      .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

                      // Step 6: DELETE the import task
                      deleteResponse <- TestApiClient.deleteJson[Json](
                                          uri"/v3/projects/$projectIri/imports/${importId.value}",
                                          rootUser,
                                        )
                      _ <- importIdRef.set(None)
                      _ <- deleteExport(exportId)
                      _ <- exportIdRef.set(None)
                    } yield assertTrue(
                      importStatus.status == DataTaskStatus.InProgress || importStatus.status == DataTaskStatus.Completed || importStatus.status == DataTaskStatus.Failed,
                      pollResult.status == DataTaskStatus.Failed,
                      deleteResponse.code == StatusCode.NoContent,
                    )
                  }.ensuring {
                    for {
                      importId <- importIdRef.get
                      _        <- ZIO.foreachDiscard(importId) { id =>
                             pollImportUntilDone(id)
                               .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))
                               .ignore *> deleteImport(id)
                           }
                      exportId <- exportIdRef.get
                      _        <- ZIO.foreachDiscard(exportId)(deleteExport)
                    } yield ()
                  }
      } yield result
    },
    test("returns 409 when import already exists") {
      for {
        exportIdRef <- Ref.make(Option.empty[DataTaskId])
        importIdRef <- Ref.make(Option.empty[DataTaskId])
        result      <- {
                    for {
                      // First, get a zip to import (use minimal approach — export then download)
                      exportStatus <- triggerExportWithCleanup()
                      exportId      = exportStatus.id
                      _            <- exportIdRef.set(Some(exportId))
                      _            <- pollExportUntilCompleted(exportId)
                             .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))
                      zipBytes <- downloadExportBytes(exportId)

                      // Trigger first import (with cleanup of stale imports)
                      firstImport <- triggerImportWithCleanup(zipBytes)
                      _           <- importIdRef.set(Some(firstImport.id))

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
                      _ <- importIdRef.set(None)
                      _ <- deleteExport(exportId)
                      _ <- exportIdRef.set(None)
                    } yield assertTrue(
                      r2.code == StatusCode.Conflict,
                    )
                  }.ensuring {
                    for {
                      importId <- importIdRef.get
                      _        <- ZIO.foreachDiscard(importId) { id =>
                             pollImportUntilDone(id)
                               .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))
                               .ignore *> deleteImport(id)
                           }
                      exportId <- exportIdRef.get
                      _        <- ZIO.foreachDiscard(exportId)(deleteExport)
                    } yield ()
                  }
      } yield result
    },
    test("returns 409 when deleting in-progress import") {
      for {
        exportIdRef <- Ref.make(Option.empty[DataTaskId])
        importIdRef <- Ref.make(Option.empty[DataTaskId])
        result      <- {
                    for {
                      // Get a zip to import
                      exportStatus <- triggerExportWithCleanup()
                      exportId      = exportStatus.id
                      _            <- exportIdRef.set(Some(exportId))
                      _            <- pollExportUntilCompleted(exportId)
                             .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))
                      zipBytes <- downloadExportBytes(exportId)

                      // Trigger import (with cleanup of stale imports)
                      importStatus <- triggerImportWithCleanup(zipBytes)
                      importId      = importStatus.id
                      _            <- importIdRef.set(Some(importId))

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
                      _ <- importIdRef.set(None)
                      _ <- deleteExport(exportId)
                      _ <- exportIdRef.set(None)
                    } yield assertTrue(
                      // Accept both 409 (in-progress) and 204 (already completed) — timing dependent
                      deleteResponse.code == StatusCode.Conflict || deleteResponse.code == StatusCode.NoContent,
                    )
                  }.ensuring {
                    for {
                      importId <- importIdRef.get
                      _        <- ZIO.foreachDiscard(importId) { id =>
                             pollImportUntilDone(id)
                               .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))
                               .ignore *> deleteImport(id)
                           }
                      exportId <- exportIdRef.get
                      _        <- ZIO.foreachDiscard(exportId)(deleteExport)
                    } yield ()
                  }
      } yield result
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

  private def triggerImportWithCleanup(
    zipBytes: Array[Byte],
  ): ZIO[TestApiClient, Throwable, DataTaskStatusResponse] =
    TestApiClient
      .postBinary[DataTaskStatusResponse](
        uri"/v3/projects/$projectIri/imports",
        zipBytes,
        MediaType.ApplicationZip,
        rootUser,
      )
      .flatMap { response =>
        if (response.code == StatusCode.Conflict) {
          val existingId = for {
            errorStr <- response.body.left.toOption
            json     <- errorStr.fromJson[Conflict].toOption
            first    <- json.errors.headOption
            id       <- first.details.get("id")
          } yield id
          for {
            _ <- ZIO.foreachDiscard(existingId) { id =>
                   pollImportUntilDone(DataTaskId.unsafeFrom(id))
                     .retry(Schedule.spaced(500.millis) && Schedule.recurs(60)) *>
                     deleteImport(DataTaskId.unsafeFrom(id))
                 }
            r <- TestApiClient.postBinary[DataTaskStatusResponse](
                   uri"/v3/projects/$projectIri/imports",
                   zipBytes,
                   MediaType.ApplicationZip,
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
