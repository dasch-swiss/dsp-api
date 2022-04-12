/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.search

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

/**
 * Tests [[SparqlTransformer]].
 */
class SparqlTransformerSpec extends CoreSpec() {

  protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

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
        IriRef("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri)
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
          OntologyConstants.KnoraBase.HasLinkToValue
        )

      generatedQueryVar should ===(QueryVariable("linkingProp1__hasLinkToValue"))
    }

    "optimise knora-base:isDeleted" in {
      val typeStatement = StatementPattern.makeExplicit(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri)
      )
      val isDeletedStatement = StatementPattern.makeExplicit(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
        obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
      )
      val linkStatement = StatementPattern.makeExplicit(
        subj = QueryVariable("foo"),
        pred = IriRef("http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri),
        obj = IriRef("http://rdfh.ch/0001/a-thing".toSmartIri)
      )

      val patterns: Seq[StatementPattern] = Seq(
        typeStatement,
        isDeletedStatement,
        linkStatement
      )

      val optimisedPatterns = SparqlTransformer.optimiseIsDeletedWithFilter(patterns)

      val expectedPatterns = Seq(
        typeStatement,
        linkStatement,
        FilterNotExistsPattern(
          Seq(
            StatementPattern.makeExplicit(
              subj = QueryVariable("foo"),
              pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
              obj = XsdLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
            )
          )
        )
      )

      optimisedPatterns should ===(expectedPatterns)
    }

    "move a BIND pattern to the beginning of a block" in {
      val typeStatement = StatementPattern.makeExplicit(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri)
      )
      val hasValueStatement =
        StatementPattern.makeExplicit(
          subj = QueryVariable("foo"),
          pred = IriRef("http://www.knora.org/ontology/0001/anything#hasText".toSmartIri),
          obj = QueryVariable("text")
        )
      val bindPattern =
        BindPattern(variable = QueryVariable("foo"), expression = IriRef("http://rdfh.ch/0001/a-thing".toSmartIri))

      val patterns: Seq[QueryPattern] = Seq(
        typeStatement,
        hasValueStatement,
        bindPattern
      )

      val optimisedPatterns = SparqlTransformer.moveBindToBeginning(patterns)

      val expectedPatterns: Seq[QueryPattern] = Seq(
        bindPattern,
        typeStatement,
        hasValueStatement
      )

      optimisedPatterns should ===(expectedPatterns)
    }

    "move a Lucene query pattern to the beginning of a block" in {
      val hasValueStatement =
        StatementPattern.makeExplicit(
          subj = QueryVariable("foo"),
          pred = IriRef("http://www.knora.org/ontology/0001/anything#hasText".toSmartIri),
          obj = QueryVariable("text")
        )
      val valueHasStringStatement =
        StatementPattern.makeExplicit(
          subj = QueryVariable("text"),
          pred = IriRef(OntologyConstants.KnoraBase.ValueHasString.toSmartIri),
          QueryVariable("text__valueHasString")
        )

      val luceneQueryPattern = LuceneQueryPattern(
        subj = QueryVariable("text"),
        obj = QueryVariable("text__valueHasString"),
        queryString = LuceneQueryString("Zeitglöcklein"),
        literalStatement = Some(valueHasStringStatement)
      )

      val patterns: Seq[QueryPattern] = Seq(
        hasValueStatement,
        valueHasStringStatement,
        luceneQueryPattern
      )

      val optimisedPatterns = SparqlTransformer.moveLuceneToBeginning(patterns)

      val expectedPatterns: Seq[QueryPattern] = Seq(
        luceneQueryPattern,
        hasValueStatement,
        valueHasStringStatement
      )

      optimisedPatterns should ===(expectedPatterns)
    }

    "expand an rdf:type statement to simulate RDFS inference" in {
      val typeStatement = StatementPattern.makeInferred(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef("http://www.knora.org/ontology/0001/anything#Thing".toSmartIri)
      )
      val expandedStatements = SparqlTransformer.transformStatementInWhereForNoInference(
        statementPattern = typeStatement,
        simulateInference = true
      )

      val expectedStatements: Seq[StatementPattern] = Seq(
        StatementPattern(
          subj = QueryVariable(variableName = "foo__subClassOf__httpwwwknoraorgontology0001anythingThing"),
          pred = IriRef(
            iri = OntologyConstants.Rdfs.SubClassOf.toSmartIri,
            propertyPathOperator = Some('*')
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "foo"),
          pred = IriRef(
            iri = OntologyConstants.Rdf.Type.toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "foo__subClassOf__httpwwwknoraorgontology0001anythingThing"),
          namedGraph = None
        )
      )

      expandedStatements should ===(expectedStatements)
    }

    "expand a statement with a property IRI to simulate RDFS inference" ignore {
      // TODO-BL: currently failing, but also trivial because in this case, nothing is inferred
      val hasValueStatement =
        StatementPattern.makeInferred(
          subj = QueryVariable("foo"),
          pred = IriRef("http://www.knora.org/ontology/0001/anything#hasText".toSmartIri),
          obj = QueryVariable("text")
        )
      val expandedStatements = SparqlTransformer.transformStatementInWhereForNoInference(
        statementPattern = hasValueStatement,
        simulateInference = true
      )
      expandedStatements.map(println(_))

      val expectedStatements: Seq[StatementPattern] = Seq(
        StatementPattern(
          subj = QueryVariable(variableName = "httpwwwknoraorgontology0001anythinghasText__subPropertyOf"),
          pred = IriRef(
            iri = OntologyConstants.Rdfs.SubPropertyOf.toSmartIri,
            propertyPathOperator = Some('*')
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "foo"),
          pred = QueryVariable(variableName = "httpwwwknoraorgontology0001anythinghasText__subPropertyOf"),
          obj = QueryVariable(variableName = "text"),
          namedGraph = None
        )
      )

      expandedStatements should ===(expectedStatements)
    }
  }
}
