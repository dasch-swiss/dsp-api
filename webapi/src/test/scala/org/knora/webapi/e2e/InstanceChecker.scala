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

import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality._
import org.knora.webapi.messages.v2.responder.ontologymessages.{ClassInfoContentV2, InputOntologyV2, KnoraApiV2WithValueObjectsTransformationRules, KnoraOutputParsingModeV2, PropertyInfoContentV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.{MessageUtil, OntologyUtil, SmartIri, StringFormatter}
import org.knora.webapi.{AssertionException, IRI, InternalSchema, OntologyConstants}
import spray.json._

/**
  * Constructs an [[InstanceChecker]] for a Knora response format.
  */
object InstanceChecker {

    /**
      * Represents Knora ontology definitions used to check class instances against their definitions.
      *
      * @param classDefs    relevant class definitions.
      * @param propertyDefs relevant property definitions.
      * @param subClassOf   A map in which each class IRI points to the full set of its base classes. A class is also
      *                     a subclass of itself.
      */
    case class Definitions(classDefs: Map[SmartIri, ClassInfoContentV2] = Map.empty,
                           propertyDefs: Map[SmartIri, PropertyInfoContentV2] = Map.empty,
                           subClassOf: Map[SmartIri, Set[SmartIri]] = Map.empty) {
        def getClassDef(classIri: SmartIri): ClassInfoContentV2 = {
            classDefs.getOrElse(classIri, throw AssertionException(s"No definition for class <$classIri>"))
        }

        def getPropertyDef(propertyIri: SmartIri): PropertyInfoContentV2 = {
            propertyDefs.getOrElse(propertyIri, throw AssertionException(s"No definition for property <$propertyIri>"))
        }

        def getBaseClasses(classIri: SmartIri): Set[SmartIri] = {
            subClassOf.getOrElse(classIri, Set.empty)
        }
    }

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

    import InstanceChecker.Definitions

    implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * Checks that an instance of an RDF class returned in a Knora response corresponds to the class definition.
      *
      * @param instanceResponse a Knora response containing the instance to be checked.
      * @param expectedClassIri the IRI of the expected class.
      * @param knoraRouteGet    a function that takes a Knora API URL path and returns a response from Knora.
      */
    def check(instanceResponse: String, expectedClassIri: SmartIri, knoraRouteGet: String => String): Unit = {
        val instanceElement: InstanceElement = instanceInspector.toElement(instanceResponse)
        val definitions: Definitions = getDefinitions(classIri = expectedClassIri, knoraRouteGet = knoraRouteGet)

        // A map of class IRIs to their immediate base classes.
        val directSubClassOfRelations: Map[SmartIri, Set[SmartIri]] = definitions.classDefs.map {
            case (classIri, classDef) => classIri -> classDef.subClassOf
        }

        val allSubClassOfRelations: Map[SmartIri, Set[SmartIri]] = definitions.classDefs.keySet.map {
            classIri => (classIri, OntologyUtil.getAllBaseDefs(classIri, directSubClassOfRelations) + classIri)
        }.toMap

        val definitionsWithSubClassOf = definitions.copy(
            subClassOf = allSubClassOfRelations
        )

        checkRec(instanceElement = instanceElement, classIri = expectedClassIri, definitions = definitionsWithSubClassOf)
    }

    /**
      * Recursively checks whether instance elements and their property objects correspond to their class definitions.
      *
      * @param instanceElement the instance to be checked.
      * @param classIri        the class IRI.
      * @param definitions     definitions of the class and any other relevant classes and properties.
      */
    private def checkRec(instanceElement: InstanceElement, classIri: SmartIri, definitions: Definitions): Unit = {
        if (!instanceInspector.elementHasCompatibleType(element = instanceElement, expectedType = classIri, definitions = definitions)) {
            throw AssertionException(s"Element type ${instanceElement.elementType} is not compatible with class IRI <$classIri>")
        }

        val classDef: ClassInfoContentV2 = definitions.getClassDef(classIri)

        val propertyIrisToInstancePropertyNames: Map[SmartIri, String] = classDef.directCardinalities.keySet.map {
            propertyIri => propertyIri -> instanceInspector.propertyIriToInstancePropertyName(propertyIri)
        }.toMap

        val allowedInstancePropertyNames: Set[String] = propertyIrisToInstancePropertyNames.values.toSet
        val extraInstancePropertyNames = instanceElement.propertyObjects.keySet -- allowedInstancePropertyNames

        if (extraInstancePropertyNames.nonEmpty) {
            throw AssertionException(s"One or more instance properties are not allowed by cardinalities: ${extraInstancePropertyNames.mkString(", ")}")
        }

        propertyIrisToInstancePropertyNames.foreach {
            case (propertyIri, instancePropertyName) =>
                val cardinality: Cardinality = classDef.directCardinalities(propertyIri).cardinality
                val objectsOfProp: Vector[InstanceElement] = instanceElement.propertyObjects.getOrElse(instancePropertyName, Vector.empty)
                val numberOfObjects = objectsOfProp.size

                if ((cardinality == MustHaveOne && numberOfObjects != 1) ||
                    (cardinality == MayHaveOne && numberOfObjects > 1) ||
                    (cardinality == MustHaveSome && numberOfObjects == 0)) {
                    throw AssertionException(s"Property $instancePropertyName has $numberOfObjects objects, but its cardinality is $cardinality")
                }
        }

        propertyIrisToInstancePropertyNames.foreach {
            case (propertyIri, instancePropertyName) =>
                val objectType: SmartIri = if (propertyIri.isKnoraApiV2EntityIri) {
                    val propertyDef = definitions.propertyDefs(propertyIri)
                    propertyDef.getPredicateIriObject(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri).getOrElse(OntologyConstants.Xsd.String.toSmartIri)
                } else {
                    OntologyConstants.Xsd.String.toSmartIri
                }

                val objectsOfProp: Vector[InstanceElement] = instanceElement.propertyObjects.getOrElse(instancePropertyName, Vector.empty)

                for (obj: InstanceElement <- objectsOfProp) {
                    // Determine the expected type that we're going to pass to the inspector, so it doesn't need
                    // to know about custom datatypes.
                    val expectedTypeForInspector: SmartIri = if (objectType.isKnoraApiV2EntityIri) {
                        val objClassDef: ClassInfoContentV2 = definitions.getClassDef(objectType)
                        val objClassRdfType = objClassDef.getRdfType

                        if (objClassRdfType.toString == OntologyConstants.Rdfs.Datatype) {
                            // If the real expected type is a custom datatype, tell the inspector it's xsd:string.
                            OntologyConstants.Xsd.String.toSmartIri
                        } else {
                            objectType
                        }
                    } else {
                        objectType
                    }

                    // Are we expecting a Knora class instance (not a custom datatype)?
                    if (expectedTypeForInspector.isKnoraApiV2EntityIri) {
                        // Yes. If the object is an IRI, accept it.
                        if (!instanceInspector.elementIsIri(obj)) {
                            // We're expecting a Knora class instance and the object isn't an IRI. Recurse.
                            checkRec(instanceElement = obj, classIri = objectType, definitions = definitions)
                        }
                    } else {
                        // We're expecting a literal. Ask the element inspector if we have a compatible one.
                        if (!instanceInspector.elementHasCompatibleType(element = obj, expectedType = objectType, definitions = definitions)) {
                            throw AssertionException(s"Element type ${obj.elementType} is not compatible with type <$objectType>")
                        }
                    }
                }
        }
    }

    private def getDefinitions(classIri: SmartIri, knoraRouteGet: String => String): Definitions = {
        getDefinitionsRec(classIri = classIri, knoraRouteGet = knoraRouteGet, definitions = Definitions())
    }

    private def getDefinitionsRec(classIri: SmartIri, knoraRouteGet: String => String, definitions: Definitions): Definitions = {
        // Get the definition of classIri.
        val classDef: ClassInfoContentV2 = getClassDef(classIri = classIri, knoraRouteGet = knoraRouteGet)

        // Get the IRIs of the base classes of that class.
        val baseClassIris = classDef.getPredicateIriObjects(OntologyConstants.Rdfs.SubClassOf.toSmartIri).toSet

        // Get the IRIs of the Knora properties on which that class has cardinalities.
        val propertyIrisFromCardinalities: Set[SmartIri] = (classDef.directCardinalities.keySet -- definitions.propertyDefs.keySet).filter(_.isKnoraApiV2EntityIri)

        // Get the definitions of those properties.
        val propertyDefsFromCardinalities: Map[SmartIri, PropertyInfoContentV2] = propertyIrisFromCardinalities.map {
            propertyIri =>
                propertyIri -> getPropertyDef(propertyIri = propertyIri, knoraRouteGet = knoraRouteGet)
        }.toMap

        // Get the IRIs of the Knora classes that are object types of those properties.
        val classIrisFromObjectTypes: Set[SmartIri] = propertyDefsFromCardinalities.values.foldLeft(Set.empty[SmartIri]) {
            case (acc, propertyDef) =>
                propertyDef.getPredicateIriObject(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri) match {
                    case Some(objectType) =>
                        if (objectType.isKnoraApiV2EntityIri && !KnoraApiV2WithValueObjectsTransformationRules.knoraBaseClassesToRemove.contains(objectType.toOntologySchema(InternalSchema))) {
                            acc + objectType
                        } else {
                            acc
                        }

                    case None => acc
                }
        }

        // Update definitions with the class and property definitions we've got so far.
        val updatedDefs: Definitions = definitions.copy(
            classDefs = definitions.classDefs + (classIri -> classDef),
            propertyDefs = definitions.propertyDefs ++ propertyDefsFromCardinalities
        )

        val classIrisForRecursion = baseClassIris ++ classIrisFromObjectTypes -- definitions.classDefs.keySet - classIri

        // Recursively add the definitions of base classes and classes that we identified as object types.
        classIrisForRecursion.foldLeft(updatedDefs) {
            case (acc, classIriFromObjectType) =>
                val recDefinitions: Definitions = getDefinitionsRec(
                    classIri = classIriFromObjectType,
                    knoraRouteGet = knoraRouteGet,
                    definitions = acc
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
        val jsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(classDefStr)

        jsonLDDocument.body.maybeString(OntologyConstants.KnoraApiV2WithValueObjects.Error) match {
            case Some(error) => throw AssertionException(error)
            case None => ()
        }

        InputOntologyV2.fromJsonLD(jsonLDDocument, parsingMode = KnoraOutputParsingModeV2).classes(classIri)
    }

    private def getPropertyDef(propertyIri: SmartIri, knoraRouteGet: String => String): PropertyInfoContentV2 = {
        val urlPath = s"/v2/ontologies/properties/${URLEncoder.encode(propertyIri.toString, "UTF-8")}"
        val propertyDefStr: String = knoraRouteGet(urlPath)
        val jsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(propertyDefStr)

        jsonLDDocument.body.maybeString(OntologyConstants.KnoraApiV2WithValueObjects.Error) match {
            case Some(error) => throw AssertionException(error)
            case None => ()
        }

        InputOntologyV2.fromJsonLD(jsonLDDocument, parsingMode = KnoraOutputParsingModeV2).properties(propertyIri)
    }
}

/**
  * Represents an instance of an RDF class, or an element of such an instance.
  *
  * @param elementType     the element type. This is an opaque string that only the [[InstanceInspector]] needs
  *                        to understand.
  * @param propertyObjects a map of property names to their objects.
  */
case class InstanceElement(elementType: String, propertyObjects: Map[String, Vector[InstanceElement]] = Map.empty)

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
      * Returns `true` if the specified instance element is an IRI pointing to an object, as opposed to
      * the object itself.
      */
    def elementIsIri(element: InstanceElement): Boolean

    /**
      * Checks, as far as possible, whether the type of an instance is compatible with the type IRI of its class.
      */
    def elementHasCompatibleType(element: InstanceElement, expectedType: SmartIri, definitions: InstanceChecker.Definitions): Boolean
}

/**
  * Constants for working with instances parsed from JSON.
  */
object JsonInstanceInspector {
    val STRING = "string"
    val IRI = "iri"
    val INTEGER = "integer"
    val DECIMAL = "decimal"
    val BOOLEAN = "boolean"
    val OBJECT = "object"

    val LiteralTypeMap: Map[String, Set[IRI]] = Map(
        STRING -> Set(OntologyConstants.Xsd.String, OntologyConstants.Xsd.DateTimeStamp),
        IRI -> Set(OntologyConstants.Xsd.Uri),
        INTEGER -> Set(OntologyConstants.Xsd.Integer, OntologyConstants.Xsd.NonNegativeInteger),
        DECIMAL -> Set(OntologyConstants.Xsd.Decimal),
        BOOLEAN -> Set(OntologyConstants.Xsd.Boolean)
    )

    val LiteralTypeIris: Set[IRI] = LiteralTypeMap.values.flatten.toSet
}

/**
  * An [[InstanceInspector]] that works with Knora responses in JSON format.
  */
class JsonInstanceInspector extends InstanceInspector {

    import JsonInstanceInspector._

    implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    override def toElement(response: String): InstanceElement = {
        jsObjectToElement(JsonParser(response).asJsObject)
    }

    private def jsValueToElements(jsValue: JsValue): Vector[InstanceElement] = {
        jsValue match {
            case jsObject: JsObject => Vector(jsObjectToElement(jsObject))
            case jsArray: JsArray => jsArray.elements.flatMap(jsValue => jsValueToElements(jsValue))

            case jsString: JsString =>
                val strType = if (stringFormatter.isIri(jsString.value)) {
                    IRI
                } else {
                    STRING
                }

                Vector(InstanceElement(strType))

            case jsNumber: JsNumber =>
                val numericType = if (jsNumber.value.isWhole) {
                    INTEGER
                } else {
                    DECIMAL
                }

                Vector(InstanceElement(numericType))

            case _: JsBoolean => Vector(InstanceElement(BOOLEAN))

            case JsNull => Vector.empty
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

    override def elementIsIri(element: InstanceElement): Boolean = {
        element.elementType == IRI
    }

    override def elementHasCompatibleType(element: InstanceElement, expectedType: SmartIri, definitions: InstanceChecker.Definitions): Boolean = {
        // Is the element a literal?
        LiteralTypeMap.get(element.elementType) match {
            case Some(typeIris) =>
                // Yes. It's compatible if the the expected type is one of the types that it could represent.
                typeIris.contains(expectedType.toString)

            case None =>
                // No. It's compatible if the expected type isn't a literal type.
                !LiteralTypeIris.contains(expectedType.toString)
        }
    }
}

/**
  * An [[InstanceInspector]] that works with Knora responses in JSON-LD format.
  */
class JsonLDInstanceInspector extends InstanceInspector {
    implicit private val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

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
                } else if (jsonLDObject.isIri) {
                    Vector(InstanceElement(OntologyConstants.Xsd.Uri))
                } else {
                    Vector(jsonLDObjectToElement(jsonLDObject))
                }

            case jsonLDArray: JsonLDArray =>
                jsonLDArray.value.flatMap(jsonLDValue => jsonLDValueToElements(jsonLDValue)).toVector

            case _: JsonLDString => Vector(InstanceElement(OntologyConstants.Xsd.String))

            case jsonLDInt: JsonLDInt =>
                val intType = if (jsonLDInt.value >= 0) {
                    OntologyConstants.Xsd.NonNegativeInteger
                } else {
                    OntologyConstants.Xsd.Integer
                }

                Vector(InstanceElement(intType))

            case _: JsonLDBoolean => Vector(InstanceElement(OntologyConstants.Xsd.Boolean))
        }
    }

    private def jsonLDObjectToElement(jsonLDObject: JsonLDObject): InstanceElement = {
        val elementType = jsonLDObject.requireString(JsonLDConstants.TYPE)

        val children: Map[String, Vector[InstanceElement]] = jsonLDObject.value.map {
            case (key, jsonLDValue: JsonLDValue) => key -> jsonLDValueToElements(jsonLDValue)
        } - JsonLDConstants.ID - JsonLDConstants.TYPE

        InstanceElement(elementType, children)
    }

    override def propertyIriToInstancePropertyName(propertyIri: SmartIri): String = {
        propertyIri.toString
    }

    override def elementIsIri(element: InstanceElement): Boolean =  {
        element.elementType == OntologyConstants.Xsd.Uri
    }

    override def elementHasCompatibleType(element: InstanceElement, expectedType: SmartIri, definitions: InstanceChecker.Definitions): Boolean = {
        val expectedTypeStr: IRI = expectedType.toString

        // Is the element's type a Knora class?
        if (expectedType.isKnoraApiV2EntityIri) {
            // Yes. It's compatible if its type is a subclass of the expected type.
            definitions.getBaseClasses(element.elementType.toSmartIri).contains(expectedType)
        } else {
            // The element's type isn't a Knora class. It's compatible if it's the same as the expected type, or if
            // we expected an xsd:integer and we got an xsd:nonNegativeInteger.
            element.elementType == expectedTypeStr ||
                (element.elementType == OntologyConstants.Xsd.NonNegativeInteger && expectedTypeStr == OntologyConstants.Xsd.Integer)
        }
    }
}
