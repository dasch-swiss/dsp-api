/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
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
  */
case class LoadOntologiesRequest() extends OntologyResponderRequestV1

/**
  * Indicates that all ontologies were loaded.
  */
case class LoadOntologiesResponse()

/**
  * Requests all available information about a list of ontology entities (resource classes and/or properties). A successful response will be an
  * [[EntityInfoGetResponseV1]].
  *
  * @param resourceClassIris the IRIs of the resource entities to be queried.
  * @param propertyIris      the IRIs of the property entities to be queried.
  * @param userProfile       the profile of the user making the request.
  */
case class EntityInfoGetRequestV1(resourceClassIris: Set[IRI] = Set.empty[IRI], propertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1) extends OntologyResponderRequestV1


/**
  * Represents assertions about one or more ontology entities (resource classes and/or properties).
  *
  * @param resourceEntityInfoMap a [[Map]] of resource entity IRIs to [[ResourceEntityInfoV1]] objects.
  * @param propertyEntityInfoMap a [[Map]] of property entity IRIs to [[PropertyEntityInfoV1]] objects.
  */
case class EntityInfoGetResponseV1(resourceEntityInfoMap: Map[IRI, ResourceEntityInfoV1],
                                   propertyEntityInfoMap: Map[IRI, PropertyEntityInfoV1])

/**
  * Requests information about a resource type and its possible properties. A successful response will be a
  * [[ResourceTypeResponseV1]].
  *
  * @param resourceTypeIri the IRI of the resource type to be queried.
  * @param userProfile     the profile of the user making the request.
  */
case class ResourceTypeGetRequestV1(resourceTypeIri: IRI, userProfile: UserProfileV1) extends OntologyResponderRequestV1

/**
  * Represents the Knora API v1 JSON response to a request for information about a resource type.
  *
  * @param restype_info basic information about the resource type.
  * @param userdata     information about the user that made the request.
  */
case class ResourceTypeResponseV1(restype_info: ResTypeInfoV1,
                                  userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = ResourceTypeV1JsonProtocol.resourceTypeResponseV1Format.write(this)
}

/**
  * Checks whether an OWL class is a subclass of (or identical to) another OWL class. This message is used
  * internally by Knora, and is not part of Knora API v1. A successful response will be a [[CheckSubClassResponseV1]].
  *
  * @param subClassIri   the IRI of the subclass.
  * @param superClassIri the IRI of the superclass.
  */
case class CheckSubClassRequestV1(subClassIri: IRI, superClassIri: IRI) extends OntologyResponderRequestV1

/**
  * Represents a response to a [[CheckSubClassRequestV1]].
  *
  * @param isSubClass `true` if the requested inheritance relationship exists.
  */
case class CheckSubClassResponseV1(isSubClass: Boolean)

/**
  * Requests all existing named graphs.
  * This corresponds to the concept of vocabularies in the SALSAH prototype.
  *
  * @param userProfile the profile of the user making the request.
  *
  */
case class NamedGraphsGetRequestV1(userProfile: UserProfileV1) extends OntologyResponderRequestV1

/**
  * Represents the Knora API V1 response to a [[NamedGraphsGetRequestV1]].
  * It contains all the existing named graphs.
  *
  * @param vocabularies all the existing named graphs.
  * @param userdata     information about the user that made the request.
  */
case class NamedGraphsResponseV1(vocabularies: Vector[NamedGraphV1], userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = ResourceTypeV1JsonProtocol.namedGraphsResponseV1Format.write(this)
}

/**
  * Requests all resource classes that are defined in the given named graph.
  *
  * @param namedGraph  the named graph for which the resource classes shall be returned.
  * @param userProfile the profile of the user making the request.
  */
case class ResourceTypesForNamedGraphGetRequestV1(namedGraph: Option[IRI], userProfile: UserProfileV1) extends OntologyResponderRequestV1

/**
  * Represents the Knora API V1 response to a [[ResourceTypesForNamedGraphGetRequestV1]].
  * It contains all the resource classes for a named graph.
  *
  * @param resourcetypes the resource classes for the queried named graph.
  * @param userdata      information about the user that made the request.
  */
case class ResourceTypesForNamedGraphResponseV1(resourcetypes: Vector[ResourceTypeV1], userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = ResourceTypeV1JsonProtocol.resourceTypesForNamedGraphResponseV1Format.write(this)
}

/**
  * Requests all property types that are defined in the given named graph.
  * If the named graph is not set, the property types of all named graphs are requested.
  *
  * @param namedGraph  the named graph to query for or None if all the named graphs should be queried.
  * @param userProfile the profile of the user making the request.
  */
