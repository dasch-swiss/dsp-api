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

package org.knora.webapi.util.jsonld

import com.github.jsonldjava.core.{JsonLdOptions, JsonLdProcessor}
import com.github.jsonldjava.utils.JsonUtils
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.util.{JavaUtil, StringFormatter}
import org.knora.webapi.{BadRequestException, IRI, LanguageCodes}

/**
  * Represents a value in a JSON-LD document.
  */
sealed trait JsonLDValue extends Ordered[JsonLDValue] {
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

    override def compare(that: JsonLDValue): Int = {
        that match {
            case thatStr: JsonLDString => value.compare(thatStr.value)
            case _ => 0
        }
    }
}

/**
  * Represents an integer value in a JSON-LD document.
  *
  * @param value the underlying integer.
  */
case class JsonLDInt(value: Int) extends JsonLDValue {
    override def toAny: Any = value

    override def compare(that: JsonLDValue): Int = {
        that match {
            case thatInt: JsonLDInt => value.compare(thatInt.value)
            case _ => 0
        }
    }
}

/**
  * Represents a boolean value in a JSON-LD document.
  *
  * @param value the underlying boolean value.
  */
case class JsonLDBoolean(value: Boolean) extends JsonLDValue {
    override def toAny: Any = value

    override def compare(that: JsonLDValue): Int = {
        that match {
            case thatBoolean: JsonLDBoolean => value.compare(thatBoolean.value)
            case _ => 0
        }
    }
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
    def requireString[T](key: String, validationFun: (String, => Nothing) => T): T = {
        value.getOrElse(key, throw BadRequestException(s"No $key provided")) match {
            case JsonLDString(str) => validationFun(str, throw BadRequestException(s"Invalid $key: $str"))
            case other => throw BadRequestException(s"Invalid $key: $other (string expected)")
        }
    }

    /**
      * Gets an optional string value of a property of this JSON-LD object, throwing
      * [[BadRequestException]] if the property's value is not a string. Parses the value with the specified validation
      * function (see [[org.knora.webapi.util.StringFormatter]] for examples of such functions), throwing
      * [[BadRequestException]] if the validation fails.
      *
      * @param key the key of the optional value.
      * @param validationFun a validation function that takes two arguments: the string to be validated, and a function
      *                      that throws an exception if the string is invalid. The function's return value is the
      *                      validated string, possibly converted to another type T.
      * @tparam T the type of the validation function's return value.
      * @return the return value of the validation function, or `None` if the value was not present.
      */
    def maybeString[T](key: String, validationFun: (String, => Nothing) => T): Option[T] = {
        value.get(key).map {
            case JsonLDString(str) => validationFun(str, throw BadRequestException(s"Invalid $key: $str"))
            case other => throw BadRequestException(s"Invalid $key: $other (string expected)")
        }
    }

    /**
      * Gets a required IRI value (contained in a JSON-LD object) of a property of this JSON-LD object, throwing
      * [[BadRequestException]] if the property is not found or if its value is not a JSON-LD object.
      * Then parses the object's ID with the specified validation function (see [[org.knora.webapi.util.StringFormatter]]
      * for examples of such functions), throwing [[BadRequestException]] if the validation fails.
      *
      * @param key the key of the required value.
      * @return the validated IRI.
      */
    def requireIriInObject[T](key: String, validationFun: (String, => Nothing) => T): T = {
        JsonLDUtil.iriFromJsonLDObject(requireObject(key), validationFun)
    }

    /**
      * Gets an optional IRI value (contained in a JSON-LD object) value of a property of this JSON-LD object, throwing
      * [[BadRequestException]] if the property's value is not a JSON-LD object. Parses the object's ID with the
      * specified validation function (see [[org.knora.webapi.util.StringFormatter]] for examples of such functions),
      * throwing [[BadRequestException]] if the validation fails.
      *
      * @param key the key of the optional value.
      * @param validationFun a validation function that takes two arguments: the string to be validated, and a function
      *                      that throws an exception if the string is invalid. The function's return value is the
      *                      validated string, possibly converted to another type T.
      * @tparam T the type of the validation function's return value.
      * @return the return value of the validation function, or `None` if the value was not present.
      */
    def maybeIriInObject[T](key: String, validationFun: (String, => Nothing) => T): Option[T] = {
        maybeObject(key).map(obj => JsonLDUtil.iriFromJsonLDObject(obj, validationFun))
    }

    /**
      * Gets the required object value of this JSON-LD object, throwing
      * [[BadRequestException]] if the property is not found or if its value is not an object.
      *
      * @param key the key of the required value.
      * @return the required value.
      */
    def requireObject(key: String): JsonLDObject = {
        value.getOrElse(key, throw BadRequestException(s"No $key provided")) match {
            case obj: JsonLDObject => obj
            case other => throw BadRequestException(s"Invalid $key: $other (object expected)")
        }
    }

    /**
      * Gets the optional object value of this JSON-LD object, throwing
      * [[BadRequestException]] if the property's value is not an object.
      *
      * @param key the key of the optional value.
      * @return the optional value.
      */
    def maybeObject(key: String): Option[JsonLDObject] = {
        value.get(key).map {
            case obj: JsonLDObject => obj
            case other => throw BadRequestException(s"Invalid $key: $other (object expected)")
        }
    }

    /**
      * Gets the required array value of this JSON-LD object. If the value is not an array,
      * returns a one-element array containing the value. Throws
      * [[BadRequestException]] if the property is not found.
      *
      * @param key the key of the required value.
      * @return the required value.
      */
    def requireArray(key: String): JsonLDArray = {
        value.getOrElse(key, throw BadRequestException(s"No $key provided")) match {
            case obj: JsonLDArray => obj
            case other => JsonLDArray(Seq(other))
        }
    }


    /**
      * Gets the optional array value of this JSON-LD object. If the value is not an array,
      * returns a one-element array containing the value.
      *
      * @param key the key of the optional value.
      * @return the optional value.
      */
    def maybeArray(key: String): Option[JsonLDArray] = {
        value.get(key).map {
            case obj: JsonLDArray => obj
            case other => JsonLDArray(Seq(other))
        }
    }

    /**
      * Gets the required integer value of this JSON-LD object, throwing
      * [[BadRequestException]] if the property is not found or if its value is not an integer.
      *
      * @param key the key of the required value.
      * @return the required value.
      */
    def requireInt(key: String): JsonLDInt = {
        value.getOrElse(key, throw BadRequestException(s"No $key provided")) match {
            case obj: JsonLDInt => obj
            case other => throw BadRequestException(s"Invalid $key: $other (integer expected)")
        }
    }

    /**
      * Gets the optional integer value of this JSON-LD object, throwing
      * [[BadRequestException]] if the property's value is not an integer.
      *
      * @param key the key of the optional value.
      * @return the optional value.
      */
    def maybeInt(key: String): Option[JsonLDInt] = {
        value.get(key).map {
            case obj: JsonLDInt => obj
            case other => throw BadRequestException(s"Invalid $key: $other (integer expected)")
        }
    }

    /**
      * Gets the required boolean value of this JSON-LD object, throwing
      * [[BadRequestException]] if the property is not found or if its value is not a boolean.
      *
      * @param key the key of the required value.
      * @return the required value.
      */
    def requireBoolean(key: String): JsonLDBoolean = {
        value.getOrElse(key, throw BadRequestException(s"No $key provided")) match {
            case obj: JsonLDBoolean => obj
            case other => throw BadRequestException(s"Invalid $key: $other (boolean expected)")
        }
    }

    /**
      * Gets the optional boolean value of this JSON-LD object, throwing
      * [[BadRequestException]] if the property's value is not a boolean.
      *
      * @param key the key of the optional value.
      * @return the optional value.
      */
    def maybeBoolean(key: String): Option[JsonLDBoolean] = {
        value.get(key).map {
            case obj: JsonLDBoolean => obj
            case other => throw BadRequestException(s"Invalid $key: $other (boolean expected)")
        }
    }

    override def compare(that: JsonLDValue): Int = 0
}

