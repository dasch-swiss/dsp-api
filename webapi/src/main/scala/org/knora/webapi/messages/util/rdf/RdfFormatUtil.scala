/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import org.apache.jena
import org.apache.pekko

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import scala.util.Try

import dsp.errors.BadRequestException
import dsp.errors.InvalidRdfException
import org.knora.webapi.IRI
import org.knora.webapi.RdfMediaTypes
import org.knora.webapi.SchemaOption
import org.knora.webapi.SchemaOptions
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaConversions
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaModel
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaModelFactory
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaNodeFactory
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaStatement

import pekko.http.scaladsl.model.MediaType

/**
 * A trait for supported RDF formats.
 */
sealed trait RdfFormat {

  /**
   * The [[MediaType]] that represents this format.
   */
  val toMediaType: MediaType
}

/**
 * A trait for formats other than JSON-LD.
 */
sealed trait NonJsonLD extends RdfFormat {

  /**
   * `true` if this format supports pretty-printing.
   */
  def supportsPrettyPrinting: Boolean
}

/**
 * Represents a format that supports quads.
 */
sealed trait QuadFormat extends NonJsonLD

object RdfFormat {

  /**
   * Converts a [[MediaType]] to an [[RdfFormat]].
   *
   * @param mediaType a [[MediaType]].
   * @return the corresponding [[RdfFormat]].
   */
  def fromMediaType(mediaType: MediaType): RdfFormat =
    mediaType match {
      case RdfMediaTypes.`application/ld+json` => JsonLD
      case RdfMediaTypes.`text/turtle`         => Turtle
      case RdfMediaTypes.`application/trig`    => TriG
      case RdfMediaTypes.`application/rdf+xml` => RdfXml
      case RdfMediaTypes.`application/n-quads` => NQuads
      case other                               => throw InvalidRdfException(s"Unsupported RDF media type: $other")
    }
}

/**
 * Represents JSON-LD format.
 */
case object JsonLD extends RdfFormat {
  override def toString: String = "JSON-LD"

  override val toMediaType: MediaType = RdfMediaTypes.`application/ld+json`
}

/**
 * Represents Turtle format.
 */
case object Turtle extends NonJsonLD {
  override def toString: String = "Turtle"

  override val toMediaType: MediaType = RdfMediaTypes.`text/turtle`

  override val supportsPrettyPrinting: Boolean = true
}

/**
 * Represents TriG format.
 */
case object TriG extends QuadFormat {
  override def toString: String = "TriG"

  override val toMediaType: MediaType = RdfMediaTypes.`application/trig`

  override val supportsPrettyPrinting: Boolean = true
}

/**
 * Represents RDF-XML format.
 */
case object RdfXml extends NonJsonLD {
  override def toString: String = "RDF/XML"

  override val toMediaType: MediaType = RdfMediaTypes.`application/rdf+xml`

  override val supportsPrettyPrinting: Boolean = true
}

/**
 * Represents N-Quads format.
 */
case object NQuads extends QuadFormat {
  override def toString: String = "N-Quads"

  override val toMediaType: MediaType = RdfMediaTypes.`application/n-quads`

  override val supportsPrettyPrinting: Boolean = false
}

/**
 * A trait for classes that process streams of RDF data.
 */
trait RdfStreamProcessor {

  /**
   * Signals the start of the RDF data.
   */
  def start(): Unit

  /**
   * Processes a namespace declaration.
   *
   * @param prefix    the prefix.
   * @param namespace the namespace.
   */
  def processNamespace(prefix: String, namespace: IRI): Unit

  /**
   * Processes a statement.
   *
   * @param statement the statement.
   */
  def processStatement(statement: Statement): Unit

  /**
   * Signals the end of the RDF data.
   */
  def finish(): Unit
}

/**
 * Represents a source of RDF data to be processed using an [[RdfStreamProcessor]].
 */
sealed trait RdfSource

/**
 * An [[RdfSource]] that reads RDF data from a string.
 *
 * @param rdfStr a string containing RDF data.
 */
case class RdfStringSource(rdfStr: String) extends RdfSource

