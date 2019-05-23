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

import akka.event.LoggingAdapter
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality._
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.{OntologyUtil, SmartIri, StringFormatter}
import spray.json._

import scala.collection.mutable

/**
  * A factory that constructs [[InstanceChecker]] instances for different Knora response formats.
  */
object InstanceChecker {
    // A cache of class definitions.
    private val classDefCache: mutable.Map[SmartIri, ClassInfoContentV2] = mutable.Map.empty

    // A cache of property definitions.
    private val propertyDefCache: mutable.Map[SmartIri, PropertyInfoContentV2] = mutable.Map.empty

    /**
      * Returns an [[InstanceChecker]] for Knora responses in JSON format.
      */
    def getJsonChecker(log: LoggingAdapter): InstanceChecker = new InstanceChecker(new JsonInstanceInspector, log)

    /**
      * Returns an [[InstanceChecker]] for Knora responses in JSON-LD format.
      */
    def getJsonLDChecker(log: LoggingAdapter): InstanceChecker = new InstanceChecker(new JsonLDInstanceInspector, log)
}

/**
  * A test utility for checking whether an instance of an RDF class returned in a Knora response corresponds to the
  * the class definition.
  *
  * @param instanceInspector an [[InstanceInspector]] for working with instances in a particular format.
  * @param log               a [[LoggingAdapter]] for debugging.
  */
class InstanceChecker(instanceInspector: InstanceInspector, log: LoggingAdapter) {

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
      * Logs an error message at DEBUG level and throws an [[AssertionException]] containing the message.
      *
      * @param msg the error message.
      */
    private def throwAndLogAssertionException(msg: String): Nothing = {
        log.debug(msg)
        throw AssertionException(msg)
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
            throwAndLogAssertionException(s"Instance type ${instanceElement.elementType} is not compatible with expected class IRI $classIri")
        }

        val classDef: ClassInfoContentV2 = definitions.getClassDef(classIri, throwAndLogAssertionException)

        // Make a map of property IRIs to property names used in the instance.
        val propertyIrisToInstancePropertyNames: Map[SmartIri, String] = classDef.directCardinalities.keySet.map {
            propertyIri => propertyIri -> instanceInspector.propertyIriToInstancePropertyName(propertyIri)
        }.toMap

        // Check that there are no extra properties.

        val allowedInstancePropertyNames: Set[String] = propertyIrisToInstancePropertyNames.values.toSet
        val extraInstancePropertyNames = instanceElement.propertyObjects.keySet -- allowedInstancePropertyNames

        if (extraInstancePropertyNames.nonEmpty) {
            throwAndLogAssertionException(s"One or more instance properties are not allowed by cardinalities: ${extraInstancePropertyNames.mkString(", ")}")
        }

        // Check that cardinalities are respected.
        propertyIrisToInstancePropertyNames.foreach {
            case (propertyIri, instancePropertyName) =>
                val cardinality: Cardinality = classDef.directCardinalities(propertyIri).cardinality
                val objectsOfProp: Vector[InstanceElement] = instanceElement.propertyObjects.getOrElse(instancePropertyName, Vector.empty)
                val numberOfObjects = objectsOfProp.size

                if ((cardinality == MustHaveOne && numberOfObjects != 1) ||
                    (cardinality == MayHaveOne && numberOfObjects > 1) ||
                    (cardinality == MustHaveSome && numberOfObjects == 0)) {
                    throwAndLogAssertionException(s"Property $instancePropertyName has $numberOfObjects objects, but its cardinality is $cardinality")
                }
        }

