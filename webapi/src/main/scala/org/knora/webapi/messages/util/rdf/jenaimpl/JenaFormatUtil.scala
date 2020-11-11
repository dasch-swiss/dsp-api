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

import java.io.{StringReader, StringWriter}

import org.apache.jena
import org.knora.webapi.feature.Feature
import org.knora.webapi.messages.util.rdf._

/**
 * An implementation of [[RdfFormatUtil]] that uses the Jena API.
 */
class JenaFormatUtil extends RdfFormatUtil with Feature {
    override def parseToRdfModel(rdfStr: String, rdfFormat: RdfFormat): RdfModel = {
        val jenaModel: JenaModel = JenaModelFactory.makeEmptyModel

        val parsingLang: jena.riot.Lang = rdfFormat match {
            case JsonLD => jena.riot.RDFLanguages.JSONLD
            case Turtle => jena.riot.RDFLanguages.TURTLE
            case TriG => jena.riot.RDFLanguages.TRIG
            case RdfXml => jena.riot.RDFLanguages.RDFXML
        }

        jena.riot.RDFParser.create()
            .source(new StringReader(rdfStr))
            .lang(parsingLang)
            .errorHandler(jena.riot.system.ErrorHandlerFactory.errorHandlerStrictNoLogging)
            .parse(jenaModel.getDataset)

        jenaModel
    }

    override def formatNonJsonLD(rdfModel: RdfModel, rdfFormat: NonJsonLD, prettyPrint: Boolean): String = {
        import JenaConversions._

        val jenaFormat: jena.riot.RDFFormat = rdfFormat match {
            case Turtle =>
                if (prettyPrint) {
                    jena.riot.RDFFormat.TURTLE_PRETTY
                } else {
                    jena.riot.RDFFormat.TURTLE_FLAT
                }

            case TriG =>
                if (prettyPrint) {
                    jena.riot.RDFFormat.TRIG_PRETTY
                } else {
                    jena.riot.RDFFormat.TRIG_FLAT
                }

            case RdfXml =>
                if (prettyPrint) {
                    jena.riot.RDFFormat.RDFXML_PRETTY
                } else {
                    jena.riot.RDFFormat.RDFXML_PLAIN
                }
        }

        val stringWriter: StringWriter = new StringWriter
        jena.riot.RDFDataMgr.write(stringWriter, rdfModel.asJenaDataset, jenaFormat)
        stringWriter.toString
    }
}
