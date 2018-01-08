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

package org.knora.webapi.messages.v2.responder.ontologymessages


import java.time.Instant
import java.util.UUID

import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.jsonld._
import org.knora.webapi.util.{SmartIri, StringFormatter}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait for messages that can be sent to `ResourcesResponderV2`.
  */
sealed trait OntologiesResponderRequestV2 extends KnoraRequestV2 {

    def userProfile: UserProfileV1
}

/**
  * Requests that all ontologies in the repository are loaded. This message must be sent only once, when the application
  * starts, before it accepts any API requests. A successful response will be a [[SuccessResponseV2]].
  *
  * @param userProfile the profile of the user making the request.
  */
case class LoadOntologiesRequestV2(userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests the creation of an empty ontology. A successful response will be a [[ReadOntologiesV2]].
  *
  * @param ontologyName the name of the ontology to be created.
  * @param projectIri   the IRI of the project that the ontology will belong to.
  * @param apiRequestID the ID of the API request.
  * @param userProfile  the profile of the user making the request.
  */
case class CreateOntologyRequestV2(ontologyName: String,
                                   projectIri: SmartIri,
                                   label: String,
                                   apiRequestID: UUID,
                                   userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Constructs instances of [[CreateOntologyRequestV2]] based on JSON-LD requests.
  */
object CreateOntologyRequestV2 extends KnoraJsonLDRequestReaderV2[CreateOntologyRequestV2] {
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            userProfile: UserProfileV1): CreateOntologyRequestV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val ontologyName: String = jsonLDDocument.requireString(OntologyConstants.KnoraApiV2WithValueObjects.OntologyName, stringFormatter.validateProjectSpecificOntologyName)
        val label: String = jsonLDDocument.requireString(OntologyConstants.Rdfs.Label, stringFormatter.toSparqlEncodedString)
        val projectIri: SmartIri = jsonLDDocument.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ProjectIri, stringFormatter.toSmartIriWithErr)

        CreateOntologyRequestV2(
            ontologyName = ontologyName,
            projectIri = projectIri,
            label = label,
            apiRequestID = apiRequestID,
            userProfile = userProfile
        )
    }
}

/**
  * Requests the addition of a property to an ontology. A successful response will be a [[ReadOntologiesV2]].
  *
  * @param propertyInfoContent information about the property to be created.
  * @param apiRequestID        the ID of the API request.
  * @param userProfile         the profile of the user making the request.
  */
case class CreatePropertyRequestV2(propertyInfoContent: PropertyInfoContentV2,
                                   lastModificationDate: Instant,
                                   apiRequestID: UUID,
                                   userProfile: UserProfileV1) extends OntologiesResponderRequestV2


/**
  * Constructs instances of [[CreatePropertyRequestV2]] based on JSON-LD requests.
  */
object CreatePropertyRequestV2 extends KnoraJsonLDRequestReaderV2[CreatePropertyRequestV2] {
    /**
      * The predicates that are expected in a submitted property definition.
      */
    private val PropertyPredicates = Set(
        "@id",
        "@type",
        OntologyConstants.Rdfs.SubPropertyOf,
        OntologyConstants.KnoraApiV2WithValueObjects.SubjectType,
        OntologyConstants.KnoraApiV2WithValueObjects.ObjectType,
        OntologyConstants.Rdfs.Label,
        OntologyConstants.Rdfs.Comment
    )

    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            userProfile: UserProfileV1): CreatePropertyRequestV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        // Here we expect the same structure that would be returned by a ReadOntologiesV2.

        val ontologyObj: JsonLDObject = jsonLDDocument.requireObject(OntologyConstants.KnoraApiV2WithValueObjects.HasOntologies)
        val externalOntologyIri: SmartIri = ontologyObj.requireString("@id", stringFormatter.toSmartIriWithErr)

        if (!(externalOntologyIri.isKnoraOntologyIri && externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects))) {
            throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIri")
        }

        // The knora-api:lastModificationDate submitted with an update request is the one returned by the previous read or update request.
        val lastModificationDate: Instant = ontologyObj.requireString(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.toInstant)

        val hasProperties: Map[String, JsonLDValue] = ontologyObj.requireObject(OntologyConstants.KnoraApiV2WithValueObjects.HasProperties).value

        if (hasProperties.isEmpty || hasProperties.size > 1) {
            throw BadRequestException(s"${OntologyConstants.KnoraApiV2WithValueObjects.HasProperties} must contain one property definition")
        }

        val propertyDef: JsonLDObject = hasProperties.values.head match {
            case obj: JsonLDObject => obj
            case _ => throw BadRequestException(s"The definition of property ${hasProperties.keys.head} is invalid")
        }

        val extraPredicates = propertyDef.value.keySet -- PropertyPredicates

        if (extraPredicates.nonEmpty) {
            throw BadRequestException(s"One or more submitted property predicates are not allowed: ${extraPredicates.mkString(", ")}")
        }

        val propertyIri: SmartIri = propertyDef.requireString("@id", stringFormatter.toSmartIriWithErr)

        if (!(propertyIri.isKnoraApiV2EntityIri &&
            propertyIri.getOntologySchema.contains(ApiV2WithValueObjects) &&
            propertyIri.getOntologyFromEntity == externalOntologyIri)) {
            throw BadRequestException(s"Invalid property IRI: $propertyIri")
        }

        val propertyType: SmartIri = propertyDef.requireString("@type", stringFormatter.toSmartIriWithErr)

        if (propertyType != OntologyConstants.Owl.ObjectProperty.toSmartIri) {
            throw BadRequestException(s"Property $propertyIri must be an owl:ObjectProperty")
        }

        val rdfTypePred = PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            ontologyIri = OntologyConstants.Rdf.RdfOntologyIri.toSmartIri,
            objects = Set(propertyType.toString)
        )

        val subPropertyOf: Set[SmartIri] = propertyDef.requireArray(OntologyConstants.Rdfs.SubPropertyOf).value.map {
            case JsonLDString(superProperty) => superProperty.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: $superProperty"))
            case other => throw BadRequestException(s"Expected property IRI: $other")
        }.toSet

        val subjectType: SmartIri = propertyDef.requireString(OntologyConstants.KnoraApiV2WithValueObjects.SubjectType, stringFormatter.toSmartIriWithErr)

        val subjectTypePred = PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri,
            ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
            objects = Set(subjectType.toString)
        )

        val objectType: SmartIri = propertyDef.requireString(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType, stringFormatter.toSmartIriWithErr)

        val objectTypePred = PredicateInfoV2(
            predicateIri = OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri,
            ontologyIri = OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri,
            objects = Set(objectType.toString)
        )

        val labelPred = PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
            ontologyIri = externalOntologyIri,
            objectsWithLang = propertyDef.requireArray(OntologyConstants.Rdfs.Label).toObjsWithLang
        )

        val commentPred = PredicateInfoV2(
            predicateIri = OntologyConstants.Rdfs.Comment.toSmartIri,
            ontologyIri = externalOntologyIri,
            objectsWithLang = propertyDef.requireArray(OntologyConstants.Rdfs.Comment).toObjsWithLang
        )

        CreatePropertyRequestV2(
            propertyInfoContent = PropertyInfoContentV2(
                propertyIri = propertyIri,
                ontologyIri = externalOntologyIri,
                predicates = Map(
                    OntologyConstants.Rdf.Type.toSmartIri -> rdfTypePred,
                    OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri -> subjectTypePred,
                    OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri -> objectTypePred,
                    OntologyConstants.Rdfs.Label.toSmartIri -> labelPred,
                    OntologyConstants.Rdfs.Comment.toSmartIri -> commentPred
                ),
                subPropertyOf = subPropertyOf,
                ontologySchema = ApiV2WithValueObjects
            ),
            lastModificationDate = lastModificationDate,
            apiRequestID = apiRequestID,
            userProfile = userProfile
        )
    }
}

/**
  * Requests a change in the metadata of an ontology. A successful response will be a [[ReadOntologyMetadataV2]].
  *
  * @param ontologyIri          the external ontology IRI.
  * @param label                the ontology's new label.
  * @param lastModificationDate the ontology's last modification date, returned in a previous operation.
  * @param apiRequestID         the ID of the API request.
  * @param userProfile          the profile of the user making the request.
  */
case class ChangeOntologyMetadataRequestV2(ontologyIri: SmartIri,
                                           label: String,
                                           lastModificationDate: Instant,
                                           apiRequestID: UUID,
                                           userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Constructs instances of [[ChangeOntologyMetadataRequestV2]] based on JSON-LD requests.
  */
object ChangeOntologyMetadataRequestV2 extends KnoraJsonLDRequestReaderV2[ChangeOntologyMetadataRequestV2] {
    override def fromJsonLD(jsonLDDocument: JsonLDDocument,
                            apiRequestID: UUID,
                            userProfile: UserProfileV1): ChangeOntologyMetadataRequestV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val externalOntologyIri: SmartIri = jsonLDDocument.requireString("@id", stringFormatter.toSmartIriWithErr)
        val label: String = jsonLDDocument.requireString(OntologyConstants.Rdfs.Label, stringFormatter.toSparqlEncodedString)
        val lastModificationDate: Instant = jsonLDDocument.requireString(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.toInstant)

        ChangeOntologyMetadataRequestV2(
            ontologyIri = externalOntologyIri,
            label = label,
            lastModificationDate = lastModificationDate,
            apiRequestID = apiRequestID,
            userProfile = userProfile
        )
    }
}

