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
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Exercises the standoff link reference count query against a live triplestore, using a dedicated project data graph
 * that no shared test data touches. Every test re-seeds the graph, so the cases are independent of order.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class StandoffLinkRefCountQuerySemanticsSpec extends E2EZSpec {

  private val triplestore = ZIO.serviceWithZIO[TriplestoreService]

  private val graph = "http://www.knora.org/data/0001/refcount"

  private val kb  = "http://www.knora.org/ontology/knora-base#"
  private val rdf = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"

  private val hasText = "http://www.knora.org/ontology/0001/anything#hasText"
  private val xsdInt  = "http://www.w3.org/2001/XMLSchema#integer"

  private val source = "http://rdfh.ch/0001/refcount/source"
  private val target = "http://rdfh.ch/0001/refcount/target"

  private def named(name: String): String = s"http://rdfh.ch/0001/refcount/$name"

  private val linkValueIri = named("link-value")

  /** A mismatch row as the query returns it; the optional fields are unbound without a current link value. */
  private final case class Row(
    source: String,
    target: String,
    linkValue: Option[String],
    storedCount: Option[String],
    expectedCount: String,
    directLink: String,
  )

  /** A text value of the source with one standoff tag per target in `tagTargets`. */
  private def textValue(
    name: String,
    tagTargets: Seq[String],
    deleted: Boolean = false,
    attached: Boolean = true,
    of: String = source,
  ) = {
    val iri  = named(name)
    val tags = tagTargets.zipWithIndex.map { case (t, i) =>
      s"""<$iri/standoff/$i> a <http://www.knora.org/ontology/standoff#StandoffLinkTag> ;
         |  <${kb}standoffTagHasLink> <$t> .
         |<$iri> <${kb}valueHasStandoff> <$iri/standoff/$i> .""".stripMargin
    }
    s"""<$iri> a <${kb}TextValue> ; <${kb}isDeleted> $deleted .
       |${if (attached) s"<$of> <$hasText> <$iri> ." else ""}
       |${tags.mkString("\n")}""".stripMargin
  }

  private def linkValue(count: Int, deleted: Boolean = false, of: String = source, iri: String = linkValueIri) =
    s"""<$of> <${kb}hasStandoffLinkToValue> <$iri> .
       |<$iri> a <${kb}LinkValue> ;
       |  <${kb}isDeleted> $deleted ;
       |  <${rdf}object> <$target> ;
       |  <${kb}valueHasRefCount> "$count"^^<$xsdInt> .""".stripMargin

  private def directLinkOf(of: String) = s"<$of> <${kb}hasStandoffLinkTo> <$target> ."

  private val directLink = directLinkOf(source)

  private val otherSource = named("other-source")

  private def seeded(triples: String*): RIO[TriplestoreService, Seq[Row]] = {
    val iriOfGraph = Iri.unsafeFrom(graph)
    for {
      _      <- triplestore(_.query(Update(s"DROP SILENT GRAPH <$graph>")))
      _      <- triplestore(_.query(Update(s"INSERT DATA { GRAPH <$graph> { ${triples.mkString("\n")} } }")))
      result <- triplestore(_.query(StandoffLinkRefCountQuery.selectMismatches(iriOfGraph)))
    } yield result.map(r =>
      Row(
        r.getRequired("source"),
        r.getRequired("target"),
        r.get("linkValue"),
        r.get("storedCount"),
        r.getRequired("expectedCount"),
        r.getRequired("directLink"),
      ),
    )
  }

  private def row(
    linkValue: Option[String],
    stored: Option[String],
    expected: String,
    direct: String,
    of: String = source,
  ) = Row(of, target, linkValue, stored, expected, direct)

  override val e2eSpec: Spec[env, Any] = suite("StandoffLinkRefCountQuery against a live triplestore")(
    test("a consistent resource has no mismatches") {
      for {
        rows <- seeded(
                  textValue("tv1", Seq(target)),
                  textValue("tv2", Seq(target)),
                  linkValue(2),
                  directLink,
                )
      } yield assertTrue(rows.isEmpty)
    },
    test("a deleted link value with count 0, attached and without a direct link, is consistent") {
      for {
        rows <- seeded(linkValue(0, deleted = true))
      } yield assertTrue(rows.isEmpty)
    },
    test("a stored count different from the number of text values is reported with both counts") {
      for {
        rows <- seeded(
                  textValue("tv1", Seq(target)),
                  textValue("tv2", Seq(target)),
                  linkValue(3),
                  directLink,
                )
      } yield assertTrue(rows == Seq(row(Some(linkValueIri), Some("3"), "2", "true")))
    },
    test("a missing direct link is reported") {
      for {
        rows <- seeded(textValue("tv1", Seq(target)), linkValue(1))
      } yield assertTrue(rows == Seq(row(Some(linkValueIri), Some("1"), "1", "false")))
    },
    test("two consistent sources linking to the same target have no mismatches") {
      for {
        rows <- seeded(
                  textValue("tv1", Seq(target)),
                  linkValue(1),
                  directLink,
                  textValue("tv2", Seq(target), of = otherSource),
                  linkValue(1, of = otherSource, iri = named("other-link-value")),
                  directLinkOf(otherSource),
                )
      } yield assertTrue(rows.isEmpty)
    },
    test("a wrong count on one of two sources linking to the same target reports only that source") {
      for {
        rows <- seeded(
                  textValue("tv1", Seq(target)),
                  linkValue(1),
                  directLink,
                  textValue("tv2", Seq(target), of = otherSource),
                  linkValue(2, of = otherSource, iri = named("other-link-value")),
                  directLinkOf(otherSource),
                )
      } yield assertTrue(
        rows == Seq(row(Some(named("other-link-value")), Some("2"), "1", "true", of = otherSource)),
      )
    },
    test("a deleted link value while a current text value has a link tag is reported without a link value") {
      for {
        rows <- seeded(textValue("tv1", Seq(target)), linkValue(0, deleted = true), directLink)
      } yield assertTrue(rows == Seq(row(None, None, "1", "true")))
    },
    test("a direct link without link tags and without a link value is reported") {
      for {
        rows <- seeded(directLink)
      } yield assertTrue(rows == Seq(row(None, None, "0", "true")))
    },
    test("a direct link with a deleted link value and without link tags is reported") {
      for {
        rows <- seeded(linkValue(0, deleted = true), directLink)
      } yield assertTrue(rows == Seq(row(None, None, "0", "true")))
    },
    test("a link tag without a link value is reported") {
      for {
        rows <- seeded(textValue("tv1", Seq(target)), directLink)
      } yield assertTrue(rows == Seq(row(None, None, "1", "true")))
    },
    test("a deleted text value and a superseded text value do not count") {
      for {
        rows <- seeded(
                  textValue("current", Seq(target)),
                  textValue("deleted", Seq(target), deleted = true),
                  textValue("superseded", Seq(target), attached = false),
                  s"<${named("current")}> <${kb}previousValue> <${named("superseded")}> .",
                  linkValue(1),
                  directLink,
                )
      } yield assertTrue(rows.isEmpty)
    },
    test("three tags to the target in one text value count once") {
      for {
        rows <- seeded(textValue("tv1", Seq(target, target, target)), linkValue(1), directLink)
      } yield assertTrue(rows.isEmpty)
    },
    test("a count of three for three tags in one text value is reported as expected one") {
      for {
        rows <- seeded(textValue("tv1", Seq(target, target, target)), linkValue(3), directLink)
      } yield assertTrue(rows == Seq(row(Some(linkValueIri), Some("3"), "1", "true")))
    },
  )
}
