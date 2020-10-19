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

package org.knora.webapi.messages.util

import java.io.{StringReader, StringWriter}
import java.math.BigInteger
import java.util
import java.util.UUID

import com.apicatalog.jsonld._
import com.apicatalog.jsonld.document._
import javax.json._
import javax.json.stream.JsonGenerator
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.eclipse.rdf4j
import org.eclipse.rdf4j.model.Statement
import org.knora.webapi._
import org.knora.webapi.exceptions.{BadRequestException, InconsistentTriplestoreDataException, InvalidJsonLDException, InvalidRdfException}
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.util.JavaUtil._

import scala.collection.JavaConverters._

/*

The classes in this file provide a Scala API for formatting and parsing JSON-LD. The implementation
uses the javax.json API and a Java implementation of the JSON-LD API <https://www.w3.org/TR/json-ld11-api/>
(currently <https://github.com/filip26/titanium-json-ld>). This shields the rest of Knora from the details
of the JSON-LD implementation. These classes also provide Knora-specific JSON-LD functionality to facilitate
reading data from Knora API requests and constructing Knora API responses.

*/

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

    val all: Set[String] = Set(CONTEXT, ID, TYPE, GRAPH, LANGUAGE, VALUE)
}

/**
 * Represents a value in a JSON-LD document.
 */
sealed trait JsonLDValue extends Ordered[JsonLDValue] {
    /**
     * Converts this JSON-LD value to a `javax.json` [[JsonValue]].
     */
    def toJavaxJsonValue: JsonValue
}

/**
 * Represents a string value in a JSON-LD document.
 *
 * @param value the underlying string.
 */
