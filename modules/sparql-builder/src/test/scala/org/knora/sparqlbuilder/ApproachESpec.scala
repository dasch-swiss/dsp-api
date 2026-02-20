/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

import org.apache.jena.arq.querybuilder.AskBuilder
import org.apache.jena.arq.querybuilder.Order
import org.apache.jena.arq.querybuilder.SelectBuilder
import org.apache.jena.arq.querybuilder.UpdateBuilder
import org.apache.jena.arq.querybuilder.WhereBuilder
import org.apache.jena.graph.NodeFactory
import org.apache.jena.sparql.core.Var
import org.apache.jena.update.UpdateRequest

import zio.test.*

/**
 * Approach E: Thin Scala 3 wrapper over Jena ARQ QueryBuilder
 *
 * Uses Jena's `jena-querybuilder` extras module directly from Scala.
 * The key trade-off: Jena produces validated `Query` objects (real SPARQL AST),
 * but the API is mutable Java-style (methods return `this`).
 *
 * This prototype evaluates whether wrapping Jena's builder in a thin Scala layer
 * provides sufficient ergonomics, or whether the mutable API is too awkward.
 */
object ApproachESpec extends ZIOSpecDefault {

  // -- Helpers for creating Jena nodes --
  def iri(uri: String)         = NodeFactory.createURI(uri)
  def variable(name: String)   = Var.alloc(name)
  def stringLit(value: String) = NodeFactory.createLiteralString(value)
  def boolLit(value: Boolean)  =
    NodeFactory.createLiteralDT(value.toString, org.apache.jena.datatypes.xsd.XSDDatatype.XSDboolean)
  def intLit(value: Int) =
    NodeFactory.createLiteralDT(value.toString, org.apache.jena.datatypes.xsd.XSDDatatype.XSDinteger)

  // -- Common vocabulary --
  val knoraBase   = "http://www.knora.org/ontology/knora-base#"
  val rdfNs       = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  val rdfs        = "http://www.w3.org/2000/01/rdf-schema#"
  val kbIsDeleted = iri(knoraBase + "isDeleted")
  val kbLastMod   = iri(knoraBase + "lastModificationDate")
  val rdfType     = iri(rdfNs + "type")

  override def spec = suite("Approach E: Jena ARQ QueryBuilder wrapper")(
    simpleSelectSuite,
    isNodeUsedBenchmark,
    conditionalPatternsSuite,
    updateBenchmark,
    ergonomicNotes,
  )

