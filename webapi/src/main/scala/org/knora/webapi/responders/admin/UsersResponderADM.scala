/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import com.typesafe.scalalogging.LazyLogging
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import zio.*
import zio.macros.accessible

import java.util.UUID

import dsp.errors.*
import dsp.valueobjects.Iri
import dsp.valueobjects.User.*
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupGetADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDataGetADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceGetUserADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServicePutUserADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceRemoveValues
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model
import org.knora.webapi.slice.admin.domain.model.UserADM
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.util.ZioHelper

/**
 * Provides information about Knora users to other responders.
 */
@accessible
trait UsersResponderADM {
  def getAllUserADMRequest(requestingUser: UserADM): Task[UsersGetResponseADM]
}

final case class UsersResponderADMLive(
  appConfig: AppConfig,
  iriService: IriService,
  messageRelay: MessageRelay,
  triplestore: TriplestoreService,
  implicit val stringFormatter: StringFormatter
) extends UsersResponderADM
    with MessageHandler
    with LazyLogging {

  // The IRI used to lock user creation and update
  private val USERS_GLOBAL_LOCK_IRI = "http://rdfh.ch/users"

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[UsersResponderRequestADM]

  /**
   * Receives a message extending [[UsersResponderRequestADM]], and returns an appropriate message.
   */
  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case UsersGetADM(_, requestingUser) => getAllUserADM(requestingUser)
    case UsersGetRequestADM(_, requestingUser) =>
      getAllUserADMRequest(requestingUser)
    case UserGetADM(identifier, userInformationTypeADM, requestingUser) =>
      getSingleUserADM(identifier, userInformationTypeADM, requestingUser)
    case UserGetRequestADM(identifier, userInformationTypeADM, requestingUser) =>
      getSingleUserADMRequest(identifier, userInformationTypeADM, requestingUser)
    case UserCreateRequestADM(userCreatePayloadADM, _, apiRequestID) =>
      createNewUserADM(userCreatePayloadADM, apiRequestID)
    case UserChangeBasicInformationRequestADM(
          userIri,
          userUpdateBasicInformationPayload,
          requestingUser,
          apiRequestID
        ) =>
      changeBasicUserInformationADM(
        userIri,
        userUpdateBasicInformationPayload,
        requestingUser,
        apiRequestID
      )
    case UserChangePasswordRequestADM(
          userIri,
          userUpdatePasswordPayload,
          requestingUser,
          apiRequestID
        ) =>
      changePasswordADM(userIri, userUpdatePasswordPayload, requestingUser, apiRequestID)
    case UserChangeStatusRequestADM(userIri, status, requestingUser, apiRequestID) =>
      changeUserStatusADM(userIri, status, requestingUser, apiRequestID)
    case UserChangeSystemAdminMembershipStatusRequestADM(
          userIri,
          changeSystemAdminMembershipStatusRequest,
          requestingUser,
          apiRequestID
        ) =>
      changeUserSystemAdminMembershipStatusADM(
        userIri,
        changeSystemAdminMembershipStatusRequest,
        requestingUser,
        apiRequestID
      )
    case UserProjectMembershipsGetRequestADM(userIri, _) => userProjectMembershipsGetRequestADM(userIri)
    case UserProjectMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID) =>
      userProjectMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID)
    case UserProjectMembershipRemoveRequestADM(
          userIri,
          projectIri,
          requestingUser,
          apiRequestID
        ) =>
      userProjectMembershipRemoveRequestADM(userIri, projectIri, requestingUser, apiRequestID)
    case UserProjectAdminMembershipsGetRequestADM(userIri, _, _) =>
      userProjectAdminMembershipsGetRequestADM(userIri)
    case UserProjectAdminMembershipAddRequestADM(
          userIri,
          projectIri,
          requestingUser,
          apiRequestID
        ) =>
      userProjectAdminMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID)
    case UserProjectAdminMembershipRemoveRequestADM(
          userIri,
          projectIri,
          requestingUser,
          apiRequestID
        ) =>
      userProjectAdminMembershipRemoveRequestADM(
        userIri,
        projectIri,
        requestingUser,
        apiRequestID
      )
    case UserGroupMembershipsGetRequestADM(userIri, _) =>
      userGroupMembershipsGetADM(userIri).map(UserGroupMembershipsGetResponseADM)
    case UserGroupMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID) =>
      userGroupMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID)
    case UserGroupMembershipRemoveRequestADM(userIri, projectIri, requestingUser, apiRequestID) =>
      userGroupMembershipRemoveRequestADM(userIri, projectIri, requestingUser, apiRequestID)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Gets all the users and returns them as a sequence of [[UserADM]].
   *
   * @param requestingUser       the user initiating the request.
   * @return all the users as a sequence of [[UserADM]].
   */
  private def getAllUserADM(requestingUser: UserADM) =
    for {
      _ <- ZIO.attempt(
             if (
               !requestingUser.permissions.isSystemAdmin && !requestingUser.permissions
                 .isProjectAdminInAnyProject() && !requestingUser.isSystemUser
             ) {
               throw ForbiddenException("ProjectAdmin or SystemAdmin permissions are required.")
             }
           )

      query = Construct(sparql.admin.txt.getUsers(maybeIri = None, maybeUsername = None, maybeEmail = None))

      statements <- triplestore
                      .query(query)
                      .flatMap(_.asExtended)
                      .map(_.statements.toList)

      users: Seq[UserADM] = statements.map { case (userIri: SubjectV2, propsMap: Map[SmartIri, Seq[LiteralV2]]) =>
                              UserADM(
                                id = userIri.toString,
                                username = propsMap
                                  .getOrElse(
                                    OntologyConstants.KnoraAdmin.Username.toSmartIri,
                                    throw InconsistentRepositoryDataException(
                                      s"User: $userIri has no 'username' defined."
                                    )
                                  )
                                  .head
                                  .asInstanceOf[StringLiteralV2]
                                  .value,
                                email = propsMap
                                  .getOrElse(
                                    OntologyConstants.KnoraAdmin.Email.toSmartIri,
                                    throw InconsistentRepositoryDataException(s"User: $userIri has no 'email' defined.")
                                  )
                                  .head
                                  .asInstanceOf[StringLiteralV2]
                                  .value,
                                givenName = propsMap
                                  .getOrElse(
                                    OntologyConstants.KnoraAdmin.GivenName.toSmartIri,
                                    throw InconsistentRepositoryDataException(
                                      s"User: $userIri has no 'givenName' defined."
                                    )
                                  )
                                  .head
                                  .asInstanceOf[StringLiteralV2]
                                  .value,
                                familyName = propsMap
                                  .getOrElse(
                                    OntologyConstants.KnoraAdmin.FamilyName.toSmartIri,
                                    throw InconsistentRepositoryDataException(
                                      s"User: $userIri has no 'familyName' defined."
                                    )
                                  )
                                  .head
                                  .asInstanceOf[StringLiteralV2]
                                  .value,
                                status = propsMap
                                  .getOrElse(
                                    OntologyConstants.KnoraAdmin.StatusProp.toSmartIri,
                                    throw InconsistentRepositoryDataException(
                                      s"User: $userIri has no 'status' defined."
                                    )
                                  )
                                  .head
                                  .asInstanceOf[BooleanLiteralV2]
                                  .value,
                                lang = propsMap
                                  .getOrElse(
                                    OntologyConstants.KnoraAdmin.PreferredLanguage.toSmartIri,
                                    throw InconsistentRepositoryDataException(
                                      s"User: $userIri has no 'preferedLanguage' defined."
                                    )
                                  )
                                  .head
                                  .asInstanceOf[StringLiteralV2]
                                  .value
                              )
                            }

    } yield users.sorted

  /**
   * Gets all the users and returns them as a [[UsersGetResponseADM]].
   *
   * @param requestingUser       the user initiating the request.
   * @return all the users as a [[UsersGetResponseADM]].
   */
  def getAllUserADMRequest(requestingUser: UserADM): Task[UsersGetResponseADM] =
    for {
      maybeUsersListToReturn <- getAllUserADM(requestingUser)

      result = maybeUsersListToReturn match {
                 case users: Seq[UserADM] if users.nonEmpty =>
                   UsersGetResponseADM(users = users)
                 case _ =>
                   throw NotFoundException(s"No users found")
               }
    } yield result

  /**
   * ~ CACHED ~
   * Gets information about a Knora user, and returns it as a [[UserADM]].
   * If possible, tries to retrieve it from the cache. If not, it retrieves
   * it from the triplestore, and then writes it to the cache. Writes to the
   * cache are always `UserInformationTypeADM.FULL`.
   *
   * @param identifier           the IRI, email, or username of the user.
   * @param userInformationType  the type of the requested profile (restricted
   *                             of full).
   *
   * @param requestingUser       the user initiating the request.
   * @param skipCache            the flag denotes to skip the cache and instead
   *                             get data from the triplestore
   * @return a [[UserADM]] describing the user.
   */
  private def getSingleUserADM(
    identifier: UserIdentifierADM,
    userInformationType: UserInformationTypeADM,
    requestingUser: UserADM,
    skipCache: Boolean = false
  ): Task[Option[UserADM]] = {

    logger.debug(
      s"getSingleUserADM - id: {}, type: {}, requester: {}, skipCache: {}",
      identifier.value,
      userInformationType,
      requestingUser.username,
      skipCache
    )

    for {
      maybeUserADM <-
        if (skipCache) {
          // getting directly from triplestore
          getUserFromTriplestore(identifier = identifier)
        } else {
          // getting from cache or triplestore
          getUserFromCacheOrTriplestore(identifier)
        }

      // return the correct amount of information depending on either the request or user permission
      finalResponse: Option[UserADM] =
        if (
          requestingUser.permissions.isSystemAdmin || requestingUser
            .isSelf(identifier) || requestingUser.isSystemUser
        ) {
          // return everything or what was requested
          maybeUserADM.map(user => user.ofType(userInformationType))
        } else {
          // return only public information
          maybeUserADM.map(user => user.ofType(UserInformationTypeADM.Public))
        }

      _ =
        if (finalResponse.nonEmpty) {
          logger.debug("getSingleUserADM - successfully retrieved user: {}", identifier.value)
        } else {
          logger.debug("getSingleUserADM - could not retrieve user: {}", identifier.value)
        }

    } yield finalResponse
  }

  /**
   * Gets information about a Knora user, and returns it as a [[UserResponseADM]].
   *
   * @param identifier          the IRI, username, or email of the user.
   * @param userInformationType the type of the requested profile (restricted of full).
   * @param requestingUser      the user initiating the request.
   * @return a [[UserResponseADM]]
   */
  private def getSingleUserADMRequest(
    identifier: UserIdentifierADM,
    userInformationType: UserInformationTypeADM,
    requestingUser: UserADM
  ): Task[UserResponseADM] =
    for {
      maybeUserADM <- getSingleUserADM(
                        identifier = identifier,
                        userInformationType = userInformationType,
                        requestingUser = requestingUser
                      )

      result = maybeUserADM match {
                 case Some(user) => UserResponseADM(user = user)
                 case None       => throw NotFoundException(s"User '${identifier.value}' not found")
               }
    } yield result

  /**
   * Updates an existing user. Only basic user data information (username, email, givenName, familyName, lang)
   * can be changed. For changing the password or user status, use the separate methods.
   *
   * @param userIri              the IRI of the existing user that we want to update.
   * @param userUpdateBasicInformationPayload    the updated information stored as [[UserUpdateBasicInformationPayloadADM]].
   *
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a future containing a [[UserOperationResponseADM]].
   *               with a [[BadRequestException]] if the necessary parameters are not supplied.
   *               with a [[ForbiddenException]]  if the user doesn't hold the necessary permission for the operation.
   */
  private def changeBasicUserInformationADM(
    userIri: IRI,
    userUpdateBasicInformationPayload: UserUpdateBasicInformationPayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {

    logger.debug(s"changeBasicUserInformationADM: changeUserRequest: {}", userUpdateBasicInformationPayload)

    /**
     * The actual change basic user data task run with an IRI lock.
     */
    def changeBasicUserDataTask(
      userIri: IRI,
      userUpdateBasicInformationPayload: UserUpdateBasicInformationPayloadADM,
      requestingUser: UserADM
    ): Task[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. requesting updates own information or is system admin)
        _ <- ZIO.attempt(
               if (!requestingUser.id.equalsIgnoreCase(userIri) && !requestingUser.permissions.isSystemAdmin) {
                 throw ForbiddenException(
                   "User information can only be changed by the user itself or a system administrator"
                 )
               }
             )

        // get current user information
        currentUserInformation <- getSingleUserADM(
                                    identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                                    userInformationType = UserInformationTypeADM.Full,
                                    requestingUser = KnoraSystemInstances.Users.SystemUser
                                  )

        // check if email is unique in case of a change email request
        emailTaken <-
          userByEmailExists(userUpdateBasicInformationPayload.email, Some(currentUserInformation.get.email))
        _ = if (emailTaken) {
              throw DuplicateValueException(
                s"User with the email '${userUpdateBasicInformationPayload.email.get.value}' already exists"
              )
            }

        // check if username is unique in case of a change username request
        usernameTaken <-
          userByUsernameExists(userUpdateBasicInformationPayload.username, Some(currentUserInformation.get.username))
        _ = if (usernameTaken) {
              throw DuplicateValueException(
                s"User with the username '${userUpdateBasicInformationPayload.username.get.value}' already exists"
              )
            }

        // send change request as SystemUser
        result <- updateUserADM(
                    userIri = userIri,
                    userUpdatePayload = UserChangeRequestADM(
                      username = userUpdateBasicInformationPayload.username,
                      email = userUpdateBasicInformationPayload.email,
                      givenName = userUpdateBasicInformationPayload.givenName,
                      familyName = userUpdateBasicInformationPayload.familyName,
                      lang = userUpdateBasicInformationPayload.lang
                    ),
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                  )
      } yield result

    IriLocker.runWithIriLock(
      apiRequestID,
      USERS_GLOBAL_LOCK_IRI,
      changeBasicUserDataTask(userIri, userUpdateBasicInformationPayload, requestingUser)
    )
  }

  /**
   * Change the users password. The old password needs to be supplied for security purposes.
   *
   * @param userIri              the IRI of the existing user that we want to update.
   * @param userUpdatePasswordPayload    the current password of the requesting user and the new password.
   *
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a future containing a [[UserOperationResponseADM]].
   *         fails with a [[BadRequestException]] if necessary parameters are not supplied.
   *         fails with a [[ForbiddenException]] if the user doesn't hold the necessary permission for the operation.
   *         fails with a [[ForbiddenException]] if the supplied old password doesn't match with the user's current password.
   *         fails with a [[NotFoundException]] if the user is not found.
   */
  private def changePasswordADM(
    userIri: IRI,
    userUpdatePasswordPayload: UserUpdatePasswordPayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {

    /**
     * The actual change password task run with an IRI lock.
     */
    def changePasswordTask(
      userIri: IRI,
      userUpdatePasswordPayload: UserUpdatePasswordPayloadADM,
      requestingUser: UserADM
    ): Task[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. requesting updates own information or is system admin)
        _ <- ZIO.attempt(
               if (!requestingUser.id.equalsIgnoreCase(userIri) && !requestingUser.permissions.isSystemAdmin) {
                 throw ForbiddenException(
                   "User's password can only be changed by the user itself or a system administrator"
                 )
               }
             )

        // check if supplied password matches requesting user's password
        _ = if (!requestingUser.passwordMatch(userUpdatePasswordPayload.requesterPassword.value)) {
              throw ForbiddenException("The supplied password does not match the requesting user's password.")
            }

        // hash the new password
        encoder = new BCryptPasswordEncoder(appConfig.bcryptPasswordStrength)
        newHashedPassword = Password
                              .make(encoder.encode(userUpdatePasswordPayload.newPassword.value))
                              .fold(e => throw e.head, value => value)

        // update the users password as SystemUser
        result <- updateUserPasswordADM(
                    userIri = userIri,
                    password = newHashedPassword,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                  )

      } yield result

    IriLocker.runWithIriLock(
      apiRequestID,
      userIri,
      changePasswordTask(userIri, userUpdatePasswordPayload, requestingUser)
    )
  }

  /**
   * Change the user's status (active / inactive).
   *
   * @param userIri              the IRI of the existing user that we want to update.
   * @param status               the new status.
   *
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a future containing a [[UserOperationResponseADM]].
   *         fails with a [[BadRequestException]] if necessary parameters are not supplied.
   *         fails with a [[ForbiddenException]] if the user doesn't hold the necessary permission for the operation.
   */
  private def changeUserStatusADM(
    userIri: IRI,
    status: UserStatus,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {

    logger.debug(s"changeUserStatusADM - new status: {}", status)

    /**
     * The actual change user status task run with an IRI lock.
     */
    def changeUserStatusTask(
      userIri: IRI,
      status: UserStatus,
      requestingUser: UserADM
    ): Task[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. requesting updates own information or is system admin)
        _ <-
          ZIO.attempt(
            if (!requestingUser.id.equalsIgnoreCase(userIri) && !requestingUser.permissions.isSystemAdmin) {
              throw ForbiddenException("User's status can only be changed by the user itself or a system administrator")
            }
          )

        // create the update request
        result <- updateUserADM(
                    userIri = userIri,
                    userUpdatePayload = UserChangeRequestADM(status = Some(status)),
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                  )

      } yield result

    IriLocker.runWithIriLock(
      apiRequestID,
      userIri,
      changeUserStatusTask(userIri, status, requestingUser)
    )
  }

  /**
   * Change the user's system admin membership status (active / inactive).
   *
   * @param userIri              the IRI of the existing user that we want to update.
   * @param systemAdmin    the new status.
   *
   * @param requestingUser       the user profile of the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a future containing a [[UserOperationResponseADM]].
   *         fails with a [[BadRequestException]] if necessary parameters are not supplied.
   *         fails with a [[ForbiddenException]] if the user doesn't hold the necessary permission for the operation.
   */
  private def changeUserSystemAdminMembershipStatusADM(
    userIri: IRI,
    systemAdmin: SystemAdmin,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {

    /**
     * The actual change user status task run with an IRI lock.
     */
    def changeUserSystemAdminMembershipStatusTask(
      userIri: IRI,
      systemAdmin: SystemAdmin,
      requestingUser: UserADM
    ): Task[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. system admin)
        _ <-
          ZIO.attempt(
            if (!requestingUser.permissions.isSystemAdmin) {
              throw ForbiddenException("User's system admin membership can only be changed by a system administrator")
            }
          )

        // create the update request
        result <- updateUserADM(
                    userIri = userIri,
                    userUpdatePayload = UserChangeRequestADM(systemAdmin = Some(systemAdmin)),
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                  )

      } yield result

    IriLocker.runWithIriLock(
      apiRequestID,
      userIri,
      changeUserSystemAdminMembershipStatusTask(userIri, systemAdmin, requestingUser)
    )
  }

  /**
   * Returns user's project memberships as a sequence of [[ProjectADM]].
   *
   * @param userIri        the IRI of the user.
   * @return a sequence of [[ProjectADM]]
   */
  private def userProjectMembershipsGetADM(userIri: IRI) =
    getSingleUserADM(
      UserIdentifierADM(maybeIri = Some(userIri)),
      UserInformationTypeADM.Full,
      KnoraSystemInstances.Users.SystemUser
    ).map(_.map(_.projects).getOrElse(Seq.empty))

  /**
   * Returns the user's project memberships as [[UserProjectMembershipsGetResponseADM]].
   *
   * @param userIri        the user's IRI.
   * @return a [[UserProjectMembershipsGetResponseADM]].
   */
  private def userProjectMembershipsGetRequestADM(userIri: IRI) =
    for {
      _        <- ZIO.whenZIO(userExists(userIri).negate)(ZIO.fail(BadRequestException(s"User $userIri does not exist.")))
      projects <- userProjectMembershipsGetADM(userIri)
    } yield UserProjectMembershipsGetResponseADM(projects)

  /**
   * Adds a user to a project.
   *
   * @param userIri              the user's IRI.
   * @param projectIri           the project's IRI.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return
   */
  private def userProjectMembershipAddRequestADM(
    userIri: IRI,
    projectIri: IRI,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {

    logger.debug(s"userProjectMembershipAddRequestADM: userIri: {}, projectIri: {}", userIri, projectIri)

    /**
     * The actual task run with an IRI lock.
     */
    def userProjectMembershipAddRequestTask(
      userIri: IRI,
      projectIri: IRI,
      requestingUser: UserADM
    ): Task[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. is project or system admin)
        _ <-
          ZIO.attempt(
            if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
              throw ForbiddenException(
                "User's project membership can only be changed by a project or system administrator"
              )
            }
          )

        // check if user exists
        userExists <- userExists(userIri)
        _           = if (!userExists) throw NotFoundException(s"The user $userIri does not exist.")

        // check if project exists
        projectExists <- projectExists(projectIri)
        _              = if (!projectExists) throw NotFoundException(s"The project $projectIri does not exist.")

        // get users current project membership list
        currentProjectMembershipIris <- userProjectMembershipsGetRequestADM(userIri).map(_.projects.map(_.id))

        // check if user is already member and if not then append to list
        updatedProjectMembershipIris =
          if (!currentProjectMembershipIris.contains(projectIri)) {
            currentProjectMembershipIris :+ projectIri
          } else {
            throw BadRequestException(
              s"User $userIri is already member of project $projectIri."
            )
          }

        // create the update request
        updateUserResult <- updateUserADM(
                              userIri = userIri,
                              userUpdatePayload = UserChangeRequestADM(projects = Some(updatedProjectMembershipIris)),
                              requestingUser = requestingUser
                            )
      } yield updateUserResult

    IriLocker.runWithIriLock(
      apiRequestID,
      userIri,
      userProjectMembershipAddRequestTask(userIri, projectIri, requestingUser)
    )

  }

  /**
   * Removes a user from a project.
   *
   * @param userIri              the user's IRI.
   * @param projectIri           the project's IRI.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return
   */
  private def userProjectMembershipRemoveRequestADM(
    userIri: IRI,
    projectIri: IRI,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def userProjectMembershipRemoveRequestTask(
      userIri: IRI,
      projectIri: IRI,
      requestingUser: UserADM
    ): Task[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. is project or system admin)
        _ <-
          ZIO.attempt(
            if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
              throw ForbiddenException(
                "User's project membership can only be changed by a project or system administrator"
              )
            }
          )

        // check if user exists
        userExists <- userExists(userIri)
        _           = if (!userExists) throw NotFoundException(s"The user $userIri does not exist.")

        // check if project exists
        projectExists <- projectExists(projectIri)
        _              = if (!projectExists) throw NotFoundException(s"The project $projectIri does not exist.")

        // get users current project membership list
        currentProjectMemberships   <- userProjectMembershipsGetADM(userIri = userIri)
        currentProjectMembershipIris = currentProjectMemberships.map(_.id)

        // check if user is a member and if he is then remove the project from to list
        updatedProjectMembershipIris =
          if (currentProjectMembershipIris.contains(projectIri)) {
            currentProjectMembershipIris diff Seq(projectIri)
          } else {
            throw BadRequestException(
              s"User $userIri is not member of project $projectIri."
            )
          }

        // get users current project admin membership list
        currentProjectAdminMembershipIris <- userProjectAdminMembershipsGetADM(userIri).map(_.map(_.id))

        // in case the user has an admin membership for that project, remove it as well
        maybeUpdatedProjectAdminMembershipIris =
          if (currentProjectAdminMembershipIris.contains(projectIri)) {
            Some(currentProjectAdminMembershipIris.filterNot(p => p == projectIri))
          } else {
            None
          }

        // create the update request by using the SystemUser
        result <- updateUserADM(
                    userIri = userIri,
                    userUpdatePayload = UserChangeRequestADM(
                      projects = Some(updatedProjectMembershipIris),
                      projectsAdmin = maybeUpdatedProjectAdminMembershipIris
                    ),
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                  )
      } yield result

    IriLocker.runWithIriLock(
      apiRequestID,
      userIri,
      userProjectMembershipRemoveRequestTask(userIri, projectIri, requestingUser)
    )
  }

  /**
   * Returns the user's project admin group memberships as a sequence of [[IRI]]
   *
   * @param userIri              the user's IRI.
   * @return a list of [[ProjectADM]].
   */
  private def userProjectAdminMembershipsGetADM(userIri: IRI): Task[Seq[ProjectADM]] =
    // ToDo: only allow system user
    // ToDo: this is a bit of a hack since the ProjectAdmin group doesn't really exist.
    for {
      userDataQueryResponse <- triplestore.query(Select(sparql.admin.txt.getUserByIri(userIri)))

      groupedUserData = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map { case (predicate, rows) =>
                          predicate -> rows.map(_.rowMap("o"))
                        }

      /* the projects the user is member of */
      projectIris = groupedUserData.get(OntologyConstants.KnoraAdmin.IsInProjectAdminGroup) match {
                      case Some(projects) => projects
                      case None           => Seq.empty[IRI]
                    }

      maybeProjectFutures =
        projectIris.map { projectIri =>
          messageRelay.ask[Option[ProjectADM]](
            ProjectGetADM(
              identifier = IriIdentifier
                .fromString(projectIri)
                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
            )
          )
        }
      maybeProjects            <- ZioHelper.sequence(maybeProjectFutures)
      projects: Seq[ProjectADM] = maybeProjects.flatten

    } yield projects

  /**
   * Returns the user's project admin group memberships, where the result contains the IRIs of the projects the user
   * is a member of the project admin group.
   *
   * @param userIri              the user's IRI.
   * @return a [[UserProjectAdminMembershipsGetResponseADM]].
   */
  private def userProjectAdminMembershipsGetRequestADM(userIri: IRI) =
    // ToDo: which user is allowed to do this operation?
    // ToDo: check permissions
    for {
      _        <- ZIO.whenZIO(userExists(userIri).negate)(ZIO.fail(BadRequestException(s"User $userIri does not exist.")))
      projects <- userProjectAdminMembershipsGetADM(userIri)
    } yield UserProjectAdminMembershipsGetResponseADM(projects)

  /**
   * Adds a user to the project admin group of a project.
   *
   * @param userIri              the user's IRI.
   * @param projectIri           the project's IRI.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]].
   */
  private def userProjectAdminMembershipAddRequestADM(
    userIri: IRI,
    projectIri: IRI,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def userProjectAdminMembershipAddRequestTask(
      userIri: IRI,
      projectIri: IRI,
      requestingUser: UserADM
    ): Task[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. project admin or system admin)
        _ <-
          ZIO.attempt(
            if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
              throw ForbiddenException(
                "User's project admin membership can only be changed by a project or system administrator"
              )
            }
          )

        // check if user exists
        userExists <- userExists(userIri)
        _           = if (!userExists) throw NotFoundException(s"The user $userIri does not exist.")

        // check if project exists
        projectExists <- projectExists(projectIri)
        _              = if (!projectExists) throw NotFoundException(s"The project $projectIri does not exist.")

        // get users current project membership list
        currentProjectMemberships <- userProjectMembershipsGetADM(userIri = userIri)

        currentProjectMembershipIris = currentProjectMemberships.map(_.id)

        // check if user is already project member and if not throw exception

        _ = if (!currentProjectMembershipIris.contains(projectIri)) {
              throw BadRequestException(
                s"User $userIri is not a member of project $projectIri. A user needs to be a member of the project to be added as project admin."
              )
            }

        // get users current project admin membership list
        currentProjectAdminMembershipIris <- userProjectAdminMembershipsGetADM(userIri).map(_.map(_.id))

        // check if user is already project admin and if not then append to list
        updatedProjectAdminMembershipIris =
          if (!currentProjectAdminMembershipIris.contains(projectIri)) {
            currentProjectAdminMembershipIris :+ projectIri
          } else {
            throw BadRequestException(
              s"User $userIri is already a project admin for project $projectIri."
            )
          }

        // create the update request
        result <- updateUserADM(
                    userIri = userIri,
                    userUpdatePayload = UserChangeRequestADM(projectsAdmin = Some(updatedProjectAdminMembershipIris)),
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                  )
      } yield result

    IriLocker.runWithIriLock(
      apiRequestID,
      userIri,
      userProjectAdminMembershipAddRequestTask(userIri, projectIri, requestingUser)
    )

  }

  /**
   * Removes a user from project admin group of a project.
   *
   * @param userIri              the user's IRI.
   * @param projectIri           the project's IRI.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]]
   */
  private def userProjectAdminMembershipRemoveRequestADM(
    userIri: IRI,
    projectIri: IRI,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def userProjectAdminMembershipRemoveRequestTask(
      userIri: IRI,
      projectIri: IRI,
      requestingUser: UserADM
    ): Task[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. requesting updates own information or is system admin)
        _ <-
          ZIO.attempt(
            if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
              throw ForbiddenException(
                "User's project admin membership can only be changed by a project or system administrator"
              )
            }
          )

        // check if user exists
        userExists <- userExists(userIri)
        _           = if (!userExists) throw NotFoundException(s"The user $userIri does not exist.")

        // check if project exists
        projectExists <- projectExists(projectIri)
        _              = if (!projectExists) throw NotFoundException(s"The project $projectIri does not exist.")

        // get users current project membership list
        currentProjectAdminMembershipIris <- userProjectAdminMembershipsGetADM(userIri).map(_.map(_.id))

        // check if user is not already a member and if he is then remove the project from to list
        updatedProjectAdminMembershipIris =
          if (currentProjectAdminMembershipIris.contains(projectIri)) {
            currentProjectAdminMembershipIris diff Seq(projectIri)
          } else {
            throw BadRequestException(
              s"User $userIri is not a project admin of project $projectIri."
            )
          }

        // create the update request
        result <- updateUserADM(
                    userIri = userIri,
                    userUpdatePayload = UserChangeRequestADM(projectsAdmin = Some(updatedProjectAdminMembershipIris)),
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                  )
      } yield result

    IriLocker.runWithIriLock(
      apiRequestID,
      userIri,
      userProjectAdminMembershipRemoveRequestTask(userIri, projectIri, requestingUser)
    )
  }

  /**
   * Returns the user's group memberships as a sequence of [[GroupADM]]
   *
   * @param userIri              the IRI of the user.
   * @return a sequence of [[GroupADM]].
   */
  private def userGroupMembershipsGetADM(userIri: IRI) =
    getSingleUserADM(
      UserIdentifierADM(maybeIri = Some(userIri)),
      UserInformationTypeADM.Full,
      KnoraSystemInstances.Users.SystemUser
    ).map(_.map(_.groups).getOrElse(Seq.empty))

  /**
   * Adds a user to a group.
   *
   * @param userIri              the user's IRI.
   * @param groupIri             the group IRI.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]].
   */
  private def userGroupMembershipAddRequestADM(
    userIri: IRI,
    groupIri: IRI,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def userGroupMembershipAddRequestTask(
      userIri: IRI,
      groupIri: IRI,
      requestingUser: UserADM
    ): Task[UserOperationResponseADM] =
      for {
        // check if user exists
        maybeUser <- getSingleUserADM(
                       UserIdentifierADM(maybeIri = Some(userIri)),
                       UserInformationTypeADM.Full,
                       KnoraSystemInstances.Users.SystemUser,
                       skipCache = true
                     )

        userToChange: UserADM = maybeUser match {
                                  case Some(user) => user
                                  case None       => throw NotFoundException(s"The user $userIri does not exist.")
                                }

        // check if group exists
        groupExists <- groupExists(groupIri)
        _            = if (!groupExists) throw NotFoundException(s"The group $groupIri does not exist.")

        // get group's info. we need the project IRI.
        maybeGroupADM <- messageRelay.ask[Option[GroupADM]](GroupGetADM(groupIri))

        projectIri = maybeGroupADM
                       .getOrElse(throw InconsistentRepositoryDataException(s"Group $groupIri does not exist"))
                       .project
                       .id

        // check if the requesting user is allowed to perform updates (i.e. project or system administrator)
        _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
              throw ForbiddenException(
                "User's group membership can only be changed by a project or system administrator"
              )
            }

        // get users current group membership list
        currentGroupMembershipIris = userToChange.groups.map(_.id)

        // check if user is already member and if not then append to list
        updatedGroupMembershipIris =
          if (!currentGroupMembershipIris.contains(groupIri)) {
            currentGroupMembershipIris :+ groupIri
          } else {
            throw BadRequestException(s"User $userIri is already member of group $groupIri.")
          }

        // create the update request
        result <- updateUserADM(
                    userIri = userIri,
                    userUpdatePayload = UserChangeRequestADM(groups = Some(updatedGroupMembershipIris)),
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                  )
      } yield result

    IriLocker.runWithIriLock(
      apiRequestID,
      userIri,
      userGroupMembershipAddRequestTask(userIri, groupIri, requestingUser)
    )
  }

  /**
   * Removes a user from a group.
   *
   * @param userIri              the user's IRI.
   * @param groupIri             the group IRI.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]].
   */
  private def userGroupMembershipRemoveRequestADM(
    userIri: IRI,
    groupIri: IRI,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def userGroupMembershipRemoveRequestTask(
      userIri: IRI,
      groupIri: IRI,
      requestingUser: UserADM
    ): Task[UserOperationResponseADM] =
      for {
        // check if user exists
        userExists <- userExists(userIri)
        _           = if (!userExists) throw NotFoundException(s"The user $userIri does not exist.")

        // check if group exists
        projectExists <- groupExists(groupIri)
        _              = if (!projectExists) throw NotFoundException(s"The group $groupIri does not exist.")

        // get group's info. we need the project IRI.
        maybeGroupADM <- messageRelay.ask[Option[GroupADM]](GroupGetADM(groupIri))

        projectIri = maybeGroupADM
                       .getOrElse(throw InconsistentRepositoryDataException(s"Group $groupIri does not exist"))
                       .project
                       .id

        // check if the requesting user is allowed to perform updates (i.e. is project or system admin)
        _ =
          if (
            !requestingUser.permissions
              .isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin && !requestingUser.isSystemUser
          ) {
            throw ForbiddenException("User's group membership can only be changed by a project or system administrator")
          }

        // get users current project membership list
        currentGroupMembershipIris <- userGroupMembershipsGetADM(userIri).map(_.map(_.id))

        // check if user is not already a member and if he is then remove the project from to list
        updatedGroupMembershipIris =
          if (currentGroupMembershipIris.contains(groupIri)) {
            currentGroupMembershipIris diff Seq(groupIri)
          } else {
            throw BadRequestException(s"User $userIri is not member of group $groupIri.")
          }

        // create the update request
        result <- updateUserADM(
                    userIri = userIri,
                    userUpdatePayload = UserChangeRequestADM(groups = Some(updatedGroupMembershipIris)),
                    requestingUser = requestingUser
                  )
      } yield result

    IriLocker.runWithIriLock(
      apiRequestID,
      userIri,
      userGroupMembershipRemoveRequestTask(userIri, groupIri, requestingUser)
    )
  }

  /**
   * Updates an existing user. Should not be directly used from the receive method.
   *
   * @param userIri              the IRI of the existing user that we want to update.
   * @param userUpdatePayload    the updated information.
   * @param requestingUser       the requesting user.
   * @return a [[UserOperationResponseADM]].
   *         fails with a BadRequestException         if necessary parameters are not supplied.
   *         fails with a UpdateNotPerformedException if the update was not performed.
   */
  private def updateUserADM(userIri: IRI, userUpdatePayload: UserChangeRequestADM, requestingUser: UserADM) = {

    logger.debug("updateUserADM - userUpdatePayload: {}", userUpdatePayload)

    // check if it is a request for a built-in user
    if (
      userIri.contains(KnoraSystemInstances.Users.SystemUser.id) || userIri.contains(
        KnoraSystemInstances.Users.AnonymousUser.id
      )
    ) {
      throw BadRequestException("Changes to built-in users are not allowed.")
    }

    for {

      // get current user
      maybeCurrentUser <- getSingleUserADM(
                            identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                            requestingUser = requestingUser,
                            userInformationType = UserInformationTypeADM.Full,
                            skipCache = true
                          )

      _ = if (maybeCurrentUser.isEmpty) {
            throw NotFoundException(s"User '$userIri' not found. Aborting update request.")
          }

      /* Update the user */
      maybeChangedUsername = userUpdatePayload.username match {
                               case Some(username) => Some(username.value)
                               case None           => None
                             }
      maybeChangedEmail = userUpdatePayload.email match {
                            case Some(email) => Some(email.value)
                            case None        => None
                          }
      maybeChangedGivenName = userUpdatePayload.givenName match {
                                case Some(givenName) =>
                                  Some(
                                    Iri
                                      .toSparqlEncodedString(givenName.value)
                                      .getOrElse(
                                        throw BadRequestException(
                                          s"The supplied given name: '${givenName.value}' is not valid."
                                        )
                                      )
                                  )
                                case None => None
                              }
      maybeChangedFamilyName = userUpdatePayload.familyName match {
                                 case Some(familyName) =>
                                   Some(
                                     Iri
                                       .toSparqlEncodedString(familyName.value)
                                       .getOrElse(
                                         throw BadRequestException(
                                           s"The supplied family name: '${familyName.value}' is not valid."
                                         )
                                       )
                                   )
                                 case None => None
                               }
      maybeChangedStatus = userUpdatePayload.status match {
                             case Some(status) => Some(status.value)
                             case None         => None
                           }
      maybeChangedLang = userUpdatePayload.lang match {
                           case Some(lang) => Some(lang.value)
                           case None       => None
                         }
      maybeChangedProjects = userUpdatePayload.projects match {
                               case Some(projects) => Some(projects)
                               case None           => None
                             }
      maybeChangedProjectsAdmin = userUpdatePayload.projectsAdmin match {
                                    case Some(projectsAdmin) => Some(projectsAdmin)
                                    case None                => None
                                  }
      maybeChangedGroups = userUpdatePayload.groups match {
                             case Some(groups) => Some(groups)
                             case None         => None
                           }
      maybeChangedSystemAdmin = userUpdatePayload.systemAdmin match {
                                  case Some(systemAdmin) => Some(systemAdmin.value)
                                  case None              => None
                                }

      updateUserSparql = sparql.admin.txt.updateUser(
                           AdminConstants.adminDataNamedGraph.value,
                           userIri = userIri,
                           maybeUsername = maybeChangedUsername,
                           maybeEmail = maybeChangedEmail,
                           maybeGivenName = maybeChangedGivenName,
                           maybeFamilyName = maybeChangedFamilyName,
                           maybeStatus = maybeChangedStatus,
                           maybeLang = maybeChangedLang,
                           maybeProjects = maybeChangedProjects,
                           maybeProjectsAdmin = maybeChangedProjectsAdmin,
                           maybeGroups = maybeChangedGroups,
                           maybeSystemAdmin = maybeChangedSystemAdmin
                         )

      // we are changing the user, so lets invalidate the cached copy
      // and write the updated user to the triplestore
      _ <- invalidateCachedUserADM(maybeCurrentUser) *> triplestore.query(Update(updateUserSparql))

      /* Verify that the user was updated */
      maybeUpdatedUserADM <- getSingleUserADM(
                               identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                               requestingUser = KnoraSystemInstances.Users.SystemUser,
                               userInformationType = UserInformationTypeADM.Full,
                               skipCache = true
                             )

      updatedUserADM: UserADM =
        maybeUpdatedUserADM.getOrElse(
          throw UpdateNotPerformedException("User was not updated. Please report this as a possible bug.")
        )

      _ = if (userUpdatePayload.username.isDefined) {
            if (updatedUserADM.username != userUpdatePayload.username.get.value)
              throw UpdateNotPerformedException(
                "User's 'username' was not updated. Please report this as a possible bug."
              )
          }

      _ = if (userUpdatePayload.email.isDefined) {
            if (updatedUserADM.email != userUpdatePayload.email.get.value)
              throw UpdateNotPerformedException("User's 'email' was not updated. Please report this as a possible bug.")
          }

      _ = if (userUpdatePayload.givenName.isDefined) {
            if (updatedUserADM.givenName != userUpdatePayload.givenName.get.value)
              throw UpdateNotPerformedException(
                "User's 'givenName' was not updated. Please report this as a possible bug."
              )
          }

      _ = if (userUpdatePayload.familyName.isDefined) {
            if (updatedUserADM.familyName != userUpdatePayload.familyName.get.value)
              throw UpdateNotPerformedException(
                "User's 'familyName' was not updated. Please report this as a possible bug."
              )
          }

      _ = if (userUpdatePayload.status.isDefined) {
            if (updatedUserADM.status != userUpdatePayload.status.get.value)
              throw UpdateNotPerformedException(
                "User's 'status' was not updated. Please report this as a possible bug."
              )
          }

      _ = if (userUpdatePayload.lang.isDefined) {
            if (updatedUserADM.lang != userUpdatePayload.lang.get.value)
              throw UpdateNotPerformedException("User's 'lang' was not updated. Please report this as a possible bug.")
          }

      _ = if (userUpdatePayload.projects.isDefined) {
            for {
              projects <- userProjectMembershipsGetADM(userIri = userIri)
              _ =
                if (projects.map(_.id).sorted != userUpdatePayload.projects.get.sorted) {
                  throw UpdateNotPerformedException(
                    "User's 'project' memberships were not updated. Please report this as a possible bug."
                  )
                }
            } yield UserProjectMembershipsGetResponseADM(projects)
          }

      _ = if (userUpdatePayload.systemAdmin.isDefined) {
            if (updatedUserADM.permissions.isSystemAdmin != userUpdatePayload.systemAdmin.get.value)
              throw UpdateNotPerformedException(
                "User's 'isInSystemAdminGroup' status was not updated. Please report this as a possible bug."
              )
          }

      _ = if (userUpdatePayload.groups.isDefined) {
            if (updatedUserADM.groups.map(_.id).sorted != userUpdatePayload.groups.get.sorted)
              throw UpdateNotPerformedException(
                "User's 'group' memberships were not updated. Please report this as a possible bug."
              )
          }

      _ <- writeUserADMToCache(
             maybeUpdatedUserADM.getOrElse(
               throw UpdateNotPerformedException("User was not updated. Please report this as a possible bug.")
             )
           )

    } yield UserOperationResponseADM(updatedUserADM.ofType(UserInformationTypeADM.Restricted))
  }

  /**
   * Updates the password for a user.
   *
   * @param userIri              the IRI of the existing user that we want to update.
   * @param password             the new password.
   * @param requestingUser       the requesting user.
   * @return a [[UserOperationResponseADM]].
   *         fails with a [[BadRequestException]]         if necessary parameters are not supplied.
   *         fails with a [[UpdateNotPerformedException]] if the update was not performed.
   */
  private def updateUserPasswordADM(userIri: IRI, password: Password, requestingUser: UserADM) = {

    // check if it is a request for a built-in user
    if (
      userIri.contains(KnoraSystemInstances.Users.SystemUser.id) || userIri.contains(
        KnoraSystemInstances.Users.AnonymousUser.id
      )
    ) {
      throw BadRequestException("Changes to built-in users are not allowed.")
    }

    for {
      maybeCurrentUser <- getSingleUserADM(
                            identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                            requestingUser = requestingUser,
                            userInformationType = UserInformationTypeADM.Full,
                            skipCache = true
                          )

      _ = if (maybeCurrentUser.isEmpty) {
            throw NotFoundException(s"User '$userIri' not found. Aborting update request.")
          }
      // we are changing the user, so lets get rid of the cached copy
      _ <- invalidateCachedUserADM(maybeCurrentUser)

      // update the password
      updateUserSparql =
        sparql.admin.txt.updateUserPassword(AdminConstants.adminDataNamedGraph.value, userIri, password.value)
      _ <- triplestore.query(Update(updateUserSparql))

      /* Verify that the password was updated. */
      maybeUpdatedUserADM <- getSingleUserADM(
                               identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                               requestingUser = requestingUser,
                               userInformationType = UserInformationTypeADM.Full,
                               skipCache = true
                             )

      updatedUserADM: UserADM =
        maybeUpdatedUserADM.getOrElse(
          throw UpdateNotPerformedException("User was not updated. Please report this as a possible bug.")
        )

      _ = if (updatedUserADM.password.get != password.value)
            throw UpdateNotPerformedException("User's password was not updated. Please report this as a possible bug.")

    } yield UserOperationResponseADM(updatedUserADM.ofType(UserInformationTypeADM.Restricted))
  }

  /**
   * Creates a new user. Self-registration is allowed, so even the default user, i.e. with no credentials supplied,
   * is allowed to create a new user.
   *
   * Referenced Websites:
   *                     - https://crackstation.net/hashing-security.htm
   *                     - http://blog.ircmaxell.com/2012/12/seven-ways-to-screw-up-bcrypt.html
   *
   * @param userCreatePayloadADM    a [[UserCreatePayloadADM]] object containing information about the new user to be created.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]].
   */
  private def createNewUserADM(
    userCreatePayloadADM: UserCreatePayloadADM,
    apiRequestID: UUID
  ): Task[UserOperationResponseADM] = {

    logger.debug("createNewUserADM - userCreatePayloadADM: {}", userCreatePayloadADM)

    /**
     * The actual task run with an IRI lock.
     */
    def createNewUserTask(userCreatePayloadADM: UserCreatePayloadADM) =
      for {
        // check if username is unique
        usernameTaken <- userByUsernameExists(Some(userCreatePayloadADM.username))
        _ = if (usernameTaken) {
              throw DuplicateValueException(
                s"User with the username '${userCreatePayloadADM.username.value}' already exists"
              )
            }

        // check if email is unique
        emailTaken <- userByEmailExists(Some(userCreatePayloadADM.email))
        _ = if (emailTaken) {
              throw DuplicateValueException(
                s"User with the email '${userCreatePayloadADM.email.value}' already exists"
              )
            }

        // check the custom IRI; if not given, create an unused IRI
        customUserIri: Option[SmartIri] = userCreatePayloadADM.id.map(_.value.toSmartIri)
        userIri                        <- iriService.checkOrCreateEntityIri(customUserIri, stringFormatter.makeRandomPersonIri)

        // hash password
        encoder        = new BCryptPasswordEncoder(appConfig.bcryptPasswordStrength)
        hashedPassword = encoder.encode(userCreatePayloadADM.password.value)

        // Create the new user.
        createNewUserSparql = sparql.admin.txt.createNewUser(
                                AdminConstants.adminDataNamedGraph.value,
                                userIri = userIri,
                                userClassIri = OntologyConstants.KnoraAdmin.User,
                                username = Iri
                                  .toSparqlEncodedString(userCreatePayloadADM.username.value)
                                  .getOrElse(
                                    throw BadRequestException(
                                      s"The supplied username: '${userCreatePayloadADM.username.value}' is not valid."
                                    )
                                  ),
                                email = Iri
                                  .toSparqlEncodedString(userCreatePayloadADM.email.value)
                                  .getOrElse(
                                    throw BadRequestException(
                                      s"The supplied email: '${userCreatePayloadADM.email.value}' is not valid."
                                    )
                                  ),
                                password = hashedPassword,
                                givenName = Iri
                                  .toSparqlEncodedString(userCreatePayloadADM.givenName.value)
                                  .getOrElse(
                                    throw BadRequestException(
                                      s"The supplied given name: '${userCreatePayloadADM.givenName.value}' is not valid."
                                    )
                                  ),
                                familyName = Iri
                                  .toSparqlEncodedString(userCreatePayloadADM.familyName.value)
                                  .getOrElse(
                                    throw BadRequestException(
                                      s"The supplied family name: '${userCreatePayloadADM.familyName.value}' is not valid."
                                    )
                                  ),
                                status = userCreatePayloadADM.status.value,
                                preferredLanguage = Iri
                                  .toSparqlEncodedString(userCreatePayloadADM.lang.value)
                                  .getOrElse(
                                    throw BadRequestException(
                                      s"The supplied language: '${userCreatePayloadADM.lang.value}' is not valid."
                                    )
                                  ),
                                systemAdmin = userCreatePayloadADM.systemAdmin.value
                              )
        _ <- triplestore.query(Update(createNewUserSparql))

        // try to retrieve newly created user (will also add to cache)
        maybeNewUserADM <- getSingleUserADM(
                             identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                             requestingUser = KnoraSystemInstances.Users.SystemUser,
                             userInformationType = UserInformationTypeADM.Full,
                             skipCache = true
                           )

        // check to see if we could retrieve the new user
        newUserADM =
          maybeNewUserADM.getOrElse(
            throw UpdateNotPerformedException(s"User $userIri was not created. Please report this as a possible bug.")
          )

        // create the user operation response
        _                        = logger.debug("createNewUserADM - created new user: {}", newUserADM)
        userOperationResponseADM = UserOperationResponseADM(newUserADM.ofType(UserInformationTypeADM.Restricted))

      } yield userOperationResponseADM
    IriLocker.runWithIriLock(
      apiRequestID,
      USERS_GLOBAL_LOCK_IRI,
      createNewUserTask(userCreatePayloadADM)
    )
  }

  ////////////////////
  // Helper Methods //
  ////////////////////

  /**
   * Tries to retrieve a [[UserADM]] either from triplestore or cache if caching is enabled.
   * If user is not found in cache but in triplestore, then user is written to cache.
   *
   * @param identifier The identifier of the user (can be IRI, e-mail or username)
   * @return a [[Option[UserADM]]]
   */
  private def getUserFromCacheOrTriplestore(
    identifier: UserIdentifierADM
  ): Task[Option[UserADM]] =
    if (appConfig.cacheService.enabled) {
      // caching enabled
      getUserFromCache(identifier).flatMap {
        case None =>
          // none found in cache. getting from triplestore.
          getUserFromTriplestore(identifier = identifier).flatMap {
            case None =>
              // also none found in triplestore. finally returning none.
              logger.debug("getUserFromCacheOrTriplestore - not found in cache and in triplestore")
              ZIO.succeed(None)
            case Some(user) =>
              // found a user in the triplestore. need to write to cache.
              logger.debug(
                "getUserFromCacheOrTriplestore - not found in cache but found in triplestore. need to write to cache."
              )
              // writing user to cache and afterwards returning the user found in the triplestore
              writeUserADMToCache(user) *> ZIO.succeed(Some(user))
          }
        case Some(user) =>
          logger.debug("getUserFromCacheOrTriplestore - found in cache. returning user.")
          ZIO.succeed(Some(user))
      }
    } else {
      // caching disabled
      logger.debug("getUserFromCacheOrTriplestore - caching disabled. getting from triplestore.")
      getUserFromTriplestore(identifier = identifier)
    }

  /**
   * Tries to retrieve a [[UserADM]] from the triplestore.
   *
   * @param identifier The identifier of the user (can be IRI, e-mail or username)
   * @return a [[Option[UserADM]]]
   */
  private def getUserFromTriplestore(
    identifier: UserIdentifierADM
  ): Task[Option[UserADM]] = {
    val query = Construct(
      sparql.admin.txt.getUsers(
        maybeIri = identifier.toIriOption,
        maybeUsername = identifier.toUsernameOption,
        maybeEmail = identifier.toEmailOption
      )
    )
    triplestore
      .query(query)
      .flatMap(_.asExtended)
      .map(_.statements.headOption)
      .flatMap(_.map(statements2UserADM).getOrElse(ZIO.none))
  }

  /**
   * Helper method used to create a [[UserADM]] from the [[SparqlExtendedConstructResponse]] containing user data.
   *
   * @param statements           result from the SPARQL query containing user data.
   * @return a [[Option[UserADM]]]
   */
  private def statements2UserADM(
    statements: (SubjectV2, Map[SmartIri, Seq[LiteralV2]])
  ): Task[Option[UserADM]] = {

    val userIri: IRI                            = statements._1.toString
    val propsMap: Map[SmartIri, Seq[LiteralV2]] = statements._2

    if (propsMap.nonEmpty) {

      /* the groups the user is member of (only explicit groups) */
      val groupIris: Seq[IRI] = propsMap.get(OntologyConstants.KnoraAdmin.IsInGroup.toSmartIri) match {
        case Some(groups) => groups.map(_.asInstanceOf[IriLiteralV2].value)
        case None         => Seq.empty[IRI]
      }

      /* the projects the user is member of (only explicit projects) */
      val projectIris: Seq[IRI] = propsMap.get(OntologyConstants.KnoraAdmin.IsInProject.toSmartIri) match {
        case Some(projects) => projects.map(_.asInstanceOf[IriLiteralV2].value)
        case None           => Seq.empty[IRI]
      }

      /* the projects for which the user is implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group */
      val isInProjectAdminGroups: Seq[IRI] = propsMap
        .getOrElse(OntologyConstants.KnoraAdmin.IsInProjectAdminGroup.toSmartIri, Vector.empty[IRI])
        .map(_.asInstanceOf[IriLiteralV2].value)

      /* is the user implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#SystemAdmin' group */
      val isInSystemAdminGroup = propsMap
        .get(OntologyConstants.KnoraAdmin.IsInSystemAdminGroup.toSmartIri)
        .exists(p => p.head.asInstanceOf[BooleanLiteralV2].value)

      for {
        /* get the user's permission profile from the permissions responder */
        permissionData <- messageRelay
                            .ask[PermissionsDataADM](
                              PermissionDataGetADM(
                                projectIris = projectIris,
                                groupIris = groupIris,
                                isInProjectAdminGroups = isInProjectAdminGroups,
                                isInSystemAdminGroup = isInSystemAdminGroup,
                                requestingUser = KnoraSystemInstances.Users.SystemUser
                              )
                            )

        maybeGroupFutures: Seq[Task[Option[GroupADM]]] = groupIris.map { groupIri =>
                                                           messageRelay.ask[Option[GroupADM]](GroupGetADM(groupIri))
                                                         }
        maybeGroups          <- ZioHelper.sequence(maybeGroupFutures)
        groups: Seq[GroupADM] = maybeGroups.flatten

        maybeProjectFutures =
          projectIris.map { projectIri =>
            messageRelay
              .ask[Option[ProjectADM]](
                ProjectGetADM(
                  identifier = IriIdentifier
                    .fromString(projectIri)
                    .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
                )
              )
          }
        projects <- ZioHelper.sequence(maybeProjectFutures).map(_.flatten)

        /* construct the user profile from the different parts */
        user = model.UserADM(
                 id = userIri,
                 username = propsMap
                   .getOrElse(
                     OntologyConstants.KnoraAdmin.Username.toSmartIri,
                     throw InconsistentRepositoryDataException(s"User: $userIri has no 'username' defined.")
                   )
                   .head
                   .asInstanceOf[StringLiteralV2]
                   .value,
                 email = propsMap
                   .getOrElse(
                     OntologyConstants.KnoraAdmin.Email.toSmartIri,
                     throw InconsistentRepositoryDataException(s"User: $userIri has no 'email' defined.")
                   )
                   .head
                   .asInstanceOf[StringLiteralV2]
                   .value,
                 givenName = propsMap
                   .getOrElse(
                     OntologyConstants.KnoraAdmin.GivenName.toSmartIri,
                     throw InconsistentRepositoryDataException(s"User: $userIri has no 'givenName' defined.")
                   )
                   .head
                   .asInstanceOf[StringLiteralV2]
                   .value,
                 familyName = propsMap
                   .getOrElse(
                     OntologyConstants.KnoraAdmin.FamilyName.toSmartIri,
                     throw InconsistentRepositoryDataException(s"User: $userIri has no 'familyName' defined.")
                   )
                   .head
                   .asInstanceOf[StringLiteralV2]
                   .value,
                 status = propsMap
                   .getOrElse(
                     OntologyConstants.KnoraAdmin.StatusProp.toSmartIri,
                     throw InconsistentRepositoryDataException(s"User: $userIri has no 'status' defined.")
                   )
                   .head
                   .asInstanceOf[BooleanLiteralV2]
                   .value,
                 lang = propsMap
                   .getOrElse(
                     OntologyConstants.KnoraAdmin.PreferredLanguage.toSmartIri,
                     throw InconsistentRepositoryDataException(s"User: $userIri has no 'preferredLanguage' defined.")
                   )
                   .head
                   .asInstanceOf[StringLiteralV2]
                   .value,
                 password = propsMap
                   .get(OntologyConstants.KnoraAdmin.Password.toSmartIri)
                   .map(_.head.asInstanceOf[StringLiteralV2].value),
                 token = None,
                 groups = groups,
                 projects = projects,
                 permissions = permissionData
               )

        result: Option[UserADM] = Some(user)
      } yield result

    } else {
      ZIO.succeed(None)
    }
  }

  /**
   * Helper method for checking if a user exists.
   *
   * @param userIri the IRI of the user.
   * @return a [[Boolean]].
   */
  private def userExists(userIri: IRI): Task[Boolean] =
    triplestore.query(Ask(sparql.admin.txt.checkUserExists(userIri)))

  /**
   * Helper method for checking if an username is already registered.
   *
   * @param maybeUsername the username of the user.
   * @param maybeCurrent  the current username of the user.
   * @return a [[Boolean]].
   */
  private def userByUsernameExists(
    maybeUsername: Option[Username],
    maybeCurrent: Option[String] = None
  ): Task[Boolean] =
    maybeUsername match {
      case Some(username) =>
        if (maybeCurrent.contains(username.value)) ZIO.succeed(true)
        else
          triplestore.query(Ask(sparql.admin.txt.checkUserExistsByUsername(username.value)))

      case None => ZIO.succeed(false)
    }

  /**
   * Helper method for checking if an email is already registered.
   *
   * @param maybeEmail   the email of the user.
   * @param maybeCurrent the current email of the user.
   * @return a [[Boolean]].
   */
  private def userByEmailExists(maybeEmail: Option[Email], maybeCurrent: Option[String] = None): Task[Boolean] =
    maybeEmail match {
      case Some(email) =>
        if (maybeCurrent.contains(email.value)) {
          ZIO.succeed(true)
        } else {
          for {
            _ <- ZIO
                   .fromOption(stringFormatter.validateEmail(email.value))
                   .orElseFail(BadRequestException(s"The email address '${email.value}' is invalid"))
            userExists <- triplestore.query(Ask(sparql.admin.txt.checkUserExistsByEmail(email.value)))
          } yield userExists
        }

      case None => ZIO.succeed(false)
    }

  /**
   * Helper method for checking if a project exists.
   *
   * @param projectIri the IRI of the project.
   * @return a [[Boolean]].
   */
  private def projectExists(projectIri: IRI): Task[Boolean] =
    triplestore.query(Ask(sparql.admin.txt.checkProjectExistsByIri(projectIri)))

  /**
   * Helper method for checking if a group exists.
   *
   * @param groupIri the IRI of the group.
   * @return a [[Boolean]].
   */
  private def groupExists(groupIri: IRI): Task[Boolean] =
    triplestore.query(Ask(sparql.admin.txt.checkGroupExistsByIri(groupIri)))

  /**
   * Tries to retrieve a [[UserADM]] from the cache.
   *
   * @param identifier the user's identifier (could be IRI, e-mail or username)
   * @return a [[Option[UserADM]]]
   */
  private def getUserFromCache(identifier: UserIdentifierADM): Task[Option[UserADM]] = {
    val result = messageRelay.ask[Option[UserADM]](CacheServiceGetUserADM(identifier))
    result.map {
      case Some(user) =>
        logger.debug("getUserFromCache - cache hit for: {}", identifier)
        Some(user)
      case None =>
        logger.debug("getUserFromCache - no cache hit for: {}", identifier)
        None
    }
  }

  /**
   * Writes the user profile to cache.
   *
   * @param user a [[UserADM]].
   * @return Unit
   */
  private def writeUserADMToCache(user: UserADM): Task[Unit] =
    messageRelay.ask[Any](CacheServicePutUserADM(user)) *>
      ZIO.logDebug(s"writeUserADMToCache done - user: ${user.id}")

  /**
   * Removes the user from cache.
   *
   * @param maybeUser the optional user which is removed from the cache
   * @return a [[Unit]]
   */
  private def invalidateCachedUserADM(maybeUser: Option[UserADM]): Task[Unit] =
    if (appConfig.cacheService.enabled) {
      val keys: Set[String] = Seq(maybeUser.map(_.id), maybeUser.map(_.email), maybeUser.map(_.username)).flatten.toSet
      // only send to cache if keys are not empty
      if (keys.nonEmpty) {
        val result = messageRelay.ask[Any](CacheServiceRemoveValues(keys))
        result.map { res =>
          logger.debug("invalidateCachedUserADM - result: {}", res)
        }
      } else {
        // since there was nothing to remove, we can immediately return
        ZIO.succeed(())
      }
    } else {
      // caching is turned off, so nothing to do.
      ZIO.succeed(())
    }
}

object UsersResponderADMLive {
  val layer: URLayer[
    StringFormatter & TriplestoreService & MessageRelay & IriService & AppConfig,
    UsersResponderADMLive
  ] = ZLayer.fromZIO {
    for {
      config  <- ZIO.service[AppConfig]
      iriS    <- ZIO.service[IriService]
      mr      <- ZIO.service[MessageRelay]
      ts      <- ZIO.service[TriplestoreService]
      sf      <- ZIO.service[StringFormatter]
      handler <- mr.subscribe(UsersResponderADMLive(config, iriS, mr, ts, sf))
    } yield handler
  }
}
