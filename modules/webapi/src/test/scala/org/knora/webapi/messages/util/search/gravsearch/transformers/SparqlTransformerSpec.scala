/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*

@RunWith(classOf[DspZTestJUnitRunner])
class SparqlTransformerSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val thingIRI         = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri
  private val hasOtherThingIRI = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri
  private val hasTextIRI       = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri

  override val spec = suite("SparqlTransformer")(
    test("create a syntactically valid base name from a given variable") {
      val baseName = SparqlTransformer.escapeEntityForVariable(QueryVariable("book"))
      assertTrue(baseName == "book")
    },
    test("create a syntactically valid base name from a given data IRI") {
      val baseName = SparqlTransformer.escapeEntityForVariable(IriRef("http://rdfh.ch/users/91e19f1e01".toSmartIri))
      assertTrue(baseName == "httprdfhchusers91e19f1e01")
    },
    test("create a syntactically valid base name from a given ontology IRI") {
      val baseName = SparqlTransformer.escapeEntityForVariable(
        IriRef("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri),
      )
      assertTrue(baseName == "httpwwwknoraorgontology0803incunabulabook")
    },
    test("create a syntactically valid base name from a given string literal") {
      val baseName =
        SparqlTransformer.escapeEntityForVariable(XsdLiteral("dumm", OntologyConstants.Xsd.String.toSmartIri))
      assertTrue(baseName == "dumm")
    },
    test("create a unique variable name based on an entity and a property") {
      val generatedQueryVar =
        SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
          QueryVariable("linkingProp1"),
          OntologyConstants.KnoraBase.HasLinkToValue,
        )
      assertTrue(generatedQueryVar == QueryVariable("linkingProp1__hasLinkToValue"))
    },
    test("optimise knora-base:isDeleted") {
      val typeStatement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(thingIRI),
      )
      val isDeletedStatement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
        obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri),
      )
      val linkStatement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(hasOtherThingIRI),
        obj = IriRef("http://rdfh.ch/0001/a-thing".toSmartIri),
      )
      val patterns: Seq[StatementPattern] = Seq(
        typeStatement,
        isDeletedStatement,
        linkStatement,
      )
      val optimisedPatterns = SparqlTransformer.optimiseIsDeletedWithFilter(patterns)
      val expectedPatterns  = Seq(
        typeStatement,
        linkStatement,
        FilterNotExistsPattern(
          Seq(
            StatementPattern(
              subj = QueryVariable("foo"),
              pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
              obj = XsdLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean.toSmartIri),
            ),
          ),
        ),
      )
      assertTrue(optimisedPatterns == expectedPatterns)
    },
    test("create an inference variable whose name is a valid SPARQL VARNAME even for an illegal-character object") {
      val statement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(hasTextIRI),
        obj = XsdLiteral(value = "(DE-588)118531379", datatype = OntologyConstants.Xsd.String.toSmartIri),
      )
      val generatedQueryVar = SparqlTransformer.createInferenceVariable(statement, "resTypes")
      assertTrue(generatedQueryVar.variableName.matches("^[A-Za-z0-9_]+$"))
    },
    test("create different inference variables for statements colliding under escapeEntityForVariable") {
      val statementA = StatementPattern(
        subj = IriRef("http://www.knora.org/ontology/0001/ab#cd".toSmartIri),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(thingIRI),
      )
      val statementB = StatementPattern(
        subj = IriRef("http://www.knora.org/ontology/0001/abc#d".toSmartIri),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(thingIRI),
      )
      val varA = SparqlTransformer.createInferenceVariable(statementA, "resTypes")
      val varB = SparqlTransformer.createInferenceVariable(statementB, "resTypes")
      assertTrue(varA != varB)
    },
    test("create the same inference variable for the same statement") {
      val statement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(thingIRI),
      )
      val varA = SparqlTransformer.createInferenceVariable(statement, "resTypes")
      val varB = SparqlTransformer.createInferenceVariable(statement, "resTypes")
      assertTrue(varA == varB)
    },
  )
}