        // Check the objects of the instance's properties.
        propertyIrisToInstancePropertyNames.foreach {
            case (propertyIri, instancePropertyName) =>
                // Get the expected type of the property's objects.
                val objectType: SmartIri = if (propertyIri.isKnoraApiV2EntityIri) {
                    val propertyDef = definitions.getPropertyDef(propertyIri, throwAndLogAssertionException)
                    getObjectType(propertyDef).getOrElse(throwAndLogAssertionException(s"Property $propertyIri has no knora-api:objectType"))
                } else {
                    OntologyConstants.Xsd.String.toSmartIri
                }

                val objectTypeStr = objectType.toString
                val objectTypeIsKnoraDefinedClass: Boolean = isKnoraDefinedClass(objectType)
                val objectTypeIsKnoraDatatype: Boolean = OntologyConstants.KnoraApiV2Simple.KnoraDatatypes.contains(objectTypeStr)
                val objectsOfProp: Vector[InstanceElement] = instanceElement.propertyObjects.getOrElse(instancePropertyName, Vector.empty)

                // Iterate over the objects of the property.
                for (obj: InstanceElement <- objectsOfProp) {
                    val literalContentMsg = obj.literalContent match {
                        case Some(literalContent) => s" with literal content '$literalContent'"
                        case None => ""
                    }

                    val errorMsg = s"Property $instancePropertyName has an object of type ${obj.elementType}$literalContentMsg, but type $objectType was expected"

                    // Are we expecting an instance of a Knora class that isn't a datatype?
                    if (objectType.isKnoraApiV2EntityIri && !objectTypeIsKnoraDatatype) {
                        // Yes. Is it a class that Knora serves?
                        if (objectTypeIsKnoraDefinedClass) {
                            // Yes. If we got an IRI, accept it. Otherwise, recurse.
                            if (!instanceInspector.elementIsIri(obj)) {
                                checkRec(instanceElement = obj, classIri = objectType, definitions = definitions)
                            }
                        } else if (!instanceInspector.elementIsIri(obj)) {
                            // It's a class that Knora doesn't serve. Accept the object only if it's an IRI.
                            throwAndLogAssertionException(s"Property $propertyIri requires an IRI referring to an instance of $objectType, but object content was received instead")
                        }
                    } else {
                        // We're expecting a literal. Ask the element inspector if the object is compatible with
                        // the expected type.
                        if (!instanceInspector.elementHasCompatibleType(element = obj, expectedType = objectType, definitions = definitions)) {
                            throwAndLogAssertionException(errorMsg)
                        }
                    }
                }
        }
    }

    /**
      * Gets all the definitions needed to check an instance of the specified class.
      *
      * @param classIri      the class IRI.
      * @param knoraRouteGet a function that takes a Knora API URL path and returns a response from Knora.
      * @return a [[Definitions]] instance containing the relevant definitions.
      */
    private def getDefinitions(classIri: SmartIri, knoraRouteGet: String => String): Definitions = {
        getDefinitionsRec(classIri = classIri, knoraRouteGet = knoraRouteGet, definitions = Definitions())
    }

    /**
      * Recursively gets definitions that are needed to check an instance of the specified class.
      *
      * @param classIri      the class IRI.
      * @param knoraRouteGet a function that takes a Knora API URL path and returns a response from Knora.
      * @param definitions   the definitions collected so far.
      * @return a [[Definitions]] instance containing the relevant definitions.
      */
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

        // Get the IRIs of the Knora classes that are object types of those properties and that are available in the
        // class's ontology schema.
        val classIrisFromObjectTypes: Set[SmartIri] = propertyDefsFromCardinalities.values.foldLeft(Set.empty[SmartIri]) {
            case (acc, propertyDef) =>
                getObjectType(propertyDef) match {
                    case Some(objectType) =>
                        if (isKnoraDefinedClass(objectType)) {
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

    /**
      * Determines whether Knora should have the definition of a class.
      *
      * @param classIri the class IRI.
      * @return `true` if Knora should have the definition of the class.
      */
    private def isKnoraDefinedClass(classIri: SmartIri): Boolean = {
        // There are some Knora classes whose definitions we refer to but don't actually serve, e.g.
        // knora-api:XMLToStandoffMapping. The ontology transformation rules list the internal classes that
        // aren't available in each external schema. But these lists include knora-base classes
        // that are converted to custom datatypes in the simple schema, so we first need to check
        // if we're talking about one of those.
        if (classIri.isKnoraApiV2EntityIri) {
            val objectTypeInternal = classIri.toOntologySchema(InternalSchema)

            classIri.getOntologySchema.get match {
                case ApiV2Simple =>
                    if (OntologyConstants.KnoraApiV2Simple.KnoraDatatypes.contains(classIri.toString)) {
                        // It's a custom data type.
                        true
                    } else if (!KnoraBaseToApiV2SimpleTransformationRules.internalClassesToRemove.contains(objectTypeInternal)) {
                        // It isn't a custom data type, and isn't one of the classes removed from the schema.
                        true
                    } else {
                        // It's one of the classes removed from the schema.
                        false
                    }

                case ApiV2Complex =>
                    if (!(KnoraBaseToApiV2ComplexTransformationRules.internalClassesToRemove.contains(objectTypeInternal) ||
                        KnoraAdminToApiV2ComplexTransformationRules.internalClassesToRemove.contains(objectTypeInternal))) {
                        // It isn't one of the classes removed from the schema.
                        true
                    } else {
                        // It's one of the classes removed from the schema.
                        false
                    }

                case _ => throwAndLogAssertionException("Unreachable code")
            }
        } else {
            false
        }
    }

    /**
      * Gets a class definition from Knora.
      *
      * @param classIri      the class IRI.
      * @param knoraRouteGet a function that takes a Knora API URL path and returns a response from Knora.
      * @return the class definition.
      */
    private def getClassDef(classIri: SmartIri, knoraRouteGet: String => String): ClassInfoContentV2 = {
        InstanceChecker.classDefCache.get(classIri) match {
            case Some(classDef) => classDef

            case None =>
                val urlPath = s"/v2/ontologies/classes/${URLEncoder.encode(classIri.toString, "UTF-8")}"
                val classDefStr: String = knoraRouteGet(urlPath)
                val jsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(classDefStr)

                // If Knora returned an error, get the error message and throw an exception.
                jsonLDDocument.body.maybeString(OntologyConstants.KnoraApiV2Complex.Error) match {
                    case Some(error) => throwAndLogAssertionException(error)
                    case None => ()
                }

                val classDef = InputOntologyV2.fromJsonLD(jsonLDDocument, parsingMode = KnoraOutputParsingModeV2).classes(classIri)
                InstanceChecker.classDefCache.put(classIri, classDef)
                classDef
        }
    }

    /**
      * Gets a property definition from Knora.
      *
      * @param propertyIri   the property IRI.
      * @param knoraRouteGet a function that takes a Knora API URL path and returns a response from Knora.
      * @return the property definition.
      */
    private def getPropertyDef(propertyIri: SmartIri, knoraRouteGet: String => String): PropertyInfoContentV2 = {
        InstanceChecker.propertyDefCache.get(propertyIri) match {
            case Some(propertyDef) => propertyDef

            case None =>
                val urlPath = s"/v2/ontologies/properties/${URLEncoder.encode(propertyIri.toString, "UTF-8")}"
                val propertyDefStr: String = knoraRouteGet(urlPath)
                val jsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(propertyDefStr)

                // If Knora returned an error, get the error message and throw an exception.
                jsonLDDocument.body.maybeString(OntologyConstants.KnoraApiV2Complex.Error) match {
                    case Some(error) => throwAndLogAssertionException(error)
                    case None => ()
                }

                val propertyDef = InputOntologyV2.fromJsonLD(jsonLDDocument, parsingMode = KnoraOutputParsingModeV2).properties(propertyIri)
                InstanceChecker.propertyDefCache.put(propertyIri, propertyDef)
                propertyDef
        }
    }

    /**
      * Gets the `knora-api:objectType` from a property definition in an external schema.
      *
      * @param propertyDef the property definition.
      * @return the property's `knora-api:objectType`, if it has one.
      */
    private def getObjectType(propertyDef: PropertyInfoContentV2): Option[SmartIri] = {
        propertyDef.getPredicateIriObject(OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri).
            orElse(propertyDef.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.ObjectType.toSmartIri))
    }
}

/**
  * Represents an instance of an RDF class, or an element of such an instance.
  *
  * @param elementType     the element type. This is an opaque string that only the [[InstanceInspector]] needs
  *                        to understand.
  * @param literalContent  the literal content of the element (if available), for debugging.
  * @param propertyObjects a map of property names to their objects.
  */
case class InstanceElement(elementType: String, literalContent: Option[String] = None, propertyObjects: Map[String, Vector[InstanceElement]] = Map.empty)

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
    def elementHasCompatibleType(element: InstanceElement, expectedType: SmartIri, definitions: Definitions): Boolean
}

