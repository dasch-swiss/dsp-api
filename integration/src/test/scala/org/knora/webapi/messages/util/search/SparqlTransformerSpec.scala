/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.search

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.transformers.*
import org.knora.webapi.routing.UnsafeZioRun

/**
 * Tests [[SparqlTransformer]].
 */
class SparqlTransformerSpec extends CoreSpec {

  protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val thingIRI         = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri
  private val blueThingIRI     = "http://www.knora.org/ontology/0001/anything#BlueThing".toSmartIri
  private val hasOtherThingIRI = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri
  private val hasTextIRI       = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri

  "SparqlTransformer" should {

    "create a syntactically valid base name from a given variable" in {
      val baseName = SparqlTransformer.escapeEntityForVariable(QueryVariable("book"))
      baseName should ===("book")
    }

    "create a syntactically valid base name from a given data IRI" in {
      val baseName = SparqlTransformer.escapeEntityForVariable(IriRef("http://rdfh.ch/users/91e19f1e01".toSmartIri))
      baseName should ===("httprdfhchusers91e19f1e01")
    }

    "create a syntactically valid base name from a given ontology IRI" in {
      val baseName = SparqlTransformer.escapeEntityForVariable(
        IriRef("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri),
      )
      baseName should ===("httpwwwknoraorgontology0803incunabulabook")
    }

    "create a syntactically valid base name from a given string literal" in {
      val baseName =
        SparqlTransformer.escapeEntityForVariable(XsdLiteral("dumm", OntologyConstants.Xsd.String.toSmartIri))
      baseName should ===("dumm")
    }

    "create a unique variable name based on an entity and a property" in {
      val generatedQueryVar =
        SparqlTransformer.createUniqueVariableNameFromEntityAndProperty(
          QueryVariable("linkingProp1"),
          OntologyConstants.KnoraBase.HasLinkToValue,
        )
      generatedQueryVar should ===(QueryVariable("linkingProp1__hasLinkToValue"))
    }

    "optimise knora-base:isDeleted" in {
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
      val expectedPatterns = Seq(
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
      optimisedPatterns should ===(expectedPatterns)
    }

    "move a BIND pattern to the beginning of a block" in {
      val typeStatement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(thingIRI),
      )
      val hasValueStatement =
        StatementPattern(
          subj = QueryVariable("foo"),
          pred = IriRef(hasTextIRI),
          obj = QueryVariable("text"),
        )
      val bindPattern =
        BindPattern(variable = QueryVariable("foo"), expression = IriRef("http://rdfh.ch/0001/a-thing".toSmartIri))
      val patterns: Seq[QueryPattern] = Seq(
        typeStatement,
        hasValueStatement,
        bindPattern,
      )
      val optimisedPatterns = SparqlTransformer.moveBindToBeginning(patterns)
      val expectedPatterns: Seq[QueryPattern] = Seq(
        bindPattern,
        typeStatement,
        hasValueStatement,
      )
      optimisedPatterns should ===(expectedPatterns)
    }

    "move a Lucene query pattern to the beginning of a block" in {
      val hasValueStatement =
        StatementPattern(
          subj = QueryVariable("foo"),
          pred = IriRef(hasTextIRI),
          obj = QueryVariable("text"),
        )
      val valueHasStringStatement =
        StatementPattern(
          subj = QueryVariable("text"),
          pred = IriRef(OntologyConstants.KnoraBase.ValueHasString.toSmartIri),
          QueryVariable("text__valueHasString"),
        )
      val luceneQueryPattern = StatementPattern(
        subj = QueryVariable("text"),
        pred = IriRef(OntologyConstants.Fuseki.luceneQueryPredicate.toSmartIri),
        obj = XsdLiteral(
          value = "Zeitglöcklein",
          datatype = OntologyConstants.Xsd.String.toSmartIri,
        ),
      )
      val patterns: Seq[QueryPattern] = Seq(
        hasValueStatement,
        valueHasStringStatement,
        luceneQueryPattern,
      )
      val optimisedPatterns = SparqlTransformer.moveLuceneToBeginning(patterns)
      val expectedPatterns: Seq[QueryPattern] = Seq(
        luceneQueryPattern,
        hasValueStatement,
        valueHasStringStatement,
      )
      optimisedPatterns should ===(expectedPatterns)
    }

    "not simulate any RDF inference for a class, if there are no known subclasses" in {
      val typeStatement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(blueThingIRI),
      )

      val expandedStatements = UnsafeZioRun
        .service[OntologyInferencer]
        .transformStatementInWhere(
          statementPattern = typeStatement,
          simulateInference = true,
        )

      val expectedStatements: Seq[StatementPattern] = Seq(
        StatementPattern(
          subj = QueryVariable("foo"),
          pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
          obj = IriRef(blueThingIRI),
        ),
      )

      UnsafeZioRun.runOrThrow(expandedStatements) should equal(expectedStatements)
    }

    "not simulate any RDF inference for a property, if there are no known subproperties" in {
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
      val expandedStatements = UnsafeZioRun
        .service[OntologyInferencer]
        .transformStatementInWhere(
          statementPattern = hasValueStatement,
          simulateInference = true,
        )
      UnsafeZioRun.runOrThrow(expandedStatements) should equal(expectedStatements)
    }

    "create a values pattern to simulate RDF inference for a class, if there are known subclasses" in {
      val typeStatement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(thingIRI),
      )
      val expandedStatements = UnsafeZioRun
        .service[OntologyInferencer]
        .transformStatementInWhere(
          statementPattern = typeStatement,
          simulateInference = true,
          queryVariableSuffix = Some("5432"),
        )
      UnsafeZioRun.runOrThrow(expandedStatements) should equal(
        List(
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
        ),
      )
    }

    "create a values pattern to simulate RDF inference for a property, if there are known subproperties" in {
      val hasValueStatement =
        StatementPattern(
          subj = QueryVariable("foo"),
          pred = IriRef(hasOtherThingIRI),
          obj = QueryVariable("text"),
        )
      val expandedStatements = UnsafeZioRun
        .service[OntologyInferencer]
        .transformStatementInWhere(
          statementPattern = hasValueStatement,
          simulateInference = true,
          queryVariableSuffix = Some("5432"),
        )

      UnsafeZioRun.runOrThrow(expandedStatements) should equal(
        List(
          ValuesPattern(
            QueryVariable("subProp5432"),
            Set(
              IriRef("http://www.knora.org/ontology/0001/something#hasOtherSomething".toSmartIri),
              IriRef("http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri),
              IriRef("http://www.knora.org/ontology/0001/anything#hasBlueThing".toSmartIri),
            ),
          ),
          StatementPattern(QueryVariable("foo"), QueryVariable("subProp5432"), QueryVariable("text")),
        ),
      )
    }
  }
}
