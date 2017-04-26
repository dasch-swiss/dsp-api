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

import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v1.responder.ontologymessages._

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
  * Requests all available information about a list of ontology entities (resource classes and/or properties). A successful response will be an
  * [[EntityInfoGetResponseV1]].
  *
  * @param resourceClassIris the IRIs of the resource entities to be queried.
  * @param propertyIris      the IRIs of the property entities to be queried.
  * @param userProfile       the profile of the user making the request.
  */
case class EntityInfoGetRequestV2(resourceClassIris: Set[IRI] = Set.empty[IRI], propertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests information to determine whether a certain Knora resource or value class is a subclass of another class.
  * A succesful response will be a [[ResourceAndValueSubClassOfRelationsResponseV1]].
  *
  * @param classIri    the Iri of the given resource or value class.
  */
case class ResourceAndValueSubClassOfRelationsRequestV2(classIri: IRI, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests information to determine whether a certain Knora resource class is a superclass of another class.
  * A succesful response will be a [[ResourceSuperclassOfRelationsResponseV1]].
  *
  * @param resourceClassIri    the Iri of the given resource class.
  * @param userProfile       the profile of the user making the request.
  */
case class ResourceSuperClassOfRelationsRequestV2(resourceClassIri: IRI, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  *
  * Request information about the entities of a named graph. A succesful response will be a [[NamedGraphEntityInfoV1]].
  *
  * @param namedGraph the Iri of the named graph.
  * @param userProfile  the profile of the user making the request.
  */
case class NamedGraphEntitiesRequestV2(namedGraph: IRI, userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests all available information about a list of ontology entities (standoff classes and/or properties). A successful response will be an
  * [[StandoffEntityInfoGetResponseV1]].
  *
  * @param standoffClassIris    the IRIs of the resource entities to be queried.
  * @param standoffPropertyIris the IRIs of the property entities to be queried.
  * @param userProfile          the profile of the user making the request.
  */
case class StandoffEntityInfoGetRequestV2(standoffClassIris: Set[IRI] = Set.empty[IRI], standoffPropertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests information about all standoff classes that are a subclass of a data type standoff class. A successful response will be an
  * [[StandoffClassesWithDataTypeGetResponseV1]].
  *
  * @param userProfile the profile of the user making the request.
  */
case class StandoffClassesWithDataTypeGetRequestV2(userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests information about all standoff property entities. A successful response will be an
  * [[StandoffAllPropertyEntitiesGetResponseV1]].
  *
  * @param userProfile the profile of the user making the request.
  */
case class StandoffAllPropertyEntitiesGetRequestV2(userProfile: UserProfileV1) extends OntologiesResponderRequestV2

/**
  * Requests the entity definitions for the given resource class Iris.
  *
  * @param resourceClassIris the IRIs of the resource classes to be queried.
  * @param userProfile the profile of the user making the request.
  */
case class ResourceClassesGetRequestV2(resourceClassIris: Set[IRI], userProfile: UserProfileV1) extends OntologiesResponderRequestV2