case class JsonLDString(value: String) extends JsonLDValue {
    override def toJavaxJsonValue: JsonString = {
        Json.createValue(value)
    }

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
    override def toJavaxJsonValue: JsonNumber = {
        Json.createValue(value)
    }

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
    override def toJavaxJsonValue: JsonValue = {
        if (value) {
            JsonValue.TRUE
        } else {
            JsonValue.FALSE
        }
    }

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

    override def toJavaxJsonValue: JsonObject = {
        val builder = Json.createObjectBuilder()

        for ((entryKey, entryValue) <- value) {
            builder.add(entryKey, entryValue.toJavaxJsonValue)
        }

        builder.build
    }

    /**
     * Recursively adds the contents of this JSON-LD object representing triples (rather than a literal)
     * to an [[rdf4j.model.Model]].
     *
     * @param model the model being constructed.
     * @return the subject of the contents of this JSON-LD object (an IRI or a blank node).
     */
    def addToModel(model: rdf4j.model.Model)
                  (implicit stringFormatter: StringFormatter,
                   valueFactory: rdf4j.model.ValueFactory): rdf4j.model.Resource = {
        // If this object has an @id, use it as the subject, otherwise generate a blank node ID.
        val rdfSubj: rdf4j.model.Resource = maybeStringWithValidation(JsonLDConstants.ID, stringFormatter.toSmartIriWithErr) match {
            case Some(subjectIri: SmartIri) =>
                // It's an IRI.
                valueFactory.createIRI(subjectIri.toString)

            case None =>
                // Generate a blank node ID.
                valueFactory.createBNode()
        }

        // Add rdf:type statements to the model.
        addRdfTypesToModel(model = model, rdfSubj = rdfSubj)

        // If this object contains a @graph, add its contents to the model, without linking to them
        // from this object.
        addGraphToModel(model)

        // Add the IRI predicates and their objects.

        val predicates = value.keySet -- JsonLDConstants.all

        for (pred <- predicates) {
            val rdfPred: rdf4j.model.IRI = valueFactory.createIRI(pred)
            val obj: JsonLDValue = value(pred)

            addJsonLDValueToModel(
                model = model,
                rdfSubj = rdfSubj,
                rdfPred = rdfPred,
                jsonLDValue = obj
            )
        }

        rdfSubj
    }

    /**
     * Adds `rdf:type` statements to an [[rdf4j.model.Model]] to specify the types of
     * a JSON-LD object that represents triples (rather than a literal).
     *
     * @param model   the model being constructed.
     * @param rdfSubj the subject of this JSON-LD object.
     */
    private def addRdfTypesToModel(model: rdf4j.model.Model,
                                   rdfSubj: rdf4j.model.Resource)
                                  (implicit stringFormatter: StringFormatter,
                                   valueFactory: rdf4j.model.ValueFactory): Unit = {
        value.get(JsonLDConstants.TYPE) match {
            case Some(rdfTypes: JsonLDValue) =>
                rdfTypes match {
                    case jsonLDString: JsonLDString =>
                        // It has just one @type.
                        model.add(
                            rdfSubj,
                            valueFactory.createIRI(OntologyConstants.Rdf.Type),
                            valueFactory.createIRI(jsonLDString.value)
                        )

                    case jsonLDArray: JsonLDArray =>
                        // It has more than one @type.
                        for (elem <- jsonLDArray.value) {
                            elem match {
                                case jsonLDString: JsonLDString =>
                                    model.add(
                                        rdfSubj,
                                        valueFactory.createIRI(OntologyConstants.Rdf.Type),
                                        valueFactory.createIRI(jsonLDString.value)
                                    )

                                case _ => throw InvalidJsonLDException("The objects of @type must be strings")
                            }
                        }

                    case _ => throw InvalidJsonLDException("The objects of @type must be strings")
                }

            case None => ()
        }
    }

    /**
     * Adds the contents of `@graph` to an [[rdf4j.model.Model]].
     *
     * @param model the model being constructed.
     */
    private def addGraphToModel(model: rdf4j.model.Model)
                               (implicit stringFormatter: StringFormatter,
                                valueFactory: rdf4j.model.ValueFactory): Unit = {
        value.get(JsonLDConstants.GRAPH) match {
            case Some(graphContents: JsonLDValue) =>
                graphContents match {
                    case jsonLDArray: JsonLDArray =>
                        for (elem <- jsonLDArray.value) {
                            elem match {
                                case jsonLDObject: JsonLDObject =>
                                    jsonLDObject.addToModel(model)

                                case _ =>
                                    throw InvalidJsonLDException("The object of @graph must be a JSON-LD array of JSON-LD objects")
                            }
                        }

                    case _ =>
                        throw InvalidJsonLDException("The object of @graph must be a JSON-LD array of JSON-LD objects")
                }

            case None => ()
        }
    }

    /**
     * Recursively adds a [[JsonLDValue]] to an [[rdf4j.model.Model]], using the specified subject and predicate.
     *
     * @param model       the model being constructed.
     * @param rdfSubj     the subject.
     * @param rdfPred     the predicate.
     * @param jsonLDValue the value to be added.
     */
    private def addJsonLDValueToModel(model: rdf4j.model.Model,
                                      rdfSubj: rdf4j.model.Resource,
                                      rdfPred: rdf4j.model.IRI,
                                      jsonLDValue: JsonLDValue)
                                     (implicit stringFormatter: StringFormatter,
                                      valueFactory: rdf4j.model.ValueFactory): Unit = {
        // Which type of JSON-LD value is this?
        jsonLDValue match {
            case jsonLDObject: JsonLDObject =>
                // It's a JSON-LD object. What does it represent?
                val rdfObj: rdf4j.model.Value = if (jsonLDObject.isIri) {
                    // An IRI.
                    valueFactory.createIRI(jsonLDObject.requireString(JsonLDConstants.ID))
                } else if (jsonLDObject.isDatatypeValue) {
                    // A literal.
                    valueFactory.createLiteral(
                        jsonLDObject.requireString(JsonLDConstants.VALUE),
                        valueFactory.createIRI(jsonLDObject.requireString(JsonLDConstants.TYPE))
                    )
                } else if (jsonLDObject.isStringWithLang) {
                    // A string literal with a language tag.
                    valueFactory.createLiteral(
                        jsonLDObject.requireString(JsonLDConstants.VALUE),
                        jsonLDObject.requireString(JsonLDConstants.LANGUAGE)
                    )
                } else {
                    // Triples. Recurse to add its contents to the model.
                    jsonLDObject.addToModel(model)
                }

                // Add a triple linking the subject to the object.
                model.add(
                    rdfSubj,
                    rdfPred,
                    rdfObj
                )

            case jsonLDArray: JsonLDArray =>
                // It's a JSON-LD array. Recurse to add the contents of each value to the
                // model, and to add a triple linking the subject to each value.
                for (elem <- jsonLDArray.value) {
                    addJsonLDValueToModel(
                        model = model,
                        rdfSubj = rdfSubj,
                        rdfPred = rdfPred,
                        jsonLDValue = elem
                    )
                }

            case jsonLDString: JsonLDString =>
                // It's a string literal.
                model.add(
                    rdfSubj,
                    rdfPred,
                    valueFactory.createLiteral(jsonLDString.value)
                )

            case jsonLDBoolean: JsonLDBoolean =>
                // It's a boolean literal.
                model.add(
                    rdfSubj,
                    rdfPred,
                    valueFactory.createLiteral(jsonLDBoolean.value)
                )

            case jsonLDInt: JsonLDInt =>
                // It's an integer literal. Use a BigInteger rather than an Int,
                // so we get xsd:integer rather than xsd:int.
                model.add(
                    rdfSubj,
                    rdfPred,
                    valueFactory.createLiteral(new BigInteger(jsonLDInt.value.toString, 10))
                )
        }
    }


    /**
     * Returns `true` if this JSON-LD object represents an IRI value.
     */
    def isIri: Boolean = {
        value.keySet == Set(JsonLDConstants.ID)
    }

    /**
     * Returns `true` if this JSON-LD object represents a string literal with a language tag.
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
     * @param validationFun    the validation function.
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
     * Then parses the value with the specified validation function (see [[StringFormatter]]
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
     * function (see [[StringFormatter]] for examples of such functions), throwing
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
     * Then parses the object's ID with the specified validation function (see [[StringFormatter]]
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
     * specified validation function (see [[StringFormatter]] for examples of such functions),
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
     * Then parses the object's literal value with the specified validation function (see [[StringFormatter]]
     * for examples of such functions), throwing [[BadRequestException]] if the validation fails.
     *
     * @param key              the key of the required value.
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
     * specified validation function (see [[StringFormatter]] for examples of such functions),
     * throwing [[BadRequestException]] if the validation fails.
     *
     * @param key              the key of the optional value.
     * @param expectedDatatype the IRI of the expected datatype.
     * @param validationFun    a validation function that takes two arguments: the string to be validated, and a function
     *                         that throws an exception if the string is invalid. The function's return value is the
     *                         validated string, possibly converted to another type T.
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

    /**
     * Validates the `@id` of a JSON-LD object as a Knora data IRI.
     *
     * @return a validated Knora data IRI.
     */
    def requireIDAsKnoraDataIri: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val iri = requireStringWithValidation(JsonLDConstants.ID, stringFormatter.toSmartIriWithErr)

        if (!iri.isKnoraDataIri) {
            throw BadRequestException(s"Invalid Knora data IRI: $iri")
        }

        iri
    }

    /**
     * Validates the optional `@id` of a JSON-LD object as a Knora data IRI.
     *
     * @return an optional validated Knora data IRI.
     */
    def maybeIDAsKnoraDataIri: Option[SmartIri] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val maybeIri: Option[SmartIri] = maybeStringWithValidation(JsonLDConstants.ID, stringFormatter.toSmartIriWithErr)

        maybeIri.foreach {
            iri =>
                if (!iri.isKnoraDataIri) {
                    throw BadRequestException(s"Invalid Knora data IRI: $maybeIri")
                }
        }

        maybeIri
    }

    /**
     * Validates an optional Base64-encoded UUID in a JSON-LD object.
     *
     * @return an optional validated decoded UUID.
     */
    def maybeUUID(key: String): Option[UUID] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
        maybeStringWithValidation(key, stringFormatter.validateBase64EncodedUuid)
    }

    /**
     * Validates the `@type` of a JSON-LD object as a Knora type IRI in the API v2 complex schema.
     *
     * @return a validated Knora type IRI.
     */
    def requireTypeAsKnoraApiV2ComplexTypeIri: SmartIri = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val typeIri = requireStringWithValidation(JsonLDConstants.TYPE, stringFormatter.toSmartIriWithErr)

        if (!(typeIri.isKnoraEntityIri && typeIri.getOntologySchema.contains(ApiV2Complex))) {
            throw BadRequestException(s"Invalid Knora API v2 complex type IRI: $typeIri")
        }

        typeIri
    }

    /**
     * When called on a JSON-LD object representing a resource, ensures that it contains a single Knora property with
     * a single value in the Knora API v2 complex schema.
     *
     * @return the property IRI and the value.
     */
    def requireResourcePropertyApiV2ComplexValue: (SmartIri, JsonLDObject) = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val resourceProps: Map[IRI, JsonLDValue] = value - JsonLDConstants.ID - JsonLDConstants.TYPE

        if (resourceProps.isEmpty) {
            throw BadRequestException("No value submitted")
        }

        if (resourceProps.size > 1) {
            throw BadRequestException(s"Only one value can be submitted per request using this route")
        }

        resourceProps.head match {
            case (key: IRI, jsonLDValue: JsonLDValue) =>
                val propertyIri = key.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: $key"))

                if (!(propertyIri.isKnoraEntityIri && propertyIri.getOntologySchema.contains(ApiV2Complex))) {
                    throw BadRequestException(s"Invalid Knora API v2 complex property IRI: $propertyIri")
                }

                jsonLDValue match {
                    case jsonLDObject: JsonLDObject => propertyIri -> jsonLDObject
                    case _ => throw BadRequestException(s"Invalid value for $propertyIri")
                }
        }
    }
}

