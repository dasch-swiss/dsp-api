/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.util.rdf

import java.io.{StringReader, StringWriter}
import java.util
import java.util.UUID

import com.apicatalog.jsonld._
import com.apicatalog.jsonld.document._
import javax.json._
import javax.json.stream.JsonGenerator
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.knora.webapi._
import org.knora.webapi.exceptions._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}

import scala.collection.JavaConverters._
import scala.util.control.Exception._

/*

The classes in this file provide a Scala API for formatting and parsing JSON-LD, and for converting
between JSON-LD documents and RDF models. These classes also provide Knora-specific JSON-LD functionality
to facilitate reading data from Knora API requests and constructing Knora API responses.

The implementation uses the javax.json API and a Java implementation of the JSON-LD API
<https://www.w3.org/TR/json-ld11-api/> (currently <https://github.com/filip26/titanium-json-ld>).

 */

/**
  * Constant keywords used in JSON-LD.
  */
object JsonLDKeywords {
  val CONTEXT: String = "@context"

  val ID: String = "@id"

  val TYPE: String = "@type"

  val GRAPH: String = "@graph"

  val LANGUAGE: String = "@language"

  val VALUE: String = "@value"

  /**
    * The set of JSON-LD keywords that are supported by [[JsonLDUtil]].
    */
  val allSupported: Set[String] = Set(CONTEXT, ID, TYPE, GRAPH, LANGUAGE, VALUE)
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
      case _                     => 0
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
      case _                  => 0
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
      case _                          => 0
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
    * Flattens this JSON-LD object by extracting inlined entities with IRIs and replacing them with
    * references to their IRI.
    *
    * @param entitiesToAddToTopLevel inlined entities that have been extracted.
    * @param isAtTopLevel            `true` if this JSON-LD object is the top level object, or if it is an element
    *                                of a `@graph`.
    * @return a flattened copy of this JSON-LD object.
    */
  def flattened(entitiesToAddToTopLevel: collection.mutable.Set[JsonLDObject], isAtTopLevel: Boolean): JsonLDObject = {
    val thisWithFlattenedContent = JsonLDObject {
      // Flatten the object of each predicate.
      value.map {
        case (pred: String, obj: JsonLDValue) =>
          // What type of object does this predicate have?
          val flatObj: JsonLDValue = obj match {
            case jsonLDObject: JsonLDObject =>
              // A JSON-LD object. Flatten its content. It's not at the top level, so if it has an IRI,
              // add it to the top level and refer to it by IRI here.
              jsonLDObject.flattened(
                entitiesToAddToTopLevel = entitiesToAddToTopLevel,
                isAtTopLevel = false
              )

            case jsonLDArray: JsonLDArray =>
              // An array. Flatten its elements. If the array is the object of @graph, don't
              // move its elements to the top level, because they're already at the top level.
              jsonLDArray.flattened(
                entitiesToAddToTopLevel = entitiesToAddToTopLevel,
                isAtTopLevel = pred == JsonLDKeywords.GRAPH
              )

            case _ =>
              // Something else. Leave it as is.
              obj
          }

          pred -> flatObj
      }
    }

    // Is this JSON-LD object already at the top level?
    if (isAtTopLevel) {
      // Yes. Just return it with flattened content.
      thisWithFlattenedContent
    } else {
      // No. Does it have an IRI?
      if (isEntityWithIri) {
        // Yes. Add it to the top level, and return a reference to its IRI.
        entitiesToAddToTopLevel += thisWithFlattenedContent
        JsonLDUtil.iriToJsonLDObject(thisWithFlattenedContent.requireString(JsonLDKeywords.ID))
      } else {
        // No, it's a blank node or some other type of data. Just return it with flattened content.
        thisWithFlattenedContent
      }
    }
  }

