/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.v1.responder.ontologymessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{SmartIri, StringFormatter}
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing a message that can be sent to `OntologyResponderV1`.
  */
sealed trait OntologyResponderRequestV1 extends KnoraRequestV1

/**
  * Requests that all ontologies in the repository are loaded. This message must be sent only once, when the application
  * starts, before it accepts any API requests. A successful response will be a [[LoadOntologiesResponse]].
  *
  * @param userADM the profile of the user making the request.
  */
case class LoadOntologiesRequest(userADM: UserADM) extends OntologyResponderRequestV1

/**
  * Indicates that all ontologies were loaded.
  */
case class LoadOntologiesResponse() extends KnoraResponseV1 {
    def toJsValue = JsObject(Map("result" -> JsString("Ontologies loaded.")))
}

/**
  * Requests all available information about a list of ontology entities (resource classes and/or properties). A successful response will be an
  * [[EntityInfoGetResponseV1]].
  *
  * @param resourceClassIris the IRIs of the resource classes to be queried.
  * @param propertyIris      the IRIs of the properties to be queried.
  * @param userProfile       the profile of the user making the request.
  */
case class EntityInfoGetRequestV1(resourceClassIris: Set[IRI] = Set.empty[IRI], propertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserADM) extends OntologyResponderRequestV1


/**
  * Represents assertions about one or more ontology entities (resource classes and/or properties).
  *
  * @param resourceClassInfoMap a [[Map]] of resource class IRIs to [[ClassInfoV1]] objects.
  * @param propertyInfoMap      a [[Map]] of property IRIs to [[PropertyInfoV1]] objects.
  */
case class EntityInfoGetResponseV1(resourceClassInfoMap: Map[IRI, ClassInfoV1],
                                   propertyInfoMap: Map[IRI, PropertyInfoV1])


/**
  * Requests all available information about a list of ontology entities (standoff classes and/or properties). A successful response will be an
  * [[StandoffEntityInfoGetResponseV1]].
  *
  * @param standoffClassIris    the IRIs of the resource entities to be queried.
  * @param standoffPropertyIris the IRIs of the property entities to be queried.
  * @param userProfile          the profile of the user making the request.
  */
case class StandoffEntityInfoGetRequestV1(standoffClassIris: Set[IRI] = Set.empty[IRI], standoffPropertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserADM) extends OntologyResponderRequestV1


/**
  * Represents assertions about one or more ontology entities (resource classes and/or properties).
  *
  * @param standoffClassInfoMap    a [[Map]] of resource class IRIs to [[ClassInfoV1]] objects.
  * @param standoffPropertyInfoMap a [[Map]] of property IRIs to [[PropertyInfoV1]] objects.
  */
case class StandoffEntityInfoGetResponseV1(standoffClassInfoMap: Map[IRI, ClassInfoV1],
                                           standoffPropertyInfoMap: Map[IRI, PropertyInfoV1])

/**
  * Requests information about all standoff classes that are a subclass of a data type standoff class. A successful response will be an
  * [[StandoffClassesWithDataTypeGetResponseV1]].
  *
  * @param userProfile the profile of the user making the request.
  */
case class StandoffClassesWithDataTypeGetRequestV1(userProfile: UserADM) extends OntologyResponderRequestV1


/**
  * Represents assertions about all standoff classes that are a subclass of a data type standoff class.
  *
  * @param standoffClassInfoMap a [[Map]] of resource class IRIs to [[ClassInfoV1]] objects.
  */
case class StandoffClassesWithDataTypeGetResponseV1(standoffClassInfoMap: Map[IRI, ClassInfoV1])

/**
  * Requests information about all standoff properties. A successful response will be an
  * [[StandoffAllPropertiesGetResponseV1]].
  *
  * @param userProfile the profile of the user making the request.
  */
case class StandoffAllPropertiesGetRequestV1(userProfile: UserADM) extends OntologyResponderRequestV1


/**
  * Represents assertions about all standoff all standoff property entities.
  *
  * @param standoffAllPropertiesInfoMap a [[Map]] of resource entity IRIs to [[PropertyInfoV1]] objects.
  */
