/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf.rdf4jimpl

import java.io.{InputStream, OutputStream, StringReader, StringWriter}

import org.eclipse.rdf4j
import org.knora.webapi.IRI
import org.knora.webapi.feature.Feature
import org.knora.webapi.messages.util.rdf._

import scala.util.{Failure, Success, Try}

/**
 * Wraps an [[RdfStreamProcessor]] in an [[rdf4j.rio.RDFHandler]].
 */
class StreamProcessorAsRDFHandler(streamProcessor: RdfStreamProcessor) extends rdf4j.rio.RDFHandler {
  override def startRDF(): Unit = streamProcessor.start()

  override def endRDF(): Unit = streamProcessor.finish()

  override def handleNamespace(prefix: String, namespace: String): Unit =
    streamProcessor.processNamespace(prefix, namespace)

  override def handleStatement(statement: rdf4j.model.Statement): Unit =
    streamProcessor.processStatement(RDF4JStatement(statement))

  override def handleComment(comment: String): Unit = {}
}

/**
 * Wraps an [[rdf4j.rio.RDFHandler]] in an [[RdfStreamProcessor]].
 */
class RDFHandlerAsStreamProcessor(rdfWriter: rdf4j.rio.RDFHandler) extends RdfStreamProcessor {

  import RDF4JConversions._

  override def start(): Unit = rdfWriter.startRDF()

  override def processNamespace(prefix: String, namespace: IRI): Unit =
    rdfWriter.handleNamespace(prefix, namespace)

  override def processStatement(statement: Statement): Unit =
    rdfWriter.handleStatement(statement.asRDF4JStatement)

  override def finish(): Unit = rdfWriter.endRDF()
}

/**
 * An implementation of [[RdfFormatUtil]] that uses the RDF4J API.
 */
class RDF4JFormatUtil(private val modelFactory: RDF4JModelFactory, private val nodeFactory: RDF4JNodeFactory)
    extends RdfFormatUtil
    with Feature {
  override def getRdfModelFactory: RdfModelFactory = modelFactory

  override def getRdfNodeFactory: RdfNodeFactory = nodeFactory

  private def rdfFormatToRDF4JFormat(rdfFormat: NonJsonLD): rdf4j.rio.RDFFormat =
    rdfFormat match {
      case Turtle => rdf4j.rio.RDFFormat.TURTLE
      case TriG   => rdf4j.rio.RDFFormat.TRIG
      case RdfXml => rdf4j.rio.RDFFormat.RDFXML
      case NQuads => rdf4j.rio.RDFFormat.NQUADS
    }

  override def parseNonJsonLDToRdfModel(rdfStr: String, rdfFormat: NonJsonLD): RdfModel =
    new RDF4JModel(
      model = rdf4j.rio.Rio.parse(
        new StringReader(rdfStr),
        "",
        rdfFormatToRDF4JFormat(rdfFormat)
      ),
      nodeFactory = nodeFactory
    )

  override def formatNonJsonLD(rdfModel: RdfModel, rdfFormat: NonJsonLD, prettyPrint: Boolean): String = {
    import RDF4JConversions._

    val stringWriter                   = new StringWriter
    val rdfWriter: rdf4j.rio.RDFWriter = rdf4j.rio.Rio.createWriter(rdfFormatToRDF4JFormat(rdfFormat), stringWriter)

    if (prettyPrint && rdfFormat.supportsPrettyPrinting) {
      rdfWriter.getWriterConfig
        .set[java.lang.Boolean](rdf4j.rio.helpers.BasicWriterSettings.INLINE_BLANK_NODES, true)
        .set[java.lang.Boolean](rdf4j.rio.helpers.BasicWriterSettings.PRETTY_PRINT, prettyPrint)
    }

    // Format the RDF.
    rdf4j.rio.Rio.write(rdfModel.asRDF4JModel, rdfWriter)
    stringWriter.toString
  }

  override def parseWithStreamProcessor(
    rdfSource: RdfSource,
    rdfFormat: NonJsonLD,
    rdfStreamProcessor: RdfStreamProcessor
  ): Unit = {
    // Construct an RDF4J parser for the requested format.
    val parser: rdf4j.rio.RDFParser = rdf4j.rio.Rio.createParser(rdfFormatToRDF4JFormat(rdfFormat))

    // Wrap the RdfStreamProcessor in a StreamProcessorAsRDFHandler and set it as the parser's RDFHandler.
    parser.setRDFHandler(new StreamProcessorAsRDFHandler(rdfStreamProcessor))

    val parseTry: Try[Unit] = Try {
      // Parse from the input source.
      rdfSource match {
        case RdfStringSource(rdfStr)           => parser.parse(new StringReader(rdfStr), "")
        case RdfInputStreamSource(inputStream) => parser.parse(inputStream, "")
      }
    }

    rdfSource match {
      case RdfInputStreamSource(inputStream) => inputStream.close()
      case _                                 => ()
    }

    parseTry match {
      case Success(_)  => ()
      case Failure(ex) => throw ex
    }
  }

  override def inputStreamToRdfModel(inputStream: InputStream, rdfFormat: NonJsonLD): RdfModel = {
    val parseTry: Try[RdfModel] = Try {
      val model: rdf4j.model.Model = rdf4j.rio.Rio.parse(
        inputStream,
        "",
        rdfFormatToRDF4JFormat(rdfFormat)
      )

      new RDF4JModel(
        model = model,
        nodeFactory = nodeFactory
      )
    }

    inputStream.close()
    parseTry.get
  }

  override def makeFormattingStreamProcessor(outputStream: OutputStream, rdfFormat: NonJsonLD): RdfStreamProcessor = {
    // Construct an RDF4J writer for the requested format.
    val rdfWriter: rdf4j.rio.RDFWriter = rdf4j.rio.Rio.createWriter(rdfFormatToRDF4JFormat(rdfFormat), outputStream)

    // Wrap it in an RDFHandlerAsStreamProcessor.
    new RDFHandlerAsStreamProcessor(rdfWriter)
  }

  override def rdfModelToOutputStream(rdfModel: RdfModel, outputStream: OutputStream, rdfFormat: NonJsonLD): Unit = {
    import RDF4JConversions._

    val formatTry: Try[Unit] = Try {
      // Construct an RDF4J writer for the requested format.
      val rdfWriter: rdf4j.rio.RDFWriter = rdf4j.rio.Rio.createWriter(rdfFormatToRDF4JFormat(rdfFormat), outputStream)

      // Format the RDF.
      rdf4j.rio.Rio.write(rdfModel.asRDF4JModel, rdfWriter)
    }

    outputStream.close()

    formatTry match {
      case Success(_)  => ()
      case Failure(ex) => throw ex
    }
  }
}
