/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2ez

import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.*
import zio.*
import zio.json.*
import zio.json.ast.Json

import org.knora.webapi.config.AppConfig
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.`export`.domain.DataTaskStatus
import org.knora.webapi.slice.api.v3.Conflict
import org.knora.webapi.slice.api.v3.projects.DataTaskStatusResponse
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.ScopeResolver
import org.knora.webapi.testservices.TestApiClient

/**
 * Helpers for E2E specs that drive the project export endpoints (`/v3/projects/{iri}/exports`) as root.
 * The export slot is server-wide: [[triggerExport]] deletes a stale export left by an earlier run before it retries.
 */
object ProjectExportTestSupport {

  private def exportsUri(projectIri: String, skipAssets: Boolean) =
    if (skipAssets) uri"/v3/projects/$projectIri/exports?skipAssets=true" else uri"/v3/projects/$projectIri/exports"

  private def failure(message: String): Throwable = new RuntimeException(message)

  def deleteExportResponse(projectIri: String, id: DataTaskId): RIO[TestApiClient, Response[Either[String, Json]]] =
    TestApiClient.deleteJson[Json](uri"/v3/projects/$projectIri/exports/${id.value}", rootUser)

  def deleteExport(projectIri: String, id: DataTaskId): URIO[TestApiClient, Unit] =
    deleteExportResponse(projectIri, id).ignore

  /** Triggers an export of `projectIri`; an export already holding the slot is deleted and the trigger repeated once. */
  def triggerExport(projectIri: String, skipAssets: Boolean = false): RIO[TestApiClient, DataTaskStatusResponse] = {
    val trigger = TestApiClient.postJson[DataTaskStatusResponse](exportsUri(projectIri, skipAssets), rootUser)
    trigger.flatMap { response =>
      if (response.code == StatusCode.Conflict) {
        val existingId = for {
          errorStr <- response.body.left.toOption
          json     <- errorStr.fromJson[Conflict].toOption
          first    <- json.errors.headOption
          id       <- first.details.get("id")
        } yield id
        ZIO.foreachDiscard(existingId)(id => deleteExport(projectIri, DataTaskId.unsafeFrom(id))) *>
          trigger.flatMap { retried =>
            if (retried.code == StatusCode.Conflict)
              ZIO.fail(failure(s"Export of $projectIri still conflicts after deleting the existing export"))
            else ZIO.fromEither(retried.body).mapError(failure)
          }
      } else ZIO.fromEither(response.body).mapError(failure)
    }
  }

  /** One status check: succeeds when completed, dies when failed, fails while still in progress. */
  def pollUntilCompleted(projectIri: String, id: DataTaskId): RIO[TestApiClient, DataTaskStatusResponse] =
    TestApiClient
      .getJson[DataTaskStatusResponse](uri"/v3/projects/$projectIri/exports/${id.value}", rootUser)
      .flatMap(r => ZIO.fromEither(r.body).mapError(failure))
      .flatMap { status =>
        status.status match {
          case DataTaskStatus.Completed => ZIO.succeed(status)
          case DataTaskStatus.Failed    => ZIO.die(failure("Export failed"))
          case _                        => ZIO.fail(failure("Export still in progress"))
        }
      }

  def awaitCompleted(projectIri: String, id: DataTaskId): RIO[TestApiClient, DataTaskStatusResponse] =
    pollUntilCompleted(projectIri, id).retry(Schedule.spaced(500.millis) && Schedule.recurs(120))

  /** Downloads the export zip of `id`, requested through the path of `projectIri`. */
  def downloadExport(projectIri: String, id: DataTaskId): RIO[AppConfig & ScopeResolver & JwtService, Array[Byte]] =
    ZIO.scoped {
      for {
        baseUrl  <- AppConfig.knoraApi(_.externalKnoraApiBaseUrl)
        scope    <- ZIO.serviceWithZIO[ScopeResolver](_.resolve(rootUser))
        jwt      <- ZIO.serviceWithZIO[JwtService](_.createJwt(rootUser.userIri, scope))
        be       <- HttpClientZioBackend.scoped()
        response <- basicRequest
                      .get(uri"$baseUrl/v3/projects/$projectIri/exports/${id.value}/download")
                      .auth
                      .bearer(jwt.jwtString)
                      .response(asByteArray)
                      .send(be)
        bytes <- ZIO.fromEither(response.body).mapError(failure)
      } yield bytes
    }

  /**
   * Triggers an export, waits until it is completed, runs `use` with its id and deletes the export afterwards, also
   * when `use` or the wait fails.
   */
  def withCompletedExport[R, A](projectIri: String, skipAssets: Boolean = false)(
    use: DataTaskId => RIO[R, A],
  ): RIO[R & TestApiClient, A] =
    ZIO.acquireReleaseWith(triggerExport(projectIri, skipAssets))(status => deleteExport(projectIri, status.id)) {
      status => awaitCompleted(projectIri, status.id) *> use(status.id)
    }
}