case class StandoffAllPropertiesGetResponseV1(standoffAllPropertiesInfoMap: Map[IRI, PropertyInfoV1])


/**
  * Requests information about a resource type and its possible properties. A successful response will be a
  * [[ResourceTypeResponseV1]].
  *
  * @param resourceTypeIri the IRI of the resource type to be queried.
  * @param userProfile     the profile of the user making the request.
  */
case class ResourceTypeGetRequestV1(resourceTypeIri: IRI, userProfile: UserADM) extends OntologyResponderRequestV1

/**
  * Represents the Knora API v1 JSON response to a request for information about a resource type.
  *
  * @param restype_info basic information about the resource type.
  */
case class ResourceTypeResponseV1(restype_info: ResTypeInfoV1) extends KnoraResponseV1 {
    def toJsValue = ResourceTypeV1JsonProtocol.resourceTypeResponseV1Format.write(this)
}

/**
  * Checks whether a Knora resource or value class is a subclass of (or identical to) another class. This message is used
  * internally by Knora, and is not part of Knora API v1. A successful response will be a [[CheckSubClassResponseV1]].
  *
  * @param subClassIri   the IRI of the subclass.
  * @param superClassIri the IRI of the superclass.
  */
case class CheckSubClassRequestV1(subClassIri: IRI, superClassIri: IRI, userProfile: UserADM) extends OntologyResponderRequestV1

/**
  * Represents a response to a [[CheckSubClassRequestV1]].
  *
  * @param isSubClass `true` if the requested inheritance relationship exists.
  */
case class CheckSubClassResponseV1(isSubClass: Boolean)

/**
  * Requests information about named graphs containing ontologies. This corresponds to the concept of vocabularies in
  * the SALSAH prototype.
  *
  * @param projectIris the IRIs of the projects for which named graphs should be returned. If this set is empty, information
  *                    about all ontology named graphs is returned.
  * @param userADM     the profile of the user making the request.
  */
case class NamedGraphsGetRequestV1(projectIris: Set[IRI] = Set.empty[IRI], userADM: UserADM) extends OntologyResponderRequestV1

/**
  * Represents the Knora API V1 response to a [[NamedGraphsGetRequestV1]].
  *
  * @param vocabularies information about named graphs containing ontologies.
  */
case class NamedGraphsResponseV1(vocabularies: Seq[NamedGraphV1]) extends KnoraResponseV1 {
    def toJsValue = ResourceTypeV1JsonProtocol.namedGraphsResponseV1Format.write(this)
}

/**
  * Requests all resource classes that are defined in the given named graph.
  *
  * @param namedGraph the named graph for which the resource classes shall be returned.
  * @param userADM    the profile of the user making the request.
  */
case class ResourceTypesForNamedGraphGetRequestV1(namedGraph: Option[IRI], userADM: UserADM) extends OntologyResponderRequestV1

/**
  * Represents the Knora API V1 response to a [[ResourceTypesForNamedGraphGetRequestV1]].
  * It contains all the resource classes for a named graph.
  *
  * @param resourcetypes the resource classes for the queried named graph.
  */
case class ResourceTypesForNamedGraphResponseV1(resourcetypes: Seq[ResourceTypeV1]) extends KnoraResponseV1 {
    def toJsValue = ResourceTypeV1JsonProtocol.resourceTypesForNamedGraphResponseV1Format.write(this)
}

/**
  * Requests all property types that are defined in the given named graph.
  * If the named graph is not set, the property types of all named graphs are requested.
  *
  * @param namedGraph the named graph to query for or None if all the named graphs should be queried.
  * @param userADM    the profile of the user making the request.
  */
case class PropertyTypesForNamedGraphGetRequestV1(namedGraph: Option[IRI], userADM: UserADM) extends OntologyResponderRequestV1

/**
  * Represents the Knora API V1 response to a [[PropertyTypesForNamedGraphGetRequestV1]].
  * It contains all property types for the requested named graph.
  *
  * @param properties the property types for the requested named graph.
  */
