/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.rdf

import org.apache.jena
import org.scalatest.compatible.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

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
class RdfFormatUtilSpec extends AnyWordSpec with Matchers {

  implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private def checkModelForRdfTypeBook(rdfModel: RdfModel, context: Option[IRI] = None): Assertion = {
    val statements: Set[Statement] = rdfModel
      .find(
        subj = Some(JenaNodeFactory.makeIriNode("http://rdfh.ch/0803/2a6221216701")),
        pred = Some(JenaNodeFactory.makeIriNode(OntologyConstants.Rdf.Type)),
        obj = None,
        context = context,
      )
      .toSet

    assert(statements.size == 1)
    assert(statements.head.obj == JenaNodeFactory.makeIriNode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book"))
  }

  private def checkJsonLDDocumentForRdfTypeBook(jsonLDDocument: JsonLDDocument): Assertion =
    assert(
      jsonLDDocument.body
        .getRequiredString(JsonLDKeywords.TYPE)
        .fold(msg => throw BadRequestException(msg), identity) == "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book",
    )

  "RdfFormatUtil" should {
    "parse RDF in Turtle format, producing an RdfModel, then format it as Turtle again" in {
      val inputTurtle: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"),
        )
      val inputModel = RdfModel.fromTurtle(inputTurtle)
      checkModelForRdfTypeBook(inputModel)

      val outputTurtle: String = RdfFormatUtil.format(rdfModel = inputModel, rdfFormat = Turtle)
      val outputModel          = RdfModel.fromTurtle(outputTurtle)
      checkModelForRdfTypeBook(outputModel)
      assert(outputModel == inputModel)
    }

    "parse RDF in JSON-LD format, producing an RdfModel, then format it as JSON-LD again" in {
      val input: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"),
        )
      val inputModel = RdfModel.fromJsonLD(input)
      checkModelForRdfTypeBook(inputModel)

      val output: String = RdfFormatUtil.format(rdfModel = inputModel, rdfFormat = JsonLD)
      val outputModel    = RdfModel.fromJsonLD(output)
      checkModelForRdfTypeBook(outputModel)
      assert(outputModel == inputModel)
    }

    "parse RDF in Turtle format, producing a JsonLDDocument, then format it as Turtle again" in {
      val inputTurtle: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"),
        )
      val inputModel                          = RdfModel.fromTurtle(inputTurtle)
      val inputJsonLDDocument: JsonLDDocument =
        RdfFormatUtilSpec.parseToJsonLDDocument(inputTurtle, Turtle)
      checkJsonLDDocumentForRdfTypeBook(inputJsonLDDocument)

      val jsonLDOutputModel: RdfModel = inputJsonLDDocument.toRdfModel
      checkModelForRdfTypeBook(jsonLDOutputModel)
      assert(jsonLDOutputModel == inputModel)

