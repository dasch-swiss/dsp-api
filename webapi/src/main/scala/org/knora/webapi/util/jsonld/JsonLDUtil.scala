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
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.util.{JavaUtil, SmartIri, StringFormatter}


/**
  * Constant strings used in JSON-LD.
  */
object JsonLDConstants {
    val CONTEXT: String = "@context"

    val ID: String = "@id"

    val TYPE: String = "@type"

    val GRAPH: String = "@graph"

    val LANGUAGE: String = "@language"

    val VALUE: String = "@value"
}

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
  * Represents a string value in a JSON-LD document.
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
      * Returns `true` if this JSON-LD object represents an IRI value.
      */
    def isIri: Boolean = {
        value.keySet == Set(JsonLDConstants.ID)
    }

    /**
      * Returns `true` if this JSON-LD object represents a string with a language tag.
      */
    def isStringWithLang: Boolean = {
        value.keySet == Set(JsonLDConstants.VALUE, JsonLDConstants.LANGUAGE)
    }

    /**
      * Returns `true` if this JSON-LD object represents a datatype value.
      */
    def isDatatypeValue: Boolean = {
        value.keySet == Set(JsonLDConstants.TYPE, JsonLDConstants.VALUE)
    }

    /**
      * Converts an IRI value from its JSON-LD object value representation, validating it using the specified validation
      * function.
      *
      * @param validationFun the validation function.
      * @tparam T the type returned by the validation function.
      * @return the return value of the validation function.
      */
    def toIri[T](validationFun: (String, => Nothing) => T): T = {
        if (isIri) {
            val id: IRI = requireString(JsonLDConstants.ID)
            validationFun(id, throw BadRequestException(s"Invalid IRI: $id"))
        } else {
            throw BadRequestException(s"This JSON-LD object does not represent an IRI: $this")
        }
    }

    /**
      * Converts a datatype value from its JSON-LD object value representation, validating it using the specified validation
      * function.
      *
      * @param expectedDatatype the IRI of the expected datatype.
      * @param validationFun the validation function.
      * @tparam T the type returned by the validation function.
      * @return the return value of the validation function.
      */
    def toDatatypeValueLiteral[T](expectedDatatype: SmartIri, validationFun: (String, => Nothing) => T): T = {
        if (isDatatypeValue) {
            val datatype: IRI = requireString(JsonLDConstants.TYPE)

            if (datatype != expectedDatatype.toString) {
                throw BadRequestException(s"Expected datatype value of type <$expectedDatatype>, found <$datatype>")
            }

            val value: String = requireString(JsonLDConstants.VALUE)
            validationFun(value, throw BadRequestException(s"Invalid datatype value literal: $value"))
        } else {
            throw BadRequestException(s"This JSON-LD object does not represent a datatype value: $this")
        }
    }

    /**
      * Gets a required string value of a property of this JSON-LD object, throwing
      * [[BadRequestException]] if the property is not found or if its value is not a string.
      *
      * @param key the key of the required value.
      * @return the value.
      */
    def requireString(key: String): String = {
        value.getOrElse(key, throw BadRequestException(s"No $key provided")) match {
            case JsonLDString(str) => str
            case other => throw BadRequestException(s"Invalid $key: $other (string expected)")
        }
    }

    /**
      * Gets a required string value of a property of this JSON-LD object, throwing
      * [[BadRequestException]] if the property is not found or if its value is not a string.
      * Then parses the value with the specified validation function (see [[org.knora.webapi.util.StringFormatter]]
      * for examples of such functions), throwing [[BadRequestException]] if the validation fails.
      *
      * @param key           the key of the required value.
      * @param validationFun a validation function that takes two arguments: the string to be validated, and a function
      *                      that throws an exception if the string is invalid. The function's return value is the
      *                      validated string, possibly converted to another type T.
      * @tparam T the type of the validation function's return value.
      * @return the return value of the validation function.
      */
    def requireStringWithValidation[T](key: String, validationFun: (String, => Nothing) => T): T = {
        val str: String = requireString(key)
        validationFun(str, throw BadRequestException(s"Invalid $key: $str"))
    }

    /**
      * Gets an optional string value of a property of this JSON-LD object, throwing
      * [[BadRequestException]] if the property's value is not a string.
      *
      * @param key the key of the optional value.
      * @return the value, or `None` if not found.
      */
    def maybeString(key: String): Option[String] = {
        value.get(key).map {
            case JsonLDString(str) => str
            case other => throw BadRequestException(s"Invalid $key: $other (string expected)")
        }
    }

    /**
      * Gets an optional string value of a property of this JSON-LD object, throwing
      * [[BadRequestException]] if the property's value is not a string. Parses the value with the specified validation
      * function (see [[org.knora.webapi.util.StringFormatter]] for examples of such functions), throwing
      * [[BadRequestException]] if the validation fails.
      *
      * @param key           the key of the optional value.
      * @param validationFun a validation function that takes two arguments: the string to be validated, and a function
      *                      that throws an exception if the string is invalid. The function's return value is the
      *                      validated string, possibly converted to another type T.
      * @tparam T the type of the validation function's return value.
      * @return the return value of the validation function, or `None` if the value was not present.
      */
    def maybeStringWithValidation[T](key: String, validationFun: (String, => Nothing) => T): Option[T] = {
        maybeString(key).map {
            str => validationFun(str, throw BadRequestException(s"Invalid $key: $str"))
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
        requireObject(key).toIri(validationFun)
    }

    /**
      * Gets an optional IRI value (contained in a JSON-LD object) value of a property of this JSON-LD object, throwing
      * [[BadRequestException]] if the property's value is not a JSON-LD object. Parses the object's ID with the
      * specified validation function (see [[org.knora.webapi.util.StringFormatter]] for examples of such functions),
      * throwing [[BadRequestException]] if the validation fails.
      *
      * @param key           the key of the optional value.
      * @param validationFun a validation function that takes two arguments: the string to be validated, and a function
      *                      that throws an exception if the string is invalid. The function's return value is the
      *                      validated string, possibly converted to another type T.
      * @tparam T the type of the validation function's return value.
      * @return the return value of the validation function, or `None` if the value was not present.
      */
    def maybeIriInObject[T](key: String, validationFun: (String, => Nothing) => T): Option[T] = {
        maybeObject(key).map(_.toIri(validationFun))
    }

    /**
      * Gets a required datatype value (contained in a JSON-LD object) of a property of this JSON-LD object, throwing
      * [[BadRequestException]] if the property is not found or if its value is not a JSON-LD object.
      * Then parses the object's literal value with the specified validation function (see [[org.knora.webapi.util.StringFormatter]]
      * for examples of such functions), throwing [[BadRequestException]] if the validation fails.
      *
      * @param key the key of the required value.
      * @param expectedDatatype the IRI of the expected datatype.
      * @tparam T the type of the validation function's return value.
      * @return the validated literal value.
      */
    def requireDatatypeValueInObject[T](key: String, expectedDatatype: SmartIri, validationFun: (String, => Nothing) => T): T = {
        requireObject(key).toDatatypeValueLiteral(expectedDatatype, validationFun)
    }

    /**
      * Gets an optional datatype value (contained in a JSON-LD object) value of a property of this JSON-LD object, throwing
      * [[BadRequestException]] if the property's value is not a JSON-LD object. Parses the object's literal value with the
      * specified validation function (see [[org.knora.webapi.util.StringFormatter]] for examples of such functions),
      * throwing [[BadRequestException]] if the validation fails.
      *
      * @param key           the key of the optional value.
      * @param expectedDatatype the IRI of the expected datatype.
      * @param validationFun a validation function that takes two arguments: the string to be validated, and a function
      *                      that throws an exception if the string is invalid. The function's return value is the
      *                      validated string, possibly converted to another type T.
      * @tparam T the type of the validation function's return value.
      * @return the return value of the validation function, or `None` if the value was not present.
      */
    def maybeDatatypeValueInObject[T](key: String, expectedDatatype: SmartIri, validationFun: (String, => Nothing) => T): Option[T] = {
        maybeObject(key).map(_.toDatatypeValueLiteral(expectedDatatype, validationFun))
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
    def requireInt(key: String): Int = {
        value.getOrElse(key, throw BadRequestException(s"No $key provided")) match {
            case obj: JsonLDInt => obj.value
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
    def maybeInt(key: String): Option[Int] = {
        value.get(key).map {
            case obj: JsonLDInt => obj.value
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
    def requireBoolean(key: String): Boolean = {
        value.getOrElse(key, throw BadRequestException(s"No $key provided")) match {
            case obj: JsonLDBoolean => obj.value
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
    def maybeBoolean(key: String): Option[Boolean] = {
        value.get(key).map {
            case obj: JsonLDBoolean => obj.value
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
                val lang = obj.requireStringWithValidation(JsonLDConstants.LANGUAGE, stringFormatter.toSparqlEncodedString)

                if (!LanguageCodes.SupportedLanguageCodes(lang)) {
                    throw BadRequestException(s"Unsupported language code: $lang")
                }

                val text = obj.requireStringWithValidation(JsonLDConstants.VALUE, stringFormatter.toSparqlEncodedString)
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
case class JsonLDDocument(body: JsonLDObject, context: JsonLDObject = JsonLDObject(Map.empty[String, JsonLDValue])) {
    /**
      * A convenience function that calls `body.requireString`.
      */
    def requireString(key: String): String = body.requireString(key)

    /**
      * A convenience function that calls `body.requireStringWithValidation`.
      */
    def requireStringWithValidation[T](key: String, validationFun: (String, => Nothing) => T): T = body.requireStringWithValidation(key, validationFun)

    /**
      * A convenience function that calls `body.maybeString`.
      */
    def maybeString(key: String): Option[String] = body.maybeString(key)

    /**
      * A convenience function that calls `body.maybeStringWithValidation`.
      */
    def maybeStringWithValidation[T](key: String, validationFun: (String, => Nothing) => T): Option[T] = body.maybeStringWithValidation(key, validationFun)

    /**
      * A convenience function that calls `body.requireIriInObject`.
      */
    def requireIriInObject[T](key: String, validationFun: (String, => Nothing) => T): T = body.requireIriInObject(key, validationFun)

    /**
      * A convenience function that calls `body.maybeIriInObject`.
      */
    def maybeIriInObject[T](key: String, validationFun: (String, => Nothing) => T): Option[T] = body.maybeIriInObject(key, validationFun)

    /**
      * A convenience function that calls `body.requireDatatypeValueInObject`.
      */
    def requireDatatypeValueInObject[T](key: String, expectedDatatype: SmartIri, validationFun: (String, => Nothing) => T): T =
        body.requireDatatypeValueInObject(
            key = key,
            expectedDatatype = expectedDatatype,
            validationFun = validationFun
        )

    /**
      * A convenience function that calls `body.maybeDatatypeValueInObject`.
      */
    def maybeDatatypeValueInObject[T](key: String, expectedDatatype: SmartIri, validationFun: (String, => Nothing) => T): Option[T] =
        body.maybeDatatypeValueInObject(
            key = key,
            expectedDatatype = expectedDatatype,
            validationFun = validationFun
        )

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
    def requireInt(key: String): Int = body.requireInt(key)

    /**
      * A convenience function that calls `body.maybeInt`.
      */
    def maybeInt(key: String): Option[Int] = body.maybeInt(key)

    /**
      * A convenience function that calls `body.requireBoolean`.
      */
    def requireBoolean(key: String): Boolean = body.requireBoolean(key)

    /**
      * A convenience function that calls `body.maybeBoolean`.
      */
    def maybeBoolean(key: String): Option[Boolean] = body.maybeBoolean(key)

    /**
      * Converts this JSON-LD object to its compacted Java representation.
      */
    private def makeCompactedObject: java.util.Map[IRI, AnyRef] = {
        val contextAsJava = JavaUtil.deepScalaToJava(context.toAny)
        val jsonAsJava = JavaUtil.deepScalaToJava(body.toAny)
        JsonLdProcessor.compact(jsonAsJava, contextAsJava, new JsonLdOptions())
    }

    /**
      * Converts this [[JsonLDDocument]] to a pretty-printed JSON-LD string.
      *
      * @return the formatted document.
      */
    def toPrettyString: String = {
        JsonUtils.toPrettyString(makeCompactedObject)
    }

    /**
      * Converts this [[JsonLDDocument]] to a compact JSON-LD string.
      *
      * @return the formatted document.
      */
    def toCompactString: String = {
        JsonUtils.toString(makeCompactedObject)
    }
}

/**
  * Utility functions for working with JSON-LD.
  */
object JsonLDUtil {

    /**
      * Makes a JSON-LD context containing prefixes for Knora and other ontologies.
      *
      * @param fixedPrefixes                  a map of fixed prefixes (e.g. `rdfs` or `knora-base`) to namespaces.
      * @param knoraOntologiesNeedingPrefixes a set of IRIs of other Knora ontologies that need prefixes.
      * @return a JSON-LD context.
      */
    def makeContext(fixedPrefixes: Map[String, String], knoraOntologiesNeedingPrefixes: Set[SmartIri]): JsonLDObject = {
        /**
          * Given a function that makes a prefix from a Knora ontology IRI, returns an association list in which
          * each element is a prefix associated with a namespace.
          *
          * @param prefixFun a function that makes a prefix from a Knora ontology IRI.
          * @return an association list in which each element is a prefix associated with a namespace.
          */
        def makeKnoraPrefixes(prefixFun: SmartIri => String): Seq[(String, String)] = {
            knoraOntologiesNeedingPrefixes.toSeq.map {
                ontology => prefixFun(ontology) -> (ontology.toString + '#')
            }
        }

        /**
          * Determines whether an association list returned by `makeKnoraPrefixes` contains conflicts,
          * including conflicts with `fixedPrefixes`.
          *
          * @param knoraPrefixes the association list to check.
          * @return `true` if the list contains conflicts.
          */
        def hasPrefixConflicts(knoraPrefixes: Seq[(String, String)]): Boolean = {
            val prefixSeq = knoraPrefixes.map(_._1) ++ fixedPrefixes.keys
            prefixSeq.size != prefixSeq.distinct.size
        }

        // Make an association list of short prefixes to the ontologies in knoraOntologiesNeedingPrefixes.
        val shortKnoraPrefixes: Seq[(String, String)] = makeKnoraPrefixes(ontology => ontology.getShortPrefixLabel)

        // Are there conflicts in that list?
        val knoraPrefixMap: Map[String, String] = if (hasPrefixConflicts(shortKnoraPrefixes)) {
            // Yes. Try again with long prefixes.
            val longKnoraPrefixes: Seq[(String, String)] = makeKnoraPrefixes(ontology => ontology.getLongPrefixLabel)

            // Are there still conflicts?
            if (hasPrefixConflicts(longKnoraPrefixes)) {
                // Yes. This shouldn't happen, so throw InconsistentTriplestoreDataException.
                throw InconsistentTriplestoreDataException(s"Can't make distinct prefixes for ontologies: ${(fixedPrefixes.values ++ knoraOntologiesNeedingPrefixes.map(_.toString)).mkString(", ")}")
            } else {
                // No. Use the long prefixes.
                longKnoraPrefixes.toMap
            }
        } else {
            // No. Use the short prefixes.
            shortKnoraPrefixes.toMap
        }

        // Make a JSON-LD context containing the fixed prefixes as well as the ones generated by this method.
        JsonLDObject((fixedPrefixes ++ knoraPrefixMap).map {
            case (prefix, namespace) => prefix -> JsonLDString(namespace)
        })
    }

    /**
      * Converts an IRI value to its JSON-LD object value representation.
      *
      * @param iri the IRI to be converted.
      * @return the JSON-LD representation of the IRI as an object value.
      */
    def iriToJsonLDObject(iri: IRI): JsonLDObject = {
        JsonLDObject(Map(JsonLDConstants.ID -> JsonLDString(iri)))
    }

    /**
      * Given a predicate value and a language code, returns a JSON-LD object containing `@value` and `@language`
      * predicates.
      *
      * @param obj a predicate value.
      * @return a JSON-LD object containing `@value` and `@language` predicates.
      */
    def objectWithLangToJsonLDObject(obj: String, lang: String): JsonLDObject = {
        JsonLDObject(Map(
            JsonLDConstants.VALUE -> JsonLDString(obj),
            JsonLDConstants.LANGUAGE -> JsonLDString(lang)
        ))
    }

    /**
      * Given a predicate value and a datatype, returns a JSON-LD object containing `@value` and `@type`
      * predicates.
      *
      * @param value a predicate value.
      * @param datatype the datatype.
      * @return a JSON-LD object containing `@value` and `@type` predicates.
      */
    def datatypeValueToJsonLDObject(value: String, datatype: SmartIri): JsonLDObject = {
        JsonLDObject(Map(
            JsonLDConstants.VALUE -> JsonLDString(value),
            JsonLDConstants.TYPE -> JsonLDString(datatype.toString)
        ))
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
            case (lang, obj) =>
                objectWithLangToJsonLDObject(
                    obj = obj,
                    lang = lang
                )
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
