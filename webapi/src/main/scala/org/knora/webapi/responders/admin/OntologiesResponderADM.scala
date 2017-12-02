/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.admin

import java.util.UUID

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.listsmessages.{FullList, ListInfo, ListNode}
import org.knora.webapi.messages.admin.responder.ontologiesmessages._
import org.knora.webapi.messages.store.triplestoremessages.{SparqlExtendedConstructRequest, SparqlExtendedConstructResponse, StringV2}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.Future

/**
  * A responder that returns information about hierarchical lists.
  */
class OntologiesResponderADM extends Responder {

    def receive: PartialFunction[Any, Unit] = {
        case OntologiesGetRequestADM(projectIri, userProfile) => future2Message(sender(), ontologiesGetRequestADM(projectIri, userProfile), log)
        case OntologyGetRequestADM(ontologyIri, userProfile) => future2Message(sender(), ontologyGetRequestADM(ontologyIri, userProfile), log)
        case OntologyCreateRequestADM(ontologyName, projectIri, apiRequestID, userProfile) => future2Message(sender(), ontologyCreateRequestADM(ontologyName, projectIri, apiRequestID, userProfile), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }


    /**
      * Gets all ontologies and returns them as a [[OntologiesGetResponseADM]]. For performance reasons
      * (as lists can be very large), we only return the IRI of the ontology.
      *
      * @param projectIri  the IRI of the project the ontology belongs to.
      * @param userProfile the profile of the user making the request.
      * @return a [[OntologiesGetResponseADM]].
      */
    def ontologiesGetRequestADM(projectIri: Option[IRI], userProfile: UserProfileV1): Future[OntologiesGetResponseADM] = {

        // log.debug("listsGetRequestV2")

        for {
            sparqlQuery <- Future(queries.sparql.admin.txt.getLists(
                triplestore = settings.triplestoreType,
                maybeProjectIri = projectIri
            ).toString())

            listsResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            // _ = log.debug("listsGetAdminRequest - listsResponse: {}", listsResponse )

            // Seq(subjectIri, (objectIri -> Seq(stringWithOptionalLand))
            statements = listsResponse.statements.toList

            items: Seq[ListInfo] = statements.map {
                case (listIri: IRI, propsMap: Map[IRI, Seq[StringV2]]) =>

                    ListInfo(
                        id = listIri,
                        projectIri = propsMap.get(OntologyConstants.KnoraBase.AttachedToProject).map(_.head.value),
                        labels = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringV2]),
                        comments = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringV2])
                    )
            }

            // _ = log.debug("listsGetAdminRequest - items: {}", items)

            ontologies = Seq.empty[IRI]

        } yield OntologiesGetResponseADM(ontologies = ontologies)
    }

    /**
      * Retrieves a complete ontology from the triplestore and returns it as a [[OntologyGetResponseADM]].
      *
      * @param ontologyIri the Iri of the ontology to be queried.
      * @param userProfile the profile of the user making the request.
      * @return a [[OntologyGetResponseADM]].
      */
    def ontologyGetRequestADM(ontologyIri: IRI, userProfile: UserProfileV1): Future[OntologyGetResponseADM] = {

        for {
            // this query will give us only the information about the root node.
            sparqlQuery <- Future(queries.sparql.admin.txt.getListNode(
                triplestore = settings.triplestoreType,
                nodeIri = ontologyIri
            ).toString())

            listInfoResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

//            // check to see if list could be found
//            _ = if (listInfoResponse.statements.isEmpty) {
//                throw NotFoundException(s"List not found: $ontologyIri")
//            }
//            // _ = log.debug(s"listExtendedGetRequestV2 - statements: {}", MessageUtil.toSource(statements))
//
//            // here we know that the list exists and it is fine if children is an empty list
//            children: Seq[ListNode] <- listGetChildren(ontologyIri, userProfile)
//
//            // _ = log.debug(s"listGetRequestV2 - children count: {}", children.size)
//
//            // Map(subjectIri -> (objectIri -> Seq(stringWithOptionalLand))
//            statements = listInfoResponse.statements
//            listinfo = statements.head match {
//                case (nodeIri: IRI, propsMap: Map[IRI, Seq[StringV2]]) =>
//                    ListInfo(
//                        id = nodeIri,
//                        projectIri = propsMap.get(OntologyConstants.KnoraBase.AttachedToProject).map(_.head.value),
//                        labels = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringV2]),
//                        comments = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringV2])
//                    )
//            }
//
//            list = FullList(listinfo = listinfo, children = children)
            // _ = log.debug(s"listGetRequestV2 - list: {}", MessageUtil.toSource(list))

            data = OntologyDataADM("", "", "", "")

        } yield OntologyGetResponseADM(ontology = data)
    }

    /**
      * Creates a new empty ontology and returns is s a [[OntologyCreateResponseADM]].
      *
      * @param ontologyName the name of the new ontology.
      * @param projectIri the project IRI the ontology belongs to.
      * @param apiRequestID the api request id.
      * @param userProfile the profile of the user making the request.
      * @return a [[OntologyCreateResponseADM]]
      */
    def ontologyCreateRequestADM(ontologyName: String, projectIri: IRI, apiRequestID: UUID, userProfile: UserProfileV1): Future[OntologyCreateResponseADM] = {
        for {

            data <- FastFuture.successful(OntologyDataADM("", "", "", ""))

        } yield OntologyCreateResponseADM(ontology = data)

    }
}