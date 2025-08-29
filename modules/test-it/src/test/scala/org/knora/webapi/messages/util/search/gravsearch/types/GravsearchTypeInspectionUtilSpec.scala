/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.types

import zio.*
import zio.test.*

import org.knora.webapi.ApiV2Simple
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser

object GravsearchTypeInspectionUtilSpec extends E2EZSpec {

  override val e2eSpec = suite("GravsearchTypeInspectionUtil")(
    test("remove the type annotations from a WHERE clause") {
      val queryWithExplicitTypeAnnotations: String =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |
          |    ?letter knora-api:hasLinkTo <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
          |    ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
          |
          |    ?letter knora-api:hasLinkTo <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
          |    ?letter ?linkingProp2  <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
          |
          |} WHERE {
          |    ?letter a knora-api:Resource .
          |    ?letter a beol:letter .
          |
          |    # Scheuchzer, Johann Jacob 1672-1733
          |    ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
          |    ?linkingProp1 knora-api:objectType knora-api:Resource .
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |    <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> a knora-api:Resource .
          |
          |    # Hermann, Jacob 1678-1733
          |    ?letter ?linkingProp2 <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
          |    ?linkingProp2 knora-api:objectType knora-api:Resource .
          |
          |    FILTER(?linkingProp2 = beol:hasAuthor || ?linkingProp2 = beol:hasRecipient)
          |
          |    beol:hasAuthor knora-api:objectType knora-api:Resource .
          |    beol:hasRecipient knora-api:objectType knora-api:Resource .
          |
          |    <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> a knora-api:Resource .
          |}
          |""".stripMargin
      val expected: WhereClause = WhereClause(
        patterns = Vector(
          StatementPattern(
            subj = QueryVariable(variableName = "letter"),
            pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
            obj = IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "letter"),
            pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
            obj = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri),
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "letter"),
            pred = QueryVariable(variableName = "linkingProp1"),
            obj = IriRef(iri = "http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA".toSmartIri),
          ),
          StatementPattern(
            subj = IriRef(iri = "http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA".toSmartIri),
            pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
            obj = IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "letter"),
            pred = QueryVariable(variableName = "linkingProp2"),
            obj = IriRef(iri = "http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA".toSmartIri),
          ),
          StatementPattern(
            subj = IriRef(iri = "http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA".toSmartIri),
            pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
            obj = IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
          ),
          FilterPattern(expression =
            OrExpression(
              leftArg = CompareExpression(
                leftArg = QueryVariable(variableName = "linkingProp1"),
                operator = CompareExpressionOperator.EQUALS,
                rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri),
              ),
              rightArg = CompareExpression(
                leftArg = QueryVariable(variableName = "linkingProp1"),
                operator = CompareExpressionOperator.EQUALS,
                rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri),
              ),
            ),
          ),
          FilterPattern(expression =
            OrExpression(
              leftArg = CompareExpression(
                leftArg = QueryVariable(variableName = "linkingProp2"),
                operator = CompareExpressionOperator.EQUALS,
                rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri),
              ),
              rightArg = CompareExpression(
                leftArg = QueryVariable(variableName = "linkingProp2"),
                operator = CompareExpressionOperator.EQUALS,
                rightArg = IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri),
              ),
            ),
          ),
        ),
        positiveEntities = Set(
          IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri),
          IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri),
          IriRef(iri = "http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA".toSmartIri),
          IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri),
          IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
          IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo".toSmartIri),
          QueryVariable(variableName = "letter"),
          IriRef(iri = "http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA".toSmartIri),
          QueryVariable(variableName = "linkingProp2"),
          QueryVariable(variableName = "linkingProp1"),
          IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
          IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
          IriRef(iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri),
        ),
        querySchema = Some(ApiV2Simple),
      )
      for {
        parsedQuery <- ZIO.attempt(GravsearchParser.parseQuery(queryWithExplicitTypeAnnotations))
        actual      <- GravsearchTypeInspectionUtil.removeTypeAnnotations(parsedQuery.whereClause)
      } yield assertTrue(actual == expected)
    },
  )
}