/**
  * Represents a JSON array in a JSON-LD document.
  *
  * @param value a sequence of JSON-LD values.
  */
case class JsonLDArray(value: Seq[JsonLDValue]) extends JsonLDValue {
    implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    override def toAny: Seq[Any] = value.map(_.toAny)

    /**
      * Tries to interpret the elements of this array as JSON-LD objects containing `@language` and `@value`,
      * and returns the results as a set of [[StringLiteralV2]]. Throws [[BadRequestException]]
      * if the array can't be interpreted in this way.
      *
      * @return a map of language keys to values.
      */
    def toObjsWithLang: Seq[StringLiteralV2] = {
        value.map {
            case obj: JsonLDObject =>
                val lang = obj.requireString("@language", stringFormatter.toSparqlEncodedString)

                if (!LanguageCodes.SupportedLanguageCodes(lang)) {
                    throw BadRequestException(s"Unsupported language code: $lang")
                }

                val text = obj.requireString("@value", stringFormatter.toSparqlEncodedString)
                StringLiteralV2(text, Some(lang))

            case other => throw BadRequestException(s"Expected JSON-LD object: $other")
        }
    }

    override def compare(that: JsonLDValue): Int = 0
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
    def requireString[T](key: String, validationFun: (String, => Nothing) => T): T = body.requireString(key, validationFun)

    /**
      * A convenience function that calls `body.maybeString`.
      */
    def maybeString[T](key: String, validationFun: (String, => Nothing) => T): Option[T] = body.maybeString(key, validationFun)

    /**
      * A convenience function that calls `body.requireIriInObject`.
      */
    def requireIriInObject[T](key: String, validationFun: (String, => Nothing) => T): T = body.requireIriInObject(key, validationFun)

    /**
      * A convenience function that calls `body.maybeIriInObject`.
      */
    def maybeIriInObject[T](key: String, validationFun: (String, => Nothing) => T): Option[T] = body.maybeIriInObject(key, validationFun)

    /**
      * A convenience function that calls `body.requireObject`.
      */
    def requireObject(key: String): JsonLDObject = body.requireObject(key)

    /**
      * A convenience function that calls `body.maybeObject`.
      */
    def maybeObject(key: String): Option[JsonLDObject] = body.maybeObject(key)

    /**
      * A convenience function that calls `body.requireArray`.
      */
    def requireArray(key: String): JsonLDArray = body.requireArray(key)

    /**
      * A convenience function that calls `body.maybeArray`.
      */
    def maybeArray(key: String): Option[JsonLDArray] = body.maybeArray(key)

    /**
      * A convenience function that calls `body.requireInt`.
      */
    def requireInt(key: String): JsonLDInt = body.requireInt(key)

    /**
      * A convenience function that calls `body.maybeInt`.
      */
    def maybeInt(key: String): Option[JsonLDInt] = body.maybeInt(key)

    /**
      * A convenience function that calls `body.requireBoolean`.
      */
    def requireBoolean(key: String): JsonLDBoolean = body.requireBoolean(key)

    /**
      * A convenience function that calls `body.maybeBoolean`.
      */
    def maybeBoolean(key: String): Option[JsonLDBoolean] = body.maybeBoolean(key)

    /**
      * Converts this [[JsonLDDocument]] to a pretty-printed JSON-LD string.
      *
      * @return the formatted document.
      */
    def toPrettyString: String = {
        val contextAsJava = JavaUtil.deepScalaToJava(context.toAny)
        val jsonAsJava = JavaUtil.deepScalaToJava(body.toAny)
        val compacted = JsonLdProcessor.compact(jsonAsJava, contextAsJava, new JsonLdOptions())
        JsonUtils.toPrettyString(compacted)
    }
}

