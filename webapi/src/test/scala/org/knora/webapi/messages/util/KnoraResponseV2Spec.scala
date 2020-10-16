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

import java.io.File

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.responder.KnoraTurtleResponseV2
import org.knora.webapi.{ApiV2Complex, CoreSpec, RdfMediaTypes}
import spray.json._

/**
 * Tests the formatting of Knora API v2 responses.
 */
class KnoraResponseV2Spec extends CoreSpec {
    case class TurtleTestMessage(turtle: String) extends KnoraTurtleResponseV2

    StringFormatter.initForTest()

    "KnoraResponseV2" should {
        // Ignored because I can't get RDF4J to inline blank nodes in JSON-LD output
        // (see the comment in RdfResponseFormatter).
        "convert Turtle to JSON-LD" ignore {
            val turtle = FileUtil.readTextFile(new File("test_data/ontologyR2RV2/anythingOntologyWithValueObjects.ttl"))
            val turtleTestMessage = TurtleTestMessage(turtle)

            val jsonLD: String = turtleTestMessage.format(
                mediaType = RdfMediaTypes.`application/ld+json`,
                targetSchema = ApiV2Complex,
                schemaOptions = Set.empty,
                settings = settings
            )

            val expectedJsonLD = FileUtil.readTextFile(new File("test_data/ontologyR2RV2/anythingOntologyWithValueObjects.jsonld"))

            val receivedOutputAsJsValue: JsValue = JsonParser(jsonLD)
            val expectedOutputAsJsValue: JsValue = JsonParser(expectedJsonLD)
            receivedOutputAsJsValue should ===(expectedOutputAsJsValue)
        }
    }
}
