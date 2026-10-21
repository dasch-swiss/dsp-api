/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*

@RunWith(classOf[DspZTestJUnitRunner])
class LuceneScopeSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val mainRes = QueryVariable("mainRes")
  private val other   = QueryVariable("other")
  private val text    = QueryVariable("text")
  private val label   = QueryVariable("label")

  private val hasText      = IriRef("http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri)
  private val hasOtherLink = IriRef("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThing".toSmartIri)
  private val rdfsLabel    = IriRef(OntologyConstants.Rdfs.Label.toSmartIri)

  private def matchTextFilter(variable: QueryVariable) = FilterPattern(
    FunctionCallExpression(
      IriRef(OntologyConstants.KnoraApiV2Complex.MatchTextFunction.toSmartIri),
      Seq(variable, XsdLiteral("term", OntologyConstants.Xsd.String.toSmartIri)),
    ),
  )

  private def anchored(patterns: QueryPattern*) = LuceneScope.mainResourceAnchoredValues(patterns, mainRes)

  override def spec: Spec[TestEnvironment, Any] = suite("LuceneScope.mainResourceAnchoredValues")(
    test("anchor a value bound only from the main resource") {
      assertTrue(anchored(StatementPattern(mainRes, hasText, text), matchTextFilter(text)) == Set(text))
    },
    test("anchor a value bound from the main resource inside an OPTIONAL") {
      assertTrue(anchored(OptionalPattern(Seq(StatementPattern(mainRes, hasText, text)))) == Set(text))
    },
    test("not anchor a value of a linked resource, but anchor the link target bound from the main resource") {
      val result = anchored(
        StatementPattern(mainRes, hasOtherLink, other),
        StatementPattern(other, hasText, text),
        matchTextFilter(text),
      )
      assertTrue(!result.contains(text), result.contains(other))
    },
    test("anchor a value bound from the main resource through a variable predicate") {
      assertTrue(anchored(StatementPattern(mainRes, QueryVariable("prop"), text)) == Set(text))
    },
    test("not anchor a value bound from a fixed resource IRI") {
      val fixedResource = IriRef("http://rdfh.ch/0001/a-thing".toSmartIri)
      assertTrue(anchored(StatementPattern(fixedResource, hasText, text)).isEmpty)
    },
    test("not anchor a value also bound from a linked resource inside a nested group or subselect") {
      val direct = StatementPattern(mainRes, hasText, text)
      val linked = StatementPattern(other, hasText, text)
      assertTrue(
        !anchored(direct, GroupPattern(Seq(linked))).contains(text),
        !anchored(direct, SubSelectPattern(text, Seq(linked))).contains(text),
      )
    },
    test("not anchor a value bound from the main resource in one UNION branch and a linked resource in the other") {
      val result = anchored(
        StatementPattern(mainRes, hasOtherLink, other),
        UnionPattern(
          Seq(
            Seq(StatementPattern(mainRes, hasText, text), matchTextFilter(text)),
            Seq(StatementPattern(other, hasText, text)),
          ),
        ),
      )
      assertTrue(!result.contains(text))
    },
    test("not anchor a value also bound from a linked resource inside MINUS or FILTER NOT EXISTS") {
      val direct = StatementPattern(mainRes, hasText, text)
      val linked = StatementPattern(other, hasText, text)
      assertTrue(
        !anchored(direct, MinusPattern(Seq(linked))).contains(text),
        !anchored(direct, FilterNotExistsPattern(Seq(linked))).contains(text),
      )
    },
    test("not anchor a label of the main resource, even when the FILTER precedes the label statement") {
      assertTrue(anchored(matchTextFilter(label), StatementPattern(mainRes, rdfsLabel, label)).isEmpty)
    },
    test("not anchor a variable that is never the object of a statement") {
      assertTrue(anchored(StatementPattern(text, hasText, hasOtherLink), matchTextFilter(text)).isEmpty)
    },
  )
}
