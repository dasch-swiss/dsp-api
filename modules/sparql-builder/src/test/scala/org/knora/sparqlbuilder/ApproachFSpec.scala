/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.query.ParameterizedSparqlString

import zio.test.*

/**
 * Approach F: Template + bind via Jena ParameterizedSparqlString
 *
 * Write SPARQL as a string template, then bind typed values by name.
 * Jena escapes values at bind time and validates the query at parse time.
 *
 * Key trade-off: Closest to raw SPARQL (developers write actual SPARQL syntax),
 * but the least composable — templates are monolithic strings, not reusable fragments.
 *
 * The Jena docs explicitly warn that ParameterizedSparqlString's injection protection
 * is "by no means foolproof" since substitution is textual.
 */
object ApproachFSpec extends ZIOSpecDefault {

  override def spec = suite("Approach F: Jena ParameterizedSparqlString")(
    simpleSelectSuite,
    isNodeUsedBenchmark,
    conditionalPatternsSuite,
    injectionNotes,
    composabilityNotes,
  )

  // -------------------------------------------------------------------------
  // Simple query: SELECT with OPTIONAL
  // -------------------------------------------------------------------------
  val simpleSelectSuite = suite("Simple SELECT with OPTIONAL")(
    test("renders a basic SELECT with OPTIONAL using PSS") {
      val pss = new ParameterizedSparqlString()
      pss.setCommandText("""
        SELECT ?s ?p ?o
        WHERE {
          ?s a ?resourceClass .
          ?s ?isDeletedPred ?isDeletedVal .
          OPTIONAL { ?s ?lastModPred ?lastModDate . }
          ?s ?p ?o .
        }
        ORDER BY DESC(?lastModDate)
        LIMIT 25
      """)
      pss.setIri("resourceClass", "http://example.org/MyClass")
      pss.setIri("isDeletedPred", "http://www.knora.org/ontology/knora-base#isDeleted")
      pss.setLiteral("isDeletedVal", false)
      pss.setIri("lastModPred", "http://www.knora.org/ontology/knora-base#lastModificationDate")

      val query = pss.toString

      assertTrue(
        query.contains("SELECT ?s ?p ?o"),
        query.contains("<http://example.org/MyClass>"),
        query.contains("OPTIONAL"),
        query.contains("LIMIT 25"),
        query.contains("knora-base#isDeleted"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Benchmark: IsNodeUsedQuery (ASK with UNION)
  // -------------------------------------------------------------------------
  val isNodeUsedBenchmark = suite("Benchmark: IsNodeUsedQuery with PSS")(
    test("renders ASK with UNION — but values must be unique per template") {
      // PSS binds variables by name across the entire template.
      // This means each value placeholder must have a unique name.
      val pss = new ParameterizedSparqlString()
      pss.setCommandText("""
        ASK
        WHERE {
          { ?s ?guiAttr ?guiAttrValue . }
          UNION
          { ?s ?valHasListNode ?nodeIri . }
        }
      """)
      pss.setIri("guiAttr", "http://www.knora.org/ontology/salsah-gui#guiAttribute")
      pss.setLiteral("guiAttrValue", "hlist=<http://rdfh.ch/lists/0001/treeList01>")
      pss.setIri("valHasListNode", "http://www.knora.org/ontology/knora-base#valueHasListNode")
      pss.setIri("nodeIri", "http://rdfh.ch/lists/0001/treeList01")

      val query = pss.toString

      assertTrue(
        query.contains("ASK"),
        query.contains("UNION"),
        query.contains("guiAttribute"),
        query.contains("valueHasListNode"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Conditional patterns — the main weakness of PSS
  // -------------------------------------------------------------------------
  val conditionalPatternsSuite = suite("Conditional patterns")(
    test("conditionals require string concatenation — loses safety") {
      // PSS templates are monolithic strings. To conditionally include
      // a triple pattern, you must build the template string dynamically,
      // which means using string concatenation — exactly the anti-pattern
      // we're trying to eliminate.
      val maybeComment: Option[String] = Some("A comment")

      val commentClause = maybeComment match {
        case Some(_) => "?s ?commentPred ?commentValue ."
        case None    => ""
      }

      val pss = new ParameterizedSparqlString()
      pss.setCommandText(s"""
        SELECT ?s
        WHERE {
          ?s ?isDeletedPred false .
          $commentClause
        }
      """)
      pss.setIri("isDeletedPred", "http://www.knora.org/ontology/knora-base#isDeleted")
      maybeComment.foreach { c =>
        pss.setIri("commentPred", "http://www.knora.org/ontology/knora-base#valueHasComment")
        pss.setLiteral("commentValue", c)
      }

      val query = pss.toString

      assertTrue(
        query.contains("isDeleted"),
        query.contains("valueHasComment"),
        query.contains("A comment"),
      )
    },
    test("iteration requires building template string dynamically") {
      // For N link targets, we need N placeholder names in the template.
      // This requires building the template string in a loop — again,
      // string concatenation of SPARQL structure.
      val targets = List("target1", "target2")

      val patternClauses = targets.zipWithIndex.map { case (_, idx) =>
        s"?resource ?hasLink ?target$idx .\n?linkValue$idx ?refCount ?refCountVal$idx ."
      }.mkString("\n")

      val pss = new ParameterizedSparqlString()
      pss.setCommandText(s"""
        SELECT ?resource
        WHERE {
          $patternClauses
        }
      """)
      pss.setIri("hasLink", "http://www.knora.org/ontology/knora-base#hasLink")
      pss.setIri("refCount", "http://www.knora.org/ontology/knora-base#valueHasRefCount")
      targets.zipWithIndex.foreach { case (target, idx) =>
        pss.setIri(s"target$idx", s"http://example.org/$target")
        pss.setLiteral(s"refCountVal$idx", "1", XSDDatatype.XSDinteger)
      }

      val query = pss.toString

      assertTrue(
        query.contains("?linkValue0"),
        query.contains("?linkValue1"),
        query.contains("target1"),
        query.contains("target2"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // Injection safety notes
  // -------------------------------------------------------------------------
  val injectionNotes = suite("Injection safety observations")(
    test("PSS escapes bound string literals — but uses single-backslash escaping") {
      val pss = new ParameterizedSparqlString()
      pss.setCommandText("SELECT ?s WHERE { ?s ?p ?value . }")
      pss.setLiteral("value", """malicious" . ?s ?p ?o . # """)

      val query = pss.toString

      // PSS does escape the quote with \", which is SPARQL-valid escaping.
      // A SPARQL parser would keep the payload inside the string literal.
      // However, the raw text still contains the injection substring —
      // this relies on the SPARQL parser correctly handling the escape.
      // The Jena docs warn PSS protection is "by no means foolproof"
      // since it uses textual substitution.
      assertTrue(
        query.contains("\\\""),
        // Note: unlike our custom Literal.string(), PSS does NOT double-escape.
        // The rendered output contains the payload characters, but they are
        // safely inside a SPARQL string literal (preceded by escaped quote).
      )
    },
    test("PSS detects some injection attempts at parse time") {
      // When calling asQuery()/asUpdate(), Jena parses the result.
      // Some injection attempts would be caught at this stage.
      val pss = new ParameterizedSparqlString()
      pss.setCommandText("SELECT ?s WHERE { ?s a ?type . }")
      pss.setIri("type", "http://example.org/Thing")

      // This succeeds — valid query
      val query = pss.asQuery()
      assertTrue(query.isSelectType)
    },
    test("but template itself is unprotected raw SPARQL") {
      // The template string is raw SPARQL — if a developer accidentally
      // uses string interpolation IN the template (s"...${userInput}..."),
      // PSS provides no protection. Only the setIri/setLiteral bindings
      // are escaped.
      val userInput = "safe value"
      val pss       = new ParameterizedSparqlString()
      pss.setCommandText(s"""SELECT ?s WHERE { ?s ?p "$userInput" . }""")
      // ^ This WORKS but bypasses PSS's escaping entirely.
      // The value "safe value" is inserted via Scala string interpolation,
      // not via PSS binding.
      val query = pss.toString
      assertTrue(
        query.contains("safe value"),
        // No escaping was applied — it's just string interpolation
      )
    },
  )

  // -------------------------------------------------------------------------
  // Composability notes
  // -------------------------------------------------------------------------
  val composabilityNotes = suite("Composability observations")(
    test("PSS templates are monolithic — no reusable fragments") {
      // You cannot extract a "permission check" or "isDeleted filter" as a
      // reusable component. Each query template is a standalone string.
      // Compare with Approach A where:
      //   val isNotDeleted = sparql"$s $kbIsDeleted false ."
      // can be reused across multiple queries.

      val pss1 = new ParameterizedSparqlString()
      pss1.setCommandText("SELECT ?s WHERE { ?s ?isDeleted false . ?s ?p ?o . }")
      pss1.setIri("isDeleted", "http://www.knora.org/ontology/knora-base#isDeleted")

      val pss2 = new ParameterizedSparqlString()
      pss2.setCommandText("ASK WHERE { ?s ?isDeleted false . ?s a ?type . }")
      pss2.setIri("isDeleted", "http://www.knora.org/ontology/knora-base#isDeleted")

      // The "?s ?isDeleted false" pattern is duplicated in both templates.
      // There is no way to share it as a fragment.
      assertTrue(
        pss1.toString.contains("isDeleted"),
        pss2.toString.contains("isDeleted"),
      )
    },
    test("PSS append() allows some composition but is mutable") {
      // PSS does have append methods for building up the template incrementally,
      // but this is still mutable string accumulation.
      val pss = new ParameterizedSparqlString()
      pss.append("SELECT ?s WHERE { ")
      pss.append("?s a ")
      pss.appendIri("http://example.org/Thing")
      pss.append(" . ")
      pss.append("}")

      val query = pss.toString
      assertTrue(
        query.contains("SELECT ?s"),
        query.contains("<http://example.org/Thing>"),
      )
    },
  )
}
