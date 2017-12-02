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

package org.knora.webapi.messages.admin.responder.ontologiesmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectsAdminJsonProtocol}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import spray.json.{DefaultJsonProtocol, RootJsonFormat, _}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

case class CreateOntologyPayloadADM(ontologyName: String, projectIri: IRI) extends OntologiesADMJsonProtocol {
    def toJsValue: JsValue = createOntologyPayloadADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait for messages that can be sent to `HierarchicalListsResponderV2`.
  */
sealed trait OntologiesResponderRequestADM extends KnoraRequestADM



/**
  * Requests a list of all ontologies or the ontologies inside a project. A successful response will be a [[OntologiesGetResponseADM]]
  *
  * @param projectIri  the IRI of the project.
  * @param userProfile the profile of the user making the request.
  */
case class OntologiesGetRequestADM(projectIri: Option[IRI] = None,
                                   userProfile: UserProfileV1) extends OntologiesResponderRequestADM


/**
  * Requests an ontology (as JSON-LD). A successful response will be a [[OntologyGetResponseADM]].
  *
  * @param iri the ontology IRI
  * @param user the user making the request.
  */
case class OntologyGetRequestADM(iri: IRI,
                                 user: UserADM) extends OntologiesResponderRequestADM


/**
  * Requests the creation of an empty ontology. A successful response will be a [[OntologyCreateResponseADM]].
  *
  * @param ontologyName the name of the ontology to be created.
  * @param projectIri   the IRI of the project that the ontology will belong to.
  * @param apiRequestID the ID of the API request.
  * @param user  the user making the request.
  */
case class OntologyCreateRequestADM(ontologyName: String,
                                    projectIri: IRI,
                                    apiRequestID: UUID,
                                    user: UserADM) extends OntologiesResponderRequestADM


/**
  * Requests updating (overwriting) an existing ontology with the provided data in JSON-LD format.
  *
  * @param iri the IRI of the existing ontology.
  * @param data the data.
  * @param apiRequestID the ID of the API request.
  * @param user the user making the request.
  */
case class OntologyUpdateRequestADM(iri: IRI,
                                    data: String,
                                    apiRequestID: UUID,
                                    user: UserADM) extends OntologiesResponderRequestADM


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses


/**
  * Represents an response to [[OntologiesGetRequestADM]] consisting of a sequence of ontology IRIs.
  *
  * @param ontologies a sequence of IRIs.
  */
case class OntologiesGetResponseADM(ontologies: Seq[OntologyInfoADM]) extends KnoraResponseADM with OntologiesADMJsonProtocol {
    def toJsValue = ontologiesGetAdminResponseFormat.write(this)
}

/**
  * Represents an response to [[OntologyGetRequestADM]] containing a [[OntologyDataADM]].
  *
  * @param ontology a [[OntologyDataADM]].
  */
case class OntologyGetResponseADM(ontology: OntologyDataADM) extends KnoraResponseADM with OntologiesADMJsonProtocol {
    def toJsValue = ontologGetAdminResponseFormat.write(this)
}


/**
  * Represents the response to [[OntologyCreateRequestADM]] containing a [[OntologyDataADM]] of the newly created ontology.
  *
  * @param ontology a [[OntologyDataADM]] of the newly created ontology.
  */
case class OntologyCreateResponseADM(ontology: OntologyDataADM) extends KnoraResponseADM with OntologiesADMJsonProtocol {
    def toJsValue = ontologyCreateAdminResponseFormat.write(this)
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents an ontology with its content as an JSON-LD string.
  *
  * @param ontologyIri the IRI of the ontology.
  * @param ontologyName the name of the ontology. This is basically the last part of the IRI.
  * @param project the [[ProjectInfoV1]] of the project to which this ontology belongs.
  * @param data the contents of the the ontology as an JSON-LD string.
  */
case class OntologyDataADM(ontologyIri: IRI, ontologyName: String, project: ProjectADM, data: String)

/**
  * Represents basic information of an ontology.
  *
  * @param ontologyIri the IRI of the ontology.
  * @param ontologyName the name of the ontology.
  * @param project the [[ProjectInfoV1]] of the project to which this ontology belongs.
  */
case class OntologyInfoADM(ontologyIri: IRI, ontologyName: String, project: ProjectADM)


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API Admin JSON.
  */
trait OntologiesADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with ProjectsAdminJsonProtocol {

    implicit val ontologyDataADMFormat: JsonFormat[OntologyDataADM] = jsonFormat4(OntologyDataADM)
    implicit val ontologyInfoADMFormat: JsonFormat[OntologyInfoADM] = jsonFormat3(OntologyInfoADM)
    implicit val createOntologyPayloadADMFormat: RootJsonFormat[CreateOntologyPayloadADM] = jsonFormat2(CreateOntologyPayloadADM)
    implicit val ontologiesGetAdminResponseFormat: RootJsonFormat[OntologiesGetResponseADM] = jsonFormat(OntologiesGetResponseADM, "ontologies")
    implicit val ontologGetAdminResponseFormat: RootJsonFormat[OntologyGetResponseADM] = jsonFormat(OntologyGetResponseADM, "ontology")
    implicit val ontologyCreateAdminResponseFormat: RootJsonFormat[OntologyCreateResponseADM] = jsonFormat(OntologyCreateResponseADM, "ontology")
}
