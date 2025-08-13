/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e

import sttp.client4.*
import zio.*

import scala.collection.mutable

import dsp.errors.AssertionException
import dsp.errors.BadRequestException
import org.knora.webapi.*
import org.knora.webapi.e2e.v2.ontology.InputOntologyParsingModeV2.KnoraOutputParsingModeV2
import org.knora.webapi.e2e.v2.ontology.InputOntologyV2
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.*
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

/**
 * A test utility for checking whether an instance of an RDF class returned in a Knora response corresponds to the
 * class definition.
 */
case class InstanceChecker()(implicit val stringFormatter: StringFormatter) { self =>

  private val instanceInspector: JsonLDInstanceInspector = JsonLDInstanceInspector()

  // A cache of class definitions.
  private val classDefCache: mutable.Map[SmartIri, ClassInfoContentV2] = mutable.Map.empty

  // A cache of property definitions.
  private val propertyDefCache: mutable.Map[SmartIri, PropertyInfoContentV2] = mutable.Map.empty

  /**
   * Checks that an instance of an RDF class returned in a Knora response corresponds to the class definition.
   *
   * @param instanceResponse a Knora response containing the instance to be checked.
   * @param expectedClassIri the IRI of the expected class.
   */
  def check(instanceResponse: String, expectedClassIri: SmartIri) = {
    val instanceElement: InstanceElement = instanceInspector.toElement(instanceResponse)
    for {
      definitions <- getDefinitions(expectedClassIri)

      // A map of class IRIs to their immediate base classes.
      directSubClassOfRelations: Map[SmartIri, Set[SmartIri]] = definitions.classDefs.map { case (classIri, classDef) =>
                                                                  classIri -> classDef.subClassOf
                                                                }

      allSubClassOfRelations: Map[SmartIri, Seq[SmartIri]] = definitions.classDefs.keySet.map { classIri =>
                                                               // get all hierarchically ordered base classes
                                                               val baseClasses: Seq[SmartIri] =
                                                                 OntologyUtil.getAllBaseDefs(
                                                                   classIri,
                                                                   directSubClassOfRelations,
                                                                 )
                                                               // prepend the classIri to the sequence of base classes because a class is also a subclass of itself.
                                                               (classIri, classIri +: baseClasses)
                                                             }.toMap

      definitionsWithSubClassOf = definitions.copy(subClassOf = allSubClassOfRelations)

      _ <- checkRec(
             instanceElement = instanceElement,
             classIri = expectedClassIri,
             definitions = definitionsWithSubClassOf,
           )
    } yield ()
  }

  /**
   * Recursively checks whether instance elements and their property objects correspond to their class definitions.
   *
   * @param instanceElement the instance to be checked.
   * @param classIri        the class IRI.
   * @param definitions     definitions of the class and any other relevant classes and properties.
   */
  private def checkRec(
    instanceElement: InstanceElement,
    classIri: SmartIri,
    definitions: Definitions,
  ): ZIO[Any, Throwable, Unit] =
    for {
      _ <- ZIO
             .fail(
               AssertionException(
                 s"Instance type ${instanceElement.elementType} is not compatible with expected class IRI $classIri",
               ),
             )
             .when(!instanceInspector.elementHasCompatibleType(instanceElement, classIri, definitions))

      classDef <- ZIO.attempt(definitions.getClassDef(classIri, msg => throw AssertionException(msg)))

      // Make a map of property IRIs to property names used in the instance.
      propertyIrisToInstancePropertyNames: Map[SmartIri, String] =
        classDef.directCardinalities.keySet.map { propertyIri =>
          propertyIri -> instanceInspector.propertyIriToInstancePropertyName(classIri, propertyIri)
        }.toMap

      // Check that there are no extra properties.

      allowedInstancePropertyNames: Set[String] = propertyIrisToInstancePropertyNames.values.toSet
      extraInstancePropertyNames                = instanceElement.propertyObjects.keySet -- allowedInstancePropertyNames

      _ <-
        ZIO
          .fail(
            AssertionException(
              s"One or more instance properties are not allowed by cardinalities: ${extraInstancePropertyNames.mkString(", ")}",
            ),
          )
          .when(extraInstancePropertyNames.nonEmpty)

      // Check that cardinalities are respected.
      _ <- ZIO.foreachDiscard(propertyIrisToInstancePropertyNames) { case (propertyIri, instancePropertyName) =>
             val cardinality     = classDef.directCardinalities(propertyIri).cardinality
             val numberOfObjects = instanceElement.propertyObjects.getOrElse(instancePropertyName, Vector.empty).size
             ZIO
               .fail(
                 AssertionException(
                   s"Property $instancePropertyName has $numberOfObjects objects, but its cardinality is ${cardinality.toString}",
                 ),
               )
               .unless(cardinality.isCountIncluded(numberOfObjects))
           }

      // Check the objects of the instance's properties.
      _ <- ZIO.foreachDiscard(propertyIrisToInstancePropertyNames) { case (propertyIri, instancePropertyName) =>
             // Get the expected type of the property's objects.
             val objectType: SmartIri = if (propertyIri.isKnoraApiV2EntityIri) {
               val propertyDef = definitions.getPropertyDef(propertyIri, msg => throw AssertionException(msg))
               getObjectType(propertyDef).getOrElse(
                 throw AssertionException(s"Property $propertyIri has no knora-api:objectType"),
               )
             } else {
               OntologyConstants.Xsd.String.toSmartIri
             }

             val objectTypeStr                          = objectType.toString
             val objectTypeIsKnoraDefinedClass: Boolean = isKnoraDefinedClass(objectType)
             val objectTypeIsKnoraDatatype: Boolean =
               OntologyConstants.KnoraApiV2Simple.KnoraDatatypes.contains(objectTypeStr)
             val objectsOfProp: Vector[InstanceElement] =
               instanceElement.propertyObjects.getOrElse(instancePropertyName, Vector.empty)

             // Iterate over the objects of the property.
             ZIO.foreach(objectsOfProp) { obj =>
               val literalContentMsg = obj.literalContent match {
                 case Some(literalContent) => s" with literal content '$literalContent'"
                 case None                 => ""
               }

               val errorMsg =
                 s"Property $instancePropertyName has an object of type ${obj.elementType}$literalContentMsg, but type $objectType was expected"

               // Are we expecting an instance of a Knora class that isn't a datatype?
               if (objectType.isKnoraApiV2EntityIri && !objectTypeIsKnoraDatatype) {
                 // Yes. Is it a class that Knora serves?
                 if (objectTypeIsKnoraDefinedClass) {
                   // Yes. If we got an IRI, accept it. Otherwise, recurse.
                   ZIO
                     .unless(instanceInspector.elementIsIri(obj))(
                       checkRec(instanceElement = obj, classIri = objectType, definitions = definitions),
                     )
                     .unit
                 } else if (!instanceInspector.elementIsIri(obj)) {
                   // It's a class that Knora doesn't serve. Accept the object only if it's an IRI.
                   ZIO.fail(
                     AssertionException(
                       s"Property $propertyIri requires an IRI referring to an instance of $objectType, but object content was received instead",
                     ),
                   )
                 } else {
                   ZIO.unit
                 }
               } else {
                 // We're expecting a literal. Ask the element inspector if the object is compatible with
                 // the expected type.
                 ZIO
                   .fail(AssertionException(errorMsg))
                   .when(
                     !instanceInspector.elementHasCompatibleType(
                       element = obj,
                       expectedType = objectType,
                       definitions = definitions,
                     ),
                   )
                   .unit
               }
             }
           }
    } yield ()

  /**
   * Gets all the definitions needed to check an instance of the specified class.
   *
   * @param classIri      the class IRI.
   * @return a [[Definitions]] instance containing the relevant definitions.
   */
  private def getDefinitions(classIri: SmartIri): ZIO[TestApiClient, Throwable, Definitions] =
    getDefinitionsRec(classIri = classIri, definitions = Definitions())

  /**
   * Recursively gets definitions that are needed to check an instance of the specified class.
   *
   * @param classIri      the class IRI.
   * @param definitions   the definitions collected so far.
   * @return a [[Definitions]] instance containing the relevant definitions.
   */
  private def getDefinitionsRec(
    classIri: SmartIri,
    definitions: Definitions,
  ): ZIO[TestApiClient, Throwable, Definitions] =
    // Get the definition of classIri.
    for {
      classDef <- getClassDef(classIri)

      // Get the IRIs of the base classes of that class.
      baseClassIris = classDef.subClassOf.filter(baseClassIri => isKnoraDefinedClass(baseClassIri))

      // Get the IRIs of the Knora properties on which that class has cardinalities.
      propertyIrisFromCardinalities: Set[SmartIri] =
        (classDef.directCardinalities.keySet -- definitions.propertyDefs.keySet).filter(_.isKnoraApiV2EntityIri)

      // Get the definitions of those properties.
      propertyDefsFromCardinalities: Map[SmartIri, PropertyInfoContentV2] <-
        ZIO
          .foreach(propertyIrisFromCardinalities.toSeq)(p => getPropertyDef(p).map(p -> _))
          .map(_.toMap)

      // Get the IRIs of the Knora classes that are object types of those properties and that are available in the
      // class's ontology schema.
      classIrisFromObjectTypes: Set[SmartIri] = propertyDefsFromCardinalities.values.foldLeft(Set.empty[SmartIri]) {
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
      updatedDefs: Definitions = definitions.copy(
                                   classDefs = definitions.classDefs + (classIri -> classDef),
                                   propertyDefs = definitions.propertyDefs ++ propertyDefsFromCardinalities,
                                 )

      classIrisForRecursion = baseClassIris ++ classIrisFromObjectTypes -- definitions.classDefs.keySet - classIri

      // Recursively add the definitions of base classes and classes that we identified as object types.
      definitions <- ZIO.foldLeft(classIrisForRecursion)(updatedDefs) { case (acc, classIriFromObjectType) =>
                       getDefinitionsRec(classIriFromObjectType, acc)
                         .map(recDefinitions =>
                           acc.copy(
                             classDefs = acc.classDefs ++ recDefinitions.classDefs,
                             propertyDefs = acc.propertyDefs ++ recDefinitions.propertyDefs,
                           ),
                         )
                     }
    } yield definitions

  /**
   * Determines whether Knora should have the definition of a class.
   *
   * @param classIri the class IRI.
   * @return `true` if Knora should have the definition of the class.
   */
  private def isKnoraDefinedClass(classIri: SmartIri): Boolean =
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
          if (!KnoraBaseToApiV2ComplexTransformationRules.internalClassesToRemove.contains(objectTypeInternal)) {
            // It isn't one of the classes removed from the schema.
            true
          } else {
            // It's one of the classes removed from the schema.
            false
          }

        case _ => throw AssertionException("Unreachable code")
      }
    } else {
      false
    }

  /**
   * Gets a class definition from Knora.
   *
   * @param classIri      the class IRI.
   * @return the class definition.
   */
  private def getClassDef(classIri: SmartIri) =
    ZIO.fromOption(self.classDefCache.get(classIri)).orElse {
      for {
        jsonLDDocument <- TestApiClient.getJsonLdDocument(uri"/v2/ontologies/classes/$classIri").flatMap(_.assert200)
        // If Knora returned an error, get the error message and fail
        _ <- ZIO
               .fromEither(jsonLDDocument.body.getString(OntologyConstants.KnoraApiV2Complex.Error))
               .mapError(msg => AssertionException(msg))
               .flatMap {
                 case Some(error) => ZIO.fail(AssertionException(error))
                 case None        => ZIO.unit
               }
        classDef <- ZIO.attempt(InputOntologyV2.fromJsonLD(jsonLDDocument, KnoraOutputParsingModeV2).classes(classIri))
        _         = self.classDefCache.put(classIri, classDef)
      } yield classDef
    }

  /**
   * Gets a property definition from Knora.
   *
   * @param propertyIri   the property IRI.
   * @return the property definition.
   */
  private def getPropertyDef(propertyIri: SmartIri) =
    ZIO.fromOption(self.propertyDefCache.get(propertyIri)).orElse {
      for {
        jsonLDDocument <-
          TestApiClient.getJsonLdDocument(uri"/v2/ontologies/properties/$propertyIri").flatMap(_.assert200)
        // If Knora returned an error, get the error message and fail
        _ <- ZIO
               .fromEither(jsonLDDocument.body.getString(OntologyConstants.KnoraApiV2Complex.Error))
               .mapError(AssertionException(_))
               .flatMap {
                 case Some(error) => ZIO.fail(AssertionException(error))
                 case None        => ZIO.unit
               }
        propertyDef <-
          ZIO.attempt(InputOntologyV2.fromJsonLD(jsonLDDocument, KnoraOutputParsingModeV2).properties(propertyIri))
        _ = self.propertyDefCache.put(propertyIri, propertyDef)
      } yield propertyDef
    }

  /**
   * Gets the `knora-api:objectType` from a property definition in an external schema.
   *
   * @param propertyDef the property definition.
   * @return the property's `knora-api:objectType`, if it has one.
   */
  private def getObjectType(propertyDef: PropertyInfoContentV2): Option[SmartIri] =
    propertyDef
      .getPredicateIriObject(OntologyConstants.KnoraApiV2Complex.ObjectType.toSmartIri)
      .orElse(propertyDef.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.ObjectType.toSmartIri))
}

