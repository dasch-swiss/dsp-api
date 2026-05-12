/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.rdf

import org.apache.jena
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import dsp.errors.BadRequestException
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.util.FileUtil

/**
 * Tests implementations of [[RdfFormatUtil]].
 */
object RdfFormatUtilSpec extends ZIOSpecDefault {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private def checkModelForRdfTypeBook(rdfModel: RdfModel, context: Option[IRI] = None): Boolean = {
    val statements: Set[Statement] = rdfModel
      .find(
        subj = Some(JenaNodeFactory.makeIriNode("http://rdfh.ch/0803/2a6221216701")),
        pred = Some(JenaNodeFactory.makeIriNode(OntologyConstants.Rdf.Type)),
        obj = None,
        context = context,
      )
      .toSet

    statements.size == 1 &&
    statements.head.obj == JenaNodeFactory.makeIriNode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book")
  }

  private def checkJsonLDDocumentForRdfTypeBook(jsonLDDocument: JsonLDDocument): Boolean =
    jsonLDDocument.body
      .getRequiredString(JsonLDKeywords.TYPE)
      .fold(msg => throw BadRequestException(msg), identity) == "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book"

  private def parseToJsonLDDocument(
    rdfStr: String,
    rdfFormat: RdfFormat,
  ): JsonLDDocument =
    rdfFormat match {
      case JsonLD =>
        JsonLDUtil.parseJsonLD(rdfStr)

      case nonJsonLD: NonJsonLD =>
        val model: JenaModel = JenaModelFactory.makeEmptyModel
        jena.riot.RDFParser
          .create()
          .source(new StringReader(rdfStr))
          .lang(RdfFormatUtil.rdfFormatToJenaParsingLang(nonJsonLD))
          .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
          .parse(model.getDataset)
        JsonLDUtil.fromRdfModel(model)
    }

  private val expectedThingLabelStatement = JenaNodeFactory.makeStatement(
    JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/0001/anything#Thing"),
    JenaNodeFactory.makeIriNode(OntologyConstants.Rdfs.Label),
    JenaNodeFactory.makeStringWithLanguage(value = "Thing", language = "en"),
  )

  val spec: Spec[Any, Nothing] = suite("RdfFormatUtil")(
    test("parse RDF in Turtle format, producing an RdfModel, then format it as Turtle again") {
      val inputTurtle: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"),
        )
      val inputModel = RdfModel.fromTurtle(inputTurtle)
      val ok1        = checkModelForRdfTypeBook(inputModel)

      val outputTurtle: String = RdfFormatUtil.format(rdfModel = inputModel, rdfFormat = Turtle)
      val outputModel          = RdfModel.fromTurtle(outputTurtle)
      val ok2                  = checkModelForRdfTypeBook(outputModel)
      assertTrue(ok1, ok2, outputModel == inputModel)
    },
    test("parse RDF in JSON-LD format, producing an RdfModel, then format it as JSON-LD again") {
      val input: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"),
        )
      val inputModel = RdfModel.fromJsonLD(input)
      val ok1        = checkModelForRdfTypeBook(inputModel)

