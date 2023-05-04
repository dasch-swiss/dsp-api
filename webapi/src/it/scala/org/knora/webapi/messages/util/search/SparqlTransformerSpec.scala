/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.search

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search._
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString
import org.knora.webapi.messages.util.search.gravsearch.transformers._

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
      val typeStatement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(thingIRI)
      )
      val isDeletedStatement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.KnoraBase.IsDeleted.toSmartIri),
        obj = XsdLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean.toSmartIri)
      )
      val linkStatement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(hasOtherThingIRI),
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
            StatementPattern(
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
      val typeStatement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(thingIRI)
      )
      val hasValueStatement =
        StatementPattern(
          subj = QueryVariable("foo"),
          pred = IriRef(hasTextIRI),
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
        StatementPattern(
          subj = QueryVariable("foo"),
          pred = IriRef(hasTextIRI),
          obj = QueryVariable("text")
        )
      val valueHasStringStatement =
        StatementPattern(
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

    "not simulate any RDF inference for a class, if there are no known subclasses" in {
      val typeStatement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(blueThingIRI)
      )

      val expandedStatements = getService[OntologyInferencer].transformStatementInWhere(
        statementPattern = typeStatement,
        simulateInference = true
      )

      val expectedStatements: Seq[StatementPattern] = Seq(
        StatementPattern(
          subj = QueryVariable("foo"),
          pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
          obj = IriRef(blueThingIRI)
        )
      )

      UnsafeZioRun.runOrThrow(expandedStatements) should equal(expectedStatements)
    }

    "create a union pattern to simulate RDF inference for a class, if there are known subclasses" in {
      val typeStatement = StatementPattern(
        subj = QueryVariable("foo"),
        pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
        obj = IriRef(thingIRI)
      )
      val expectedUnionPattern = UnionPattern(
        Seq(
          Seq(
            StatementPattern(
              subj = QueryVariable("foo"),
              pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
              obj = IriRef(thingIRI)
            )
          ),
          Seq(
            StatementPattern(
              subj = QueryVariable("foo"),
              pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
              obj = IriRef(blueThingIRI)
            )
          ),
          Seq(
            StatementPattern(
              subj = QueryVariable("foo"),
              pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
              obj = IriRef("http://www.knora.org/ontology/0001/something#Something".toSmartIri)
            )
          ),
          Seq(
            StatementPattern(
              subj = QueryVariable("foo"),
              pred = IriRef(OntologyConstants.Rdf.Type.toSmartIri),
              obj = IriRef("http://www.knora.org/ontology/0001/anything#ThingWithSeqnum".toSmartIri)
            )
          )
        )
      )
      val expandedStatements = getService[OntologyInferencer].transformStatementInWhere(
        statementPattern = typeStatement,
        simulateInference = true
      )
      UnsafeZioRun.runOrThrow(expandedStatements) match {
        case (head: UnionPattern) :: Nil =>
          head.blocks.toSet should equal(expectedUnionPattern.blocks.toSet)
        case _ => throw new AssertionError("Simulated RDF inference should have resulted in exactly one Union Pattern")
      }
    }

    "not simulate any RDF inference for a property, if there are no known subproperties" in {
      val hasValueStatement =
        StatementPattern(
          subj = QueryVariable("foo"),
          pred = IriRef(hasTextIRI),
          obj = QueryVariable("text")
        )
      val expectedStatements: Seq[StatementPattern] = Seq(
        StatementPattern(
          subj = QueryVariable(variableName = "foo"),
          pred = IriRef(
            iri = hasTextIRI,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "text")
        )
      )
      val expandedStatements = getService[OntologyInferencer].transformStatementInWhere(
        statementPattern = hasValueStatement,
        simulateInference = true
      )
      UnsafeZioRun.runOrThrow(expandedStatements) should equal(expectedStatements)
    }

    "create a union pattern to simulate RDF inference for a property, if there are known subproperties" in {
      val hasValueStatement =
        StatementPattern(
          subj = QueryVariable("foo"),
          pred = IriRef(hasOtherThingIRI),
          obj = QueryVariable("text")
        )
      val expectedUnionPattern: UnionPattern = UnionPattern(
        Seq(
          Seq(
            StatementPattern(
              subj = QueryVariable(variableName = "foo"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/0001/something#hasOtherSomething".toSmartIri,
                propertyPathOperator = None
              ),
              obj = QueryVariable(variableName = "text")
            )
          ),
          Seq(
            StatementPattern(
              subj = QueryVariable(variableName = "foo"),
              pred = IriRef(
                iri = hasOtherThingIRI,
                propertyPathOperator = None
              ),
              obj = QueryVariable(variableName = "text")
            )
          ),
          Seq(
            StatementPattern(
              subj = QueryVariable(variableName = "foo"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/0001/anything#hasBlueThing".toSmartIri,
                propertyPathOperator = None
              ),
              obj = QueryVariable(variableName = "text")
            )
          )
        )
      )
      val expandedStatements = getService[OntologyInferencer].transformStatementInWhere(
        statementPattern = hasValueStatement,
        simulateInference = true
      )
      UnsafeZioRun.runOrThrow(expandedStatements) match {
        case (head: UnionPattern) :: Nil =>
          head.blocks.toSet should equal(expectedUnionPattern.blocks.toSet)
        case _ => throw new AssertionError("Simulated RDF inference should have resulted in exactly one Union Pattern")
      }
    }
  }
}