object InstanceChecker {
  def make(implicit sf: StringFormatter): InstanceChecker = new InstanceChecker()
}

/**
 * Represents an instance of an RDF class, or an element of such an instance.
 *
 * @param elementType     the element type. This is an opaque string that only the [[InstanceInspector]] needs
 *                        to understand.
 * @param literalContent  the literal content of the element (if available), for debugging.
 * @param propertyObjects a map of property names to their objects.
 */
case class InstanceElement(
  elementType: String,
  literalContent: Option[String] = None,
  propertyObjects: Map[String, Vector[InstanceElement]] = Map.empty,
)

case class JsonLDInstanceInspector()(implicit private val stringFormatter: StringFormatter) {

  def toElement(response: String): InstanceElement =
    jsonLDObjectToElement(JsonLDUtil.parseJsonLD(response).body)

  private def jsonLDValueToElements(jsonLDValue: JsonLDValue): Vector[InstanceElement] =
    jsonLDValue match {
      case jsonLDObject: JsonLDObject =>
        if (jsonLDObject.isStringWithLang) {
          // This object represents a string with a language tag.
          val literalContent =
            jsonLDObject.getRequiredString(JsonLDKeywords.VALUE).fold(msg => throw BadRequestException(msg), identity)
          Vector(InstanceElement(elementType = OntologyConstants.Xsd.String, literalContent = Some(literalContent)))
        } else if (jsonLDObject.isDatatypeValue) {
          // This object represents a JSON-LD datatype value.
          val datatype =
            jsonLDObject.getRequiredString(JsonLDKeywords.TYPE).fold(msg => throw BadRequestException(msg), identity)
          val literalContent =
            jsonLDObject.getRequiredString(JsonLDKeywords.VALUE).fold(msg => throw BadRequestException(msg), identity)
          Vector(InstanceElement(elementType = datatype, literalContent = Some(literalContent)))
        } else if (jsonLDObject.isIri) {
          // This object represents an IRI.
          val literalContent =
            jsonLDObject.getRequiredString(JsonLDKeywords.ID).fold(msg => throw BadRequestException(msg), identity)
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
        Vector(
          InstanceElement(
            elementType = OntologyConstants.Xsd.Boolean,
            literalContent = Some(jsonLDBoolean.value.toString),
          ),
        )
    }

  private def jsonLDObjectToElement(jsonLDObject: JsonLDObject): InstanceElement = {
    val elementType =
      jsonLDObject
        .getRequiredString(JsonLDKeywords.TYPE)
        .fold(msg => throw BadRequestException(msg), identity)

    val propertyObjects: Map[String, Vector[InstanceElement]] = jsonLDObject.value.map {
      case (key, jsonLDValue: JsonLDValue) => key -> jsonLDValueToElements(jsonLDValue)
    } - JsonLDKeywords.ID - JsonLDKeywords.TYPE

    InstanceElement(elementType = elementType, propertyObjects = propertyObjects)
  }

  def propertyIriToInstancePropertyName(classIri: SmartIri, propertyIri: SmartIri): String =
    propertyIri.toString

  def elementIsIri(element: InstanceElement): Boolean =
    element.elementType == OntologyConstants.Xsd.Uri

  def elementHasCompatibleType(
    element: InstanceElement,
    expectedType: SmartIri,
    definitions: Definitions,
  ): Boolean = {
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
case class Definitions(
  classDefs: Map[SmartIri, ClassInfoContentV2] = Map.empty,
  propertyDefs: Map[SmartIri, PropertyInfoContentV2] = Map.empty,
  subClassOf: Map[SmartIri, Seq[SmartIri]] = Map.empty,
) {
  def getClassDef(classIri: SmartIri, errorFun: String => Nothing): ClassInfoContentV2 =
    classDefs.getOrElse(classIri, errorFun(s"No definition for class $classIri"))

  def getPropertyDef(propertyIri: SmartIri, errorFun: String => Nothing): PropertyInfoContentV2 =
    propertyDefs.getOrElse(propertyIri, errorFun(s"No definition for property $propertyIri"))

  def getBaseClasses(classIri: SmartIri): Seq[SmartIri] =
    subClassOf.getOrElse(classIri, Seq.empty)
}
