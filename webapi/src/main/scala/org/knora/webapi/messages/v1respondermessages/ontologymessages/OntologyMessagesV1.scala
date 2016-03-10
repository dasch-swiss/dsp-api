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

package org.knora.webapi.messages.v1respondermessages.ontologymessages

import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1respondermessages.{KnoraRequestV1, KnoraResponseV1}
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait representing a message that can be sent to `OntologyResponderV1`.
  */
sealed trait OntologyResponderRequestV1 extends KnoraRequestV1

/**
  * Requests all available information about a list of ontology entities (resource classes and/or properties). A successful response will be an
  * [[EntityInfoGetResponseV1]].
  *
  * @param resourceClassIris the IRIs of the resource entities to be queried.
  * @param propertyIris the IRIs of the property entities to be queried.
  * @param userProfile the profile of the user making the request.
  */
case class EntityInfoGetRequestV1(resourceClassIris: Set[IRI] = Set.empty[IRI], propertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1) extends OntologyResponderRequestV1


/**
  * Represents assertions about one or more ontology entities (resource classes and/or properties).
  * @param resourceEntityInfoMap a [[Map]] of resource entity IRIs to [[ResourceEntityInfoV1]] objects.
  * @param propertyEntityInfoMap a [[Map]] of property entity IRIs to [[PropertyEntityInfoV1]] objects.
  */
case class EntityInfoGetResponseV1(resourceEntityInfoMap: Map[IRI, ResourceEntityInfoV1],
                                   propertyEntityInfoMap: Map[IRI, PropertyEntityInfoV1])

/**
  * Requests information about a resource type and its possible properties. A successful response will be a
  * [[ResourceTypeResponseV1]].
  * @param resourceTypeIri the IRI of the resource type to be queried.
  * @param userProfile the profile of the user making the request.
  */
case class ResourceTypeGetRequestV1(resourceTypeIri: IRI, userProfile: UserProfileV1) extends OntologyResponderRequestV1

/**
  * Represents the Knora API v1 JSON response to a request for information about a resource type.
  * @param restype_info basic information about the resource type.
  */
case class ResourceTypeResponseV1(restype_info: ResTypeInfoV1,
                                  userdata: UserDataV1) extends KnoraResponseV1 {
    def toJsValue = ResourceTypeV1JsonProtocol.resourceTypeResponseV1Format.write(this)
}

/**
  * Checks whether an OWL class is a subclass of (or identical to) another OWL class. This message is used
  * internally by Knora, and is not part of Knora API v1. A successful response will be a [[CheckSubClassResponseV1]].
  *
  * @param subClassIri the IRI of the subclass.
  * @param superClassIri the IRI of the superclass.
  */
case class CheckSubClassRequestV1(subClassIri: IRI, superClassIri: IRI) extends OntologyResponderRequestV1

/**
  * Represents a response to a [[CheckSubClassRequestV1]].
  *
  * @param isSubClass `true` if the requested inheritance relationship exists.
  */
case class CheckSubClassResponseV1(isSubClass: Boolean)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a predicate that is asserted about a given ontology entity, and the objects of that predicate.
  * @param ontologyIri the IRI of the ontology in which the assertions occur.
  * @param objects the objects of the predicate.
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
      * @param propertyIri the IRI of the property that the OWL cardinality applies to.
      * @param owlCardinalityIri the IRI of the OWL cardinality, which must be a member of the set
      *                          [[OntologyConstants.Owl.cardinalityOWLRestrictions]]. Qualified and unqualified
      *                          cardinalities are treated as equivalent.
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
  * @param resourceIri the IRI of the queried entity.
  * @param predicates a [[Map]] of predicate IRIs to [[PredicateInfoV1]] objects.
  * @param cardinalities a [[Map]] of predicates representing cardinalities to [[Cardinality.Value]] objects.
  * @param linkProperties a [[Set]] of IRIs of properties of the resource that point to other resources.
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
  * @param propertyIri the Iri of the queried property entity.
  * @param isLinkProp `true` if the property is a subproperty of `knora-base:hasLinkTo`.
  * @param predicates a [[Map]] of predicate IRIs to [[PredicateInfoV1]] objects.
  */
case class PropertyEntityInfoV1(propertyIri: IRI,
                                isLinkProp: Boolean,
                                predicates: Map[IRI, PredicateInfoV1]) extends EntityInfoV1

/**
  * Represents information about a resource type.
  *
  * @param name the IRI of the resource type.
  * @param label the label of the resource type.
  * @param description a description of the resource type.
  * @param iconsrc an icon representing the resource type.
  * @param properties a list of definitions of properties that resources of this type can have.
  */
case class ResTypeInfoV1(name: IRI,
                         label: Option[String],
                         description: Option[String],
                         iconsrc: Option[String],
                         properties: Set[PropertyDefinitionV1])

/**
  * Describes a property that resources of some particular type can have.
  *
  * @param id the IRI of the property definition.
  * @param name the IRI of the property definition.
  * @param label the label of the property definition.
  * @param description a description of the property definition.
  * @param vocabulary the IRI of the vocabulary (i.e. the named graph) that the property definition belongs to.
  * @param occurrence the cardinality of this property: 1, 1-n, 0-1, or 0-n.
  * @param valuetype_id the IRI of a subclass of `knora-base:Value`, representing the type of value that this property contains.
  * @param attributes HTML attributes to be used with the property's GUI element.
  * @param gui_name the IRI of a named individual of type `salsah-gui:Guielement`, representing the type of GUI element
  *                 that should be used for inputting values for this property.
  */
case class PropertyDefinitionV1(id: IRI,
                                name: IRI,
                                label: Option[String],
                                description: Option[String],
                                vocabulary: IRI,
                                occurrence: String,
                                valuetype_id: IRI,
                                attributes: Option[String],
                                gui_name: Option[String])

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about resources and their properties.
  */
object ResourceTypeV1JsonProtocol extends DefaultJsonProtocol with NullOptions {

    import org.knora.webapi.messages.v1respondermessages.usermessages.UserV1JsonProtocol._

    implicit val propertyDefinitionV1Format: JsonFormat[PropertyDefinitionV1] = jsonFormat9(PropertyDefinitionV1)
    implicit val resTypeInfoV1Format: JsonFormat[ResTypeInfoV1] = jsonFormat5(ResTypeInfoV1)
    implicit val resourceTypeResponseV1Format: RootJsonFormat[ResourceTypeResponseV1] = jsonFormat2(ResourceTypeResponseV1)
}