  // -------------------------------------------------------------------------
  // Simple query: SELECT with OPTIONAL
  // -------------------------------------------------------------------------
  val simpleSelectSuite = suite("Simple SELECT with OPTIONAL")(
    test("renders a basic SELECT with OPTIONAL using Jena builder") {
      val s             = variable("s")
      val p             = variable("p")
      val o             = variable("o")
      val lmd           = variable("lastModDate")
      val resourceClass = iri("http://example.org/MyClass")

      // Jena's mutable builder pattern
      val builder = new SelectBuilder()
      builder.addVar(s).addVar(p).addVar(o)
      builder.addWhere(s, rdfType, resourceClass)
      builder.addWhere(s, kbIsDeleted, boolLit(false))
      builder.addOptional(s, kbLastMod, lmd)
      builder.addWhere(s, p, o)
      builder.addOrderBy(lmd, Order.DESCENDING)
      builder.setLimit(25)

      val query = builder.buildString()

      assertTrue(
        query.contains("SELECT"),
        query.contains("?s"),
        query.contains("?p"),
        query.contains("?o"),
        query.contains("OPTIONAL"),
        query.contains("LIMIT   25"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Benchmark: IsNodeUsedQuery (ASK with UNION)
  // -------------------------------------------------------------------------
  val isNodeUsedBenchmark = suite("Benchmark: IsNodeUsedQuery with Jena builder")(
    test("renders ASK with UNION") {
      val s              = variable("s")
      val nodeIri        = iri("http://rdfh.ch/lists/0001/treeList01")
      val guiAttr        = iri("http://www.knora.org/ontology/salsah-gui#guiAttribute")
      val valHasListNode = iri(knoraBase + "valueHasListNode")

      val branch1 = new WhereBuilder()
        .addWhere(s, guiAttr, stringLit(s"hlist=<$nodeIri>"))
      val branch2 = new WhereBuilder()
        .addWhere(s, valHasListNode, nodeIri)

      val builder = new AskBuilder()
      builder.addUnion(branch1)
      builder.addUnion(branch2)

      val query = builder.buildString()

      assertTrue(
        query.contains("ASK"),
        query.contains("UNION"),
        query.contains("guiAttribute"),
        query.contains("valueHasListNode"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Conditional patterns with Jena builder
  // -------------------------------------------------------------------------
  val conditionalPatternsSuite = suite("Conditional patterns")(
    test("Option-based patterns — mutable builder requires imperative style") {
      val s                            = variable("s")
      val commentIri                   = iri(knoraBase + "valueHasComment")
      val maybeComment: Option[String] = Some("A comment")

      // With Jena's mutable builder, conditionals require imperative if/foreach
      val builder = new SelectBuilder()
      builder.addVar(s)
      builder.addWhere(s, kbIsDeleted, boolLit(false))
      maybeComment.foreach(c => builder.addWhere(s, commentIri, stringLit(c)))

      val query = builder.buildString()

      assertTrue(
        query.contains("isDeleted"),
        query.contains("valueHasComment"),
        query.contains("A comment"),
      )
    },
    test("iteration with indexed variables — mutable state accumulation") {
      val resource = variable("resource")
      val targets  = List("target1", "target2")
      val hasLink  = iri(knoraBase + "hasLink")
      val refCount = iri(knoraBase + "valueHasRefCount")

      val builder = new SelectBuilder()
      builder.addVar(resource)

      // Mutable iteration — must use foreach, not map
      targets.zipWithIndex.foreach { case (target, idx) =>
        val linkValue = variable(s"linkValue$idx")
        val targetIri = iri(s"http://example.org/$target")
        builder.addWhere(resource, hasLink, targetIri)
        builder.addWhere(linkValue, refCount, intLit(1))
      }

      val query = builder.buildString()

      assertTrue(
        query.contains("?linkValue0"),
        query.contains("?linkValue1"),
        query.contains("target1"),
        query.contains("target2"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Update benchmark
  // -------------------------------------------------------------------------
  val updateBenchmark = suite("UPDATE query")(
    test("renders DELETE/INSERT/WHERE using Jena UpdateBuilder") {
      val s       = variable("s")
      val oldDate = stringLit("2024-01-01")
      val newDate = stringLit("2024-01-02")

      // Jena's UpdateBuilder has a different API: addInsert/addDelete operate on triple patterns
      val builder = new UpdateBuilder()
      builder.addDelete(s, kbLastMod, oldDate)
      builder.addInsert(s, kbLastMod, newDate)
      builder.addWhere(s, kbLastMod, oldDate)

      val update: UpdateRequest = builder.buildRequest()
      val query                 = update.toString

      assertTrue(
        query.contains("DELETE"),
        query.contains("INSERT"),
        query.contains("WHERE"),
        query.contains("lastModificationDate"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Ergonomic notes
  // -------------------------------------------------------------------------
  val ergonomicNotes = suite("Ergonomic observations")(
    test("Jena builder produces validated Query objects (not just strings)") {
      // Jena parses and validates the built query — invalid SPARQL would fail at build time
      val builder = new SelectBuilder()
      builder.addVar(variable("s"))
      builder.addWhere(variable("s"), rdfType, iri("http://example.org/Thing"))

      // .build() returns org.apache.jena.query.Query — a real AST
      val jenaQuery = builder.build()
      assertTrue(
        jenaQuery.isSelectType,
        jenaQuery.getResultVars.contains("s"),
      )
    },
    test("Jena builder is mutable — cannot safely compose or reuse") {
      // Demonstrate that builder mutation makes composition tricky
      val builder = new SelectBuilder()
      builder.addVar(variable("s"))
      builder.addWhere(variable("s"), rdfType, iri("http://example.org/A"))

      // Adding more patterns to the same builder mutates it
      builder.addWhere(variable("s"), kbIsDeleted, boolLit(false))

      // Cannot "fork" the builder to create two variants
      // builder.clone() exists but returns Object, not SelectBuilder
      val query = builder.buildString()
      assertTrue(
        query.contains("example.org/A"),
        query.contains("isDeleted"),
      )
    },
  )
}