/**
 * An [[RdfSource]] that reads data from an [[InputStream]].
 *
 * @param inputStream the input stream.
 */
case class RdfInputStreamSource(inputStream: InputStream) extends RdfSource

/**
 * Formats and parses RDF.
 */
final case class RdfFormatUtil(modelFactory: JenaModelFactory, nodeFactory: JenaNodeFactory) {

  /**
   * Parses an RDF string to an [[RdfModel]].
   *
   * @param rdfStr    the RDF string to be parsed.
   * @param rdfFormat the format of the string.
   * @return the corresponding [[RdfModel]].
   */
  def parseToRdfModel(rdfStr: String, rdfFormat: RdfFormat): RdfModel =
    rdfFormat match {
      case JsonLD =>
        // Use JsonLDUtil to parse JSON-LD, and to convert the
        // resulting JsonLDDocument to an RdfModel.
        JsonLDUtil.parseJsonLD(rdfStr).toRdfModel(modelFactory)

      case nonJsonLD: NonJsonLD =>
        // Use an implementation-specific function to parse other formats.
        parseNonJsonLDToRdfModel(rdfStr = rdfStr, rdfFormat = nonJsonLD)
    }

  /**
   * Parses an RDF string to a [[JsonLDDocument]].
   *
   * @param rdfStr     the RDF string to be parsed.
   * @param rdfFormat  the format of the string.
   * @param flatJsonLD if `true`, return flat JSON-LD.
   * @return the corresponding [[JsonLDDocument]].
   */
  def parseToJsonLDDocument(rdfStr: String, rdfFormat: RdfFormat, flatJsonLD: Boolean = false): JsonLDDocument =
    rdfFormat match {
      case JsonLD =>
        // Use JsonLDUtil to parse JSON-LD.
        JsonLDUtil.parseJsonLD(jsonLDString = rdfStr, flatten = flatJsonLD)

      case nonJsonLD: NonJsonLD =>
        // Use an implementation-specific function to parse other formats to an RdfModel.
        // Use JsonLDUtil to convert the resulting model to a JsonLDDocument.
        JsonLDUtil.fromRdfModel(
          model = parseNonJsonLDToRdfModel(rdfStr = rdfStr, rdfFormat = nonJsonLD),
          flatJsonLD = flatJsonLD
        )
    }

  /**
   * Converts an [[RdfModel]] to a string.
   *
   * @param rdfModel      the model to be formatted.
   * @param rdfFormat     the format to be used.
   * @param schemaOptions the schema options that were submitted with the request.
   * @param prettyPrint   if `true`, the output should be pretty-printed.
   * @return a string representation of the RDF model.
   */
  def format(
    rdfModel: RdfModel,
    rdfFormat: RdfFormat,
    schemaOptions: Set[SchemaOption] = Set.empty,
    prettyPrint: Boolean = true
  ): String =
    rdfFormat match {
      case JsonLD =>
        // Use JsonLDUtil to convert to JSON-LD.
        val jsonLDDocument: JsonLDDocument = JsonLDUtil.fromRdfModel(
          model = rdfModel,
          flatJsonLD = SchemaOptions.returnFlatJsonLD(schemaOptions)
        )

        // Format the document as a string.
        if (prettyPrint) {
          jsonLDDocument.toPrettyString()
        } else {
          jsonLDDocument.toCompactString()
        }

      case nonJsonLD: NonJsonLD =>
        // Some formats can't represent named graphs.
        if (rdfModel.getContexts.nonEmpty) {
          nonJsonLD match {
            case _: QuadFormat => ()
            case _             => throw BadRequestException(s"Named graphs are not supported in $rdfFormat")
          }
        }

        // Use an implementation-specific function to convert to formats other than JSON-LD.
        formatNonJsonLD(
          rdfModel = rdfModel,
          rdfFormat = nonJsonLD,
          prettyPrint = prettyPrint
        )
    }

  /**
   * Reads an RDF file into an [[RdfModel]].
   *
   * @param file      the file.
   * @param rdfFormat the file format.
   * @return a [[RdfModel]] representing the contents of the file.
   */
  def fileToRdfModel(file: Path, rdfFormat: NonJsonLD): RdfModel =
    inputStreamToRdfModel(
      inputStream = new BufferedInputStream(Files.newInputStream(file)),
      rdfFormat = rdfFormat
    )

  /**
   * Writes an [[RdfModel]] to a file.
   *
   * @param rdfModel  the [[RdfModel]].
   * @param file      the file to be written.
   * @param rdfFormat the file format.
   */
  def rdfModelToFile(rdfModel: RdfModel, file: Path, rdfFormat: NonJsonLD): Unit =
    rdfModelToOutputStream(
      rdfModel = rdfModel,
      outputStream = new BufferedOutputStream(Files.newOutputStream(file)),
      rdfFormat = rdfFormat
    )

  /**
   * Parses RDF input, processing it with an [[RdfStreamProcessor]].  If the source is an input
   * stream, this method closes it before returning. The caller must close any output stream
   * used by the [[RdfStreamProcessor]].
   *
   * @param rdfSource          the input source from which the RDF data should be read.
   * @param rdfFormat          the input format.
   * @param rdfStreamProcessor the [[RdfStreamProcessor]] that will be used to process the input.
   */
  def parseWithStreamProcessor(rdfSource: RdfSource, rdfStreamProcessor: RdfStreamProcessor): Unit = {

    val streamRDF = new jena.riot.system.StreamRDF {
      override def start(): Unit = rdfStreamProcessor.start()
      override def triple(triple: jena.graph.Triple): Unit =
        rdfStreamProcessor.processStatement(
          JenaStatement(jena.sparql.core.Quad.create(jena.sparql.core.Quad.defaultGraphIRI, triple))
        )
      override def quad(quad: jena.sparql.core.Quad): Unit   = rdfStreamProcessor.processStatement(JenaStatement(quad))
      override def base(base: String): Unit                  = {}
      override def prefix(prefix: String, iri: String): Unit = rdfStreamProcessor.processNamespace(prefix, iri)
      override def finish(): Unit                            = rdfStreamProcessor.finish()

    }

    // Build a parser.
    val parser = jena.riot.RDFParser.create()

    // Configure it to read from the input source.
    rdfSource match {
      case RdfStringSource(rdfStr)           => parser.source(new StringReader(rdfStr))
      case RdfInputStreamSource(inputStream) => parser.source(inputStream)
    }

    val parseTry: Try[Unit] = Try {
      // Add the other configuration and run the parser.
      parser
        .lang(jena.riot.RDFLanguages.TURTLE)
        .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
        .parse(streamRDF)
    }

    rdfSource match {
      case RdfInputStreamSource(inputStream) => inputStream.close()
      case _                                 => ()
    }

    parseTry.get
  }

  /**
   * Reads RDF data from an [[InputStream]] and returns it as an [[RdfModel]]. Closes the input stream
   * before returning.
   *
   * @param inputStream the input stream.
   * @param rdfFormat   the data format.
   * @return the corresponding [[RdfModel]].
   */
  def inputStreamToRdfModel(inputStream: InputStream, rdfFormat: NonJsonLD): RdfModel = {
    val parseTry: Try[RdfModel] = Try {
      val model: JenaModel = modelFactory.makeEmptyModel
      jena.riot.RDFDataMgr.read(
        model.getDataset.asDatasetGraph,
        inputStream,
        RdfFormatUtil.rdfFormatToJenaParsingLang(rdfFormat)
      )
      model
    }
    inputStream.close()
    parseTry.get
  }

  /**
   * Formats an [[RdfModel]], writing the output to an [[OutputStream]]. Closes the output stream before
   * returning.
   *
   * @param rdfModel     the model to be written.
   * @param outputStream the output stream.
   * @param rdfFormat    the output format.
   */
  def rdfModelToOutputStream(rdfModel: RdfModel, outputStream: OutputStream, rdfFormat: NonJsonLD): Unit = {
    val formatTry: Try[Unit] = Try {
      val datasetGraph: jena.sparql.core.DatasetGraph = JenaConversions.asJenaDataset(rdfModel).asDatasetGraph

      rdfFormat match {
        case Turtle =>
          jena.riot.RDFDataMgr.write(outputStream, datasetGraph.getDefaultGraph, jena.riot.RDFFormat.TURTLE_FLAT)

        case RdfXml =>
          jena.riot.RDFDataMgr.write(outputStream, datasetGraph.getDefaultGraph, jena.riot.RDFFormat.RDFXML_PLAIN)

        case TriG =>
          jena.riot.RDFDataMgr.write(outputStream, datasetGraph, jena.riot.RDFFormat.TRIG_FLAT)

        case NQuads =>
          jena.riot.RDFDataMgr.write(outputStream, datasetGraph, jena.riot.RDFFormat.NQUADS)
      }
    }
    outputStream.close()
    formatTry.get
  }

  /**
   * Creates an [[RdfStreamProcessor]] that writes formatted output.
   *
   * @param outputStream the output stream to which the formatted RDF data should be written.
   * @param rdfFormat    the output format.
   * @return an an [[RdfStreamProcessor]].
   */
  def makeFormattingStreamProcessor(outputStream: OutputStream, rdfFormat: NonJsonLD): RdfStreamProcessor = {
    val streamRDF: jena.riot.system.StreamRDF = jena.riot.system.StreamRDFWriter.getWriterStream(
      outputStream,
      RdfFormatUtil.rdfFormatToJenaParsingLang(rdfFormat)
    )
    new RdfStreamProcessor {
      override def start(): Unit                                          = streamRDF.start()
      override def processNamespace(prefix: String, namespace: IRI): Unit = streamRDF.prefix(prefix, namespace)
      override def processStatement(statement: Statement): Unit           = streamRDF.quad(JenaConversions.asJenaQuad(statement))
      override def finish(): Unit                                         = streamRDF.finish()
    }
  }

  /**
   * Reads RDF data in Turtle format from an [[RdfSource]], adds a named graph IRI to each statement,
   * and writes the result to a file in a format that supports quads. If the source is an input
   * stream, this method closes it before returning.
   *
   * @param rdfSource    the RDF data source.
   * @param graphIri     the named graph IRI to be added.
   * @param outputFile   the output file.
   * @param outputFormat the output file format.
   */
  def turtleToQuadsFile(rdfSource: RdfSource, graphIri: IRI, outputFile: Path, outputFormat: QuadFormat): Unit = {
    val bufferedFileOutputStream = new BufferedOutputStream(Files.newOutputStream(outputFile))
    val formattingStreamProcessor: RdfStreamProcessor = makeFormattingStreamProcessor(
      outputStream = bufferedFileOutputStream,
      rdfFormat = outputFormat
    )

    val streamRDF = new jena.riot.system.StreamRDF {
      private def processStatement(statement: Statement): Unit = {
        val outputStatement = JenaStatement(
          new jena.sparql.core.Quad(
            jena.graph.NodeFactory.createURI(graphIri),
            JenaConversions.asJenaNode(statement.subj),
            JenaConversions.asJenaNode(statement.pred),
            JenaConversions.asJenaNode(statement.obj)
          )
        )

        formattingStreamProcessor.processStatement(outputStatement)
      }
      override def start(): Unit = formattingStreamProcessor.start()
      override def triple(triple: jena.graph.Triple): Unit =
        quad(jena.sparql.core.Quad.create(jena.sparql.core.Quad.defaultGraphIRI, triple))
      override def quad(quad: jena.sparql.core.Quad): Unit = processStatement(JenaStatement(quad))
      override def base(s: String): Unit                   = {}
      override def prefix(prefixStr: String, namespace: String): Unit =
        formattingStreamProcessor.processNamespace(prefixStr, namespace)
      override def finish(): Unit = formattingStreamProcessor.finish()
    }

    // Build a parser.
    val parser = jena.riot.RDFParser.create()

    // Configure it to read from the input source.
    rdfSource match {
      case RdfStringSource(rdfStr)           => parser.source(new StringReader(rdfStr))
      case RdfInputStreamSource(inputStream) => parser.source(inputStream)
    }

    val parseTry: Try[Unit] = Try {
      // Add the other configuration and run the parser.
      parser
        .lang(jena.riot.RDFLanguages.TURTLE)
        .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
        .parse(streamRDF)
    }

    rdfSource match {
      case RdfInputStreamSource(inputStream) => inputStream.close()
      case _                                 => ()
    }
    bufferedFileOutputStream.close()

    parseTry.get
  }

  // /**
  //  * Returns an [[RdfModelFactory]] with the same underlying implementation as this [[RdfFormatUtil]].
  //  */
  // def getRdfModelFactory: RdfModelFactory

  // /**
  //  * Returns an [[RdfNodeFactory]] with the same underlying implementation as this [[RdfFormatUtil]].
  //  */
  // def getRdfNodeFactory: RdfNodeFactory

  /**
   * Parses RDF in a format other than JSON-LD to an [[RdfModel]].
   *
   * @param rdfStr    the RDF string to be parsed.
   * @param rdfFormat the format of the string.
   * @return the corresponding [[RdfModel]].
   */
  protected def parseNonJsonLDToRdfModel(rdfStr: String, rdfFormat: NonJsonLD): RdfModel = {
    val jenaModel: JenaModel = modelFactory.makeEmptyModel
    jena.riot.RDFParser
      .create()
      .source(new StringReader(rdfStr))
      .lang(RdfFormatUtil.rdfFormatToJenaParsingLang(rdfFormat))
      .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
      .parse(jenaModel.getDataset)
    jenaModel
  }

  /**
   * Converts an [[RdfModel]] to a string in a format other than JSON-LD.
   *
   * @param rdfModel    the model to be formatted.
   * @param rdfFormat   the format to be used.
   * @param prettyPrint if `true`, the output should be pretty-printed.
   * @return a string representation of the RDF model.
   */
  protected def formatNonJsonLD(rdfModel: RdfModel, rdfFormat: NonJsonLD, prettyPrint: Boolean): String = {

    val datasetGraph: jena.sparql.core.DatasetGraph = JenaConversions.asJenaDataset(rdfModel).asDatasetGraph
    val stringWriter: StringWriter                  = new StringWriter

    rdfFormat match {
      case Turtle =>
        val jenaRdfFormat: jena.riot.RDFFormat = if (prettyPrint) {
          jena.riot.RDFFormat.TURTLE_PRETTY
        } else {
          jena.riot.RDFFormat.TURTLE_FLAT
        }

        jena.riot.RDFDataMgr.write(stringWriter, datasetGraph.getDefaultGraph, jenaRdfFormat)

      case RdfXml =>
        val jenaRdfFormat: jena.riot.RDFFormat = if (prettyPrint) {
          jena.riot.RDFFormat.RDFXML_PRETTY
        } else {
          jena.riot.RDFFormat.RDFXML_PLAIN
        }

        jena.riot.RDFDataMgr.write(stringWriter, datasetGraph.getDefaultGraph, jenaRdfFormat)

      case TriG =>
        val jenaRdfFormat: jena.riot.RDFFormat = if (prettyPrint) {
          jena.riot.RDFFormat.TRIG_PRETTY
        } else {
          jena.riot.RDFFormat.TRIG_FLAT
        }

        jena.riot.RDFDataMgr.write(stringWriter, datasetGraph, jenaRdfFormat)

      case NQuads =>
        jena.riot.RDFDataMgr.write(stringWriter, datasetGraph, jena.riot.RDFFormat.NQUADS)
    }

    stringWriter.toString
  }
}

object RdfFormatUtil {
  def rdfFormatToJenaParsingLang(rdfFormat: NonJsonLD): jena.riot.Lang =
    rdfFormat match {
      case Turtle => jena.riot.RDFLanguages.TURTLE
      case TriG   => jena.riot.RDFLanguages.TRIG
      case RdfXml => jena.riot.RDFLanguages.RDFXML
      case NQuads => jena.riot.RDFLanguages.NQUADS
    }
}
