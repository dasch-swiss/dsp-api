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
import org.knora.webapi.exceptions.{InconsistentTriplestoreDataException, NotFoundException}
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectIdentifierADM, ProjectRestrictedViewSettingsADM, ProjectRestrictedViewSettingsGetADM}
import org.knora.webapi.messages.admin.responder.sipimessages.{SipiFileInfoGetRequestADM, SipiFileInfoGetResponseADM, SipiResponderRequestADM}
import org.knora.webapi.messages.store.triplestoremessages.{IriSubjectV2, LiteralV2, SparqlExtendedConstructRequest, SparqlExtendedConstructResponse}
import org.knora.webapi.messages.util.PermissionUtilADM.EntityPermission
import org.knora.webapi.messages.util.{KnoraSystemInstances, PermissionUtilADM, ResponderData}
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.Responder.handleUnexpectedMessage

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
            sparqlQuery <- Future(org.knora.webapi.messages.twirl.queries.sparql.admin.txt.getFileValue(
                triplestore = settings.triplestoreType,
                filename = request.filename
            ).toString())

            queryResponse: SparqlExtendedConstructResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            _ = if (queryResponse.statements.isEmpty) throw NotFoundException(s"No file value was found for filename ${request.filename}")
            _ = if (queryResponse.statements.size > 1) throw InconsistentTriplestoreDataException(s"Filename ${request.filename} is used in more than one file value")

            fileValueIriSubject: IriSubjectV2 = queryResponse.statements.keys.head match {
                case iriSubject: IriSubjectV2 => iriSubject
                case _ => throw InconsistentTriplestoreDataException(s"The subject of the file value with filename ${request.filename} is not an IRI")
            }

            assertions: Seq[(String, String)] = queryResponse.statements(fileValueIriSubject).toSeq.flatMap {
                case (predicate: SmartIri, values: Seq[LiteralV2]) =>
                    values.map {
                        value => predicate.toString -> value.toString
                    }
            }

            maybeEntityPermission: Option[EntityPermission] = PermissionUtilADM.getUserPermissionFromAssertionsADM(
                entityIri = fileValueIriSubject.toString,
                assertions = assertions,
                requestingUser = request.requestingUser
            )

            _ = log.debug(s"SipiResponderADM - getFileInfoForSipiADM - maybePermissionCode: $maybeEntityPermission, requestingUser: ${request.requestingUser.username}")

            permissionCode: Int = maybeEntityPermission.map(_.toInt).getOrElse(0) // Sipi expects a permission code from 0 to 8

            response <- permissionCode match {
                case 1 =>
                    for {
                        maybeRVSettings <- (
                            responderManager ? ProjectRestrictedViewSettingsGetADM(
                                ProjectIdentifierADM(maybeShortcode = Some(request.projectID)),
                                requestingUser = KnoraSystemInstances.Users.SystemUser)
                            ).mapTo[Option[ProjectRestrictedViewSettingsADM]]
                    } yield SipiFileInfoGetResponseADM(permissionCode = permissionCode, maybeRVSettings)

                case _ => FastFuture.successful(SipiFileInfoGetResponseADM(permissionCode = permissionCode, restrictedViewSettings = None))
            }
        } yield response
    }
}