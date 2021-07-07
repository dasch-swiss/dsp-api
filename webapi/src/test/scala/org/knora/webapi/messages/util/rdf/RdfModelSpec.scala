/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util.rdf

import java.io.{BufferedInputStream, FileInputStream}

import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.feature._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.{CoreSpec, IRI}

/**
  * Tests implementations of [[RdfModel]].
  *
  * @param featureToggle a feature toggle specifying which implementation of [[RdfModel]] should
  *                      be used for the test.
  */
abstract class RdfModelSpec(featureToggle: FeatureToggle) extends CoreSpec {
  private val featureFactoryConfig: FeatureFactoryConfig = new TestFeatureFactoryConfig(
    testToggles = Set(featureToggle),
    parent = new KnoraSettingsFeatureFactoryConfig(settings)
  )

  private val model: RdfModel = RdfFeatureFactory.getRdfModelFactory(featureFactoryConfig).makeEmptyModel
  private val nodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)
  private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(featureFactoryConfig)

  /**
    * Adds a statement, then searches for it by subject and predicate.
    *
    * @param subj    the subject.
    * @param pred    the predicate.
    * @param obj     the object.
    * @param context the context.
    */
  private def addAndFindBySubjAndPred(subj: RdfResource,
                                      pred: IriNode,
                                      obj: RdfNode,
                                      context: Option[IRI] = None): Unit = {
    val statement: Statement = nodeFactory.makeStatement(subj = subj, pred = pred, obj = obj)
    model.addStatement(statement)
    assert(model.find(subj = Some(subj), pred = Some(pred), obj = None).toSet == Set(statement))
  }

  "An RdfModel" should {
    "add a triple with a datatype literal object, without first creating a Statement" in {
      val subj: IriNode = nodeFactory.makeIriNode("http://example.org/1")
      val pred: IriNode = nodeFactory.makeIriNode("http://example.org/int_prop")
      val obj: DatatypeLiteral = nodeFactory.makeDatatypeLiteral(value = "5", datatype = OntologyConstants.Xsd.Integer)

      model.add(subj = subj, pred = pred, obj = obj)
      val expectedStatement: Statement = nodeFactory.makeStatement(subj = subj, pred = pred, obj = obj)
      assert(model.find(subj = Some(subj), pred = Some(pred), obj = None).toSet == Set(expectedStatement))
    }

    "add a triple with a datatype literal object" in {
      addAndFindBySubjAndPred(
        subj = nodeFactory.makeIriNode("http://example.org/2"),
        pred = nodeFactory.makeIriNode("http://example.org/decimal_prop"),
        obj = nodeFactory.makeDatatypeLiteral(value = "123.45", datatype = OntologyConstants.Xsd.Decimal)
      )
    }

    "add a triple with an IRI object" in {
      addAndFindBySubjAndPred(
        subj = nodeFactory.makeIriNode("http://example.org/3"),
        pred = nodeFactory.makeIriNode("http://example.org/object_prop"),
        obj = nodeFactory.makeIriNode("http://example.org/1")
      )
    }

    "add a blank node" in {
      addAndFindBySubjAndPred(
        subj = nodeFactory.makeBlankNodeWithID("bnode_1"),
        pred = nodeFactory.makeIriNode("http://example.org/boolean_prop"),
        obj = nodeFactory.makeDatatypeLiteral(value = "true", datatype = OntologyConstants.Xsd.Boolean)
      )
    }

    "add a triple with a blank node object" in {
      addAndFindBySubjAndPred(
        subj = nodeFactory.makeIriNode("http://example.org/4"),
        pred = nodeFactory.makeIriNode("http://example.org/object_prop"),
        obj = nodeFactory.makeBlankNodeWithID("bnode_1")
      )
    }

    "remove a triple" in {
      val subj: IriNode = nodeFactory.makeIriNode("http://example.org/1")
      model.remove(subj = Some(subj), pred = None, obj = None)
      assert(model.find(subj = Some(subj), pred = None, obj = None).isEmpty)
    }

    "add and find several triples with the same subject" in {
      val subj: IriNode = nodeFactory.makeIriNode("http://example.org/5")

      val booleanStatement: Statement = nodeFactory.makeStatement(
        subj = subj,
        pred = nodeFactory.makeIriNode("http://example.org/boolean_prop"),
        obj = nodeFactory.makeDatatypeLiteral(value = "false", datatype = OntologyConstants.Xsd.Boolean)
      )

      val stringStatement: Statement = nodeFactory.makeStatement(
        subj = subj,
        pred = nodeFactory.makeIriNode("http://example.org/string_prop"),
        obj = nodeFactory.makeDatatypeLiteral(value = "Hello", datatype = OntologyConstants.Xsd.String)
      )

      val stringWithLangStatement: Statement = nodeFactory.makeStatement(
        subj = subj,
        pred = nodeFactory.makeIriNode("http://example.org/string_with_lang_prop"),
        obj = nodeFactory.makeStringWithLanguage(value = "Hello", language = "en")
      )

      val statements: Set[Statement] = Set(
        booleanStatement,
        stringStatement,
        stringWithLangStatement
      )

      model.addStatements(statements)
      assert(model.find(subj = Some(subj), pred = None, obj = None).toSet == statements)

      val stringWithLangFindResult: Set[Statement] = model
        .find(
          subj = Some(subj),
          pred = Some(stringWithLangStatement.pred),
          obj = Some(stringWithLangStatement.obj)
        )
        .toSet

      assert(stringWithLangFindResult.size == 1)

      // Try some matching.
      stringWithLangFindResult.head match {
        case statement =>
          statement.obj match {
            case resource: RdfResource =>
              throw AssertionException(s"Expected a string with a language code, got $resource")

            case rdfLiteral: RdfLiteral =>
              rdfLiteral match {
                case datatypeLiteral: DatatypeLiteral =>
                  throw AssertionException(s"Expected a string with a language code, got $datatypeLiteral")

                case stringWithLanguage: StringWithLanguage =>
                  assert(stringWithLanguage.value == "Hello" && stringWithLanguage.language == "en")
              }
          }
      }
    }

    "add, find, and remove quads" in {
      val labelPred: IriNode = nodeFactory.makeIriNode(OntologyConstants.Rdfs.Label)
      val commentPred: IriNode = nodeFactory.makeIriNode(OntologyConstants.Rdfs.Comment)
      val context1 = "http://example.org/graph1"

      val graph1LabelStatement = nodeFactory.makeStatement(
        subj = nodeFactory.makeIriNode("http://example.org/6"),
        pred = labelPred,
        obj = nodeFactory.makeDatatypeLiteral(value = "Lucky's Discount X-Wing Repair",
                                              datatype = OntologyConstants.Xsd.String),
        context = Some(context1)
      )

      val graph1CommentStatement = nodeFactory.makeStatement(
        subj = nodeFactory.makeIriNode("http://example.org/6"),
        pred = commentPred,
        obj = nodeFactory.makeDatatypeLiteral(value = "A safe flight or your money back",
                                              datatype = OntologyConstants.Xsd.String),
        context = Some(context1)
      )

      val graph1 = Set(
        graph1LabelStatement,
        graph1CommentStatement
      )

      val context2 = "http://example.org/graph2"

      val graph2LabelStatement = nodeFactory.makeStatement(
        subj = nodeFactory.makeIriNode("http://example.org/7"),
        pred = labelPred,
        obj = nodeFactory.makeDatatypeLiteral(value = "Mos Eisley Used Droids", datatype = OntologyConstants.Xsd.String),
        context = Some(context2)
      )

      val graph2CommentStatement = nodeFactory.makeStatement(
        subj = nodeFactory.makeIriNode("http://example.org/7"),
        pred = commentPred,
        obj = nodeFactory.makeDatatypeLiteral(value = "All droids guaranteed for 10 seconds",
                                              datatype = OntologyConstants.Xsd.String),
        context = Some(context2)
      )

      val graph2 = Set(
        graph2LabelStatement,
        graph2CommentStatement
      )

      model.addStatements(graph1)
      model.addStatements(graph2)

      assert(model.find(subj = None, pred = None, obj = None, context = Some(context1)).toSet == graph1)
      assert(model.find(subj = None, pred = None, obj = None, context = Some(context2)).toSet == graph2)
      assert(
        model.find(subj = None, pred = Some(labelPred), obj = None).toSet == Set(graph1LabelStatement,
                                                                                 graph2LabelStatement))
      assert(
        model.find(subj = None, pred = Some(commentPred), obj = None).toSet == Set(graph1CommentStatement,
                                                                                   graph2CommentStatement))

      model.removeStatement(graph1CommentStatement)
      assert(!model.contains(graph1CommentStatement))

      assert(model.contains(graph1LabelStatement))
      assert(model.contains(graph2LabelStatement))
      assert(model.contains(graph2CommentStatement))
    }

    "Remove a statement from the default graph, rather than an otherwise identical statement in a named graph" in {
      val subj: IriNode = nodeFactory.makeIriNode("http://example.org/foo")
      val pred: IriNode = nodeFactory.makeIriNode(OntologyConstants.Rdfs.Label)
      val obj: DatatypeLiteral = nodeFactory.makeDatatypeLiteral(value = "Foo", datatype = OntologyConstants.Xsd.String)

      val statementInDefaultGraph: Statement = nodeFactory.makeStatement(
        subj = subj,
        pred = pred,
        obj = obj,
        context = None
      )

      val context = "http://example.org/namedGraph"

      val statementInNamedGraph = nodeFactory.makeStatement(
        subj = subj,
        pred = pred,
        obj = obj,
        context = Some(context)
      )

      model.addStatement(statementInDefaultGraph)
      model.addStatement(statementInNamedGraph)

      assert(model.contains(statementInDefaultGraph))
      assert(model.contains(statementInNamedGraph))

      model.removeStatement(statementInDefaultGraph)

      assert(!model.contains(statementInDefaultGraph))
      assert(model.contains(statementInNamedGraph))
    }

    "Remove a statement from the default graph, and an otherwise identical statement in a named graph" in {
      val subj: IriNode = nodeFactory.makeIriNode("http://example.org/bar")
      val pred: IriNode = nodeFactory.makeIriNode(OntologyConstants.Rdfs.Label)
      val obj: DatatypeLiteral = nodeFactory.makeDatatypeLiteral(value = "Bar", datatype = OntologyConstants.Xsd.String)

      val statementInDefaultGraph: Statement = nodeFactory.makeStatement(
        subj = subj,
        pred = pred,
        obj = obj,
        context = None
      )

      val context = "http://example.org/namedGraph"

      val statementInNamedGraph = nodeFactory.makeStatement(
        subj = subj,
        pred = pred,
        obj = obj,
        context = Some(context)
      )

      model.addStatement(statementInDefaultGraph)
      model.addStatement(statementInNamedGraph)

      assert(model.contains(statementInDefaultGraph))
      assert(model.contains(statementInNamedGraph))

      model.remove(
        subj = Some(subj),
        pred = Some(pred),
        obj = Some(obj)
      )

      assert(!model.contains(statementInDefaultGraph))
      assert(!model.contains(statementInNamedGraph))
    }

    "do a SPARQL SELECT query" in {
      val fileInputStream = new BufferedInputStream(new FileInputStream("test_data/all_data/anything-data.ttl"))
      val anythingModel: RdfModel =
        rdfFormatUtil.inputStreamToRdfModel(inputStream = fileInputStream, rdfFormat = Turtle)
      fileInputStream.close()

      val rdfRepository: RdfRepository = anythingModel.asRepository

      val selectQuery =
        """PREFIX anything: <http://www.knora.org/ontology/0001/anything#>
          |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
          |
          |SELECT ?resource ?value ?decimalValue WHERE {
          |    ?resource anything:hasDecimal ?value .
          |    ?value knora-base:valueHasDecimal ?decimalValue .
          |} ORDER BY ?resource""".stripMargin

      val queryResult: SparqlSelectResult = rdfRepository.doSelect(selectQuery)

      assert(queryResult.head.vars == Seq("resource", "value", "decimalValue"))

      val results: Seq[Map[String, String]] = queryResult.results.bindings.map(_.rowMap)

      val expectedResults = Seq(
        Map(
          "resource" -> "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw",
          "value" -> "http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw/values/bXMwnrHvQH2DMjOFrGmNzg",
          "decimalValue" -> "1.5"
        ),
        Map(
          "resource" -> "http://rdfh.ch/resources/uqmMo72OQ2K2xe7mkIytlg",
          "value" -> "http://rdfh.ch/resources/uqmMo72OQ2K2xe7mkIytlg/values/85et-o-STOmn2JcVqrGTCQ",
          "decimalValue" -> "2.1"
        )
      )

      assert(results == expectedResults)
    }
  }
}