      val outputTurtle: String = RdfFormatUtil.format(rdfModel = jsonLDOutputModel, rdfFormat = Turtle)
      val turtleOutputModel    = RdfModel.fromTurtle(outputTurtle)
      checkModelForRdfTypeBook(turtleOutputModel)
      assert(turtleOutputModel == inputModel)
    }

    "parse RDF in RDF/XML format, producing a JsonLDDocument, then format it as RDF/XML again" in {
      val inputRdfXml: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.rdf"),
        )
      val inputModel                          = RdfModel.fromRdfXml(inputRdfXml)
      val inputJsonLDDocument: JsonLDDocument =
        RdfFormatUtilSpec.parseToJsonLDDocument(inputRdfXml, RdfXml)
      checkJsonLDDocumentForRdfTypeBook(inputJsonLDDocument)

      val jsonLDOutputModel: RdfModel = inputJsonLDDocument.toRdfModel
      checkModelForRdfTypeBook(jsonLDOutputModel)
      assert(jsonLDOutputModel == inputModel)

      val outputRdfXml: String = RdfFormatUtil.format(rdfModel = jsonLDOutputModel, rdfFormat = RdfXml)
      val rdfXmlOutputModel    = RdfModel.fromRdfXml(outputRdfXml)
      checkModelForRdfTypeBook(rdfXmlOutputModel)
      assert(rdfXmlOutputModel == inputModel)
    }

    "parse RDF in TriG format" in {
      val graphIri  = "http://example.org/data#"
      val inputTrig = FileUtil.readTextFile(
        Paths.get("test_data/generated_test_data/rdfFormatUtil/BookReiseInsHeiligeLand.trig"),
      )
      val inputModel = RdfModel.fromTriG(inputTrig)
      checkModelForRdfTypeBook(rdfModel = inputModel, context = Some(graphIri))
    }

    "parse RDF in N-Quads format" in {
      val graphIri  = "http://example.org/data#"
      val inputTrig =
        FileUtil.readTextFile(Paths.get("test_data/generated_test_data/rdfFormatUtil/BookReiseInsHeiligeLand.nq"))
      val inputModel = RdfModel.fromNQuads(inputTrig)
      checkModelForRdfTypeBook(rdfModel = inputModel, context = Some(graphIri))
    }

    "read Turtle, add a graph IRI to it, write it to a TriG file, and read back the TriG file" in {
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
      checkModelForRdfTypeBook(rdfModel = quadsModel, context = Some(graphIri))
    }

    "read Turtle, add a graph IRI to it, write it to an N-Quads file, and read back the N-Quads file" in {
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
      checkModelForRdfTypeBook(rdfModel = quadsModel, context = Some(graphIri))
    }

    "parse RDF in JSON-LD format, producing a JsonLDDocument, then format it as JSON-LD again" in {
      val inputTurtle: String =
        FileUtil.readTextFile(
          Paths.get("test_data/generated_test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"),
        )
      val inputJsonLDDocument: JsonLDDocument =
        RdfFormatUtilSpec.parseToJsonLDDocument(inputTurtle, JsonLD)
      checkJsonLDDocumentForRdfTypeBook(inputJsonLDDocument)

      val outputJsonLD: String                 = inputJsonLDDocument.toPrettyString()
      val outputJsonLDDocument: JsonLDDocument =
        RdfFormatUtilSpec.parseToJsonLDDocument(outputJsonLD, JsonLD)
      checkJsonLDDocumentForRdfTypeBook(outputJsonLDDocument)
      assert(inputJsonLDDocument == outputJsonLDDocument)
    }

    "use prefixes and custom datatypes" in {
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
      assert(outputTurtle.contains("\"JULIAN:1481 CE\"^^knora-api:Date"))
    }

    "stream RDF data from an InputStream into an RdfModel, then into an OutputStream, then back into an RdfModel" in {
      val fileInputStream =
        new BufferedInputStream(Files.newInputStream(Paths.get("test_data/project_ontologies/anything-onto.ttl")))
      val rdfModel: RdfModel = RdfFormatUtil.inputStreamToRdfModel(inputStream = fileInputStream, rdfFormat = Turtle)
      fileInputStream.close()
      assert(rdfModel.contains(RdfFormatUtilSpec.expectedThingLabelStatement))

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

      assert(copyOfRdfModel == rdfModel)
    }
  }
}

object RdfFormatUtilSpec {
  private val expectedThingLabelStatement = JenaNodeFactory.makeStatement(
    JenaNodeFactory.makeIriNode("http://www.knora.org/ontology/0001/anything#Thing"),
    JenaNodeFactory.makeIriNode(OntologyConstants.Rdfs.Label),
    JenaNodeFactory.makeStringWithLanguage(value = "Thing", language = "en"),
  )

  def parseToJsonLDDocument(
    rdfStr: String,
    rdfFormat: RdfFormat,
  ): JsonLDDocument =
    rdfFormat match {
      case JsonLD =>
        // Use JsonLDUtil to parse JSON-LD.
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
}
