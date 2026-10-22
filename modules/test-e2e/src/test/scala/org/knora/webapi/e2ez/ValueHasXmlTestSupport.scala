/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2ez

import sttp.client4.*
import zio.*
import zio.json.ast.Json
import zio.test.*

import java.nio.file.Paths

import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.standoff.service.StandoffMappingService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.testservices.RequestsUpdates.RequestUpdate
import org.knora.webapi.testservices.RequestsUpdates.addVersionQueryParam
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

/**
 * Helpers for E2E specs that write `anything:hasText` values of project 0001 as XML and compare the stored
 * `kb:valueHasXml` literal with the XML served by the API.
 *
 * Which comparison a spec uses depends on who wrote the literal. Write-path specs (`/v2/values`, resource create) use
 * [[storedByteIdenticalToServed]]. The backfill uses [[storedCanonicallyEqualsServed]]: it re-renders standoff stored
 * before the literal existed and compares documents, not bytes. The bulk import path is proved by the isomorphism with
 * resource create in `BulkImportParityE2ESpec`.
 */
object ValueHasXmlTestSupport {

  val projectIri: String   = "http://rdfh.ch/projects/0001"
  val dataGraph: String    = "http://www.knora.org/data/0001/anything"
  val anythingOnto: String = "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  val hasTextIri: String   = s"${anythingOnto}hasText"
  val kb: String           = "http://www.knora.org/ontology/knora-base#"

  private val anythingOntoInternal = "http://www.knora.org/ontology/0001/anything#"

  def textValueJson(xml: String, mapping: StandoffMappingIri, valueIri: Option[String]): Json.Obj = {
    val id = valueIri.map(iri => Chunk("@id" -> Json.Str(iri))).getOrElse(Chunk.empty)
    Json.Obj(
      Chunk[(String, Json)]("@type" -> Json.Str("knora-api:TextValue")) ++ id ++ Chunk(
        "knora-api:textValueAsXml"      -> Json.Str(xml),
        "knora-api:textValueHasMapping" -> Json.Obj("@id" -> Json.Str(mapping.value)),
      ),
    )
  }

  val context: Json.Obj = Json.Obj(
    "knora-api" -> Json.Str("http://api.knora.org/ontology/knora-api/v2#"),
    "rdfs"      -> Json.Str("http://www.w3.org/2000/01/rdf-schema#"),
    "anything"  -> Json.Str(anythingOnto),
  )

