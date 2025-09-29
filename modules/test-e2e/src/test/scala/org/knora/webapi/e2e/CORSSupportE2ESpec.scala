/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e

import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.client4.wrappers.ResolveRelativeUrisBackend
import sttp.model.*
import sttp.model.HeaderNames.*
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*

object CORSSupportE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = anythingRdfOntologyAndData

  private val corsClient = ZIO.serviceWithZIO[CorsClient]

  final case class CorsClient(
    private val apiConfig: KnoraApi,
    private val be: StreamBackend[Task, ZioStreams],
  ) {
    private val backend: StreamBackend[Task, ZioStreams] =
      ResolveRelativeUrisBackend(be, uri"${apiConfig.externalKnoraApiBaseUrl}")
    private val originHeader = Header(Origin, "http://example.com")

    def options(uri: Uri, hs: Header*): Task[Response[Either[String, String]]] = {
      val req = basicRequest.options(uri).headers(hs :+ originHeader: _*)
      backend.send(req)
    }

    def get(uri: Uri, hs: Header*): Task[Response[Either[String, String]]] = {
      val req = basicRequest.get(uri).headers(hs :+ originHeader: _*)
      backend.send(req)
    }
  }

  override val e2eSpec = suite("A Route with enabled CORS support should")(
    test("accept valid pre-flight requests") {
      corsClient(_.options(uri"/admin/projects", Header(AccessControlRequestMethod, "GET")))
        .map(response =>
          assertTrue(
            response.code == StatusCode.Ok,
            response.header(AccessControlAllowOrigin).contains("http://example.com"),
            response.header(AccessControlAllowMethods).exists(_.contains("GET")),
            response.header(AccessControlAllowMethods).exists(_.contains("PUT")),
            response.header(AccessControlAllowMethods).exists(_.contains("POST")),
            response.header(AccessControlAllowMethods).exists(_.contains("DELETE")),
            response.header(AccessControlAllowMethods).exists(_.contains("PATCH")),
            response.header(AccessControlAllowMethods).exists(_.contains("HEAD")),
            response.header(AccessControlAllowMethods).exists(_.contains("OPTIONS")),
            response.header(AccessControlMaxAge).contains("1800"),
            response.header(AccessControlAllowCredentials).contains("true"),
          ),
        )

    },
    test("send `Access-Control-Allow-Origin` header when the Knora resource is found ") {
      val resourceIri = "http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mg"
      corsClient(_.get(uri"/v2/resources/$resourceIri"))
        .map(response =>
          assertTrue(
            response.code == StatusCode.Ok,
            response.header(AccessControlAllowOrigin).contains("http://example.com"),
          ),
        )
    },
    test("send `Access-Control-Allow-Origin` header when the Knora resource is NOT found ") {
      val resourceIri = "http://rdfh.ch/0001/nonexistent"
      corsClient(_.get(uri"/v2/resources/$resourceIri"))
        .map(response =>
          assertTrue(
            response.code == StatusCode.NotFound,
            response.header(AccessControlAllowOrigin).contains("http://example.com"),
          ),
        )
    },
  ).provideSomeAuto(HttpClientZioBackend.layer().orDie >>> ZLayer.derive[CorsClient])
}
