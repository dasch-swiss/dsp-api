/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import com.typesafe.scalalogging.LazyLogging
import zio.*
import zio.Task

import dsp.errors.ForbiddenException
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.sipimessages.DeleteTemporaryFileRequest
import org.knora.webapi.messages.store.sipimessages.MoveTemporaryFileToPermanentStorageRequest
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.v2.responder.UpdateResultInProject
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.FileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageExternalFileValueContentV2
import org.knora.webapi.routing.v2.AssetIngestState
import org.knora.webapi.routing.v2.AssetIngestState.*
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

/**
 * Utility functions for working with Knora resources and their values.
 */
trait ResourceUtilV2 {

  /**
   * Checks that a user has the specified permission on a resource.
   *
   * @param resourceInfo             the resource to be updated.
   * @param permissionNeeded         the necessary Permission.ObjectAccess,
   * @param requestingUser           the requesting user.
   * @return [[ForbiddenException]]  if user does not have permission needed on the resource.
   */
  def checkResourcePermission(
    resourceInfo: ReadResourceV2,
    permissionNeeded: Permission.ObjectAccess,
    requestingUser: User,
  ): IO[ForbiddenException, Unit]

  /**
   * Checks that a user has the specified permission on a value.
   *
   * @param resourceInfo              the resource containing the value.
   * @param valueInfo                 the value to be updated.
   * @param permissionNeeded          the necessary Permission.ObjectAccess,
   * @param requestingUser            the requesting user.
   * @return  [[ForbiddenException]]  if user does not have permissions on the value.
   */
  def checkValuePermission(
    resourceInfo: ReadResourceV2,
    valueInfo: ReadValueV2,
    permissionNeeded: Permission.ObjectAccess,
    requestingUser: User,
  ): IO[ForbiddenException, Unit]

  /**
   * Checks whether a list node exists and if is a root node.
   *
   * @param nodeIri the IRI of the list node.
   * @return Task of Either None for nonexistent, true for root and false for child node.
   */
  def checkListNodeExistsAndIsRootNode(nodeIri: IRI): Task[Either[Option[Nothing], Boolean]]

  /**
   * Given an update task which changes [[FileValueContentV2]] values the related files need to be finalized.
   * If the update was successful the temporary files are moved to permanent storage.
   * If the update failed the temporary files are deleted silently.
   *
   * @param updateTask     the [[Task]] that updates the triplestore.
   * @param fileValues     the values which the task updates.
   * @param requestingUser the user making the request.
   *
   * @return The result of the updateTask, unless this task was successful and the subsequent move to permanent storage failed.
   *         In the latter case the failure from Sipi is returned.
   */
  def doSipiPostUpdate[T <: UpdateResultInProject](
    updateTask: Task[T],
    fileValues: Seq[FileValueContentV2],
    requestingUser: User,
  ): Task[T]

  def doSipiPostUpdateIfInTemp[T <: UpdateResultInProject](
    ingestState: AssetIngestState,
    updateTask: Task[T],
    fileValues: Seq[FileValueContentV2],
    requestingUser: User,
  ): Task[T] =
    ingestState match {
      case AssetIngested => updateTask
      case AssetInTemp   => doSipiPostUpdate(updateTask, fileValues, requestingUser)
    }
}

