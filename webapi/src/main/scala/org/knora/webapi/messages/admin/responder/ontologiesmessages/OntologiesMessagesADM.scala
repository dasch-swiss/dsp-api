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

package org.knora.webapi.messages.admin.responder.ontologiesmessages

import java.util.UUID

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectsADMJsonProtocol}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UsersADMJsonProtocol}
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import spray.json.{DefaultJsonProtocol, RootJsonFormat, _}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

case class CreateOntologyPayloadADM(ontologyName: String, projectIri: IRI) {
    def toJsValue: JsValue = OntologiesADMJsonProtocol.createOntologyPayloadADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait for messages that can be sent to `HierarchicalListsResponderV2`.
  */
sealed trait OntologiesResponderRequestADM extends KnoraRequestADM

/**
  * Requests basic informations about all ontologies. A successful response will be a [[OntologiesGetResponseADM]]
  *
  * @param requestingUser the user making the request.
  */
case class OntologyInfosGetRequestADM(requestingUser: UserADM) extends OntologiesResponderRequestADM

/**
  * Requests basic informations about a single ontology. A successful response will be a [[OntologyGetResponseADM]]
  *
  * @param requestingUser the user making the request.
  */
case class OntologyInfoGetRequestADM(ontologyIri: IRI,
                                     requestingUser: UserADM) extends OntologiesResponderRequestADM


/**
  * Requests a list of all ontologies. A successful response will be a [[OntologiesGetResponseADM]]
  *
  * @param requestingUser the user making the request.
  */
case class OntologiesGetRequestADM(requestingUser: UserADM) extends OntologiesResponderRequestADM


/**
  * Requests an ontology (as JSON-LD). A successful response will be a [[OntologyGetResponseADM]].
  *
  * @param ontologyIri the ontology IRI
  * @param requestingUser the user making the request.
  */
case class OntologyGetRequestADM(ontologyIri: IRI,
                                 requestingUser: UserADM) extends OntologiesResponderRequestADM


/**
  * Requests the creation of an empty ontology. A successful response will be a [[OntologyCreateResponseADM]].
  *
  * @param ontologyName the name of the ontology to be created.
  * @param projectIri   the IRI of the project that the ontology will belong to.
  * @param apiRequestID the ID of the API request.
  * @param requestingUser  the user making the request.
  */
case class OntologyCreateRequestADM(ontologyName: String,
                                    projectIri: IRI,
                                    apiRequestID: UUID,
                                    requestingUser: UserADM) extends OntologiesResponderRequestADM


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
  * Represents an response to [[OntologyInfosGetRequestADM]] consisting of a sequence of [[OntologyInfoADM]].
  *
  * @param ontologies a sequence of IRIs.
  */
case class OntologyInfosGetResponseADM(ontologies: Seq[OntologyInfoADM]) extends KnoraResponseADM {
    def toJsValue = OntologiesADMJsonProtocol.ontologyInfosGetResponseADMFormat.write(this)
}

/**
  * Represents an response to [[OntologyInfoGetRequestADM]] consisting of a single [[OntologyInfoADM]].
  *
  * @param ontology the basic ontology information.
  */
case class OntologyInfoGetResponseADM(ontology: OntologyInfoADM) extends KnoraResponseADM {
    def toJsValue = OntologiesADMJsonProtocol.ontologyInfoGetResponseADMFormat.write(this)
}

/**
  * Represents an response to [[OntologiesGetRequestADM]] containing a sequence of [[OntologyDataADM]].
  *
  * @param ontologies a asequence of [[OntologyDataADM]].
  */
case class OntologiesGetResponseADM(ontologies: Seq[OntologyDataADM]) extends KnoraResponseADM {
    def toJsValue = OntologiesADMJsonProtocol.ontologiesGetResponseADMFormat.write(this)
}

/**
  * Represents an response to [[OntologyGetRequestADM]] containing a [[OntologyDataADM]].
  *
  * @param ontology a [[OntologyDataADM]].
  */
case class OntologyGetResponseADM(ontology: OntologyDataADM) extends KnoraResponseADM {
    def toJsValue = OntologiesADMJsonProtocol.ontologGetResponseADMFormat.write(this)
}


/**
  * Represents the response to [[OntologyCreateRequestADM]] containing a [[OntologyDataADM]] of the newly created ontology.
  *
  * @param ontology a [[OntologyDataADM]] of the newly created ontology.
  */
case class OntologyCreateResponseADM(ontology: OntologyDataADM) extends KnoraResponseADM {
    def toJsValue = OntologiesADMJsonProtocol.ontologyCreateResponseADMFormat.write(this)
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
case class OntologyDataADM(ontologyIri: IRI,
                           ontologyName: String,
                           project: ProjectADM,
                           data: String) {

    def toJsValue: JsValue = OntologiesADMJsonProtocol.ontologyDataADMFormat.write(this)
}

/**
  * Represents basic information about an ontology (with project).
  *
  * @param ontologyIri the IRI of the ontology.
  * @param ontologyName the name of the ontology.
  * @param project the [[ProjectADM]] of the project to which this ontology belongs.
  */
case class OntologyInfoADM(ontologyIri: IRI,
                           ontologyName: String,
                           project: ProjectADM) {

    def toJsValue: JsValue = OntologiesADMJsonProtocol.ontologyInfoADMFormat.write(this)

    def asOntologyInfoShortADM: OntologyInfoShortADM = {
        OntologyInfoShortADM(
            ontologyIri = ontologyIri,
            ontologyName = ontologyName
        )
    }
}

/**
  * Represents basic information about an ontology (without project).
  *
  * @param ontologyIri the IRI of the ontology.
  * @param ontologyName the name of the ontology.
  */
case class OntologyInfoShortADM(ontologyIri: IRI,
                                ontologyName: String) {

    def toJsValue: JsValue = OntologiesADMJsonProtocol.ontologyInfoShortADMFormat.write(this)
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API Admin JSON.
  */
object OntologiesADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with ProjectsADMJsonProtocol {

    implicit val ontologyDataADMFormat: JsonFormat[OntologyDataADM] = lazyFormat(jsonFormat4(OntologyDataADM))
    implicit val ontologyInfoADMFormat: JsonFormat[OntologyInfoADM] = lazyFormat(jsonFormat3(OntologyInfoADM))
    implicit val ontologyInfoShortADMFormat: JsonFormat[OntologyInfoShortADM] = lazyFormat(jsonFormat2(OntologyInfoShortADM))
    implicit val ontologyInfosGetResponseADMFormat: RootJsonFormat[OntologyInfosGetResponseADM] = rootFormat(lazyFormat(jsonFormat(OntologyInfosGetResponseADM, "ontologies")))
    implicit val ontologyInfoGetResponseADMFormat: RootJsonFormat[OntologyInfoGetResponseADM] = jsonFormat1(OntologyInfoGetResponseADM)
    implicit val ontologiesGetResponseADMFormat: RootJsonFormat[OntologiesGetResponseADM] = jsonFormat1(OntologiesGetResponseADM)
    implicit val ontologGetResponseADMFormat: RootJsonFormat[OntologyGetResponseADM] = jsonFormat1(OntologyGetResponseADM)
    implicit val createOntologyPayloadADMFormat: RootJsonFormat[CreateOntologyPayloadADM] = jsonFormat2(CreateOntologyPayloadADM)

    implicit val ontologyCreateResponseADMFormat: RootJsonFormat[OntologyCreateResponseADM] = jsonFormat1(OntologyCreateResponseADM)
}
