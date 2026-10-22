/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.resources

import org.apache.jena.query.Dataset
import org.apache.jena.riot.Lang
import org.junit.runner.RunWith
import sttp.client4.*
import sttp.client4.httpclient.zio.HttpClientZioBackend
import sttp.model.*
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.nio.file.Files as ZFiles
import zio.nio.file.Path as ZPath
import zio.test.*

import java.nio.file.Files
import scala.jdk.CollectionConverters.*

import org.knora.bagit.BagIt
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.AppConfig
import org.knora.webapi.e2ez.ValueHasXmlE2E.*
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.`export`.domain.DataTaskStatus
import org.knora.webapi.slice.api.v3.Conflict
import org.knora.webapi.slice.api.v3.projects.DataTaskStatusResponse
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.common.jena.DatasetOps
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.ScopeResolver
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

/**
 * A formatted text value written through `POST /v2/resources` stores `kb:valueHasXml`, byte-identical to the
 * `textValueAsXml` the API serves, and the raw project dump carries it.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlOnResourceCreateE2ESpec extends E2EZSpec {

  private val standard = StandoffMappingIri.StandardMapping

  private def xmlDoc(body: String): String =
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<text documentType="html">$body</text>""".stripMargin

  /** `POST /v2/resources` of an `anything:Thing` with the given `anything:hasText` values; returns the resource IRI. */
  private def createThing(values: Json*): RIO[TestApiClient, String] = {
    val body = Json.Obj(
      "@type"                       -> Json.Str("anything:Thing"),
      "knora-api:attachedToProject" -> Json.Obj("@id" -> Json.Str(projectIri)),
      "rdfs:label"                  -> Json.Str("value-has-xml-on-resource-create-e2e"),
      "anything:hasText"            -> Json.Arr(Chunk.from(values)),
      "@context"                    -> context,
    )
    for {
      doc <- TestApiClient.postJsonLdDocument(uri"/v2/resources", body.toString, anythingUser1).flatMap(_.assert200)
      iri <- ZIO.fromEither(doc.body.getRequiredString(JsonLDKeywords.ID)).mapError(new RuntimeException(_))
    } yield iri
  }

  private def plainText(text: String): Json.Obj =
    Json.Obj("@type" -> Json.Str("knora-api:TextValue"), "knora-api:valueAsString" -> Json.Str(text))

  private def valuesOf(resourceIri: String): RIO[TriplestoreService, List[String]] =
    ts(
      _.query(
        TriplestoreService.Queries.Select(
          s"SELECT ?v WHERE { GRAPH <$dataGraph> { <$resourceIri> <http://www.knora.org/ontology/0001/anything#hasText> ?v } }",
        ),
      ),
    ).map(_.getCol("v").toList)

  // --- Raw project dump (POST /v3/projects/{iri}/exports) ----------------------------------------

  private def deleteExport(id: DataTaskId) =
    TestApiClient.deleteJson[Json](uri"/v3/projects/$projectIri/exports/${id.value}", rootUser).ignore

  private def triggerExport: RIO[TestApiClient, DataTaskStatusResponse] =
    TestApiClient
      .postJson[DataTaskStatusResponse](uri"/v3/projects/$projectIri/exports?skipAssets=true", rootUser)
      .flatMap { response =>
        if (response.code == StatusCode.Conflict) {
          val existingId = for {
            errorStr <- response.body.left.toOption
            json     <- errorStr.fromJson[Conflict].toOption
            first    <- json.errors.headOption
            id       <- first.details.get("id")
          } yield id
          ZIO.foreachDiscard(existingId)(id => deleteExport(DataTaskId.unsafeFrom(id))) *>
            TestApiClient
              .postJson[DataTaskStatusResponse](uri"/v3/projects/$projectIri/exports?skipAssets=true", rootUser)
              .flatMap(r => ZIO.fromEither(r.body).mapError(new RuntimeException(_)))
        } else ZIO.fromEither(response.body).mapError(new RuntimeException(_))
      }

  private def awaitCompleted(id: DataTaskId): RIO[TestApiClient, DataTaskStatusResponse] =
    TestApiClient
      .getJson[DataTaskStatusResponse](uri"/v3/projects/$projectIri/exports/${id.value}", rootUser)
      .flatMap(r => ZIO.fromEither(r.body).mapError(new RuntimeException(_)))
      .flatMap { status =>
        status.status match {
          case DataTaskStatus.Completed => ZIO.succeed(status)
          case DataTaskStatus.Failed    => ZIO.die(new RuntimeException("Export failed"))
          case _                        => ZIO.fail(new RuntimeException("Export still in progress"))
        }
      }
      .retry(Schedule.spaced(500.millis) && Schedule.recurs(120))

  private def downloadExport(id: DataTaskId): RIO[AppConfig & ScopeResolver & JwtService, Array[Byte]] =
    for {
      baseUrl  <- AppConfig.knoraApi(_.externalKnoraApiBaseUrl)
      scope    <- ZIO.serviceWithZIO[ScopeResolver](_.resolve(rootUser))
      jwt      <- ZIO.serviceWithZIO[JwtService](_.createJwt(rootUser.userIri, scope))
      be       <- HttpClientZioBackend()
      response <- basicRequest
                    .get(uri"$baseUrl/v3/projects/$projectIri/exports/$id/download")
                    .auth
                    .bearer(jwt.jwtString)
                    .response(asByteArray)
                    .send(be)
      bytes <- ZIO.fromEither(response.body).mapError(new RuntimeException(_))
    } yield bytes

  /** All `valueHasXml` literals of `valueIri` in the `rdf/data.nq` of a fresh project dump. */
  private def dumpedValueHasXml(valueIri: String) =
    for {
      status <- triggerExport
      _      <- awaitCompleted(status.id)
      bytes  <- downloadExport(status.id).ensuring(deleteExport(status.id))
      xmls   <- ZIO.scoped {
                for {
                  tempDir <- ZFiles.createTempDirectoryScoped(Some("value-has-xml-dump"), Seq.empty)
                  zipPath  = tempDir / "export.zip"
                  _       <- ZFiles.writeBytes(zipPath, Chunk.fromArray(bytes))
                  bagRoot <- BagIt.readAndValidateZip(zipPath).map(_._2)
                  dataNq  <- ZIO.attemptBlocking(
                              Files.walk(bagRoot.toFile.toPath).iterator().asScala.find(_.endsWith("rdf/data.nq")).get,
                            )
                  ds   <- DatasetOps.from(ZPath.fromJava(dataNq), Lang.NQUADS)
                  xmls <- ZIO.attempt(xmlLiterals(ds, valueIri))
                } yield xmls
              }
    } yield xmls

  private def xmlLiterals(ds: Dataset, valueIri: String): List[String] = {
    val model = org.apache.jena.rdf.model.ModelFactory.createDefaultModel()
    model.add(ds.getDefaultModel)
    ds.listNames().asScala.foreach(name => model.add(ds.getNamedModel(name)))
    model
      .listObjectsOfProperty(model.createResource(valueIri), model.createProperty(s"${kb}valueHasXml"))
      .asScala
      .map(_.asLiteral().getLexicalForm)
      .toList
  }

  override val e2eSpec: Spec[env, Any] = suite("valueHasXml written by POST /v2/resources")(
    test("a formatted value stores valueHasXml byte-identical to the served textValueAsXml") {
      for {
        resource <-
          createResourceWithText(xmlDoc("<p>no. 1: <strong>two</strong>, (3); <em>4</em>?</p>"), standard)
        value  <- currentValueOf(resource)
        result <- storedIdenticalToServed(resource, value)
      } yield result
    },
    test("a custom mapping stores valueHasXml byte-identical to the served textValueAsXml") {
      for {
        suffix  <- Random.nextUUID.map(_.toString.take(8))
        mapping <- createCustomMapping(s"OnCreateCustom$suffix")
        xml      =
          xmlDoc(
            """<p>B <span class="event" data-description="a &quot;gala&quot;" data-date="GREGORIAN:2018-03-14 CE">soiree</span> on 14.3.</p>""",
          )
        resource <- createResourceWithText(xml, mapping)
        value    <- currentValueOf(resource)
        result   <- storedIdenticalToServed(resource, value)
      } yield result
    },
    test("exactly the formatted value of a resource carries valueHasXml, the plain sibling has none") {
      for {
        resource <- createThing(
                      plainText("plain 7 + 8 = 15; no markup"),
                      textValueJson(xmlDoc("<p>marked <em>9</em> up, 10.</p>"), standard, None),
                    )
        values    <- valuesOf(resource)
        xmls      <- ZIO.foreach(values)(storedXml)
        withXml   <- ZIO.filter(values)(v => storedXml(v).map(_.isDefined))
        plainIris <- ZIO.filter(values) { v =>
                       selectFirst(
                         s"SELECT ?s WHERE { GRAPH <$dataGraph> { <$v> <${kb}valueHasString> ?s } }",
                         "s",
                       ).map(_.contains("plain 7 + 8 = 15; no markup"))
                     }
      } yield assertTrue(
        values.size == 2,
        withXml.size == 1,
        plainIris.size == 1,
        withXml.forall(!plainIris.contains(_)),
        xmls.flatten.forall(_.contains("marked")),
      )
    },
    test("the raw project dump carries valueHasXml of a value created with its resource") {
      for {
        resource <- createResourceWithText(xmlDoc("<p>dump 11: <strong>twelve</strong> (13)</p>"), standard)
        value    <- currentValueOf(resource)
        stored   <- storedXml(value).someOrFail(new RuntimeException("no valueHasXml stored"))
        dumped   <- dumpedValueHasXml(value)
      } yield assertTrue(stored.nonEmpty, dumped == List(stored))
    },
  )
}
