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
import org.knora.webapi.messages.admin.responder.ontologiesadminmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.Future

/**
  * A responder that returns information about hierarchical lists.
  */
class OntologiesAdminResponder extends Responder {

    def receive: PartialFunction[Any, Unit] = {
        case OntologiesGetAdminRequest(projectIri, userProfile) => future2Message(sender(), ontologiesGetAdminRequest(projectIri, userProfile), log)
        case OntologyGetAdminRequest(listIri, userProfile) => future2Message(sender(), ontologyGetAdminRequest(listIri, userProfile), log)
        case OntologyCreateAdminRequest(ontologyName, projectIri, apiRequestID, userProfile) => future2Message(sender(), ontologyCreateAdminRequest(ontologyName, projectIri, apiRequestID, userProfile), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }


    /**
      * Gets all ontologies and returns them as a [[OntologiesGetAdminResponse]]. For performance reasons
      * (as lists can be very large), we only return the IRI of the ontology.
      *
      * @param projectIri  the IRI of the project the ontology belongs to.
      * @param userProfile the profile of the user making the request.
      * @return a [[OntologiesGetAdminResponse]].
      */
    def ontologiesGetAdminRequest(projectIri: Option[IRI], userProfile: UserProfileV1): Future[OntologiesGetAdminResponse] = {

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

        } yield OntologiesGetAdminResponse(ontologies = ontologies)
    }

    /**
      * Retrieves a complete ontology from the triplestore and returns it as a [[OntologyGetAdminResponse]].
      *
      * @param ontologyIri the Iri of the ontology to be queried.
      * @param userProfile the profile of the user making the request.
      * @return a [[OntologyGetAdminResponse]].
      */
    def ontologyGetAdminRequest(ontologyIri: IRI, userProfile: UserProfileV1): Future[OntologyGetAdminResponse] = {

        for {
            // this query will give us only the information about the root node.
            sparqlQuery <- Future(queries.sparql.admin.txt.getListNode(
                triplestore = settings.triplestoreType,
                nodeIri = rootNodeIri
            ).toString())

            listInfoResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            // check to see if list could be found
            _ = if (listInfoResponse.statements.isEmpty) {
                throw NotFoundException(s"List not found: $rootNodeIri")
            }
            // _ = log.debug(s"listExtendedGetRequestV2 - statements: {}", MessageUtil.toSource(statements))

            // here we know that the list exists and it is fine if children is an empty list
            children: Seq[ListNode] <- listGetChildren(rootNodeIri, userProfile)

            // _ = log.debug(s"listGetRequestV2 - children count: {}", children.size)

            // Map(subjectIri -> (objectIri -> Seq(stringWithOptionalLand))
            statements = listInfoResponse.statements
            listinfo = statements.head match {
                case (nodeIri: IRI, propsMap: Map[IRI, Seq[StringV2]]) =>
                    ListInfo(
                        id = nodeIri,
                        projectIri = propsMap.get(OntologyConstants.KnoraBase.AttachedToProject).map(_.head.value),
                        labels = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringV2]),
                        comments = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringV2])
                    )
            }

            list = FullList(listinfo = listinfo, children = children)
            // _ = log.debug(s"listGetRequestV2 - list: {}", MessageUtil.toSource(list))

            data = OntologyAdminData("", "", "", "")

        } yield OntologyGetAdminResponse(ontology = data)
    }

    /**
      * Creates a new empty ontology and returns is s a [[OntologyCreateAdminResponse]].
      *
      * @param ontologyName the name of the new ontology.
      * @param projectIri the project IRI the ontology belongs to.
      * @param apiRequestID the api request id.
      * @param userProfile the profile of the user making the request.
      * @return a [[OntologyCreateAdminResponse]]
      */
    def ontologyCreateAdminRequest(ontologyName: String, projectIri: IRI, apiRequestID: UUID, userProfile: UserProfileV1): Future[OntologyCreateAdminResponse] = {
        for {

            data <- FastFuture.successful(OntologyAdminData("", "", "", ""))

        } yield OntologyCreateAdminResponse(ontology = data)

    }
}