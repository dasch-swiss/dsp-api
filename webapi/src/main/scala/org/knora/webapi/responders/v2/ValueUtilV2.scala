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

package org.knora.webapi.responders.v2

import akka.actor.ActorSelection
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.messages.admin.responder.permissionsmessages.{DefaultObjectAccessPermissionsStringForPropertyGetADM, DefaultObjectAccessPermissionsStringResponseADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.{SparqlAskRequest, SparqlAskResponse}
import org.knora.webapi.util.SmartIri
import org.knora.webapi.{IRI, KnoraSystemInstances, NotFoundException}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Utility functions for working with Knora values.
  */
object ValueUtilV2 {
    /**
      * Gets the default permissions for a new value.
      *
      * @param projectIri       the IRI of the project of the containing resource.
      * @param resourceClassIri the IRI of the resource class.
      * @param propertyIri      the IRI of the property that points to the value.
      * @param requestingUser   the user that is creating the value.
      * @return a permission string.
      */
    def getDefaultValuePermissions(projectIri: IRI,
                                   resourceClassIri: SmartIri,
                                   propertyIri: SmartIri,
                                   requestingUser: UserADM,
                                   responderManager: ActorSelection)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[String] = {
        for {
            defaultObjectAccessPermissionsResponse: DefaultObjectAccessPermissionsStringResponseADM <- {
                responderManager ? DefaultObjectAccessPermissionsStringForPropertyGetADM(
                    projectIri = projectIri,
                    resourceClassIri = resourceClassIri.toString,
                    propertyIri = propertyIri.toString,
                    targetUser = requestingUser,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )
            }.mapTo[DefaultObjectAccessPermissionsStringResponseADM]
        } yield defaultObjectAccessPermissionsResponse.permissionLiteral
    }

    /**
      * Checks whether a list node exists, and throws [[NotFoundException]] otherwise.
      *
      * @param listNodeIri the IRI of the list node.
      */
    def checkListNodeExists(listNodeIri: IRI,
                            storeManager: ActorSelection)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[Unit] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkListNodeExistsByIri(listNodeIri = listNodeIri).toString)

            checkListNodeExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]

            _ = if (!checkListNodeExistsResponse.result) {
                throw NotFoundException(s"<$listNodeIri> does not exist or is not a ListNode")
            }
        } yield ()
    }
}
