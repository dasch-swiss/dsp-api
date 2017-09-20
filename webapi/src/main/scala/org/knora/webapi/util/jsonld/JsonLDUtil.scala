/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.util.jsonld

import org.knora.webapi.IRI

sealed trait JsonLDValue {
    def toJsonLDApiValue: Any
}

case class JsonLDString(value: String) extends JsonLDValue {
    override def toJsonLDApiValue: Any = value
}

case class JsonLDInt(value: Int) extends JsonLDValue {
    override def toJsonLDApiValue: Any = value
}

case class JsonLDDecimal(value: BigDecimal) extends JsonLDValue {
    override def toJsonLDApiValue: Any = value
}

case class JsonLDBoolean(value: Boolean) extends JsonLDValue {
    override def toJsonLDApiValue: Any = value
}

case class JsonLDObject(value: Map[IRI, JsonLDValue]) extends JsonLDValue {
    override def toJsonLDApiValue: Map[IRI, Any] = value.map {
        case (k, v) => (k, v.toJsonLDApiValue)
    }
}

case class JsonLDArray(value: Seq[JsonLDValue]) extends JsonLDValue {
    override def toJsonLDApiValue: Seq[Any] = value.map(_.toJsonLDApiValue)
}

case class JsonLDDocument(body: JsonLDObject, context: JsonLDObject = JsonLDObject(Map.empty[IRI, JsonLDValue]))


/**
  * Utility functions for working with JSON-LD.
  */
object JsonLDUtil {
    /**
      * Given a map of language codes to predicate values, returns a JSON-LD array in which each element
      * has a `@value` predicate and a `@language` predicate.
      *
      * @param objectsWithLangs a map of language codes to predicate values.
      * @return a JSON-LD array in which each element has a `@value` predicate and a `@language` predicate.
      */
    def objectsWithLangsToJsonLDArray(objectsWithLangs: Map[String, String]): JsonLDArray = {
        val objects: Seq[JsonLDObject] = objectsWithLangs.toSeq.map {
            case (lang, obj) => JsonLDObject(Map(
                "@value" -> JsonLDString(obj),
                "@language" -> JsonLDString(lang)
            ))
        }

        JsonLDArray(objects)
    }
}
