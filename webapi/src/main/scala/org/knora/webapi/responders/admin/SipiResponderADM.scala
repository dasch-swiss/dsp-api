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

package org.knora.webapi.responders.admin

import akka.actor.Status
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectIdentifierADM, ProjectRestrictedViewSettingsADM, ProjectRestrictedViewSettingsGetADM}
import org.knora.webapi.messages.admin.responder.sipimessages.{SipiFileInfoGetRequestADM, SipiFileInfoGetResponseADM, SipiResponderRequestADM}
import org.knora.webapi.messages.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.responders.v1.GroupedProps.ValueProps
import org.knora.webapi.responders.{Responder, ResponderData}
import org.knora.webapi.util.PermissionUtilADM
import org.knora.webapi.util.PermissionUtilADM.{EntityPermission, filterPermissionRelevantAssertionsFromValueProps}
import org.knora.webapi._

import scala.concurrent.Future

/**
  * Responds to requests for information about binary representations of resources, and returns responses in Knora API
  * ADM format.
  */
class SipiResponderADM(responderData: ResponderData) extends Responder(responderData) {

    /**
      * Receives a message of type [[SipiResponderRequestADM]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive(msg: SipiResponderRequestADM) = msg match {
        case sipiFileInfoGetRequestADM: SipiFileInfoGetRequestADM => getFileInfoForSipiADM(sipiFileInfoGetRequestADM)
        case other => handleUnexpectedMessage(other, log, this.getClass.getName)
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Methods for generating complete responses.

    /**
      * Returns a [[SipiFileInfoGetResponseADM]] containing the permissions and path for a file.
      *
      * @param request the request.
      * @return a [[SipiFileInfoGetResponseADM]].
      */
    private def getFileInfoForSipiADM(request: SipiFileInfoGetRequestADM): Future[SipiFileInfoGetResponseADM] = {

        log.debug(s"SipiResponderADM - getFileInfoForSipiADM: projectID: ${request.projectID}, filename: ${request.filename}, user: ${request.requestingUser.username}")

        for {
            sparqlQuery <- Future(queries.sparql.admin.txt.getFileValue(
                triplestore = settings.triplestoreType,
                filename = request.filename
            ).toString())

            // _ = println(sparqlQuery)

            queryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            rows: Seq[VariableResultsRow] = queryResponse.results.bindings

            // check if rows were found for the given filename
            _ = if (rows.isEmpty) throw NotFoundException(s"No file value was found for filename ${request.filename}")

            // check that only one file value was found (by grouping by file value IRI)
            groupedByFileValue: Map[String, Seq[VariableResultsRow]] = rows.groupBy {
                row: VariableResultsRow => row.rowMap("fileValue")
            }

            _ = if (groupedByFileValue.size > 1) throw InconsistentTriplestoreDataException(s"Filename ${request.filename} is used in more than one file value")

            fileValueIri: IRI = groupedByFileValue.keys.head

            assertions: Seq[(String, String)] = rows.map {
                row => (row.rowMap("objPred"), row.rowMap("objObj"))
            }

            maybeEntityPermission: Option[EntityPermission] = PermissionUtilADM.getUserPermissionFromAssertionsADM(
                entityIri = fileValueIri,
                assertions = assertions,
                requestingUser = request.requestingUser
            )

            _ = log.debug(s"SipiResponderADM - getFileInfoForSipiADM - maybePermissionCode: $maybeEntityPermission, requestingUser: ${request.requestingUser.username}")

            permissionCode: Int = maybeEntityPermission.map(_.toInt).getOrElse(0) // Sipi expects a permission code from 0 to 8

            response <- permissionCode match {

                case 1 =>
                    for {
                        maybeRVSettings <- (responderManager ? ProjectRestrictedViewSettingsGetADM(ProjectIdentifierADM(shortcode = Some(request.projectID)), KnoraSystemInstances.Users.SystemUser)).mapTo[Option[ProjectRestrictedViewSettingsADM]]

                    } yield SipiFileInfoGetResponseADM(permissionCode = permissionCode, maybeRVSettings)
                case _ => FastFuture.successful(SipiFileInfoGetResponseADM(permissionCode = permissionCode, restrictedViewSettings = None))
            }

        } yield response
    }
}