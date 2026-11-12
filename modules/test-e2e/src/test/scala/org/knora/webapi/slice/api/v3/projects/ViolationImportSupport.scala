/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import sttp.client4.*
import sttp.model.*
import zio.*
import zio.json.*
import zio.json.ast.Json

import java.nio.charset.StandardCharsets

import org.knora.webapi.sharedtestdata.SharedTestDataADM.rootUser
import org.knora.webapi.slice.`export`.domain.DataTaskStatus
import org.knora.webapi.testservices.TestApiClient

/** Imports a JSON-LD fixture of `test_data/bulk-import-violations/` and waits for the task to end. */
trait ViolationImportSupport {

  /** The IRI of the project the fixtures are imported into. */
  protected def projectIri: String

  /** The username of the project member the import runs on behalf of. */
  protected def onBehalfOfUser: String

  private val jsonLdType = MediaType.unsafeApply("application", "ld+json")
  private val labelIri   = "http://www.w3.org/2000/01/rdf-schema#label"

  /** The ended import: the trigger status code and the final task status. The task is deleted afterwards. */
  protected final case class EndedImport(triggerCode: StatusCode, status: DataTaskStatusResponse) {
    def failed: Boolean             = status.status == DataTaskStatus.Failed
    def errorMessage: String        = status.errorMessage.getOrElse("")
    def accepted: Boolean           = triggerCode == StatusCode.Accepted
    def reports(s: String): Boolean = errorMessage.contains(s)
  }

  protected def readFixture(name: String): Task[String] =
    ZIO.attemptBlocking(scala.io.Source.fromResource(s"test_data/bulk-import-violations/$name").mkString)

  /** The IRI of every top-level resource of the payload, keyed by its rdfs:label. */
  protected def resourceIrisByLabel(payload: String): Task[Map[String, String]] =
    ZIO
      .fromEither(payload.fromJson[List[Json.Obj]])
      .mapError(new RuntimeException(_))
      .map(_.flatMap { r =>
        for {
          id    <- r.get("@id").flatMap(_.asString)
          label <- r.get(labelIri).flatMap(_.asString)
        } yield label -> id
      }.toMap)

  protected def importAndWait(payload: String): ZIO[TestApiClient, Throwable, EndedImport] =
    ZIO.scoped {
      for {
        trigger <- TestApiClient.postBinary[DataTaskStatusResponse](
                     uri"/v3/projects/$projectIri/data-imports?onBehalfOfUser=$onBehalfOfUser",
                     payload.getBytes(StandardCharsets.UTF_8),
                     jsonLdType,
                     rootUser,
                   )
        started <- ZIO.fromEither(trigger.body).mapError(new RuntimeException(_))
        // A finished task blocks the next import, so it is deleted whatever the test outcome.
        _ <- ZIO.addFinalizer(
               TestApiClient
                 .deleteJson[Json](uri"/v3/projects/$projectIri/data-imports/${started.id.value}", rootUser)
                 .ignore,
             )
        ended <- poll(started).retry(Schedule.spaced(500.millis) && Schedule.recurs(120))
      } yield EndedImport(trigger.code, ended)
    }

  private def poll(started: DataTaskStatusResponse) =
    TestApiClient
      .getJson[DataTaskStatusResponse](uri"/v3/projects/$projectIri/data-imports/${started.id.value}", rootUser)
      .flatMap(r => ZIO.fromEither(r.body).mapError(new RuntimeException(_)))
      .flatMap { status =>
        status.status match {
          case DataTaskStatus.Completed | DataTaskStatus.Failed => ZIO.succeed(status)
          case _                                                => ZIO.fail(new RuntimeException("Import still in progress"))
        }
      }

  protected def resourceNotFound(iri: String): ZIO[TestApiClient, Throwable, Boolean] =
    TestApiClient.getJsonLd(uri"/v2/resources/$iri", rootUser).map(_.code == StatusCode.NotFound)
}
