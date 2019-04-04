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

package org.knora.webapi.e2e

import org.knora.webapi.messages.v2.responder.ontologymessages.{ReadClassInfoV2, ReadPropertyInfoV2}
import org.knora.webapi.util.SmartIri
import org.knora.webapi.util.jsonld._
import org.knora.webapi.{IRI, OntologyConstants}
import spray.json._

/**
  * A test utility for checking whether an instance of an RDF class returned in a Knora response corresponds to the
  * the class definition.
  *
  * @param instanceInspector an [[InstanceInspector]] for working with instances in a particular format.
  */
class InstanceChecker(instanceInspector: InstanceInspector) {
    /**
      * Checks whether an instance of an RDF class returned in a Knora response corresponds to the
      * * the class definition.
      *
      * @param classDef        the class definition.
      * @param propertyDefs    the definitions of the properties used in the class.
      * @param instanceElement an [[InstanceElement]] representing the instance to be checked.
      */
    def checkInstance(classDef: ReadClassInfoV2, propertyDefs: Map[SmartIri, ReadPropertyInfoV2], instanceElement: InstanceElement): Unit = {

    }
}

/**
  * Represents an instance of an RDF class, or an element of such an instance.
  *
  * @param elementType the element type. This is an opaque string that only the [[InstanceInspector]] needs
  *                    to understand.
  * @param children    a map of property names to child elements.
  */
case class InstanceElement(elementType: String, children: Map[String, Vector[InstanceElement]] = Map.empty)

/**
  * A trait for classes that help [[InstanceChecker]] check instances in different formats.
  */
trait InstanceInspector {
    /**
      * Converts a Knora response to an [[InstanceElement]].
      */
    def toElement(response: String): InstanceElement

    /**
      * Converts an RDF property IRI to the property name used in instances.
      */
    def propertyIriToInstancePropertyName(propertyIri: SmartIri): String

    /**
      * Checks, as far as possible, whether the type of an instance is compatible with the type IRI of its class.
      */
    def elementHasCompatibleType(element: InstanceElement, elementTypeIri: SmartIri): Boolean
}

/**
  * Constants for working with instances parsed from JSON.
  */
object JsonInstanceInspector {
    val STRING = "string"
    val NUMBER = "number"
    val BOOLEAN = "boolean"
    val OBJECT = "object"

    val LiteralTypeMap: Map[String, Set[IRI]] = Map(
        STRING -> Set(OntologyConstants.Xsd.String, OntologyConstants.Xsd.DateTimeStamp, OntologyConstants.Xsd.Uri),
        NUMBER -> Set(OntologyConstants.Xsd.Int, OntologyConstants.Xsd.Integer, OntologyConstants.Xsd.NonNegativeInteger, OntologyConstants.Xsd.Decimal),
        BOOLEAN -> Set(OntologyConstants.Xsd.Boolean)
    )

    val LiteralTypeIris: Set[IRI] = LiteralTypeMap.values.flatten.toSet
}

/**
  * An [[InstanceInspector]] that works with Knora responses in JSON format.
  */
class JsonInstanceInspector extends InstanceInspector {

    import JsonInstanceInspector._

    override def toElement(response: String): InstanceElement = {
        jsObjectToElement(JsonParser(response).asJsObject)
    }

    private def jsValueToElements(jsValue: JsValue): Vector[InstanceElement] = {
        jsValue match {
            case jsObject: JsObject => Vector(jsObjectToElement(jsObject))
            case jsArray: JsArray => jsArray.elements.flatMap(jsValue => jsValueToElements(jsValue))
            case _: JsString => Vector(InstanceElement(STRING))
            case _: JsNumber => Vector(InstanceElement(NUMBER))
            case _: JsBoolean => Vector(InstanceElement(BOOLEAN))
        }
    }

    private def jsObjectToElement(jsObject: JsObject): InstanceElement = {
        val children = jsObject.fields.map {
            case (key: String, jsValue: JsValue) => key -> jsValueToElements(jsValue)
        }

        InstanceElement(OBJECT, children)
    }

    override def propertyIriToInstancePropertyName(propertyIri: SmartIri): String = {
        propertyIri.getEntityName
    }

    override def elementHasCompatibleType(element: InstanceElement, elementTypeIri: SmartIri): Boolean = {
        LiteralTypeMap.get(element.elementType) match {
            case Some(typeIris) => typeIris.contains(elementTypeIri.toString)
            case None => !LiteralTypeIris.contains(elementTypeIri.toString)
        }
    }
}

/**
  * An [[InstanceInspector]] that works with Knora responses in JSON-LD format.
  */
class JsonLDInstanceInspector extends InstanceInspector {
    override def toElement(response: String): InstanceElement = {
        jsonLDObjectToElement(JsonLDUtil.parseJsonLD(response).body)
    }

    private def jsonLDValueToElements(jsonLDValue: JsonLDValue): Vector[InstanceElement] = {
        jsonLDValue match {
            case jsonLDObject: JsonLDObject =>
                if (jsonLDObject.isStringWithLang) {
                    Vector(InstanceElement(OntologyConstants.Xsd.String))
                } else if (jsonLDObject.isDatatypeValue) {
                    Vector(InstanceElement(jsonLDObject.requireString(JsonLDConstants.TYPE)))
                } else {
                    Vector(jsonLDObjectToElement(jsonLDObject))
                }

            case jsonLDArray: JsonLDArray => jsonLDArray.value.flatMap(jsonLDValue => jsonLDValueToElements(jsonLDValue)).toVector

            case _: JsonLDString => Vector(InstanceElement(OntologyConstants.Xsd.String))

            case _: JsonLDInt => Vector(InstanceElement(OntologyConstants.Xsd.Integer))

            case _: JsonLDBoolean => Vector(InstanceElement(OntologyConstants.Xsd.Boolean))
        }
    }

    private def jsonLDObjectToElement(jsonLDObject: JsonLDObject): InstanceElement = {
        val elementType = jsonLDObject.requireString(JsonLDConstants.TYPE)

        val children = jsonLDObject.value.map {
            case (key, jsonLDValue: JsonLDValue) => key -> jsonLDValueToElements(jsonLDValue)
        }

        InstanceElement(elementType, children)
    }

    override def propertyIriToInstancePropertyName(propertyIri: SmartIri): String = {
        propertyIri.toString
    }

    override def elementHasCompatibleType(element: InstanceElement, elementTypeIri: SmartIri): Boolean = {
        element.elementType == elementTypeIri.toString
    }
}
