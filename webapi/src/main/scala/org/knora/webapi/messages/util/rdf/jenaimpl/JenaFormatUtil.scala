/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf.jenaimpl

import org.apache.jena

import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.io.StringWriter
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.knora.webapi.IRI
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaFormatUtil.rdfFormatToJenaParsingLang

/**
 * Wraps a [[jena.riot.system.StreamRDF]] in a [[RdfStreamProcessor]].
 */
class StreamRDFAsStreamProcessor(streamRDF: jena.riot.system.StreamRDF) extends RdfStreamProcessor {

  import JenaConversions.*

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
    extends RdfFormatUtil {
  override def getRdfModelFactory: RdfModelFactory = modelFactory

  override def getRdfNodeFactory: RdfNodeFactory = nodeFactory

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
    import JenaConversions.*

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
    import JenaConversions.*

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

object JenaFormatUtil {
  def rdfFormatToJenaParsingLang(rdfFormat: NonJsonLD): jena.riot.Lang =
    rdfFormat match {
      case Turtle => jena.riot.RDFLanguages.TURTLE
      case TriG   => jena.riot.RDFLanguages.TRIG
      case RdfXml => jena.riot.RDFLanguages.RDFXML
      case NQuads => jena.riot.RDFLanguages.NQUADS
    }
}
