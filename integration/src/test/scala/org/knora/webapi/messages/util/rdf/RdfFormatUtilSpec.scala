/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.rdf

import org.apache.jena

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.util.Try

import dsp.errors.BadRequestException
import org.knora.webapi.CoreSpec
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaModel
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaModelFactory
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaNodeFactory
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaStatement
import org.knora.webapi.util.FileUtil

/**
 * Tests implementations of [[RdfFormatUtil]].
 */
class RdfFormatUtilSpec() extends CoreSpec {

  private val rdfFormatUtil: RdfFormatUtil      = RdfFeatureFactory.getRdfFormatUtil()
  private val rdfNodeFactory: JenaNodeFactory   = RdfFeatureFactory.getRdfNodeFactory()
  private val rdfModelFactory: JenaModelFactory = RdfFeatureFactory.getRdfModelFactory()

  private def checkModelForRdfTypeBook(rdfModel: RdfModel, context: Option[IRI] = None): Unit = {
    val statements: Set[Statement] = rdfModel
      .find(
        subj = Some(rdfNodeFactory.makeIriNode("http://rdfh.ch/0803/2a6221216701")),
        pred = Some(rdfNodeFactory.makeIriNode(OntologyConstants.Rdf.Type)),
        obj = None,
        context = context
      )
      .toSet

    assert(statements.size == 1)
    assert(statements.head.obj == rdfNodeFactory.makeIriNode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book"))
  }

  private def checkJsonLDDocumentForRdfTypeBook(jsonLDDocument: JsonLDDocument): Unit =
    assert(
      jsonLDDocument.body
        .getRequiredString(JsonLDKeywords.TYPE)
        .fold(msg => throw BadRequestException(msg), identity) == "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book"
    )

  "RdfFormatUtil" should {
    "parse RDF in Turtle format, producing an RdfModel, then format it as Turtle again" in {
      val inputTurtle: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl")
        )
      val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = inputTurtle, rdfFormat = Turtle)
      checkModelForRdfTypeBook(inputModel)

      val outputTurtle: String  = rdfFormatUtil.format(rdfModel = inputModel, rdfFormat = Turtle)
      val outputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = outputTurtle, rdfFormat = Turtle)
      checkModelForRdfTypeBook(outputModel)
      assert(outputModel == inputModel)
    }

    "parse RDF in JSON-LD format, producing an RdfModel, then format it as JSON-LD again" in {
      val inputTurtle: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld")
        )
      val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = inputTurtle, rdfFormat = JsonLD)
      checkModelForRdfTypeBook(inputModel)

      val outputTurtle: String  = rdfFormatUtil.format(rdfModel = inputModel, rdfFormat = JsonLD)
      val outputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = outputTurtle, rdfFormat = JsonLD)
      checkModelForRdfTypeBook(outputModel)
      assert(outputModel == inputModel)
    }

    "parse RDF in Turtle format, producing a JsonLDDocument, then format it as Turtle again" in {
      val inputTurtle: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl")
        )
      val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = inputTurtle, rdfFormat = Turtle)
      val inputJsonLDDocument: JsonLDDocument =
        RdfFormatUtilSpec.parseToJsonLDDocument(
          rdfStr = inputTurtle,
          rdfFormat = Turtle,
          modelFactory = rdfModelFactory
        )
      checkJsonLDDocumentForRdfTypeBook(inputJsonLDDocument)

      val jsonLDOutputModel: RdfModel = inputJsonLDDocument.toRdfModel(rdfModelFactory)
      checkModelForRdfTypeBook(jsonLDOutputModel)
      assert(jsonLDOutputModel == inputModel)

      val outputTurtle: String        = rdfFormatUtil.format(rdfModel = jsonLDOutputModel, rdfFormat = Turtle)
      val turtleOutputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = outputTurtle, rdfFormat = Turtle)
      checkModelForRdfTypeBook(turtleOutputModel)
      assert(turtleOutputModel == inputModel)
    }

    "parse RDF in RDF/XML format, producing a JsonLDDocument, then format it as RDF/XML again" in {
      val inputRdfXml: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.rdf")
        )
      val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = inputRdfXml, rdfFormat = RdfXml)
      val inputJsonLDDocument: JsonLDDocument =
        RdfFormatUtilSpec.parseToJsonLDDocument(
          rdfStr = inputRdfXml,
          rdfFormat = RdfXml,
          modelFactory = rdfModelFactory
        )
      checkJsonLDDocumentForRdfTypeBook(inputJsonLDDocument)

      val jsonLDOutputModel: RdfModel = inputJsonLDDocument.toRdfModel(rdfModelFactory)
      checkModelForRdfTypeBook(jsonLDOutputModel)
      assert(jsonLDOutputModel == inputModel)

      val outputRdfXml: String        = rdfFormatUtil.format(rdfModel = jsonLDOutputModel, rdfFormat = RdfXml)
      val rdfXmlOutputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = outputRdfXml, rdfFormat = RdfXml)
      checkModelForRdfTypeBook(rdfXmlOutputModel)
      assert(rdfXmlOutputModel == inputModel)
    }

    "parse RDF in TriG format" in {
      val graphIri = "http://example.org/data#"
      val inputTrig = FileUtil.readTextFile(
        Paths.get("..", "test_data/generated_test_data/rdfFormatUtil/BookReiseInsHeiligeLand.trig")
      )
      val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = inputTrig, rdfFormat = TriG)
      checkModelForRdfTypeBook(rdfModel = inputModel, context = Some(graphIri))
    }

    "parse RDF in N-Quads format" in {
      val graphIri = "http://example.org/data#"
      val inputTrig =
        FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/rdfFormatUtil/BookReiseInsHeiligeLand.nq"))
      val inputModel: RdfModel = rdfFormatUtil.parseToRdfModel(rdfStr = inputTrig, rdfFormat = NQuads)
      checkModelForRdfTypeBook(rdfModel = inputModel, context = Some(graphIri))
    }

    "read Turtle, add a graph IRI to it, write it to a TriG file, and read back the TriG file" in {
      val graphIri = "http://example.org/data#"
      val rdfSource = RdfInputStreamSource(
        new BufferedInputStream(
          Files.newInputStream(
            Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl")
          )
        )
      )
      val outputFile: Path = Files.createTempFile("test", ".trig")

      rdfFormatUtil.turtleToQuadsFile(
        rdfSource = rdfSource,
        graphIri = graphIri,
        outputFile = outputFile,
        outputFormat = TriG
      )

      val quadsModel: RdfModel = rdfFormatUtil.fileToRdfModel(file = outputFile, rdfFormat = TriG)
      checkModelForRdfTypeBook(rdfModel = quadsModel, context = Some(graphIri))
    }

    "read Turtle, add a graph IRI to it, write it to an N-Quads file, and read back the N-Quads file" in {
      val graphIri = "http://example.org/data#"
      val rdfSource = RdfInputStreamSource(
        new BufferedInputStream(
          Files.newInputStream(
            Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl")
          )
        )
      )
      val outputFile: Path = Files.createTempFile("test", ".trig")

      rdfFormatUtil.turtleToQuadsFile(
        rdfSource = rdfSource,
        graphIri = graphIri,
        outputFile = outputFile,
        outputFormat = NQuads
      )

      val quadsModel: RdfModel = rdfFormatUtil.fileToRdfModel(file = outputFile, rdfFormat = NQuads)
      checkModelForRdfTypeBook(rdfModel = quadsModel, context = Some(graphIri))
    }

    "parse RDF in JSON-LD format, producing a JsonLDDocument, then format it as JSON-LD again" in {
      val inputTurtle: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld")
        )
      val inputJsonLDDocument: JsonLDDocument =
        RdfFormatUtilSpec.parseToJsonLDDocument(
          rdfStr = inputTurtle,
          rdfFormat = JsonLD,
          modelFactory = rdfModelFactory
        )
      checkJsonLDDocumentForRdfTypeBook(inputJsonLDDocument)

      val outputJsonLD: String = inputJsonLDDocument.toPrettyString()
      val outputJsonLDDocument: JsonLDDocument =
        RdfFormatUtilSpec.parseToJsonLDDocument(
          rdfStr = outputJsonLD,
          rdfFormat = JsonLD,
          modelFactory = rdfModelFactory
        )
      checkJsonLDDocumentForRdfTypeBook(outputJsonLDDocument)
      assert(inputJsonLDDocument == outputJsonLDDocument)
    }

    "use prefixes and custom datatypes" in {
      val inputJsonLD: String =
        FileUtil.readTextFile(
          Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld")
        )
      val inputJsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(inputJsonLD)
      val outputModel: RdfModel               = inputJsonLDDocument.toRdfModel(rdfModelFactory)

      // Add namespaces, which were removed by compacting the JSON-LD document when parsing it.

      val namespaces: Map[String, IRI] = Map(
        "incunabula" -> "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#",
        "knora-api"  -> "http://api.knora.org/ontology/knora-api/simple/v2#",
        "rdf"        -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
        "rdfs"       -> "http://www.w3.org/2000/01/rdf-schema#",
        "xsd"        -> "http://www.w3.org/2001/XMLSchema#"
      )

      for ((prefix, namespace) <- namespaces) {
        outputModel.setNamespace(prefix, namespace)
      }

      val outputTurtle: String = rdfFormatUtil.format(rdfModel = outputModel, rdfFormat = Turtle)
      assert(outputTurtle.contains("\"JULIAN:1481 CE\"^^knora-api:Date"))
    }

    "parse RDF from a stream and process it using an RdfStreamProcessor" in {
      val inputStream =
        new BufferedInputStream(Files.newInputStream(Paths.get("..", "test_data/project_ontologies/anything-onto.ttl")))
      RdfFormatUtilSpec.testStream(inputStream)
    }

    "process streamed RDF and write the formatted result to an output stream" in {
      // Read the file, process it with an RdfStreamProcessor, and write the result
      // to a ByteArrayOutputStream.

      val fileInputStream =
        new BufferedInputStream(Files.newInputStream(Paths.get("..", "test_data/project_ontologies/anything-onto.ttl")))
      val byteArrayOutputStream = new ByteArrayOutputStream()

      rdfFormatUtil.parseStreamWithFormatting(fileInputStream, byteArrayOutputStream)

      // Read back the ByteArrayOutputStream and check that it's correct.
      val byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray)
      RdfFormatUtilSpec.testStream(byteArrayInputStream)
    }

    "stream RDF data from an InputStream into an RdfModel, then into an OutputStream, then back into an RdfModel" in {
      val fileInputStream =
        new BufferedInputStream(Files.newInputStream(Paths.get("..", "test_data/project_ontologies/anything-onto.ttl")))
      val rdfModel: RdfModel = rdfFormatUtil.inputStreamToRdfModel(inputStream = fileInputStream, rdfFormat = Turtle)
      fileInputStream.close()
      assert(rdfModel.contains(RdfFormatUtilSpec.expectedThingLabelStatement))

      val byteArrayOutputStream = new ByteArrayOutputStream()
      rdfFormatUtil.rdfModelToOutputStream(
        rdfModel = rdfModel,
        outputStream = byteArrayOutputStream,
        rdfFormat = Turtle
      )
      byteArrayOutputStream.close()

      val byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray)
      val copyOfRdfModel: RdfModel =
        rdfFormatUtil.inputStreamToRdfModel(inputStream = byteArrayInputStream, rdfFormat = Turtle)
      byteArrayInputStream.close()

      assert(copyOfRdfModel == rdfModel)
    }
  }
}

