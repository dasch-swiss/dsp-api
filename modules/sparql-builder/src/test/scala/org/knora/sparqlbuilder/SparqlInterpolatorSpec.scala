/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

/**
 * Doobie-style Fragment + `sparql"..."` interpolator.
 *
 * Demonstrates the library API against the benchmark queries. Whole queries are written as
 * `sparql"""..."""` templates; dynamic parts (conditionals, iteration) are composed as
 * `Fragment` values and dropped into template holes.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class SparqlInterpolatorSpec extends ZIOSpecDefault {

  // -- Common vocabulary (would live in the adapter layer in production) --
  val knoraBase      = "http://www.knora.org/ontology/knora-base#"
  val rdf            = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  val rdfs           = "http://www.w3.org/2000/01/rdf-schema#"
  val xsd            = "http://www.w3.org/2001/XMLSchema#"
  val owl            = "http://www.w3.org/2002/07/owl#"
  val kbIsDeleted    = Iri.unsafeFrom(knoraBase + "isDeleted")
  val kbResource     = Iri.unsafeFrom(knoraBase + "Resource")
  val kbLastMod      = Iri.unsafeFrom(knoraBase + "lastModificationDate")
  val rdfType        = Iri.unsafeFrom(rdf + "type")
  val rdfsSubClassOf = Iri.unsafeFrom(rdfs + "subClassOf")

  override def spec = suite("Fragment + sparql interpolator")(
    simpleSelectSuite,
    insertValueBenchmarkSketch,
    combinatorsSuite,
    conditionalFragmentsSuite,
    iterationSuite,
  )

  // -------------------------------------------------------------------------
  // Simple query: SELECT with OPTIONAL
  // -------------------------------------------------------------------------
  val simpleSelectSuite = suite("Simple SELECT with OPTIONAL")(
    test("renders a basic SELECT with OPTIONAL") {
      val s             = Variable("s")
      val p             = Variable("p")
      val o             = Variable("o")
      val lmd           = Variable("lastModDate")
      val resourceClass = Iri.unsafeFrom("http://example.org/MyClass")

      val query = sparql"""
        SELECT $s $p $o
        WHERE {
          $s a $resourceClass .
          $s $kbIsDeleted false .
          ${Fragments.optional(sparql"$s $kbLastMod $lmd .")}
          $s $p $o .
        }
        ORDER BY DESC($lmd)
        LIMIT 25
      """.render

      assertTrue(
        query.contains("SELECT ?s ?p ?o"),
        query.contains("?s a <http://example.org/MyClass>"),
        query.contains("?s <http://www.knora.org/ontology/knora-base#isDeleted> false"),
        query.contains("OPTIONAL"),
        query.contains("DESC(?lastModDate)"),
        query.contains("LIMIT 25"),
      )
    },
  )

  /** Simplified link update data for the benchmark. */
  case class LinkUpdate(
    linkPropertyIri: String,
    linkTargetIri: String,
    deleteDirectLink: Boolean,
    insertDirectLink: Boolean,
    linkValueExists: Boolean,
    newLinkValueIri: String,
    newReferenceCount: Int,
    currentReferenceCount: Int,
    newLinkValueCreator: String,
    newLinkValuePermissions: String,
  )

  val insertValueBenchmarkSketch = suite("Benchmark: InsertValueQueryBuilder sketch")(
    test("handles conditional link patterns and iteration with indexed variables") {
      val resource         = Variable("resource")
      val resourceLastMod  = Variable("resourceLastModificationDate")
      val kbHasPermissions = Iri.unsafeFrom(knoraBase + "hasPermissions")
      val kbValueHasUUID   = Iri.unsafeFrom(knoraBase + "valueHasUUID")

      val linkUpdates = List(
        LinkUpdate(
          "http://example.org/hasLink",
          "http://example.org/target1",
          deleteDirectLink = true,
          insertDirectLink = true,
          linkValueExists = true,
          "http://example.org/newLinkValue1",
          newReferenceCount = 1,
          currentReferenceCount = 1,
          "http://example.org/user1",
          "CR knora-admin:ProjectAdmin",
        ),
      )

      // Build delete patterns with conditional logic (mirrors InsertValueQueryBuilder.buildDeletePatterns)
      val linkValueDeletePatterns: Fragment = linkUpdates.zipWithIndex.map { case (linkUpdate, index) =>
        val deleteDirectLink = Option.when(linkUpdate.deleteDirectLink) {
          val linkProp = Iri.unsafeFrom(linkUpdate.linkPropertyIri)
          val target   = Iri.unsafeFrom(linkUpdate.linkTargetIri)
          sparql"$resource $linkProp $target ."
        }

        val linkValueExistsPatterns = Option.when(linkUpdate.linkValueExists) {
          val linkValue      = Variable(s"linkValue$index")
          val linkValueUUID  = Variable(s"linkValueUUID$index")
          val linkValuePerms = Variable(s"linkValuePermissions$index")
          val linkPropValue  = Iri.unsafeFrom(linkUpdate.linkPropertyIri + "Value")
          Fragment.join(
            List(
              sparql"$resource $linkPropValue $linkValue .",
              sparql"$linkValue $kbValueHasUUID $linkValueUUID .",
              sparql"$linkValue $kbHasPermissions $linkValuePerms .",
            ),
            Fragment.raw("\n"),
          )
        }

        Fragment.combine(deleteDirectLink, linkValueExistsPatterns)
      }.combineAll

      val query = sparql"""
        DELETE {
          $resource $kbLastMod $resourceLastMod .
          $linkValueDeletePatterns
        }
        WHERE {
          $resource a $kbResource .
        }
      """.render

      assertTrue(
        query.contains(
          "?resource <http://www.knora.org/ontology/knora-base#lastModificationDate> ?resourceLastModificationDate",
        ),
        query.contains("?resource <http://example.org/hasLink> <http://example.org/target1>"),
        query.contains("?linkValue0"),
        query.contains("?linkValueUUID0"),
        query.contains("?linkValuePermissions0"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Fragments combinators
  // -------------------------------------------------------------------------
  val combinatorsSuite = suite("Fragments combinators")(
    test("union renders UNION branches") {
      val s        = Variable("s")
      val nodeIri  = Iri.unsafeFrom("http://rdfh.ch/lists/0001/treeList01")
      val rendered = Fragments
        .union(
          sparql"$s <http://www.knora.org/ontology/salsah-gui#guiAttribute> ${Literal.string(s"hlist=${nodeIri.render}")} .",
          sparql"$s $kbLastMod $nodeIri .",
        )
        .render
      assertTrue(
        rendered.contains("UNION"),
        rendered.contains("guiAttribute"),
        rendered.contains("\"hlist=<http://rdfh.ch/lists/0001/treeList01>\""),
      )
    },
    test("graph wraps a pattern in a GRAPH clause") {
      val g        = Iri.unsafeFrom("http://www.knora.org/data/0001/anything")
      val rendered = Fragments.graph(sparql"$g")(sparql"?s ?p ?o .").render
      assertTrue(rendered.startsWith("GRAPH <http://www.knora.org/data/0001/anything> {"))
    },
    test("values renders a VALUES clause over IRIs") {
      val v        = Variable("cls")
      val rendered = Fragments
        .values(v, List(Iri.unsafeFrom("http://example.org/A"), Iri.unsafeFrom("http://example.org/B")))
        .render
      assertTrue(rendered == "VALUES ?cls { <http://example.org/A> <http://example.org/B> }")
    },
    test("filter and bind render expressions") {
      val n = Variable("n")
      assertTrue(
        Fragments.filter(sparql"$n > ${Literal.int(5)}").render == "FILTER(?n > 5)",
        Fragments.bind(sparql"NOW()", n).render == "BIND(NOW() AS ?n)",
      )
    },
    test("property path operator sits outside the interpolated IRI") {
      val cls      = Variable("cls")
      val rendered = sparql"$cls $rdfsSubClassOf* $kbResource .".render
      assertTrue(
        rendered == "?cls <http://www.w3.org/2000/01/rdf-schema#subClassOf>* <http://www.knora.org/ontology/knora-base#Resource> .",
      )
    },
  )

  // -------------------------------------------------------------------------
  // Conditional fragments (Twirl @if/@match equivalent)
  // -------------------------------------------------------------------------
  val conditionalFragmentsSuite = suite("Conditional fragments")(
    test("Option[Fragment] with combine") {
      val maybeComment: Option[String] = Some("A comment")
      val commentIri                   = Iri.unsafeFrom(knoraBase + "valueHasComment")
      val newValue                     = Variable("newValue")

      val commentPattern: Option[Fragment] = maybeComment.map { c =>
        sparql"$newValue $commentIri ${Literal.string(c)} ."
      }

      val result = Fragment.combine(
        Some(sparql"$newValue a ${Iri.unsafeFrom(knoraBase + "TextValue")} ."),
        commentPattern,
      )

      assertTrue(
        result.render.contains("TextValue"),
        result.render.contains("valueHasComment"),
        result.render.contains("A comment"),
      )
    },
    test("None fragments are skipped") {
      val noComment: Option[Fragment] = None
      val result                      = Fragment.combine(
        Some(sparql"?s a ?type ."),
        noComment,
      )
      assertTrue(
        result.render.contains("?s a ?type"),
        !result.render.contains("comment"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Dynamic iteration (Twirl @for equivalent)
  // -------------------------------------------------------------------------
  val iterationSuite = suite("Dynamic iteration")(
    test("collection to indexed variable patterns via map + combineAll") {
      case class ValueUpdate(predicateIri: String, value: String)

      val updates = List(
        ValueUpdate("http://example.org/hasName", "Alice"),
        ValueUpdate("http://example.org/hasAge", "30"),
      )

      val newValue = Variable("newValue")

      val patterns: Fragment = updates.map { update =>
        val pred = Iri.unsafeFrom(update.predicateIri)
        val lit  = Literal.string(update.value)
        sparql"$newValue $pred $lit ."
      }.combineAll

      val rendered = patterns.render
      assertTrue(
        rendered.contains("hasName"),
        rendered.contains("\"Alice\""),
        rendered.contains("hasAge"),
        rendered.contains("\"30\""),
      )
    },
    test("indexed variables for link updates (Twirl @for equivalent)") {
      val resource    = Variable("resource")
      val linkUpdates = List("target1", "target2", "target3")

      val patterns: Fragment = linkUpdates.zipWithIndex.map { case (target, idx) =>
        val linkValue = Variable(s"linkValue$idx")
        val targetIri = Iri.unsafeFrom(s"http://example.org/$target")
        sparql"$resource ${Iri.unsafeFrom(knoraBase + "hasLink")} $targetIri .\n$linkValue ${Iri.unsafeFrom(knoraBase + "valueHasRefCount")} ${Literal.int(1)} ."
      }.combineAll

      val rendered = patterns.render
      assertTrue(
        rendered.contains("?linkValue0"),
        rendered.contains("?linkValue1"),
        rendered.contains("?linkValue2"),
        rendered.contains("target1"),
        rendered.contains("target2"),
        rendered.contains("target3"),
      )
    },
  )
}
