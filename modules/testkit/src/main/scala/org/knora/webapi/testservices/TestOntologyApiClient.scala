/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testservices

import sttp.client4.*
import zio.*
import zio.json.*
import zio.json.ast.*

import java.time.Instant

import org.knora.webapi.slice.common.KnoraIris.OntologyIri

import ResponseOps.assert200

case class TestOntologyApiClient(private val apiClient: TestApiClient) {
  def getLastModificationDate(ontologyIri: String): Task[String] = for {
    json  <- apiClient.getJson[Json](uri"/v2/ontologies/allentities/$ontologyIri").flatMap(_.assert200)
    cursor = JsonCursor.field("knora-api:lastModificationDate").isObject.field("@value").isString
    lmd   <- ZIO.fromEither(json.get(cursor)).mapError(ResponseError.apply)
  } yield lmd.value
}

object TestOntologyApiClient {
  def getLastModificationDate(iri: String): ZIO[TestOntologyApiClient, Throwable, String] =
    ZIO.serviceWithZIO[TestOntologyApiClient](_.getLastModificationDate(iri))

  def getLastModificationDate(iri: OntologyIri): ZIO[TestOntologyApiClient, Throwable, Instant] = ZIO
    .serviceWithZIO[TestOntologyApiClient](_.getLastModificationDate(iri.toComplexSchema.toIri))
    .mapAttempt(Instant.parse)

  val layer = zio.ZLayer.derive[TestOntologyApiClient]
}
