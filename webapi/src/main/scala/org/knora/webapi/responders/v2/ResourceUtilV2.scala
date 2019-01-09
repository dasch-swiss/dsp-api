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
import akka.event.LoggingAdapter
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.messages.admin.responder.permissionsmessages.{DefaultObjectAccessPermissionsStringForPropertyGetADM, DefaultObjectAccessPermissionsStringResponseADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.{SparqlAskRequest, SparqlAskResponse}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.sipimessages.{DeleteTemporaryFileRequestV2, MoveTemporaryFileToPermanentStorageRequestV2}
import org.knora.webapi.messages.v2.responder.valuemessages.{FileValueContentV2, ReadValueV2, ValueContentV2}
import org.knora.webapi.util.PermissionUtilADM.EntityPermission
import org.knora.webapi.util.{PermissionUtilADM, SmartIri}
import org.knora.webapi.{ForbiddenException, IRI, KnoraSystemInstances, NotFoundException}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
  * Utility functions for working with Knora resources and their values.
  */
object ResourceUtilV2 {

    /**
      * Checks that a user has the specified permission on a resource.
      *
      * @param resourceInfo   the resource to be updated.
      * @param requestingUser the requesting user.
      */
    def checkResourcePermission(resourceInfo: ReadResourceV2, permissionNeeded: EntityPermission, requestingUser: UserADM): Unit = {
        val maybeUserPermission: Option[EntityPermission] = PermissionUtilADM.getUserPermissionADM(
            entityIri = resourceInfo.resourceIri,
            entityCreator = resourceInfo.attachedToUser,
            entityProject = resourceInfo.projectADM.id,
            entityPermissionLiteral = resourceInfo.permissions,
            requestingUser = requestingUser
        )

        val hasRequiredPermission: Boolean = maybeUserPermission match {
            case Some(userPermission: EntityPermission) => userPermission >= permissionNeeded
            case None => false
        }

        if (!hasRequiredPermission) {
            throw ForbiddenException(s"User ${requestingUser.email} does not have ${permissionNeeded.getName} on resource <${resourceInfo.resourceIri}>")
        }
    }

    /**
      * Checks that a user has the specified permission on a value.
      *
      * @param resourceInfo   the resource containing the value.
      * @param valueInfo      the value to be updated.
      * @param requestingUser the requesting user.
      */
    def checkValuePermission(resourceInfo: ReadResourceV2, valueInfo: ReadValueV2, permissionNeeded: EntityPermission, requestingUser: UserADM): Unit = {
        val maybeUserPermission: Option[EntityPermission] = PermissionUtilADM.getUserPermissionADM(
            entityIri = valueInfo.valueIri,
            entityCreator = valueInfo.attachedToUser,
            entityProject = resourceInfo.projectADM.id,
            entityPermissionLiteral = valueInfo.permissions,
            requestingUser = requestingUser
        )

        val hasRequiredPermission: Boolean = maybeUserPermission match {
            case Some(userPermission: EntityPermission) => userPermission >= permissionNeeded
            case None => false
        }

        if (!hasRequiredPermission) {
            throw ForbiddenException(s"User ${requestingUser.email} does not have ${permissionNeeded.getName} on value <${valueInfo.valueIri}>")
        }
    }

    /**
      * Gets the default permissions for a new value.
      *
      * @param projectIri       the IRI of the project of the containing resource.
      * @param resourceClassIri the internal IRI of the resource class.
      * @param propertyIri      the internal IRI of the property that points to the value.
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

    /**
      * Given a future representing an operation that was supposed to update a value in a triplestore, checks whether
      * the updated value was a file value. If not, this method returns the same future. If it was a file value, this
      * method checks whether the update was successful. If so, it asks Sipi to move the file to permanent storage.
      * If not, it asks Sipi to delete the temporary file.
      *
      * @param updateFuture   the future that should have updated the triplestore.
      * @param valueContent   the value that should have been created or updated.
      * @param requestingUser the user making the request.
      */
    def doSipiPostUpdate[T](updateFuture: Future[T],
                            valueContent: ValueContentV2,
                            requestingUser: UserADM,
                            responderManager: ActorSelection,
                            log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[T] = {
        // Was this a file value update?
        valueContent match {
            case fileValueContent: FileValueContentV2 =>
                // Yes. Did it succeed?
                updateFuture.transformWith {
                    case Success(_) =>
                        // Yes. Ask Sipi to move the file to permanent storage.
                        val sipiRequest = MoveTemporaryFileToPermanentStorageRequestV2(
                            internalFilename = fileValueContent.fileValue.internalFilename,
                            requestingUser = requestingUser
                        )

                        // If Sipi succeeds, return the future we were given. Otherwise, return a failed future.
                        (responderManager ? sipiRequest).mapTo[SuccessResponseV2].flatMap(_ => updateFuture)

                    case Failure(_) =>
                        // The file value update failed. Ask Sipi to delete the temporary file.
                        val sipiRequest = DeleteTemporaryFileRequestV2(
                            internalFilename = fileValueContent.fileValue.internalFilename,
                            requestingUser = requestingUser
                        )

                        val sipiResponseFuture: Future[SuccessResponseV2] = (responderManager ? sipiRequest).mapTo[SuccessResponseV2]

                        // Did Sipi successfully delete the temporary file?
                        sipiResponseFuture.transformWith {
                            case Success(_) =>
                                // Yes. Return the future we were given.
                                updateFuture

                            case Failure(sipiException) =>
                                // No. Log Sipi's error, and return the future we were given.
                                log.error(cause = sipiException, message = "Sipi error")
                                updateFuture
                        }
                }

            case _ =>
                // This wasn't a file value update. Return the future we were given.
                updateFuture
        }
    }
}