case class PropertyTypesForNamedGraphResponseV1(properties: Seq[PropertyDefinitionInNamedGraphV1]) extends KnoraResponseV1 {
    def toJsValue = ResourceTypeV1JsonProtocol.propertyTypesForNamedGraphResponseV1Format.write(this)
}

/**
  * Gets all property types that are defined for the given resource class.
  *
  * @param resourceClassIri the IRI of the resource class to query for.
  * @param userProfile      the profile of the user making the request.
  */
case class PropertyTypesForResourceTypeGetRequestV1(resourceClassIri: IRI, userProfile: UserADM) extends OntologyResponderRequestV1

/**
  * Represents the Knora API V1 response to a [[PropertyTypesForResourceTypeGetRequestV1]].
  * It contains all the property types for the requested resource class.
  *
  * @param properties the property types for the requested resource class.
  */
case class PropertyTypesForResourceTypeResponseV1(properties: Vector[PropertyDefinitionV1]) extends KnoraResponseV1 {
    def toJsValue = ResourceTypeV1JsonProtocol.propertyTypesForResourceTypeResponseV1Format.write(this)
}

/**
  * Requests information about the subclasses of a Knora resource class. A successful response will be
  * a [[SubClassesGetResponseV1]].
  *
  * @param resourceClassIri the IRI of the Knora resource class.
  * @param userADM          the profile of the user making the request.
  */
case class SubClassesGetRequestV1(resourceClassIri: IRI, userADM: UserADM) extends OntologyResponderRequestV1

/**
  * Provides information about the subclasses of a Knora resource class.
  *
  * @param subClasses a list of [[SubClassInfoV1]] representing the subclasses of the specified class.
  */
case class SubClassesGetResponseV1(subClasses: Seq[SubClassInfoV1]) extends KnoraResponseV1 {
    def toJsValue = ResourceTypeV1JsonProtocol.subClassesGetResponseV1Format.write(this)
}

/**
  * Requests information about the ontology entities in the specified named graph. A successful response will be a
  * [[NamedGraphEntityInfoV1]].
  *
  * @param namedGraphIri the IRI of the named graph.
  * @param userProfile   the profile of the user making the request.
  */
case class NamedGraphEntityInfoRequestV1(namedGraphIri: IRI, userProfile: UserADM) extends OntologyResponderRequestV1


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a predicate that is asserted about a given ontology entity, and the objects of that predicate.
  */
class PredicateInfoV1(predicateInfoV2: PredicateInfoV2) {

    /**
      * Returns the IRI of the predicate.
      */
    def predicateIri: IRI = predicateInfoV2.predicateIri.toString

    /**
      * Returns the objects of the predicate that have no language codes.
      */
    def objects: Set[String] = predicateInfoV2.objects.filter {
        case StringLiteralV2(_, Some(_)) => false
        case _ => true
    }.map(_.toString).toSet

    /**
      * Returns the objects of the predicate that have language codes: a Map of language codes to literals.
      */
    def objectsWithLang: Map[String, String] = predicateInfoV2.objects.collect {
        case StringLiteralV2(str, Some(lang)) => lang -> str
    }.toMap
}

/**
  * Represents information about an OWL class or property.
  */
sealed trait EntityInfoV1 {
    protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    protected def entityInfoContent: EntityInfoContentV2

    /**
      * Returns a [[Map]] of predicate IRIs to [[PredicateInfoV1]] objects.
      */
    lazy val predicates: Map[IRI, PredicateInfoV1] = {
        entityInfoContent.predicates.map {
            case (smartIri, predicateInfoV2) => smartIri.toString -> new PredicateInfoV1(predicateInfoV2)
        }
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
        entityInfoContent.getPredicateStringLiteralObject(
            predicateIri = predicateIri.toSmartIri,
            preferredLangs = preferredLangs
        ) match {
            case Some(obj) => Some(obj)
            case None => predicates.get(predicateIri).flatMap(_.objects.headOption)
        }
    }