case class PropertyTypesForNamedGraphGetRequestV1(namedGraph: Option[IRI], userProfile: UserProfileV1) extends OntologyResponderRequestV1

/**
  * Represents the Knora API V1 response to a [[PropertyTypesForNamedGraphGetRequestV1]].
  * It contains all property types for the requested named graph.
  *
  * @param properties the property types for the requested named graph.
  * @param userdata   information about the user that made the request.
  */
case class PropertyTypesForNamedGraphResponseV1(properties: Vector[PropertyDefinitionInNamedGraphV1], userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = ResourceTypeV1JsonProtocol.propertyTypesForNamedGraphResponseV1Format.write(this)
}

/**
  * Gets all property types that are defined for the given resource class.
  *
  * @param resourceClassIri the Iri of the resource class to query for.
  * @param userProfile      the profile of the user making the request.
  */
case class PropertyTypesForResourceTypeGetRequestV1(resourceClassIri: IRI, userProfile: UserProfileV1) extends OntologyResponderRequestV1

/**
  * Represents the Knora API V1 response to a [[PropertyTypesForResourceTypeGetRequestV1]].
  * It contains all the property types for the requested resource class.
  *
  * @param properties the property types for the requested resource class.
  * @param userdata   information about the user that made the request.
  */
case class PropertyTypesForResourceTypeResponseV1(properties: Vector[PropertyDefinitionV1], userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = ResourceTypeV1JsonProtocol.propertyTypesForResourceTypeResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a predicate that is asserted about a given ontology entity, and the objects of that predicate.
  *
  * @param ontologyIri the IRI of the ontology in which the assertions occur.
  * @param objects     the objects of the predicate.
  */
case class PredicateInfoV1(predicateIri: IRI, ontologyIri: IRI, objects: Set[String])

object Cardinality extends Enumeration {
    type Cardinality = Value
    val MayHaveOne = Value(0, "0-1")
    val MayHaveMany = Value(1, "0-n")
    val MustHaveOne = Value(2, "1")
    val MustHaveSome = Value(3, "1-n")

    val valueMap: Map[String, Value] = values.map(v => (v.toString, v)).toMap

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
      * @param propertyIri         the IRI of the property that the OWL cardinality applies to.
      * @param owlCardinalityIri   the IRI of the OWL cardinality, which must be a member of the set
      *                            [[OntologyConstants.Owl.cardinalityOWLRestrictions]]. Qualified and unqualified
      *                            cardinalities are treated as equivalent.
      * @param owlCardinalityValue the integer value associated with the cardinality.
      * @return a [[Value]].
      */
    def owlCardinality2KnoraCardinality(propertyIri: IRI, owlCardinalityIri: IRI, owlCardinalityValue: Int): Value = {
        owlCardinalityIri match {
            case OntologyConstants.Owl.MinCardinality =>
                if (owlCardinalityValue == 0) {
                    Cardinality.MayHaveMany
                } else if (owlCardinalityValue == 1) {
                    Cardinality.MustHaveSome
                } else {
                    throw new InconsistentTriplestoreDataException(s"Invalid cardinality restriction $owlCardinalityIri $owlCardinalityValue for $propertyIri")
                }

            case OntologyConstants.Owl.Cardinality if owlCardinalityValue == 1 =>
                Cardinality.MustHaveOne

            case OntologyConstants.Owl.MaxCardinality if owlCardinalityValue == 1 =>
                Cardinality.MayHaveOne

            case _ =>
                // if none of the cases above match, the data is inconsistent
                throw new InconsistentTriplestoreDataException(s"Invalid cardinality restriction $owlCardinalityIri $owlCardinalityValue for $propertyIri")
        }
    }
}

/**
  * Represents information about either a resource or a property entity.
  * It is extended by [[ResourceEntityInfoV1]] and [[PropertyEntityInfoV1]].
  *
  */
sealed trait EntityInfoV1 {
    val predicates: Map[IRI, PredicateInfoV1]

    /**
      * Returns the first object specified for a given predicate.
      *
      * @param predicateIri the IRI of the predicate.
      * @return the predicate's first object, or [[None]] if this entity doesn't have the specified predicate, or
      *         if the predicate has no objects.
      */
    def getPredicateObject(predicateIri: IRI): Option[String] = {
        predicates.get(predicateIri) match {
            case Some(predicateInfo) =>
                predicateInfo.objects.headOption
            case None => None
        }
    }

    /**
      * Returns all the objects specified for a given predicate.
      *
      * @param predicateIri the IRI of the predicate.
      * @return the predicate's objects, or an empty set if this entity doesn't have the specified predicate.
      */
    def getPredicateObjects(predicateIri: IRI): Set[String] = {
        predicates.get(predicateIri) match {
            case Some(predicateInfo) =>
                predicateInfo.objects
            case None => Set.empty[String]
        }
    }

}

/**
  * Represents the assertions about a given resource entity.
  *
  * @param resourceIri         the IRI of the queried entity.
  * @param predicates          a [[Map]] of predicate IRIs to [[PredicateInfoV1]] objects.
  * @param cardinalities       a [[Map]] of predicates representing cardinalities to [[Cardinality.Value]] objects.
  * @param linkProperties      a [[Set]] of IRIs of properties of the resource that point to other resources.
  * @param linkValueProperties a [[Set]] of IRIs of properties of the resource
  *                            that point to `LinkValue` objects.
  * @param fileValueProperties a [[Set]] of IRIs of properties of the resource
  *                            that point to `FileValue` objects.
  */
case class ResourceEntityInfoV1(resourceIri: IRI,
                                predicates: Map[IRI, PredicateInfoV1],
                                cardinalities: Map[IRI, Cardinality.Value],
                                linkProperties: Set[IRI],
                                linkValueProperties: Set[IRI],
                                fileValueProperties: Set[IRI]) extends EntityInfoV1

/**
  * Represents the assertions about a given property entity.
  *
  * @param propertyIri the Iri of the queried property entity.
  * @param isLinkProp  `true` if the property is a subproperty of `knora-base:hasLinkTo`.
  * @param predicates  a [[Map]] of predicate IRIs to [[PredicateInfoV1]] objects.
  */
case class PropertyEntityInfoV1(propertyIri: IRI,
                                isLinkProp: Boolean,
                                predicates: Map[IRI, PredicateInfoV1]) extends EntityInfoV1

/**
  * Represents the assertions about a given named graph entity.
  *
  * @param namedGraphIri   the Iri of the named graph.
  * @param resourceClasses the resource classes defined in the named graph.
  * @param propertyIris    the properties defined in the named graph.
  */
case class NamedGraphEntityInfoV1(namedGraphIri: IRI,
                                  resourceClasses: Vector[IRI],
                                  propertyIris: Vector[IRI])

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
                         properties: Set[PropertyDefinitionV1])

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
  */
