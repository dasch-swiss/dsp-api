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

import java.io.{BufferedInputStream, ByteArrayInputStream, ByteArrayOutputStream, File, FileInputStream}

import org.knora.webapi.{CoreSpec, IRI}
import org.knora.webapi.feature.{FeatureFactoryConfig, FeatureToggle, KnoraSettingsFeatureFactoryConfig, TestFeatureFactoryConfig}
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}
import org.knora.webapi.util.FileUtil

/**
 * Tests implementations of [[RdfFormatUtil]].
 */
abstract class RdfFormatUtilSpec(featureToggle: FeatureToggle) extends CoreSpec {
    StringFormatter.initForTest()

    private val featureFactoryConfig: FeatureFactoryConfig = new TestFeatureFactoryConfig(
        testToggles = Set(featureToggle),
        parent = new KnoraSettingsFeatureFactoryConfig(settings)
    )

    private val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(featureFactoryConfig)
    private val rdfNodeFactory: RdfNodeFactory = RdfFeatureFactory.getRdfNodeFactory(featureFactoryConfig)
    private val rdfModelFactory: RdfModelFactory = RdfFeatureFactory.getRdfModelFactory(featureFactoryConfig)

    private val expectedThingLabelStatement = rdfNodeFactory.makeStatement(
        rdfNodeFactory.makeIriNode("http://www.knora.org/ontology/0001/anything#Thing"),
        rdfNodeFactory.makeIriNode(OntologyConstants.Rdfs.Label),
        rdfNodeFactory.makeStringWithLanguage(value = "Thing", language = "en")
    )

    /**
     * Processes `anything-onto.ttl` and checks whether the expected content is received.
     */
    class TestStreamProcessor extends RdfStreamProcessor {
        var startCalled: Boolean = false
        var finishCalled: Boolean = false
        var gotKnoraBaseNamespace = false
        var gotThingLabelStatement: Boolean = false

        override def start(): Unit = {
            startCalled = true
        }

        override def processNamespace(prefix: String, namespace: IRI): Unit = {
            if (prefix == "knora-base" && namespace == "http://www.knora.org/ontology/knora-base#") {
                gotKnoraBaseNamespace = true
            }
        }

        override def processStatement(statement: Statement): Unit = {
            if (statement == expectedThingLabelStatement) {
                gotThingLabelStatement = true
            }
        }

        override def finish(): Unit = {
            finishCalled = true
        }

        def check(): Unit = {
            assert(startCalled)
            assert(gotKnoraBaseNamespace)
            assert(gotThingLabelStatement)
            assert(finishCalled)
        }
    }

    private def checkModelForRdfTypeBook(rdfModel: RdfModel): Unit = {
        val statements: Set[Statement] = rdfModel.find(
            subj = Some(rdfNodeFactory.makeIriNode("http://rdfh.ch/0803/2a6221216701")),
            pred = Some(rdfNodeFactory.makeIriNode(OntologyConstants.Rdf.Type)),
            obj = None
        )

        assert(statements.size == 1)
        assert(statements.head.obj == rdfNodeFactory.makeIriNode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book"))
    }

    private def checkJsonLDDocumentForRdfTypeBook(jsonLDDocument: JsonLDDocument): Unit = {
        assert(jsonLDDocument.requireString(JsonLDKeywords.TYPE) == "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book")
    }

    "RdfFormatUtil" should {
        "parse RDF in Turtle format, producing an RdfModel, then format it as Turtle again" in {
            val inputTurtle: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"))
            val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = inputTurtle, rdfFormat = Turtle)
            checkModelForRdfTypeBook(inputModel)

            val outputTurtle: String = rdfFormatUtil.format(rdfModel = inputModel, rdfFormat = Turtle)
            val outputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = outputTurtle, rdfFormat = Turtle)
            checkModelForRdfTypeBook(outputModel)
            assert(outputModel == inputModel)
        }

        "parse RDF in JSON-LD format, producing an RdfModel, then format it as JSON-LD again" in {
            val inputTurtle: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"))
            val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = inputTurtle, rdfFormat = JsonLD)
            checkModelForRdfTypeBook(inputModel)