    /**
      * Returns all the string (non-IRI) objects specified without language tags for a given predicate.
      *
      * @param predicateIri the IRI of the predicate.
      * @return the predicate's objects, or an empty set if this entity doesn't have the specified predicate.
      */
    def getPredicateStringObjectsWithoutLang(predicateIri: IRI): Set[String] = {
        entityInfoContent.getPredicateStringLiteralObjectsWithoutLang(predicateIri.toSmartIri).toSet
    }
}

/**
  * Represents the assertions about an OWL class.
  */
class ClassInfoV1(classInfoV2: ReadClassInfoV2) extends EntityInfoV1 {
    override protected def entityInfoContent: EntityInfoContentV2 = classInfoV2.entityInfoContent

    /**
      * Returns the IRI of the resource class.
      */
    def resourceClassIri: IRI = classInfoV2.entityInfoContent.classIri.toString

    def allCardinalities: Map[IRI, KnoraCardinalityInfo] = {
        classInfoV2.allCardinalities.map {
            case (smartIri, cardinality) => smartIri.toString -> cardinality
        }
    }

    /**
      * Returns a [[Map]] of properties to [[Cardinality.Value]] objects representing the resource class's
      * cardinalities on those properties.
      */
    def knoraResourceCardinalities: Map[IRI, KnoraCardinalityInfo] = {
        classInfoV2.allResourcePropertyCardinalities.map {
            case (smartIri, cardinality) => smartIri.toString -> cardinality
        }
    }

    /**
      * Returns a [[Set]] of IRIs of properties of the resource class that point to other resources.
      */
    def linkProperties: Set[IRI] = classInfoV2.linkProperties.map(_.toString)

    /**
      * Returns a [[Set]] of IRIs of properties of the resource class that point to `LinkValue` objects.
      */
    def linkValueProperties: Set[IRI] = classInfoV2.linkValueProperties.map(_.toString)

    /**
      * Returns a [[Set]] of IRIs of properties of the resource class that point to `FileValue` objects.
      */
    def fileValueProperties: Set[IRI] = classInfoV2.fileValueProperties.map(_.toString)

    /**
      * If this is a standoff tag class, returns the standoff datatype tag class (if any) that it
      * is a subclass of.
      */
    def standoffDataType: Option[StandoffDataTypeClasses.Value] = classInfoV2.standoffDataType
}

/**
  * Represents the assertions about an OWL property.
  */
class PropertyInfoV1(propertyInfoV2: ReadPropertyInfoV2) extends EntityInfoV1 {

    override protected def entityInfoContent: EntityInfoContentV2 = propertyInfoV2.entityInfoContent

    /**
      * Returns the IRI of the queried property.
      */
    def propertyIri: IRI = propertyInfoV2.entityInfoContent.propertyIri.toString

    /**
      * Returns the IRI of the ontology in which the property is defined.
      */
    def ontologyIri: IRI = propertyInfoV2.entityInfoContent.propertyIri.getOntologyFromEntity.toString

    /**
      * Returns `true` if the property is a subproperty of `knora-base:hasLinkTo`.
      */
    def isLinkProp: Boolean = propertyInfoV2.isLinkProp

    /**
      * Returns `true` if the property is a subproperty of `knora-base:hasLinkToValue`.
      */
    def isLinkValueProp: Boolean = propertyInfoV2.isLinkValueProp

    /**
      * Returns `true` if the property is a subproperty of `knora-base:hasFileValue`.
      */
    def isFileValueProp: Boolean = propertyInfoV2.isFileValueProp

    /**
      * Returns `true` if this is a subproperty (directly or indirectly) of
      * [[OntologyConstants.KnoraBase.StandoffTagHasInternalReference]].
      */
    def isStandoffInternalReferenceProperty: Boolean = propertyInfoV2.isStandoffInternalReferenceProperty
}

/**
  * Methods to convert v2 ontology classes to v1.
  */
object ConvertOntologyClassV2ToV1 {