/**
  * Requests all available information about a list of ontology entities (classes and/or properties). A successful response will be an
  * [[EntityInfoGetResponseV2]].
  *
  * @param classIris    the IRIs of the class entities to be queried.
  * @param propertyIris the IRIs of the property entities to be queried.
  * @param userProfile  the profile of the user making the request.
  */
case class EntityInfoGetRequestV2(classIris: Set[SmartIri] = Set.empty[SmartIri], propertyIris: Set[SmartIri] = Set.empty[SmartIri], userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Represents assertions about one or more ontology entities (resource classes and/or properties).
  *
  * @param classInfoMap    a [[Map]] of class entity IRIs to [[ReadClassInfoV2]] objects.
  * @param propertyInfoMap a [[Map]] of property entity IRIs to [[ReadPropertyInfoV2]] objects.
  */
case class EntityInfoGetResponseV2(classInfoMap: Map[SmartIri, ReadClassInfoV2],
                                   propertyInfoMap: Map[SmartIri, ReadPropertyInfoV2])

/**
  * Requests all available information about a list of ontology entities (standoff classes and/or properties). A successful response will be an
  * [[StandoffEntityInfoGetResponseV2]].
  *
  * @param standoffClassIris    the IRIs of the resource entities to be queried.
  * @param standoffPropertyIris the IRIs of the property entities to be queried.
  * @param userProfile          the profile of the user making the request.
  */
case class StandoffEntityInfoGetRequestV2(standoffClassIris: Set[SmartIri] = Set.empty[SmartIri], standoffPropertyIris: Set[SmartIri] = Set.empty[SmartIri], userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Represents assertions about one or more ontology entities (resource classes and/or properties).
  *
  * @param standoffClassInfoMap    a [[Map]] of standoff class IRIs to [[ReadClassInfoV2]] objects.
  * @param standoffPropertyInfoMap a [[Map]] of standoff property IRIs to [[ReadPropertyInfoV2]] objects.
  */
case class StandoffEntityInfoGetResponseV2(standoffClassInfoMap: Map[SmartIri, ReadClassInfoV2],
                                           standoffPropertyInfoMap: Map[SmartIri, ReadPropertyInfoV2])

/**
  * Requests information about all standoff classes that are a subclass of a data type standoff class. A successful response will be an
  * [[StandoffClassesWithDataTypeGetResponseV2]].
  *
  * @param userProfile the profile of the user making the request.
  */
case class StandoffClassesWithDataTypeGetRequestV2(userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Represents assertions about all standoff classes that are a subclass of a data type standoff class.
  *
  * @param standoffClassInfoMap a [[Map]] of standoff class entity IRIs to [[ReadClassInfoV2]] objects.
  */
case class StandoffClassesWithDataTypeGetResponseV2(standoffClassInfoMap: Map[SmartIri, ReadClassInfoV2])

/**
  * Requests information about all standoff property entities. A successful response will be an
  * [[StandoffAllPropertyEntitiesGetResponseV2]].
  *
  * @param userProfile the profile of the user making the request.
  */
case class StandoffAllPropertyEntitiesGetRequestV2(userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Represents assertions about all standoff all standoff property entities.
  *
  * @param standoffAllPropertiesEntityInfoMap a [[Map]] of standoff property IRIs to [[ReadPropertyInfoV2]] objects.
  */
case class StandoffAllPropertyEntitiesGetResponseV2(standoffAllPropertiesEntityInfoMap: Map[SmartIri, ReadPropertyInfoV2])

/**
  * Checks whether a Knora resource or value class is a subclass of (or identical to) another class.
  * A successful response will be a [[CheckSubClassResponseV2]].
  *
  * @param subClassIri   the IRI of the subclass.
  * @param superClassIri the IRI of the superclass.
  */
case class CheckSubClassRequestV2(subClassIri: SmartIri, superClassIri: SmartIri, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Represents a response to a [[CheckSubClassRequestV2]].
  *
  * @param isSubClass `true` if the requested inheritance relationship exists.
  */
case class CheckSubClassResponseV2(isSubClass: Boolean)

/**
  * Requests information about the subclasses of a Knora resource class. A successful response will be
  * a [[SubClassesGetResponseV2]].
  *
  * @param resourceClassIri the IRI of the given resource class.
  * @param userProfile      the profile of the user making the request.
  */
case class SubClassesGetRequestV2(resourceClassIri: SmartIri, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Provides information about the subclasses of a Knora resource class.
  *
  * @param subClasses a list of [[SubClassInfoV2]] representing the subclasses of the specified class.
  */
case class SubClassesGetResponseV2(subClasses: Seq[SubClassInfoV2])

/**
  *
  * Request information about the entities of a named graph. A succesful response will be a [[OntologyEntitiesIriInfoV2]].
  *
  * @param ontologyIri the IRI of the named graph.
  * @param userProfile the profile of the user making the request.
  */
case class OntologyEntityIrisGetRequestV2(ontologyIri: SmartIri, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests metadata about ontologies.
  *
  * @param projectIris the IRIs of the projects for which ontologies should be returned. If this set is empty, information
  *                    about all ontologies is returned.
  * @param userProfile the profile of the user making the request.
  */
case class OntologyMetadataGetRequestV2(projectIris: Set[SmartIri] = Set.empty[SmartIri], userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests entity definitions for the given ontologies.
  *
  * @param ontologyGraphIris the ontologies to query for.
  * @param responseSchema    the API schema that will be used for the response.
  * @param allLanguages      true if information in all available languages should be returned.
  * @param userProfile       the profile of the user making the request.
  */
case class OntologyEntitiesGetRequestV2(ontologyGraphIris: Set[SmartIri], responseSchema: ApiV2Schema, allLanguages: Boolean, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests the entity definitions for the given class IRIs. A successful response will be a [[ReadOntologiesV2]].
  *
  * @param resourceClassIris the IRIs of the classes to be queried.
  * @param responseSchema    the API schema that will be used for the response.
  * @param allLanguages      true if information in all available languages should be returned.
  * @param userProfile       the profile of the user making the request.
  */
case class ClassesGetRequestV2(resourceClassIris: Set[SmartIri], responseSchema: ApiV2Schema, allLanguages: Boolean, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests the definitions of the specified properties. A successful response will be a [[ReadOntologiesV2]].
  *
  * @param propertyIris the IRIs of the properties to be queried.
  * @param allLanguages true if information in all available languages should be returned.
  * @param userProfile  the profile of the user making the request.
  */
case class PropertiesGetRequestV2(propertyIris: Set[SmartIri], allLanguages: Boolean, userProfile: UserProfileV1) extends OntologiesResponderRequestV2


/**
  * Represents the contents of an ontology to be returned in an API response.
  *
  * @param ontologyMetadata     metadata about the ontology.
  * @param classes              information about non-standoff classes.
  * @param properties           information about non-standoff properties.
  * @param standoffClasses      information about standoff classes.
  * @param standoffProperties   information about standoff properties.
  * @param userLang             the preferred language in which the information should be returned, or [[None]] if information
  *                             should be returned in all available languages.
  */
case class ReadOntologyV2(ontologyMetadata: OntologyMetadataV2,
                          classes: Map[SmartIri, ReadClassInfoV2] = Map.empty[SmartIri, ReadClassInfoV2],
                          properties: Map[SmartIri, ReadPropertyInfoV2] = Map.empty[SmartIri, ReadPropertyInfoV2],
                          standoffClasses: Map[SmartIri, ReadClassInfoV2] = Map.empty[SmartIri, ReadClassInfoV2],
                          standoffProperties: Map[SmartIri, ReadPropertyInfoV2] = Map.empty[SmartIri, ReadPropertyInfoV2],
                          userLang: Option[String] = None) {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * Converts this [[ReadOntologyV2]] to the specified Knora API v2 schema.
      *
      * @param targetSchema the target schema.
      * @return the converted [[ReadOntologyV2]].
      */
    def toOntologySchema(targetSchema: ApiV2Schema): ReadOntologyV2 = {
        // If we're converting to the API v2 simple schema, filter out link value properties.
        val filteredProperties = targetSchema match {
            case ApiV2Simple =>
                properties.filterNot {
                    case (_, propertyInfo) => propertyInfo.isLinkValueProp
                }

            case _ => properties
        }

        val convertedProperties = filteredProperties.map {
            case (propertyIri, readPropertyInfo) => propertyIri.toOntologySchema(targetSchema) -> readPropertyInfo.toOntologySchema(targetSchema)
        }

        copy(
            ontologyMetadata = ontologyMetadata.toOntologySchema(targetSchema),
            classes = classes.map {
                case (classIri, readClassInfo) => classIri.toOntologySchema(targetSchema) -> readClassInfo.toOntologySchema(targetSchema)
            },
            properties = convertedProperties,
            standoffClasses = standoffClasses.map {
                case (classIri, readClassInfo) => classIri.toOntologySchema(targetSchema) -> readClassInfo.toOntologySchema(targetSchema)
            },
            standoffProperties = standoffProperties.map {
                case (propertyIri, readPropertyInfo) => propertyIri.toOntologySchema(targetSchema) -> readPropertyInfo.toOntologySchema(targetSchema)
            }
        )
    }

    def toJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDObject = {
        def classesToJsonLD(classDefs: Map[SmartIri, ReadClassInfoV2]): Map[IRI, JsonLDObject] = {
            classDefs.map {
                case (classIri: SmartIri, resourceEntity: ReadClassInfoV2) =>
                    val jsonClass = userLang match {
                        case Some(lang) => resourceEntity.toJsonLDWithSingleLanguage(targetSchema = targetSchema, userLang = lang, settings = settings)
                        case None => resourceEntity.toJsonLDWithAllLanguages(targetSchema = targetSchema)
                    }

                    classIri.toString -> jsonClass
            }
        }

        def propertiesToJsonLD(propertyDefs: Map[SmartIri, ReadPropertyInfoV2]): Map[IRI, JsonLDObject] = {
            propertyDefs.map {
                case (propertyIri, propertyInfo) =>
                    // If this is a knora-api property, use its constant definition, otherwise use the one we were given.
                    val schemaPropertyInfo = targetSchema match {
                        case ApiV2Simple => KnoraApiV2Simple.Properties.getOrElse(propertyIri, propertyInfo)
                        case ApiV2WithValueObjects => KnoraApiV2WithValueObjects.Properties.getOrElse(propertyIri, propertyInfo)
                    }

                    val propJson: JsonLDObject = userLang match {
                        case Some(lang) => schemaPropertyInfo.toJsonLDWithSingleLanguage(targetSchema = targetSchema, userLang = lang, settings = settings)
                        case None => schemaPropertyInfo.toJsonLDWithAllLanguages(targetSchema = targetSchema)
                    }

                    propertyIri.toString -> propJson
            }
        }

        // classes

        val jsonClasses: Map[IRI, JsonLDObject] = classesToJsonLD(classes)

        // properties

        val jsonProperties: Map[IRI, JsonLDObject] = propertiesToJsonLD(properties)

        // standoff classes and properties

        val hasStandoffClassesProp = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.HasStandoffClasses
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffClasses
        }

        val hasStandoffPropertiesProp = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.HasStandoffProperties
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffProperties
        }

        val jsonStandoffClasses: Map[IRI, JsonLDObject] = classesToJsonLD(standoffClasses)
        val jsonStandoffProperties: Map[IRI, JsonLDObject] = propertiesToJsonLD(standoffProperties)

        val jsonStandoffEntities: Map[IRI, JsonLDObject] = Map(
            hasStandoffClassesProp -> JsonLDObject(jsonStandoffClasses),
            hasStandoffPropertiesProp -> JsonLDObject(jsonStandoffProperties)
        )

        val hasClassesProp = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.HasClasses
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.HasClasses
        }

        val hasPropertiesProp = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.HasProperties
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.HasProperties
        }

        JsonLDObject(
            ontologyMetadata.toJsonLD(targetSchema) ++
                Map(hasClassesProp -> JsonLDObject(jsonClasses),
                    hasPropertiesProp -> JsonLDObject(jsonProperties)) ++
                jsonStandoffEntities
        )
    }
}

/**
  * Represents information about ontologies received as input, either from the client or from the API server (in
  * the case of a test). This information is necessarily less complete than the information in a [[ReadOntologiesV2]],
  * which takes advantage of additional knowledge that is available from the triplestore.
  *
  * @param ontologies information about ontologies.
  */
case class InputOntologiesV2(ontologies: Seq[InputOntologyV2]) {
    /**
      * Converts this [[InputOntologiesV2]] to the specified Knora API v2 schema.
      *
      * @param targetSchema the target schema.
      * @return the converted [[InputOntologiesV2]].
      */
    def toOntologySchema(targetSchema: ApiV2Schema): InputOntologiesV2 = {
        InputOntologiesV2(ontologies.map(_.toOntologySchema(targetSchema)))
    }
}

object InputOntologiesV2 extends KnoraJsonLDRequestReaderV2[InputOntologiesV2] {
    /**
      * Constructs an [[InputOntologiesV2]] based on JSON-LD input.
      *
      * @param jsonLDDocument the JSON-LD input.
      * @param apiRequestID   the UUID of the API request.
      * @param userProfile    the profile of the user making the request.
      * @return a case class instance representing the input.
      */
    override def fromJsonLD(jsonLDDocument: JsonLDDocument, apiRequestID: UUID, userProfile: UserProfileV1): InputOntologiesV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val hasOntologies: JsonLDArray = jsonLDDocument.requireArray(OntologyConstants.KnoraApiV2WithValueObjects.HasOntologies)

        val ontologies: Seq[InputOntologyV2] = hasOntologies.value.map {
            case ontologyObj: JsonLDObject => InputOntologyV2.fromJsonLDObject(ontologyObj)
            case other => throw BadRequestException(s"Unexpected JSON-LD value: $other")
        }

        InputOntologiesV2(ontologies)
    }
}

/**
  * Represents information about an ontology received as input, either from the client or from the API server (in
  * the case of a test). This information is necessarily less complete than the information in a [[ReadOntologyV2]],
  * which takes advantage of additional knowledge that is available from the triplestore.
  *
  * @param ontologyMetadata metadata about the ontology.
  * @param classes information about classes in the ontology.
  * @param properties information about properties in the ontology.
  * @param standoffClasses information about standoff classes in the ontology.
  * @param standoffProperties information about standoff properties in the ontology.
  */
case class InputOntologyV2(ontologyMetadata: OntologyMetadataV2,
                           classes: Map[SmartIri, ClassInfoContentV2] = Map.empty[SmartIri, ClassInfoContentV2],
                           properties: Map[SmartIri, PropertyInfoContentV2] = Map.empty[SmartIri, PropertyInfoContentV2],
                           standoffClasses: Map[SmartIri, ClassInfoContentV2] = Map.empty[SmartIri, ClassInfoContentV2],
                           standoffProperties: Map[SmartIri, PropertyInfoContentV2] = Map.empty[SmartIri, PropertyInfoContentV2]) {

    /**
      * Converts this [[InputOntologyV2]] to the specified Knora API v2 schema.
      *
      * @param targetSchema the target schema.
      * @return the converted [[InputOntologyV2]].
      */
    def toOntologySchema(targetSchema: ApiV2Schema): InputOntologyV2 = {
        InputOntologyV2(
            ontologyMetadata = ontologyMetadata.toOntologySchema(targetSchema),
            classes = classes.map {
                case (classIri, classInfoContent) => classIri.toOntologySchema(targetSchema) -> classInfoContent.toOntologySchema(targetSchema)
            },
            properties = properties.map {
                case (propertyIri, propertyInfoContent) => propertyIri.toOntologySchema(targetSchema) -> propertyInfoContent.toOntologySchema(targetSchema)
            },
            standoffClasses = standoffClasses.map {
                case (classIri, classInfoContent) => classIri.toOntologySchema(targetSchema) -> classInfoContent.toOntologySchema(targetSchema)
            },
            standoffProperties = standoffProperties.map {
                case (propertyIri, propertyInfoContent) => propertyIri.toOntologySchema(targetSchema) -> propertyInfoContent.toOntologySchema(targetSchema)
            }
        )
    }
}

object InputOntologyV2 {
    private def jsonLDObjectToProperties(maybeJsonLDObject: Option[JsonLDObject]): Map[SmartIri, PropertyInfoContentV2] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        maybeJsonLDObject match {
            case Some(jsonLDObject: JsonLDObject) =>
                jsonLDObject.value.map {
                    case (propertyIrStr, jsonPropertyDef: JsonLDObject) =>
                        propertyIrStr.toSmartIri -> PropertyInfoContentV2.fromJsonLDObject(jsonPropertyDef)

                    case (propertyIri, _) => throw BadRequestException(s"The definition of property $propertyIri is invalid")
                }

            case None => Map.empty[SmartIri, PropertyInfoContentV2]
        }
    }

    private def jsonLDObjectToClasses(maybeJsonLDObject: Option[JsonLDObject]): Map[SmartIri, ClassInfoContentV2] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        maybeJsonLDObject match {
            case Some(jsonLDObject: JsonLDObject) =>
                jsonLDObject.value.map {
                    case (classIriStr, jsonClassDef: JsonLDObject) =>
                        classIriStr.toSmartIri -> ClassInfoContentV2.fromJsonLDObject(jsonClassDef)

                    case (classIriStr, _) => throw BadRequestException(s"The definition of class $classIriStr is invalid")
                }

            case None => Map.empty[SmartIri, ClassInfoContentV2]
        }
    }

    /**
      * Constructs an [[InputOntologyV2]] based on a JSON-LD object.
      *
      * @param ontologyObj a JSON-LD object representing information about the ontology.
      * @return an [[InputOntologyV2]] representing the same information.
      */
    def fromJsonLDObject(ontologyObj: JsonLDObject): InputOntologyV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val externalOntologyIri: SmartIri = ontologyObj.requireString("@id", stringFormatter.toSmartIriWithErr)

        if (!(externalOntologyIri.isKnoraApiV2DefinitionIri && externalOntologyIri.isKnoraOntologyIri)) {
            throw BadRequestException(s"Invalid ontology IRI: $externalOntologyIri")
        }

        val ontologyLabel = ontologyObj.requireString(OntologyConstants.Rdfs.Label, stringFormatter.toSparqlEncodedString)

        val lastModificationDate: Option[Instant] =
            ontologyObj.maybeString(OntologyConstants.KnoraApiV2Simple.LastModificationDate, stringFormatter.toInstant).
                orElse(ontologyObj.maybeString(OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate, stringFormatter.toInstant))

        val ontologyMetadata = OntologyMetadataV2(ontologyIri = externalOntologyIri, label = ontologyLabel, lastModificationDate = lastModificationDate)

        val maybeHasClasses: Option[JsonLDObject] = ontologyObj.maybeObject(OntologyConstants.KnoraApiV2Simple.HasClasses).
            orElse(ontologyObj.maybeObject(OntologyConstants.KnoraApiV2WithValueObjects.HasClasses))

        val maybeHasProperties: Option[JsonLDObject] = ontologyObj.maybeObject(OntologyConstants.KnoraApiV2Simple.HasProperties).
            orElse(ontologyObj.maybeObject(OntologyConstants.KnoraApiV2WithValueObjects.HasProperties))

        val maybeHasStandoffClasses: Option[JsonLDObject] = ontologyObj.maybeObject(OntologyConstants.KnoraApiV2Simple.HasStandoffClasses).
            orElse(ontologyObj.maybeObject(OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffClasses))

        val maybeHasStandoffProperties: Option[JsonLDObject] = ontologyObj.maybeObject(OntologyConstants.KnoraApiV2Simple.HasStandoffProperties).
            orElse(ontologyObj.maybeObject(OntologyConstants.KnoraApiV2WithValueObjects.HasStandoffProperties))

        val classes: Map[SmartIri, ClassInfoContentV2] = jsonLDObjectToClasses(maybeHasClasses)
        val properties: Map[SmartIri, PropertyInfoContentV2] = jsonLDObjectToProperties(maybeHasProperties)
        val standoffClasses: Map[SmartIri, ClassInfoContentV2] = jsonLDObjectToClasses(maybeHasStandoffClasses)
        val standoffProperties: Map[SmartIri, PropertyInfoContentV2] = jsonLDObjectToProperties(maybeHasStandoffProperties)

        InputOntologyV2(
            ontologyMetadata = ontologyMetadata,
            classes = classes,
            properties = properties,
            standoffClasses = standoffClasses,
            standoffProperties = standoffProperties
        )
    }
}


/**
  * Represents the contents of one or more ontologies to be returned in an API response.
  *
  * @param ontologies the contents of the ontologies.
  */
case class ReadOntologiesV2(ontologies: Seq[ReadOntologyV2]) extends KnoraResponseV2 {
    private implicit def stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    override def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        toOntologySchema(targetSchema).generateJsonLD(targetSchema, settings)
    }

    /**
      * Converts this [[ReadOntologiesV2]] to the specified ontology schema.
      *
      * @param targetSchema the target schema.
      * @return the same ontology definitions as represented in the target schema.
      */
    def toOntologySchema(targetSchema: ApiV2Schema): ReadOntologiesV2 = {
        copy(ontologies.map(_.toOntologySchema(targetSchema)))
    }

    private def generateJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        // To make prefix labels, we need the ontologies of all entities mentioned in all the ontologies
        // to be returned. First, get the ontologies of all entities mentioned in class definitions.

        val allClasses = ontologies.flatMap(ontology => ontology.classes ++ ontology.standoffClasses).toMap

        val ontologiesFromClasses: Set[SmartIri] = allClasses.values.flatMap {
            classInfo =>
                val entityIris: Set[SmartIri] = classInfo.allCardinalities.keySet ++ classInfo.entityInfoContent.subClassOf

                entityIris.flatMap {
                    entityIri =>
                        if (entityIri.isKnoraEntityIri) {
                            Set(entityIri.getOntologyFromEntity)
                        } else {
                            Set.empty[SmartIri]
                        }
                } + classInfo.entityInfoContent.ontologyIri
        }.toSet

        // Get the ontologies of all entities mentioned in property definitions.

        val allProperties = ontologies.flatMap(ontology => ontology.properties ++ ontology.standoffProperties).toMap

        val ontologiesFromProperties: Set[SmartIri] = allProperties.values.flatMap {
            property =>
                val entityIris = property.entityInfoContent.subPropertyOf ++
                    property.entityInfoContent.getPredicateIriObjects(OntologyConstants.KnoraApiV2Simple.SubjectType.toSmartIri) ++
                    property.entityInfoContent.getPredicateIriObjects(OntologyConstants.KnoraApiV2Simple.ObjectType.toSmartIri) ++
                    property.entityInfoContent.getPredicateIriObjects(OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri) ++
                    property.entityInfoContent.getPredicateIriObjects(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri)

                entityIris.flatMap {
                    entityIri =>
                        if (entityIri.isKnoraEntityIri) {
                            Set(entityIri.getOntologyFromEntity)
                        } else {
                            Set.empty[SmartIri]
                        }
                } + property.entityInfoContent.ontologyIri
        }.toSet

        val ontologiesUsed: Set[SmartIri] = ontologiesFromClasses ++ ontologiesFromProperties

        // Make JSON-LD prefixes for the ontologies used in the response.
        val ontologyPrefixes: Map[String, JsonLDString] = ontologiesUsed.map {
            ontologyIri =>
                ontologyIri.getPrefixLabel -> JsonLDString(ontologyIri.toString + "#")
        }.toMap

        // Determine which ontology to use as the knora-api prefix expansion.
        val knoraApiPrefixExpansion = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion
        }

        // Make the JSON-LD context.
        val context = JsonLDObject(Map(
            OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(knoraApiPrefixExpansion),
            "rdfs" -> JsonLDString("http://www.w3.org/2000/01/rdf-schema#"),
            "rdf" -> JsonLDString("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
            "owl" -> JsonLDString("http://www.w3.org/2002/07/owl#"),
            "xsd" -> JsonLDString("http://www.w3.org/2001/XMLSchema#")
        ) ++ ontologyPrefixes)

        val ontologiesJson: Seq[JsonLDObject] = ontologies.map(_.toJsonLD(targetSchema, settings))

        val hasOntologiesProp = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.HasOntologies
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.HasOntologies
        }

        val body = JsonLDObject(Map(
            hasOntologiesProp -> JsonLDArray(ontologiesJson)
        ))

        JsonLDDocument(body = body, context = context)
    }
}

