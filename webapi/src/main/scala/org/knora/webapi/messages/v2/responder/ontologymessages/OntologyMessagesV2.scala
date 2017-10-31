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


import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.jsonld._

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
  * Requests the creation of an empty ontology. A successful response will be a [[SuccessResponseV2]].
  *
  * @param ontologyIri the IRI of the ontology to be created. This must be an internal ontology IRI.
  * @param userProfile the profile of the user making the request.
  */
case class CreateOntologyRequestV2(ontologyIri: IRI, userProfile: UserProfileV1) extends OntologiesResponderRequestV2 {
    private val stringFormatter = StringFormatter.getInstance

    if (!stringFormatter.isProjectSpecificInternalOntologyIri(ontologyIri)) {
        throw AssertionException(s"Expected internal project-specific ontology IRI: $ontologyIri")
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
case class EntityInfoGetRequestV2(classIris: Set[IRI] = Set.empty[IRI], propertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Represents assertions about one or more ontology entities (resource classes and/or properties).
  *
  * @param classEntityInfoMap    a [[Map]] of class entity IRIs to [[ClassEntityInfoV2]] objects.
  * @param propertyEntityInfoMap a [[Map]] of property entity IRIs to [[PropertyEntityInfoV2]] objects.
  */
case class EntityInfoGetResponseV2(classEntityInfoMap: Map[IRI, ClassEntityInfoV2],
                                   propertyEntityInfoMap: Map[IRI, PropertyEntityInfoV2])

/**
  * Requests all available information about a list of ontology entities (standoff classes and/or properties). A successful response will be an
  * [[StandoffEntityInfoGetResponseV2]].
  *
  * @param standoffClassIris    the IRIs of the resource entities to be queried.
  * @param standoffPropertyIris the IRIs of the property entities to be queried.
  * @param userProfile          the profile of the user making the request.
  */
case class StandoffEntityInfoGetRequestV2(standoffClassIris: Set[IRI] = Set.empty[IRI], standoffPropertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Represents assertions about one or more ontology entities (resource classes and/or properties).
  *
  * @param standoffClassEntityInfoMap    a [[Map]] of resource entity IRIs to [[StandoffClassEntityInfoV2]] objects.
  * @param standoffPropertyEntityInfoMap a [[Map]] of property entity IRIs to [[StandoffPropertyEntityInfoV2]] objects.
  */
case class StandoffEntityInfoGetResponseV2(standoffClassEntityInfoMap: Map[IRI, StandoffClassEntityInfoV2],
                                           standoffPropertyEntityInfoMap: Map[IRI, StandoffPropertyEntityInfoV2])

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
  * @param standoffClassEntityInfoMap a [[Map]] of resource entity IRIs to [[StandoffClassEntityInfoV2]] objects.
  */
case class StandoffClassesWithDataTypeGetResponseV2(standoffClassEntityInfoMap: Map[IRI, StandoffClassEntityInfoV2])

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
  * @param standoffAllPropertiesEntityInfoMap a [[Map]] of resource entity IRIs to [[StandoffPropertyEntityInfoV2]] objects.
  */
case class StandoffAllPropertyEntitiesGetResponseV2(standoffAllPropertiesEntityInfoMap: Map[IRI, StandoffPropertyEntityInfoV2])

/**
  * Checks whether a Knora resource or value class is a subclass of (or identical to) another class.
  * A successful response will be a [[CheckSubClassResponseV2]].
  *
  * @param subClassIri   the IRI of the subclass.
  * @param superClassIri the IRI of the superclass.
  */
case class CheckSubClassRequestV2(subClassIri: IRI, superClassIri: IRI, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

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
case class SubClassesGetRequestV2(resourceClassIri: IRI, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Provides information about the subclasses of a Knora resource class.
  *
  * @param subClasses a list of [[SubClassInfoV2]] representing the subclasses of the specified class.
  */
case class SubClassesGetResponseV2(subClasses: Seq[SubClassInfoV2])

/**
  *
  * Request information about the entities of a named graph. A succesful response will be a [[NamedGraphEntityInfoV2]].
  *
  * @param namedGraph  the IRI of the named graph.
  * @param userProfile the profile of the user making the request.
  */
case class NamedGraphEntitiesRequestV2(namedGraph: IRI, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests the existing named graphs.
  *
  * @param userProfile the profile of the user making the request.
  */
case class NamedGraphsGetRequestV2(userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests entity definitions for the given named graphs.
  *
  * @param namedGraphIris the named graphs to query for.
  * @param responseSchema    the API schema that will be used for the response.
  * @param allLanguages   true if information in all available languages should be returned.
  * @param userProfile    the profile of the user making the request.
  */
case class NamedGraphEntitiesGetRequestV2(namedGraphIris: Set[IRI], responseSchema: ApiV2Schema, allLanguages: Boolean, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests the entity definitions for the given class IRIs. A successful response will be a [[ReadEntityDefinitionsV2]].
  *
  * @param resourceClassIris the IRIs of the classes to be queried.
  * @param responseSchema    the API schema that will be used for the response.
  * @param allLanguages      true if information in all available languages should be returned.
  * @param userProfile       the profile of the user making the request.
  */
case class ClassesGetRequestV2(resourceClassIris: Set[IRI], responseSchema: ApiV2Schema, allLanguages: Boolean, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests the entity definitions for the given property Iris. A successful response will be a [[ReadEntityDefinitionsV2]].
  *
  * @param propertyIris the IRIs of the properties to be queried.
  * @param allLanguages true if information in all available languages should be returned.
  * @param userProfile  the profile of the user making the request.
  */
case class PropertyEntitiesGetRequestV2(propertyIris: Set[IRI], allLanguages: Boolean, userProfile: UserProfileV1) extends OntologiesResponderRequestV2


/**
  * Returns information about ontology entities.
  *
  * @param ontologies named graphs and their classes.
  * @param classes    information about classes.
  * @param properties information about properties.
  * @param userLang   the preferred language in which the information should be returned, or [[None]] if information
  *                   should be returned in all available languages.
  */
case class ReadEntityDefinitionsV2(ontologies: Map[IRI, Set[IRI]] = Map.empty[IRI, Set[IRI]],
                                   classes: Map[IRI, ClassEntityInfoV2] = Map.empty[IRI, ClassEntityInfoV2],
                                   properties: Map[IRI, PropertyEntityInfoV2] = Map.empty[IRI, PropertyEntityInfoV2], userLang: Option[String]) extends KnoraResponseV2 {

    private val stringFormatter = StringFormatter.getInstance

    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        // Make JSON-LD prefixes for the project-specific ontologies used in the response.
        val ontologiesFromResourceClasses: Set[IRI] = classes.values.map {
            resourceClass => resourceClass.ontologyIri
        }.toSet

        val ontologiesFromProperties: Set[IRI] = properties.values.map {
            property => property.ontologyIri
        }.toSet

        val internalProjectSpecificOntologiesUsed: Set[IRI] = (ontologiesFromResourceClasses ++ ontologiesFromProperties) -
            OntologyConstants.KnoraBase.KnoraBaseOntologyIri -
            OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri -
            OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri

        val projectSpecificOntologyPrefixes: Map[String, JsonLDString] = internalProjectSpecificOntologiesUsed.map {
            internalOntologyIri =>
                val prefix = stringFormatter.getOntologyPrefixLabelFromInternalOntologyIri(internalOntologyIri, () => throw InconsistentTriplestoreDataException(s"Can't parse $internalOntologyIri as an internal ontology IRI"))
                val externalOntologyIri = stringFormatter.toExternalOntologyIri(ontologyIri = internalOntologyIri, targetSchema = targetSchema)
                prefix -> JsonLDString(externalOntologyIri + "#")
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
        ) ++ projectSpecificOntologyPrefixes)

        // ontologies with their classes

        val jsonOntologies: Map[IRI, JsonLDArray] = ontologies.map {
            case (namedGraphIri: IRI, classIris: Set[IRI]) =>
                val classIrisInOntology = classIris.toArray.sorted.map {
                    classIri =>
                        JsonLDString(stringFormatter.toExternalEntityIri(
                            entityIri = classIri,
                            targetSchema = targetSchema
                        ))
                }

                val convertedNamedGraphIri = stringFormatter.toExternalOntologyIri(
                    ontologyIri = namedGraphIri,
                    targetSchema = targetSchema
                )

                convertedNamedGraphIri -> JsonLDArray(classIrisInOntology)

        }

        // classes

        val jsonClasses: Map[IRI, JsonLDObject] = classes.map {
            case (classIri: IRI, resourceEntity: ClassEntityInfoV2) =>
                val externalClassIri = stringFormatter.toExternalEntityIri(
                    entityIri = classIri,
                    targetSchema = targetSchema
                )

                val jsonClass = userLang match {
                    case Some(lang) => resourceEntity.toJsonLDWithSingleLanguage(targetSchema = targetSchema, userLang = lang, settings = settings)
                    case None => resourceEntity.toJsonLDWithAllLanguages(targetSchema = targetSchema)
                }

                externalClassIri -> jsonClass
        }

        // properties

        // If we're using the simplified API, don't return link value properties.
        val filteredProperties = if (targetSchema == ApiV2Simple) {
            properties.filterNot {
                case (_, propertyInfo) => propertyInfo.isLinkValueProp
            }
        } else {
            properties
        }

        val jsonProperties: Map[IRI, JsonLDObject] = filteredProperties.map {
            case (propertyIri, propertyInfo) =>
                val externalPropertyIri = stringFormatter.toExternalEntityIri(
                    entityIri = propertyIri,
                    targetSchema = targetSchema
                )
                // If this is a built-in property, use its built-in definition, otherwise use the one we were given.
                val schemaPropertyInfo = targetSchema match {
                    case ApiV2Simple => KnoraApiV2Simple.Properties.getOrElse(externalPropertyIri, propertyInfo)
                    case ApiV2WithValueObjects => KnoraApiV2WithValueObjects.Properties.getOrElse(externalPropertyIri, propertyInfo)
                }

                val propJson: JsonLDObject = userLang match {
                    case Some(lang) => schemaPropertyInfo.toJsonLDWithSingleLanguage(targetSchema = targetSchema, userLang = lang, settings = settings)
                    case None => schemaPropertyInfo.toJsonLDWithAllLanguages(targetSchema = targetSchema)
                }

                externalPropertyIri -> propJson
        }

        val hasOntologiesWithClassesProp = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.HasOntologiesWithClasses
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.HasOntologiesWithClasses
        }

        val hasPropertiesProp = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.HasProperties
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.HasProperties
        }

        val hasClassesProp = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.HasClasses
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.HasClasses
        }

        val body = JsonLDObject(Map(
            hasOntologiesWithClassesProp -> JsonLDObject(jsonOntologies),
            hasPropertiesProp -> JsonLDObject(jsonProperties),
            hasClassesProp -> JsonLDObject(jsonClasses)
        ))

        JsonLDDocument(body = body, context = context)
    }

}

case class ReadNamedGraphsV2(namedGraphs: Set[IRI]) extends KnoraResponseV2 {

    private val stringFormatter = StringFormatter.getInstance

    def toJsonLDDocument(targetSchema: ApiV2Schema, settings: SettingsImpl): JsonLDDocument = {
        val knoraApiOntologyPrefixExpansion = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.KnoraApiV2PrefixExpansion
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion
        }

        val context = JsonLDObject(Map(
            OntologyConstants.KnoraApi.KnoraApiOntologyLabel -> JsonLDString(knoraApiOntologyPrefixExpansion)
        ))

        val namedGraphIris: Seq[JsonLDString] = namedGraphs.toSeq.map {
            namedGraphIri => JsonLDString(stringFormatter.toExternalOntologyIri(namedGraphIri, targetSchema))
        }

        val hasOntologiesProp = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.HasOntologies
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.HasOntologies
        }

        val body = JsonLDObject(Map(
            hasOntologiesProp -> JsonLDArray(namedGraphIris)
        ))

        JsonLDDocument(body = body, context = context)
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
case class PredicateInfoV2(predicateIri: IRI,
                           ontologyIri: IRI,
                           objects: Set[String] = Set.empty[String],
                           objectsWithLang: Map[String, String] = Map.empty[String, String])

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
            throw InconsistentTriplestoreDataException(s"Invalid OWL cardinality property $owlCardinalityIri")
        }

        if (!(owlCardinalityValue == 0 || owlCardinalityValue == 1)) {
            throw InconsistentTriplestoreDataException(s"Invalid OWL cardinality value $owlCardinalityValue")
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
  * Represents information about either a resource or a property entity.
  */
sealed trait EntityInfoV2 {
    val predicates: Map[IRI, PredicateInfoV2]

    /**
      * Gets a predicate and its object from an entity in a specific language.
      *
      * @param predicateIri the IRI of the predicate.
      * @param userLang     the language in which the object should to be returned.
      * @return the requested predicate and object.
      */
    def getPredicateAndObjectWithLang(predicateIri: IRI, settings: SettingsImpl, userLang: String): Option[(IRI, String)] = {
        getPredicateObject(
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
    def getPredicateObject(predicateIri: IRI, preferredLangs: Option[(String, String)] = None): Option[String] = {
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
                                                    case (lang, obj) => lang
                                                }.headOption.map {
                                                    case (lang, obj) => obj
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
      * Returns all the objects specified for a given predicate.
      *
      * @param predicateIri the IRI of the predicate.
      * @return the predicate's objects, or an empty set if this entity doesn't have the specified predicate.
      */
    def getPredicateObjectsWithoutLang(predicateIri: IRI): Set[String] = {
        predicates.get(predicateIri) match {
            case Some(predicateInfo) => predicateInfo.objects
            case None => Set.empty[String]
        }
    }

    def getPredicateObjectsWithLangs(predicateIri: IRI): Map[String, String] = {
        predicates.get(predicateIri) match {
            case Some(predicateInfo) => predicateInfo.objectsWithLang
            case None => Map.empty[String, String]
        }
    }
}

/**
  * Represents information about an ontology entity that has mostly non-language-specific predicates, plus
  * language specific `rdfs:label` and `rdfs:comment` predicates.
  *
  * It is extended by [[ClassEntityInfoV2]] and [[PropertyEntityInfoV2]].
  */
sealed trait EntityInfoWithLabelAndCommentV2 extends EntityInfoV2 {

    protected def getNonLanguageSpecific(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue]

    def toJsonLDWithSingleLanguage(targetSchema: ApiV2Schema, userLang: String, settings: SettingsImpl): JsonLDObject = {
        val label: Option[(IRI, JsonLDString)] = getPredicateAndObjectWithLang(OntologyConstants.Rdfs.Label, settings, userLang).map {
            case (k, v: String) => (k, JsonLDString(v))
        }

        val comment: Option[(IRI, JsonLDString)] = getPredicateAndObjectWithLang(OntologyConstants.Rdfs.Comment, settings, userLang).map {
            case (k, v: String) => (k, JsonLDString(v))
        }

        JsonLDObject(getNonLanguageSpecific(targetSchema) ++ label ++ comment)
    }

    def toJsonLDWithAllLanguages(targetSchema: ApiV2Schema): JsonLDObject = {
        val labelObjs: Map[String, String] = getPredicateObjectsWithLangs(OntologyConstants.Rdfs.Label)

        val labels: Option[(IRI, JsonLDArray)] = if (labelObjs.nonEmpty) {
            Some(OntologyConstants.Rdfs.Label -> JsonLDUtil.objectsWithLangsToJsonLDArray(labelObjs))
        } else {
            None
        }

        val commentObjs: Map[String, String] = getPredicateObjectsWithLangs(OntologyConstants.Rdfs.Comment)

        val comments: Option[(IRI, JsonLDArray)] = if (commentObjs.nonEmpty) {
            Some(OntologyConstants.Rdfs.Comment -> JsonLDUtil.objectsWithLangsToJsonLDArray(commentObjs))
        } else {
            None
        }

        JsonLDObject(getNonLanguageSpecific(targetSchema) ++ labels ++ comments)
    }
}

/**
  * Represents the assertions about a given property.
  *
  * @param propertyIri     the IRI of the queried property.
  * @param ontologyIri     the IRI of the ontology in which the property is defined.
  * @param isLinkProp      `true` if the property is a subproperty of `knora-base:hasLinkTo`.
  * @param isLinkValueProp `true` if the property is a subproperty of `knora-base:hasLinkToValue`.
  * @param isFileValueProp `true` if the property is a subproperty of `knora-base:hasFileValue`.
  * @param predicates      a [[Map]] of predicate IRIs to [[PredicateInfoV2]] objects.
  * @param ontologySchema  indicates whether this ontology entity belongs to an internal ontology (for use in the
  *                        triplestore) or an external one (for use in the Knora API).
  */
case class PropertyEntityInfoV2(propertyIri: IRI,
                                ontologyIri: IRI,
                                isEditable: Boolean = false,
                                isLinkProp: Boolean = false,
                                isLinkValueProp: Boolean = false,
                                isFileValueProp: Boolean = false,
                                predicates: Map[IRI, PredicateInfoV2] = Map.empty[IRI, PredicateInfoV2],
                                subPropertyOf: Set[IRI] = Set.empty[IRI],
                                ontologySchema: OntologySchema) extends EntityInfoWithLabelAndCommentV2 {
    private val stringFormatter = StringFormatter.getInstance

    def getNonLanguageSpecific(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue] = {
        // If this is an internal property IRI, convert it to an external one.
        val convertedPropertyIri = stringFormatter.toExternalEntityIri(
            entityIri = propertyIri,
            targetSchema = targetSchema
        )

        // Get the correct knora-api:subjectType and knora-api:objectType predicates for the target API schema.
        val (subjectTypePred: IRI, objectTypePred: IRI) = targetSchema match {
            case ApiV2Simple => (OntologyConstants.KnoraApiV2Simple.SubjectType, OntologyConstants.KnoraApiV2Simple.ObjectType)
            case ApiV2WithValueObjects => (OntologyConstants.KnoraApiV2WithValueObjects.SubjectType, OntologyConstants.KnoraApiV2WithValueObjects.ObjectType)
        }

        // If this is a built-in API ontology property, get its knora-api:subjectType and knora-api:objectType, if provided.
        val (maybeBuiltInSubjectType: Option[IRI], maybeBuiltInObjectType: Option[IRI]) = ontologySchema match {
            case InternalSchema => (None, None)

            case ApiV2Simple =>
                (getPredicateObjectsWithoutLang(OntologyConstants.KnoraApiV2Simple.SubjectType).headOption,
                    getPredicateObjectsWithoutLang(OntologyConstants.KnoraApiV2Simple.ObjectType).headOption)

            case ApiV2WithValueObjects =>
                (getPredicateObjectsWithoutLang(OntologyConstants.KnoraApiV2WithValueObjects.SubjectType).headOption,
                    getPredicateObjectsWithoutLang(OntologyConstants.KnoraApiV2WithValueObjects.ObjectType).headOption)
        }

        // If this is an internal ontology property, get its knora-base:subjectClassConstraint and knora-base:objectClassConstraint, if provided.
        val maybeInternalSubjectClassConstraint = getPredicateObjectsWithoutLang(OntologyConstants.KnoraBase.SubjectClassConstraint).headOption
        val maybeInternalObjectClassConstraint = getPredicateObjectsWithoutLang(OntologyConstants.KnoraBase.ObjectClassConstraint).headOption

        // Determine the type that we will return as the property's subject type.
        val maybeSubjectTypeObj: Option[IRI] = maybeBuiltInSubjectType match {
            case Some(_) =>
                // The property is from a built-in API ontology and declared a knora-api:subjectType, so use that.
                maybeBuiltInSubjectType

            case None =>
                // The property is from an internal ontology. If it declared a knora-base:subjectClassConstraint, convert
                // the specified type to an API type for the target schema.
                maybeInternalSubjectClassConstraint match {
                    case Some(internalSubjectClassConstraint) =>
                        Some(stringFormatter.toExternalEntityIri(
                            entityIri = internalSubjectClassConstraint,
                            targetSchema = targetSchema
                        ))

                    case None => None
                }
        }

        // Determine the type that we will return as the property's object type.
        val maybeObjectTypeObj: Option[IRI] = maybeBuiltInObjectType match {
            case Some(_) =>
                // The property is from a built-in API ontology and declared a knora-api:objectType, so use that.
                maybeBuiltInObjectType

            case None =>
                // The property is from an internal ontology. If it declared a knora-base:objectClassConstraint, convert
                // the specified type to an API type for the target schema.
                maybeInternalObjectClassConstraint match {
                    case Some(internalObjectClassConstraint) =>
                        Some(stringFormatter.toExternalEntityIri(
                            entityIri = internalObjectClassConstraint,
                            targetSchema = targetSchema
                        ))

                    case None => None
                }
        }

        // Make the property's knora-api:subjectType and knora-api:objectType statements.
        val subjectTypeStatement: Option[(IRI, JsonLDString)] = maybeSubjectTypeObj.map(subjectTypeObj => (subjectTypePred, JsonLDString(subjectTypeObj)))
        val objectTypeStatement: Option[(IRI, JsonLDString)] = maybeObjectTypeObj.map(objectTypeObj => (objectTypePred, JsonLDString(objectTypeObj)))

        // Get the property's rdf:type, which should be rdf:Property, owl:ObjectProperty, or owl:DatatypeProperty.
        val sourcePropertyType: IRI = getPredicateObjectsWithoutLang(OntologyConstants.Rdf.Type).headOption.getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no rdf:type"))

        // Determine the type that we will return as the property's JSON-LD @type.
        val convertedPropertyType: IRI = sourcePropertyType match {
            case OntologyConstants.Owl.DatatypeProperty | OntologyConstants.Rdf.Property | OntologyConstants.Owl.AnnotationProperty =>
                // The property doesn't claim to be an object property, so use whatever type it provides.
                sourcePropertyType

            case OntologyConstants.Owl.ObjectProperty =>
                // The property says it's an object property. Are we using the simplified API?
                targetSchema match {
                    case ApiV2Simple =>
                        // Yes. Are we going to return an object type for the property?
                        maybeObjectTypeObj match {
                            case Some(objectTypeObj) =>
                                // Yes. Are we going to use an datatype as the object type?
                                if (OntologyConstants.KnoraApiV2Simple.Datatypes.contains(objectTypeObj)) {
                                    // Yes. Say that this is a datatype property.
                                    OntologyConstants.Owl.DatatypeProperty
                                } else {
                                    // No. Say it's an object property.
                                    sourcePropertyType
                                }

                            case None =>
                                // We don't know the property's object type, so leave it as an object property.
                                sourcePropertyType
                        }

                    case _ =>
                        // We're not using the simplified API, so leave it as an object property.
                        sourcePropertyType
                }

            case other => throw InconsistentTriplestoreDataException(s"Unsupported rdf:type for property: $other")
        }

        val belongsToOntologyPred: IRI = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.BelongsToOntology
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.BelongsToOntology
        }

        val convertedOntologyIri: IRI = stringFormatter.toExternalOntologyIri(
            ontologyIri = ontologyIri,
            targetSchema = targetSchema
        )

        val jsonSubPropertyOf: Seq[JsonLDString] = subPropertyOf.toSeq.map {
            superProperty =>
                JsonLDString(
                    stringFormatter.toExternalEntityIri(
                        entityIri = superProperty,
                        targetSchema = targetSchema
                    )
                )
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

        Map(
            "@id" -> JsonLDString(convertedPropertyIri),
            "@type" -> JsonLDString(convertedPropertyType),
            belongsToOntologyPred -> JsonLDString(convertedOntologyIri)
        ) ++ jsonSubPropertyOfStatement ++ subjectTypeStatement ++ objectTypeStatement ++ isEditableStatement
    }
}

/**
  * Represents the assertions about a given OWL class.
  *
  * @param classIri                    the IRI of the class.
  * @param ontologyIri                 the IRI of the ontology in which the class is defined.
  * @param rdfType                     the rdf:type of the class (defaults to owl:Class).
  * @param predicates                  a [[Map]] of predicate IRIs to [[PredicateInfoV2]] objects.
  * @param cardinalities               a [[Map]] of properties to [[Cardinality.Value]] objects representing the class's
  *                                    cardinalities on those properties.
  * @param xsdStringRestrictionPattern if the class's rdf:type is rdfs:Datatype, an optional xsd:pattern specifying
  *                                    the regular expression that restricts its values. This has the effect of making the
  *                                    class a subclass of a blank node with owl:onDatatype xsd:string.
  * @param linkProperties              a [[Set]] of IRIs of properties of the class that point to resources.
  * @param linkValueProperties         a [[Set]] of IRIs of properties of the class
  *                                    that point to `LinkValue` objects.
  * @param fileValueProperties         a [[Set]] of IRIs of properties of the class
  *                                    that point to `FileValue` objects.
  * @param subClassOf                  the classes that this class is a subclass of.
  * @param ontologySchema              indicates whether this ontology entity belongs to an internal ontology (for use in the
  *                                    triplestore) or an external one (for use in the Knora API).
  */
case class ClassEntityInfoV2(classIri: IRI,
                             ontologyIri: IRI,
                             rdfType: IRI = OntologyConstants.Owl.Class,
                             canBeInstantiated: Boolean = false,
                             predicates: Map[IRI, PredicateInfoV2] = Map.empty[IRI, PredicateInfoV2],
                             cardinalities: Map[IRI, Cardinality.Value] = Map.empty[IRI, Cardinality.Value],
                             xsdStringRestrictionPattern: Option[String] = None,
                             linkProperties: Set[IRI] = Set.empty[IRI],
                             linkValueProperties: Set[IRI] = Set.empty[IRI],
                             fileValueProperties: Set[IRI] = Set.empty[IRI],
                             subClassOf: Set[IRI] = Set.empty[IRI],
                             ontologySchema: OntologySchema) extends EntityInfoWithLabelAndCommentV2 {

    private val stringFormatter = StringFormatter.getInstance

    def getNonLanguageSpecific(targetSchema: ApiV2Schema): Map[IRI, JsonLDValue] = {
        // Convert the property IRIs in the cardinalities according to the target schema.

        val cardinalitiesWithTargetSchemaIris = cardinalities.map {
            case (propertyIri: IRI, cardinality: Cardinality.Value) =>
                val schemaPropertyIri: IRI = stringFormatter.toExternalEntityIri(
                    entityIri = propertyIri,
                    targetSchema = targetSchema
                )

                (schemaPropertyIri, cardinality)
        }

        val linkValuePropertiesWithTargetSchemaIris = linkValueProperties.map {
             propertyIri => stringFormatter.toExternalEntityIri(
                 propertyIri,
                 targetSchema = targetSchema
             )
        }

        // If we're using the simplified API, don't return link value properties.
        val filteredCardinalities = targetSchema match {
            case ApiV2Simple => cardinalitiesWithTargetSchemaIris.filterNot {
                case (propertyIri, _) => linkValuePropertiesWithTargetSchemaIris.contains(propertyIri)
            }

            case ApiV2WithValueObjects => cardinalitiesWithTargetSchemaIris
        }

        // If this is a project-specific class, add the standard cardinalities from knora-api:Resource for the target
        // schema.
        val schemaSpecificCardinalities: Map[IRI, Cardinality.Value] = if (!stringFormatter.isBuiltInEntityIri(classIri)) {
            targetSchema match {
                case ApiV2Simple => filteredCardinalities ++ KnoraApiV2Simple.Resource.cardinalities
                case ApiV2WithValueObjects => filteredCardinalities ++ KnoraApiV2WithValueObjects.Resource.cardinalities
            }
        } else {
            filteredCardinalities
        }

        // Convert OWL cardinalities to JSON-LD.
        val owlCardinalities: Seq[JsonLDObject] = schemaSpecificCardinalities.toArray.sortBy(_._1).map {
            case (propertyIri: IRI, cardinality: Cardinality.Value) =>

                val prop2card: (IRI, JsonLDInt) = cardinality match {
                    case Cardinality.MayHaveMany => OntologyConstants.Owl.MinCardinality -> JsonLDInt(0)
                    case Cardinality.MayHaveOne => OntologyConstants.Owl.MaxCardinality -> JsonLDInt(1)
                    case Cardinality.MustHaveOne => OntologyConstants.Owl.Cardinality -> JsonLDInt(1)
                    case Cardinality.MustHaveSome => OntologyConstants.Owl.MinCardinality -> JsonLDInt(1)
                }

                JsonLDObject(Map(
                    "@type" -> JsonLDString(OntologyConstants.Owl.Restriction),
                    OntologyConstants.Owl.OnProperty -> JsonLDString(propertyIri),
                    prop2card
                ))
        }

        val convertedResourceClassIri = stringFormatter.toExternalEntityIri(
            entityIri = classIri,
            targetSchema = targetSchema
        )

        val belongsToOntologyPred = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.BelongsToOntology
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.BelongsToOntology
        }

        val convertedOntologyIri = stringFormatter.toExternalOntologyIri(
            ontologyIri = ontologyIri,
            targetSchema = targetSchema
        )

        val resourceIconPred = targetSchema match {
            case ApiV2Simple => OntologyConstants.KnoraApiV2Simple.ResourceIcon
            case ApiV2WithValueObjects => OntologyConstants.KnoraApiV2WithValueObjects.ResourceIcon
        }

        val resourceIconStatement: Option[(IRI, JsonLDString)] = getPredicateObjectsWithoutLang(OntologyConstants.KnoraBase.ResourceIcon).headOption.map {
            resIcon => resourceIconPred -> JsonLDString(resIcon)
        }

        val jsonRestriction: Option[JsonLDObject] = xsdStringRestrictionPattern.map {
            (pattern: String) =>
                JsonLDObject(Map(
                    "@type" -> JsonLDString(OntologyConstants.Rdfs.Datatype),
                    OntologyConstants.Owl.OnDatatype -> JsonLDString(OntologyConstants.Xsd.String),
                    OntologyConstants.Owl.WithRestrictions -> JsonLDArray(Seq(
                        JsonLDObject(Map(OntologyConstants.Xsd.Pattern -> JsonLDString(pattern))
                        ))
                    )))
        }

        val jsonSubClassOf = subClassOf.toArray.sorted.map {
            superClass =>
                JsonLDString(
                    stringFormatter.toExternalEntityIri(
                        entityIri = superClass,
                        targetSchema = targetSchema
                    )
                )
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
            "@id" -> JsonLDString(convertedResourceClassIri),
            belongsToOntologyPred -> JsonLDString(convertedOntologyIri),
            "@type" -> JsonLDString(rdfType)
        ) ++ jsonSubClassOfStatement ++ resourceIconStatement ++ canBeInstantiatedStatement
    }
}

/**
  * Represents the assertions about a given standoff class.
  *
  * @param standoffClassIri the IRI of the standoff class.
  * @param ontologyIri      the IRI of the ontology in which the standoff class is defined.
  * @param predicates       a [[Map]] of predicate IRIs to [[PredicateInfoV2]] objects.
  * @param cardinalities    a [[Map]] of property IRIs to [[Cardinality.Value]] objects.
  */
case class StandoffClassEntityInfoV2(standoffClassIri: IRI,
                                     ontologyIri: IRI,
                                     predicates: Map[IRI, PredicateInfoV2],
                                     cardinalities: Map[IRI, Cardinality.Value],
                                     dataType: Option[StandoffDataTypeClasses.Value] = None) extends EntityInfoV2

/**
  * Represents the assertions about a given standoff property.
  *
  * @param standoffPropertyIri the IRI of the queried standoff property.
  * @param ontologyIri         the IRI of the ontology in which the standoff property is defined.
  * @param predicates          a [[Map]] of predicate IRIs to [[PredicateInfoV2]] objects.
  * @param isSubPropertyOf     a [[Set]] of IRIs representing this standoff property's super properties.
  */
case class StandoffPropertyEntityInfoV2(standoffPropertyIri: IRI,
                                        ontologyIri: IRI,
                                        predicates: Map[IRI, PredicateInfoV2],
                                        isSubPropertyOf: Set[IRI]) extends EntityInfoV2

/**
  * Represents the assertions about a given named graph entity.
  *
  * @param namedGraphIri the IRI of the named graph.
  * @param classIris     the classes defined in the named graph.
  * @param propertyIris  the properties defined in the named graph.
  */
case class NamedGraphEntityInfoV2(namedGraphIri: IRI,
                                  classIris: Set[IRI],
                                  propertyIris: Set[IRI])

/**
  * Represents information about a subclass of a resource class.
  *
  * @param id    the IRI of the subclass.
  * @param label the `rdfs:label` of the subclass.
  */
case class SubClassInfoV2(id: IRI, label: String)