/**
 * Represents a JSON array in a JSON-LD document.
 *
 * @param value a sequence of JSON-LD values.
 */
case class JsonLDArray(value: Seq[JsonLDValue]) extends JsonLDValue {
    implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    override def equals(that: Any): Boolean = {
        // Ignore the order of elements when testing equality for this class, since by default,
        // order is not significant in JSON-LD arrays (see
        // <https://www.w3.org/TR/json-ld11/#terms-imported-from-other-specifications>).
        that match {
            case otherArray: JsonLDArray => value.toSet == otherArray.value.toSet
            case _ => false
        }
    }

    override def hashCode(): Int = {
        val hashCodeBuilder = new HashCodeBuilder(21, 41)

        for (elem <- value.sorted) {
            hashCodeBuilder.append(elem)
        }

        hashCodeBuilder.toHashCode
    }

    override def toJavaxJsonValue: JsonArray = {
        val builder = Json.createArrayBuilder()

        for (elem <- value) {
            builder.add(elem.toJavaxJsonValue)
        }

        builder.build
    }

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
     * A convenience function that calls `body.requireIDAsKnoraDataIri`.
     */
    def requireIDAsKnoraDataIri: SmartIri = body.requireIDAsKnoraDataIri

    /**
     * A convenience function that calls `body.maybeIDAsKnoraDataIri`.
     */
    def maybeIDAsKnoraDataIri: Option[SmartIri] = body.maybeIDAsKnoraDataIri

    /**
     * A convenience function that calls `body.requireTypeAsKnoraApiV2ComplexTypeIri`.
     */
    def requireTypeAsKnoraTypeIri: SmartIri = body.requireTypeAsKnoraApiV2ComplexTypeIri

    /**
     * A convenience function that calls `body.requireResourcePropertyApiV2ComplexValue`.
     */
    def requireResourcePropertyValue: (SmartIri, JsonLDObject) = body.requireResourcePropertyApiV2ComplexValue

    /**
     * A convenience function that calls `body.maybeUUID`.
     */
    def maybeUUID(key: String): Option[UUID] = body.maybeUUID(key: String)

    /**
     * Converts this JSON-LD document to its compacted representation.
     */
    private def makeCompactedJavaxJsonObject: JsonObject = {
        val bodyAsTitaniumJsonDocument: JsonDocument = JsonDocument.of(body.toJavaxJsonValue)
        val contextAsTitaniumJsonDocument: JsonDocument = JsonDocument.of(context.toJavaxJsonValue)
        JsonLd.compact(bodyAsTitaniumJsonDocument, contextAsTitaniumJsonDocument).get
    }

    /**
     * Formats this JSON-LD document as a string, using the specified [[JsonWriterFactory]].
     *
     * @param jsonWriterFactory a [[JsonWriterFactory]] configured with the desired options.
     * @return the formatted document.
     */
    private def formatWithJsonWriterFactory(jsonWriterFactory: JsonWriterFactory): String = {
        val compactedJavaxJsonObject: JsonObject = makeCompactedJavaxJsonObject
        val stringWriter = new StringWriter()
        val jsonWriter = jsonWriterFactory.createWriter(stringWriter)
        jsonWriter.write(compactedJavaxJsonObject)
        jsonWriter.close()
        stringWriter.toString
    }

    /**
     * Converts this JSON-LD document to a pretty-printed JSON-LD string.
     *
     * @return the formatted document.
     */
    def toPrettyString: String = {
        val config = new util.HashMap[String, Boolean]()
        config.put(JsonGenerator.PRETTY_PRINTING, true)
        val jsonWriterFactory: JsonWriterFactory = Json.createWriterFactory(config)
        formatWithJsonWriterFactory(jsonWriterFactory)
    }

    /**
     * Converts this [[JsonLDDocument]] to a compact JSON-LD string.
     *
     * @return the formatted document.
     */
    def toCompactString: String = {
        val config = new util.HashMap[String, Boolean]()
        val jsonWriterFactory: JsonWriterFactory = Json.createWriterFactory(config)
        formatWithJsonWriterFactory(jsonWriterFactory)
    }

    /**
     * Converts this JSON-LD document to an [[rdf4j.model.Model]], for conversion to
     * other RDF formats.
     */
    def toRDF4JModel: rdf4j.model.Model = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Make an RDF4J ValueFactory for constructing RDF4J values.
        implicit val valueFactory: rdf4j.model.impl.SimpleValueFactory = rdf4j.model.impl.SimpleValueFactory.getInstance

        // Make an empty RDF4J model.
        val model = new rdf4j.model.impl.LinkedHashModel()

        // Add the prefixes and namespaces from the JSON-LD context to the model.
        for ((prefix: String, namespaceValue: JsonLDValue) <- context.value) {
            val namespace: rdf4j.model.Namespace = namespaceValue match {
                case jsonLDString: JsonLDString => new rdf4j.model.impl.SimpleNamespace(prefix, jsonLDString.value)
                case _ => throw InvalidJsonLDException("The keys and values of @context must be strings")
            }

            model.setNamespace(namespace)
        }

        // Recursively add the JSON-LD document body to the model.
        body.addToModel(model)
        model
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
    def makeContext(fixedPrefixes: Map[String, String], knoraOntologiesNeedingPrefixes: Set[SmartIri] = Set.empty): JsonLDObject = {
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
     * @param value    a predicate value.
     * @param datatype the datatype.
     * @return a JSON-LD object containing `@value` and `@type` predicates.
     */
    def datatypeValueToJsonLDObject(value: String, datatype: SmartIri): JsonLDObject = {
        // Normalise the formatting of decimal values to ensure consistency in tests.
        val strValue: String = if (datatype.toString == OntologyConstants.Xsd.Decimal) {
            BigDecimal(value).underlying.stripTrailingZeros.toPlainString
        } else {
            value
        }

        JsonLDObject(Map(
            JsonLDConstants.VALUE -> JsonLDString(strValue),
            JsonLDConstants.TYPE -> JsonLDString(datatype.toString)
        ))
    }

    /**
     * Given a map of language codes to predicate values, returns a JSON-LD array in which each element
     * has a `@value` predicate and a `@language` predicate.
     *
     * @param objectsWithLangs a map of language codes to predicate values.
     * @return a JSON-LD array in which each element has a `@value` predicate and a `@language` predicate,
     *         sorted by language code.
     */
    def objectsWithLangsToJsonLDArray(objectsWithLangs: Map[String, String]): JsonLDArray = {
        val objects: Seq[JsonLDObject] = objectsWithLangs.toSeq.sortBy(_._1).map {
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
        // Parse the string into a javax.json.JsonStructure.
        val stringReader = new StringReader(jsonLDString)
        val jsonReader: JsonReader = Json.createReader(stringReader)
        val jsonStructure: JsonStructure = jsonReader.read()

        // Convert the JsonStructure to a Titanium JsonDocument.
        val titaniumDocument: JsonDocument = JsonDocument.of(jsonStructure)

        // Use Titanium to compact the document with an empty context.
        val emptyContext = JsonDocument.of(Json.createObjectBuilder().build())
        val compactedJsonObject: JsonObject = JsonLd.compact(titaniumDocument, emptyContext).get

        // Convert the resulting javax.json.JsonObject to a JsonLDDocument.
        javaxJsonObjectToJsonLDDocument(compactedJsonObject)
    }

    /**
     * Converts a [[JsonObject]] into a [[JsonLDDocument]].
     *
     * @param jsonObject a JSON object.
     * @return the corresponding [[JsonLDDocument]].
     */
    private def javaxJsonObjectToJsonLDDocument(jsonObject: JsonObject): JsonLDDocument = {
        /**
         * Converts a [[JsonValue]] to a [[JsonLDValue]].
         *
         * @param jsonValue a [[JsonValue]].
         * @return the corresponding [[JsonLDValue]].
         */
        def jsonValueToJsonLDValue(jsonValue: JsonValue): JsonLDValue = {
            jsonValue match {
                case jsonString: JsonString => JsonLDString(jsonString.getString)
                case jsonNumber: JsonNumber => JsonLDInt(jsonNumber.intValue)
                case JsonValue.TRUE => JsonLDBoolean(true)
                case JsonValue.FALSE => JsonLDBoolean(false)

                case jsonObject: JsonObject =>
                    val content: Map[IRI, JsonLDValue] = jsonObject.keySet.asScala.toSet.map {
                        key: IRI => key -> jsonValueToJsonLDValue(jsonObject.get(key))
                    }.toMap

                    JsonLDObject(content)

                case jsonArray: JsonArray =>
                    val content: Seq[JsonLDValue] = jsonArray.asScala.map {
                        elem => jsonValueToJsonLDValue(elem)
                    }.toVector

                    JsonLDArray(content)

                case _ => throw BadRequestException(s"Unexpected type in JSON-LD input: $jsonValue")
            }
        }

        jsonValueToJsonLDValue(jsonObject) match {
            case obj: JsonLDObject => JsonLDDocument(body = obj, context = JsonLDObject(Map.empty[IRI, JsonLDValue]))
            case _ => throw BadRequestException(s"Expected JSON-LD object: $jsonObject")
        }
    }

    /**
     * Converts an [[rdf4j.model.Model]] to a [[JsonLDDocument]]. There can be more than one valid
     * way to nest objects in the converted JSON-LD. This implementation takes the following
     * approach:
     *
     * - Inline blank nodes wherever they are used.
     * - Nest each entity in the first encountered entity that refers to it, and refer to it by IRI elsewhere.
     * - Do not nest Knora ontology entities.
     * - After nesting, if more than one top-level entity remains, wrap them all in a `@graph`.
     *
     * An error is returned if more than one entity refers to the same blank node.
     *
     * @param model the model to be read.
     * @return the corresponding [[JsonLDDocument]].
     */
    def fromRDF4JModel(model: rdf4j.model.Model): JsonLDDocument = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Make a JSON-LD context from the model's namespaces.
        val namespaces: Set[rdf4j.model.Namespace] = model.getNamespaces.asScala.toSet

        val contextMap: Map[IRI, JsonLDString] = namespaces.map {
            namespace: rdf4j.model.Namespace =>
                namespace.getPrefix -> JsonLDString(namespace.getName)
        }.toMap

        val context = JsonLDObject(contextMap)

        // Is the model empty?
        val body: JsonLDObject = if (model.isEmpty) {
            // Yes. Just make an empty JSON-LD object.
            JsonLDObject(Map.empty)
        } else {
            // No. Copy the model, so we can modify our copy.
            val modelCopy = new rdf4j.model.impl.LinkedHashModel(model)

            // Get the set of subjects in the model.
            val subjects: Set[rdf4j.model.Resource] = modelCopy.subjects.asScala.toSet

            // Make an empty collection of top-level JSON-LD objects. This is a mutable collection
            // so JSON-LD objects can be added to it and later removed when they are inlined.
            val topLevelObjects: collection.mutable.Map[rdf4j.model.Resource, JsonLDObject] = collection.mutable.Map.empty

            // Make a JSON-LD object for each entity, inlining them as we go along.
            for (subj: rdf4j.model.Resource <- subjects) {
                // Get the statements about the entity.
                val statements: Set[rdf4j.model.Statement] = modelCopy.filter(subj, null, null).asScala.toSet

                // Have we already processed this entity?
                if (statements.isEmpty) {
                    // Yes. Skip it.
                    ()
                } else {
                    // No. Make a JSON-LD object representing the entity and any nested entities.
                    val jsonLDObject = entityToJsonLDObject(
                        subj = subj,
                        statements = statements,
                        model = modelCopy,
                        topLevelObjects = topLevelObjects
                    )

                    // Add it to the collection of top-level objects.
                    topLevelObjects += (subj -> jsonLDObject)
                }
            }

            // Is there more than one top-level entity?
            if (topLevelObjects.size > 1) {
                // Yes. Make a @graph.
                JsonLDObject(Map(JsonLDConstants.GRAPH -> JsonLDArray(topLevelObjects.values.toVector)))
            } else {
                // No. Use the single top-level object as the body of the document.
                topLevelObjects.values.head
            }
        }

        JsonLDDocument(body = body, context = context)
    }

    /**
     * Converts an RDF entity to a [[JsonLDObject]].
     *
     * @param subj            the subject of the entity.
     * @param statements      the statements representing the entity.
     * @param model           the RDF4J model that is being read.
     * @param topLevelObjects the top-level JSON-LD objects that have been constructed so far.
     * @return the JSON-LD object that was constructed.
     */
    private def entityToJsonLDObject(subj: rdf4j.model.Resource,
                                     statements: Set[rdf4j.model.Statement],
                                     model: rdf4j.model.Model,
                                     topLevelObjects: collection.mutable.Map[rdf4j.model.Resource, JsonLDObject])
                                    (implicit stringFormatter: StringFormatter): JsonLDObject = {
        // Remove the statements from the model.
        model.remove(subj, null, null)

        // Does this entity have an IRI, or is it a blank node?
        val idContent: Map[String, JsonLDValue] = subj match {
            case iri: rdf4j.model.IRI =>
                // It has an IRI. Use it for the @id.
                Map(JsonLDConstants.ID -> JsonLDString(iri.stringValue))

            case _ =>
                // It's a blank node. Don't make an @id.
                Map.empty[String, JsonLDValue]
        }

        // Group the statements by predicate.
        val groupedByPred: Map[rdf4j.model.IRI, Set[rdf4j.model.Statement]] = statements.groupBy(_.getPredicate)

        // Make JSON-LD content representing the predicates and their objects.
        val predsAndObjs: Map[IRI, JsonLDValue] = groupedByPred.keySet.map {
            pred: rdf4j.model.IRI =>
                val predStatements: Set[Statement] = groupedByPred(pred)
                val predIri: IRI = pred.stringValue

                // Is the predicate rdf:type?
                if (predIri == OntologyConstants.Rdf.Type) {
                    // Yes. Add @type. Is there just one rdf:type?
                    val rdfType: JsonLDValue = if (predStatements.size == 1) {
                        // Yes.
                        JsonLDString(predStatements.head.getObject.stringValue)
                    } else {
                        // No. Make a JSON-LD array.
                        JsonLDArray(
                            predStatements.map {
                                statement => JsonLDString(statement.getObject.stringValue)
                            }.toVector
                        )

                    }

                    JsonLDConstants.TYPE -> rdfType
                } else {
                    // The predicate is not rdf:type. Convert its objects.
                    val objs: Seq[JsonLDValue] = predStatements.map(_.getObject).map {
                        case resource: rdf4j.model.Resource =>
                            // The object is an entity. Recurse to get it.
                            rdf4jResourceToJsonLDValue(
                                resource = resource,
                                model = model,
                                topLevelObjects = topLevelObjects
                            )

                        case literal: rdf4j.model.Literal => rdf4jLiteralToJsonLDValue(literal)

                        case other => throw InvalidRdfException(s"Unexpected RDF value: ${other.stringValue}")
                    }.toVector

                    // Does the predicate have just one object?
                    val jsonLDValue = if (objs.size == 1) {
                        // Yes.
                        objs.head
                    } else {
                        // No. Make a JSON-LD array.
                        JsonLDArray(objs)
                    }

                    predIri -> jsonLDValue
                }
        }.toMap

        JsonLDObject(idContent ++ predsAndObjs)
    }

    /**
     * Converts an [[rdf4j.model.Literal]] to a [[JsonLDValue]].
     *
     * @param literal the literal to be converted.
     * @return the corresponding JSON-LD value.
     */
    private def rdf4jLiteralToJsonLDValue(literal: rdf4j.model.Literal)
                                         (implicit stringFormatter: StringFormatter): JsonLDValue = {
        // Is this a string literal with a language tag?
        literal.getLanguage.toOption match {
            case Some(language) =>
                // Yes.
                objectWithLangToJsonLDObject(obj = literal.getLabel, lang = language)

            case None =>
                // No. Is there a native JSON-LD type for it?
                val datatypeIri: IRI = literal.getDatatype.stringValue

                datatypeIri match {
                    case OntologyConstants.Xsd.String =>
                        // String.
                        JsonLDString(literal.stringValue)

                    case OntologyConstants.Xsd.Int | OntologyConstants.Xsd.Integer =>
                        // Integer.
                        JsonLDInt(literal.intValue)

                    case OntologyConstants.Xsd.Boolean =>
                        // Boolean.
                        JsonLDBoolean(literal.booleanValue)

                    case _ =>
                        // There's no native JSON-LD type for this literal.
                        // Make a JSON-LD object representing a datatype value.
                        datatypeValueToJsonLDObject(value = literal.getLabel, datatype = datatypeIri.toSmartIri)
                }
        }
    }

    /**
     * Converts an [[rdf4j.model.Resource]] to a [[JsonLDValue]].
     *
     * @param resource        the resource to be converted.
     * @param model           the RDF4J model that is being read.
     * @param topLevelObjects the top-level JSON-LD objects that have been constructed so far.
     * @return a JSON-LD value representing the resource.
     */
    private def rdf4jResourceToJsonLDValue(resource: rdf4j.model.Resource,
                                           model: rdf4j.model.Model,
                                           topLevelObjects: collection.mutable.Map[rdf4j.model.Resource, JsonLDObject])
                                          (implicit stringFormatter: StringFormatter): JsonLDValue = {
        // Have we already made a top-level JSON-LD object for this entity?
        topLevelObjects.get(resource) match {
            case Some(jsonLDObject) =>
                // Yes. Remove it from the top level and inline it here.
                topLevelObjects -= resource
                jsonLDObject

            case None =>
                // No. Is it a Knora ontology entity?
                resource match {
                    case iri: rdf4j.model.IRI if iri.stringValue.toSmartIri.isKnoraDefinitionIri =>
                        // Yes. Just use its IRI, because we don't inline ontology entities.
                        iriToJsonLDObject(iri.stringValue)

                    case _ =>
                        // It's not a Knora ontology entity. Is it still in the model?
                        if (model.contains(resource, null, null)) {
                            // Yes. Recurse to get it, and inline it here.

                            val resourceStatements: Set[rdf4j.model.Statement] = model.filter(resource, null, null).asScala.toSet

                            entityToJsonLDObject(
                                subj = resource,
                                statements = resourceStatements,
                                model = model,
                                topLevelObjects = topLevelObjects
                            )
                        } else {
                            // It's not in the model. Maybe it was already inlined somewhere else,
                            // or maybe it wasn't provided in the model. Does it have an IRI?
                            resource match {
                                case iri: rdf4j.model.IRI =>
                                    // Yes. Just use that.
                                    iriToJsonLDObject(iri.stringValue)

                                case blankNode: rdf4j.model.BNode =>
                                    // No, it's a blank node. This shouldn't happen.
                                    throw InvalidRdfException(s"Blank node ${blankNode.stringValue} was not found or is referenced in more than one place")

                                case _ =>
                                    // Other resource types aren't supported.
                                    throw InvalidRdfException(s"Unexpected RDF resource: ${resource.stringValue}")
                            }
                        }
                }
        }
    }
}