/**
  * Returns metadata about Knora ontologies.
  *
  * @param ontologies      the metadata to be returned.
  * @param includeKnoraApi if true, includes metadata about the `knora-api` ontology for the target schema.
  */
case class ReadOntologyMetadataV2(ontologies: Set[OntologyMetadataV2], includeKnoraApi: Boolean = false) extends KnoraResponseV2 {

    private def toOntologySchema(targetSchema: ApiV2Schema): ReadOntologyMetadataV2 = {
        copy(
            ontologies = ontologies.map(_.toOntologySchema(targetSchema))
        )
    }

    private def generateJsonLD(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        val knoraApiOntologyPrefixExpansion = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion
        }

        val context = JsonLDObject(Map(
            OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(knoraApiOntologyPrefixExpansion),
            "rdfs" -> JsonLDString(OntologyConstants.Rdfs.RdfsPrefixExpansion)
        ))

        val maybeKnoraApiMetadata = if (includeKnoraApi) {
            targetSchema match {
                case ApiV2Simple => Some(KnoraApiV2Simple.OntologyMetadata)
                case ApiV2WithValueObjects => Some(KnoraApiV2WithValueObjects.OntologyMetadata)
            }
        } else {
            None
        }

        val ontologiesWithKnoraApi = ontologies ++ maybeKnoraApiMetadata
        val ontologiesJson: Vector[JsonLDObject] = ontologiesWithKnoraApi.toVector.sortBy(_.ontologyIri).map(ontology => JsonLDObject(ontology.toJsonLD(targetSchema)))

        val hasOntologiesProp = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.HasOntologies
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.HasOntologies
        }

        val body = JsonLDObject(Map(
            hasOntologiesProp -> JsonLDArray(ontologiesJson)
        ))

        JsonLDDocument(body = body, context = context)
    }

    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        toOntologySchema(targetSchema).generateJsonLD(targetSchema, settings)
    }
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a predicate that is asserted about a given ontology entity, and the objects of that predicate.
  *
  * @param ontologyIri     the IRI of the ontology in which the assertions occur.
  * @param objects         the objects of the predicate that have no language codes.
  * @param objectsWithLang the objects of the predicate that have language codes: a Map of language codes to literals.
  */
