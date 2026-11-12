/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2ez

import sttp.client4.*
import sttp.model.StatusCode
import zio.*
import zio.json.ast.Json
import zio.test.*

import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

import org.knora.sparqlbuilder.Iri
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.resources.repo.StandoffLinkRefCountQuery
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
 * Helpers for E2E specs that write text values as XML and compare the stored `kb:valueHasXml` literal with the XML
 * served by the API. The default is `anything:hasText` of project 0001; project, user, graph and text property can be
 * overridden.
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

  def iriOf(doc: JsonLDDocument): Task[String] =
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
  def createValue(
    resourceIri: String,
    value: Json.Obj,
    property: String = "anything:hasText",
  ): RIO[TestApiClient, String] = {
    val body = Json.Obj(
      "@id"      -> Json.Str(resourceIri),
      "@type"    -> Json.Str("anything:Thing"),
      property   -> value,
      "@context" -> context,
    )
    TestApiClient
      .postJsonLdDocument(uri"/v2/values", body.toString, anythingUser1)
      .flatMap(_.assert200)
      .flatMap(iriOf)
  }

  /** `PUT /v2/values`: updates the text value and returns the IRI of its new version. */
  def updateText(
    resourceIri: String,
    valueIri: String,
    xml: String,
    mapping: StandoffMappingIri,
    property: String = "anything:hasText",
  ): RIO[TestApiClient, String] = {
    val body = Json.Obj(
      "@id"      -> Json.Str(resourceIri),
      "@type"    -> Json.Str("anything:Thing"),
      property   -> textValueJson(xml, mapping, Some(valueIri)),
      "@context" -> context,
    )
    TestApiClient
      .putJsonLdDocument(uri"/v2/values", body.toString, anythingUser1)
      .flatMap(_.assert200)
      .flatMap(iriOf)
  }

  def deleteText(
    resourceIri: String,
    valueIri: String,
    property: String = "anything:hasText",
  ): RIO[TestApiClient, Unit] = {
    val body = Json.Obj(
      "@id"    -> Json.Str(resourceIri),
      "@type"  -> Json.Str("anything:Thing"),
      property -> Json.Obj(
        "@id"                     -> Json.Str(valueIri),
        "@type"                   -> Json.Str("knora-api:TextValue"),
        "knora-api:deleteComment" -> Json.Str("deleted by an e2e spec"),
      ),
      "@context" -> context,
    )
    TestApiClient.postJsonLd(uri"/v2/values/delete", body.toString, anythingUser1).flatMap(_.assert200).unit
  }

  /**
   * Creates the mapping `mappingName` in `project` (default 0001) from `mappingFile`, by default
   * `test_data/test_route/texts/mappingForHTML.xml`. `user` must be an administrator of `project`. A mapping name can
   * be created only once per project.
   */
  def createCustomMapping(
    mappingName: String,
    mappingFile: java.nio.file.Path = Paths.get("test_data/test_route/texts/mappingForHTML.xml"),
    project: String = projectIri,
    user: User = anythingAdminUser,
  ): RIO[TestApiClient, StandoffMappingIri] = {
    val params =
      s"""{
         |  "knora-api:mappingHasName": "$mappingName",
         |  "knora-api:attachedToProject": { "@id": "$project" },
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
      .postMultiPart[Json](uri"/v2/mapping", body, user)
      .flatMap(_.assert200)
      .as(StandoffMappingIri.unsafeFrom(s"$project/mappings/$mappingName"))
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

  def storedXml(valueIri: String): RIO[TriplestoreService, Option[String]] = storedXmlIn(valueIri, dataGraph)

  /** The `kb:valueHasXml` literal of the value, read from the given data graph. */
  def storedXmlIn(valueIri: String, graph: String): RIO[TriplestoreService, Option[String]] =
    selectFirst(s"SELECT ?xml WHERE { GRAPH <$graph> { <$valueIri> <${kb}valueHasXml> ?xml } }", "xml")

  /** The `textValueAsXml` the API serves for the resource's text value, optionally at a historical version. */
  def servedXml(
    resourceIri: String,
    version: Option[String],
    textProperty: String = hasTextIri,
  ): RIO[TestApiClient, String] = {
    val update = version.fold[RequestUpdate[JsonLDDocument]](identity)(addVersionQueryParam(_))
    for {
      doc   <- TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", rootUser, update).flatMap(_.assert200)
      value <- ZIO.fromEither(doc.body.getRequiredObject(textProperty)).mapError(new RuntimeException(_))
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
    graph: String = dataGraph,
    textProperty: String = hasTextIri,
  ): RIO[TriplestoreService & TestApiClient & StandoffMappingService, TestResult] =
    for {
      stored      <- storedXmlIn(valueIri, graph).someOrFail(new RuntimeException(s"$valueIri has no valueHasXml"))
      served      <- servedXml(resourceIri, version, textProperty)
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

  /** The stored `kb:valueHasRefCount` of the current link value of the source's standoff links, if there is one. */
  def storedCount(source: String): RIO[TriplestoreService, Option[String]] =
    selectFirst(
      s"""PREFIX kb: <$kb>
         |SELECT ?count WHERE { GRAPH <$dataGraph> { <$source> kb:hasStandoffLinkToValue ?lv . ?lv kb:isDeleted false ; kb:valueHasRefCount ?count } }""".stripMargin,
      "count",
    )

  /** Overwrites the `kb:valueHasRefCount` of the source's current standoff link values. */
  def setStoredCount(source: String, count: Int): RIO[TriplestoreService, Unit] =
    update(
      s"""PREFIX kb: <$kb>
         |DELETE { GRAPH <$dataGraph> { ?lv kb:valueHasRefCount ?old } }
         |INSERT { GRAPH <$dataGraph> { ?lv kb:valueHasRefCount $count } }
         |WHERE { GRAPH <$dataGraph> { <$source> kb:hasStandoffLinkToValue ?lv . ?lv kb:isDeleted false ; kb:valueHasRefCount ?old } }""".stripMargin,
    )

  /** The log lines of the application under test, captured by [[logger]]; install it with `Runtime.addLogger`. */
  final class LogCapture {
    private val queue = new ConcurrentLinkedQueue[String]()

    val logger: ZLogger[String, Unit] =
      (_, _, _, message, _, _, _, _) => { queue.add(message()); () }

    def lines: UIO[Seq[String]] = ZIO.succeed(queue.asScala.toSeq)

    private[ValueHasXmlTestSupport] def summaryCount: UIO[Int] =
      lines.map(_.count(_.contains(repairSummaryMarker)))
  }

  val repairUri                   = uri"/admin/maintenance/repair-standoff-link-ref-counts"
  private val repairPollSchedule  = Schedule.spaced(500.millis) && Schedule.recurs(120)
  private val repairSummaryMarker = "repair finished: dryRun="

  private final case class RepairStillRunning() extends RuntimeException("a run is still active")

  /**
   * Starts a repair of project 0001 and waits for its summary line in `log`; a 409 means another run is active and is
   * retried. The summary count is read right before each attempt, so the summary of another run cannot end the wait.
   */
  def repairToCompletion(log: LogCapture, dryRun: Boolean = false): RIO[TestApiClient, Unit] = {
    val body                           = Json.Obj("projectShortcode" -> Json.Str("0001"), "dryRun" -> Json.Bool(dryRun))
    val start: RIO[TestApiClient, Int] =
      for {
        before <- log.summaryCount
        _      <- TestApiClient
               .postJson[Json, Json](repairUri, body, rootUser)
               .map(_.code)
               .flatMap {
                 case StatusCode.Accepted => ZIO.unit
                 case StatusCode.Conflict => ZIO.fail(RepairStillRunning())
                 case other               => ZIO.die(new IllegalStateException(s"unexpected status $other"))
               }
      } yield before
    for {
      before <- start.retry(repairPollSchedule)
      _      <- log.summaryCount.filterOrFail(_ > before)(RepairStillRunning()).retry(repairPollSchedule)
    } yield ()
  }

  /** The standoff link reference counts of the resource's links agree with its text values' link tags, in the given data graph. */
  def refCountsConsistent(resourceIri: String, graph: String = dataGraph): RIO[TriplestoreService, TestResult] =
    for {
      graphIri <- ZIO.fromEither(Iri.from(graph)).mapError(new RuntimeException(_))
      result   <- ts(_.query(StandoffLinkRefCountQuery.selectMismatches(graphIri)))
      rows      = result.map(_.rowMap).filter(_.get("source").contains(resourceIri)).map(_.toString)
    } yield assert(rows)(Assertion.isEmpty)
}
