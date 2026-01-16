/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.IriRef
import org.knora.webapi.messages.util.search.QueryVariable
import org.knora.webapi.messages.util.search.StatementPattern
import org.knora.webapi.messages.util.search.ValuesPattern

object OntologyInferencerE2ESpec extends E2EZSpec {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance
  private val ontologyInferencer           = ZIO.serviceWithZIO[OntologyInferencer]

  private val thingIRI         = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri
  private val blueThingIRI     = "http://www.knora.org/ontology/0001/anything#BlueThing".toSmartIri
  private val hasOtherThingIRI = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri
  private val hasTextIRI       = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri

  override val e2eSpec = suite("OntologyInferencer")(
    suite("transformStatementInWhere")(
      test("not simulate any RDF inference for a class, if there are no known subclasses") {
        val typeStatement = StatementPattern(
          subj = QueryVariable("foo"),
          pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
          obj = IriRef(blueThingIRI),
        )
        val expectedStatements = Seq(
          StatementPattern(
            subj = QueryVariable("foo"),
            pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
            obj = IriRef(blueThingIRI),
          ),
        )
        ontologyInferencer(_.transformStatementInWhere(statementPattern = typeStatement, simulateInference = true))
          .map(expandedStatements => assertTrue(expandedStatements == expectedStatements))
      },
      test("not simulate any RDF inference for a property, if there are no known subproperties") {
        val hasValueStatement =
          StatementPattern(
            subj = QueryVariable("foo"),
            pred = IriRef(hasTextIRI),
            obj = QueryVariable("text"),
          )
        val expectedStatements: Seq[StatementPattern] = Seq(
          StatementPattern(
            subj = QueryVariable(variableName = "foo"),
            pred = IriRef(
              iri = hasTextIRI,
              propertyPathOperator = None,
            ),
            obj = QueryVariable(variableName = "text"),
          ),
        )
        ontologyInferencer(_.transformStatementInWhere(statementPattern = hasValueStatement, simulateInference = true))
          .map(expandedStatements => assertTrue(expandedStatements == expectedStatements))
      },
      test("create a values pattern to simulate RDF inference for a class, if there are known subclasses") {
        val typeStatement = StatementPattern(
          subj = QueryVariable("foo"),
          pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
          obj = IriRef(thingIRI),
        )
        val expected = List(
          ValuesPattern(
            QueryVariable("resTypes5432"),
            Set(
              IriRef("http://www.knora.org/ontology/0001/something#Something".toSmartIri),
              IriRef("http://www.knora.org/ontology/0001/anything#BlueThing".toSmartIri),
              IriRef("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri),
              IriRef("http://www.knora.org/ontology/0001/anything#ThingWithSeqnum".toSmartIri),
            ),
          ),
          StatementPattern(
            QueryVariable("foo"),
            IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
            QueryVariable("resTypes5432"),
          ),
        )
        ontologyInferencer(
          _.transformStatementInWhere(
            statementPattern = typeStatement,
            simulateInference = true,
            queryVariableSuffix = Some("5432"),
          ),
        ).map(expandedStatements => assertTrue(expandedStatements == expected))
      },
      test("create a values pattern to simulate RDF inference for a property, if there are known subproperties") {
        val hasValueStatement =
          StatementPattern(
            subj = QueryVariable("foo"),
            pred = IriRef(hasOtherThingIRI),
            obj = QueryVariable("text"),
          )
        val expected = List(
          ValuesPattern(
            QueryVariable("subProp5432"),
            Set(
              IriRef("http://www.knora.org/ontology/0001/something#hasOtherSomething".toSmartIri),
              IriRef("http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri),
              IriRef("http://www.knora.org/ontology/0001/anything#hasBlueThing".toSmartIri),
            ),
          ),
          StatementPattern(QueryVariable("foo"), QueryVariable("subProp5432"), QueryVariable("text")),
        )
        ontologyInferencer(
          _.transformStatementInWhere(
            statementPattern = hasValueStatement,
            simulateInference = true,
            queryVariableSuffix = Some("5432"),
          ),
        ).map(expandedStatements => assertTrue(expandedStatements == expected))
      },
    ),
  )
}