case class PredicateInfoV2(predicateIri: SmartIri,
                           ontologyIri: SmartIri,
                           objects: Set[String] = Set.empty[String],
                           objectsWithLang: Map[String, String] = Map.empty[String, String]) {
    // TODO: This class should really store its IRI objects as SmartIris. But this would need more help
    // from OntologyResponderV2 and probably also from the store package (#668).

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * Converts this [[PredicateInfoV2]] to another ontology schema, without converting its objects.
      *
      * @param targetSchema the target schema.
      * @return the converted [[PredicateInfoV2]].
      */
    def justPredicateToOntologySchema(targetSchema: OntologySchema): PredicateInfoV2 = {
        copy(
            predicateIri = predicateIri.toOntologySchema(targetSchema),
            ontologyIri = ontologyIri.toOntologySchema(targetSchema)
        )
    }

    /**
      * Converts this [[PredicateInfoV2]] and all its objects (which must all be non-language-specific)
      * from one ontology schema to another. May be used only if the predicate is known to have IRIs as objects.
      *
      * @param targetSchema the target schema.
      * @return the converted [[PredicateInfoV2]].
      */
    def predicateAndObjectsToOntologySchema(targetSchema: OntologySchema): PredicateInfoV2 = {
        if (objectsWithLang.nonEmpty) {
            throw DataConversionException(s"The objects of $predicateIri cannot be converted to schema $targetSchema, because they are not IRIs")
        }

        copy(
            predicateIri = predicateIri.toOntologySchema(targetSchema),
            ontologyIri = ontologyIri.toOntologySchema(targetSchema),
            objects = objects.map(_.toSmartIri.toOntologySchema(targetSchema).toString)
        )
    }
}

