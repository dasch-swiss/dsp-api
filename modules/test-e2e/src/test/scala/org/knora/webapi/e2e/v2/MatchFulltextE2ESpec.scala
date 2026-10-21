/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.junit.runner.RunWith
import sttp.client4.UriContext
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2e.v2.SearchEndpointE2ESpecHelper.postGravsearchQuery
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.TestApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class MatchFulltextE2ESpec extends E2EZSpec {

  // Each fixture goes into its project's data graph, next to that project's data: a lookup scoped to a project
  // sees only that graph's Lucene hits.
  override val rdfDataObjects: List[RdfDataObject] = SearchEndpointE2ESpecHelper.rdfDataObjects ++ List(
    RdfDataObject(
      path = "test_data/generated_test_data/e2e.v2.MatchFulltextE2ESpec/matchfulltext-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
    RdfDataObject(
      path = "test_data/generated_test_data/e2e.v2.MatchFulltextE2ESpec/matchfulltext-crossproject-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
  )

  private def scopedCount(project: String, query: String) = for {
    response <- TestApiClient.postJsonLdDocument(
                  uri"/v2/searchextended/count".addParam("limitToProject", project),
                  query,
                )
    jsonLd      <- response.assert200
    actualCount <- ZIO.fromEither(jsonLd.body.getRequiredInt(OntologyConstants.SchemaOrg.NumberOfItems))
  } yield actualCount

  private def matchFulltextCountQuery(
    term: String,
    extraWhere: String = "",
    complexSchema: Boolean = false,
  ): String = {
    val knoraApiIri =
      if (complexSchema) "http://api.knora.org/ontology/knora-api/v2#"
      else "http://api.knora.org/ontology/knora-api/simple/v2#"
    s"""PREFIX knora-api: <$knoraApiIri>
       |CONSTRUCT {
       |    ?mainRes knora-api:isMainResource true .
       |} WHERE {
       |    ?mainRes a knora-api:Resource .
       |    $extraWhere
       |    FILTER knora-api:matchFulltext(?mainRes, "$term")
       |}""".stripMargin
  }

  private val anythingProject    = "http://rdfh.ch/projects/0001"
  private val incunabulaProject  = "http://rdfh.ch/projects/0803"
  private val nonexistentProject = "http://rdfh.ch/projects/matchfulltextNoSuchProject"
  private val systemProject      = "http://www.knora.org/ontology/knora-admin#SystemProject"

  // A count query over anything:Thing in the simple schema, with the given WHERE patterns after the class statement.
  private def thingCountQuery(where: String): String =
    s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
       |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
       |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
       |CONSTRUCT {
       |    ?thing knora-api:isMainResource true .
       |} WHERE {
       |    ?thing a anything:Thing .
       |    $where
       |}""".stripMargin

  private def verifyCount(query: String, expectedCount: Int) =
    newEndpointCount(query).map(actualCount => assertTrue(actualCount == expectedCount))

  private def oldEndpointCount(term: String, limitToProject: Option[String] = None) = for {
    response <- TestApiClient.getJsonLdDocument(
                  uri"/v2/search/count/$term".addParam("limitToProject", limitToProject),
                )
    jsonLd      <- response.assert200
    actualCount <- ZIO.fromEither(jsonLd.body.getRequiredInt(OntologyConstants.SchemaOrg.NumberOfItems))
  } yield actualCount

  private def newEndpointCount(query: String) = for {
    response    <- TestApiClient.postJsonLdDocument(uri"/v2/searchextended/count", query)
    jsonLd      <- response.assert200
    actualCount <- ZIO.fromEither(jsonLd.body.getRequiredInt(OntologyConstants.SchemaOrg.NumberOfItems))
  } yield actualCount

  override def e2eSpec = suite("MatchFulltext")(
    suite("match paths")(
      test("matches via the resource's label") {
        verifyCount(matchFulltextCountQuery("Zqxvnonce7f3Label"), 1)
      },
      test("matches via a text value") {
        verifyCount(matchFulltextCountQuery("Zqxvnonce7f3Text"), 1)
      },
      test("matches via a value comment") {
        verifyCount(matchFulltextCountQuery("Zqxvnonce7f3Comment"), 1)
      },
      test("matches via a list node's label (including sub-nodes)") {
        verifyCount(matchFulltextCountQuery("Zqxvnonce7f3Listnode"), 1)
      },
      test("matches a second, otherwise unrelated resource via its label only") {
        verifyCount(matchFulltextCountQuery("Zqxvnonce7f3OnlyLabel"), 1)
      },
      test("counts a resource matching via two independent paths (label + text value) exactly once") {
        verifyCount(matchFulltextCountQuery("Zqxvnonce7f3Dedup"), 1)
      },
    ),
    suite("deleted-value handling")(
      test("a deleted-value-only match is absent") {
        verifyCount(matchFulltextCountQuery("Zqxvnonce7f3DeletedText"), 0)
      },
      test("an explicit isDeleted true in the query does not resurrect a deleted-value match (D6)") {
        // knora-api:isDeleted is only exposed in the complex schema; its object must be a variable.
        verifyCount(
          matchFulltextCountQuery(
            "Zqxvnonce7f3DeletedText",
            extraWhere = "?mainRes knora-api:isDeleted ?isDeleted . FILTER(?isDeleted = true)",
            complexSchema = true,
          ),
          0,
        )
      },
      test("a deleted list value whose node still matches is present (D11)") {
        verifyCount(matchFulltextCountQuery("Zqxvnonce7f3D11List"), 1)
      },
    ),
    suite("combined with structured criteria")(
      test("combines with a class restriction and a property filter (AND semantics)") {
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
            |CONSTRUCT {
            |    ?thing knora-api:isMainResource true .
            |} WHERE {
            |    ?thing a anything:Thing .
            |    ?thing anything:hasListItem ?listItem .
            |    FILTER(?listItem = "Zqxvnonce7f3Listnode"^^knora-api:ListNode)
            |    FILTER knora-api:matchFulltext(?thing, "Zqxvnonce7f3Label")
            |}""".stripMargin
        verifyCount(query, 1)
      },
      test("returns nothing when the structured criterion doesn't match the fulltext hit's resource") {
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
            |CONSTRUCT {
            |    ?thing knora-api:isMainResource true .
            |} WHERE {
            |    ?thing a anything:Thing .
            |    ?thing anything:hasListItem ?listItem .
            |    FILTER(?listItem = "Zqxvnonce7f3D11List"^^knora-api:ListNode)
            |    FILTER knora-api:matchFulltext(?thing, "Zqxvnonce7f3Label")
            |}""".stripMargin
        verifyCount(query, 0)
      },
    ),
    suite("project scoping")(
      test("limitToProject scopes results to the given project") {
        scopedCount(anythingProject, matchFulltextCountQuery("Zqxvnonce7f3Label"))
          .map(actualCount => assertTrue(actualCount == 1))
      },
      test("limitToProject to another project finds nothing") {
        // The target project's data graph must exist: GRAPH <g> on a missing graph returns 0 whatever the scoping.
        // attachedToProject also yields 0, so this does not detect a text index without the graph field.
        scopedCount(incunabulaProject, matchFulltextCountQuery("Zqxvnonce7f3Label"))
          .map(actualCount => assertTrue(actualCount == 0))
      },
      test("a scoped matchFulltext finds the text value, value comment and list node paths") {
        for {
          text     <- scopedCount(anythingProject, matchFulltextCountQuery("Zqxvnonce7f3Text"))
          comment  <- scopedCount(anythingProject, matchFulltextCountQuery("Zqxvnonce7f3Comment"))
          listNode <- scopedCount(anythingProject, matchFulltextCountQuery("Zqxvnonce7f3Listnode"))
        } yield assertTrue(text == 1, comment == 1, listNode == 1)
      },
      test("a scoped matchLabel finds the resource in its project only") {
        val query = thingCountQuery("""FILTER knora-api:matchLabel(?thing, "Zqxvnonce7f3Label")""")
        for {
          inProject    <- scopedCount(anythingProject, query)
          otherProject <- scopedCount(incunabulaProject, query)
        } yield assertTrue(inProject == 1, otherProject == 0)
      },
      test("a scoped matchText finds the value in its project only") {
        val query = thingCountQuery(
          """?thing anything:hasText ?text .
            |    FILTER knora-api:matchText(?text, "Zqxvnonce7f3Text")""".stripMargin,
        )
        for {
          inProject    <- scopedCount(anythingProject, query)
          otherProject <- scopedCount(incunabulaProject, query)
        } yield assertTrue(inProject == 1, otherProject == 0)
      },
      test("a scoped matchLabel inside FILTER NOT EXISTS excludes the same resources as an exact label filter") {
        // The exact label filter uses no Lucene lookup, so its count is the unscoped answer restricted to 0001.
        val withLookup = thingCountQuery(
          """FILTER NOT EXISTS {
            |        FILTER knora-api:matchLabel(?thing, "Zqxvnonce7f3Label")
            |    }""".stripMargin,
        )
        val withoutLookup = thingCountQuery(
          """FILTER NOT EXISTS {
            |        ?thing rdfs:label ?label .
            |        FILTER(?label = "Zqxvnonce7f3Label")
            |    }""".stripMargin,
        )
        for {
          scoped   <- scopedCount(anythingProject, withLookup)
          expected <- scopedCount(anythingProject, withoutLookup)
          all      <- scopedCount(anythingProject, thingCountQuery(""))
        } yield assertTrue(scoped == expected, expected == all - 1)
      },
      test("GET /v2/search/count with limitToProject counts the project's hits only") {
        // No attachedToProject join backs up the scoped /v2/search lookup, so the 0803 count is 1 instead of 0
        // on a text index without the graph field: this is the E2E check that the Fuseki image carries it.
        for {
          inProject    <- oldEndpointCount("Zqxvnonce7f3Label", Some(anythingProject))
          otherProject <- oldEndpointCount("Zqxvnonce7f3Label", Some(incunabulaProject))
          // Two terms, so the breadth probe runs, scoped as well.
          twoTerms <- oldEndpointCount("Zqxvnonce7f3Label Zqxvnonce7f3Text", Some(anythingProject))
        } yield assertTrue(inProject == 1, otherProject == 0, twoTerms == 1)
      },
      test("limitToProject naming a nonexistent project counts nothing on both count endpoints") {
        for {
          gravsearch <- scopedCount(nonexistentProject, matchFulltextCountQuery("Zqxvnonce7f3Label"))
          fulltext   <- oldEndpointCount("Zqxvnonce7f3Label", Some(nonexistentProject))
        } yield assertTrue(gravsearch == 0, fulltext == 0)
      },
      test("limitToProject naming the system project counts nothing on both count endpoints") {
        for {
          gravsearch <- scopedCount(systemProject, matchFulltextCountQuery("Zqxvnonce7f3Label"))
          fulltext   <- oldEndpointCount("Zqxvnonce7f3Label", Some(systemProject))
        } yield assertTrue(gravsearch == 0, fulltext == 0)
      },
      test("an invalid Lucene term with limitToProject is a 400 on /v2/search and on Gravsearch matchFulltext") {
        val rawTerm = "foo\"bar\\baz" // the term of the D7 escaping test below, invalid Lucene syntax
        for {
          fulltext   <- TestApiClient.getJsonLd(uri"/v2/search/$rawTerm".addParam("limitToProject", anythingProject))
          gravsearch <- postGravsearchQuery(
                          matchFulltextCountQuery("""foo\"bar\\baz"""),
                          limitToProject = Some(anythingProject),
                        )
        } yield assertTrue(fulltext.code.code == 400, gravsearch.code.code == 400)
      },
      test("limitToProject leaves a lookup on a linked resource of another project unscoped") {
        // The linked resource is attached to 0803 and indexed in 0803's data graph. Links across projects are
        // allowed, so only a lookup on the main resource may be scoped to the main resource's project.
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
            |CONSTRUCT {
            |    ?thing knora-api:isMainResource true .
            |    ?thing anything:hasOtherThing ?other .
            |} WHERE {
            |    ?thing a anything:Thing .
            |    ?thing anything:hasOtherThing ?other .
            |    FILTER knora-api:matchFulltext(?other, "Zqxvnonce7f3CrossProject")
            |}""".stripMargin
        scopedCount(anythingProject, query).map(actualCount => assertTrue(actualCount == 1))
      },
    ),
    suite("non-main-resource variable usage")(
      test("matchFulltext works on a linked (non-main) resource variable") {
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
            |CONSTRUCT {
            |    ?thing knora-api:isMainResource true .
            |    ?thing anything:hasOtherThing ?other .
            |} WHERE {
            |    ?thing a anything:Thing .
            |    ?thing anything:hasOtherThing ?other .
            |    FILTER knora-api:matchFulltext(?other, "Zqxvnonce7f3Label")
            |}""".stripMargin
        verifyCount(query, 1)
      },
    ),
    suite("parity with GET /v2/search")(
      test("matchFulltext count matches the old fulltext endpoint's count for 'Narr'") {
        for {
          oldCount <- oldEndpointCount("Narr")
          newCount <- newEndpointCount(matchFulltextCountQuery("Narr"))
        } yield assertTrue(newCount == oldCount)
      },
      test("matchFulltext count matches the old fulltext endpoint's count for 'Uniform'") {
        for {
          oldCount <- oldEndpointCount("Uniform")
          newCount <- newEndpointCount(matchFulltextCountQuery("Uniform"))
        } yield assertTrue(newCount == oldCount)
      },
    ),
    suite("schema variants")(
      test("matchFulltext works in the simple schema (default in this spec's other tests)") {
        verifyCount(matchFulltextCountQuery("Zqxvnonce7f3Label", complexSchema = false), 1)
      },
      test("matchFulltext works in the complex schema") {
        verifyCount(matchFulltextCountQuery("Zqxvnonce7f3Label", complexSchema = true), 1)
      },
    ),
    suite("errors")(
      test("rejects an IRI in place of a variable as the first argument") {
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |CONSTRUCT {
            |    ?mainRes knora-api:isMainResource true .
            |} WHERE {
            |    ?mainRes a knora-api:Resource .
            |    FILTER knora-api:matchFulltext(<http://rdfh.ch/0001/matchfulltextA>, "Zqxvnonce7f3Label")
            |}""".stripMargin
        for {
          response <- postGravsearchQuery(query)
          actual   <- response.assert400
        } yield assertTrue(actual.contains("Variable required as function argument"))
      },
      test("rejects matchFulltext nested inside a boolean && expression") {
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |CONSTRUCT {
            |    ?mainRes knora-api:isMainResource true .
            |} WHERE {
            |    ?mainRes a knora-api:Resource .
            |    FILTER(knora-api:matchFulltext(?mainRes, "Zqxvnonce7f3Label") && knora-api:matchFulltext(?mainRes, "Zqxvnonce7f3Label"))
            |}""".stripMargin
        for {
          response <- postGravsearchQuery(query)
          actual   <- response.assert400
        } yield assertTrue(actual.contains("must be the top-level expression in a FILTER"))
      },
      test("rejects an empty search term") {
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |CONSTRUCT {
            |    ?mainRes knora-api:isMainResource true .
            |} WHERE {
            |    ?mainRes a knora-api:Resource .
            |    FILTER knora-api:matchFulltext(?mainRes, "")
            |}""".stripMargin
        for {
          response <- postGravsearchQuery(query)
          actual   <- response.assert400
        } yield assertTrue(actual.contains("Invalid search string"))
      },
      test("rejects a search term shorter than the configured minimum length") {
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |CONSTRUCT {
            |    ?mainRes knora-api:isMainResource true .
            |} WHERE {
            |    ?mainRes a knora-api:Resource .
            |    FILTER knora-api:matchFulltext(?mainRes, "ab")
            |}""".stripMargin
        for {
          response <- postGravsearchQuery(query)
          actual   <- response.assert400
        } yield assertTrue(actual.contains("is expected to have at least length of"))
      },
      test("rejects a second matchFulltext call in the same query") {
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |CONSTRUCT {
            |    ?mainRes knora-api:isMainResource true .
            |} WHERE {
            |    ?mainRes a knora-api:Resource .
            |    FILTER knora-api:matchFulltext(?mainRes, "Zqxvnonce7f3Label")
            |    FILTER knora-api:matchFulltext(?mainRes, "Zqxvnonce7f3Label")
            |}""".stripMargin
        for {
          response <- postGravsearchQuery(query)
          actual   <- response.assert400
        } yield assertTrue(actual.contains("may only be used once per query"))
      },
      test("escapes a search term containing a double quote and a backslash safely, without SPARQL injection (D7)") {
        // The raw term (after SPARQL unescaping) is itself invalid Lucene query syntax - an
        // unescaped internal quote/backslash - so Jena's text index rejects it downstream with its
        // own 400, exactly like the old endpoint does for the same raw term (same underlying Lucene
        // index, no pre-validation on either path). D7's actual guarantee is upstream of that: the
        // term is escaped before being embedded in the *generated SPARQL* literal, so it cannot
        // break out of that literal - the SPARQL itself parses fine, and the failure below is a
        // clean Lucene-level 400 from deep inside query execution, not a SPARQL syntax error or 500.
        val rawTerm           = "foo\"bar\\baz"     // as understood after SPARQL unescaping: foo"bar\baz
        val sparqlLiteralTerm = """foo\"bar\\baz""" // the same term, escaped for the generated SPARQL literal
        val query             =
          s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
             |CONSTRUCT {
             |    ?mainRes knora-api:isMainResource true .
             |} WHERE {
             |    ?mainRes a knora-api:Resource .
             |    FILTER knora-api:matchFulltext(?mainRes, "$sparqlLiteralTerm")
             |}""".stripMargin
        for {
          oldResponse <- TestApiClient.getJsonLdDocument(uri"/v2/search/count/$rawTerm")
          newResponse <- TestApiClient.postJsonLdDocument(uri"/v2/searchextended/count", query)
        } yield assertTrue(oldResponse.code.code == newResponse.code.code, newResponse.code.code != 500)
      },
      test("escapes an embedded newline in a search term without producing invalid SPARQL (D7)") {
        // Unlike a raw `"` or `\`, an embedded LF is not itself invalid Lucene syntax (Lucene treats
        // it as whitespace), so this must succeed with 200 - a SPARQL-level failure here would mean
        // SparqlStringLiteral.escape let a raw LF reach the generated string literal, which SPARQL's
        // STRING_LITERAL_QUOTE grammar disallows unescaped, exactly as it disallows a raw `"` or `\`.
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |CONSTRUCT {
            |    ?mainRes knora-api:isMainResource true .
            |} WHERE {
            |    ?mainRes a knora-api:Resource .
            |    FILTER knora-api:matchFulltext(?mainRes, "foo\nbar")
            |}""".stripMargin
        for {
          response <- TestApiClient.postJsonLdDocument(uri"/v2/searchextended/count", query)
        } yield assertTrue(response.code.code == 200)
      },
      test("passes invalid Lucene syntax through with the same behavior as the old endpoint (D4)") {
        // A bare boolean operator is invalid Lucene query syntax; matchFulltext does not validate
        // it (parity with the old endpoint, which has the same passthrough behavior).
        for {
          oldResponse <- TestApiClient.getJsonLdDocument(uri"/v2/search/count/AND")
          newResponse <- TestApiClient.postJsonLdDocument(uri"/v2/searchextended/count", matchFulltextCountQuery("AND"))
        } yield assertTrue(oldResponse.code.code == newResponse.code.code)
      },
    ),
  )
}
