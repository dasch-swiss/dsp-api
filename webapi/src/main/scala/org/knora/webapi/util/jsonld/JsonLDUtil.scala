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

/**
  * Represents a value in a JSON-LD document.
  */
sealed trait JsonLDValue {
    /**
      * Converts this JSON-LD value to a Scala object that can be passed to [[org.knora.webapi.util.JavaUtil.deepScalaToJava]],
      * whose return value can then be passed to the JSON-LD Java library.
      */
    def toAny: Any
}

/**
  * Represents a string value in a JSON-LD document.
  *
  * @param value the underlying string.
  */
case class JsonLDString(value: String) extends JsonLDValue {
    override def toAny: Any = value
}

/**
  * Represents an integer value in a JSON-LD document.
  *
  * @param value the underlying integer.
  */
case class JsonLDInt(value: Int) extends JsonLDValue {
    override def toAny: Any = value
}

/**
  * Represents a decimal value in a JSON-LD document.
  *
  * @param value the underlying decimal value.
  */
case class JsonLDDecimal(value: BigDecimal) extends JsonLDValue {
    override def toAny: Any = value
}

/**
  * Represents a boolean value in a JSON-LD document.
  *
  * @param value the underlying boolean value.
  */
case class JsonLDBoolean(value: Boolean) extends JsonLDValue {
    override def toAny: Any = value
}

/**
  * Represents a JSON object in a JSON-LD document.
  *
  * @param value a map of keys to JSON-LD values.
  */
case class JsonLDObject(value: Map[IRI, JsonLDValue]) extends JsonLDValue {
    override def toAny: Map[IRI, Any] = value.map {
        case (k, v) => (k, v.toAny)
    }
}

/**
  * Represents a JSON array in a JSON-LD document.
  *
  * @param value a sequence of JSON-LD values.
  */
case class JsonLDArray(value: Seq[JsonLDValue]) extends JsonLDValue {
    override def toAny: Seq[Any] = value.map(_.toAny)
}

/**
  * Represents a JSON-LD document.
  *
  * @param body the body of the JSON-LD document.
  * @param context the context of the JSON-LD document.
  */
case class JsonLDDocument(body: JsonLDObject, context: JsonLDObject)


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
