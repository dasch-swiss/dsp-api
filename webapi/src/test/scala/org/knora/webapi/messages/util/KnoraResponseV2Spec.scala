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

package org.knora.webapi.util

import java.io.{File, StringReader}

import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}
import org.knora.webapi.messages.util.{JsonLDDocument, JsonLDUtil}
import org.knora.webapi.messages.v2.responder.{KnoraJsonLDResponseV2, KnoraTurtleResponseV2}
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi._

/**
 * Tests the formatting of Knora API v2 responses.
 */
class KnoraResponseV2Spec extends CoreSpec {

    /**
     * A test implementation of [[KnoraTurtleResponseV2]].
     */
    case class TurtleTestMessage(turtle: String) extends KnoraTurtleResponseV2

    /**
     * A test implementation of [[KnoraJsonLDResponseV2]].
     */
    case class JsonLDTestMessage(jsonLD: String) extends KnoraJsonLDResponseV2 {
        override protected def toJsonLDDocument(targetSchema: ApiV2Schema,
                                                settings: KnoraSettingsImpl,
                                                schemaOptions: Set[SchemaOption]): JsonLDDocument = {
            JsonLDUtil.parseJsonLD(jsonLD)
        }
    }

    "KnoraResponseV2" should {
        "convert Turtle to JSON-LD" in {
            // Read a Turtle file representing a resource.
            val turtle: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.ttl"))

            // Wrap it in a KnoraTurtleResponseV2.
            val turtleTestMessage = TurtleTestMessage(turtle)

            // Ask the KnoraTurtleResponseV2 to convert the content to JSON-LD.
            val jsonLD: String = turtleTestMessage.format(
                mediaType = RdfMediaTypes.`application/ld+json`,
                targetSchema = ApiV2Complex,
                schemaOptions = Set.empty,
                settings = settings
            )

            // Parse the JSON-LD to a JsonLDDocument.
            val parsedJsonLD = JsonLDUtil.parseJsonLD(jsonLD)

            // Read an isomorphic JSON-LD file and parse it to a JsonLDDocument.
            val expectedJsonLD = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"))
            val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedJsonLD)

            // Compare the two documents.
            parsedJsonLD.body should ===(parsedExpectedJsonLD.body)
        }

        "convert JSON-LD to Turtle" in {
            // Read a JSON-LD file representing a resource.
            val jsonLD: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"))

            // Wrap it in a KnoraJsonLDResponseV2.
            val jsonLDTestMessage = JsonLDTestMessage(jsonLD)

            // Ask the KnoraJsonLDResponseV2 to convert the content to Turtle.
            val turtle: String = jsonLDTestMessage.format(
                mediaType = RdfMediaTypes.`text/turtle`,
                targetSchema = ApiV2Complex,
                schemaOptions = Set.empty,
                settings = settings
            )

            // Parse the Turtle to an RDF4J Model.
            val parsedTurtle = Rio.parse(new StringReader(turtle), "", RDFFormat.TURTLE, null)

            // Read an isomorphic Turtle file and parse it to an RDF4J Model.
            val expectedTurtle: String = FileUtil.readTextFile(new File("test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.ttl"))
            val parsedExpectedTurtle: Model = Rio.parse(new StringReader(expectedTurtle), "", RDFFormat.TURTLE, null)

            // Compare the two models.
            parsedTurtle should ===(parsedExpectedTurtle)
        }
    }
}