    /**
      * Wraps OWL class information from `OntologyResponderV2` for use in API v1.
      */
    def classInfoMapV2ToV1(classInfoMap: Map[SmartIri, ReadClassInfoV2]): Map[IRI, ClassInfoV1] = {
        classInfoMap.map {
            case (smartIri, classInfoV2) => smartIri.toString -> new ClassInfoV1(classInfoV2)
        }
    }

    /**
      * Wraps OWL property information from `OntologyResponderV2` for use in API v1.
      */
    def propertyInfoMapV2ToV1(propertyInfoMap: Map[SmartIri, ReadPropertyInfoV2]): Map[IRI, PropertyInfoV1] = {
        propertyInfoMap.map {
            case (smartIri, propertyInfoV2) => smartIri.toString -> new PropertyInfoV1(propertyInfoV2)
        }
    }

}

/**
  * Represents the assertions about a given named graph entity.
  *
  * @param namedGraphIri   the IRI of the named graph.
  * @param resourceClasses the resource classes defined in the named graph.
  * @param propertyIris    the properties defined in the named graph.
  */
case class NamedGraphEntityInfoV1(namedGraphIri: IRI,
                                  resourceClasses: Set[IRI],
                                  propertyIris: Set[IRI])

/**
  * Represents information about a resource type.
  *
  * @param name        the IRI of the resource type.
  * @param label       the label of the resource type.
  * @param description a description of the resource type.
  * @param iconsrc     an icon representing the resource type.
  * @param properties  a list of definitions of properties that resources of this type can have.
  */
case class ResTypeInfoV1(name: IRI,
                         label: Option[String],
                         description: Option[String],
                         iconsrc: Option[String],
                         properties: Seq[PropertyDefinitionV1])

/**
  * Represents information about a property type. It is extended by [[PropertyDefinitionV1]]
  * and [[PropertyDefinitionInNamedGraphV1]].
  */
trait PropertyDefinitionBaseV1 {
    val id: IRI
    val name: IRI
    val label: Option[String]
    val description: Option[String]
    val vocabulary: IRI
    val valuetype_id: IRI
    val attributes: Option[String]
    val gui_name: Option[String]
}

/**
  * Describes a property type that resources of some particular type can have.
  *
  * @param id           the IRI of the property definition.
  * @param name         the IRI of the property definition.
  * @param label        the label of the property definition.
  * @param description  a description of the property definition.
  * @param vocabulary   the IRI of the vocabulary (i.e. the named graph) that the property definition belongs to.
  * @param occurrence   the cardinality of this property: 1, 1-n, 0-1, or 0-n.
  * @param valuetype_id the IRI of a subclass of `knora-base:Value`, representing the type of value that this property contains.
  * @param attributes   HTML attributes to be used with the property's GUI element.
  * @param gui_name     the IRI of a named individual of type `salsah-gui:Guielement`, representing the type of GUI element
  *                     that should be used for inputting values for this property.
  * @param guiorder     the property's order among the properties defined on some particular class.
  */
case class PropertyDefinitionV1(id: IRI,
                                name: IRI,
                                label: Option[String],
                                description: Option[String],
                                vocabulary: IRI,
                                occurrence: String,
                                valuetype_id: IRI,
                                attributes: Option[String],
                                gui_name: Option[String],
                                guiorder: Option[Int] = None) extends PropertyDefinitionBaseV1

/**
  * Describes a property type that a named graph contains.
  *
  * @param id           the IRI of the property definition.
  * @param name         the IRI of the property definition.
  * @param label        the label of the property definition.
  * @param description  a description of the property definition.
  * @param vocabulary   the IRI of the vocabulary (i.e. the named graph) that the property definition belongs to.
  * @param valuetype_id the IRI of a subclass of `knora-base:Value`, representing the type of value that this property contains.
  * @param attributes   HTML attributes to be used with the property's GUI element.
  * @param gui_name     the IRI of a named individual of type `salsah-gui:Guielement`, representing the type of GUI element
  *                     that should be used for inputting values for this property.
  */
