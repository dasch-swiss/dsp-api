/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import sttp.client4.UriContext
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2e.v2.SearchEndpointE2ESpecHelper.postGravsearchQuery
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.TestApiClient

/**
 * E2E tests for the `knora-api:matchFulltext` Gravsearch function (DEV-6715). Uses a dedicated,
 * self-contained fixture (matchfulltext-data.ttl) rather than extending anything-data.ttl, per
 * CLAUDE.md: shared datasets are loaded by many tests, so a new record there risks cascading
 * failures in unrelated specs. The fixture reuses the existing "anything" project/ontology, so no
 * new project/user registration is needed.
 */
object MatchFulltextE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = SearchEndpointE2ESpecHelper.rdfDataObjects :+
    RdfDataObject(
      path = "test_data/generated_test_data/e2e.v2.MatchFulltextE2ESpec/matchfulltext-data.ttl",
      name = "http://www.knora.org/data/0001/matchfulltexttest",
    )

  private def matchFulltextCountQuery(
    term: String,
    extraWhere: String = "",
    mainResVar: String = "mainRes",
    complexSchema: Boolean = false,
  ): String = {
    val knoraApiIri =
      if (complexSchema) "http://api.knora.org/ontology/knora-api/v2#"
      else "http://api.knora.org/ontology/knora-api/simple/v2#"
    s"""PREFIX knora-api: <$knoraApiIri>
       |CONSTRUCT {
       |    ?$mainResVar knora-api:isMainResource true .
       |} WHERE {
       |    ?$mainResVar a knora-api:Resource .
       |    $extraWhere
       |    FILTER knora-api:matchFulltext(?$mainResVar, "$term")
       |}""".stripMargin
  }

  private def verifyCount(query: String, expectedCount: Int) = for {
    response    <- TestApiClient.postJsonLdDocument(uri"/v2/searchextended/count", query)
    jsonLd      <- response.assert200
    actualCount <- ZIO.fromEither(jsonLd.body.getRequiredInt(OntologyConstants.SchemaOrg.NumberOfItems))
  } yield assertTrue(actualCount == expectedCount)

  private def oldEndpointCount(term: String) = for {
    response    <- TestApiClient.getJsonLdDocument(uri"/v2/search/count/$term")
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
        for {
          response <- TestApiClient.postJsonLdDocument(
                        uri"/v2/searchextended/count".addParam("limitToProject", "http://rdfh.ch/projects/0001"),
                        matchFulltextCountQuery("Zqxvnonce7f3Label"),
                      )
          jsonLd      <- response.assert200
          actualCount <- ZIO.fromEither(jsonLd.body.getRequiredInt(OntologyConstants.SchemaOrg.NumberOfItems))
        } yield assertTrue(actualCount == 1)
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
