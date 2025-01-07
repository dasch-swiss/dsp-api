/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourceV2
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
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
}

object ResourceUtilV2Live {
  val layer = ZLayer.derive[ResourceUtilV2Live]
}