/**
  * Represents the OWL cardinalities that Knora supports.
  */
object Cardinality extends Enumeration {

    /**
      * Represents information about an OWL cardinality.
      *
      * @param owlCardinalityIri   the IRI of the OWL cardinality, which must be a member of the set
      *                            [[OntologyConstants.Owl.cardinalityOWLRestrictions]].
      * @param owlCardinalityValue the value of the OWL cardinality, which must be 0 or 1.
      * @return a [[Value]].
      */
    case class OwlCardinalityInfo(owlCardinalityIri: IRI, owlCardinalityValue: Int) {
        if (!OntologyConstants.Owl.cardinalityOWLRestrictions.contains(owlCardinalityIri)) {
            throw InconsistentTriplestoreDataException(s"Invalid OWL cardinality property: $owlCardinalityIri")
        }

        if (!(owlCardinalityValue == 0 || owlCardinalityValue == 1)) {
            throw InconsistentTriplestoreDataException(s"Invalid OWL cardinality value: $owlCardinalityValue")
        }

        override def toString: String = s"<$owlCardinalityIri> $owlCardinalityValue"
    }

    type Cardinality = Value

    val MayHaveOne: Value = Value(0, "0-1")
    val MayHaveMany: Value = Value(1, "0-n")
    val MustHaveOne: Value = Value(2, "1")
    val MustHaveSome: Value = Value(3, "1-n")

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

    /**
      * The valid mappings between Knora cardinalities and OWL cardinalities.
      */
    private val knoraCardinality2OwlCardinalityMap: Map[Value, OwlCardinalityInfo] = Map(
        MayHaveOne -> OwlCardinalityInfo(owlCardinalityIri = OntologyConstants.Owl.MaxCardinality, owlCardinalityValue = 1),
        MayHaveMany -> OwlCardinalityInfo(owlCardinalityIri = OntologyConstants.Owl.MinCardinality, owlCardinalityValue = 0),
        MustHaveOne -> OwlCardinalityInfo(owlCardinalityIri = OntologyConstants.Owl.Cardinality, owlCardinalityValue = 1),
        MustHaveSome -> OwlCardinalityInfo(owlCardinalityIri = OntologyConstants.Owl.MinCardinality, owlCardinalityValue = 1)
    )

    private val owlCardinality2KnoraCardinalityMap: Map[OwlCardinalityInfo, Value] = knoraCardinality2OwlCardinalityMap.map {
        case (knoraC, owlC) => (owlC, knoraC)
    }

    /**
      * Given the name of a value in this enumeration, returns the value. If the value is not found, throws an
      * [[InconsistentTriplestoreDataException]].
      *
      * @param name the name of the value.
      * @return the requested value.
      */
    def lookup(name: String): Value = {
        valueMap.get(name) match {
            case Some(value) => value
            case None => throw InconsistentTriplestoreDataException(s"Cardinality not found: $name")
        }
    }

    /**
      * Converts information about an OWL cardinality restriction to a [[Value]] of this enumeration.
      *
      * @param propertyIri    the IRI of the property that the OWL cardinality applies to.
      * @param owlCardinality information about an OWL cardinality.
      * @return a [[Value]].
      */
    def owlCardinality2KnoraCardinality(propertyIri: IRI, owlCardinality: OwlCardinalityInfo): Value = {
        owlCardinality2KnoraCardinalityMap.getOrElse(owlCardinality, throw InconsistentTriplestoreDataException(s"Invalid OWL cardinality $owlCardinality for $propertyIri"))
    }

    /**
      * Converts a [[Value]] of this enumeration to information about an OWL cardinality restriction.
      *
      * @param knoraCardinality a [[Value]].
      * @return an [[OwlCardinalityInfo]].
      */
    def knoraCardinality2OwlCardinality(knoraCardinality: Value): OwlCardinalityInfo = knoraCardinality2OwlCardinalityMap(knoraCardinality)
}


/**
  * Represents information about an ontology entity (a class or property definition).
  */
sealed trait EntityInfoContentV2 {
    /**
      * The predicates of the entity, and their objects.
      */
    val predicates: Map[SmartIri, PredicateInfoV2]

    protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * Converts this entity's predicates from one ontology schema to another. Each predicate's IRI is converted,
      * and its objects are also optionally converted.
      *
      * @param predsWithKnoraDefinitionIriObjs a set of the predicates whose objects are known to be Knora definition IRIs.
      *                                        The objects of these predicates will be converted.
      * @param targetSchema                    the target schema.
      * @return a map of converted predicate IRIs to converted [[PredicateInfoV2]] objects.
      */
    protected def convertPredicates(predsWithKnoraDefinitionIriObjs: Set[SmartIri], targetSchema: OntologySchema): Map[SmartIri, PredicateInfoV2] = {
        predicates.map {
            case (predicateIri, predicateInfo) =>
                val convertedPredicateIri = predicateIri.toOntologySchema(targetSchema)

                val convertedPredicateInfo = if (predsWithKnoraDefinitionIriObjs.contains(predicateIri)) {
                    predicateInfo.predicateAndObjectsToOntologySchema(targetSchema)
                } else {
                    predicateInfo.justPredicateToOntologySchema(targetSchema)
                }

                convertedPredicateIri -> convertedPredicateInfo
        }
    }

    /**
      * Gets a predicate and its object from an entity in a specific language.
      *
      * @param predicateIri the IRI of the predicate.
      * @param userLang     the language in which the object should to be returned.
      * @return the requested predicate and object.
      */
    def getPredicateAndObjectWithLang(predicateIri: SmartIri, settings: SettingsImpl, userLang: String): Option[(SmartIri, String)] = {
        getPredicateLiteralObject(
            predicateIri = predicateIri,
            preferredLangs = Some(userLang, settings.fallbackLanguage)
        ).map(obj => predicateIri -> obj)
    }

    /**
      * Returns an object for a given predicate. If requested, attempts to return the object in the user's preferred
      * language, in the system's default language, or in any language, in that order.
      *
      * @param predicateIri   the IRI of the predicate.
      * @param preferredLangs the user's preferred language and the system's default language.
      * @return an object for the predicate, or [[None]] if this entity doesn't have the specified predicate, or
      *         if the predicate has no objects.
      */
    def getPredicateLiteralObject(predicateIri: SmartIri, preferredLangs: Option[(String, String)] = None): Option[String] = {
        // Does the predicate exist?
        predicates.get(predicateIri) match {
            case Some(predicateInfo) =>
                // Yes. Were preferred languages specified?
                preferredLangs match {
                    case Some((userLang, defaultLang)) =>
                        // Yes. Is the object available in the user's preferred language?
                        predicateInfo.objectsWithLang.get(userLang) match {
                            case Some(objectInUserLang) =>
                                // Yes.
                                Some(objectInUserLang)
                            case None =>
                                // The object is not available in the user's preferred language. Is it available
                                // in the system default language?
                                predicateInfo.objectsWithLang.get(defaultLang) match {
                                    case Some(objectInDefaultLang) =>
                                        // Yes.
                                        Some(objectInDefaultLang)
                                    case None =>
                                        // The object is not available in the system default language. Is it available
                                        // without a language tag?
                                        predicateInfo.objects.headOption match {
                                            case Some(objectWithoutLang) =>
                                                // Yes.
                                                Some(objectWithoutLang)
                                            case None =>
                                                // The object is not available without a language tag. Sort the
                                                // available objects by language code to get a deterministic result,
                                                // and return the object in the language with the lowest sort
                                                // order.
                                                predicateInfo.objectsWithLang.toVector.sortBy {
                                                    case (lang, _) => lang
                                                }.headOption.map {
                                                    case (_, obj) => obj
                                                }
                                        }
                                }
                        }

                    case None =>
                        // Preferred languages were not specified. Take the first object without a language tag.
                        predicateInfo.objects.headOption

                }

            case None => None
        }
    }

    /**
      * Returns all the non-language-specific, non-IRI objects specified for a given predicate.
      *
      * @param predicateIri the IRI of the predicate.
      * @return the predicate's objects, or an empty set if this entity doesn't have the specified predicate.
      */
    def getPredicateLiteralsWithoutLang(predicateIri: SmartIri): Set[String] = {
        predicates.get(predicateIri) match {
            case Some(predicateInfo) => predicateInfo.objects
            case None => Set.empty[String]
        }
    }

    /**
      * Returns all the IRI objects specified for a given predicate.
      *
      * @param predicateIri the IRI of the predicate.
      * @return the predicate's IRI objects, or an empty set if this entity doesn't have the specified predicate.
      */
    def getPredicateIriObjects(predicateIri: SmartIri): Set[SmartIri] = {
        getPredicateLiteralsWithoutLang(predicateIri).map(_.toSmartIri)
    }

    def getPredicateIriObject(predicateIri: SmartIri): Option[SmartIri] = getPredicateIriObjects(predicateIri).headOption

    /**
      * Returns all the objects specified for a given predicate, along with the language tag of each object.
      *
      * @param predicateIri the IRI of the predicate.
      * @return a map of language tags to objects, or an empty map if this entity doesn't have the specified predicate.
      */
    def getPredicateObjectsWithLangs(predicateIri: SmartIri): Map[String, String] = {
        predicates.get(predicateIri) match {
            case Some(predicateInfo) => predicateInfo.objectsWithLang
            case None => Map.empty[String, String]
        }
    }
}


