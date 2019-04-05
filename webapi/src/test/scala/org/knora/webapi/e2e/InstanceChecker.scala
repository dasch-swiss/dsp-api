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

import java.net.URLEncoder

import org.knora.webapi.messages.v2.responder.ontologymessages.{ClassInfoContentV2, InputOntologyV2, PropertyInfoContentV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.{SmartIri, StringFormatter}
import org.knora.webapi.{IRI, OntologyConstants}
import spray.json._

/**
  * Constructs an [[InstanceChecker]] for a Knora response format.
  */
object InstanceChecker {
    /**
      * Returns an [[InstanceChecker]] for Knora responses in JSON format.
      */
    def getJsonChecker: InstanceChecker = new InstanceChecker(new JsonInstanceInspector)

    /**
      * Returns an [[InstanceChecker]] for Knora responses in JSON-LD format.
      */
    def getJsonLDChecker: InstanceChecker = new InstanceChecker(new JsonLDInstanceInspector)
}

/**
  * A test utility for checking whether an instance of an RDF class returned in a Knora response corresponds to the
  * the class definition.
  *
  * @param instanceInspector an [[InstanceInspector]] for working with instances in a particular format.
  */
class InstanceChecker(instanceInspector: InstanceInspector) {

    private case class Definitions(classDefs: Map[SmartIri, ClassInfoContentV2] = Map.empty,
                                   propertyDefs: Map[SmartIri, PropertyInfoContentV2] = Map.empty)

    /**
      * Checks that an instance of an RDF class eturned in a Knora response corresponds to the class definition.
      *
      * @param classIri         the class IRI.
      * @param instanceResponse a Knora response containing the instance to be checked.
      * @param knoraRouteGet    a function that takes a Knora API URL path and returns a response from Knora.
      */
    def check(classIri: SmartIri, instanceResponse: String, knoraRouteGet: String => String): Unit = {
        val instanceElement = instanceInspector.toElement(instanceResponse)
        val definitions = getDefinitions(classIri = classIri, knoraRouteGet = knoraRouteGet)
        checkRec(classIri = classIri, instanceElement = instanceElement, definitions = definitions)
    }

    /**
      * Recursively checks whether instance elements and child elements correspond to their class definitions.
      *
      * @param classIri        the class IRI.
      * @param instanceElement the instance to be checked.
      * @param definitions     definitions of the class and any other relevant classes and properties.
      */
    private def checkRec(classIri: SmartIri, instanceElement: InstanceElement, definitions: Definitions): Unit = {
        
    }

    private def getDefinitions(classIri: SmartIri, knoraRouteGet: String => String): Definitions = {
        getDefinitionsRec(classIri = classIri, knoraRouteGet = knoraRouteGet, definitions = Definitions())
    }

    private def getDefinitionsRec(classIri: SmartIri, knoraRouteGet: String => String, definitions: Definitions): Definitions = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Get the definition of classIri.
        val classDef: ClassInfoContentV2 = getClassDef(classIri = classIri, knoraRouteGet = knoraRouteGet)

        // Get the IRIs of the Knora properties on which that class has cardinalities.
        val propertyIrisFromCardinalities: Set[SmartIri] = (classDef.directCardinalities.keySet -- definitions.propertyDefs.keySet).filter(_.isKnoraApiV2EntityIri)

        // Get the definitions of those properties.
        val propertyDefsFromCardinalities: Map[SmartIri, PropertyInfoContentV2] = propertyIrisFromCardinalities.map {
            propertyIri =>
                propertyIri -> getPropertyDef(propertyIri = propertyIri, knoraRouteGet = knoraRouteGet)
        }.toMap

        // Get the IRIs of the object types of those properties.
        val classIrisFromObjectTypes: Set[SmartIri] = propertyDefsFromCardinalities.values.foldLeft(Set.empty[SmartIri]) {
            case (acc, propertyDef) =>
                propertyDef.getPredicateIriObject(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri) match {
                    case Some(objectType) => acc + objectType
                    case None => acc
                }
        } -- definitions.classDefs.keySet - classIri

        // Update definitions with the class and property definitions we've got so far.
        val updatedDefs: Definitions = definitions.copy(
            classDefs = definitions.classDefs + (classIri -> classDef),
            propertyDefs = definitions.propertyDefs ++ propertyDefsFromCardinalities
        )

        // Recursively add the definitions of the classes that we identified as object types.
        classIrisFromObjectTypes.foldLeft(updatedDefs) {
            case (acc, classIriFromObjectType) =>
                val recDefinitions: Definitions = getDefinitionsRec(
                    classIri = classIriFromObjectType,
                    knoraRouteGet = knoraRouteGet,
                    definitions = updatedDefs
                )

                acc.copy(
                    classDefs = acc.classDefs ++ recDefinitions.classDefs,
                    propertyDefs = acc.propertyDefs ++ recDefinitions.propertyDefs
                )
        }
    }

    private def getClassDef(classIri: SmartIri, knoraRouteGet: String => String): ClassInfoContentV2 = {
        val urlPath = s"/v2/ontologies/classes/${URLEncoder.encode(classIri.toString, "UTF-8")}"
        val classDefStr: String = knoraRouteGet(urlPath)
        InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(classDefStr)).classes(classIri)
    }

    private def getPropertyDef(propertyIri: SmartIri, knoraRouteGet: String => String): PropertyInfoContentV2 = {
        val urlPath = s"/v2/ontologies/properties/${URLEncoder.encode(propertyIri.toString, "UTF-8")}"
        val propertyDefStr: String = knoraRouteGet(urlPath)
        InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(propertyDefStr)).properties(propertyIri)
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
      * Converts a Knora response representing a class instance to an [[InstanceElement]].
      */
    def toElement(instanceResponse: String): InstanceElement

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
        val elementTypeIriStr: IRI = elementTypeIri.toString

        element.elementType == elementTypeIriStr ||
            (element.elementType == OntologyConstants.Xsd.Integer && elementTypeIriStr == OntologyConstants.Xsd.Int)
    }
}