object RdfFormatUtilSpec {

  private val rdfFormatUtil: RdfFormatUtil      = RdfFeatureFactory.getRdfFormatUtil()
  private val rdfNodeFactory: RdfNodeFactory    = RdfFeatureFactory.getRdfNodeFactory()
  private val rdfModelFactory: JenaModelFactory = RdfFeatureFactory.getRdfModelFactory()

  private val expectedThingLabelStatement = rdfNodeFactory.makeStatement(
    rdfNodeFactory.makeIriNode("http://www.knora.org/ontology/0001/anything#Thing"),
    rdfNodeFactory.makeIriNode(OntologyConstants.Rdfs.Label),
    rdfNodeFactory.makeStringWithLanguage(value = "Thing", language = "en")
  )

  /**
   * Processes `anything-onto.ttl` and checks whether the expected content is received.
   */
  private case class TestStreamProcessor() extends jena.riot.system.StreamRDF {
    var startCalled: Boolean            = false
    var finishCalled: Boolean           = false
    var gotKnoraBaseNamespace           = false
    var gotThingLabelStatement: Boolean = false

    private def checkPrefix(prefix: String, namespace: IRI): Boolean =
      prefix == "knora-base" && namespace == "http://www.knora.org/ontology/knora-base#"
    private def processStatement(statement: Statement): Unit =
      if (statement == expectedThingLabelStatement) gotThingLabelStatement = true

    override def start(): Unit            = startCalled = true
    override def finish(): Unit           = finishCalled = true
    override def base(base: String): Unit = ()
    override def prefix(prefix: String, namespace: IRI): Unit =
      if (checkPrefix(prefix, namespace)) gotKnoraBaseNamespace = true
    override def quad(quad: jena.sparql.core.Quad): Unit = processStatement(JenaStatement(quad))
    override def triple(triple: jena.graph.Triple): Unit =
      processStatement(JenaStatement(jena.sparql.core.Quad.create(jena.sparql.core.Quad.defaultGraphIRI, triple)))

    def check(): Unit = {
      assert(startCalled)
      assert(gotKnoraBaseNamespace)
      assert(gotThingLabelStatement)
      assert(finishCalled)
    }
  }