  def xmlDoc(body: String): String =
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<text documentType="html">$body</text>""".stripMargin

  def plainTextJson(text: String, valueIri: Option[String] = None): Json.Obj = {
    val id = valueIri.map(iri => Chunk("@id" -> Json.Str(iri))).getOrElse(Chunk.empty)
    Json.Obj(
      Chunk[(String, Json)]("@type" -> Json.Str("knora-api:TextValue")) ++ id ++ Chunk(
        "knora-api:valueAsString" -> Json.Str(text),
      ),
    )
  }

  private def iriOf(doc: JsonLDDocument): Task[String] =
    ZIO.fromEither(doc.body.getRequiredString(JsonLDKeywords.ID)).mapError(new RuntimeException(_))

  /** `POST /v2/resources` of an `anything:Thing` of project 0001 with the given `anything:hasText` values; returns its IRI. */
  def createThing(textValues: Json*): RIO[TestApiClient, String] = {
    val values = textValues match {
      case Seq()      => Chunk.empty
      case Seq(value) => Chunk("anything:hasText" -> value)
      case many       => Chunk("anything:hasText" -> Json.Arr(Chunk.from(many)))
    }
    val body = Json.Obj(
      Chunk[(String, Json)](
        "@type"                       -> Json.Str("anything:Thing"),
        "knora-api:attachedToProject" -> Json.Obj("@id" -> Json.Str(projectIri)),
        "rdfs:label"                  -> Json.Str("value-has-xml-e2e"),
      ) ++ values ++ Chunk("@context" -> context),
    )
    TestApiClient
      .postJsonLdDocument(uri"/v2/resources", body.toString, anythingUser1)
      .flatMap(_.assert200)
      .flatMap(iriOf)
  }

  /** Creates an `anything:Thing` of project 0001 with one XML text value and returns its IRI. */
  def createResourceWithText(xml: String, mapping: StandoffMappingIri): RIO[TestApiClient, String] =
    createThing(textValueJson(xml, mapping, None))

  /** `POST /v2/values`: adds the text value to the resource and returns the IRI of the new value. */
  def createValue(resourceIri: String, value: Json.Obj): RIO[TestApiClient, String] = {
    val body = Json.Obj(
      "@id"              -> Json.Str(resourceIri),
      "@type"            -> Json.Str("anything:Thing"),
      "anything:hasText" -> value,
      "@context"         -> context,
    )
    TestApiClient.postJsonLdDocument(uri"/v2/values", body.toString, anythingUser1).flatMap(_.assert200).flatMap(iriOf)
  }

  def updateText(
    resourceIri: String,
    valueIri: String,
    xml: String,
    mapping: StandoffMappingIri,
  ): RIO[TestApiClient, Unit] = {
    val body = Json.Obj(
      "@id"              -> Json.Str(resourceIri),
      "@type"            -> Json.Str("anything:Thing"),
      "anything:hasText" -> textValueJson(xml, mapping, Some(valueIri)),
      "@context"         -> context,
    )
    TestApiClient.putJsonLd(uri"/v2/values", body.toString, anythingUser1).flatMap(_.assert200).unit
  }

  def deleteText(resourceIri: String, valueIri: String): RIO[TestApiClient, Unit] = {
    val body = Json.Obj(
      "@id"              -> Json.Str(resourceIri),
      "@type"            -> Json.Str("anything:Thing"),
      "anything:hasText" -> Json.Obj(
        "@id"                     -> Json.Str(valueIri),
        "@type"                   -> Json.Str("knora-api:TextValue"),
        "knora-api:deleteComment" -> Json.Str("deleted by an e2e spec"),
      ),
      "@context" -> context,
    )
    TestApiClient.postJsonLd(uri"/v2/values/delete", body.toString, anythingUser1).flatMap(_.assert200).unit
  }

  /**
   * Creates the mapping `mappingName` in project 0001 from `mappingFile`, by default
   * `test_data/test_route/texts/mappingForHTML.xml`. A mapping name can be created only once per project.
   */
  def createCustomMapping(
    mappingName: String,
    mappingFile: java.nio.file.Path = Paths.get("test_data/test_route/texts/mappingForHTML.xml"),
  ): RIO[TestApiClient, StandoffMappingIri] = {
    val params =
      s"""{
         |  "knora-api:mappingHasName": "$mappingName",
         |  "knora-api:attachedToProject": { "@id": "$projectIri" },
         |  "rdfs:label": "mapping $mappingName of an e2e spec",
         |  "@context": {
         |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
         |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
         |  }
         |}""".stripMargin
    val body = Seq(
      multipart("json", params).contentType("application/json"),
      multipartFile("xml", mappingFile).contentType("text/xml(UTF-8)"),
    )
    TestApiClient
      .postMultiPart[Json](uri"/v2/mapping", body, anythingUser1)
      .flatMap(_.assert200)
      .as(StandoffMappingIri.unsafeFrom(s"$projectIri/mappings/$mappingName"))
  }

  private def ts[A](f: TriplestoreService => Task[A]): ZIO[TriplestoreService, Throwable, A] =
    ZIO.serviceWithZIO[TriplestoreService](f)

  def ask(query: String): RIO[TriplestoreService, Boolean] = ts(_.query(Ask(query)))

  def select(query: String): RIO[TriplestoreService, SparqlSelectResult] = ts(_.query(Select(query)))

  def update(query: String): RIO[TriplestoreService, Unit] = ts(_.query(Update(query)))

  def currentValueOf(resourceIri: String): RIO[TriplestoreService, String] =
    select(
      s"SELECT ?v WHERE { GRAPH <$dataGraph> { <$resourceIri> <${anythingOntoInternal}hasText> ?v } }",
    ).map(_.getFirstOrThrow("v"))

  /** All `anything:hasText` values of the resource, in the triplestore's order. */
  def textValuesOf(resourceIri: String): RIO[TriplestoreService, List[String]] =
    select(
      s"SELECT ?v WHERE { GRAPH <$dataGraph> { <$resourceIri> <${anythingOntoInternal}hasText> ?v } }",
    ).map(_.getCol("v").toList)

  def selectFirst(query: String, variable: String): RIO[TriplestoreService, Option[String]] =
    select(query).map(_.getFirst(variable))

  def previousVersionOf(valueIri: String): RIO[TriplestoreService, String] =
    selectFirst(
      s"SELECT ?p WHERE { GRAPH <$dataGraph> { <$valueIri> <${kb}previousValue> ?p } }",
      "p",
    ).someOrFail(new RuntimeException(s"$valueIri has no previous version"))

  def creationDateOf(valueIri: String): RIO[TriplestoreService, String] =
    selectFirst(
      s"SELECT ?d WHERE { GRAPH <$dataGraph> { <$valueIri> <${kb}valueCreationDate> ?d } }",
      "d",
    ).someOrFail(new RuntimeException(s"$valueIri has no creation date"))

  def valueHasString(valueIri: String): RIO[TriplestoreService, Option[String]] =
    selectFirst(s"SELECT ?s WHERE { GRAPH <$dataGraph> { <$valueIri> <${kb}valueHasString> ?s } }", "s")

  def storedXml(valueIri: String): RIO[TriplestoreService, Option[String]] =
    selectFirst(s"SELECT ?xml WHERE { GRAPH <$dataGraph> { <$valueIri> <${kb}valueHasXml> ?xml } }", "xml")

  /** The `textValueAsXml` the API serves for the resource's text value, optionally at a historical version. */
  def servedXml(resourceIri: String, version: Option[String]): RIO[TestApiClient, String] = {
    val update = version.fold[RequestUpdate[JsonLDDocument]](identity)(addVersionQueryParam(_))
    for {
      doc   <- TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", rootUser, update).flatMap(_.assert200)
      value <- ZIO.fromEither(doc.body.getRequiredObject(hasTextIri)).mapError(new RuntimeException(_))
      xml   <- ZIO.fromEither(value.getRequiredString(KA.TextValueAsXml)).mapError(new RuntimeException(_))
    } yield xml
  }

  def canonical(xml: String, mappingIri: StandoffMappingIri): RIO[StandoffMappingService, String] =
    ZIO
      .serviceWithZIO[StandoffMappingService](_.getMappingV2(mappingIri))
      .flatMap(mapping => ZIO.attempt(StandoffTagUtilV2.canonicalize(xml, mapping)))

  /** Canonical equality: the stored literal and the served XML are the same document after canonicalization, and neither is empty. */
  def storedCanonicallyEqualsServed(
    resourceIri: String,
    valueIri: String,
    mapping: StandoffMappingIri,
    version: Option[String] = None,
  ): RIO[TriplestoreService & TestApiClient & StandoffMappingService, TestResult] =
    for {
      stored      <- storedXml(valueIri).someOrFail(new RuntimeException(s"$valueIri has no valueHasXml"))
      served      <- servedXml(resourceIri, version)
      storedCanon <- canonical(stored, mapping)
      servedCanon <- canonical(served, mapping)
    } yield assertTrue(stored.nonEmpty, storedCanon == servedCanon)

  /** Strict equality: the stored literal is byte-equal to the served `textValueAsXml`, without canonicalization. */
  def storedByteIdenticalToServed(
    resourceIri: String,
    valueIri: String,
    version: Option[String] = None,
  ): RIO[TriplestoreService & TestApiClient, TestResult] =
    for {
      stored <- storedXml(valueIri).someOrFail(new RuntimeException(s"$valueIri has no valueHasXml"))
      served <- servedXml(resourceIri, version)
    } yield assertTrue(stored.nonEmpty, stored == served)
}