/**
  * Represents information about either a resource or a property entity, as returned in an API response.
  */
sealed trait ReadEntityInfoV2 {
    /**
      * Provides basic information about the entity.
      */
    val entityInfoContent: EntityInfoContentV2

    protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * Returns the contents of a JSON-LD object containing non-language-specific information about the entity.
      *
      * @param targetSchema the API v2 schema in which the response will be returned.
      */
    protected def getNonLanguageSpecific(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue]

    /**
      * Returns a JSON-LD object representing the entity, with language-specific information provided in a single language.
      *
      * @param targetSchema the API v2 schema in which the response will be returned.
      * @param userLang     the user's preferred language.
      * @param settings     the application settings.
      * @return a JSON-LD object representing the entity.
      */
    def toJsonLDWithSingleLanguage(targetSchema: ApiV2Schema, userLang: String, settings: SettingsImpl): JsonLDObject = {
        val label: Option[(IRI, JsonLDString)] = entityInfoContent.getPredicateAndObjectWithLang(OntologyConstants.Rdfs.Label.toSmartIri, settings, userLang).map {
            case (k: SmartIri, v: String) => (k.toString, JsonLDString(v))
        }

        val comment: Option[(IRI, JsonLDString)] = entityInfoContent.getPredicateAndObjectWithLang(OntologyConstants.Rdfs.Comment.toSmartIri, settings, userLang).map {
            case (k: SmartIri, v: String) => (k.toString, JsonLDString(v))
        }

        JsonLDObject(getNonLanguageSpecific(targetSchema) ++ label ++ comment)
    }

    /**
      * Returns a JSON-LD object representing the entity, with language-specific information provided in all
      * available languages.
      *
      * @param targetSchema the API v2 schema in which the response will be returned.
      * @return a JSON-LD object representing the entity.
      */
    def toJsonLDWithAllLanguages(targetSchema: ApiV2Schema): JsonLDObject = {
        val labelObjs: Map[String, String] = entityInfoContent.getPredicateObjectsWithLangs(OntologyConstants.Rdfs.Label.toSmartIri)

        val labels: Option[(IRI, JsonLDArray)] = if (labelObjs.nonEmpty) {
            Some(OntologyConstants.Rdfs.Label -> JsonLDUtil.objectsWithLangsToJsonLDArray(labelObjs))
        } else {
            None
        }

        val commentObjs: Map[String, String] = entityInfoContent.getPredicateObjectsWithLangs(OntologyConstants.Rdfs.Comment.toSmartIri)

        val comments: Option[(IRI, JsonLDArray)] = if (commentObjs.nonEmpty) {
            Some(OntologyConstants.Rdfs.Comment -> JsonLDUtil.objectsWithLangsToJsonLDArray(commentObjs))
        } else {
            None
        }

        JsonLDObject(getNonLanguageSpecific(targetSchema) ++ labels ++ comments)
    }
}

/**
  * Represents assertions about an RDF property.
  *
  * @param propertyIri    the IRI of the queried property.
  * @param ontologyIri    the IRI of the ontology in which the property is defined.
  * @param predicates     a [[Map]] of predicate IRIs to [[PredicateInfoV2]] objects.
  * @param subPropertyOf  the property's direct superproperties.
  * @param ontologySchema indicates whether this ontology entity belongs to an internal ontology (for use in the
  *                       triplestore) or an external one (for use in the Knora API).
  */
case class PropertyInfoContentV2(propertyIri: SmartIri,
                                 ontologyIri: SmartIri,
                                 predicates: Map[SmartIri, PredicateInfoV2] = Map.empty[SmartIri, PredicateInfoV2],
                                 subPropertyOf: Set[SmartIri] = Set.empty[SmartIri],
                                 ontologySchema: OntologySchema) extends EntityInfoContentV2 with KnoraContentV2[PropertyInfoContentV2] {

    import PropertyInfoContentV2._

    def requireIriPredicate(predicateIri: SmartIri, errorFun: => Nothing): SmartIri = {
        predicates.getOrElse(predicateIri, errorFun).objects.headOption.getOrElse(errorFun).toSmartIri
    }

    override def toOntologySchema(targetSchema: OntologySchema): PropertyInfoContentV2 = {

        // Are we converting from the internal schema to the API v2 simple schema?
        val predicatesWithAdjustedRdfType: Map[SmartIri, PredicateInfoV2] = if (ontologySchema == InternalSchema && targetSchema == ApiV2Simple) {
            // Yes. Is this an object property?
            val rdfTypeIri = OntologyConstants.Rdf.Type.toSmartIri
            val sourcePropertyType: SmartIri = getPredicateIriObject(rdfTypeIri).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no rdf:type"))

            if (sourcePropertyType.toString == OntologyConstants.Owl.ObjectProperty) {
                // Yes. See if we need to change it to a datatype property. Does it have a knora-base:objectClassConstraint?
                val objectClassConstraintIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri
                val maybeObjectType: Option[SmartIri] = getPredicateIriObject(objectClassConstraintIri)

                maybeObjectType match {
                    case Some(objectTypeObj) =>
                        // Yes. Is there a corresponding type in the API v2 simple ontology?
                        OntologyConstants.KnoraApiV2Simple.ValueClassesToSimplifiedTypes.get(objectTypeObj.toString) match {
                            case Some(simplifiedType) =>
                                // Yes. Is it a datatype?
                                val isDatatype = simplifiedType.startsWith(OntologyConstants.Xsd.XsdPrefixExpansion) ||
                                    (KnoraApiV2Simple.Classes.get(simplifiedType.toSmartIri) match {
                                        case Some(simpleClass: ReadClassInfoV2) if simpleClass.entityInfoContent.rdfType.toString == OntologyConstants.Rdfs.Datatype => true
                                        case _ => false
                                    })

                                if (isDatatype) {
                                    // Yes. Make this a datatype property.
                                    (predicates - rdfTypeIri) +
                                        (rdfTypeIri -> PredicateInfoV2(
                                            predicateIri = rdfTypeIri,
                                            ontologyIri = rdfTypeIri.getOntologyFromEntity,
                                            objects = Set(OntologyConstants.Owl.DatatypeProperty)
                                        ))
                                } else {
                                    predicates
                                }

                            case None => predicates
                        }
                    case None => predicates
                }
            } else {
                predicates
            }
        } else {
            predicates
        }

        // Make a copy of this PredicateInfoContentV2 with the adjusted rdf:type, so we can call convertPredicates() on it.
        val copyWithAdjustedPredicates = copy(
            propertyIri = propertyIri.toOntologySchema(targetSchema),
            ontologyIri = ontologyIri.toOntologySchema(targetSchema),
            predicates = predicatesWithAdjustedRdfType,
            subPropertyOf = subPropertyOf.map(_.toOntologySchema(targetSchema)),
            ontologySchema = targetSchema
        )

        // Call its convertPredicates() method to convert the rest of the predicates.
        copyWithAdjustedPredicates.copy(
            predicates = copyWithAdjustedPredicates.convertPredicates(
                predsWithKnoraDefinitionIriObjs = PredicatesWithIriObjects.map(iri => iri.toSmartIri),
                targetSchema = targetSchema
            )
        )
    }
}

/**
  * Constants used by [[PropertyInfoContentV2]].
  */
object PropertyInfoContentV2 {
    /**
      * A set of property predicates that are used in API v2 requests and responses and whose objects are known to be
      * Knora definition IRIs.
      */
    private val PredicatesWithIriObjects = Set(
        OntologyConstants.KnoraApiV2Simple.SubjectType,
        OntologyConstants.KnoraApiV2Simple.ObjectType,
        OntologyConstants.KnoraApiV2WithValueObjects.SubjectType,
        OntologyConstants.KnoraApiV2WithValueObjects.ObjectType,
        OntologyConstants.KnoraBase.SubjectClassConstraint,
        OntologyConstants.KnoraBase.ObjectClassConstraint
    )

    def fromJsonLDObject(propertyDef: JsonLDObject): PropertyInfoContentV2 = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        val propertyIri: SmartIri = propertyDef.requireString("@id", stringFormatter.toSmartIriWithErr)
        val ontologySchema: OntologySchema = propertyIri.getOntologySchema.getOrElse(throw BadRequestException(s"Invalid property IRI: $propertyIri"))
        val ontologyIri: SmartIri = propertyIri.getOntologyFromEntity
        val propertyType: SmartIri = propertyDef.requireString("@type", stringFormatter.toSmartIriWithErr)

        val rdfType: (SmartIri, PredicateInfoV2) = OntologyConstants.Rdf.Type.toSmartIri -> PredicateInfoV2(
            predicateIri = OntologyConstants.Rdf.Type.toSmartIri,
            ontologyIri = OntologyConstants.Rdf.RdfOntologyIri.toSmartIri,
            objects = Set(propertyType.toString)
        )

        val subPropertyOf: Set[SmartIri] = propertyDef.maybeArray(OntologyConstants.Rdfs.SubPropertyOf) match {
            case Some(valueArray: JsonLDArray) =>
                valueArray.value.map {
                    case JsonLDString(superProperty) => superProperty.toSmartIriWithErr(throw BadRequestException(s"Invalid property IRI: $superProperty"))
                    case other => throw BadRequestException(s"Expected a property IRI: $other")
                }.toSet

            case None => Set.empty[SmartIri]
        }

        val otherProps = propertyDef.value - "@id" - "@type" - OntologyConstants.Rdfs.SubPropertyOf

