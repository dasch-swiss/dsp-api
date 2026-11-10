/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.StatusCode
import sttp.model.Uri
import zio.*
import zio.json.ast.Json
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.DataImportLimit.overLimitBytes
import org.knora.webapi.slice.api.RawHeadOnlyPost
import org.knora.webapi.testservices.TestApiClient

/**
 * The v3 import, export and data-import routes have no repeatable, side-effect-free valid request, so they are exempt
 * from the authorization matrix. This spec proves their SystemAdmin policy is decided in the security logic: the
 * dummy bodies and ids below are never read, because the caller is refused first.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class V3SystemAdminRoutesE2ESpec extends E2EZSpec {

  private val projectIri = "http://rdfh.ch/projects/0001"
  private val taskId     = "AAAAAAAAAAAAAAAAAAAAAA"

  private def send(method: String, target: Uri)(user: Option[User]): RIO[env, StatusCode] = {
    val dummy = Json.Obj()
    (method, user) match {
      case ("GET", None)       => TestApiClient.getJson[Json](target).map(_.code)
      case ("GET", Some(u))    => TestApiClient.getJson[Json](target, u).map(_.code)
      case ("DELETE", None)    => TestApiClient.deleteJson[Json](target).map(_.code)
      case ("DELETE", Some(u)) => TestApiClient.deleteJson[Json](target, u).map(_.code)
      case (_, None)           => TestApiClient.postJson[Json, Json](target, dummy).map(_.code)
      case (_, Some(u))        => TestApiClient.postJson[Json, Json](target, dummy, u).map(_.code)
    }
  }

  private val routes: List[(String, Uri)] = List(
    "POST"   -> uri"/v3/projects/$projectIri/exports",
    "GET"    -> uri"/v3/projects/$projectIri/exports/$taskId",
    "DELETE" -> uri"/v3/projects/$projectIri/exports/$taskId",
    "GET"    -> uri"/v3/projects/$projectIri/exports/$taskId/download",
    "POST"   -> uri"/v3/projects/$projectIri/imports",
    "GET"    -> uri"/v3/projects/$projectIri/imports/$taskId",
    "DELETE" -> uri"/v3/projects/$projectIri/imports/$taskId",
    "POST"   -> uri"/v3/projects/$projectIri/data-imports?onBehalfOfUser=nobody",
    "GET"    -> uri"/v3/projects/$projectIri/data-imports/$taskId",
    "DELETE" -> uri"/v3/projects/$projectIri/data-imports/$taskId",
    "POST"   -> uri"/v3/export/resources/oai",
  )

  private val dataImportPostWithoutCredentials =
    test("POST data-imports without credentials declaring a Content-Length above the size limit returns 401, not 413") {
      for {
        api      <- ZIO.service[KnoraApi]
        rejected <- RawHeadOnlyPost(
                      api.externalHost,
                      api.externalPort,
                      "/v3/projects/http%3A%2F%2Frdfh.ch%2Fprojects%2F0001/data-imports?onBehalfOfUser=nobody",
                      "application/ld+json",
                      overLimitBytes,
                      bearer = None,
                    )
      } yield assertTrue(
        rejected.statusLine == "HTTP/1.1 401 Unauthorized",
        rejected.header("Connection").exists(_.equalsIgnoreCase("close")),
        rejected.serverClosed,
      )
    }

  override val e2eSpec: Spec[env, Any] = suite("v3 SystemAdmin routes")(
    (dataImportPostWithoutCredentials +: routes.flatMap { (method, target) =>
      List(
        test(s"$method ${target.path.mkString("/")} without credentials returns 401") {
          send(method, target)(None).map(code => assertTrue(code == StatusCode.Unauthorized))
        },
        test(s"$method ${target.path.mkString("/")} as a non-admin returns 403") {
          send(method, target)(Some(normalUser)).map(code => assertTrue(code == StatusCode.Forbidden))
        },
      )
    })*,
  )
}
