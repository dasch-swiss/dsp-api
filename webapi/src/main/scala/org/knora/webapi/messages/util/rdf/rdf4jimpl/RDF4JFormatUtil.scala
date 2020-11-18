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

package org.knora.webapi.messages.util.rdf.rdf4jimpl

import java.io.{InputStream, OutputStream, StringReader, StringWriter}

import org.eclipse.rdf4j
import org.knora.webapi.IRI
import org.knora.webapi.feature.Feature
import org.knora.webapi.messages.util.rdf._

/**
 * Wraps an [[RdfStreamProcessor]] in an [[rdf4j.rio.RDFHandler]].
 */
class StreamProcessorAsRDFHandler(streamProcessor: RdfStreamProcessor) extends rdf4j.rio.RDFHandler {
    override def startRDF(): Unit = streamProcessor.start()

    override def endRDF(): Unit = streamProcessor.finish()

    override def handleNamespace(prefix: String, namespace: String): Unit = {
        streamProcessor.handleNamespace(prefix, namespace)
    }

    override def handleStatement(statement: rdf4j.model.Statement): Unit = {
        streamProcessor.handleStatement(RDF4JStatement(statement))
    }

    override def handleComment(comment: String): Unit = {}
}

/**
 * Wraps an [[rdf4j.rio.RDFHandler]] in an [[RdfStreamProcessor]].
 */
class RDFHandlerAsStreamProcessor(rdfWriter: rdf4j.rio.RDFHandler) extends RdfStreamProcessor {

    import RDF4JConversions._

    override def start(): Unit = rdfWriter.startRDF()

    override def handleNamespace(prefix: String, namespace: IRI): Unit = {
        rdfWriter.handleNamespace(prefix, namespace)
    }

    override def handleStatement(statement: Statement): Unit = {
        rdfWriter.handleStatement(statement.asRDF4JStatement)
    }

    override def finish(): Unit = rdfWriter.endRDF()
}

/**
 * An implementation of [[RdfFormatUtil]] that uses the RDF4J API.
 */
class RDF4JFormatUtil(private val modelFactory: RDF4JModelFactory,
                      private val nodeFactory: RDF4JNodeFactory) extends RdfFormatUtil with Feature {
    override def getRdfModelFactory: RdfModelFactory = modelFactory

    private def rdfFormatToRDF4JFormat(rdfFormat: NonJsonLD): rdf4j.rio.RDFFormat = {
        rdfFormat match {
            case Turtle => rdf4j.rio.RDFFormat.TURTLE
            case TriG => rdf4j.rio.RDFFormat.TRIG
            case RdfXml => rdf4j.rio.RDFFormat.RDFXML
        }
    }

    override def parseNonJsonLDToRdfModel(rdfStr: String, rdfFormat: NonJsonLD): RdfModel = {
        new RDF4JModel(
            model = rdf4j.rio.Rio.parse(
                new StringReader(rdfStr),
                "",
                rdfFormatToRDF4JFormat(rdfFormat)
            ),
            nodeFactory = nodeFactory
        )
    }

    override def formatNonJsonLD(rdfModel: RdfModel, rdfFormat: NonJsonLD, prettyPrint: Boolean): String = {
        import RDF4JConversions._

        val stringWriter = new StringWriter

        val rdfWriter: rdf4j.rio.RDFWriter = rdfFormat match {
            case Turtle => rdf4j.rio.Rio.createWriter(rdf4j.rio.RDFFormat.TURTLE, stringWriter)
            case TriG => rdf4j.rio.Rio.createWriter(rdf4j.rio.RDFFormat.TRIG, stringWriter)
            case RdfXml => new rdf4j.rio.rdfxml.util.RDFXMLPrettyWriter(stringWriter)
        }

        // Configure the RDFWriter.
        rdfWriter.getWriterConfig.
            set[java.lang.Boolean](rdf4j.rio.helpers.BasicWriterSettings.INLINE_BLANK_NODES, true).
            set[java.lang.Boolean](rdf4j.rio.helpers.BasicWriterSettings.PRETTY_PRINT, true)

        // Format the RDF.
        rdf4j.rio.Rio.write(rdfModel.asRDF4JModel, rdfWriter)
        stringWriter.toString
    }

    override def parseToStream(inputStream: InputStream,
                               rdfFormat: NonJsonLD,
                               rdfStreamProcessor: RdfStreamProcessor): Unit = {
        // Construct an RDF4J parser for the requested format.
        val parser: rdf4j.rio.RDFParser = rdf4j.rio.Rio.createParser(rdfFormatToRDF4JFormat(rdfFormat))

        // Wrap the RdfStreamProcessor in a StreamProcessorAsRDFHandler and set it as the parser's RDFHandler.
        parser.setRDFHandler(new StreamProcessorAsRDFHandler(rdfStreamProcessor))

        // Parse the input.
        parser.parse(inputStream, "")
    }

    override def makeFormattingStreamProcessor(outputStream: OutputStream,
                                               rdfFormat: NonJsonLD): RdfStreamProcessor = {
        // Construct an RDF4J writer for the requested format.
        val rdfWriter: rdf4j.rio.RDFWriter = rdf4j.rio.Rio.createWriter(rdfFormatToRDF4JFormat(rdfFormat), outputStream)

        // Wrap it in an RDFHandlerAsStreamProcessor.
        new RDFHandlerAsStreamProcessor(rdfWriter)
    }
}
