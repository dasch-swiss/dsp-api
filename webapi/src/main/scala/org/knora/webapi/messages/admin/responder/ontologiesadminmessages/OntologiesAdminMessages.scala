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

package org.knora.webapi.messages.admin.responder.ontologiesadminmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.{KnoraAdminRequest, KnoraAdminResponse}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import spray.json.{DefaultJsonProtocol, RootJsonFormat, _}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

case class CreateOntologyAdminPayload(ontologyName: String, projectIri: IRI) extends OntologiesAdminJsonProtocol {
    def toJsValue: JsValue = createOntologyAdminPayloadFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait for messages that can be sent to `HierarchicalListsResponderV2`.
  */
sealed trait OntologiesAdminResponderRequest extends KnoraAdminRequest



/**
  * Requests a list of all ontologies or the ontologies inside a project. A successful response will be a [[OntologiesGetAdminResponse]]
  *
  * @param projectIri  the IRI of the project.
  * @param userProfile the profile of the user making the request.
  */
case class OntologiesGetAdminRequest(projectIri: Option[IRI] = None,
                                     userProfile: UserProfileV1) extends OntologiesAdminResponderRequest


/**
  * Requests an ontology (as JSON-LD). A successful response will be a [[OntologyGetAdminResponse]].
  *
  * @param iri the ontology IRI
  * @param userProfile the profile of the user making the request.
  */
case class OntologyGetAdminRequest(iri: IRI,
                                   userProfile: UserProfileV1) extends OntologiesAdminResponderRequest


/**
  * Requests the creation of an empty ontology. A successful response will be a [[OntologyCreateAdminResponse]].
  *
  * @param ontologyName the name of the ontology to be created.
  * @param projectIri   the IRI of the project that the ontology will belong to.
  * @param apiRequestID the ID of the API request.
  * @param userProfile  the profile of the user making the request.
  */
case class OntologyCreateAdminRequest(ontologyName: String,
                                      projectIri: IRI,
                                      apiRequestID: UUID,
                                      userProfile: UserProfileV1) extends OntologiesAdminResponderRequest


/**
  * Requests updating (overwriting) an existing ontology with the provided data in JSON-LD format.
  *
  * @param iri the IRI of the existing ontology.
  * @param data the data.
  * @param apiRequestID the ID of the API request.
  * @param userProfile the profile of the user making the request.
  */
case class OntologyUpdateAdminRequest(iri: IRI,
                                      data: String,
                                      apiRequestID: UUID,
                                      userProfile: UserProfileV1) extends OntologiesAdminResponderRequest


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses


/**
  * Represents an response to [[OntologiesGetAdminRequest]] consisting of a sequence of ontology IRIs.
  *
  * @param ontologies a sequence of IRIs.
  */
case class OntologiesGetAdminResponse(ontologies: Seq[IRI]) extends KnoraAdminResponse with OntologiesAdminJsonProtocol {
    def toJsValue = ontologiesGetAdminResponseFormat.write(this)
}

/**
  * Represents an response to [[OntologyGetAdminRequest]] containing a [[OntologyAdminData]].
  *
  * @param ontology a [[OntologyAdminData]].
  */
case class OntologyGetAdminResponse(ontology: OntologyAdminData) extends KnoraAdminResponse with OntologiesAdminJsonProtocol {
    def toJsValue = ontologGetAdminResponseFormat.write(this)
}


/**
  * Represents the response to [[OntologyCreateAdminRequest]] containing a [[OntologyAdminData]] of the newly created ontology.
  *
  * @param ontology a [[OntologyAdminData]] of the newly created ontology.
  */
case class OntologyCreateAdminResponse(ontology: OntologyAdminData) extends KnoraAdminResponse with OntologiesAdminJsonProtocol {
    def toJsValue = ontologyCreateAdminResponseFormat.write(this)
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents an ontology with its content as an JSON-LD string.
  *
  * @param ontologyIri the IRI of the ontology.
  * @param ontologyName the name of the ontology. This is basically the last part of the IRI.
  * @param projectIri the IRI of the project to which this ontology belongs.
  * @param data the contents of the the ontology as an JSON-LD string.
  */
case class OntologyAdminData(ontologyIri: IRI, ontologyName: String, projectIri: IRI, data: String)



//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API Admin JSON.
  */
trait OntologiesAdminJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {

    implicit val ontologyAdminDataFormat: JsonFormat[OntologyAdminData] = jsonFormat4(OntologyAdminData)
    implicit val createOntologyAdminPayloadFormat: RootJsonFormat[CreateOntologyAdminPayload] = jsonFormat2(CreateOntologyAdminPayload)
    implicit val ontologiesGetAdminResponseFormat: RootJsonFormat[OntologiesGetAdminResponse] = jsonFormat(OntologiesGetAdminResponse, "ontologies")
    implicit val ontologGetAdminResponseFormat: RootJsonFormat[OntologyGetAdminResponse] = jsonFormat(OntologyGetAdminResponse, "ontology")
    implicit val ontologyCreateAdminResponseFormat: RootJsonFormat[OntologyCreateAdminResponse] = jsonFormat(OntologyCreateAdminResponse, "ontology")
}
