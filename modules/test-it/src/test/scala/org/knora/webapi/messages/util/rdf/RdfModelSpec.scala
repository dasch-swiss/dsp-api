/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import zio.test.Spec
import zio.test.TestAspect
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.io.BufferedInputStream
import java.io.FileInputStream

import dsp.errors.AssertionException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf.*

/**
 * Tests implementations of [[RdfModel]].
 */
object RdfModelSpec extends ZIOSpecDefault {

  private val model: RdfModel = JenaModelFactory.makeEmptyModel

  /**
   * Adds a statement, then searches for it by subject and predicate.
   *
   * @param subj    the subject.
   * @param pred    the predicate.
   * @param obj     the object.
   */
  private def addAndFindBySubjAndPred(
    subj: RdfResource,
    pred: IriNode,
    obj: RdfNode,
  ): Boolean = {
    val statement: Statement = JenaNodeFactory.makeStatement(subj = subj, pred = pred, obj = obj)
    model.addStatement(statement)
    model.find(subj = Some(subj), pred = Some(pred), obj = None).toSet == Set(statement)
  }

  val spec: Spec[Any, Nothing] = suite("An RdfModel")(
    test("add a triple with a datatype literal object, without first creating a Statement") {
      val subj: IriNode        = JenaNodeFactory.makeIriNode("http://example.org/1")
      val pred: IriNode        = JenaNodeFactory.makeIriNode("http://example.org/int_prop")
      val obj: DatatypeLiteral =
        JenaNodeFactory.makeDatatypeLiteral(value = "5", datatype = OntologyConstants.Xsd.Integer)

      model.add(subj = subj, pred = pred, obj = obj)
      val expectedStatement: Statement = JenaNodeFactory.makeStatement(subj = subj, pred = pred, obj = obj)
      assertTrue(model.find(subj = Some(subj), pred = Some(pred), obj = None).toSet == Set(expectedStatement))
    },
    test("add a triple with a datatype literal object") {
      assertTrue(
        addAndFindBySubjAndPred(
          subj = JenaNodeFactory.makeIriNode("http://example.org/2"),
          pred = JenaNodeFactory.makeIriNode("http://example.org/decimal_prop"),
          obj = JenaNodeFactory.makeDatatypeLiteral(value = "123.45", datatype = OntologyConstants.Xsd.Decimal),
        ),
      )
    },
    test("add a triple with an IRI object") {
      assertTrue(
        addAndFindBySubjAndPred(
          subj = JenaNodeFactory.makeIriNode("http://example.org/3"),
          pred = JenaNodeFactory.makeIriNode("http://example.org/object_prop"),
          obj = JenaNodeFactory.makeIriNode("http://example.org/1"),
        ),
      )
    },
    test("add a blank node") {
      assertTrue(
        addAndFindBySubjAndPred(
          subj = JenaNodeFactory.makeBlankNodeWithID("bnode_1"),
          pred = JenaNodeFactory.makeIriNode("http://example.org/boolean_prop"),
          obj = JenaNodeFactory.makeDatatypeLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean),
        ),
      )
    },
    test("add a triple with a blank node object") {
      assertTrue(
        addAndFindBySubjAndPred(
          subj = JenaNodeFactory.makeIriNode("http://example.org/4"),
          pred = JenaNodeFactory.makeIriNode("http://example.org/object_prop"),
          obj = JenaNodeFactory.makeBlankNodeWithID("bnode_1"),
        ),
      )
    },
    test("remove a triple") {
      val subj: IriNode = JenaNodeFactory.makeIriNode("http://example.org/1")
      model.remove(subj = Some(subj), pred = None, obj = None)
      assertTrue(model.find(subj = Some(subj), pred = None, obj = None).isEmpty)
    },
    test("add and find several triples with the same subject") {
      val subj: IriNode = JenaNodeFactory.makeIriNode("http://example.org/5")

      val booleanStatement: Statement = JenaNodeFactory.makeStatement(
        subj = subj,
        pred = JenaNodeFactory.makeIriNode("http://example.org/boolean_prop"),
        obj = JenaNodeFactory.makeDatatypeLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean),
      )

      val stringStatement: Statement = JenaNodeFactory.makeStatement(
        subj = subj,
        pred = JenaNodeFactory.makeIriNode("http://example.org/string_prop"),
        obj = JenaNodeFactory.makeDatatypeLiteral(value = "Hello", datatype = OntologyConstants.Xsd.String),
      )

