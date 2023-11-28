/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf.jenaimpl

import org.apache.jena

import java.io.InputStream
import java.io.StringReader
import java.io.StringWriter

import org.knora.webapi.IRI
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.messages.util.rdf.jenaimpl.JenaFormatUtil.rdfFormatToJenaParsingLang
import scala.util.Try

/**
 * Wraps a [[jena.riot.system.StreamRDF]] in a [[RdfStreamProcessor]].
 */
class StreamRDFAsStreamProcessor(streamRDF: jena.riot.system.StreamRDF) extends RdfStreamProcessor {

  override def start(): Unit = streamRDF.start()

  override def processNamespace(prefix: String, namespace: IRI): Unit =
    streamRDF.prefix(prefix, namespace)

  override def processStatement(statement: Statement): Unit =
    streamRDF.quad(JenaConversions.asJenaQuad(statement))

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

  override def inputStreamToRdfModel(inputStream: InputStream, rdfFormat: NonJsonLD): RdfModel = {
    val parseTry: Try[RdfModel] = Try {
      val model: JenaModel = modelFactory.makeEmptyModel
      jena.riot.RDFDataMgr.read(model.getDataset.asDatasetGraph, inputStream, rdfFormatToJenaParsingLang(rdfFormat))
      model
    }
    inputStream.close()
    parseTry.get
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
