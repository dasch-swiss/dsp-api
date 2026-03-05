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

import org.knora.bagit.BagIt
import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.`export`.domain.DataTaskStatus
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.api.v3.Conflict
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.ScopeResolver
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.testservices.TestAdminApiClient
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.testservices.TestDspIngestClient

object ProjectMigrationRoundTripE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = incunabulaRdfOntologyAndData

  private val projectIri = incunabulaProjectIri.value
  private val shortcode  = Shortcode.unsafeFrom("0803")
  private val db         = ZIO.serviceWithZIO[TriplestoreService]

  override val e2eSpec: Spec[env, Any] = suite("Project Migration Round-Trip E2E")(
    test("export, erase, import round-trip succeeds (without assets)") {
      for {
        exportIdRef <- Ref.make(Option.empty[DataTaskId])
        importIdRef <- Ref.make(Option.empty[DataTaskId])
        result      <- {
                    for {
                      // Step 1: Collect user IRIs belonging to the project (needed for cleanup before import)
                      userIris <- findProjectUserIris(projectIri)

                      // Step 2: Export the project (skip assets for this test)
                      exportStatus <- triggerExportWithCleanup(skipAssets = true)
                      exportId      = exportStatus.id
                      _            <- exportIdRef.set(Some(exportId))

                      // Step 3: Poll export until completed
                      _ <- pollExportUntilCompleted(exportId)
                             .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

                      // Step 4: Download the export zip
                      zipBytes <- downloadExportBytes(exportId)

                      // Step 5: Erase the project (keepAssets=true since we exported without assets)
                      eraseResp <- TestApiClient.deleteJson[Json](
                                     uri"/admin/projects/shortcode/${shortcode.value}/erase?keepAssets=true",
                                     rootUser,
                                   )

                      // Step 6: Hard-delete the users from the admin graph so import validation passes
                      _ <- ZIO.foreachDiscard(userIris)(hardDeleteUser)

                      // Step 7: Verify project is gone
                      projectGone <- TestAdminApiClient.getProject(shortcode, rootUser)

                      // Step 8: Import the exported zip
                      importStatus <- triggerImportWithCleanup(zipBytes)
                      importId      = importStatus.id
                      _            <- importIdRef.set(Some(importId))

                      // Step 9: Poll import until completed
                      completed <- pollImportUntilDone(importId)
                                     .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

                      // Step 10: Verify project is restored
                      projectRestored <- TestAdminApiClient.getProject(shortcode, rootUser)

                      // Cleanup
                      _ <- deleteImport(importId)
                      _ <- importIdRef.set(None)
                      _ <- deleteExport(exportId)
                      _ <- exportIdRef.set(None)
                    } yield assertTrue(
                      eraseResp.code == StatusCode.Ok,
                      projectGone.code == StatusCode.NotFound,
                      completed.status == DataTaskStatus.Completed,
                      projectRestored.code == StatusCode.Ok,
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
    test("export with assets, erase, import with assets round-trip succeeds") {
      for {
        exportIdRef <- Ref.make(Option.empty[DataTaskId])
        importIdRef <- Ref.make(Option.empty[DataTaskId])
        result      <- {
                    for {
                      // Step 1: Upload a test asset to ingest for the project
                      _ <- TestDspIngestClient.createImageAsset(shortcode)

                      // Step 2: Collect user IRIs belonging to the project
                      userIris <- findProjectUserIris(projectIri)

                      // Step 3: Export the project WITH assets (skipAssets=false, the default)
                      exportStatus <- triggerExportWithCleanup(skipAssets = false)
                      exportId      = exportStatus.id
                      _            <- exportIdRef.set(Some(exportId))

                      // Step 4: Poll export until completed
                      _ <- pollExportUntilCompleted(exportId)
                             .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

                      // Step 5: Download the export zip
                      zipBytes <- downloadExportBytes(exportId)

                      // Step 6: Validate the BagIt zip contains assets/assets.zip
                      hasAssets <- ZIO.scoped {
                                     for {
                                       tempDir <- Files.createTempDirectoryScoped(Some("bagit-asset-test"), Seq.empty)
                                       zipPath  = tempDir / "export.zip"
                                       _       <- Files.writeBytes(zipPath, Chunk.fromArray(zipBytes))
                                       result  <- BagIt.readAndValidateZip(zipPath)
                                     } yield result._1.payloadFiles.map(_.value).exists(_ == "assets/assets.zip")
                                   }

                      // Step 7: Erase the project INCLUDING assets (keepAssets=false)
                      eraseResp <- TestApiClient.deleteJson[Json](
                                     uri"/admin/projects/shortcode/${shortcode.value}/erase",
                                     rootUser,
                                   )

                      // Step 8: Hard-delete the users
                      _ <- ZIO.foreachDiscard(userIris)(hardDeleteUser)

                      // Step 9: Verify project is gone
                      projectGone <- TestAdminApiClient.getProject(shortcode, rootUser)

                      // Step 10: Import the exported zip (includes assets)
                      importStatus <- triggerImportWithCleanup(zipBytes)
                      importId      = importStatus.id
                      _            <- importIdRef.set(Some(importId))

                      // Step 11: Poll import until completed
                      completed <- pollImportUntilDone(importId)
                                     .retry(Schedule.spaced(500.millis) && Schedule.recurs(60))

                      // Step 12: Verify project is restored
                      projectRestored <- TestAdminApiClient.getProject(shortcode, rootUser)

                      // Cleanup
                      _ <- deleteImport(importId)
                      _ <- importIdRef.set(None)
                      _ <- deleteExport(exportId)
                      _ <- exportIdRef.set(None)
                    } yield assertTrue(
                      hasAssets,
                      eraseResp.code == StatusCode.Ok,
                      projectGone.code == StatusCode.NotFound,
                      completed.status == DataTaskStatus.Completed,
                      projectRestored.code == StatusCode.Ok,
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

  // === SPARQL helpers for user cleanup ===

  private def findProjectUserIris(prjIri: String): ZIO[TriplestoreService, Throwable, Seq[String]] =
    db(
      _.query(Select(s"""
        SELECT ?user WHERE {
          GRAPH <http://www.knora.org/data/admin> {
            ?user a <http://www.knora.org/ontology/knora-admin#User> .
            ?user <http://www.knora.org/ontology/knora-admin#isInProject> <$prjIri> .
          }
        }
      """)),
    ).map(_.getCol("user"))

  private def hardDeleteUser(userIri: String): ZIO[TriplestoreService, Throwable, Unit] =
    db(_.query(Update(s"""
      DELETE WHERE {
        GRAPH <http://www.knora.org/data/admin> {
          <$userIri> ?p ?o .
        }
      }
    """)))

  // === Export helpers ===

  private def triggerExportWithCleanup(
    skipAssets: Boolean,
  ): ZIO[TestApiClient, Throwable, DataTaskStatusResponse] =
    TestApiClient
      .postJson[DataTaskStatusResponse, Json](
        uri"/v3/projects/$projectIri/exports?skipAssets=$skipAssets",
        Json.Obj(),
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
            _ <- ZIO.foreachDiscard(existingId)(id => deleteExport(DataTaskId.unsafeFrom(id)))
            r <- TestApiClient.postJson[DataTaskStatusResponse, Json](
                   uri"/v3/projects/$projectIri/exports?skipAssets=$skipAssets",
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

  // === Import helpers ===

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

  // === Cleanup helpers ===

  private def deleteExport(exportId: DataTaskId) =
    TestApiClient
      .deleteJson[Json](uri"/v3/projects/$projectIri/exports/${exportId.value}", rootUser)
      .ignore

  private def deleteImport(importId: DataTaskId) =
    TestApiClient
      .deleteJson[Json](uri"/v3/projects/$projectIri/imports/${importId.value}", rootUser)
      .ignore
}