  /**
    * Recursively adds the contents of a JSON-LD entity to an [[RdfModel]].
    *
    * @param model the model being constructed.
    * @return the subject of the contents of this JSON-LD object (an IRI or a blank node).
    */
  def addToModel(model: RdfModel)(implicit stringFormatter: StringFormatter): RdfResource = {
    val nodeFactory: RdfNodeFactory = model.getNodeFactory

    // If this object has an @id, use it as the subject, otherwise generate a blank node ID.
    val rdfSubj: RdfResource = maybeStringWithValidation(JsonLDKeywords.ID, stringFormatter.toSmartIriWithErr) match {
      case Some(subjectIri: SmartIri) =>
        // It's an IRI.
        nodeFactory.makeIriNode(subjectIri.toString)

      case None =>
        // Generate a blank node ID.
        nodeFactory.makeBlankNode
    }

    // Add rdf:type statements to the model.
    addRdfTypesToModel(model = model, rdfSubj = rdfSubj)

    // If this object contains a @graph, add its contents to the model, without linking to them
    // from this object.
    addGraphToModel(model)

    // Add the IRI predicates and their objects.
    val iriPredicates: Set[IRI] = value.keySet -- JsonLDKeywords.allSupported

    for (pred: IRI <- iriPredicates) {
      val rdfPred: IriNode = nodeFactory.makeIriNode(pred)
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
    * Adds `rdf:type` statements to an [[RdfModel]] to specify the types of a JSON-LD entity.
    *
    * @param model   the model being constructed.
    * @param rdfSubj the subject of this JSON-LD object.
    */
  private def addRdfTypesToModel(model: RdfModel, rdfSubj: RdfResource)(
      implicit stringFormatter: StringFormatter): Unit = {
    val nodeFactory: RdfNodeFactory = model.getNodeFactory

    def addRdfType(typeIri: JsonLDString): Unit = {
      model.add(
        subj = rdfSubj,
        pred = nodeFactory.makeIriNode(OntologyConstants.Rdf.Type),
        obj = nodeFactory.makeIriNode(typeIri.value)
      )
    }

    def invalidType: Nothing = throw InvalidJsonLDException("The objects of @type must be strings")

    // Does this JSON-LD object have a @type?
    value.get(JsonLDKeywords.TYPE) match {
      case Some(rdfTypes: JsonLDValue) =>
        // Yes. How many types does it have?
        rdfTypes match {
          case typeIri: JsonLDString =>
            // Just one.
            addRdfType(typeIri)

          case jsonLDArray: JsonLDArray =>
            // More than one.
            for (elem <- jsonLDArray.value) {
              // Is the object of @type a JsonLDString?
              elem match {
                case typeIri: JsonLDString =>
                  // Yes. Add the type to the model.
                  addRdfType(typeIri)

                case _ =>
                  // No. The JSON-LD is invalid.
                  invalidType
              }
            }

          case _ => invalidType
        }

      case None =>
        // This JSON-LD object doesn't have a @type.
        ()
    }
  }

  /**
    * Adds the contents of `@graph` to an [[RdfModel]].
    *
    * @param model the model being constructed.
    */
  private def addGraphToModel(model: RdfModel)(implicit stringFormatter: StringFormatter): Unit = {
    def invalidGraph: Nothing =
      throw InvalidJsonLDException("The object of @graph must be a JSON-LD array of JSON-LD objects")

    // Does this JSON-LD object have a @graph?
    value.get(JsonLDKeywords.GRAPH) match {
      case Some(graphContents: JsonLDValue) =>
        // Yes. The object of @graph should be an array.
        graphContents match {
          case jsonLDArray: JsonLDArray =>
            // Add each of the array's elements to the model.
            for (elem <- jsonLDArray.value) {
              // Is the element a JsonLDObject?
              elem match {
                case jsonLDObject: JsonLDObject =>
                  // Yes. Add it to the model.
                  jsonLDObject.addToModel(model)

                case _ =>
                  // No. The JSON-LD is invalid.
                  invalidGraph
              }
            }

          case _ => invalidGraph
        }

      case None =>
        // This JSON-LD object doesn't have a @graph.
        ()
    }
  }

  /**
    * Recursively adds a [[JsonLDValue]] to an [[RdfModel]], using the specified subject and predicate.
    *
    * @param model       the model being constructed.
    * @param rdfSubj     the subject.
    * @param rdfPred     the predicate.
    * @param jsonLDValue the value to be added.
    */
  private def addJsonLDValueToModel(model: RdfModel, rdfSubj: RdfResource, rdfPred: IriNode, jsonLDValue: JsonLDValue)(
      implicit stringFormatter: StringFormatter): Unit = {
    val nodeFactory: RdfNodeFactory = model.getNodeFactory

    // Which type of JSON-LD value is this?
    jsonLDValue match {
      case jsonLDObject: JsonLDObject =>
        // It's a JSON-LD object. What does it represent?
        val rdfObj: RdfNode = if (jsonLDObject.isIri) {
          // An IRI.
          nodeFactory.makeIriNode(jsonLDObject.requireString(JsonLDKeywords.ID))
        } else if (jsonLDObject.isDatatypeValue) {
          // A literal.
          nodeFactory.makeDatatypeLiteral(
            value = jsonLDObject.requireString(JsonLDKeywords.VALUE),
            datatype = jsonLDObject.requireString(JsonLDKeywords.TYPE)
          )
        } else if (jsonLDObject.isStringWithLang) {
          // A string literal with a language tag.
          nodeFactory.makeStringWithLanguage(
            value = jsonLDObject.requireString(JsonLDKeywords.VALUE),
            language = jsonLDObject.requireString(JsonLDKeywords.LANGUAGE)
          )
        } else {
          // Triples. Recurse to add its contents to the model.
          jsonLDObject.addToModel(model)
        }

        // Add a triple linking the subject to the object.
        model.add(
          subj = rdfSubj,
          pred = rdfPred,
          obj = rdfObj
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
          subj = rdfSubj,
          pred = rdfPred,
          obj = nodeFactory.makeStringLiteral(jsonLDString.value)
        )

      case jsonLDBoolean: JsonLDBoolean =>
        // It's a boolean literal.
        model.add(
          rdfSubj,
          rdfPred,
          nodeFactory.makeBooleanLiteral(jsonLDBoolean.value)
        )

      case jsonLDInt: JsonLDInt =>
        model.add(
          subj = rdfSubj,
          pred = rdfPred,
          obj = nodeFactory.makeDatatypeLiteral(
            value = jsonLDInt.value.toString,
            datatype = OntologyConstants.Xsd.Integer
          )
        )
    }
  }

  /**
    * Returns `true` if this JSON-LD object represents an RDF entity with an IRI,
    * i.e. if it has an `@id` and a `@type`.
    */
  def isEntityWithIri: Boolean = {
    Set(JsonLDKeywords.ID, JsonLDKeywords.TYPE).subsetOf(value.keySet)
  }

  /**
    * Returns `true` if this JSON-LD object represents an IRI value.
    */
  def isIri: Boolean = {
    value.keySet == Set(JsonLDKeywords.ID)
  }

  /**
    * Returns `true` if this JSON-LD object represents a string literal with a language tag.
    */
  def isStringWithLang: Boolean = {
    value.keySet == Set(JsonLDKeywords.VALUE, JsonLDKeywords.LANGUAGE)
  }

  /**
    * Returns `true` if this JSON-LD object represents a datatype value.
    */
  def isDatatypeValue: Boolean = {
    value.keySet == Set(JsonLDKeywords.TYPE, JsonLDKeywords.VALUE)
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
      val id: IRI = requireString(JsonLDKeywords.ID)
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
      val datatype: IRI = requireString(JsonLDKeywords.TYPE)

      if (datatype != expectedDatatype.toString) {
        throw BadRequestException(s"Expected datatype value of type <$expectedDatatype>, found <$datatype>")
      }

      val value: String = requireString(JsonLDKeywords.VALUE)
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
      case other             => throw BadRequestException(s"Invalid $key: $other (string expected)")
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
      case other             => throw BadRequestException(s"Invalid $key: $other (string expected)")
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
    maybeString(key).map { str =>
      validationFun(str, throw BadRequestException(s"Invalid $key: $str"))
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
  def requireDatatypeValueInObject[T](key: String,
                                      expectedDatatype: SmartIri,
                                      validationFun: (String, => Nothing) => T): T = {
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
  def maybeDatatypeValueInObject[T](key: String,
                                    expectedDatatype: SmartIri,
                                    validationFun: (String, => Nothing) => T): Option[T] = {
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
      case other             => throw BadRequestException(s"Invalid $key: $other (object expected)")
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
      case other             => throw BadRequestException(s"Invalid $key: $other (object expected)")
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
      case other            => JsonLDArray(Seq(other))
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
      case other            => JsonLDArray(Seq(other))
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
      case other          => throw BadRequestException(s"Invalid $key: $other (integer expected)")
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
      case other          => throw BadRequestException(s"Invalid $key: $other (integer expected)")
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
      case other              => throw BadRequestException(s"Invalid $key: $other (boolean expected)")
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
      case other              => throw BadRequestException(s"Invalid $key: $other (boolean expected)")
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

    val iri = requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.toSmartIriWithErr)

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

    val maybeIri: Option[SmartIri] = maybeStringWithValidation(JsonLDKeywords.ID, stringFormatter.toSmartIriWithErr)

    maybeIri.foreach { iri =>
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

    val typeIri = requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr)

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

    val resourceProps: Map[IRI, JsonLDValue] = value - JsonLDKeywords.ID - JsonLDKeywords.TYPE

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
          case _                          => throw BadRequestException(s"Invalid value for $propertyIri")
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
      case _                       => false
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
    * Flattens this JSON-LD array by extracting inlined entities with IRIs and replacing them with
    * references to their IRI.
    *
    * @param entitiesToAddToTopLevel inlined entities that have been extracted.
    * @param isAtTopLevel            `true` if this array is the object of `@graph` at the top level of the document.
    * @return a flattened copy of this JSON-LD array.
    */
  def flattened(entitiesToAddToTopLevel: collection.mutable.Set[JsonLDObject],
                isAtTopLevel: Boolean = false): JsonLDArray = {
    JsonLDArray {
      // Flatten the JSON-LD objects that are elements of the array.
      value.map { elem: JsonLDValue =>
        // What type of element is it?
        elem match {
          case jsonLDObject: JsonLDObject =>
            // A JSON-LD object. Flatten its content. If it has an IRI, move it to the top level,
            // unless this array is a @graph, meaning that the JSON-LD object is already
            // at the top level.
            jsonLDObject.flattened(
              entitiesToAddToTopLevel = entitiesToAddToTopLevel,
              isAtTopLevel = isAtTopLevel
            )

          case _ =>
            // Something else. Leave it as is.
            elem
        }
      }
    }
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
        val lang = obj.requireStringWithValidation(JsonLDKeywords.LANGUAGE, stringFormatter.toSparqlEncodedString)

        if (!LanguageCodes.SupportedLanguageCodes(lang)) {
          throw BadRequestException(s"Unsupported language code: $lang")
        }

        val text = obj.requireStringWithValidation(JsonLDKeywords.VALUE, stringFormatter.toSparqlEncodedString)
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
  * @param isFlat  `true` if this JSON-LD document has been constructed as a flat document, i.e.
  *                without inlining entities that have IRIs.
  */
case class JsonLDDocument(body: JsonLDObject,
                          context: JsonLDObject = JsonLDObject(Map.empty[String, JsonLDValue]),
                          isFlat: Boolean = false) {

  /**
    * A convenience function that calls `body.requireString`.
    */
  def requireString(key: String): String = body.requireString(key)

  /**
    * A convenience function that calls `body.requireStringWithValidation`.
    */
  def requireStringWithValidation[T](key: String, validationFun: (String, => Nothing) => T): T =
    body.requireStringWithValidation(key, validationFun)

  /**
    * A convenience function that calls `body.maybeString`.
    */
  def maybeString(key: String): Option[String] = body.maybeString(key)

  /**
    * A convenience function that calls `body.maybeStringWithValidation`.
    */
  def maybeStringWithValidation[T](key: String, validationFun: (String, => Nothing) => T): Option[T] =
    body.maybeStringWithValidation(key, validationFun)

  /**
    * A convenience function that calls `body.requireIriInObject`.
    */
  def requireIriInObject[T](key: String, validationFun: (String, => Nothing) => T): T =
    body.requireIriInObject(key, validationFun)

  /**
    * A convenience function that calls `body.maybeIriInObject`.
    */
  def maybeIriInObject[T](key: String, validationFun: (String, => Nothing) => T): Option[T] =
    body.maybeIriInObject(key, validationFun)

  /**
    * A convenience function that calls `body.requireDatatypeValueInObject`.
    */
  def requireDatatypeValueInObject[T](key: String,
                                      expectedDatatype: SmartIri,
                                      validationFun: (String, => Nothing) => T): T =
    body.requireDatatypeValueInObject(
      key = key,
      expectedDatatype = expectedDatatype,
      validationFun = validationFun
    )

  /**
    * A convenience function that calls `body.maybeDatatypeValueInObject`.
    */
  def maybeDatatypeValueInObject[T](key: String,
                                    expectedDatatype: SmartIri,
                                    validationFun: (String, => Nothing) => T): Option[T] =
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
    * Flattens this JSON-LD document by moving inlined entities with IRIs to the top level.
    *
    * @return a flattened copy of this JSON-LD document.
    */
  def flattened: JsonLDDocument = {
    // Is this JSON-LD document already flat?
    if (isFlat) {
      // Yes. Just return it.
      this
    } else {
      // No. Make a mutable Set to collect inlined entities that will be moved to the top level.
      val entitiesToAddToTopLevel: collection.mutable.Set[JsonLDObject] = collection.mutable.Set.empty

      // Flatten the content of the document.
      val flattenedContent: JsonLDObject = body.flattened(
        entitiesToAddToTopLevel = entitiesToAddToTopLevel,
        isAtTopLevel = true
      )

      // Are there any entities to add to the top level?
      val allContent = if (entitiesToAddToTopLevel.nonEmpty) {
        // Yes. Is there a top-level entity, i.e. does the flattened top level have any content
        // besides @graph?

        val topLevelWithoutGraph: Map[String, JsonLDValue] = flattenedContent.value - JsonLDKeywords.GRAPH

        val maybeTopLevelObject: Vector[JsonLDObject] = if (topLevelWithoutGraph.nonEmpty) {
          // Yes. Make a JsonLDObject for that content.
          Vector(JsonLDObject(topLevelWithoutGraph))
        } else {
          // No.
          Vector.empty
        }

        // Make a @graph containing the entities to add to the top level, the elements of the existing @graph
        // if there is one, and the existing top-level entity if there is one.
        val existingGraphElements: Seq[JsonLDValue] =
          flattenedContent.maybeArray(JsonLDKeywords.GRAPH).map(_.value).getOrElse(Seq.empty)
        JsonLDObject(Map(
          JsonLDKeywords.GRAPH -> JsonLDArray(maybeTopLevelObject ++ existingGraphElements ++ entitiesToAddToTopLevel)))
      } else {
        // No. Just keep the existing @graph, if there is one, with the existing top-level entity.
        flattenedContent
      }

      copy(
        body = allContent,
        isFlat = true
      )
    }
  }

  /**
    * Converts this JSON-LD document to its compacted representation.
    *
    * @param flatten `true` if a flat JSON-LD document should be returned.
    */
  def makeCompactedJavaxJsonObject(flatten: Boolean = false): JsonObject = {
    // Flatten the document if requested.
    val documentFlattenedIfRequested: JsonLDDocument = if (flatten) {
      flattened
    } else {
      this
    }

    val bodyAsTitaniumJsonDocument: JsonDocument = JsonDocument.of(documentFlattenedIfRequested.body.toJavaxJsonValue)
    val contextAsTitaniumJsonDocument: JsonDocument = JsonDocument.of(context.toJavaxJsonValue)
    JsonLd.compact(bodyAsTitaniumJsonDocument, contextAsTitaniumJsonDocument).get
  }

  /**
    * Formats this JSON-LD document as a string, using the specified [[JsonWriterFactory]].
    *
    * @param jsonWriterFactory a [[JsonWriterFactory]] configured with the desired options.
    * @param flatten           `true` if a flat JSON-LD document should be returned.
    * @return the formatted document.
    */
  private def formatWithJsonWriterFactory(jsonWriterFactory: JsonWriterFactory, flatten: Boolean): String = {
    val compactedJavaxJsonObject: JsonObject = makeCompactedJavaxJsonObject(flatten)
    val stringWriter = new StringWriter()
    val jsonWriter = jsonWriterFactory.createWriter(stringWriter)
    jsonWriter.write(compactedJavaxJsonObject)
    jsonWriter.close()
    stringWriter.toString
  }

  /**
    * Converts this JSON-LD document to a pretty-printed JSON-LD string.
    *
    * @param flatten `true` if a flat JSON-LD document should be returned.
    * @return the formatted document.
    */
  def toPrettyString(flatten: Boolean = false): String = {
    val config = new util.HashMap[String, Boolean]()
    config.put(JsonGenerator.PRETTY_PRINTING, true)
    val jsonWriterFactory: JsonWriterFactory = Json.createWriterFactory(config)
    formatWithJsonWriterFactory(jsonWriterFactory = jsonWriterFactory, flatten = flatten)
  }

  /**
    * Converts this [[JsonLDDocument]] to a compact JSON-LD string.
    *
    * @param flatten `true` if a flat JSON-LD document should be returned.
    * @return the formatted document.
    */
  def toCompactString(flatten: Boolean = false): String = {
    val config = new util.HashMap[String, Boolean]()
    val jsonWriterFactory: JsonWriterFactory = Json.createWriterFactory(config)
    formatWithJsonWriterFactory(jsonWriterFactory = jsonWriterFactory, flatten = flatten)
  }

  /**
    * Converts this JSON-LD document to an [[RdfModel]].
    *
    * @param modelFactory an [[RdfModelFactory]].
    */
  def toRdfModel(modelFactory: RdfModelFactory): RdfModel = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    val model: RdfModel = modelFactory.makeEmptyModel

    // Add the prefixes and namespaces from the JSON-LD context to the model.
    for ((prefix: String, namespaceValue: JsonLDValue) <- context.value) {
      namespaceValue match {
        case jsonLDString: JsonLDString => model.setNamespace(prefix, jsonLDString.value)
        case _                          => throw InvalidJsonLDException("The keys and values of @context must be strings")
      }
    }

    // Recursively add the JSON-LD document body to the model.
    body.addToModel(model)
    model
  }
}

/**
  * A tool for working with JSON-LD.
  */
object JsonLDUtil {

  /**
    * Makes a JSON-LD context containing prefixes for Knora and other ontologies.
    *
    * @param fixedPrefixes                  a map of fixed prefixes (e.g. `rdfs` or `knora-base`) to namespaces.
    * @param knoraOntologiesNeedingPrefixes a set of IRIs of other Knora ontologies that need prefixes.
    * @return a JSON-LD context.
    */
  def makeContext(fixedPrefixes: Map[String, String],
                  knoraOntologiesNeedingPrefixes: Set[SmartIri] = Set.empty): JsonLDObject = {

    /**
      * Given a function that makes a prefix from a Knora ontology IRI, returns an association list in which
      * each element is a prefix associated with a namespace.
      *
      * @param prefixFun a function that makes a prefix from a Knora ontology IRI.
      * @return an association list in which each element is a prefix associated with a namespace.
      */
    def makeKnoraPrefixes(prefixFun: SmartIri => String): Seq[(String, String)] = {
      knoraOntologiesNeedingPrefixes.toSeq.map { ontology =>
        prefixFun(ontology) -> (ontology.toString + '#')
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
        // Yes. This shouldn't happen, so throw InconsistentRepositoryDataException.
        throw InconsistentRepositoryDataException(
          s"Can't make distinct prefixes for ontologies: ${(fixedPrefixes.values ++ knoraOntologiesNeedingPrefixes.map(
            _.toString)).mkString(", ")}")
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
    JsonLDObject(Map(JsonLDKeywords.ID -> JsonLDString(iri)))
  }

  /**
    * Given a predicate value and a language code, returns a JSON-LD object containing `@value` and `@language`
    * predicates.
    *
    * @param obj a predicate value.
    * @return a JSON-LD object containing `@value` and `@language` predicates.
    */
  def objectWithLangToJsonLDObject(obj: String, lang: String): JsonLDObject = {
    JsonLDObject(
      Map(
        JsonLDKeywords.VALUE -> JsonLDString(obj),
        JsonLDKeywords.LANGUAGE -> JsonLDString(lang)
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

    JsonLDObject(
      Map(
        JsonLDKeywords.VALUE -> JsonLDString(strValue),
        JsonLDKeywords.TYPE -> JsonLDString(datatype.toString)
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
    * Converts a [[JsonStructure]] to a [[JsonLDDocument]] with an empty context.
    *
    * @param jsonStructure the JSON to be converted.
    * @param flatten      `true` if a flat JSON-LD document should be returned.
    * @return a [[JsonLDDocument]].
    */
  def jsonToJsonLD(jsonStructure: JsonStructure, flatten: Boolean = false): JsonLDDocument = {
    // Convert the JsonStructure to a Titanium JsonDocument.
    val titaniumDocument: JsonDocument = JsonDocument.of(jsonStructure)

    // Use Titanium to compact the document with an empty context.
    val emptyContext = JsonDocument.of(Json.createObjectBuilder().build())
    val compactedJsonObject: JsonObject = JsonLd.compact(titaniumDocument, emptyContext).get

    // Convert the resulting javax.json.JsonObject to a JsonLDDocument.
    val jsonLDDocument: JsonLDDocument = javaxJsonObjectToJsonLDDocument(compactedJsonObject)

    // Was flat JSON-LD requested?
    if (flatten) {
      // Yes. Flatten the document.
      jsonLDDocument.flattened
    } else {
      // No. Leave it as is.
      jsonLDDocument
    }
  }

  /**
    * Parses a JSON-LD string as a [[JsonLDDocument]] with an empty context.
    *
    * @param jsonLDString the string to be parsed.
    * @param flatten      `true` if a flat JSON-LD document should be returned.
    * @return a [[JsonLDDocument]].
    */
  def parseJsonLD(jsonLDString: String, flatten: Boolean = false): JsonLDDocument = {
    // Parse the string into a javax.json.JsonStructure.
    val stringReader = new StringReader(jsonLDString)
    val jsonReader: JsonReader = Json.createReader(stringReader)
    val jsonStructure: JsonStructure = jsonReader.read()
    jsonToJsonLD(jsonStructure = jsonStructure, flatten = flatten)
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
        case JsonValue.TRUE         => JsonLDBoolean(true)
        case JsonValue.FALSE        => JsonLDBoolean(false)

        case jsonObject: JsonObject =>
          val content: Map[IRI, JsonLDValue] = jsonObject.keySet.asScala.toSet.map { key: IRI =>
            if (key.startsWith("@") && !JsonLDKeywords.allSupported.contains(key)) {
              throw BadRequestException(s"JSON-LD keyword $key is not supported")
            }

            key -> jsonValueToJsonLDValue(jsonObject.get(key))
          }.toMap

          JsonLDObject(content)

        case jsonArray: JsonArray =>
          val content: Seq[JsonLDValue] = jsonArray.asScala.map { elem =>
            jsonValueToJsonLDValue(elem)
          }.toVector

          JsonLDArray(content)

        case _ => throw BadRequestException(s"Unexpected type in JSON-LD input: $jsonValue")
      }
    }

    jsonValueToJsonLDValue(jsonObject) match {
      case obj: JsonLDObject => JsonLDDocument(body = obj, context = JsonLDObject(Map.empty[IRI, JsonLDValue]))
      case _                 => throw BadRequestException(s"Expected JSON-LD object: $jsonObject")
    }
  }

  /**
    * Converts an [[RdfModel]] to a [[JsonLDDocument]]. There can be more than one valid
    * way to nest objects in the converted JSON-LD. This implementation takes the following
    * approach:
    *
    * - Inline blank nodes wherever they are used.
    * - Nest each entity in the first encountered entity that refers to it, and refer to it by IRI elsewhere.
    * - Don't nest an entity with an IRI inside a blank node.
    * - Don't inline Knora ontology entities.
    * - After nesting, if more than one top-level entity remains, wrap them all in a `@graph`.
    *
    * An error is returned if the same blank node is used more than once.
    *
    * @param model      the [[RdfModel]] to be read.
    * @param flatJsonLD if `true`, produce a flat JSON-LD document.
    * @return the corresponding [[JsonLDDocument]].
    */
  def fromRdfModel(model: RdfModel, flatJsonLD: Boolean = false): JsonLDDocument = {
    if (model.getContexts.nonEmpty) {
      throw BadRequestException("Named graphs in JSON-LD are not supported")
    }

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    // Make a JSON-LD context from the model's namespaces.
    val contextMap: Map[IRI, JsonLDString] = model.getNamespaces.map {
      case (prefix, namespace) => prefix -> JsonLDString(namespace)
    }

    val context: JsonLDObject = JsonLDObject(contextMap)

    // Is the model empty?
    val body: JsonLDObject = if (model.isEmpty) {
      // Yes. Just make an empty JSON-LD object.
      JsonLDObject(Map.empty)
    } else {
      // Get the set of subjects in the model.
      val subjects: Set[RdfResource] = model.getSubjects

      // Make a Set to collect the subjects that have already been processed.
      val processedSubjects: collection.mutable.Set[RdfResource] = collection.mutable.Set.empty

      // Make a Map to collect the top-level entities. We first assume that an entity is at the top level,
      // and add it to this collection. Later, it can be inlined (nested inside another entity) and removed
      // from this collection.
      val topLevelEntities: collection.mutable.Map[RdfResource, JsonLDObject] = collection.mutable.Map.empty

      // Make a JSON-LD object for each entity, inlining them as we go along.
      for (subj: RdfResource <- subjects) {
        // Have we already processed this subject?
        if (!processedSubjects.contains(subj)) {
          // No. Get the statements about it.
          val statements: Set[Statement] = model.find(Some(subj), None, None).toSet

          // Make a JsonLDObject representing the entity and any nested entities.
          val jsonLDObject: JsonLDObject = entityToJsonLDObject(
            subj = subj,
            statements = statements,
            model = model,
            topLevelEntities = topLevelEntities,
            processedSubjects = processedSubjects,
            flatJsonLD = flatJsonLD
          )

          // Add it to the collection of top-level entities.
          topLevelEntities += (subj -> jsonLDObject)
        }
      }

      // Is there just one top-level entity?
      if (topLevelEntities.size == 1) {
        // Yes. Use it as the body of the document.
        topLevelEntities.values.head
      } else {
        // No. Make a @graph.
        JsonLDObject(Map(JsonLDKeywords.GRAPH -> JsonLDArray(topLevelEntities.values.toSeq)))
      }
    }

    JsonLDDocument(body = body, context = context, isFlat = flatJsonLD)
  }

  /**
    * Converts an RDF entity to a [[JsonLDObject]] representing the statements about the entity.
    *
    * @param subj              the subject of the entity.
    * @param statements        the statements representing the entity.
    * @param model             the [[RdfModel]] that is being read.
    * @param topLevelEntities  the top-level entities that have been constructed so far.
    * @param processedSubjects the subjects that have already been processed.
    * @param flatJsonLD        if `true`, produce flat JSON-LD.
    * @return the JSON-LD object that was constructed.
    */
  private def entityToJsonLDObject(subj: RdfResource,
                                   statements: Set[Statement],
                                   model: RdfModel,
                                   topLevelEntities: collection.mutable.Map[RdfResource, JsonLDObject],
                                   processedSubjects: collection.mutable.Set[RdfResource],
                                   flatJsonLD: Boolean)(implicit stringFormatter: StringFormatter): JsonLDObject = {
    // Mark the subject as processed.
    processedSubjects += subj

    // Does this entity have an IRI, or is it a blank node?
    val idContent: Map[String, JsonLDValue] = subj match {
      case iriNode: IriNode =>
        // It has an IRI. Use it for the @id.
        Map(JsonLDKeywords.ID -> JsonLDString(iriNode.iri))

      case _: BlankNode =>
        // It's a blank node. Don't make an @id.
        Map.empty[String, JsonLDValue]
    }

    // Group the statements by predicate.
    val groupedByPred: Map[IriNode, Set[Statement]] = statements.groupBy(_.pred)

    // Make JSON-LD content representing the predicates and their objects.
    val predsAndObjs: Map[IRI, JsonLDValue] = groupedByPred.keySet.map { pred: IriNode =>
      val predStatements: Set[Statement] = groupedByPred(pred)
      val predIri: IRI = pred.iri

      // Is the predicate rdf:type?
      val (jsonLDKey: String, jsonLDObjs: Vector[JsonLDValue]) = if (predIri == OntologyConstants.Rdf.Type) {
        // Yes. Add @type.
        val typeList: Vector[JsonLDString] = predStatements.map { statement =>
          statement.obj match {
            case iriNode: IriNode => JsonLDString(iriNode.iri)
            case other            => throw InvalidRdfException(s"Unexpected object of rdf:type: $other")
          }
        }.toVector

        JsonLDKeywords.TYPE -> typeList
      } else {
        // The predicate is not rdf:type. Convert its objects.
        val objs: Vector[JsonLDValue] = predStatements
          .map(_.obj)
          .map {
            case resource: RdfResource =>
              // The object is an entity. Recurse to get it and inline it here.
              referencedRdfResourceToJsonLDValue(
                resource = resource,
                model = model,
                topLevelEntities = topLevelEntities,
                referrerIsBlankNode = idContent.isEmpty,
                processedSubjects = processedSubjects,
                flatJsonLD = flatJsonLD
              )

            case literal: RdfLiteral => rdfLiteralToJsonLDValue(literal)
          }
          .toVector

        predIri -> objs
      }

      // Does the predicate have just one object?
      if (jsonLDObjs.size == 1) {
        // Yes.
        jsonLDKey -> jsonLDObjs.head
      } else {
        // No. Make a JSON-LD array.
        jsonLDKey -> JsonLDArray(jsonLDObjs)
      }
    }.toMap

    JsonLDObject(idContent ++ predsAndObjs)
  }

  /**
    * Converts an [[RdfLiteral]] to a [[JsonLDValue]].
    *
    * @param literal the literal to be converted.
    * @return the corresponding JSON-LD value.
    */
  private def rdfLiteralToJsonLDValue(literal: RdfLiteral)(implicit stringFormatter: StringFormatter): JsonLDValue = {
    literal match {
      case stringWithLanguage: StringWithLanguage =>
        objectWithLangToJsonLDObject(obj = stringWithLanguage.value, lang = stringWithLanguage.language)

      case datatypeLiteral: DatatypeLiteral =>
        // Is there a native JSON-LD type for this literal?
        val datatypeIri: IRI = datatypeLiteral.datatype
        val datatypeValue: String = datatypeLiteral.value

        datatypeIri match {
          case OntologyConstants.Xsd.String => JsonLDString(datatypeValue)

          case OntologyConstants.Xsd.Int | OntologyConstants.Xsd.Integer =>
            JsonLDInt(
              allCatch
                .opt(datatypeValue.toInt)
                .getOrElse(throw InvalidRdfException(s"Invalid integer: $datatypeValue")))

          case OntologyConstants.Xsd.Boolean =>
            JsonLDBoolean(
              allCatch
                .opt(datatypeValue.toBoolean)
                .getOrElse(throw InvalidRdfException(s"Invalid boolean: $datatypeValue")))

          case _ =>
            // There's no native JSON-LD type for this literal.
            // Make a JSON-LD object representing a datatype value.
            datatypeValueToJsonLDObject(value = datatypeValue, datatype = datatypeIri.toSmartIri)
        }
    }
  }

  /**
    * Given an [[RdfResource]] that is referred to by another entity, make a [[JsonLDValue]] to
    * represent the referenced resource. This will be either a complete entity for nesting, or just
    * the referenced resource's IRI.
    *
    * @param resource            the resource to be converted.
    * @param model               the [[RdfModel]] that is being read.
    * @param topLevelEntities    the top-level entities that have been constructed so far.
    * @param referrerIsBlankNode `true` if the referrer is a blank node. If the referenced resource has an IRI,
    *                            it will not be inlined.
    * @param processedSubjects   the subjects that have already been processed.
    * @param flatJsonLD          if `true` and the resource has an IRI, do not inline it, regardless of the referrer.
    * @return a JSON-LD value representing the resource.
    */
  private def referencedRdfResourceToJsonLDValue(
      resource: RdfResource,
      model: RdfModel,
      topLevelEntities: collection.mutable.Map[RdfResource, JsonLDObject],
      referrerIsBlankNode: Boolean,
      processedSubjects: collection.mutable.Set[RdfResource],
      flatJsonLD: Boolean)(implicit stringFormatter: StringFormatter): JsonLDValue = {

    /**
      * Inlines a resource if possible, otherwise calls the specified function.
      *
      * @param nonInliningFunction a function to be called if the resource cannot be inlined.
      * @return a [[JsonLDValue]] representing the resource.
      */
    def inlineResource(nonInliningFunction: => JsonLDValue): JsonLDValue = {
      // How we deal with circular references: the referenced resource is not yet in topLevelEntities, but it
      // is already marked as processed. Therefore we will return its IRI rather than inlining it.

      // Is this entity already in topLevelEntities?
      topLevelEntities.get(resource) match {
        case Some(jsonLDObject) =>
          // Yes. Remove it from the top level so it can be inlined.
          topLevelEntities -= resource
          jsonLDObject

        case None =>
          // No. See if it's in the model.
          val resourceStatements: Set[Statement] = model.find(Some(resource), None, None).toSet

          // Is it in the model and not yet marked as processed?
          if (resourceStatements.nonEmpty && !processedSubjects.contains(resource)) {
            // Yes. Recurse to get it so it can be inlined.
            entityToJsonLDObject(
              subj = resource,
              statements = resourceStatements,
              model = model,
              topLevelEntities = topLevelEntities,
              processedSubjects = processedSubjects,
              flatJsonLD = flatJsonLD
            )
          } else {
            // No. Do something else with it.
            nonInliningFunction
          }
      }
    }

    // Is this resource an IRI?
    resource match {
      case iriNode: IriNode =>
        // Yes. Are any of the following true?
        // - We were asked for flat JSON-LD.
        // - The resource IRI is a Knora definition IRI.
        // - The referrer is a blank node.
        if (flatJsonLD ||
            iriNode.iri.toSmartIri.isKnoraDefinitionIri ||
            referrerIsBlankNode) {
          // Yes. Don't try to inline the resource, just return its IRI.
          iriToJsonLDObject(iriNode.iri)
        } else {
          // No. Try to inline it.
          inlineResource(iriToJsonLDObject(iriNode.iri))
        }

      case _: BlankNode =>
        // It's a blank node. It should be possible to inline it. If not, return an empty blank node.
        inlineResource(JsonLDObject(Map.empty))
    }
  }
}
