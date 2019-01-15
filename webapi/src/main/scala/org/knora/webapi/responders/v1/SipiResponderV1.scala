/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.responders.v1

import akka.actor.{ActorRef, ActorSystem, Status}
import akka.pattern._
import org.knora.webapi.messages.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.messages.v1.responder.sipimessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.util.PermissionUtilADM
import org.knora.webapi.{BadRequestException, InconsistentTriplestoreDataException}

import scala.concurrent.Future

/**
  * Responds to requests for information about binary representations of resources, and returns responses in Knora API
  * v1 format.
  */
class SipiResponderV1(system: ActorSystem, applicationStateActor: ActorRef, responderManager: ActorRef, storeManager: ActorRef) extends Responder(system: ActorSystem, applicationStateActor: ActorRef, responderManager: ActorRef, storeManager: ActorRef) {

    // Converts SPARQL query results to ApiValueV1 objects.
    val valueUtilV1 = new ValueUtilV1(settings)

    /**
      * Receives a message of type [[SipiResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive(msg: SipiResponderRequestV1) = msg match {
        case SipiFileInfoGetRequestV1(fileValueIri, userProfile) => getFileInfoForSipiV1(fileValueIri, userProfile)
        case other => handleUnexpectedMessage(other, log, this.getClass.getName)
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Methods for generating complete responses.

    /**
      * Returns a [[SipiFileInfoGetResponseV1]] containing the permissions and path for a file.
      *
      * @param filename the iri of the resource.
      * @return a [[SipiFileInfoGetResponseV1]].
      */
    private def getFileInfoForSipiV1(filename: String, userProfile: UserProfileV1): Future[SipiFileInfoGetResponseV1] = {

        log.debug(s"SipiResponderV1 - getFileInfoForSipiV1: filename: $filename, user: ${userProfile.userData.email}")

        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getFileValue(
                triplestore = settings.triplestoreType,
                filename = filename
            ).toString())

            queryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            rows: Seq[VariableResultsRow] = queryResponse.results.bindings
            // check if rows were found for the given filename
            _ = if (rows.isEmpty) throw BadRequestException(s"No file value was found for filename $filename")

            // check that only one file value was found (by grouping by file value IRI)
            groupedByResourceIri = rows.groupBy {
                row: VariableResultsRow =>
                    row.rowMap("fileValue")
            }
            _ = if (groupedByResourceIri.size > 1) throw InconsistentTriplestoreDataException(s"filename $filename is referred to from more than one file value")

            valueProps = valueUtilV1.createValueProps(filename, rows)

            maybePermissionCode: Option[Int] = PermissionUtilADM.getUserPermissionWithValuePropsV1(
                valueIri = filename,
                valueProps = valueProps,
                entityProject = None, // no need to specify this here, because it's in valueProps
                userProfile = userProfile
            )

            _ = log.debug(s"SipiResponderV1 - getFileInfoForSipiV1 - maybePermissionCode: $maybePermissionCode, requestingUser: ${userProfile.userData.email}")

            permissionCode: Int = maybePermissionCode.getOrElse(0) // Sipi expects a permission code from 0 to 8

        } yield SipiFileInfoGetResponseV1(
            permissionCode = permissionCode
        )
    }




}