      val stringWithLangStatement: Statement = JenaNodeFactory.makeStatement(
        subj = subj,
        pred = JenaNodeFactory.makeIriNode("http://example.org/string_with_lang_prop"),
        obj = JenaNodeFactory.makeStringWithLanguage(value = "Hello", language = "en"),
      )

      val statements: Set[Statement] = Set(
        booleanStatement,
        stringStatement,
        stringWithLangStatement,
      )

      model.addStatements(statements)

      val foundAll = model.find(subj = Some(subj), pred = None, obj = None).toSet == statements

      val stringWithLangFindResult: Set[Statement] = model
        .find(
          subj = Some(subj),
          pred = Some(stringWithLangStatement.pred),
          obj = Some(stringWithLangStatement.obj),
        )
        .toSet

      val singleResult = stringWithLangFindResult.size == 1

      // Try some matching.
      val matchOk = stringWithLangFindResult.head match {
        case statement =>
          statement.obj match {
            case resource: RdfResource =>
              throw AssertionException(s"Expected a string with a language code, got $resource")

            case rdfLiteral: RdfLiteral =>
              rdfLiteral match {
                case datatypeLiteral: DatatypeLiteral =>
                  throw AssertionException(s"Expected a string with a language code, got $datatypeLiteral")

                case stringWithLanguage: StringWithLanguage =>
                  stringWithLanguage.value == "Hello" && stringWithLanguage.language == "en"
              }
          }
      }

