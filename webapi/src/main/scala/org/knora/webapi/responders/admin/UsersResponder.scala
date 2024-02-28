/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import com.typesafe.scalalogging.LazyLogging
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer

import java.util.UUID

import dsp.errors.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.UserChangeRequest
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class UsersResponder(
  appConfig: AppConfig,
  iriService: IriService,
  iriConverter: IriConverter,
  userService: UserService,
  userRepo: KnoraUserRepo,
  passwordService: PasswordService,
  messageRelay: MessageRelay,
  implicit val stringFormatter: StringFormatter
) extends MessageHandler
    with LazyLogging {

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[UsersResponderRequestADM]

  /**
   * Receives a message extending [[UsersResponderRequestADM]], and returns an appropriate message.
   */
  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case UserGetByIriADM(identifier, userInformationTypeADM, requestingUser) =>
      findUserByIri(identifier, userInformationTypeADM, requestingUser)
    case UserGroupMembershipRemoveRequestADM(userIri, projectIri, apiRequestID) =>
      removeGroupFromUserIsInGroup(userIri, projectIri, apiRequestID)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Gets information about a Knora user, and returns it as a [[User]].
   *
   * @param identifier          the IRI of the user.
   * @param userInformationType the type of the requested profile (restricted
   *                            of full).
   * @param requestingUser      the user initiating the request.
   * @return a [[User]] describing the user.
   */
  def findUserByIri(
    identifier: UserIri,
    userInformationType: UserInformationType,
    requestingUser: User
  ): Task[Option[User]] =
    userService.findUserByIri(identifier).map(_.map(_.filterUserInformation(requestingUser, userInformationType)))

  /**
   * Removes a project from the user's projects.
   * If the project is not in the user's projects, a BadRequestException is returned.
   * If the project is in the user's admin projects, it is removed.
   *
   * @param userIri      the user's IRI.
   * @param projectIri   the project's IRI.
   * @param apiRequestID the unique api request ID.
   * @return
   */
  def removeProjectFromUserIsInProjectAndIsInProjectAdminGroup(
    userIri: UserIri,
    projectIri: ProjectIri,
    apiRequestID: UUID
  ): Task[UserResponseADM] = {
    val updateTask =
      for {
        kUser             <- userRepo.findById(userIri).someOrFail(NotFoundException(s"The user $userIri does not exist."))
        currentIsInProject = kUser.isInProject
        _ <- ZIO.when(!currentIsInProject.contains(projectIri))(
               ZIO.fail(BadRequestException(s"User $userIri is not member of project ${projectIri.value}."))
             )
        newIsInProject               = currentIsInProject.filterNot(_ == projectIri)
        currentIsInProjectAdminGroup = kUser.isInProjectAdminGroup
        newIsInProjectAdminGroup     = currentIsInProjectAdminGroup.filterNot(_ == projectIri)
        theChange                    = UserChangeRequest(projects = Some(newIsInProject), projectsAdmin = Some(newIsInProjectAdminGroup))
        updateUserResult            <- updateUserADM(userIri, theChange)
      } yield updateUserResult
    IriLocker.runWithIriLock(apiRequestID, userIri.value, updateTask)
  }

  /**
   * Removes a user from project admin group of a project.
   *
   * @param userIri      the user's IRI.
   * @param projectIri   the project's IRI.
   * @param apiRequestID the unique api request ID.
   * @return a [[UserResponseADM]]
   */
  def removeProjectFromUserIsInProjectAdminGroup(
    userIri: UserIri,
    projectIri: ProjectIri,
    apiRequestID: UUID
  ): Task[UserResponseADM] = {
    val updateTask =
      for {
        kUser                       <- userRepo.findById(userIri).someOrFail(NotFoundException(s"The user $userIri does not exist."))
        currentIsInProjectAdminGroup = kUser.isInProjectAdminGroup
        _ <- ZIO.when(!currentIsInProjectAdminGroup.contains(projectIri))(
               ZIO.fail(BadRequestException(s"User $userIri is not a project admin of project $projectIri."))
             )
        newIsInProjectAdminGroup = currentIsInProjectAdminGroup.filterNot(_ == projectIri)
        theChange                = UserChangeRequest(projectsAdmin = Some(newIsInProjectAdminGroup))
        updateUserResult        <- updateUserADM(userIri, theChange)
      } yield updateUserResult
    IriLocker.runWithIriLock(apiRequestID, userIri.value, updateTask)
  }

  /**
   * Removes a user from a group.
   *
   * @param userIri      the user's IRI.
   * @param groupIri     the group IRI.
   * @param apiRequestID the unique api request ID.
   * @return a [[UserResponseADM]].
   */
  def removeGroupFromUserIsInGroup(
    userIri: UserIri,
    groupIri: GroupIri,
    apiRequestID: UUID
  ): Task[UserResponseADM] = {
    val updateTask =
      for {
        kUser           <- userRepo.findById(userIri).someOrFail(NotFoundException(s"The user $userIri does not exist."))
        currentIsInGroup = kUser.isInGroup
        _ <- ZIO.when(!currentIsInGroup.contains(groupIri))(
               ZIO.fail(BadRequestException(s"User $userIri is not member of group $groupIri."))
             )
        newIsInGroup = currentIsInGroup.filterNot(_ == groupIri)
        theUpdate    = UserChangeRequest(groups = Some(newIsInGroup))
        result      <- updateUserADM(userIri, theUpdate)
      } yield result
    IriLocker.runWithIriLock(apiRequestID, userIri.value, updateTask)
  }

  private def ensureNotABuiltInUser(userIri: UserIri) =
    ZIO.when(userIri.isBuiltInUser)(ZIO.fail(BadRequestException("Changes to built-in users are not allowed.")))

  /**
   * Updates an existing user. Should not be directly used from the receive method.
   *
   * @param userIri   the IRI of the existing user that we want to update.
   * @param theUpdate the updated information.
   * @return a [[UserResponseADM]].
   *         fails with a BadRequestException         if necessary parameters are not supplied.
   *         fails with a UpdateNotPerformedException if the update was not performed.
   */
  private def updateUserADM(
    userIri: UserIri,
    theUpdate: UserChangeRequest
  ): ZIO[Any, Throwable, UserResponseADM] =
    for {
      _           <- ensureNotABuiltInUser(userIri)
      currentUser <- userRepo.findById(userIri).someOrFail(NotFoundException(s"User '$userIri' not found."))
      _           <- userService.updateUser(currentUser, theUpdate)
      updatedUserADM <-
        userService
          .findUserByIri(userIri)
          .someOrFail(UpdateNotPerformedException("User was not updated. Please report this as a possible bug."))
    } yield UserResponseADM(updatedUserADM.ofType(UserInformationType.Restricted))
}