case class PropertyDefinitionV1(id: IRI,
                                name: IRI,
                                label: Option[String],
                                description: Option[String],
                                vocabulary: IRI,
                                occurrence: String,
                                valuetype_id: IRI,
                                attributes: Option[String],
                                gui_name: Option[String]) extends PropertyDefinitionBaseV1

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
  * @param uri         the Iri of the named graph.
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
object ResourceTypeV1JsonProtocol extends DefaultJsonProtocol with NullOptions {

    import org.knora.webapi.messages.v1.responder.usermessages.UserDataV1JsonProtocol._

    implicit val propertyDefinitionV1Format: JsonFormat[PropertyDefinitionV1] = jsonFormat9(PropertyDefinitionV1)
    implicit val propertyDefinitionInNamedGraphV1Format: JsonFormat[PropertyDefinitionInNamedGraphV1] = jsonFormat8(PropertyDefinitionInNamedGraphV1)
    implicit val resTypeInfoV1Format: JsonFormat[ResTypeInfoV1] = jsonFormat5(ResTypeInfoV1)
    implicit val resourceTypeResponseV1Format: RootJsonFormat[ResourceTypeResponseV1] = jsonFormat2(ResourceTypeResponseV1)
    implicit val namedGraphV1Format: RootJsonFormat[NamedGraphV1] = jsonFormat7(NamedGraphV1)
    implicit val namedGraphsResponseV1Format: RootJsonFormat[NamedGraphsResponseV1] = jsonFormat2(NamedGraphsResponseV1)
    implicit val propertyTypeV1Format: RootJsonFormat[PropertyTypeV1] = jsonFormat2(PropertyTypeV1)
    implicit val resourceTypeV1Format: RootJsonFormat[ResourceTypeV1] = jsonFormat3(ResourceTypeV1)
    implicit val resourceTypesForNamedGraphResponseV1Format: RootJsonFormat[ResourceTypesForNamedGraphResponseV1] = jsonFormat2(ResourceTypesForNamedGraphResponseV1)
    implicit val propertyTypesForNamedGraphResponseV1Format: RootJsonFormat[PropertyTypesForNamedGraphResponseV1] = jsonFormat2(PropertyTypesForNamedGraphResponseV1)
    implicit val propertyTypesForResourceTypeResponseV1Format: RootJsonFormat[PropertyTypesForResourceTypeResponseV1] = jsonFormat2(PropertyTypesForResourceTypeResponseV1)
}
