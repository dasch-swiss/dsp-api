/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.util

import org.knora.webapi.{CoreSpec, IRI}
import org.knora.webapi.feature._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.util.rdf._

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

    private val rdfModelFactory: RdfModelFactory = new RdfModelFactory
    private val model: RdfModel = rdfModelFactory.makeRdfModel(featureFactoryConfig)
    private val nodeFactory: RdfNodeFactory = rdfModelFactory.makeRdfNodeFactory(featureFactoryConfig)

    /**
     * Adds a statement, then searches for it by subject and predicate.
     *
     * @param subj    the subject.
     * @param pred    the predicate.
     * @param obj     the object.
     * @param context the context.
     */
    private def addAndFindBySubjAndPred(subj: RdfResource, pred: IriNode, obj: RdfNode, context: Option[IRI] = None): Unit = {
        val statement: Statement = nodeFactory.makeStatement(subj = subj, pred = pred, obj = obj)
        model.addStatement(statement)
        assert(model.find(subj = Some(subj), pred = Some(pred), obj = None) == Set(statement))
    }

    "An RdfModel" should {
        "add a triple with a datatype literal object, without first creating a Statement" in {
            val subj: IriNode = nodeFactory.makeIriNode("http://example.org/1")
            val pred: IriNode = nodeFactory.makeIriNode("http://example.org/datatype_prop")
            val obj: DatatypeLiteral = nodeFactory.makeDatatypeLiteral(value = "5", datatype = OntologyConstants.Xsd.Integer)

            model.add(subj = subj, pred = pred, obj = obj)
            val expectedStatement: Statement = nodeFactory.makeStatement(subj = subj, pred = pred, obj = obj)
            assert(model.find(subj = Some(subj), pred = Some(pred), obj = None) == Set(expectedStatement))
        }

        "add a triple with a datatype literal object" in {
            addAndFindBySubjAndPred(
                subj = nodeFactory.makeIriNode("http://example.org/2"),
                pred = nodeFactory.makeIriNode("http://example.org/datatype_prop"),
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
                pred = nodeFactory.makeIriNode("http://example.org/datatype_prop"),
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
    }
}
