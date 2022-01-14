/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import java.io.{BufferedInputStream, BufferedOutputStream, InputStream, OutputStream}
import java.nio.file.{Files, Path}

import akka.http.scaladsl.model.MediaType
import org.knora.webapi.exceptions.{BadRequestException, InvalidRdfException}
import org.knora.webapi.{IRI, RdfMediaTypes, SchemaOption, SchemaOptions}

import scala.util.{Failure, Success, Try}

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
trait RdfFormatUtil {

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
        JsonLDUtil.parseJsonLD(rdfStr).toRdfModel(getRdfModelFactory)

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
  def parseWithStreamProcessor(rdfSource: RdfSource, rdfFormat: NonJsonLD, rdfStreamProcessor: RdfStreamProcessor): Unit

  /**
   * Reads RDF data from an [[InputStream]] and returns it as an [[RdfModel]]. Closes the input stream
   * before returning.
   *
   * @param inputStream the input stream.
   * @param rdfFormat   the data format.
   * @return the corresponding [[RdfModel]].
   */
  def inputStreamToRdfModel(inputStream: InputStream, rdfFormat: NonJsonLD): RdfModel

  /**
   * Formats an [[RdfModel]], writing the output to an [[OutputStream]]. Closes the output stream before
   * returning.
   *
   * @param rdfModel     the model to be written.
   * @param outputStream the output stream.
   * @param rdfFormat    the output format.
   */
  def rdfModelToOutputStream(rdfModel: RdfModel, outputStream: OutputStream, rdfFormat: NonJsonLD): Unit

  /**
   * Creates an [[RdfStreamProcessor]] that writes formatted output.
   *
   * @param outputStream the output stream to which the formatted RDF data should be written.
   * @param rdfFormat    the output format.
   * @return an an [[RdfStreamProcessor]].
   */
  def makeFormattingStreamProcessor(outputStream: OutputStream, rdfFormat: NonJsonLD): RdfStreamProcessor

  /**
   * Adds a context IRI to RDF statements.
   *
   * @param graphIri                  the IRI of the named graph.
   * @param formattingStreamProcessor an [[RdfStreamProcessor]] for writing the result.
   */
  private class ContextAddingProcessor(graphIri: IRI, formattingStreamProcessor: RdfStreamProcessor)
      extends RdfStreamProcessor {
    private val nodeFactory: RdfNodeFactory = getRdfNodeFactory

    override def start(): Unit = formattingStreamProcessor.start()

    override def processNamespace(prefix: String, namespace: IRI): Unit =
      formattingStreamProcessor.processNamespace(prefix = prefix, namespace = namespace)

    override def processStatement(statement: Statement): Unit = {
      val outputStatement = nodeFactory.makeStatement(
        subj = statement.subj,
        pred = statement.pred,
        obj = statement.obj,
        context = Some(graphIri)
      )

      formattingStreamProcessor.processStatement(outputStatement)
    }

    override def finish(): Unit = formattingStreamProcessor.finish()
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
    var maybeBufferedFileOutputStream: Option[BufferedOutputStream] = None

    val processingTry: Try[Unit] = Try {
      val bufferedFileOutputStream = new BufferedOutputStream(Files.newOutputStream(outputFile))
      maybeBufferedFileOutputStream = Some(bufferedFileOutputStream)

      val formattingStreamProcessor: RdfStreamProcessor = makeFormattingStreamProcessor(
        outputStream = bufferedFileOutputStream,
        rdfFormat = outputFormat
      )

      val contextAddingProcessor = new ContextAddingProcessor(
        graphIri = graphIri,
        formattingStreamProcessor = formattingStreamProcessor
      )

      parseWithStreamProcessor(
        rdfSource = rdfSource,
        rdfFormat = Turtle,
        rdfStreamProcessor = contextAddingProcessor
      )
    }

    maybeBufferedFileOutputStream.foreach(_.close)

    processingTry match {
      case Success(_)  => ()
      case Failure(ex) => throw ex
    }
  }

  /**
   * Returns an [[RdfModelFactory]] with the same underlying implementation as this [[RdfFormatUtil]].
   */
  def getRdfModelFactory: RdfModelFactory

  /**
   * Returns an [[RdfNodeFactory]] with the same underlying implementation as this [[RdfFormatUtil]].
   */
  def getRdfNodeFactory: RdfNodeFactory

  /**
   * Parses RDF in a format other than JSON-LD to an [[RdfModel]].
   *
   * @param rdfStr    the RDF string to be parsed.
   * @param rdfFormat the format of the string.
   * @return the corresponding [[RdfModel]].
   */
  protected def parseNonJsonLDToRdfModel(rdfStr: String, rdfFormat: NonJsonLD): RdfModel

  /**
   * Converts an [[RdfModel]] to a string in a format other than JSON-LD.
   *
   * @param rdfModel    the model to be formatted.
   * @param rdfFormat   the format to be used.
   * @param prettyPrint if `true`, the output should be pretty-printed.
   * @return a string representation of the RDF model.
   */
  protected def formatNonJsonLD(rdfModel: RdfModel, rdfFormat: NonJsonLD, prettyPrint: Boolean): String
}