        val predicates: Map[SmartIri, PredicateInfoV2] = otherProps.map {
            case (predicateIriStr: IRI, predicateValue: JsonLDValue) =>
                val predicateIri = predicateIriStr.toSmartIri

                val predicateInfo: PredicateInfoV2 = predicateValue match {
                    case JsonLDString(objStr) =>
                        PredicateInfoV2(
                            predicateIri = predicateIri,
                            ontologyIri = predicateIri.getOntologyFromEntity,
                            objects = Set(objStr.toString)
                        )

                    case objArray: JsonLDArray =>
                        if (objArray.value.forall(_.isInstanceOf[JsonLDString])) {
                            PredicateInfoV2(
                                predicateIri = predicateIri,
                                ontologyIri = predicateIri.getOntologyFromEntity,
                                objects = objArray.value.map {
                                    case JsonLDString(objStr) => objStr
                                    case other => throw AssertionException(s"Invalid object for property predicate $predicateIriStr: $other")
                                }.toSet
                            )
                        } else if (objArray.value.forall(_.isInstanceOf[JsonLDObject])) {
                            PredicateInfoV2(
                                predicateIri = predicateIri,
                                ontologyIri = predicateIri.getOntologyFromEntity,
                                objectsWithLang = objArray.toObjsWithLang
                            )
                        } else {
                            throw BadRequestException(s"Invalid object for property predicate $predicateIriStr: $predicateValue")
                        }

                    case other => throw BadRequestException(s"Invalid object for property predicate $predicateIriStr: $other")
                }

                predicateIri -> predicateInfo
        }

        PropertyInfoContentV2(
            propertyIri = propertyIri,
            ontologyIri = ontologyIri,
            predicates = predicates + rdfType,
            subPropertyOf = subPropertyOf,
            ontologySchema = ontologySchema
        )
    }
}

/**
  * Represents an RDF property definition as returned in an API response.
  *
  * @param entityInfoContent                   a [[PropertyInfoContentV2]] providing information about the property.
  * @param isEditable                          `true` if the property's value is editable via the Knora API.
  * @param isLinkProp                          `true` if the property is a subproperty of `knora-base:hasLinkTo`.
  * @param isLinkValueProp                     `true` if the property is a subproperty of `knora-base:hasLinkToValue`.
  * @param isFileValueProp                     `true` if the property is a subproperty of `knora-base:hasFileValue`.
  * @param isStandoffInternalReferenceProperty if `true`, this is a subproperty (directly or indirectly) of
  *                                            [[OntologyConstants.KnoraBase.StandoffTagHasInternalReference]].
  */
case class ReadPropertyInfoV2(entityInfoContent: PropertyInfoContentV2,
                              isEditable: Boolean = false,
                              isLinkProp: Boolean = false,
                              isLinkValueProp: Boolean = false,
                              isFileValueProp: Boolean = false,
                              isStandoffInternalReferenceProperty: Boolean = false) extends ReadEntityInfoV2 {
    def toOntologySchema(targetSchema: ApiV2Schema): ReadPropertyInfoV2 = copy(
        entityInfoContent = entityInfoContent.toOntologySchema(targetSchema)
    )

    def getNonLanguageSpecific(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue] = {
        if (entityInfoContent.ontologySchema != targetSchema) {
            throw DataConversionException(s"ReadPropertyInfoV2 for property ${entityInfoContent.propertyIri} is not in schema $targetSchema")
        }

        // Get the correct knora-api:subjectType and knora-api:objectType predicates for the target API schema.
        val (subjectTypePred: IRI, objectTypePred: IRI) = targetSchema match {
            case ApiV2Simple => (OntologyConstants.KnoraApiV2Simple.SubjectType, OntologyConstants.KnoraApiV2Simple.ObjectType)
            case ApiV2WithValueObjects => (OntologyConstants.KnoraApiV2WithValueObjects.SubjectType, OntologyConstants.KnoraApiV2WithValueObjects.ObjectType)
        }

        // Get the property's knora-api:subjectType and knora-api:objectType, if provided.
        val (maybeSubjectType: Option[SmartIri], maybeObjectType: Option[SmartIri]) = entityInfoContent.ontologySchema match {
            case InternalSchema => throw DataConversionException(s"ReadPropertyInfoV2 for property ${entityInfoContent.propertyIri} is not in schema $targetSchema")

            case ApiV2Simple =>
                (entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.SubjectType.toSmartIri),
                    entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2Simple.ObjectType.toSmartIri))

            case ApiV2WithValueObjects =>
                (entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2WithValueObjects.SubjectType.toSmartIri),
                    entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType.toSmartIri))
        }

        // Make the property's knora-api:subjectType and knora-api:objectType statements.
        val subjectTypeStatement: Option[(IRI, JsonLDString)] = maybeSubjectType.map(subjectTypeObj => (subjectTypePred, JsonLDString(subjectTypeObj.toString)))
        val objectTypeStatement: Option[(IRI, JsonLDString)] = maybeObjectType.map(objectTypeObj => (objectTypePred, JsonLDString(objectTypeObj.toString)))

        // Get the property's rdf:type.
        val propertyType: SmartIri = entityInfoContent.getPredicateIriObject(OntologyConstants.Rdf.Type.toSmartIri).getOrElse(throw InconsistentTriplestoreDataException(s"Property ${entityInfoContent.propertyIri} has no rdf:type"))

        val jsonSubPropertyOf: Seq[JsonLDString] = entityInfoContent.subPropertyOf.filter(_ != OntologyConstants.KnoraBase.ObjectCannotBeMarkedAsDeleted).toSeq.map {
            superProperty => JsonLDString(superProperty.toString)
        }

        val jsonSubPropertyOfStatement: Option[(IRI, JsonLDArray)] = if (jsonSubPropertyOf.nonEmpty) {
            Some(OntologyConstants.Rdfs.SubPropertyOf -> JsonLDArray(jsonSubPropertyOf))
        } else {
            None
        }

        val isEditableStatement: Option[(IRI, JsonLDBoolean)] = if (isEditable && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.IsEditable -> JsonLDBoolean(true))
        } else {
            None
        }

        val isLinkValuePropertyStatement: Option[(IRI, JsonLDBoolean)] = if (isLinkValueProp && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.IsLinkValueProperty -> JsonLDBoolean(true))
        } else {
            None
        }

        val isLinkPropertyStatement = if (isLinkProp && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.IsLinkProperty -> JsonLDBoolean(true))
        } else {
            None
        }

        Map(
            "@id" -> JsonLDString(entityInfoContent.propertyIri.toString),
            "@type" -> JsonLDString(propertyType.toString)
        ) ++ jsonSubPropertyOfStatement ++ subjectTypeStatement ++ objectTypeStatement ++ isEditableStatement ++ isLinkValuePropertyStatement ++ isLinkPropertyStatement
    }
}

/**
  * Represents an OWL class definition as returned in an API response.
  *
  * @param entityInfoContent      a [[ReadClassInfoV2]] providing information about the class.
  * @param canBeInstantiated      `true` if the class can be instantiated via the API.
  * @param inheritedCardinalities a [[Map]] of properties to [[Cardinality.Value]] objects representing the class's
  *                               inherited cardinalities on those properties.
  * @param linkProperties         a [[Set]] of IRIs of properties of the class that point to resources.
  * @param linkValueProperties    a [[Set]] of IRIs of properties of the class
  *                               that point to `LinkValue` objects.
  * @param fileValueProperties    a [[Set]] of IRIs of properties of the class
  *                               that point to `FileValue` objects.
  */
