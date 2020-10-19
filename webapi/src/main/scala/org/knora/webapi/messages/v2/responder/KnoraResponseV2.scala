/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.v2.responder

import java.io.{StringReader, StringWriter}

import akka.http.scaladsl.model.MediaType
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings
import org.eclipse.rdf4j.rio.rdfxml.util.RDFXMLPrettyWriter
import org.eclipse.rdf4j.rio.{RDFFormat, RDFWriter, Rio}
import org.knora.webapi._
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.util.{JsonLDDocument, JsonLDObject, JsonLDString, JsonLDUtil}
import org.knora.webapi.settings.KnoraSettingsImpl


/**
 * A trait for Knora API V2 response messages.
 */
trait KnoraResponseV2 {
    /**
     * Returns this response message in the requested format.
     *
     * @param mediaType     the specific media type selected for the response.
     * @param targetSchema  the response schema.
     * @param schemaOptions the schema options.
     * @param settings      the application settings.
     * @return a formatted string representing this response message.
     */
    def format(mediaType: MediaType.NonBinary,
               targetSchema: ApiV2Schema,
               schemaOptions: Set[SchemaOption],
               settings: KnoraSettingsImpl): String
}

/**
 * A trait for Knora API V2 response messages that are constructed as JSON-LD documents.
 */
trait KnoraJsonLDResponseV2 extends KnoraResponseV2 {

    override def format(mediaType: MediaType.NonBinary,
                        targetSchema: ApiV2Schema,
                        schemaOptions: Set[SchemaOption],
                        settings: KnoraSettingsImpl): String = {
        // Convert this response message to a JSON-LD document.
        val jsonLDDocument: JsonLDDocument = toJsonLDDocument(
            targetSchema = targetSchema,
            settings = settings,
            schemaOptions = schemaOptions
        )

        // Which response format was requested?
        mediaType match {
            case RdfMediaTypes.`application/ld+json` =>
                // JSON-LD. Convert the document to a string in JSON-LD format.
                jsonLDDocument.toPrettyString

            case _ =>
                // Some other format. Convert the document to an RDF4J Model,
                // and return the model in the requested format.
                RdfResponseFormatter.formatResponse(
                    model = jsonLDDocument.toRDF4JModel,
                    mediaType = mediaType
                )
        }
    }

    /**
     * Converts the response to a data structure that can be used to generate JSON-LD.
     *
     * @param targetSchema the Knora API schema to be used in the JSON-LD document.
     * @return a [[JsonLDDocument]] representing the response.
     */
    protected def toJsonLDDocument(targetSchema: ApiV2Schema, settings: KnoraSettingsImpl, schemaOptions: Set[SchemaOption]): JsonLDDocument
}

/**
 * A trait for Knora API V2 response messages that are constructed as
 * strings in Turtle format.
 */
trait KnoraTurtleResponseV2 extends KnoraResponseV2 {
    /**
     * A string containing RDF data in Turtle format.
     */
    protected val turtle: String

    override def format(mediaType: MediaType.NonBinary,
                        targetSchema: ApiV2Schema,
                        schemaOptions: Set[SchemaOption],
                        settings: KnoraSettingsImpl): String = {
        // Which response format was requested?
        mediaType match {
            case RdfMediaTypes.`text/turtle` =>
                // Turtle. Return the Turtle string as is.
                turtle

            case _ =>
                // Some other format. Parse the Turtle to an RDF4J Model.
                val model = Rio.parse(new StringReader(turtle), "", RDFFormat.TURTLE, null)

                // Return the model in the requested format.
                RdfResponseFormatter.formatResponse(
                    model = model,
                    mediaType = mediaType
                )
        }
    }
}

/**
 * A utility for formatting RDF responses.
 */
object RdfResponseFormatter {
    /**
     * Formats RDF as an API response in the requested media type.
     *
     * @param model         an RDF4J model.
     * @param mediaType     the specific media type selected for the response.
     * @return the model formatted in the specified format.
     */
    def formatResponse(model: Model,
                       mediaType: MediaType.NonBinary): String = {
        // A StringWriter to collect the formatted output.
        val stringWriter = new StringWriter()

        mediaType match {
            case RdfMediaTypes.`application/ld+json` =>
                // Use our own conversion.
                JsonLDUtil.fromRDF4JModel(model).toPrettyString

            case _ =>
                // Construct an RDFWriter for the specified format.
                val rdfWriter: RDFWriter = mediaType match {
                    case RdfMediaTypes.`text/turtle` => Rio.createWriter(RDFFormat.TURTLE, stringWriter)
                    case RdfMediaTypes.`application/rdf+xml` => new RDFXMLPrettyWriter(stringWriter)
                    case other => throw BadRequestException(s"Unsupported media type: $other")
                }

                // Configure the RDFWriter.
                rdfWriter.getWriterConfig.set[java.lang.Boolean](BasicWriterSettings.INLINE_BLANK_NODES, true).
                    set[java.lang.Boolean](BasicWriterSettings.PRETTY_PRINT, true)

                // Format the RDF.
                Rio.write(model, rdfWriter)
                stringWriter.toString
        }
    }
}

/**
 * Provides a message indicating that the result of an operation was successful.
 *
 * @param message the message to be returned.
 */
case class SuccessResponseV2(message: String) extends KnoraJsonLDResponseV2 {
    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: KnoraSettingsImpl, schemaOptions: Set[SchemaOption]): JsonLDDocument = {
        val (ontologyPrefixExpansion, resultProp) = targetSchema match {
            case ApiV2Simple => (OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion, OntologyConstants.KnoraApiV2Simple.Result)
            case ApiV2Complex => (OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion, OntologyConstants.KnoraApiV2Complex.Result)
        }

        JsonLDDocument(
            body = JsonLDObject(
                Map(resultProp -> JsonLDString(message))
            ),
            context = JsonLDObject(
                Map(OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(ontologyPrefixExpansion))
            )
        )
    }
}

/**
 * A trait for content classes that can convert themselves between internal and internal schemas.
 *
 * @tparam C the type of the content class that extends this trait.
 */
trait KnoraContentV2[C <: KnoraContentV2[C]] {
    this: C =>
    def toOntologySchema(targetSchema: OntologySchema): C
}

/**
 * A trait for read wrappers that can convert themselves to external schemas.
 *
 * @tparam C the type of the read wrapper that extends this trait.
 */
trait KnoraReadV2[C <: KnoraReadV2[C]] {
    this: C =>
    def toOntologySchema(targetSchema: ApiV2Schema): C
}


/**
 * Allows the successful result of an update operation to indicate which project was updated.
 */
trait UpdateResultInProject {
    /**
     * The project that was updated.
     */
    def projectADM: ProjectADM
}
