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

import com.github.jsonldjava.core.{JsonLdOptions, JsonLdProcessor}
import com.github.jsonldjava.utils.JsonUtils
import org.knora.webapi.util.{JavaUtil, SmartIri}
import org.knora.webapi.{BadRequestException, IRI}

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
  * Represents a string value in a JSON-LD document. Note: this type should also be used for xsd:decimal, to
  * ensure that precision is not lost. See [[http://ontology2.com/the-book/decimal-the-missing-datatype.html]].
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
case class JsonLDObject(value: Map[String, JsonLDValue]) extends JsonLDValue {
    override def toAny: Map[String, Any] = value.map {
        case (k, v) => (k, v.toAny)
    }

    /**
      * Gets a required string value of a property of this JSON-LD object, throwing
      * [[BadRequestException]] if the property is not found or if its value is not a string.
      * Then parses the value with the specified validation function (see [[org.knora.webapi.util.StringFormatter]]
      * for examples of such functions), throwing [[BadRequestException]] if the validation fails.
      *
      * @param key the key of the required value.
      * @param validationFun a validation function that takes two arguments: the string to be validated, and a function
      *                      that throws an exception if the string is invalid. The function's return value is the
      *                      validated string, possibly converted to another type T.
      * @tparam T the type of the validation function's return value.
      * @return the return value of the validation function.
      */
    def requireString[T](key: String, validationFun: (String, => Nothing) => T ): T = {
        value.getOrElse(key, throw BadRequestException(s"No $key provided")) match {
            case JsonLDString(str) => validationFun(str, throw BadRequestException(s"Invalid $key: $str"))
            case other => throw BadRequestException(s"Invalid $key: $other")
        }
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
  * @param body    the body of the JSON-LD document.
  * @param context the context of the JSON-LD document.
  */
case class JsonLDDocument(body: JsonLDObject, context: JsonLDObject) {
    /**
      * A convenience function that calls `body.requireString`.
      */
    def requireString[T](key: String, validationFun: (String, => Nothing) => T ): T = body.requireString(key, validationFun)
}


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

    /**
      * Parses a JSON-LD string as a [[JsonLDDocument]] with an empty context.
      *
      * @param jsonLDString the string to be parsed.
      * @return a [[JsonLDDocument]].
      */
    def parseJsonLD(jsonLDString: String): JsonLDDocument = {
        val jsonObject: AnyRef = JsonUtils.fromString(jsonLDString)
        val context: java.util.HashMap[String, Any] = new java.util.HashMap[String, Any]()
        val options: JsonLdOptions = new JsonLdOptions()
        val compact: java.util.Map[IRI, AnyRef] = JsonLdProcessor.compact(jsonObject, context, options)
        val scalaColl: Any = JavaUtil.deepJavatoScala(compact)

        val scalaMap: Map[String, Any] = try {
            scalaColl.asInstanceOf[Map[String, Any]]
        } catch {
            case _: java.lang.ClassCastException => throw BadRequestException(s"Expected JSON-LD object: $scalaColl")
        }

        mapToJsonLDDocument(scalaMap)
    }

    /**
      * Converts a map into a [[JsonLDDocument]].
      *
      * @param docContent a map representing a JSON-LD object.
      * @return
      */
    private def mapToJsonLDDocument(docContent: Map[String, Any]): JsonLDDocument = {
        def anyToJsonLDValue(anyVal: Any): JsonLDValue = {
            anyVal match {
                case string: String => JsonLDString(string)
                case int: Int => JsonLDInt(int)
                case bool: Boolean => JsonLDBoolean(bool)

                case obj: Map[_, _] =>
                    val content: Map[String, JsonLDValue] = obj.map {
                        case (key: String, value: Any) => key -> anyToJsonLDValue(value)
                        case (otherKey, otherValue) => throw BadRequestException(s"Unexpected types in JSON-LD object: $otherKey, $otherValue")
                    }

                    JsonLDObject(content)

                case array: Seq[Any] => JsonLDArray(array.map(value => anyToJsonLDValue(value)))

                case _ => throw BadRequestException(s"Unexpected type in JSON-LD input: $anyVal")
            }
        }

        anyToJsonLDValue(docContent) match {
            case obj: JsonLDObject => JsonLDDocument(body = obj, context = JsonLDObject(Map.empty[IRI, JsonLDValue]))
            case _ => throw BadRequestException(s"Expected JSON-LD object: $docContent")
        }
    }
}
