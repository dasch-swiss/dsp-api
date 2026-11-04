/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*

@RunWith(classOf[DspZTestJUnitRunner])
class GravsearchQueryCheckerSpec extends ZIOSpecDefault {

  private def parse(construct: String, where: String): ConstructQuery = {
    // The parser reads the global StringFormatter, which must be initialised first.
    StringFormatter.getInitializedTestInstance: Unit
    GravsearchParser.parseQuery(
      s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
         |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
         |CONSTRUCT {
         |  $construct
         |} WHERE {
         |  ?thing a anything:Thing .
         |  $where
         |}""".stripMargin,
    )
  }

  private val mainThing = "?thing knora-api:isMainResource true ."

  private def removed(construct: String, where: String) =
    GravsearchQueryChecker.removeMainResourceStatementsFromWhereClause(parse(construct, where))

  private def rejectionMessage(construct: String, where: String): String =
    removed(construct, where).left.map(_.getMessage).swap.getOrElse("")

  override def spec: Spec[TestEnvironment, Any] = suite("GravsearchQueryChecker")(
    suite("removeMainResourceStatementsFromWhereClause")(
      test("removes a top-level statement that marks the CONSTRUCT clause's main resource") {
        val expected = parse(mainThing, "")
        val actual   = removed(mainThing, mainThing)
        assertTrue(actual.map(_.whereClause.patterns) == Right(expected.whereClause.patterns))
      },
      test("leaves a query without such a statement unchanged") {
        val query = parse(mainThing, "")
        assertTrue(GravsearchQueryChecker.removeMainResourceStatementsFromWhereClause(query) == Right(query))
      },
      test("rejects a statement that marks a different variable") {
        assertTrue(
          rejectionMessage(mainThing, "?other knora-api:isMainResource true .") ==
            "knora-api:isMainResource in the WHERE clause marks ?other, but the CONSTRUCT clause marks ?thing as the main resource",
        )
      },
      test("rejects the object false") {
        assertTrue(
          rejectionMessage(mainThing, "?thing knora-api:isMainResource false .") ==
            "The object of knora-api:isMainResource can only be true",
        )
      },
      test("rejects a subject that is not a variable") {
        assertTrue(
          rejectionMessage(mainThing, "<http://rdfh.ch/0001/a-thing> knora-api:isMainResource true .") ==
            "The subject of knora-api:isMainResource must be a variable",
        )
      },
      test("rejects a statement nested in OPTIONAL, UNION, MINUS or FILTER NOT EXISTS") {
        val nested = Seq(
          s"OPTIONAL { $mainThing }",
          s"{ $mainThing } UNION { ?thing anything:hasInteger ?int . }",
          s"MINUS { $mainThing }",
          s"FILTER NOT EXISTS { $mainThing }",
        )
        val expected = "knora-api:isMainResource can only be used at the top level of the WHERE clause, " +
          "not inside a nested block such as OPTIONAL, UNION, MINUS or FILTER NOT EXISTS"
        assertTrue(nested.map(rejectionMessage(mainThing, _)).forall(_ == expected))
      },
    ),
    suite("mainResourceVariable")(
      test("returns the variable marked in the CONSTRUCT clause") {
        val actual = GravsearchQueryChecker.mainResourceVariable(parse(mainThing, "").constructClause)
        assertTrue(actual == Right(QueryVariable("thing")))
      },
      test("rejects the object false") {
        val actual = GravsearchQueryChecker
          .mainResourceVariable(parse("?thing knora-api:isMainResource false .", "").constructClause)
          .left
          .map(_.getMessage)
        assertTrue(actual == Left("The object of knora-api:isMainResource can only be true"))
      },
      test("rejects a CONSTRUCT clause without a main resource") {
        val actual = GravsearchQueryChecker
          .mainResourceVariable(
            parse("?thing anything:hasInteger ?int .", "?thing anything:hasInteger ?int .").constructClause,
          )
          .left
          .map(_.getMessage)
        assertTrue(actual == Left("CONSTRUCT clause contains no knora-api:isMainResource"))
      },
    ),
  )
}
