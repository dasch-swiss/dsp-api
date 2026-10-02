/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.sparqlbuilder.Iri
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.slice.resources.service.ValueHasXmlBackfill
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Exercises the `valueHasXml` backfill queries against a live triplestore, using two dedicated project data graphs
 * that no shared test data touches. Every test re-seeds both graphs, so the cases are independent of order.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlBackfillQuerySemanticsSpec extends E2EZSpec {

  private val triplestore = ZIO.serviceWithZIO[TriplestoreService]

  private val graphA = "http://www.knora.org/data/0001/xmlbackfilla"
  private val graphB = "http://www.knora.org/data/0001/xmlbackfillb"

  private val kb       = "http://www.knora.org/ontology/knora-base#"
  private val standoff = "http://www.knora.org/ontology/standoff#"
  private val mapping  = "http://rdfh.ch/standoff/mappings/StandardMapping"
  private val xsdInt   = "http://www.w3.org/2001/XMLSchema#integer"

  private val current    = "http://rdfh.ch/0001/xmlbackfill/current"
  private val historical = "http://rdfh.ch/0001/xmlbackfill/historical"
  private val deleted    = "http://rdfh.ch/0001/xmlbackfill/deleted"
  private val withXml    = "http://rdfh.ch/0001/xmlbackfill/with-xml"
  private val plain      = "http://rdfh.ch/0001/xmlbackfill/plain"
  private val noStandoff = "http://rdfh.ch/0001/xmlbackfill/no-standoff"
  private val inGraphB   = "http://rdfh.ch/0001/xmlbackfill/in-graph-b"
  private val absent     = "http://rdfh.ch/0001/xmlbackfill/absent"
  private val notText    = "http://rdfh.ch/0001/xmlbackfill/not-a-text-value"

  private def node(value: String, index: Int): String = s"$value/standoff/$index"

  private def standoffNode(value: String, index: Int, tag: String, startIndex: Int): String =
    s"""<${node(value, index)}> a <$standoff$tag> ;
       |  <${kb}standoffTagHasStartIndex> "$startIndex"^^<$xsdInt> ;
       |  <${kb}standoffTagHasStartPosition> "0"^^<$xsdInt> ;
       |  <${kb}standoffTagHasEndPosition> "5"^^<$xsdInt> ;
       |  <${kb}standoffTagHasUUID> "uuid-$index" .
       |<$value> <${kb}valueHasStandoff> <${node(value, index)}> .""".stripMargin

  /** A formatted text value with a mapping and two standoff nodes. */
  private def formatted(value: String, extra: String = ""): String =
    s"""<$value> a <${kb}TextValue> ;
       |  <${kb}valueHasString> "Hello" ;
       |  <${kb}valueHasMapping> <$mapping> ;
       |  <${kb}hasTextValueType> <${kb}FormattedText> .
       |${standoffNode(value, 0, "StandoffRootTag", 0)}
       |${standoffNode(value, 1, "StandoffParagraphTag", 1)}
       |$extra""".stripMargin

  private val currentTriples =
    formatted(
      current,
      s"<$current> <${kb}previousValue> <$historical> .\n${standoffNode(current, 2, "StandoffItalicTag", -1)}",
    )

  private val graphATriples = Seq(
    currentTriples,
    formatted(historical),
    formatted(deleted, s"<$deleted> <${kb}isDeleted> true ."),
    formatted(withXml, s"""<$withXml> <${kb}valueHasXml> "<existing/>" ."""),
    s"""<$plain> a <${kb}TextValue> ; <${kb}valueHasString> "Plain" .""",
    s"""<$noStandoff> a <${kb}TextValue> ;
       |  <${kb}valueHasString> "No standoff" ;
       |  <${kb}valueHasMapping> <$mapping> .""".stripMargin,
    s"""<$notText> a <${kb}IntValue> ; <${kb}valueHasInteger> 1 .""",
  ).mkString("\n")

  private def insertData(graph: String, triples: String): Update =
    Update(s"INSERT DATA { GRAPH <$graph> { $triples } }")

  private val seed: RIO[TriplestoreService, Unit] =
    for {
      _ <- triplestore(_.query(Update(s"DROP SILENT GRAPH <$graphA>")))
      _ <- triplestore(_.query(Update(s"DROP SILENT GRAPH <$graphB>")))
      _ <- triplestore(_.query(insertData(graphA, graphATriples)))
      _ <- triplestore(_.query(insertData(graphB, formatted(inGraphB))))
    } yield ()

  private def iri(value: String): Iri = Iri.unsafeFrom(value)

  private def candidates(graph: String): RIO[TriplestoreService, Set[String]] =
    triplestore(_.query(ValueHasXmlBackfillQuery.selectCandidates(iri(graph)))).map(_.getCol("v").toSet)

  private def xmlOf(graph: String, subject: String): RIO[TriplestoreService, List[String]] =
    triplestore(
      _.query(
        Select(
          s"""SELECT ?x WHERE { GRAPH <$graph> { <$subject> <${kb}valueHasXml> ?x } }""",
        ),
      ),
    ).map(_.getCol("x").toList)

  private def insertXml(graph: String, values: (String, String)*): RIO[TriplestoreService, Unit] =
    triplestore(_.query(ValueHasXmlBackfillQuery.insertXml(iri(graph), values.map((v, x) => iri(v) -> x))))

  private def standoffNodesOf(value: String): Set[String] = Set(node(value, 0), node(value, 1))

  override val e2eSpec: Spec[env, Any] = suite("ValueHasXmlBackfillQuery against a live triplestore")(
    test("selectCandidates returns the current, historical and deleted versions only") {
      for {
        _      <- seed
        result <- candidates(graphA)
      } yield assertTrue(result == Set(current, historical, deleted))
    },
    test("constructStandoff returns the non-negative standoff of the requested values and nothing else") {
      for {
        _        <- seed
        response <- triplestore(
                      _.query(
                        ValueHasXmlBackfillQuery.constructStandoff(iri(graphA), Seq(iri(current), iri(historical))),
                      ),
                    )
        statements = response.statements
        grouped    = ValueHasXmlBackfill.groupByValue(statements, Seq(current, historical))
        linked     = statements.getOrElse(current, Seq.empty).collect { case (p, n) if p == s"${kb}valueHasStandoff" => n }
      } yield assertTrue(
        statements.keySet == Set(current, historical) ++ standoffNodesOf(current) ++ standoffNodesOf(historical),
        linked.toSet == standoffNodesOf(current),
        grouped(current).toOption.map(_.standoffNodes.keySet) == Some(standoffNodesOf(current)),
        grouped(historical).toOption.map(_.standoffNodes.keySet) == Some(standoffNodesOf(historical)),
      )
    },
    test("insertXml writes a mixed batch only for text values without XML") {
      for {
        _ <- seed
        _ <- insertXml(
               graphA,
               withXml -> "<other/>",
               current -> "<text>ok</text>",
               notText -> "<n/>",
               absent  -> "<a/>",
             )
        xmlWithXml       <- xmlOf(graphA, withXml)
        xmlCurrent       <- xmlOf(graphA, current)
        xmlNotText       <- xmlOf(graphA, notText)
        absentPredicates <- triplestore(
                              _.query(Select(s"SELECT ?p WHERE { GRAPH <$graphA> { <$absent> ?p ?o } }")),
                            ).map(_.getCol("p"))
        after <- candidates(graphA)
      } yield assertTrue(
        xmlWithXml == List("<existing/>"),
        xmlCurrent == List("<text>ok</text>"),
        xmlNotText.isEmpty,
        absentPredicates.isEmpty,
        !after.contains(current),
      )
    },
    test("insertXml round-trips XML with quotes, backslashes, newlines, ampersands and non-BMP characters") {
      val xml = "<text a=\"x\" b='y'>Tom &amp; \\Jerry\r\n\t<b>\uD834\uDD1E</b></text>"
      for {
        _      <- seed
        _      <- insertXml(graphA, current -> xml)
        stored <- xmlOf(graphA, current)
      } yield assertTrue(stored == List(xml))
    },
    test("queries scoped to graph A leave graph B untouched") {
      for {
        _      <- seed
        found  <- candidates(graphA)
        _      <- insertXml(graphA, inGraphB -> "x")
        xmlInB <- xmlOf(graphB, inGraphB)
        xmlInA <- xmlOf(graphA, inGraphB)
      } yield assertTrue(!found.contains(inGraphB), xmlInB.isEmpty, xmlInA.isEmpty)
    },
  )
}
