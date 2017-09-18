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


import com.github.jsonldjava.core.{JsonLdOptions, JsonLdProcessor}
import com.github.jsonldjava.utils.JsonUtils
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.util.{InputValidation, JavaUtil, JsonLDUtil}
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants, SettingsImpl}

import scala.collection.mutable

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
  * starts, before it accepts any API requests. A successful response will be a [[LoadOntologiesResponseV2]].
  *
  * @param userProfile the profile of the user making the request.
  */
case class LoadOntologiesRequestV2(userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Indicates that all ontologies were loaded.
  */
case class LoadOntologiesResponseV2() extends KnoraResponseV2 {
    def toJsonLDWithValueObject(settings: SettingsImpl) = """{"result": "Ontologies loaded."}"""

    def toXML = ???
}

/**
  * Requests all available information about a list of ontology entities (resource classes and/or properties). A successful response will be an
  * [[EntityInfoGetResponseV2]].
  *
  * @param resourceClassIris the IRIs of the resource entities to be queried.
  * @param propertyIris      the IRIs of the property entities to be queried.
  * @param userProfile       the profile of the user making the request.
  */
case class EntityInfoGetRequestV2(resourceClassIris: Set[IRI] = Set.empty[IRI], propertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Represents assertions about one or more ontology entities (resource classes and/or properties).
  *
  * @param resourceEntityInfoMap a [[Map]] of resource entity IRIs to [[ResourceEntityInfoV2]] objects.
  * @param propertyEntityInfoMap a [[Map]] of property entity IRIs to [[PropertyEntityInfoV2]] objects.
  */
case class EntityInfoGetResponseV2(resourceEntityInfoMap: Map[IRI, ResourceEntityInfoV2],
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
  * @param resourceClassIri the Iri of the given resource class.
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
  * @param namedGraph  the Iri of the named graph.
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
  * @param allLanguages true if information in all available languages should be returned.
  * @param userProfile    the profile of the user making the request.
  */
case class NamedGraphEntitiesGetRequestV2(namedGraphIris: Set[IRI], allLanguages: Boolean, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests the entity definitions for the given resource class Iris. A successful response will be a [[ReadEntityDefinitionsV2]].
  *
  * @param resourceClassIris the IRIs of the resource classes to be queried.
  * @param allLanguages true if information in all available languages should be returned.
  * @param userProfile       the profile of the user making the request.
  */
case class ResourceClassesGetRequestV2(resourceClassIris: Set[IRI],allLanguages: Boolean,  userProfile: UserProfileV1) extends OntologiesResponderRequestV2

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
  * @param ontologies      named graphs and their resource classes.
  * @param resourceClasses information about resource classes.
  * @param properties      information about properties.
  * @param userLang        the preferred language in which the information should be returned, or [[None]] if information
  *                        should be returned in all available languages.
  */
case class ReadEntityDefinitionsV2(ontologies: Map[IRI, Set[IRI]] = Map.empty[IRI, Set[IRI]],
                                   resourceClasses: Map[IRI, ResourceEntityInfoV2] = Map.empty[IRI, ResourceEntityInfoV2],
                                   properties: Map[IRI, PropertyEntityInfoV2] = Map.empty[IRI, PropertyEntityInfoV2], userLang: Option[String]) extends KnoraResponseV2 {


    def toJsonLDWithValueObject(settings: SettingsImpl): String = {
        val context = mutable.Map.empty[String, String]
        context.put(OntologyConstants.KnoraApi.KnoraApiOntologyLabel, OntologyConstants.KnoraApiV2WithValueObject.KnoraApiV2PrefixExpansion)
        context.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        context.put("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        context.put("owl", "http://www.w3.org/2002/07/owl#")

        val json: mutable.Map[String, Any] = mutable.Map.empty[String, Any]

        // ontologies with their resource classes

        val ontos: Map[IRI, Seq[IRI]] = ontologies.map {
            case (namedGraphIri: IRI, resourceClassIris: Set[IRI]) =>
                val resClassIris = resourceClassIris.toSeq.map {
                    resClass =>
                        InputValidation.internalEntityIriToApiV2WithValueObjectEntityIri(resClass, () => throw InconsistentTriplestoreDataException(s"internal resource class Iri $resClass could not be converted to knora-api v2 with value object entity Iri"))
                }

                InputValidation.internalOntologyIriToApiV2WithValueObjectOntologyIri(namedGraphIri, () => throw InconsistentTriplestoreDataException(s"internal ontology Iri could not be converted to knora-api v2 with value object ontology Iri")) -> resClassIris

        }

        json.put(OntologyConstants.KnoraApiV2WithValueObject.HasOntologiesWithResourceClasses, ontos)

        // resource classes

        val resClasses: Map[IRI, Map[IRI, Any]] = resourceClasses.map {
            case (resClassIri: IRI, resourceEntity: ResourceEntityInfoV2) =>
                val apiResClassIri = InputValidation.internalEntityIriToApiV2WithValueObjectEntityIri(
                    resClassIri,
                    () => throw InconsistentTriplestoreDataException(s"internal resource class Iri $resClassIri could not be converted to a knora-api v2 with value object resource class Iri")
                )

                val resourceEntityJson = userLang match {
                    case Some(lang) => resourceEntity.toJsonLDWithSingleLanguage(userLang = lang, settings = settings)
                    case None => resourceEntity.toJsonLDWithAllLanguages
                }

                apiResClassIri -> resourceEntityJson
        }

        json.put(OntologyConstants.KnoraApiV2WithValueObject.HasResourceClasses, resClasses)

        // properties

        val props: Map[IRI, Map[IRI, Any]] = properties.map {
            case (propIri: IRI, propEntity: PropertyEntityInfoV2) =>
                val apiPropIri = InputValidation.internalEntityIriToApiV2WithValueObjectEntityIri(
                    propIri,
                    () => throw InconsistentTriplestoreDataException(s"internal property $propIri could not be converted to knora-api v2 with value object property Iri")
                )

                val propJson = userLang match {
                    case Some(lang) => propEntity.toJsonLDWithSingleLanguage(userLang = lang, settings = settings)
                    case None => propEntity.toJsonLDWithAllLanguages
                }

                apiPropIri -> propJson
        }

        json.put(OntologyConstants.KnoraApiV2WithValueObject.HasProperties, props)

        val contextAsJava = JavaUtil.deepScalaToJava(context).asInstanceOf[java.util.Map[String, Any]]
        val jsonAsJava = JavaUtil.deepScalaToJava(json).asInstanceOf[java.util.Map[String, Any]]
        val compacted = JsonLdProcessor.compact(jsonAsJava, contextAsJava, new JsonLdOptions())
        JsonUtils.toPrettyString(compacted)
    }

    def toXML = ???

}

case class ReadNamedGraphsV2(namedGraphs: Set[IRI]) extends KnoraResponseV2 {

    def toJsonLDWithValueObject(settings: SettingsImpl): String = {

        val context = mutable.Map.empty[String, String]
        context.put(OntologyConstants.KnoraApi.KnoraApiOntologyLabel, OntologyConstants.KnoraApiV2WithValueObject.KnoraApiV2PrefixExpansion)

        val json: mutable.Map[String, Object] = mutable.Map.empty[String, Object]

        val namedGraphIris: Seq[IRI] = namedGraphs.toSeq.map {
            namedGraphIri =>
                // translate an internal ontology Iri to knora-api v2 with value object ontology Iri
                InputValidation.internalOntologyIriToApiV2WithValueObjectOntologyIri(namedGraphIri, () => throw InconsistentTriplestoreDataException(s"internal ontology Iri $namedGraphIri could not be converted to knora-api v2 with value object ontology Iri"))
        }

        json.put(OntologyConstants.KnoraApiV2WithValueObject.HasOntologies, namedGraphIris)

        val contextAsJava = JavaUtil.deepScalaToJava(context).asInstanceOf[java.util.Map[String, Any]]
        val jsonAsJava = JavaUtil.deepScalaToJava(json).asInstanceOf[java.util.Map[String, Any]]
        val compacted = JsonLdProcessor.compact(jsonAsJava, contextAsJava, new JsonLdOptions())
        JsonUtils.toPrettyString(compacted)

    }

    def toXML = ???
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
case class PredicateInfoV2(predicateIri: IRI, ontologyIri: IRI, objects: Set[String], objectsWithLang: Map[String, String])

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
      * Gets a predicate and its object from an entity, using the first object that doesn't have a language tag.
      *
      * @param predicateIri the IRI of the predicate.
      * @return the requested predicate and object.
      */
    def getPredicateAndObjectWithoutLang(predicateIri: IRI): Option[(IRI, String)] = {
        getPredicateObject(
            predicateIri = predicateIri,
            preferredLangs = None
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
                                                // The object is not available without a language tag. Return it in
                                                // any other language.
                                                predicateInfo.objectsWithLang.values.headOption
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
  * It is extended by [[ResourceEntityInfoV2]] and [[PropertyEntityInfoV2]].
  */
sealed trait EntityInfoWithLabelAndCommentV2 extends EntityInfoV2 {

    protected def getNonLanguageSpecific: Map[IRI, Any]

    def toJsonLDWithSingleLanguage(userLang: String, settings: SettingsImpl): Map[IRI, Any] = {
        val label: Option[(IRI, String)] = getPredicateAndObjectWithLang(OntologyConstants.Rdfs.Label, settings, userLang)
        val comment: Option[(IRI, String)] = getPredicateAndObjectWithLang(OntologyConstants.Rdfs.Comment, settings, userLang)
        getNonLanguageSpecific ++ label ++ comment
    }

    def toJsonLDWithAllLanguages: Map[IRI, Any] = {
        val labelObjs: Map[String, String] = getPredicateObjectsWithLangs(OntologyConstants.Rdfs.Label)

        val labels: Option[(IRI, Seq[Map[String, String]])] = if (labelObjs.nonEmpty) {
            Some(OntologyConstants.Rdfs.Label -> JsonLDUtil.objectsWithLangsToJsonLDArray(labelObjs))
        } else {
            None
        }

        val commentObjs: Map[String, String] = getPredicateObjectsWithLangs(OntologyConstants.Rdfs.Comment)

        val comments: Option[(IRI, Seq[Map[String, String]])] = if (commentObjs.nonEmpty) {
            Some(OntologyConstants.Rdfs.Comment -> JsonLDUtil.objectsWithLangsToJsonLDArray(commentObjs))
        } else {
            None
        }

        getNonLanguageSpecific ++ labels ++ comments
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
  */
case class PropertyEntityInfoV2(propertyIri: IRI,
                                ontologyIri: IRI,
                                isLinkProp: Boolean,
                                isLinkValueProp: Boolean,
                                isFileValueProp: Boolean,
                                predicates: Map[IRI, PredicateInfoV2]) extends EntityInfoWithLabelAndCommentV2 {
    def getNonLanguageSpecific: Map[IRI, Any] = {
        val objectClassConstraint: Option[(IRI, IRI)] = getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint) match {
            case Some(objectClassConstrObj) =>
                val checkedPred = InputValidation.internalEntityIriToApiV2WithValueObjectEntityIri(
                    objectClassConstrObj,
                    () => throw InconsistentTriplestoreDataException(s"Internal property ${OntologyConstants.KnoraBase.ObjectClassConstraint} could not be converted to knora-api v2 with value object property IRI")
                )

                val checkedObj = InputValidation.internalEntityIriToApiV2WithValueObjectEntityIri(
                    objectClassConstrObj,
                    () => throw InconsistentTriplestoreDataException(s"Internal class IRI $objectClassConstrObj could not be converted to knora-api v2 with value object class IRI")
                )

                Some((checkedPred, checkedObj))

            case None => None
        }

        Map(
            "@id" -> InputValidation.internalEntityIriToApiV2WithValueObjectEntityIri(
                propertyIri,
                () => throw InconsistentTriplestoreDataException(s"Internal property $propertyIri could not be converted to knora-api v2 with value object property IRI")
            ),
            OntologyConstants.KnoraApiV2WithValueObject.BelongsToOntology -> InputValidation.internalOntologyIriToApiV2WithValueObjectOntologyIri(
                ontologyIri,
                () => throw InconsistentTriplestoreDataException(s"Internal ontology $ontologyIri could not be converted to knora-api v2 with value object ontology IRI")
            )
        ) ++ getPredicateAndObjectWithoutLang(OntologyConstants.Rdf.Type) ++ objectClassConstraint
    }

}

/**
  * Represents the assertions about a given resource class.
  *
  * @param resourceClassIri    the IRI of the resource class.
  * @param ontologyIri         the IRI of the ontology in which the resource class.
  * @param predicates          a [[Map]] of predicate IRIs to [[PredicateInfoV2]] objects.
  * @param cardinalities       a [[Map]] of properties to [[Cardinality.Value]] objects representing the resource class's
  *                            cardinalities on those properties.
  * @param linkProperties      a [[Set]] of IRIs of properties of the resource class that point to other resources.
  * @param linkValueProperties a [[Set]] of IRIs of properties of the resource class
  *                            that point to `LinkValue` objects.
  * @param fileValueProperties a [[Set]] of IRIs of properties of the resource class
  *                            that point to `FileValue` objects.
  */
case class ResourceEntityInfoV2(resourceClassIri: IRI,
                                ontologyIri: IRI,
                                predicates: Map[IRI, PredicateInfoV2],
                                cardinalities: Map[IRI, Cardinality.Value],
                                linkProperties: Set[IRI],
                                linkValueProperties: Set[IRI],
                                fileValueProperties: Set[IRI]) extends EntityInfoWithLabelAndCommentV2 {

    def getNonLanguageSpecific: Map[IRI, Any] = {
        val owlCardinalities = cardinalities.map {
            case (propertyIri: IRI, cardinality: Cardinality.Value) =>

                val prop2card: (IRI, Int) = cardinality match {
                    case Cardinality.MayHaveMany =>
                        OntologyConstants.Owl.MinCardinality -> 0

                    case Cardinality.MayHaveOne =>
                        OntologyConstants.Owl.MaxCardinality -> 1

                    case Cardinality.MustHaveOne =>
                        OntologyConstants.Owl.Cardinality -> 1

                    case Cardinality.MustHaveSome =>
                        OntologyConstants.Owl.MinCardinality -> 1
                }

                Map(
                    "@type" -> OntologyConstants.Owl.Restriction,
                    OntologyConstants.Owl.OnProperty -> InputValidation.internalEntityIriToApiV2WithValueObjectEntityIri(propertyIri, () => throw InconsistentTriplestoreDataException(s"internal property Iri $propertyIri could not be converted to knora-api v2 with value object property Iri")),
                    prop2card
                )
        }.toSeq

        val resourceIcon: Option[(IRI, String)] = getPredicateAndObjectWithoutLang(OntologyConstants.KnoraBase.ResourceIcon) match {
            case Some((hasResIcon, resIcon)) =>
                Some(InputValidation.internalEntityIriToApiV2WithValueObjectEntityIri(
                    hasResIcon,
                    () => throw InconsistentTriplestoreDataException(s"Internal property $hasResIcon could not be converted to knora-api v2 with value object property IRI")
                ) -> resIcon)
            case None => None
        }

        Map(
            "@id" -> InputValidation.internalEntityIriToApiV2WithValueObjectEntityIri(resourceClassIri, () => throw InconsistentTriplestoreDataException(s"Internal property $resourceClassIri could not be converted to knora-api v2 with value object property IRI")),
            OntologyConstants.KnoraApiV2WithValueObject.BelongsToOntology -> InputValidation.internalOntologyIriToApiV2WithValueObjectOntologyIri(ontologyIri, () => throw InconsistentTriplestoreDataException(s"internal ontology Iri $ontologyIri could not be converted to knora-api v2 with value object ontology Iri")),
            "@type" -> OntologyConstants.Owl.Class, // TODO: does this need to be coming from the triplestore?
            OntologyConstants.Rdfs.SubClassOf -> owlCardinalities
        ) ++ resourceIcon
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
  * @param namedGraphIri   the Iri of the named graph.
  * @param resourceClasses the resource classes defined in the named graph.
  * @param propertyIris    the properties defined in the named graph.
  */
case class NamedGraphEntityInfoV2(namedGraphIri: IRI,
                                  resourceClasses: Set[IRI],
                                  propertyIris: Set[IRI])

/**
  * Represents information about a subclass of a resource class.
  *
  * @param id    the IRI of the subclass.
  * @param label the `rdfs:label` of the subclass.
  */
case class SubClassInfoV2(id: IRI, label: String)