final case class ResourceUtilV2Live(triplestore: TriplestoreService, sipiService: SipiService)
    extends ResourceUtilV2
    with LazyLogging {

  /**
   * Checks that a user has the specified permission on a resource.
   *
   * @param resourceInfo     the resource to be updated.
   * @param permissionNeeded the necessary Permission.ObjectAccess,
   * @param requestingUser   the requesting user.
   */
  override def checkResourcePermission(
    resourceInfo: ReadResourceV2,
    permissionNeeded: Permission.ObjectAccess,
    requestingUser: User,
  ): IO[ForbiddenException, Unit] = {
    val maybeUserPermission: Option[Permission.ObjectAccess] = PermissionUtilADM.getUserPermissionADM(
      entityCreator = resourceInfo.attachedToUser,
      entityProject = resourceInfo.projectADM.id,
      entityPermissionLiteral = resourceInfo.permissions,
      requestingUser = requestingUser,
    )

    val hasRequiredPermission: Boolean = maybeUserPermission.exists(_ >= permissionNeeded)

    ZIO
      .fail(
        ForbiddenException(
          s"User ${requestingUser.email} does not have ${permissionNeeded.token} on resource <${resourceInfo.resourceIri}>",
        ),
      )
      .when(!hasRequiredPermission)
      .unit
  }

  /**
   * Checks that a user has the specified permission on a value.
   *
   * @param resourceInfo     the resource containing the value.
   * @param valueInfo        the value to be updated.
   * @param permissionNeeded the necessary Permission.ObjectAccess,
   * @param requestingUser   the requesting user.
   */
  override def checkValuePermission(
    resourceInfo: ReadResourceV2,
    valueInfo: ReadValueV2,
    permissionNeeded: Permission.ObjectAccess,
    requestingUser: User,
  ): IO[ForbiddenException, Unit] = {
    val maybeUserPermission: Option[Permission.ObjectAccess] = PermissionUtilADM.getUserPermissionADM(
      entityCreator = valueInfo.attachedToUser,
      entityProject = resourceInfo.projectADM.id,
      entityPermissionLiteral = valueInfo.permissions,
      requestingUser = requestingUser,
    )

    val hasRequiredPermission: Boolean = maybeUserPermission.exists(_ >= permissionNeeded)

    ZIO
      .fail(
        ForbiddenException(
          s"User ${requestingUser.email} does not have ${permissionNeeded.token} on value <${valueInfo.valueIri}>",
        ),
      )
      .when(!hasRequiredPermission)
      .unit
  }

  /**
   * Checks whether a list node exists and if is a root node.
   *
   * @param nodeIri the IRI of the list node.
   * @return [[Task]] of Either None for nonexistent, true for root and false for child node.
   */
  override def checkListNodeExistsAndIsRootNode(nodeIri: IRI): Task[Either[Option[Nothing], Boolean]] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val query = Construct(sparql.admin.txt.getListNode(nodeIri))
    for {
      statements <- triplestore.query(query).flatMap(_.asExtended).map(_.statements)
      maybeList =
        if (statements.nonEmpty) {
          val propToCheck: SmartIri = stringFormatter.toSmartIri(OntologyConstants.KnoraBase.IsRootNode)
          val isRootNode: Boolean   = statements.map(_._2.contains(propToCheck)).head
          Right(isRootNode)
        } else {
          Left(None)
        }

    } yield maybeList
  }

  /**
   * Given a future representing an operation that was supposed to update a value in a triplestore, checks whether
   * the updated value was a file value. If not, this method returns the same future. If it was a file value, this
   * method checks whether the update was successful. If so, it asks Sipi to move the file to permanent storage.
   * If not, it asks Sipi to delete the temporary file.
   *
   * @param updateTask     the Task that should have updated the triplestore.
   * @param valueContents: Seq[FileValueContentV2],   the value that should have been created or updated.
   * @param requestingUser the user making the request.
   */
  override def doSipiPostUpdate[T <: UpdateResultInProject](
    updateTask: Task[T],
    valueContents: Seq[FileValueContentV2],
    requestingUser: User,
  ): Task[T] = {
    val temporaryFiles = valueContents.filterNot(_.is[StillImageExternalFileValueContentV2])
    updateTask.foldZIO(
      (e: Throwable) => {
        ZIO
          .foreachDiscard(temporaryFiles) { file =>
            sipiService
              .deleteTemporaryFile(DeleteTemporaryFileRequest(file.fileValue.internalFilename, requestingUser))
              .logError
          }
          .ignore *> ZIO.fail(e)
      },
      (updateInProject: T) => {
        ZIO
          .foreachDiscard(temporaryFiles) { file =>
            sipiService.moveTemporaryFileToPermanentStorage(
              MoveTemporaryFileToPermanentStorageRequest(
                file.fileValue.internalFilename,
                updateInProject.projectADM.shortcode,
                requestingUser,
              ),
            )
          }
          .as(updateInProject)
      },
    )
  }
}

object ResourceUtilV2Live {
  val layer = ZLayer.derive[ResourceUtilV2Live]
}