/**
  * Utility functions for working with JSON-LD.
  */
object JsonLDUtil {

    /**
      * Returns `true` if the specified JSON-LD object represents an IRI value.
      *
      * @param jsonLDObject the JSON-LD object.
      * @return `true` if the object represents an IRI value.
      */
    def isIriValue(jsonLDObject: JsonLDObject): Boolean = {
        jsonLDObject.value.keySet == Set("@id")
    }

    /**
      * Converts an IRI value to its JSON-LD object value representation.
      *
      * @param iri the IRI to be converted.
      * @return the JSON-LD representation of the IRI as an object value.
      */
    def iriToJsonLDObject(iri: IRI): JsonLDObject = {
        JsonLDObject(Map("@id" -> JsonLDString(iri)))
    }

    /**
      * Converts an IRI value from its JSON-LD object value representation, validating it using the specified validation
      * function.
      *
      * @param jsonLDObject a JSON-LD object value representing an IRI.
      * @return the return value of the validation function.
      */
    def iriFromJsonLDObject[T](jsonLDObject: JsonLDObject, validationFun: (String, => Nothing) => T): T = {
        if (isIriValue(jsonLDObject)) {
            jsonLDObject.value.values.head match {
                case iriJsonLDString: JsonLDString => validationFun(iriJsonLDString.value, throw BadRequestException(s"Invalid IRI: ${iriJsonLDString.value}"))
                case other => throw BadRequestException(s"Expected an IRI: $other")
            }
        } else {
            throw BadRequestException(s"Expected a JSON-LD object containing an IRI: $jsonLDObject")
        }
    }

    /**
      * Returns `true` if the specified JSON-LD object represents a string with a language tag.
      *
      * @param jsonLDObject the JSON-LD object.
      * @return `true` if the object represents a string with a language tag.
      */
    def isStringWithLang(jsonLDObject: JsonLDObject): Boolean = {
        jsonLDObject.value.keySet == Set("@value", "@language")
    }

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
