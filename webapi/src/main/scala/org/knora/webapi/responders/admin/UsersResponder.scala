/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import com.typesafe.scalalogging.LazyLogging
import zio.Chunk
import zio.RIO
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer

import java.util.UUID

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.messages.admin.responder.usersmessages.UserOperationResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceGetUserByEmailADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceGetUserByIriADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceGetUserByUsernameADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServicePutUserADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceRemoveValues
import org.knora.webapi.messages.util.KnoraSystemInstances.Users
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.BasicUserInformationChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.PasswordChangeRequest
import org.knora.webapi.slice.admin.api.UsersEndpoints.Requests.UserCreateRequest
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

  // The IRI used to lock user creation and update
  private val USERS_GLOBAL_LOCK_IRI = "http://rdfh.ch/users"

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
   * ~ CACHED ~
   * Gets information about a Knora user, and returns it as a [[User]].
   * If possible, tries to retrieve it from the cache. If not, it retrieves
   * it from the triplestore, and then writes it to the cache. Writes to the
   * cache are always `UserInformationTypeADM.FULL`.
   *
   * @param identifier          the IRI of the user.
   * @param userInformationType the type of the requested profile (restricted
   *                            of full).
   * @param requestingUser      the user initiating the request.
   * @param skipCache           the flag denotes to skip the cache and instead
   *                            get data from the triplestore
   * @return a [[User]] describing the user.
   */
  def findUserByIri(
    identifier: UserIri,
    userInformationType: UserInformationTypeADM,
    requestingUser: User,
    skipCache: Boolean = false
  ): Task[Option[User]] =
    for {
      maybeUserADM <- if (skipCache) userService.findUserByIri(identifier)
                      else getUserFromCacheOrTriplestoreByIri(identifier)
    } yield maybeUserADM.map(filterUserInformation(_, requestingUser, userInformationType))

  /**
   * If the requesting user is a system admin, or is requesting themselves, or is a system user,
   * returns the user in the requested format. Otherwise, returns only public information.
   * @param user           the user to be returned
   * @param requestingUser the user requesting the information
   * @param infoType       the type of information requested
   * @return
   */
  private def filterUserInformation(user: User, requestingUser: User, infoType: UserInformationTypeADM): User =
    if (requestingUser.permissions.isSystemAdmin || requestingUser.id == user.id || requestingUser.isSystemUser)
      user.ofType(infoType)
    else user.ofType(UserInformationTypeADM.Public)

  /**
   * ~ CACHED ~
   * Gets information about a Knora user, and returns it as a [[User]].
   * If possible, tries to retrieve it from the cache. If not, it retrieves
   * it from the triplestore, and then writes it to the cache. Writes to the
   * cache are always `UserInformationTypeADM.FULL`.
   *
   * @param email                the email of the user.
   * @param userInformationType  the type of the requested profile (restricted
   *                             of full).
   * @param requestingUser       the user initiating the request.
   * @param skipCache            the flag denotes to skip the cache and instead
   *                             get data from the triplestore
   * @return a [[User]] describing the user.
   */
  def findUserByEmail(
    email: Email,
    userInformationType: UserInformationTypeADM,
    requestingUser: User,
    skipCache: Boolean = false
  ): Task[Option[User]] =
    for {
      maybeUserADM <- if (skipCache) userService.findUserByEmail(email)
                      else getUserFromCacheOrTriplestoreByEmail(email)
    } yield maybeUserADM.map(filterUserInformation(_, requestingUser, userInformationType))

  /**
   * ~ CACHED ~
   * Gets information about a Knora user, and returns it as a [[User]].
   * If possible, tries to retrieve it from the cache. If not, it retrieves
   * it from the triplestore, and then writes it to the cache. Writes to the
   * cache are always `UserInformationTypeADM.FULL`.
   *
   * @param username             the username of the user.
   * @param userInformationType  the type of the requested profile (restricted
   *                             of full).
   * @param requestingUser       the user initiating the request.
   * @param skipCache            the flag denotes to skip the cache and instead
   *                             get data from the triplestore
   * @return a [[User]] describing the user.
   */
  def findUserByUsername(
    username: Username,
    userInformationType: UserInformationTypeADM,
    requestingUser: User,
    skipCache: Boolean = false
  ): Task[Option[User]] =
    for {
      maybeUserADM <-
        if (skipCache) userService.findUserByUsername(username)
        else getUserFromCacheOrTriplestoreByUsername(username)
    } yield maybeUserADM.map(filterUserInformation(_, requestingUser, userInformationType))

  /**
   * Updates an existing user. Only basic user data information (username, email, givenName, familyName, lang)
   * can be changed. For changing the password or user status, use the separate methods.
   *
   * @param userIri              the IRI of the existing user that we want to update.
   * @param changeRequest        the updated information stored as [[BasicUserInformationChangeRequest]].
   *
   * @param apiRequestID         the unique api request ID.
   * @return a future containing a [[UserOperationResponseADM]].
   *               with a [[BadRequestException]] if the necessary parameters are not supplied.
   *               with a [[ForbiddenException]]  if the user doesn't hold the necessary permission for the operation.
   */
  def changeBasicUserInformationADM(
    userIri: UserIri,
    changeRequest: BasicUserInformationChangeRequest,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {
    val updateTask =
      for {
        _ <- userRepo.findById(userIri).someOrFail(NotFoundException(s"User with IRI $userIri not found"))
        _ <- ZIO.foreachDiscard(changeRequest.email)(ensureEmailDoesNotExist)
        _ <- ZIO.foreachDiscard(changeRequest.username)(ensureUsernameDoesNotExist)
        theChange = UserChangeRequest(
                      username = changeRequest.username,
                      email = changeRequest.email,
                      givenName = changeRequest.givenName,
                      familyName = changeRequest.familyName,
                      lang = changeRequest.lang
                    )
        result <- updateUserADM(userIri, theChange)
      } yield result

    IriLocker.runWithIriLock(apiRequestID, USERS_GLOBAL_LOCK_IRI, updateTask)
  }

  private def ensureEmailDoesNotExist(email: Email) =
    ZIO.whenZIO(userRepo.existsByEmail(email))(
      ZIO.fail(DuplicateValueException(s"User with the email '${email.value}' already exists"))
    )

  private def ensureUsernameDoesNotExist(username: Username) =
    ZIO.whenZIO(userRepo.existsByUsername(username))(
      ZIO.fail(DuplicateValueException(s"User with the username '${username.value}' already exists"))
    )

  /**
   * Change the users password. The old password needs to be supplied for security purposes.
   *
   * @param userIri              the IRI of the existing user that we want to update.
   * @param changeRequest    the current password of the requesting user and the new password.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a future containing a [[UserOperationResponseADM]].
   *         fails with a [[BadRequestException]] if necessary parameters are not supplied.
   *         fails with a [[ForbiddenException]] if the user doesn't hold the necessary permission for the operation.
   *         fails with a [[ForbiddenException]] if the supplied old password doesn't match with the user's current password.
   *         fails with a [[NotFoundException]] if the user is not found.
   */
  def changePassword(
    userIri: UserIri,
    changeRequest: PasswordChangeRequest,
    requestingUser: User,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {
    val updateTask =
      for {
        _ <- // check if supplied password matches requesting user's password
          ZIO
            .fromOption(requestingUser.password)
            .map(PasswordHash.unsafeFrom)
            .mapBoth(
              _ => ForbiddenException("The requesting user has no password."),
              pwHash => passwordService.matches(changeRequest.requesterPassword, pwHash)
            )
            .filterOrFail(identity)(
              ForbiddenException("The supplied password does not match the requesting user's password.")
            )

        newPasswordHash = passwordService.hashPassword(changeRequest.newPassword)
        theChange       = UserChangeRequest(passwordHash = Some(newPasswordHash))
        result         <- updateUserADM(userIri, theChange)
      } yield result

    IriLocker.runWithIriLock(apiRequestID, userIri.value, updateTask)
  }

  /**
   * Change the user's status (active / inactive).
   *
   * @param userIri        the IRI of the existing user that we want to update.
   * @param status         the new status.
   * @param apiRequestID   the unique api request ID.
   * @return a task containing a [[UserOperationResponseADM]].
   *         fails with a [[BadRequestException]] if necessary parameters are not supplied.
   *         fails with a [[ForbiddenException]] if the requestingUser doesn't hold the necessary permission for the operation.
   */
  def changeUserStatus(userIri: UserIri, status: UserStatus, apiRequestID: UUID): Task[UserOperationResponseADM] = {
    val updateTask = updateUserADM(userIri, UserChangeRequest(status = Some(status)))
    IriLocker.runWithIriLock(apiRequestID, userIri.value, updateTask)
  }

  /**
   * Change the user's system admin membership status (active / inactive).
   *
   * @param userIri              the IRI of the existing user that we want to update.
   * @param systemAdmin    the new status.
   *
   * @param apiRequestId         the unique api request ID.
   * @return a future containing a [[UserOperationResponseADM]].
   *         fails with a [[BadRequestException]] if necessary parameters are not supplied.
   *         fails with a [[ForbiddenException]] if the user doesn't hold the necessary permission for the operation.
   */
  def changeSystemAdmin(
    userIri: UserIri,
    systemAdmin: SystemAdmin,
    apiRequestId: UUID
  ): Task[UserOperationResponseADM] = {
    val updateTask = updateUserADM(userIri, UserChangeRequest(systemAdmin = Some(systemAdmin)))
    IriLocker.runWithIriLock(apiRequestId, userIri.value, updateTask)
  }

  /**
   * Returns user's project memberships as a sequence of [[ProjectADM]].
   *
   * @param userIri        the IRI of the user.
   * @return a sequence of [[ProjectADM]]
   */
  private def userProjectMembershipsGetADM(userIri: IRI) =
    findUserByIri(
      UserIri.unsafeFrom(userIri),
      UserInformationTypeADM.Full,
      Users.SystemUser
    ).map(_.map(_.projects).getOrElse(Seq.empty))

  /**
   * Returns the user's project memberships as [[UserProjectMembershipsGetResponseADM]].
   *
   * @param userIri        the user's IRI.
   * @return a [[UserProjectMembershipsGetResponseADM]].
   */
  def findProjectMemberShipsByIri(userIri: UserIri): Task[UserProjectMembershipsGetResponseADM] =
    for {
      _ <-
        ZIO.whenZIO(userRepo.existsById(userIri).negate)(
          ZIO.fail(BadRequestException(s"User $userIri does not exist."))
        )
      projects <- userProjectMembershipsGetADM(userIri.value)
    } yield UserProjectMembershipsGetResponseADM(projects)

  /**
   * Adds a user to a project.
   *
   * @param userIri              the user's IRI.
   * @param projectIri           the project's IRI.
   * @param apiRequestID         the unique api request ID.
   * @return
   */
  def addProjectToUserIsInProject(
    userIri: UserIri,
    projectIri: ProjectIri,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {
    val updateTask =
      for {
        kUser             <- userRepo.findById(userIri).someOrFail(NotFoundException(s"The user $userIri does not exist."))
        currentIsInProject = kUser.isInProject
        _ <- ZIO.when(currentIsInProject.contains(projectIri))(
               ZIO.fail(BadRequestException(s"User ${userIri.value} is already member of project ${projectIri.value}."))
             )
        newIsInProject    = currentIsInProject :+ projectIri
        theChange         = UserChangeRequest(projects = Some(newIsInProject))
        updateUserResult <- updateUserADM(userIri, theChange)
      } yield updateUserResult
    IriLocker.runWithIriLock(apiRequestID, userIri.value, updateTask)
  }

  /**
   * Removes a project from the user's projects.
   * If the project is not in the user's projects, a BadRequestException is returned.
   * If the project is in the user's admin projects, it is removed.
   *
   * @param userIri              the user's IRI.
   * @param projectIri           the project's IRI.
   * @param apiRequestID         the unique api request ID.
   * @return
   */
  def removeProjectFromUserIsInProjectAndIsInProjectAdminGroup(
    userIri: UserIri,
    projectIri: ProjectIri,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {
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
   * Returns the user's project admin group memberships, where the result contains the IRIs of the projects the user
   * is a member of the project admin group.
   *
   * @param userIri              the user's IRI.
   * @return a [[UserProjectAdminMembershipsGetResponseADM]].
   */
  def findUserProjectAdminMemberships(userIri: UserIri): Task[UserProjectAdminMembershipsGetResponseADM] =
    ZIO.whenZIO(userRepo.existsById(userIri).negate)(
      ZIO.fail(BadRequestException(s"User ${userIri.value} does not exist."))
    ) *> (for {
      kUser <- userRepo
                 .findById(userIri)
                 .someOrFail(NotFoundException(s"The user $userIri does not exist."))
      requests  = kUser.isInProjectAdminGroup.map(IriIdentifier.from).map(ProjectGetADM.apply)
      projects <- ZIO.foreach(requests)(messageRelay.ask[Option[ProjectADM]](_))
    } yield projects.flatten).map(UserProjectAdminMembershipsGetResponseADM)

  /**
   * Adds a user to the project admin group of a project.
   *
   * @param userIri              the user's IRI.
   * @param projectIri           the project's IRI.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]].
   */
  def addProjectToUserIsInProjectAdminGroup(
    userIri: UserIri,
    projectIri: ProjectIri,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {
    val updateTask =
      for {
        kUser             <- userRepo.findById(userIri).someOrFail(NotFoundException(s"The user $userIri does not exist."))
        currentIsInProject = kUser.isInProject
        _ <-
          ZIO.when(!currentIsInProject.contains(projectIri))(
            ZIO.fail(
              BadRequestException(
                s"User ${userIri.value} is not a member of project ${projectIri.value}. A user needs to be a member of the project to be added as project admin."
              )
            )
          )
        currentIsInProjectAdminGroup = kUser.isInProjectAdminGroup
        _ <- ZIO.when(currentIsInProjectAdminGroup.contains(projectIri))(
               ZIO.fail(BadRequestException(s"User $userIri is already a project admin for project $projectIri."))
             )
        newIsInProjectAdminGroup = currentIsInProjectAdminGroup :+ projectIri
        theChange                = UserChangeRequest(projectsAdmin = Some(newIsInProjectAdminGroup))
        updateUserResult        <- updateUserADM(userIri, theChange)
      } yield updateUserResult
    IriLocker.runWithIriLock(apiRequestID, userIri.value, updateTask)
  }

  /**
   * Removes a user from project admin group of a project.
   *
   * @param userIri              the user's IRI.
   * @param projectIri           the project's IRI.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]]
   */
  def removeProjectFromUserIsInProjectAdminGroup(
    userIri: UserIri,
    projectIri: ProjectIri,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {
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
   * Returns the user's group memberships as a sequence of [[GroupADM]]
   *
   * @param userIri              the IRI of the user.
   * @return a sequence of [[GroupADM]].
   */
  def findGroupMembershipsByIri(userIri: UserIri): Task[Seq[GroupADM]] =
    findUserByIri(userIri, UserInformationTypeADM.Full, Users.SystemUser)
      .map(_.map(_.groups).getOrElse(Seq.empty))

  /**
   * Adds a user to a group.
   *
   * @param userIri              the user's IRI.
   * @param groupIri             the group IRI.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]].
   */
  def addGroupToUserIsInGroup(
    userIri: UserIri,
    groupIri: GroupIri,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {
    val updateTask =
      for {
        kUser           <- userRepo.findById(userIri).someOrFail(NotFoundException(s"The user $userIri does not exist."))
        currentIsInGroup = kUser.isInGroup
        _ <- ZIO.when(currentIsInGroup.contains(groupIri))(
               ZIO.fail(BadRequestException(s"User $userIri is already member of group $groupIri."))
             )
        theChange = UserChangeRequest(groups = Some(currentIsInGroup :+ groupIri))
        result   <- updateUserADM(userIri, theChange)
      } yield result
    IriLocker.runWithIriLock(apiRequestID, userIri.value, updateTask)
  }

  /**
   * Removes a user from a group.
   *
   * @param userIri              the user's IRI.
   * @param groupIri             the group IRI.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]].
   */
  def removeGroupFromUserIsInGroup(
    userIri: UserIri,
    groupIri: GroupIri,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {
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
   * @param userIri              the IRI of the existing user that we want to update.
   * @param req    the updated information.
   * @return a [[UserOperationResponseADM]].
   *         fails with a BadRequestException         if necessary parameters are not supplied.
   *         fails with a UpdateNotPerformedException if the update was not performed.
   */
  private def updateUserADM(userIri: UserIri, req: UserChangeRequest): ZIO[Any, Throwable, UserOperationResponseADM] =
    for {
      _ <- ensureNotABuiltInUser(userIri)
      currentUser <- userRepo
                       .findById(userIri)
                       .someOrFail(NotFoundException(s"User '$userIri' not found."))
      _ <- userService.updateUser(currentUser, req)
      _ <- messageRelay.ask[Unit](
             CacheServiceRemoveValues(Set(currentUser.id.value, currentUser.email.value, currentUser.username.value))
           )
      updatedUserADM <-
        findUserByIri(userIri, UserInformationTypeADM.Full, Users.SystemUser, skipCache = true)
          .someOrFail(UpdateNotPerformedException("User was not updated. Please report this as a possible bug."))
      _ <- messageRelay.ask[Unit](CacheServicePutUserADM(updatedUserADM))
    } yield UserOperationResponseADM(updatedUserADM.ofType(UserInformationTypeADM.Restricted))

  /**
   * Creates a new user. Self-registration is allowed, so even the default user, i.e. with no credentials supplied,
   * is allowed to create a new user.
   *
   * Referenced Websites:
   *                     - https://crackstation.net/hashing-security.htm
   *                     - http://blog.ircmaxell.com/2012/12/seven-ways-to-screw-up-bcrypt.html
   *
   * @param req    a [[UserCreateRequest]] object containing information about the new user to be created.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]].
   */
  def createNewUserADM(req: UserCreateRequest, apiRequestID: UUID): Task[UserOperationResponseADM] = {
    val createNewUserTask =
      for {
        _ <- ensureUsernameDoesNotExist(req.username)
        _ <- ensureEmailDoesNotExist(req.email)

        // check the custom IRI; if not given, create an unused IRI
        customUserIri <- ZIO.foreach(req.id.map(_.value))(iriConverter.asSmartIri)
        userIri <- iriService
                     .checkOrCreateEntityIri(customUserIri, UserIri.makeNew.value)
                     .flatMap(iri =>
                       ZIO.fromEither(UserIri.from(iri)).orElseFail(BadRequestException(s"Invalid User IRI: $iri"))
                     )

        // Create the new user.
        passwordHash = passwordService.hashPassword(req.password)
        newUser = KnoraUser(
                    userIri,
                    req.username,
                    req.email,
                    familyName = req.familyName,
                    req.givenName,
                    passwordHash,
                    preferredLanguage = req.lang,
                    req.status,
                    Chunk.empty,
                    Chunk.empty,
                    req.systemAdmin,
                    Chunk.empty
                  )
        _ <- userRepo.save(newUser)

        // try to retrieve newly created user (will also add to cache)
        createdUser <-
          findUserByIri(userIri, UserInformationTypeADM.Full, Users.SystemUser, skipCache = true).someOrFail {
            val msg = s"User ${userIri.value} was not created. Please report this as a possible bug."
            UpdateNotPerformedException(msg)
          }
      } yield UserOperationResponseADM(createdUser.ofType(UserInformationTypeADM.Restricted))

    IriLocker.runWithIriLock(apiRequestID, USERS_GLOBAL_LOCK_IRI, createNewUserTask)
  }

  /**
   * Tries to retrieve a [[User]] either from triplestore or cache if caching is enabled.
   * If user is not found in cache but in triplestore, then user is written to cache.
   */
  private def getUserFromCacheOrTriplestoreByIri(
    userIri: UserIri
  ): Task[Option[User]] =
    if (appConfig.cacheService.enabled) {
      // caching enabled
      messageRelay.ask[Option[User]](CacheServiceGetUserByIriADM(userIri)).flatMap {
        case None =>
          // none found in cache. getting from triplestore.
          userService.findUserByIri(userIri).flatMap {
            case None =>
              // also none found in triplestore. finally returning none.
              logger.debug("getUserFromCacheOrTriplestore - not found in cache and in triplestore")
              ZIO.none
            case Some(user) =>
              // found a user in the triplestore. need to write to cache.
              logger.debug(
                "getUserFromCacheOrTriplestore - not found in cache but found in triplestore. need to write to cache."
              )
              // writing user to cache and afterwards returning the user found in the triplestore
              messageRelay.ask[Unit](CacheServicePutUserADM(user)).as(Some(user))
          }
        case Some(user) =>
          logger.debug("getUserFromCacheOrTriplestore - found in cache. returning user.")
          ZIO.some(user)
      }
    } else {
      // caching disabled
      logger.debug("getUserFromCacheOrTriplestore - caching disabled. getting from triplestore.")
      userService.findUserByIri(userIri)
    }

  /**
   * Tries to retrieve a [[User]] either from triplestore or cache if caching is enabled.
   * If user is not found in cache but in triplestore, then user is written to cache.
   */
  private def getUserFromCacheOrTriplestoreByUsername(
    username: Username
  ): Task[Option[User]] =
    if (appConfig.cacheService.enabled) {
      // caching enabled
      messageRelay.ask[Option[User]](CacheServiceGetUserByUsernameADM(username)).flatMap {
        case None =>
          // none found in cache. getting from triplestore.
          userService.findUserByUsername(username).flatMap {
            case None =>
              // also none found in triplestore. finally returning none.
              logger.debug("getUserFromCacheOrTriplestore - not found in cache and in triplestore")
              ZIO.none
            case Some(user) =>
              // found a user in the triplestore. need to write to cache.
              logger.debug(
                "getUserFromCacheOrTriplestore - not found in cache but found in triplestore. need to write to cache."
              )
              // writing user to cache and afterwards returning the user found in the triplestore
              messageRelay.ask[Unit](CacheServicePutUserADM(user)).as(Some(user))
          }
        case Some(user) =>
          logger.debug("getUserFromCacheOrTriplestore - found in cache. returning user.")
          ZIO.some(user)
      }
    } else {
      // caching disabled
      logger.debug("getUserFromCacheOrTriplestore - caching disabled. getting from triplestore.")
      userService.findUserByUsername(username)
    }

  /**
   * Tries to retrieve a [[User]] either from triplestore or cache if caching is enabled.
   * If user is not found in cache but in triplestore, then user is written to cache.
   */
  private def getUserFromCacheOrTriplestoreByEmail(
    email: Email
  ): Task[Option[User]] =
    if (appConfig.cacheService.enabled) {
      // caching enabled
      messageRelay.ask[Option[User]](CacheServiceGetUserByEmailADM(email)).flatMap {
        case None =>
          // none found in cache. getting from triplestore.
          userService.findUserByEmail(email).flatMap {
            case None =>
              // also none found in triplestore. finally returning none.
              logger.debug("getUserFromCacheOrTriplestore - not found in cache and in triplestore")
              ZIO.none
            case Some(user) =>
              // found a user in the triplestore. need to write to cache.
              logger.debug(
                "getUserFromCacheOrTriplestore - not found in cache but found in triplestore. need to write to cache."
              )
              // writing user to cache and afterwards returning the user found in the triplestore
              messageRelay.ask[Unit](CacheServicePutUserADM(user)).as(Some(user))
          }
        case Some(user) =>
          logger.debug("getUserFromCacheOrTriplestore - found in cache. returning user.")
          ZIO.some(user)
      }
    } else {
      // caching disabled
      logger.debug("getUserFromCacheOrTriplestore - caching disabled. getting from triplestore.")
      userService.findUserByEmail(email)
    }
}

object UsersResponder {
  def changeUserStatus(
    userIri: UserIri,
    status: UserStatus,
    apiRequestID: UUID
  ): ZIO[UsersResponder, Throwable, UserOperationResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](_.changeUserStatus(userIri, status, apiRequestID))

  def changeSystemAdmin(
    userIri: UserIri,
    systemAdmin: SystemAdmin,
    apiRequestId: UUID
  ): ZIO[UsersResponder, Throwable, UserOperationResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](_.changeSystemAdmin(userIri, systemAdmin, apiRequestId))

  def findUserByIri(
    identifier: UserIri,
    userInformationType: UserInformationTypeADM,
    requestingUser: User,
    skipCache: Boolean = false
  ): ZIO[UsersResponder, Throwable, Option[User]] =
    ZIO.serviceWithZIO[UsersResponder](_.findUserByIri(identifier, userInformationType, requestingUser, skipCache))

  def findUserByEmail(
    email: Email,
    userInformationType: UserInformationTypeADM,
    requestingUser: User,
    skipCache: Boolean = false
  ): ZIO[UsersResponder, Throwable, Option[User]] =
    ZIO.serviceWithZIO[UsersResponder](_.findUserByEmail(email, userInformationType, requestingUser, skipCache))

  def findUserByUsername(
    username: Username,
    userInformationType: UserInformationTypeADM,
    requestingUser: User,
    skipCache: Boolean = false
  ): ZIO[UsersResponder, Throwable, Option[User]] =
    ZIO.serviceWithZIO[UsersResponder](_.findUserByUsername(username, userInformationType, requestingUser, skipCache))

  def findProjectMemberShipsByIri(
    userIri: UserIri
  ): ZIO[UsersResponder, Throwable, UserProjectMembershipsGetResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](_.findProjectMemberShipsByIri(userIri))

  def addProjectToUserIsInProject(
    userIri: UserIri,
    projectIri: ProjectIri,
    apiRequestID: UUID
  ): ZIO[UsersResponder, Throwable, UserOperationResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](_.addProjectToUserIsInProject(userIri, projectIri, apiRequestID))

  def addProjectToUserIsInProjectAdminGroup(
    userIri: UserIri,
    projectIri: ProjectIri,
    apiRequestID: UUID
  ): ZIO[UsersResponder, Throwable, UserOperationResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](
      _.addProjectToUserIsInProjectAdminGroup(userIri, projectIri, apiRequestID)
    )

  def removeProjectFromUserIsInProjectAndIsInProjectAdminGroup(
    userIri: UserIri,
    projectIri: ProjectIri,
    apiRequestID: UUID
  ): ZIO[UsersResponder, Throwable, UserOperationResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](
      _.removeProjectFromUserIsInProjectAndIsInProjectAdminGroup(userIri, projectIri, apiRequestID)
    )

  def removeProjectFromUserIsInProjectAdminGroup(
    userIri: UserIri,
    projectIri: ProjectIri,
    apiRequestID: UUID
  ): ZIO[UsersResponder, Throwable, UserOperationResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](
      _.removeProjectFromUserIsInProjectAdminGroup(userIri, projectIri, apiRequestID)
    )

  def findUserProjectAdminMemberships(
    userIri: UserIri
  ): ZIO[UsersResponder, Throwable, UserProjectAdminMembershipsGetResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](_.findUserProjectAdminMemberships(userIri))

  def findGroupMembershipsByIri(userIri: UserIri): ZIO[UsersResponder, Throwable, Seq[GroupADM]] =
    ZIO.serviceWithZIO[UsersResponder](_.findGroupMembershipsByIri(userIri))

  def createNewUserADM(
    req: UserCreateRequest,
    apiRequestID: UUID
  ): ZIO[UsersResponder, Throwable, UserOperationResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](_.createNewUserADM(req, apiRequestID))

  def changeBasicUserInformationADM(
    userIri: UserIri,
    changeRequest: BasicUserInformationChangeRequest,
    apiRequestID: UUID
  ): ZIO[UsersResponder, Throwable, UserOperationResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](_.changeBasicUserInformationADM(userIri, changeRequest, apiRequestID))

  def changePassword(
    userIri: UserIri,
    changeRequest: PasswordChangeRequest,
    requestingUser: User,
    apiRequestID: UUID
  ): RIO[UsersResponder, UserOperationResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](_.changePassword(userIri, changeRequest, requestingUser, apiRequestID))

  def addGroupToUserIsInGroup(
    userIri: UserIri,
    groupIri: GroupIri,
    apiRequestID: UUID
  ): ZIO[UsersResponder, Throwable, UserOperationResponseADM] =
    ZIO.serviceWithZIO[UsersResponder](_.addGroupToUserIsInGroup(userIri, groupIri, apiRequestID))

  def removeGroupFromUserIsInGroup(
    userIri: UserIri,
    groupIri: GroupIri,
    apiRequestID: UUID
  ): ZIO[UsersResponder, Throwable, UserOperationResponseADM] =
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
