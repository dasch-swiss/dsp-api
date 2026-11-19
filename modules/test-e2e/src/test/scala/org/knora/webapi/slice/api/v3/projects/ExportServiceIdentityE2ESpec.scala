/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.*
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.nio.file.Files
import zio.test.*

import org.knora.bagit.BagIt
import org.knora.bagit.domain.Bag
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.e2ez.ProjectExportTestSupport.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.api.v3.Conflict
import org.knora.webapi.slice.api.v3.ErrorDetail
import org.knora.webapi.slice.api.v3.V3ErrorCode
import org.knora.webapi.testservices.TestApiClient

/** The export endpoints called with a service token, against the key that this module's configuration trusts. */
@RunWith(classOf[DspZTestJUnitRunner])
class ExportServiceIdentityE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = incunabulaRdfOntologyAndData

  private val projectIri = incunabulaProjectIri.value

  private def bearer[A](token: String): Request[A] => Request[A] = _.auth.bearer(token)

  private def failure(message: String): Throwable = new RuntimeException(message)

  private val serviceToken: URIO[KnoraApi, String] =
    ZIO.serviceWith[KnoraApi](_.externalKnoraApiBaseUrl).flatMap(ExportServiceTestKey.token)

  private def exportUri(id: DataTaskId) = uri"/v3/projects/$projectIri/exports/${id.value}"

  // The slot is server-wide: an export an earlier spec left behind is deleted as root first.
  private val freeSlot: RIO[TestApiClient, Unit] = withCompletedExport(projectIri, skipAssets = true)(_ => ZIO.unit)

  /**
   * Triggers an export as the service and runs `use` with the response. An export the trigger started is deleted as
   * root afterwards, whatever the test's outcome, so a failure here cannot leave the shared slot occupied.
   */
  private def withServiceTrigger[R, A](token: String)(
    use: Response[Either[String, Json]] => RIO[R, A],
  ): RIO[R & TestApiClient, A] =
    ZIO.acquireReleaseWith(
      TestApiClient.postJson[Json](uri"/v3/projects/$projectIri/exports?skipAssets=true", bearer(token)),
    )(response => ZIO.foreachDiscard(startedExport(response))(id => awaitAndDeleteExport(projectIri, id)))(use)

  private def startedExport(response: Response[Either[String, Json]]): Option[DataTaskId] =
    if (response.code != StatusCode.Accepted) None
    else response.body.toOption.flatMap(_.as[DataTaskStatusResponse].toOption).map(_.id)

  // A typed client decodes any JSON body, so an error body may arrive on either side.
  private def conflictErrors(body: Either[String, Json]): List[ErrorDetail] =
    body.fold(_.fromJson[Conflict], _.as[Conflict]).toOption.toList.flatMap(_.errors)

  private def readBag(zipBytes: Array[Byte]): Task[Bag] =
    ZIO.scoped {
      for {
        tempDir <- Files.createTempDirectoryScoped(Some("service-export-e2e"), Seq.empty)
        zipPath  = tempDir / "export.zip"
        _       <- Files.writeBytes(zipPath, Chunk.fromArray(zipBytes))
        result  <- BagIt.readAndValidateZip(zipPath).mapError(e => failure(s"Invalid export zip: $e"))
      } yield result._1
    }

  override val e2eSpec: Spec[env, Any] = suite("Export service identity E2E")(
    test("a service token triggers an export, polls it, downloads a valid zip and deletes it") {
      for {
        _      <- freeSlot
        token  <- serviceToken
        result <- withServiceTrigger(token) { triggered =>
                    for {
                      exportId <-
                        ZIO.fromOption(startedExport(triggered)).orElseFail(failure(s"Trigger failed: $triggered"))
                      completed <- awaitCompleted(projectIri, exportId, bearer(token))
                      download  <- TestApiClient.getBytes(
                                    uri"/v3/projects/$projectIri/exports/${exportId.value}/download",
                                    bearer(token),
                                  )
                      bag         <- ZIO.fromEither(download.body).mapError(failure).flatMap(readBag)
                      deleted     <- TestApiClient.deleteJson[Json](exportUri(exportId), bearer(token))
                      afterDelete <- TestApiClient.getJson[Json](exportUri(exportId), bearer(token))
                    } yield assertTrue(
                      completed.createdBy.value == s"urn:dsp:service:${ExportServiceTestKey.Name}",
                      bag.payloadFiles.map(_.value).contains("rdf/data.nq"),
                      deleted.code == StatusCode.NoContent,
                      afterDelete.code == StatusCode.NotFound,
                    )
                  }
      } yield result
    },
    test("the same token gets 401 on an import, an admin and a v2 endpoint") {
      for {
        token      <- serviceToken
        importCall <- TestApiClient.getJson[Json](
                        uri"/v3/projects/$projectIri/imports/AAAAAAAAAAAAAAAAAAAAAA",
                        bearer(token),
                      )
        adminCall <- TestApiClient.getJson[Json](uri"/admin/users", bearer(token))
        v2Call    <- TestApiClient.getJson[Json](uri"/v2/authentication", bearer(token))
      } yield assertTrue(
        importCall.code == StatusCode.Unauthorized,
        adminCall.code == StatusCode.Unauthorized,
        v2Call.code == StatusCode.Unauthorized,
      )
    },
    test("with a SystemAdmin's export in the slot, the service gets export_exists for it but cannot see its status") {
      withCompletedExport(projectIri, skipAssets = true) { adminExportId =>
        for {
          token     <- serviceToken
          triggered <- withServiceTrigger(token)(ZIO.succeed(_))
          status    <- TestApiClient.getJson[Json](exportUri(adminExportId), bearer(token))
        } yield assertTrue(
          triggered.code == StatusCode.Conflict,
          conflictErrors(triggered.body).map(e => (e.code, e.details)) ==
            List(
              (
                V3ErrorCode.export_exists,
                Map("id" -> adminExportId.value, "projectIri" -> projectIri),
              ),
            ),
          status.code == StatusCode.NotFound,
        )
      }
    },
    test("a service token for another server's audience gets 401") {
      for {
        token     <- ExportServiceTestKey.token("https://api.example.org")
        triggered <- withServiceTrigger(token)(ZIO.succeed(_))
      } yield assertTrue(triggered.code == StatusCode.Unauthorized)
    },
  )
}