object UsersResponder {
  def findUserByIri(
    identifier: UserIri,
    userInformationType: UserInformationType,
    requestingUser: User
  ): ZIO[UsersResponder, Throwable, Option[User]] =
    ZIO.serviceWithZIO[UsersResponder](_.findUserByIri(identifier, userInformationType, requestingUser))

  def removeProjectFromUserIsInProjectAndIsInProjectAdminGroup(
    userIri: UserIri,
    projectIri: ProjectIri,
    apiRequestID: UUID
  ): ZIO[UsersResponder, Throwable, UserResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](
      _.removeProjectFromUserIsInProjectAndIsInProjectAdminGroup(userIri, projectIri, apiRequestID)
    )

  def removeProjectFromUserIsInProjectAdminGroup(
    userIri: UserIri,
    projectIri: ProjectIri,
    apiRequestID: UUID
  ): ZIO[UsersResponder, Throwable, UserResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](
      _.removeProjectFromUserIsInProjectAdminGroup(userIri, projectIri, apiRequestID)
    )

  def removeGroupFromUserIsInGroup(
    userIri: UserIri,
    groupIri: GroupIri,
    apiRequestID: UUID
  ): ZIO[UsersResponder, Throwable, UserResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](_.removeGroupFromUserIsInGroup(userIri, groupIri, apiRequestID))

  val layer: URLayer[
    AppConfig & IriConverter & IriService & PasswordService & KnoraUserRepo & MessageRelay & UserService & StringFormatter & TriplestoreService,
    UsersResponder
  ] = ZLayer.fromZIO {
    for {
      config  <- ZIO.service[AppConfig]
      iriS    <- ZIO.service[IriService]
      ic      <- ZIO.service[IriConverter]
      us      <- ZIO.service[UserService]
      ur      <- ZIO.service[KnoraUserRepo]
      ps      <- ZIO.service[PasswordService]
      mr      <- ZIO.service[MessageRelay]
      sf      <- ZIO.service[StringFormatter]
      handler <- mr.subscribe(UsersResponder(config, iriS, ic, us, ur, ps, mr, sf))
    } yield handler
  }
}
