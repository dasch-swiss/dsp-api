/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf.jenaimpl

import org.apache.jena
import org.knora.webapi.IRI
import org.knora.webapi.feature.Feature
import org.knora.webapi.messages.util.rdf._

import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.io.StringWriter
import scala.util.Failure
import scala.util.Success
import scala.util.Try

/**
 * Wraps an [[RdfStreamProcessor]] in a [[jena.riot.system.StreamRDF]].
 */
class StreamProcessorAsStreamRDF(streamProcessor: RdfStreamProcessor) extends jena.riot.system.StreamRDF {
  override def start(): Unit = streamProcessor.start()

  override def triple(triple: jena.graph.Triple): Unit =
    streamProcessor.processStatement(
      JenaStatement(jena.sparql.core.Quad.create(jena.sparql.core.Quad.defaultGraphIRI, triple))
    )

  override def quad(quad: jena.sparql.core.Quad): Unit =
    streamProcessor.processStatement(JenaStatement(quad))

  override def base(s: String): Unit = {}

  override def prefix(prefixStr: String, namespace: String): Unit =
    streamProcessor.processNamespace(prefixStr, namespace)

  override def finish(): Unit = streamProcessor.finish()
}

/**
 * Wraps a [[jena.riot.system.StreamRDF]] in a [[RdfStreamProcessor]].
 */
class StreamRDFAsStreamProcessor(streamRDF: jena.riot.system.StreamRDF) extends RdfStreamProcessor {

  import JenaConversions._

  override def start(): Unit = streamRDF.start()

  override def processNamespace(prefix: String, namespace: IRI): Unit =
    streamRDF.prefix(prefix, namespace)

  override def processStatement(statement: Statement): Unit =
    streamRDF.quad(statement.asJenaQuad)

  override def finish(): Unit = streamRDF.finish()
}

/**
 * An implementation of [[RdfFormatUtil]] that uses the Jena API.
 */
class JenaFormatUtil(private val modelFactory: JenaModelFactory, private val nodeFactory: JenaNodeFactory)
    extends RdfFormatUtil
    with Feature {
  override def getRdfModelFactory: RdfModelFactory = modelFactory

  override def getRdfNodeFactory: RdfNodeFactory = nodeFactory

  private def rdfFormatToJenaParsingLang(rdfFormat: NonJsonLD): jena.riot.Lang =
    rdfFormat match {
      case Turtle => jena.riot.RDFLanguages.TURTLE
      case TriG   => jena.riot.RDFLanguages.TRIG
      case RdfXml => jena.riot.RDFLanguages.RDFXML
      case NQuads => jena.riot.RDFLanguages.NQUADS
    }

  override def parseNonJsonLDToRdfModel(rdfStr: String, rdfFormat: NonJsonLD): RdfModel = {
    val jenaModel: JenaModel = modelFactory.makeEmptyModel

    jena.riot.RDFParser
      .create()
      .source(new StringReader(rdfStr))
      .lang(rdfFormatToJenaParsingLang(rdfFormat))
      .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
      .parse(jenaModel.getDataset)

    jenaModel
  }

  override def formatNonJsonLD(rdfModel: RdfModel, rdfFormat: NonJsonLD, prettyPrint: Boolean): String = {
    import JenaConversions._

    val datasetGraph: jena.sparql.core.DatasetGraph = rdfModel.asJenaDataset.asDatasetGraph
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

  override def parseWithStreamProcessor(
    rdfSource: RdfSource,
    rdfFormat: NonJsonLD,
    rdfStreamProcessor: RdfStreamProcessor
  ): Unit = {
    // Wrap the RdfStreamProcessor in a StreamProcessorAsStreamRDF.
    val streamRDF = new StreamProcessorAsStreamRDF(rdfStreamProcessor)

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
        .lang(rdfFormatToJenaParsingLang(rdfFormat))
        .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
        .parse(streamRDF)
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
      val model: JenaModel = modelFactory.makeEmptyModel

      jena.riot.RDFDataMgr.read(
        model.getDataset.asDatasetGraph,
        inputStream,
        rdfFormatToJenaParsingLang(rdfFormat)
      )

      model
    }

    inputStream.close()
    parseTry.get
  }

  override def makeFormattingStreamProcessor(outputStream: OutputStream, rdfFormat: NonJsonLD): RdfStreamProcessor = {
    // Construct a Jena StreamRDF for the requested format.
    val streamRDF: jena.riot.system.StreamRDF = jena.riot.system.StreamRDFWriter.getWriterStream(
      outputStream,
      rdfFormatToJenaParsingLang(rdfFormat)
    )

    // Wrap it in a StreamRDFAsStreamProcessor.
    new StreamRDFAsStreamProcessor(streamRDF)
  }

  override def rdfModelToOutputStream(rdfModel: RdfModel, outputStream: OutputStream, rdfFormat: NonJsonLD): Unit = {
    import JenaConversions._

    val formatTry: Try[Unit] = Try {
      val datasetGraph: jena.sparql.core.DatasetGraph = rdfModel.asJenaDataset.asDatasetGraph

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

    formatTry match {
      case Success(_)  => ()
      case Failure(ex) => throw ex
    }
  }
}
