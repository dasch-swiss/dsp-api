/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.actor.ActorRef
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.{ForbiddenException, NotFoundException}
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.permissionsmessages.{
  DefaultObjectAccessPermissionsStringForPropertyGetADM,
  DefaultObjectAccessPermissionsStringResponseADM
}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.sipimessages.{
  DeleteTemporaryFileRequest,
  MoveTemporaryFileToPermanentStorageRequest
}
import org.knora.webapi.messages.store.triplestoremessages.{SparqlAskRequest, SparqlAskResponse}
import org.knora.webapi.messages.util.PermissionUtilADM.EntityPermission
import org.knora.webapi.messages.util.{KnoraSystemInstances, PermissionUtilADM}
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.{FileValueContentV2, ReadValueV2, ValueContentV2}
import org.knora.webapi.messages.v2.responder.{SuccessResponseV2, UpdateResultInProject}

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
  def checkResourcePermission(
    resourceInfo: ReadResourceV2,
    permissionNeeded: EntityPermission,
    requestingUser: UserADM
  ): Unit = {
    val maybeUserPermission: Option[EntityPermission] = PermissionUtilADM.getUserPermissionADM(
      entityCreator = resourceInfo.attachedToUser,
      entityProject = resourceInfo.projectADM.id,
      entityPermissionLiteral = resourceInfo.permissions,
      requestingUser = requestingUser
    )

    val hasRequiredPermission: Boolean = maybeUserPermission match {
      case Some(userPermission: EntityPermission) => userPermission >= permissionNeeded
      case None                                   => false
    }

    if (!hasRequiredPermission) {
      throw ForbiddenException(
        s"User ${requestingUser.email} does not have ${permissionNeeded.getName} on resource <${resourceInfo.resourceIri}>"
      )
    }
  }

  /**
   * Checks that a user has the specified permission on a value.
   *
   * @param resourceInfo   the resource containing the value.
   * @param valueInfo      the value to be updated.
   * @param requestingUser the requesting user.
   */
  def checkValuePermission(
    resourceInfo: ReadResourceV2,
    valueInfo: ReadValueV2,
    permissionNeeded: EntityPermission,
    requestingUser: UserADM
  ): Unit = {
    val maybeUserPermission: Option[EntityPermission] = PermissionUtilADM.getUserPermissionADM(
      entityCreator = valueInfo.attachedToUser,
      entityProject = resourceInfo.projectADM.id,
      entityPermissionLiteral = valueInfo.permissions,
      requestingUser = requestingUser
    )

    val hasRequiredPermission: Boolean = maybeUserPermission match {
      case Some(userPermission: EntityPermission) => userPermission >= permissionNeeded
      case None                                   => false
    }

    if (!hasRequiredPermission) {
      throw ForbiddenException(
        s"User ${requestingUser.email} does not have ${permissionNeeded.getName} on value <${valueInfo.valueIri}>"
      )
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
  def getDefaultValuePermissions(
    projectIri: IRI,
    resourceClassIri: SmartIri,
    propertyIri: SmartIri,
    requestingUser: UserADM,
    responderManager: ActorRef
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[String] =
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

  /**
   * Checks whether a list node exists, and throws [[NotFoundException]] otherwise.
   *
   * @param listNodeIri the IRI of the list node.
   */
  def checkListNodeExists(listNodeIri: IRI, storeManager: ActorRef)(implicit
    timeout: Timeout,
    executionContext: ExecutionContext
  ): Future[Unit] =
    for {
      askString <- Future(
        org.knora.webapi.messages.twirl.queries.sparql.admin.txt
          .checkListNodeExistsByIri(listNodeIri = listNodeIri)
          .toString
      )

      checkListNodeExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]

      _ = if (!checkListNodeExistsResponse.result) {
        throw NotFoundException(s"<$listNodeIri> does not exist or is not a ListNode")
      }
    } yield ()

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
  def doSipiPostUpdate[T <: UpdateResultInProject](
    updateFuture: Future[T],
    valueContent: ValueContentV2,
    requestingUser: UserADM,
    responderManager: ActorRef,
    storeManager: ActorRef,
    log: Logger
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[T] =
    // Was this a file value update?
    valueContent match {
      case fileValueContent: FileValueContentV2 =>
        // Yes. Did it succeed?
        updateFuture.transformWith {
          case Success(updateInProject: UpdateResultInProject) =>
            // Yes. Ask Sipi to move the file to permanent storage.
            val sipiRequest = MoveTemporaryFileToPermanentStorageRequest(
              internalFilename = fileValueContent.fileValue.internalFilename,
              prefix = updateInProject.projectADM.shortcode,
              requestingUser = requestingUser
            )

            // If Sipi succeeds, return the future we were given. Otherwise, return a failed future.
            (storeManager ? sipiRequest).mapTo[SuccessResponseV2].flatMap(_ => updateFuture)

          case Failure(_) =>
            // The file value update failed. Ask Sipi to delete the temporary file.
            val sipiRequest = DeleteTemporaryFileRequest(
              internalFilename = fileValueContent.fileValue.internalFilename,
              requestingUser = requestingUser
            )

            val sipiResponseFuture: Future[SuccessResponseV2] = (storeManager ? sipiRequest).mapTo[SuccessResponseV2]

            // Did Sipi successfully delete the temporary file?
            sipiResponseFuture.transformWith {
              case Success(_) =>
                // Yes. Return the future we were given.
                updateFuture

              case Failure(sipiException) =>
                // No. Log Sipi's error, and return the future we were given.
                log.error("Sipi error", sipiException)
                updateFuture
            }
        }

      case _ =>
        // This wasn't a file value update. Return the future we were given.
        updateFuture
    }
}