case class ReadClassInfoV2(entityInfoContent: ClassInfoContentV2,
                           canBeInstantiated: Boolean = false,
                           inheritedCardinalities: Map[SmartIri, Cardinality.Value] = Map.empty[SmartIri, Cardinality.Value],
                           linkProperties: Set[SmartIri] = Set.empty[SmartIri],
                           linkValueProperties: Set[SmartIri] = Set.empty[SmartIri],
                           fileValueProperties: Set[SmartIri] = Set.empty[SmartIri]) extends ReadEntityInfoV2 {
    /**
      * All the class's cardinalities, both direct and indirect.
      */
    lazy val allCardinalities: Map[SmartIri, Cardinality.Value] = inheritedCardinalities ++ entityInfoContent.directCardinalities

    def toOntologySchema(targetSchema: ApiV2Schema): ReadClassInfoV2 = {
        // If we're converting to the simplified API v2 schema, remove references to link value properties.

        val filteredInheritedCardinalities = if (targetSchema == ApiV2Simple) {
            inheritedCardinalities.filterNot {
                case (propertyIri, _) => linkValueProperties.contains(propertyIri)
            }
        } else {
            inheritedCardinalities
        }

        val filteredDirectCardinalities = if (targetSchema == ApiV2Simple) {
            entityInfoContent.directCardinalities.filterNot {
                case (propertyIri, _) => linkValueProperties.contains(propertyIri)
            }
        } else {
            entityInfoContent.directCardinalities
        }

        val filteredLinkValueProperties = if (targetSchema == ApiV2Simple) {
            Set.empty[SmartIri]
        } else {
            linkValueProperties
        }

        // Make a copy of the ClassInfoContentV2 without the filtered direct cardinalities, so we can then call
        // toOntologySchema() on it.
        val entityInfoContentWithFilteredCardinalities = entityInfoContent.copy(
            directCardinalities = filteredDirectCardinalities
        )

        copy(
            entityInfoContent = entityInfoContentWithFilteredCardinalities.toOntologySchema(targetSchema),
            inheritedCardinalities = filteredInheritedCardinalities.map {
                case (propertyIri, cardinality) => propertyIri.toOntologySchema(targetSchema) -> cardinality
            },
            linkProperties = filteredLinkValueProperties.map(_.toOntologySchema(targetSchema)),
            linkValueProperties = filteredLinkValueProperties.map(_.toOntologySchema(targetSchema)),
            fileValueProperties = fileValueProperties.map(_.toOntologySchema(targetSchema))
        )
    }

    def getNonLanguageSpecific(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue] = {
        if (entityInfoContent.ontologySchema != targetSchema) {
            throw DataConversionException(s"ReadClassInfoV2 for class ${entityInfoContent.classIri} is not in schema $targetSchema")
        }

        // If this is a project-specific class, add the standard cardinalities from knora-api:Resource for the target
        // schema.
        val completedCardinalities: Map[SmartIri, Cardinality.Value] = if (!entityInfoContent.classIri.isKnoraBuiltInDefinitionIri) {
            targetSchema match {
                case ApiV2Simple => allCardinalities ++ KnoraApiV2Simple.Resource.allCardinalities
                case ApiV2WithValueObjects => allCardinalities ++ KnoraApiV2WithValueObjects.Resource.allCardinalities
            }
        } else {
            allCardinalities
        }

        // Convert OWL cardinalities to JSON-LD.
        val owlCardinalities: Seq[JsonLDObject] = completedCardinalities.toArray.sortBy(_._1).map {
            case (propertyIri: SmartIri, cardinality: Cardinality.Value) =>

                val prop2card: (IRI, JsonLDInt) = cardinality match {
                    case Cardinality.MayHaveMany => OntologyConstants.Owl.MinCardinality -> JsonLDInt(0)
                    case Cardinality.MayHaveOne => OntologyConstants.Owl.MaxCardinality -> JsonLDInt(1)
                    case Cardinality.MustHaveOne => OntologyConstants.Owl.Cardinality -> JsonLDInt(1)
                    case Cardinality.MustHaveSome => OntologyConstants.Owl.MinCardinality -> JsonLDInt(1)
                }

                // If we're using the complex schema and the cardinality is inherited, add an annotation to say so.
                val isInherited = if (targetSchema == ApiV2WithValueObjects && !entityInfoContent.directCardinalities.contains(propertyIri)) {
                    Some(OntologyConstants.KnoraApiV2WithValueObjects.IsInherited -> JsonLDBoolean(true))
                } else {
                    None
                }

                JsonLDObject(Map(
                    "@type" -> JsonLDString(OntologyConstants.Owl.Restriction),
                    OntologyConstants.Owl.OnProperty -> JsonLDString(propertyIri.toString),
                    prop2card
                ) ++ isInherited)
        }

        val resourceIconPred = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.ResourceIcon
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.ResourceIcon
        }

        val resourceIconStatement: Option[(IRI, JsonLDString)] = entityInfoContent.getPredicateLiteralsWithoutLang(OntologyConstants.KnoraBase.ResourceIcon.toSmartIri).headOption.map {
            resIcon => resourceIconPred -> JsonLDString(resIcon)
        }

        val jsonRestriction: Option[JsonLDObject] = entityInfoContent.xsdStringRestrictionPattern.map {
            (pattern: String) =>
                JsonLDObject(Map(
                    "@type" -> JsonLDString(OntologyConstants.Rdfs.Datatype),
                    OntologyConstants.Owl.OnDatatype -> JsonLDString(OntologyConstants.Xsd.String),
                    OntologyConstants.Owl.WithRestrictions -> JsonLDArray(Seq(
                        JsonLDObject(Map(OntologyConstants.Xsd.Pattern -> JsonLDString(pattern))
                        ))
                    )))
        }

        val jsonSubClassOf = entityInfoContent.subClassOf.toArray.sorted.map {
            superClass => JsonLDString(superClass.toString)
        } ++ owlCardinalities ++ jsonRestriction

        val jsonSubClassOfStatement: Option[(IRI, JsonLDArray)] = if (jsonSubClassOf.nonEmpty) {
            Some(OntologyConstants.Rdfs.SubClassOf -> JsonLDArray(jsonSubClassOf))
        } else {
            None
        }

        val canBeInstantiatedStatement: Option[(IRI, JsonLDBoolean)] = if (canBeInstantiated && targetSchema == ApiV2WithValueObjects) {
            Some(OntologyConstants.KnoraApiV2WithValueObjects.CanBeInstantiated -> JsonLDBoolean(true))
        } else {
            None
        }

        Map(
            "@id" -> JsonLDString(entityInfoContent.classIri.toString),
            "@type" -> JsonLDString(entityInfoContent.rdfType.toString)
        ) ++ jsonSubClassOfStatement ++ resourceIconStatement ++ canBeInstantiatedStatement
    }
}

/**
  * Represents assertions about an OWL class.
  *
  * @param classIri                    the IRI of the class.
  * @param ontologyIri                 the IRI of the ontology in which the class is defined.
  * @param rdfType                     the rdf:type of the class.
  * @param predicates                  a [[Map]] of predicate IRIs to [[PredicateInfoV2]] objects.
  * @param directCardinalities         a [[Map]] of properties to [[Cardinality.Value]] objects representing the cardinalities
  *                                    that are directly defined on the class (as opposed to inherited) on those properties.
  * @param xsdStringRestrictionPattern if the class's rdf:type is rdfs:Datatype, an optional xsd:pattern specifying
  *                                    the regular expression that restricts its values. This has the effect of making the
  *                                    class a subclass of a blank node with owl:onDatatype xsd:string.
  * @param standoffDataType            if this is a standoff tag class, the standoff datatype tag class (if any) that it
  *                                    is a subclass of.
  * @param subClassOf                  the classes that this class is a subclass of.
  * @param ontologySchema              indicates whether this ontology entity belongs to an internal ontology (for use in the
  *                                    triplestore) or an external one (for use in the Knora API).
  */
case class ClassInfoContentV2(classIri: SmartIri,
                              ontologyIri: SmartIri,
                              rdfType: SmartIri,
                              predicates: Map[SmartIri, PredicateInfoV2] = Map.empty[SmartIri, PredicateInfoV2],
                              directCardinalities: Map[SmartIri, Cardinality.Value] = Map.empty[SmartIri, Cardinality.Value],
                              xsdStringRestrictionPattern: Option[String] = None,
                              standoffDataType: Option[StandoffDataTypeClasses.Value] = None,
                              subClassOf: Set[SmartIri] = Set.empty[SmartIri],
                              ontologySchema: OntologySchema) extends EntityInfoContentV2 with KnoraContentV2[ClassInfoContentV2] {
    override def toOntologySchema(targetSchema: OntologySchema): ClassInfoContentV2 = {
        copy(
            classIri = classIri.toOntologySchema(targetSchema),
            ontologyIri = ontologyIri.toOntologySchema(targetSchema),
            predicates = predicates,
            directCardinalities = directCardinalities.map {
                case (propertyIri, cardinality) => propertyIri.toOntologySchema(targetSchema) -> cardinality
            },
            subClassOf = subClassOf.map(_.toOntologySchema(targetSchema)),
            ontologySchema = targetSchema
        )
    }

}


object ClassInfoContentV2 {
    def fromJsonLDObject(jsonClassDef: JsonLDObject): ClassInfoContentV2 = ??? // TODO
}


/**
  * Represents the IRIs of entities defined in a particular ontology.
  *
  * @param ontologyIri          the IRI of the ontology.
  * @param classIris            the classes defined in the ontology.
  * @param propertyIris         the properties defined in the ontology.
  * @param standoffClassIris    the standoff classes defined in the ontology.
  * @param standoffPropertyIris the standoff properties defined in the ontology.
  */
case class OntologyEntitiesIriInfoV2(ontologyIri: SmartIri,
                                     classIris: Set[SmartIri],
                                     propertyIris: Set[SmartIri],
                                     standoffClassIris: Set[SmartIri],
                                     standoffPropertyIris: Set[SmartIri])

/**
  * Represents information about a subclass of a resource class.
  *
  * @param id    the IRI of the subclass.
  * @param label the `rdfs:label` of the subclass.
  */
case class SubClassInfoV2(id: SmartIri, label: String)

/**
  * Returns metadata about an ontology.
  *
  * @param ontologyIri          the IRI of the ontology.
  * @param label                the label of the ontology.
  * @param lastModificationDate the ontology's last modification date, if any.
  */
case class OntologyMetadataV2(ontologyIri: SmartIri,
                              label: String,
                              lastModificationDate: Option[Instant] = None) extends KnoraContentV2[OntologyMetadataV2] {
    override def toOntologySchema(targetSchema: OntologySchema): OntologyMetadataV2 = {
        copy(
            ontologyIri = ontologyIri.toOntologySchema(targetSchema)
        )
    }

    def toJsonLD(targetSchema: ApiV2Schema): Map[String, JsonLDValue] = {
        val maybeLastModDateStatement: Option[(IRI, JsonLDString)] = lastModificationDate.map {
            lastModDate =>
                val lastModDateProp = targetSchema match {
                    case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.LastModificationDate
                    case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.LastModificationDate
                }

                lastModDateProp -> JsonLDString(lastModDate.toString)
        }


        Map("@id" -> JsonLDString(ontologyIri.toString),
            "@type" -> JsonLDString(OntologyConstants.Owl.Ontology),
            OntologyConstants.Rdfs.Label -> JsonLDString(label)
        ) ++ maybeLastModDateStatement
    }
}