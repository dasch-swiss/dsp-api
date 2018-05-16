/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.v2.responder

import org.knora.webapi._
import org.knora.webapi.util.jsonld._

/**
  *
  * A trait for Knora API V2 response messages. Any response can be converted into JSON or XML.
  *
  */
trait KnoraResponseV2 {

    /**
      * Converts the response to a data structure that can be used to generate JSON-LD.
      *
      * @param targetSchema the Knora API schema to be used in the JSON-LD document.
      * @return a [[JsonLDDocument]] representing the response.
      */
    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument
}

/**
  * Provides a message indicating that the result of an operation was successful.
  *
  * @param message the message to be returned.
  */
case class SuccessResponseV2(message: String) extends KnoraResponseV2 {
    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        val (ontologyPrefixExpansion, resultProp) = targetSchema match {
            case ApiV2Simple => (OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion, OntologyConstants.KnoraApiV2Simple.Result)
            case ApiV2WithValueObjects => (OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion, OntologyConstants.KnoraApiV2WithValueObjects.Result)
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