  def testStream(inputStream: InputStream): Unit = {
    val testStreamProcessor = TestStreamProcessor()
    val parser              = jena.riot.RDFParser.create()
    parser.source(inputStream)
    val parseTry: Try[Unit] = Try {
      parser
        .lang(jena.riot.RDFLanguages.TURTLE)
        .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
        .parse(testStreamProcessor)
    }
    inputStream.close()
    testStreamProcessor.check()
    parseTry.get
  }

  def parseToJsonLDDocument(
    rdfStr: String,
    rdfFormat: RdfFormat,
    flatJsonLD: Boolean = false,
    modelFactory: JenaModelFactory
  ): JsonLDDocument =
    rdfFormat match {
      case JsonLD =>
        // Use JsonLDUtil to parse JSON-LD.
        JsonLDUtil.parseJsonLD(jsonLDString = rdfStr, flatten = flatJsonLD)

      case nonJsonLD: NonJsonLD =>
        val model: JenaModel = modelFactory.makeEmptyModel
        jena.riot.RDFParser
          .create()
          .source(new StringReader(rdfStr))
          .lang(RdfFormatUtil.rdfFormatToJenaParsingLang(nonJsonLD))
          .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
          .parse(model.getDataset)
        JsonLDUtil.fromRdfModel(model, flatJsonLD)
    }

}
