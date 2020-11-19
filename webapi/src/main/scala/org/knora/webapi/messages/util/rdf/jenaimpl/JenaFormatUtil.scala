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

package org.knora.webapi.messages.util.rdf.jenaimpl

import java.io.{InputStream, OutputStream, StringReader, StringWriter}

import org.apache.jena
import org.knora.webapi.IRI
import org.knora.webapi.feature.Feature
import org.knora.webapi.messages.util.rdf._

/**
 * Wraps an [[RdfStreamProcessor]] in a [[jena.riot.system.StreamRDF]].
 */
class StreamProcessorAsStreamRDF(streamProcessor: RdfStreamProcessor) extends jena.riot.system.StreamRDF {
    override def start(): Unit = streamProcessor.start()

    override def triple(triple: jena.graph.Triple): Unit = {
        streamProcessor.processStatement(
            JenaStatement(jena.sparql.core.Quad.create(jena.sparql.core.Quad.defaultGraphIRI, triple))
        )
    }

    override def quad(quad: jena.sparql.core.Quad): Unit = {
        streamProcessor.processStatement(JenaStatement(quad))
    }

    override def base(s: String): Unit = {}

    override def prefix(prefixStr: String, namespace: String): Unit = {
        streamProcessor.processNamespace(prefixStr, namespace)
    }

    override def finish(): Unit = streamProcessor.finish()
}

/**
 * Wraps a [[jena.riot.system.StreamRDF]] in a [[RdfStreamProcessor]].
 */
class StreamRDFAsStreamProcessor(streamRDF: jena.riot.system.StreamRDF) extends RdfStreamProcessor {

    import JenaConversions._

    override def start(): Unit = streamRDF.start()

    override def processNamespace(prefix: String, namespace: IRI): Unit = {
        streamRDF.prefix(prefix, namespace)
    }

    override def processStatement(statement: Statement): Unit = {
        streamRDF.quad(statement.asJenaQuad)
    }

    override def finish(): Unit = streamRDF.finish()
}

/**
 * An implementation of [[RdfFormatUtil]] that uses the Jena API.
 */
class JenaFormatUtil(private val modelFactory: JenaModelFactory) extends RdfFormatUtil with Feature {
    override def getRdfModelFactory: RdfModelFactory = modelFactory

    private def rdfFormatToJenaParsingLang(rdfFormat: NonJsonLD): jena.riot.Lang = {
        rdfFormat match {
            case Turtle => jena.riot.RDFLanguages.TURTLE
            case TriG => jena.riot.RDFLanguages.TRIG
            case RdfXml => jena.riot.RDFLanguages.RDFXML
        }
    }

    override def parseNonJsonLDToRdfModel(rdfStr: String, rdfFormat: NonJsonLD): RdfModel = {
        val jenaModel: JenaModel = modelFactory.makeEmptyModel

        jena.riot.RDFParser.create()
            .source(new StringReader(rdfStr))
            .lang(rdfFormatToJenaParsingLang(rdfFormat))
            .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
            .parse(jenaModel.getDataset)

        jenaModel
    }

    override def formatNonJsonLD(rdfModel: RdfModel, rdfFormat: NonJsonLD, prettyPrint: Boolean): String = {
        import JenaConversions._

        val datasetGraph: jena.sparql.core.DatasetGraph = rdfModel.asJenaDataset.asDatasetGraph
        val stringWriter: StringWriter = new StringWriter

        rdfFormat match {
            case Turtle =>
                val rdfFormat: jena.riot.RDFFormat = if (prettyPrint) {
                    jena.riot.RDFFormat.TURTLE_PRETTY
                } else {
                    jena.riot.RDFFormat.TURTLE_FLAT
                }

                jena.riot.RDFDataMgr.write(stringWriter, datasetGraph.getDefaultGraph, rdfFormat)

            case RdfXml =>
                val rdfFormat: jena.riot.RDFFormat = if (prettyPrint) {
                    jena.riot.RDFFormat.RDFXML_PRETTY
                } else {
                    jena.riot.RDFFormat.RDFXML_PLAIN
                }

                jena.riot.RDFDataMgr.write(stringWriter, datasetGraph.getDefaultGraph, rdfFormat)

            case TriG =>
                val rdfFormat: jena.riot.RDFFormat = if (prettyPrint) {
                    jena.riot.RDFFormat.TRIG_PRETTY
                } else {
                    jena.riot.RDFFormat.TRIG_FLAT
                }

                jena.riot.RDFDataMgr.write(stringWriter, datasetGraph, rdfFormat)
        }

        stringWriter.toString
    }

    override def parseToStream(rdfSource: RdfSource,
                               rdfFormat: NonJsonLD,
                               rdfStreamProcessor: RdfStreamProcessor): Unit = {
        // Wrap the RdfStreamProcessor in a StreamProcessorAsStreamRDF.
        val streamRDF = new StreamProcessorAsStreamRDF(rdfStreamProcessor)

        // Build a parser.
        val parser = jena.riot.RDFParser.create()

        // Configure it to read from the input source.
        rdfSource match {
            case RdfStringSource(rdfStr) => parser.source(new StringReader(rdfStr))
            case RdfInputStreamSource(inputStream) => parser.source(inputStream)
        }

        // Add the other configuration and run the parser.
        parser.lang(rdfFormatToJenaParsingLang(rdfFormat))
            .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
            .parse(streamRDF)
    }

    override def streamToRdfModel(inputStream: InputStream, rdfFormat: NonJsonLD): RdfModel = {
        val model: JenaModel = modelFactory.makeEmptyModel

        jena.riot.RDFDataMgr.read(
            model.getDataset.asDatasetGraph,
            inputStream,
            rdfFormatToJenaParsingLang(rdfFormat)
        )

        model
    }

    override def makeFormattingStreamProcessor(outputStream: OutputStream,
                                               rdfFormat: NonJsonLD): RdfStreamProcessor = {
        // Construct a Jena StreamRDF for the requested format.
        val streamRDF: jena.riot.system.StreamRDF = jena.riot.system.StreamRDFWriter.getWriterStream(
            outputStream,
            rdfFormatToJenaParsingLang(rdfFormat)
        )

        // Wrap it in a StreamRDFAsStreamProcessor.
        new StreamRDFAsStreamProcessor(streamRDF)
    }
}