/**
  * Constants for working with instances parsed from JSON.
  */
object JsonInstanceInspector {
    val STRING = "String"
    val IRI = "IRI"
    val INTEGER = "Integer"
    val DECIMAL = "Decimal"
    val BOOLEAN = "Boolean"
    val OBJECT = "Object"

    val LiteralTypeMap: Map[String, Set[IRI]] = Map(
        STRING ->
            Set(OntologyConstants.Xsd.String,
                OntologyConstants.Xsd.DateTimeStamp,
                OntologyConstants.KnoraApiV2Simple.Date,
                OntologyConstants.KnoraApiV2Simple.Geom,
                OntologyConstants.KnoraApiV2Simple.Color,
                OntologyConstants.KnoraApiV2Simple.Interval,
                OntologyConstants.KnoraApiV2Simple.Geoname,
                OntologyConstants.KnoraApiV2Simple.ListNode),

        IRI -> Set(OntologyConstants.Xsd.Uri, OntologyConstants.KnoraApiV2Simple.File),
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
                // Does this string represent an IRI?
                val strType = if (stringFormatter.isIri(jsString.value)) {
                    // Yes. Store its type as IRI.
                    IRI
                } else {
                    // No. Store its type as STRING.
                    STRING
                }

                Vector(InstanceElement(elementType = strType, literalContent = Some(jsString.value)))

            case jsNumber: JsNumber =>
                // Is this an integer?
                val numericType = if (jsNumber.value.isWhole) {
                    // Yes. Store its type as INTEGER.
                    INTEGER
                } else {
                    // No. Store its type as DECIMAL.
                    DECIMAL
                }

                Vector(InstanceElement(elementType = numericType, literalContent = Some(jsNumber.value.toString)))

            case jsBoolean: JsBoolean =>
                Vector(InstanceElement(elementType = BOOLEAN, literalContent = Some(jsBoolean.value.toString)))

            case JsNull => Vector.empty
        }
    }

    private def jsObjectToElement(jsObject: JsObject): InstanceElement = {
        val propertyObjects = jsObject.fields.map {
            case (key: String, jsValue: JsValue) => key -> jsValueToElements(jsValue)
        }

        InstanceElement(elementType = OBJECT, propertyObjects = propertyObjects)
    }

    override def propertyIriToInstancePropertyName(propertyIri: SmartIri): String = {
        // To convert a property IRI to a JSON object key, remove the namespace.
        propertyIri.getEntityName
    }

    override def elementIsIri(element: InstanceElement): Boolean = {
        element.elementType == IRI
    }

    override def elementHasCompatibleType(element: InstanceElement, expectedType: SmartIri, definitions: Definitions): Boolean = {
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
                    // This object represents a string with a language tag.
                    val literalContent = jsonLDObject.requireString(JsonLDConstants.VALUE)
                    Vector(InstanceElement(elementType = OntologyConstants.Xsd.String, literalContent = Some(literalContent)))
                } else if (jsonLDObject.isDatatypeValue) {
                    // This object represents a JSON-LD datatype value.
                    val datatype = jsonLDObject.requireString(JsonLDConstants.TYPE)
                    val literalContent = jsonLDObject.requireString(JsonLDConstants.VALUE)
                    Vector(InstanceElement(elementType = datatype, literalContent = Some(literalContent)))
                } else if (jsonLDObject.isIri) {
                    // This object represents an IRI.
                    val literalContent = jsonLDObject.requireString(JsonLDConstants.ID)
                    Vector(InstanceElement(elementType = OntologyConstants.Xsd.Uri, literalContent = Some(literalContent)))
                } else {
                    // This object represents a class instance.
                    Vector(jsonLDObjectToElement(jsonLDObject))
                }

            case jsonLDArray: JsonLDArray =>
                jsonLDArray.value.flatMap(jsonLDValue => jsonLDValueToElements(jsonLDValue)).toVector

            case jsonLDString: JsonLDString =>
                Vector(InstanceElement(OntologyConstants.Xsd.String, literalContent = Some(jsonLDString.value)))

            case jsonLDInt: JsonLDInt =>
                val intType = if (jsonLDInt.value >= 0) {
                    OntologyConstants.Xsd.NonNegativeInteger
                } else {
                    OntologyConstants.Xsd.Integer
                }

                Vector(InstanceElement(elementType = intType, literalContent = Some(jsonLDInt.value.toString)))

            case jsonLDBoolean: JsonLDBoolean =>
                Vector(InstanceElement(elementType = OntologyConstants.Xsd.Boolean, literalContent = Some(jsonLDBoolean.value.toString)))
        }
    }

    private def jsonLDObjectToElement(jsonLDObject: JsonLDObject): InstanceElement = {
        val elementType = jsonLDObject.requireString(JsonLDConstants.TYPE)

        val propertyObjects: Map[String, Vector[InstanceElement]] = jsonLDObject.value.map {
            case (key, jsonLDValue: JsonLDValue) => key -> jsonLDValueToElements(jsonLDValue)
        } - JsonLDConstants.ID - JsonLDConstants.TYPE

        InstanceElement(elementType = elementType, propertyObjects = propertyObjects)
    }

    override def propertyIriToInstancePropertyName(propertyIri: SmartIri): String = {
        propertyIri.toString
    }

    override def elementIsIri(element: InstanceElement): Boolean = {
        element.elementType == OntologyConstants.Xsd.Uri
    }

    override def elementHasCompatibleType(element: InstanceElement, expectedType: SmartIri, definitions: Definitions): Boolean = {
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

/**
  * Represents Knora ontology definitions used to check class instances against their definitions.
  *
  * @param classDefs    relevant class definitions.
  * @param propertyDefs relevant property definitions.
  * @param subClassOf   a map in which each class IRI points to the full set of its base classes. A class is also
  *                     a subclass of itself.
  */
case class Definitions(classDefs: Map[SmartIri, ClassInfoContentV2] = Map.empty,
                       propertyDefs: Map[SmartIri, PropertyInfoContentV2] = Map.empty,
                       subClassOf: Map[SmartIri, Set[SmartIri]] = Map.empty) {
    def getClassDef(classIri: SmartIri,
                    errorFun: String => Nothing): ClassInfoContentV2 = {
        classDefs.getOrElse(classIri, errorFun(s"No definition for class $classIri"))
    }

    def getPropertyDef(propertyIri: SmartIri,
                       errorFun: String => Nothing): PropertyInfoContentV2 = {
        propertyDefs.getOrElse(propertyIri, errorFun(s"No definition for property $propertyIri"))
    }

    def getBaseClasses(classIri: SmartIri): Set[SmartIri] = {
        subClassOf.getOrElse(classIri, Set.empty)
    }
}