            val outputTurtle: String = rdfFormatUtil.format(rdfModel = inputModel, rdfFormat = JsonLD)
            val outputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = outputTurtle, rdfFormat = JsonLD)
            checkModelForRdfTypeBook(outputModel)
            assert(outputModel == inputModel)
        }

        "parse RDF in Turtle format, producing a JsonLDDocument, then format it as Turtle again" in {
            val inputTurtle: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"))
            val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = inputTurtle, rdfFormat = Turtle)
            val inputJsonLDDocument: JsonLDDocument = rdfFormatUtil.parseToJsonLDDocument(rdfStr = inputTurtle, rdfFormat = Turtle)
            checkJsonLDDocumentForRdfTypeBook(inputJsonLDDocument)

            val jsonLDOutputModel: RdfModel = inputJsonLDDocument.toRdfModel(rdfModelFactory)
            checkModelForRdfTypeBook(jsonLDOutputModel)
            assert(jsonLDOutputModel == inputModel)

            val outputTurtle: String = rdfFormatUtil.format(rdfModel = jsonLDOutputModel, rdfFormat = Turtle)
            val turtleOutputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = outputTurtle, rdfFormat = Turtle)
            checkModelForRdfTypeBook(turtleOutputModel)
            assert(turtleOutputModel == inputModel)
        }

        "parse RDF in RDF/XML format, producing a JsonLDDocument, then format it as RDF/XML again" in {
            val inputRdfXml: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.rdf"))
            val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = inputRdfXml, rdfFormat = RdfXml)
            val inputJsonLDDocument: JsonLDDocument = rdfFormatUtil.parseToJsonLDDocument(rdfStr = inputRdfXml, rdfFormat = RdfXml)
            checkJsonLDDocumentForRdfTypeBook(inputJsonLDDocument)

            val jsonLDOutputModel: RdfModel = inputJsonLDDocument.toRdfModel(rdfModelFactory)
            checkModelForRdfTypeBook(jsonLDOutputModel)
            assert(jsonLDOutputModel == inputModel)

            val outputRdfXml: String = rdfFormatUtil.format(rdfModel = jsonLDOutputModel, rdfFormat = RdfXml)
            val rdfXmlOutputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = outputRdfXml, rdfFormat = RdfXml)
            checkModelForRdfTypeBook(rdfXmlOutputModel)
            assert(rdfXmlOutputModel == inputModel)
        }

        "parse RDF in JSON-LD format, producing a JsonLDDocument, then format it as JSON-LD again" in {
            val inputTurtle: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"))
            val inputJsonLDDocument: JsonLDDocument = rdfFormatUtil.parseToJsonLDDocument(rdfStr = inputTurtle, rdfFormat = JsonLD)
            checkJsonLDDocumentForRdfTypeBook(inputJsonLDDocument)

            val outputJsonLD: String = inputJsonLDDocument.toPrettyString()
            val outputJsonLDDocument: JsonLDDocument = rdfFormatUtil.parseToJsonLDDocument(rdfStr = outputJsonLD, rdfFormat = JsonLD)
            checkJsonLDDocumentForRdfTypeBook(outputJsonLDDocument)
            assert(inputJsonLDDocument == outputJsonLDDocument)
        }

        "use prefixes and custom datatypes" in {
            val inputJsonLD: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"))
            val inputJsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(inputJsonLD)
            val outputModel: RdfModel = inputJsonLDDocument.toRdfModel(rdfModelFactory)

            // Add namespaces, which were removed by compacting the JSON-LD document when parsing it.

            val namespaces: Map[String, IRI] = Map(
                "incunabula" -> "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#",
                "knora-api" -> "http://api.knora.org/ontology/knora-api/simple/v2#",
                "rdf" -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                "rdfs" -> "http://www.w3.org/2000/01/rdf-schema#",
                "xsd" -> "http://www.w3.org/2001/XMLSchema#"
            )

            for ((prefix, namespace) <- namespaces) {
                outputModel.setNamespace(prefix, namespace)
            }

            val outputTurtle: String = rdfFormatUtil.format(rdfModel = outputModel, rdfFormat = Turtle)
            assert(outputTurtle.contains("\"JULIAN:1481 CE\"^^knora-api:Date"))
        }

        "parse RDF from a stream and process it using an RdfStreamProcessor" in {
            val inputStream = new BufferedInputStream(new FileInputStream("test_data/ontologies/anything-onto.ttl"))
            val testStreamProcessor = new TestStreamProcessor

            rdfFormatUtil.parseToStream(
                rdfSource = RdfInputStreamSource(inputStream),
                rdfFormat = Turtle,
                rdfStreamProcessor = testStreamProcessor
            )

            inputStream.close()
            testStreamProcessor.check()
        }

        "process streamed RDF and write the formatted result to an output stream" in {
            // Read the file, process it with an RdfStreamProcessor, and write the result
            // to a ByteArrayOutputStream.

            val fileInputStream = new BufferedInputStream(new FileInputStream("test_data/ontologies/anything-onto.ttl"))
            val byteArrayOutputStream = new ByteArrayOutputStream()

            val formattingStreamProcessor = rdfFormatUtil.makeFormattingStreamProcessor(
                outputStream = byteArrayOutputStream,
                rdfFormat = Turtle
            )

            rdfFormatUtil.parseToStream(
                rdfSource = RdfInputStreamSource(fileInputStream),
                rdfFormat = Turtle,
                rdfStreamProcessor = formattingStreamProcessor
            )

            fileInputStream.close()

            // Read back the ByteArrayOutputStream and check that it's correct.

            val byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray)
            val testStreamProcessor = new TestStreamProcessor

            rdfFormatUtil.parseToStream(
                rdfSource = RdfInputStreamSource(byteArrayInputStream),
                rdfFormat = Turtle,
                rdfStreamProcessor = testStreamProcessor
            )

            byteArrayInputStream.close()
            testStreamProcessor.check()
        }

        "stream RDF data into an RdfModel" in {
            val fileInputStream = new BufferedInputStream(new FileInputStream("test_data/ontologies/anything-onto.ttl"))
            val rdfModel: RdfModel = rdfFormatUtil.streamToRdfModel(inputStream = fileInputStream, rdfFormat = Turtle)
            fileInputStream.close()
            assert(rdfModel.contains(expectedThingLabelStatement))
        }
    }
}