      val output: String = RdfFormatUtil.format(rdfModel = inputModel, rdfFormat = JsonLD)
      val outputModel    = RdfModel.fromJsonLD(output)
      val ok2            = checkModelForRdfTypeBook(outputModel)
      assertTrue(ok1, ok2, outputModel == inputModel)
    },
    test("parse RDF in Turtle format, producing a JsonLDDocument, then format it as Turtle again") {
      val inputTurtle: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"),
        )
      val inputModel                          = RdfModel.fromTurtle(inputTurtle)
      val inputJsonLDDocument: JsonLDDocument =
        parseToJsonLDDocument(inputTurtle, Turtle)
      val ok1 = checkJsonLDDocumentForRdfTypeBook(inputJsonLDDocument)

      val jsonLDOutputModel: RdfModel = inputJsonLDDocument.toRdfModel
      val ok2                         = checkModelForRdfTypeBook(jsonLDOutputModel)

      val outputTurtle: String = RdfFormatUtil.format(rdfModel = jsonLDOutputModel, rdfFormat = Turtle)
      val turtleOutputModel    = RdfModel.fromTurtle(outputTurtle)
      val ok3                  = checkModelForRdfTypeBook(turtleOutputModel)
      assertTrue(ok1, ok2, ok3, jsonLDOutputModel == inputModel, turtleOutputModel == inputModel)
    },
    test("parse RDF in RDF/XML format, producing a JsonLDDocument, then format it as RDF/XML again") {
      val inputRdfXml: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.rdf"),
        )
      val inputModel                          = RdfModel.fromRdfXml(inputRdfXml)
      val inputJsonLDDocument: JsonLDDocument =
        parseToJsonLDDocument(inputRdfXml, RdfXml)
      val ok1 = checkJsonLDDocumentForRdfTypeBook(inputJsonLDDocument)

      val jsonLDOutputModel: RdfModel = inputJsonLDDocument.toRdfModel
      val ok2                         = checkModelForRdfTypeBook(jsonLDOutputModel)

      val outputRdfXml: String = RdfFormatUtil.format(rdfModel = jsonLDOutputModel, rdfFormat = RdfXml)
      val rdfXmlOutputModel    = RdfModel.fromRdfXml(outputRdfXml)
      val ok3                  = checkModelForRdfTypeBook(rdfXmlOutputModel)
      assertTrue(ok1, ok2, ok3, jsonLDOutputModel == inputModel, rdfXmlOutputModel == inputModel)
    },
    test("parse RDF in TriG format") {
      val graphIri  = "http://example.org/data#"
      val inputTrig = FileUtil.readTextFile(
        Paths.get("test_data/generated_test_data/rdfFormatUtil/BookReiseInsHeiligeLand.trig"),
      )
      val inputModel = RdfModel.fromTriG(inputTrig)
      assertTrue(checkModelForRdfTypeBook(rdfModel = inputModel, context = Some(graphIri)))
    },
    test("parse RDF in N-Quads format") {
      val graphIri  = "http://example.org/data#"
      val inputTrig =
        FileUtil.readTextFile(Paths.get("test_data/generated_test_data/rdfFormatUtil/BookReiseInsHeiligeLand.nq"))
      val inputModel = RdfModel.fromNQuads(inputTrig)
      assertTrue(checkModelForRdfTypeBook(rdfModel = inputModel, context = Some(graphIri)))
    },
    test("read Turtle, add a graph IRI to it, write it to a TriG file, and read back the TriG file") {
      val graphIri  = "http://example.org/data#"
      val rdfSource = RdfInputStreamSource(
        new BufferedInputStream(
          Files.newInputStream(
            Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"),
          ),
        ),
      )
      val outputFile: Path = Files.createTempFile("test", ".trig")

      RdfFormatUtil.turtleToQuadsFile(
        rdfSource = rdfSource,
        graphIri = graphIri,
        outputFile = outputFile,
        outputFormat = TriG,
      )

      val quadsModel: RdfModel = RdfFormatUtil.fileToRdfModel(file = outputFile, rdfFormat = TriG)
      assertTrue(checkModelForRdfTypeBook(rdfModel = quadsModel, context = Some(graphIri)))
    },
    test("read Turtle, add a graph IRI to it, write it to an N-Quads file, and read back the N-Quads file") {
      val graphIri  = "http://example.org/data#"
      val rdfSource = RdfInputStreamSource(
        new BufferedInputStream(
          Files.newInputStream(
            Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"),
          ),
        ),
      )
      val outputFile: Path = Files.createTempFile("test", ".trig")

      RdfFormatUtil.turtleToQuadsFile(
        rdfSource = rdfSource,
        graphIri = graphIri,
        outputFile = outputFile,
        outputFormat = NQuads,
      )

      val quadsModel: RdfModel = RdfFormatUtil.fileToRdfModel(file = outputFile, rdfFormat = NQuads)
      assertTrue(checkModelForRdfTypeBook(rdfModel = quadsModel, context = Some(graphIri)))
    },
    test("parse RDF in JSON-LD format, producing a JsonLDDocument, then format it as JSON-LD again") {
      val inputTurtle: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"),
        )
      val inputJsonLDDocument: JsonLDDocument =
        parseToJsonLDDocument(inputTurtle, JsonLD)
      val ok1 = checkJsonLDDocumentForRdfTypeBook(inputJsonLDDocument)

      val outputJsonLD: String                 = inputJsonLDDocument.toPrettyString()
      val outputJsonLDDocument: JsonLDDocument =
        parseToJsonLDDocument(outputJsonLD, JsonLD)
      val ok2 = checkJsonLDDocumentForRdfTypeBook(outputJsonLDDocument)
      assertTrue(ok1, ok2, inputJsonLDDocument == outputJsonLDDocument)
    },
    test("use prefixes and custom datatypes") {
      val inputJsonLD: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"),
        )
      val inputJsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(inputJsonLD)
      val outputModel: RdfModel               = inputJsonLDDocument.toRdfModel

      // Add namespaces, which were removed by compacting the JSON-LD document when parsing it.

      val namespaces: Map[String, IRI] = Map(
        "incunabula" -> "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#",
        "knora-api"  -> "http://api.knora.org/ontology/knora-api/simple/v2#",
        "rdf"        -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
        "rdfs"       -> "http://www.w3.org/2000/01/rdf-schema#",
        "xsd"        -> "http://www.w3.org/2001/XMLSchema#",
      )

      for ((prefix, namespace) <- namespaces) {
        outputModel.setNamespace(prefix, namespace)
      }

      val outputTurtle: String = RdfFormatUtil.format(rdfModel = outputModel, rdfFormat = Turtle)
      assertTrue(outputTurtle.contains("\"JULIAN:1481 CE\"^^knora-api:Date"))
    },
    test(
      "stream RDF data from an InputStream into an RdfModel, then into an OutputStream, then back into an RdfModel",
    ) {
      val fileInputStream =
        new BufferedInputStream(Files.newInputStream(Paths.get("test_data/project_ontologies/anything-onto.ttl")))
      val rdfModel: RdfModel = RdfFormatUtil.inputStreamToRdfModel(inputStream = fileInputStream, rdfFormat = Turtle)
      fileInputStream.close()
      val ok1 = rdfModel.contains(expectedThingLabelStatement)

      val byteArrayOutputStream = new ByteArrayOutputStream()
      RdfFormatUtil.rdfModelToOutputStream(
        rdfModel = rdfModel,
        outputStream = byteArrayOutputStream,
        rdfFormat = Turtle,
      )
      byteArrayOutputStream.close()

      val byteArrayInputStream     = new ByteArrayInputStream(byteArrayOutputStream.toByteArray)
      val copyOfRdfModel: RdfModel =
        RdfFormatUtil.inputStreamToRdfModel(inputStream = byteArrayInputStream, rdfFormat = Turtle)
      byteArrayInputStream.close()

      assertTrue(ok1, copyOfRdfModel == rdfModel)
    },
  )
}