      assertTrue(foundAll, singleResult, matchOk)
    },
    test("add, find, and remove quads") {
      val labelPred: IriNode   = JenaNodeFactory.makeIriNode(OntologyConstants.Rdfs.Label)
      val commentPred: IriNode = JenaNodeFactory.makeIriNode(OntologyConstants.Rdfs.Comment)
      val context1             = "http://example.org/graph1"

      val graph1LabelStatement = JenaNodeFactory.makeStatement(
        subj = JenaNodeFactory.makeIriNode("http://example.org/6"),
        pred = labelPred,
        obj = JenaNodeFactory
          .makeDatatypeLiteral(value = "Lucky's Discount X-Wing Repair", datatype = OntologyConstants.Xsd.String),
        context = Some(context1),
      )

      val graph1CommentStatement = JenaNodeFactory.makeStatement(
        subj = JenaNodeFactory.makeIriNode("http://example.org/6"),
        pred = commentPred,
        obj = JenaNodeFactory
          .makeDatatypeLiteral(value = "A safe flight or your money back", datatype = OntologyConstants.Xsd.String),
        context = Some(context1),
      )

      val graph1 = Set(
        graph1LabelStatement,
        graph1CommentStatement,
      )

      val context2 = "http://example.org/graph2"

      val graph2LabelStatement = JenaNodeFactory.makeStatement(
        subj = JenaNodeFactory.makeIriNode("http://example.org/7"),
        pred = labelPred,
        obj = JenaNodeFactory
          .makeDatatypeLiteral(value = "Mos Eisley Used Droids", datatype = OntologyConstants.Xsd.String),
        context = Some(context2),
      )

      val graph2CommentStatement = JenaNodeFactory.makeStatement(
        subj = JenaNodeFactory.makeIriNode("http://example.org/7"),
        pred = commentPred,
        obj = JenaNodeFactory
          .makeDatatypeLiteral(value = "All droids guaranteed for 10 seconds", datatype = OntologyConstants.Xsd.String),
        context = Some(context2),
      )

      val graph2 = Set(
        graph2LabelStatement,
        graph2CommentStatement,
      )

      model.addStatements(graph1)
      model.addStatements(graph2)

      val ok1 = model.find(subj = None, pred = None, obj = None, context = Some(context1)).toSet == graph1
      val ok2 = model.find(subj = None, pred = None, obj = None, context = Some(context2)).toSet == graph2
      val ok3 =
        model.find(subj = None, pred = Some(labelPred), obj = None).toSet == Set(
          graph1LabelStatement,
          graph2LabelStatement,
        )
      val ok4 =
        model.find(subj = None, pred = Some(commentPred), obj = None).toSet == Set(
          graph1CommentStatement,
          graph2CommentStatement,
        )

      model.removeStatement(graph1CommentStatement)
      val ok5 = !model.contains(graph1CommentStatement)

      val ok6 = model.contains(graph1LabelStatement)
      val ok7 = model.contains(graph2LabelStatement)
      val ok8 = model.contains(graph2CommentStatement)

      assertTrue(ok1, ok2, ok3, ok4, ok5, ok6, ok7, ok8)
    },
    test("Remove a statement from the default graph, rather than an otherwise identical statement in a named graph") {
      val subj: IriNode        = JenaNodeFactory.makeIriNode("http://example.org/foo")
      val pred: IriNode        = JenaNodeFactory.makeIriNode(OntologyConstants.Rdfs.Label)
      val obj: DatatypeLiteral =
        JenaNodeFactory.makeDatatypeLiteral(value = "Foo", datatype = OntologyConstants.Xsd.String)

      val statementInDefaultGraph: Statement = JenaNodeFactory.makeStatement(
        subj = subj,
        pred = pred,
        obj = obj,
        context = None,
      )

      val context = "http://example.org/namedGraph"

      val statementInNamedGraph = JenaNodeFactory.makeStatement(
        subj = subj,
        pred = pred,
        obj = obj,
        context = Some(context),
      )

      model.addStatement(statementInDefaultGraph)
      model.addStatement(statementInNamedGraph)

      val ok1 = model.contains(statementInDefaultGraph)
      val ok2 = model.contains(statementInNamedGraph)

      model.removeStatement(statementInDefaultGraph)

      val ok3 = !model.contains(statementInDefaultGraph)
      val ok4 = model.contains(statementInNamedGraph)

      assertTrue(ok1, ok2, ok3, ok4)
    },
    test("Remove a statement from the default graph, and an otherwise identical statement in a named graph") {
      val subj: IriNode        = JenaNodeFactory.makeIriNode("http://example.org/bar")
      val pred: IriNode        = JenaNodeFactory.makeIriNode(OntologyConstants.Rdfs.Label)
      val obj: DatatypeLiteral =
        JenaNodeFactory.makeDatatypeLiteral(value = "Bar", datatype = OntologyConstants.Xsd.String)

      val statementInDefaultGraph: Statement = JenaNodeFactory.makeStatement(
        subj = subj,
        pred = pred,
        obj = obj,
        context = None,
      )

      val context = "http://example.org/namedGraph"

      val statementInNamedGraph = JenaNodeFactory.makeStatement(
        subj = subj,
        pred = pred,
        obj = obj,
        context = Some(context),
      )

      model.addStatement(statementInDefaultGraph)
      model.addStatement(statementInNamedGraph)

      val ok1 = model.contains(statementInDefaultGraph)
      val ok2 = model.contains(statementInNamedGraph)

      model.remove(
        subj = Some(subj),
        pred = Some(pred),
        obj = Some(obj),
      )

      val ok3 = !model.contains(statementInDefaultGraph)
      val ok4 = !model.contains(statementInNamedGraph)

      assertTrue(ok1, ok2, ok3, ok4)
    },
    test("do a SPARQL SELECT query") {
      val fileInputStream =
        new BufferedInputStream(new FileInputStream("test_data/project_data/anything-data.ttl"))
      val anythingModel: RdfModel =
        RdfFormatUtil.inputStreamToRdfModel(inputStream = fileInputStream, rdfFormat = Turtle)
      fileInputStream.close()

      val rdfRepository: JenaRepository = anythingModel.asRepository

      val selectQuery =
        """PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |SELECT ?resource ?value ?decimalValue WHERE {
          |    ?resource anything:hasDecimal ?value .
          |    ?value knora-base:valueHasDecimal ?decimalValue .
          |} ORDER BY ?resource""".stripMargin

      val queryResult: SparqlSelectResult = rdfRepository.doSelect(selectQuery)

      val results: Seq[Map[String, String]] = queryResult.results.bindings.map(_.rowMap)

      val expectedResults = Seq(
        Map(
          "resource"     -> "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw",
          "value"        -> "http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw/values/bXMwnrHvQH2DMjOFrGmNzg",
          "decimalValue" -> "1.5",
        ),
        Map(
          "resource"     -> "http://rdfh.ch/0001/uqmMo72OQ2K2xe7mkIytlg",
          "value"        -> "http://rdfh.ch/0001/uqmMo72OQ2K2xe7mkIytlg/values/85et-o-STOmn2JcVqrGTCQ",
          "decimalValue" -> "2.1",
        ),
      )

      assertTrue(
        queryResult.head.vars == Seq("resource", "value", "decimalValue"),
        results == expectedResults,
      )
    },
  ) @@ TestAspect.sequential
}