case class PropertyDefinitionInNamedGraphV1(id: IRI,
                                            name: IRI,
                                            label: Option[String],
                                            description: Option[String],
                                            vocabulary: IRI,
                                            valuetype_id: IRI,
                                            attributes: Option[String],
                                            gui_name: Option[String]) extends PropertyDefinitionBaseV1


/**
  * Represents a named graph (corresponds to a vocabulary in the SALSAH prototype).
  *
  * @param id          the id of the named graph.
  * @param shortname   the short name of the named graph.
  * @param longname    the full name of the named graph.
  * @param description a description of the named graph.
  * @param project_id  the project belonging to the named graph.
  * @param uri         the IRI of the named graph.
  * @param active      indicates if this is named graph the user's project belongs to.
  */
case class NamedGraphV1(id: IRI,
                        shortname: String,
                        longname: String,
                        description: String,
                        project_id: IRI,
                        uri: IRI,
                        active: Boolean) {
    def toJsValue = ResourceTypeV1JsonProtocol.namedGraphV1Format.write(this)
}

/**
  * Represents information about a subclass of a resource class.
  *
  * @param id    the IRI of the subclass.
  * @param label the `rdfs:label` of the subclass.
  */
case class SubClassInfoV1(id: IRI, label: String)

/**
  * Represents a resource class and its properties.
  *
  * @param id         the IRI of the resource class.
  * @param label      the label of the resource class.
  * @param properties the properties of the resource class.
  */
case class ResourceTypeV1(id: IRI, label: String, properties: Vector[PropertyTypeV1]) {
    def toJsValue = ResourceTypeV1JsonProtocol.resourceTypeV1Format.write(this)
}

/**
  * Represents a property type.
  *
  * @param id    the IRI of the property type.
  * @param label the label of the property type.
  */
case class PropertyTypeV1(id: IRI, label: String) {
    def toJsValue = ResourceTypeV1JsonProtocol.propertyTypeV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about resources and their properties.
  */
object ResourceTypeV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit val propertyDefinitionV1Format: JsonFormat[PropertyDefinitionV1] = jsonFormat10(PropertyDefinitionV1)
    implicit val propertyDefinitionInNamedGraphV1Format: JsonFormat[PropertyDefinitionInNamedGraphV1] = jsonFormat8(PropertyDefinitionInNamedGraphV1)
    implicit val resTypeInfoV1Format: JsonFormat[ResTypeInfoV1] = jsonFormat5(ResTypeInfoV1)
    implicit val resourceTypeResponseV1Format: RootJsonFormat[ResourceTypeResponseV1] = jsonFormat1(ResourceTypeResponseV1)
    implicit val namedGraphV1Format: RootJsonFormat[NamedGraphV1] = jsonFormat7(NamedGraphV1)
    implicit val namedGraphsResponseV1Format: RootJsonFormat[NamedGraphsResponseV1] = jsonFormat1(NamedGraphsResponseV1)
    implicit val propertyTypeV1Format: RootJsonFormat[PropertyTypeV1] = jsonFormat2(PropertyTypeV1)
    implicit val resourceTypeV1Format: RootJsonFormat[ResourceTypeV1] = jsonFormat3(ResourceTypeV1)
    implicit val resourceTypesForNamedGraphResponseV1Format: RootJsonFormat[ResourceTypesForNamedGraphResponseV1] = jsonFormat1(ResourceTypesForNamedGraphResponseV1)
    implicit val propertyTypesForNamedGraphResponseV1Format: RootJsonFormat[PropertyTypesForNamedGraphResponseV1] = jsonFormat1(PropertyTypesForNamedGraphResponseV1)
    implicit val propertyTypesForResourceTypeResponseV1Format: RootJsonFormat[PropertyTypesForResourceTypeResponseV1] = jsonFormat1(PropertyTypesForResourceTypeResponseV1)
    implicit val subClassInfoV1Format: JsonFormat[SubClassInfoV1] = jsonFormat2(SubClassInfoV1)
    implicit val subClassesGetResponseV1Format: RootJsonFormat[SubClassesGetResponseV1] = jsonFormat1(SubClassesGetResponseV1)
}
