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

package org.knora.webapi.util.rdf

import java.io.File

import org.knora.webapi.CoreSpec
import org.knora.webapi.feature.{FeatureFactoryConfig, FeatureToggle, KnoraSettingsFeatureFactoryConfig, TestFeatureFactoryConfig}
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}
import org.knora.webapi.util.FileUtil

/**
 * Tests implementations of [[RdfFormatTool]].
 */
abstract class RdfFormatToolSpec(featureToggle: FeatureToggle) extends CoreSpec {
    private val featureFactoryConfig: FeatureFactoryConfig = new TestFeatureFactoryConfig(
        testToggles = Set(featureToggle),
        parent = new KnoraSettingsFeatureFactoryConfig(settings)
    )

    private val rdfFormatTool: RdfFormatTool = RdfToolFactory.makeRdfFormatTool(featureFactoryConfig)
    private val nodeFactory: RdfNodeFactory = RdfToolFactory.makeRdfNodeFactory(featureFactoryConfig)

    StringFormatter.initForTest()

    private def checkModelForRdfTypeBook(rdfModel: RdfModel): Unit = {
        val statements: Set[Statement] = rdfModel.find(
            subj = Some(nodeFactory.makeIriNode("http://rdfh.ch/0803/2a6221216701")),
            pred = Some(nodeFactory.makeIriNode(OntologyConstants.Rdf.Type)),
            obj = None
        )

        assert(statements.size == 1)
        assert(statements.head.obj == nodeFactory.makeIriNode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book"))
    }

    private def checkJsonLDDocumentForRdfTypeBook(jsonLDDocument: JsonLDDocument): Unit = {
        assert(jsonLDDocument.requireString(JsonLDConstants.TYPE) == "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book")
    }

    "RdfFormatUtil" should {
        "parse RDF in Turtle format, producing an RdfModel" in {
            val inputTurtle: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"))
            val rdfModel: RdfModel = rdfFormatTool.parseToRdfModel(rdfStr = inputTurtle, rdfFormat = Turtle)
            checkModelForRdfTypeBook(rdfModel)
        }

        "parse RDF in JSON-LD format, producing an RdfModel" in {
            val inputTurtle: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"))
            val rdfModel: RdfModel = rdfFormatTool.parseToRdfModel(rdfStr = inputTurtle, rdfFormat = JsonLD)
            checkModelForRdfTypeBook(rdfModel)
        }

        "parse RDF in Turtle format, producing a JsonLDDocument" in {
            val inputTurtle: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"))
            val jsonLDDocument: JsonLDDocument = rdfFormatTool.parseToJsonLDDocument(rdfStr = inputTurtle, rdfFormat = Turtle)
            checkJsonLDDocumentForRdfTypeBook(jsonLDDocument)
        }

        "parse RDF in JSON-LD format, producing a JsonLDDocument" in {
            val inputTurtle: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"))
            val jsonLDDocument: JsonLDDocument = rdfFormatTool.parseToJsonLDDocument(rdfStr = inputTurtle, rdfFormat = JsonLD)
            checkJsonLDDocumentForRdfTypeBook(jsonLDDocument)
        }
    }
}
