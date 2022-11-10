/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

import java.util.UUID
import scala.concurrent.Future

import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors._
import dsp.valueobjects.User._
import org.knora.webapi._
import org.knora.webapi.instrumentation.InstrumentationSupport
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupGetADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDataGetADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserChangeRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceGetUserADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServicePutUserADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceRemoveValues
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.Responder.handleUnexpectedMessage

/**
 * Provides information about Knora users to other responders.
 */
class UsersResponderADM(responderData: ResponderData) extends Responder(responderData) with InstrumentationSupport {

  // The IRI used to lock user creation and update
  private val USERS_GLOBAL_LOCK_IRI = "http://rdfh.ch/users"

  /**
   * Receives a message extending [[UsersResponderRequestADM]], and returns an appropriate message.
   */
  def receive(msg: UsersResponderRequestADM): Future[Equals] = msg match {
    case UsersGetADM(userInformationTypeADM, requestingUser) =>
      getAllUserADM(userInformationTypeADM, requestingUser)
    case UsersGetRequestADM(userInformationTypeADM, requestingUser) =>
      getAllUserADMRequest(userInformationTypeADM, requestingUser)
    case UserGetADM(identifier, userInformationTypeADM, requestingUser) =>
      getSingleUserADM(identifier, userInformationTypeADM, requestingUser)
    case UserGetRequestADM(identifier, userInformationTypeADM, requestingUser) =>
      getSingleUserADMRequest(identifier, userInformationTypeADM, requestingUser)
    case UserCreateRequestADM(userCreatePayloadADM, requestingUser, apiRequestID) =>
      createNewUserADM(userCreatePayloadADM, requestingUser, apiRequestID)
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
    case UserProjectMembershipsGetRequestADM(userIri, requestingUser) =>
      userProjectMembershipsGetRequestADM(userIri, requestingUser)
    case UserProjectMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID) =>
      userProjectMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID)
    case UserProjectMembershipRemoveRequestADM(
          userIri,
          projectIri,
          requestingUser,
          apiRequestID
        ) =>
      userProjectMembershipRemoveRequestADM(userIri, projectIri, requestingUser, apiRequestID)
    case UserProjectAdminMembershipsGetRequestADM(userIri, requestingUser, apiRequestID) =>
      userProjectAdminMembershipsGetRequestADM(userIri, requestingUser, apiRequestID)
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
    case UserGroupMembershipsGetRequestADM(userIri, requestingUser) =>
      userGroupMembershipsGetRequestADM(userIri, requestingUser)
    case UserGroupMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID) =>
      userGroupMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID)
    case UserGroupMembershipRemoveRequestADM(userIri, projectIri, requestingUser, apiRequestID) =>
      userGroupMembershipRemoveRequestADM(userIri, projectIri, requestingUser, apiRequestID)
    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  /**
   * Gets all the users and returns them as a sequence of [[UserADM]].
   *
   * @param userInformationType  the extent of the information returned.
   *
   * @param requestingUser       the user initiating the request.
   * @return all the users as a sequence of [[UserADM]].
   */
  private def getAllUserADM(
    userInformationType: UserInformationTypeADM,
    requestingUser: UserADM
  ): Future[Seq[UserADM]] =
    for {
      _ <- Future(
             if (
               !requestingUser.permissions.isSystemAdmin && !requestingUser.permissions
                 .isProjectAdminInAnyProject() && !requestingUser.isSystemUser
             ) {
               throw ForbiddenException("ProjectAdmin or SystemAdmin permissions are required.")
             }
           )

      sparqlQueryString <- Future(
                             org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                               .getUsers(
                                 maybeIri = None,
                                 maybeUsername = None,
                                 maybeEmail = None
                               )
                               .toString()
                           )

      usersResponse <- appActor
                         .ask(
                           SparqlExtendedConstructRequest(
                             sparql = sparqlQueryString
                           )
                         )
                         .mapTo[SparqlExtendedConstructResponse]

      statements = usersResponse.statements.toList

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
                                    OntologyConstants.KnoraAdmin.Status.toSmartIri,
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
   * @param userInformationType  the extent of the information returned.
   *
   * @param requestingUser       the user initiating the request.
   * @return all the users as a [[UsersGetResponseV1]].
   */
  private def getAllUserADMRequest(
    userInformationType: UserInformationTypeADM,
    requestingUser: UserADM
  ): Future[UsersGetResponseADM] =
    for {
      maybeUsersListToReturn <- getAllUserADM(
                                  userInformationType = userInformationType,
                                  requestingUser = requestingUser
                                )

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
  ): Future[Option[UserADM]] = tracedFuture("admin-user-get-single-user") {

    log.debug(
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
          log.debug("getSingleUserADM - successfully retrieved user: {}", identifier.value)
        } else {
          log.debug("getSingleUserADM - could not retrieve user: {}", identifier.value)
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
  ): Future[UserResponseADM] =
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
   * @throws BadRequestException if the necessary parameters are not supplied.
   * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
   */
  private def changeBasicUserInformationADM(
    userIri: IRI,
    userUpdateBasicInformationPayload: UserUpdateBasicInformationPayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[UserOperationResponseADM] = {

    log.debug(s"changeBasicUserInformationADM: changeUserRequest: {}", userUpdateBasicInformationPayload)

    /**
     * The actual change basic user data task run with an IRI lock.
     */
    def changeBasicUserDataTask(
      userIri: IRI,
      userUpdateBasicInformationPayload: UserUpdateBasicInformationPayloadADM,
      requestingUser: UserADM,
      apiRequestID: UUID
    ): Future[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. requesting updates own information or is system admin)
        _ <- Future(
               if (!requestingUser.id.equalsIgnoreCase(userIri) && !requestingUser.permissions.isSystemAdmin) {
                 throw ForbiddenException(
                   "User information can only be changed by the user itself or a system administrator"
                 )
               }
             )

        // get current user information
        currentUserInformation: Option[UserADM] <- getSingleUserADM(
                                                     identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                                                     userInformationType = UserInformationTypeADM.Full,
                                                     requestingUser = KnoraSystemInstances.Users.SystemUser
                                                   )

        // check if email is unique in case of a change email request
        emailTaken: Boolean <-
          userByEmailExists(userUpdateBasicInformationPayload.email, Some(currentUserInformation.get.email))
        _ = if (emailTaken) {
              throw DuplicateValueException(
                s"User with the email '${userUpdateBasicInformationPayload.email.get.value}' already exists"
              )
            }

        // check if username is unique in case of a change username request
        usernameTaken: Boolean <-
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
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = apiRequestID
                  )
      } yield result

    for {
      // run the user update with a global IRI lock
      taskResult <-
        IriLocker.runWithIriLock(
          apiRequestID,
          USERS_GLOBAL_LOCK_IRI,
          () => changeBasicUserDataTask(userIri, userUpdateBasicInformationPayload, requestingUser, apiRequestID)
        )
    } yield taskResult
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
   * @throws BadRequestException if necessary parameters are not supplied.
   * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
   * @throws ForbiddenException  if the supplied old password doesn't match with the user's current password.
   * @throws NotFoundException   if the user is not found.
   */
  private def changePasswordADM(
    userIri: IRI,
    userUpdatePasswordPayload: UserUpdatePasswordPayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[UserOperationResponseADM] = {

    /**
     * The actual change password task run with an IRI lock.
     */
    def changePasswordTask(
      userIri: IRI,
      userUpdatePasswordPayload: UserUpdatePasswordPayloadADM,
      requestingUser: UserADM,
      apiRequestID: UUID
    ): Future[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. requesting updates own information or is system admin)
        _ <- Future(
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
        encoder = new BCryptPasswordEncoder(responderData.appConfig.bcryptPasswordStrength)
        newHashedPassword = Password
                              .make(encoder.encode(userUpdatePasswordPayload.newPassword.value))
                              .fold(e => throw e.head, value => value)

        // update the users password as SystemUser
        result <- updateUserPasswordADM(
                    userIri = userIri,
                    password = newHashedPassword,
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = apiRequestID
                  )

      } yield result

    for {
      // run the change password task with an IRI lock
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      userIri,
                      () => changePasswordTask(userIri, userUpdatePasswordPayload, requestingUser, apiRequestID)
                    )
    } yield taskResult
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
   * @throws BadRequestException if necessary parameters are not supplied.
   * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
   */
  private def changeUserStatusADM(
    userIri: IRI,
    status: UserStatus,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[UserOperationResponseADM] = {

    log.debug(s"changeUserStatusADM - new status: {}", status)

    /**
     * The actual change user status task run with an IRI lock.
     */
    def changeUserStatusTask(
      userIri: IRI,
      status: UserStatus,
      requestingUser: UserADM,
      apiRequestID: UUID
    ): Future[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. requesting updates own information or is system admin)
        _ <-
          Future(
            if (!requestingUser.id.equalsIgnoreCase(userIri) && !requestingUser.permissions.isSystemAdmin) {
              throw ForbiddenException("User's status can only be changed by the user itself or a system administrator")
            }
          )

        // create the update request
        result <- updateUserADM(
                    userIri = userIri,
                    userUpdatePayload = UserChangeRequestADM(status = Some(status)),
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = apiRequestID
                  )

      } yield result

    for {
      // run the change status task with an IRI lock
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      userIri,
                      () => changeUserStatusTask(userIri, status, requestingUser, apiRequestID)
                    )
    } yield taskResult
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
   * @throws BadRequestException if necessary parameters are not supplied.
   * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
   */
  private def changeUserSystemAdminMembershipStatusADM(
    userIri: IRI,
    systemAdmin: SystemAdmin,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[UserOperationResponseADM] = {

    /**
     * The actual change user status task run with an IRI lock.
     */
    def changeUserSystemAdminMembershipStatusTask(
      userIri: IRI,
      systemAdmin: SystemAdmin,
      requestingUser: UserADM,
      apiRequestID: UUID
    ): Future[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. system admin)
        _ <-
          Future(
            if (!requestingUser.permissions.isSystemAdmin) {
              throw ForbiddenException("User's system admin membership can only be changed by a system administrator")
            }
          )

        // create the update request
        result <- updateUserADM(
                    userIri = userIri,
                    userUpdatePayload = UserChangeRequestADM(systemAdmin = Some(systemAdmin)),
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = apiRequestID
                  )

      } yield result

    for {
      // run the change status task with an IRI lock
      taskResult <-
        IriLocker.runWithIriLock(
          apiRequestID,
          userIri,
          () => changeUserSystemAdminMembershipStatusTask(userIri, systemAdmin, requestingUser, apiRequestID)
        )
    } yield taskResult
  }

  /**
   * Returns user's project memberships as a sequence of [[ProjectADM]].
   *
   * @param userIri        the IRI of the user.
   * @param requestingUser the requesting user.
   * @return a sequence of [[ProjectADM]]
   */
  private def userProjectMembershipsGetADM(
    userIri: IRI,
    requestingUser: UserADM
  ): Future[Seq[ProjectADM]] =
    for {
      maybeUser <- getSingleUserADM(
                     identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                     userInformationType = UserInformationTypeADM.Full,
                     requestingUser = KnoraSystemInstances.Users.SystemUser
                   )

      result = maybeUser match {
                 case Some(userADM) => userADM.projects
                 case None          => Seq.empty[ProjectADM]
               }

    } yield result

  /**
   * Returns the user's project memberships as [[UserProjectMembershipsGetResponseADM]].
   *
   * @param userIri        the user's IRI.
   * @param requestingUser the requesting user.
   * @return a [[UserProjectMembershipsGetResponseADM]].
   */
  private def userProjectMembershipsGetRequestADM(
    userIri: IRI,
    requestingUser: UserADM
  ): Future[UserProjectMembershipsGetResponseADM] =
    for {
      userExists <- userExists(userIri)
      _ = if (!userExists) {
            throw BadRequestException(s"User $userIri does not exist.")
          }

      projects: Seq[ProjectADM] <- userProjectMembershipsGetADM(
                                     userIri = userIri,
                                     requestingUser = requestingUser
                                   )
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
  ): Future[UserOperationResponseADM] = {

    log.debug(s"userProjectMembershipAddRequestADM: userIri: {}, projectIri: {}", userIri, projectIri)

    /**
     * The actual task run with an IRI lock.
     */
    def userProjectMembershipAddRequestTask(
      userIri: IRI,
      projectIri: IRI,
      requestingUser: UserADM,
      apiRequestID: UUID
    ): Future[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. is project or system admin)
        _ <-
          Future(
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
        currentProjectMemberships <- userProjectMembershipsGetRequestADM(
                                       userIri = userIri,
                                       requestingUser = KnoraSystemInstances.Users.SystemUser
                                     )

        currentProjectMembershipIris: Seq[IRI] = currentProjectMemberships.projects.map(_.id)

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
                              requestingUser = requestingUser,
                              apiRequestID = apiRequestID
                            )
      } yield updateUserResult

    for {
      // run the task with an IRI lock
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      userIri,
                      () => userProjectMembershipAddRequestTask(userIri, projectIri, requestingUser, apiRequestID)
                    )
    } yield taskResult

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
  ): Future[UserOperationResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def userProjectMembershipRemoveRequestTask(
      userIri: IRI,
      projectIri: IRI,
      requestingUser: UserADM,
      apiRequestID: UUID
    ): Future[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. is project or system admin)
        _ <-
          Future(
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
        currentProjectMemberships <- userProjectMembershipsGetADM(
                                       userIri = userIri,
                                       requestingUser = KnoraSystemInstances.Users.SystemUser
                                     )
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
        currentProjectAdminMemberships <- userProjectAdminMembershipsGetADM(
                                            userIri = userIri,
                                            requestingUser = KnoraSystemInstances.Users.SystemUser,
                                            apiRequestID = apiRequestID
                                          )

        currentProjectAdminMembershipIris: Seq[IRI] = currentProjectAdminMemberships.map(_.id)

        // in case the user has an admin membership for that project, remove it as well
        maybeUpdatedProjectAdminMembershipIris = if (currentProjectAdminMembershipIris.contains(projectIri)) {
                                                   Some(
                                                     currentProjectAdminMembershipIris.filterNot(p => p == projectIri)
                                                   )
                                                 } else None

        // create the update request by using the SystemUser
        result <- updateUserADM(
                    userIri = userIri,
                    userUpdatePayload = UserChangeRequestADM(
                      projects = Some(updatedProjectMembershipIris),
                      projectsAdmin = maybeUpdatedProjectAdminMembershipIris
                    ),
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = apiRequestID
                  )
      } yield result

    for {
      // run the task with an IRI lock
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      userIri,
                      () => userProjectMembershipRemoveRequestTask(userIri, projectIri, requestingUser, apiRequestID)
                    )
    } yield taskResult
  }

  /**
   * Returns the user's project admin group memberships as a sequence of [[IRI]]
   *
   * @param userIri              the user's IRI.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserProjectMembershipsGetResponseV1]].
   */
  private def userProjectAdminMembershipsGetADM(
    userIri: IRI,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[Seq[ProjectADM]] =
    // ToDo: only allow system user
    // ToDo: this is a bit of a hack since the ProjectAdmin group doesn't really exist.
    for {
      sparqlQueryString <- Future(
                             org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                               .getUserByIri(
                                 userIri = userIri
                               )
                               .toString()
                           )

      userDataQueryResponse <- appActor.ask(SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResult]

      groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                                                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                                                  }

      /* the projects the user is member of */
      projectIris: Seq[IRI] = groupedUserData.get(OntologyConstants.KnoraAdmin.IsInProjectAdminGroup) match {
                                case Some(projects) => projects
                                case None           => Seq.empty[IRI]
                              }

      maybeProjectFutures: Seq[Future[Option[ProjectADM]]] =
        projectIris.map { projectIri =>
          appActor
            .ask(
              ProjectGetADM(
                identifier = ProjectIdentifierADM.Iri(projectIri)
              )
            )
            .mapTo[Option[ProjectADM]]
        }
      maybeProjects: Seq[Option[ProjectADM]] <- Future.sequence(maybeProjectFutures)
      projects: Seq[ProjectADM]               = maybeProjects.flatten

    } yield projects

  /**
   * Returns the user's project admin group memberships, where the result contains the IRIs of the projects the user
   * is a member of the project admin group.
   *
   * @param userIri              the user's IRI.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserProjectMembershipsGetResponseV1]].
   */
  private def userProjectAdminMembershipsGetRequestADM(
    userIri: IRI,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[UserProjectAdminMembershipsGetResponseADM] =
    // ToDo: which user is allowed to do this operation?
    // ToDo: check permissions
    for {
      userExists <- userExists(userIri)
      _ = if (!userExists) {
            throw BadRequestException(s"User $userIri does not exist.")
          }

      projects: Seq[ProjectADM] <- userProjectAdminMembershipsGetADM(
                                     userIri = userIri,
                                     requestingUser = KnoraSystemInstances.Users.SystemUser,
                                     apiRequestID = apiRequestID
                                   )
    } yield UserProjectAdminMembershipsGetResponseADM(projects = projects)

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
  ): Future[UserOperationResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def userProjectAdminMembershipAddRequestTask(
      userIri: IRI,
      projectIri: IRI,
      requestingUser: UserADM,
      apiRequestID: UUID
    ): Future[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. project admin or system admin)
        _ <-
          Future(
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
        currentProjectMemberships <- userProjectMembershipsGetADM(
                                       userIri = userIri,
                                       requestingUser = KnoraSystemInstances.Users.SystemUser
                                     )

        currentProjectMembershipIris = currentProjectMemberships.map(_.id)

        // check if user is already project member and if not throw exception

        _ = if (!currentProjectMembershipIris.contains(projectIri)) {
              throw BadRequestException(
                s"User $userIri is not a member of project $projectIri. A user needs to be a member of the project to be added as project admin."
              )
            }

        // get users current project admin membership list
        currentProjectAdminMemberships <- userProjectAdminMembershipsGetADM(
                                            userIri = userIri,
                                            requestingUser = KnoraSystemInstances.Users.SystemUser,
                                            apiRequestID = apiRequestID
                                          )

        currentProjectAdminMembershipIris: Seq[IRI] = currentProjectAdminMemberships.map(_.id)

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
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = apiRequestID
                  )
      } yield result

    for {
      // run the task with an IRI lock
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      userIri,
                      () => userProjectAdminMembershipAddRequestTask(userIri, projectIri, requestingUser, apiRequestID)
                    )
    } yield taskResult

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
  ): Future[UserOperationResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def userProjectAdminMembershipRemoveRequestTask(
      userIri: IRI,
      projectIri: IRI,
      requestingUser: UserADM,
      apiRequestID: UUID
    ): Future[UserOperationResponseADM] =
      for {
        // check if the requesting user is allowed to perform updates (i.e. requesting updates own information or is system admin)
        _ <-
          Future(
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
        currentProjectAdminMemberships <- userProjectAdminMembershipsGetADM(
                                            userIri = userIri,
                                            requestingUser = KnoraSystemInstances.Users.SystemUser,
                                            apiRequestID = apiRequestID
                                          )

        currentProjectAdminMembershipIris: Seq[IRI] = currentProjectAdminMemberships.map(_.id)

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
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = apiRequestID
                  )
      } yield result

    for {
      // run the task with an IRI lock
      taskResult <-
        IriLocker.runWithIriLock(
          apiRequestID,
          userIri,
          () => userProjectAdminMembershipRemoveRequestTask(userIri, projectIri, requestingUser, apiRequestID)
        )
    } yield taskResult
  }

  /**
   * Returns the user's group memberships as a sequence of [[GroupADM]]
   *
   * @param userIri              the IRI of the user.
   * @param requestingUser       the requesting user.
   * @return a sequence of [[GroupADM]].
   */
  private def userGroupMembershipsGetADM(
    userIri: IRI,
    requestingUser: UserADM
  ): Future[Seq[GroupADM]] =
    for {
      maybeUserADM: Option[UserADM] <- getSingleUserADM(
                                         identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                                         userInformationType = UserInformationTypeADM.Full,
                                         requestingUser = KnoraSystemInstances.Users.SystemUser
                                       )

      groups: Seq[GroupADM] = maybeUserADM match {
                                case Some(user) =>
                                  log.debug(
                                    "userGroupMembershipsGetADM - user found. Returning his groups: {}.",
                                    user.groups
                                  )
                                  user.groups
                                case None =>
                                  log.debug("userGroupMembershipsGetADM - user not found. Returning empty seq.")
                                  Seq.empty[GroupADM]
                              }

    } yield groups

  /**
   * Returns the user's group memberships as a [[UserGroupMembershipsGetResponseADM]]
   *
   * @param userIri              the IRI of the user.
   *
   * @param requestingUser       the requesting user.
   * @return a [[UserGroupMembershipsGetResponseADM]].
   */
  private def userGroupMembershipsGetRequestADM(
    userIri: IRI,
    requestingUser: UserADM
  ): Future[UserGroupMembershipsGetResponseADM] =
    for {
      groups: Seq[GroupADM] <- userGroupMembershipsGetADM(
                                 userIri = userIri,
                                 requestingUser = requestingUser
                               )
    } yield UserGroupMembershipsGetResponseADM(groups = groups)

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
  ): Future[UserOperationResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def userGroupMembershipAddRequestTask(
      userIri: IRI,
      groupIri: IRI,
      requestingUser: UserADM,
      apiRequestID: UUID
    ): Future[UserOperationResponseADM] =
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
        maybeGroupADM <- appActor
                           .ask(
                             GroupGetADM(
                               groupIri = groupIri
                             )
                           )
                           .mapTo[Option[GroupADM]]

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
        currentGroupMemberships = userToChange.groups

        currentGroupMembershipIris: Seq[IRI] = currentGroupMemberships.map(_.id)

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
                    requestingUser = KnoraSystemInstances.Users.SystemUser,
                    apiRequestID = apiRequestID
                  )
      } yield result

    for {
      // run the task with an IRI lock
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      userIri,
                      () => userGroupMembershipAddRequestTask(userIri, groupIri, requestingUser, apiRequestID)
                    )
    } yield taskResult

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
  ): Future[UserOperationResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def userGroupMembershipRemoveRequestTask(
      userIri: IRI,
      groupIri: IRI,
      requestingUser: UserADM,
      apiRequestID: UUID
    ): Future[UserOperationResponseADM] =
      for {
        // check if user exists
        userExists <- userExists(userIri)
        _           = if (!userExists) throw NotFoundException(s"The user $userIri does not exist.")

        // check if group exists
        projectExists <- groupExists(groupIri)
        _              = if (!projectExists) throw NotFoundException(s"The group $groupIri does not exist.")

        // get group's info. we need the project IRI.
        maybeGroupADM <- appActor
                           .ask(
                             GroupGetADM(
                               groupIri = groupIri
                             )
                           )
                           .mapTo[Option[GroupADM]]

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
        currentGroupMemberships <- userGroupMembershipsGetRequestADM(
                                     userIri = userIri,
                                     requestingUser = KnoraSystemInstances.Users.SystemUser
                                   )

        currentGroupMembershipIris: Seq[IRI] = currentGroupMemberships.groups.map(_.id)

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
                    requestingUser = requestingUser,
                    apiRequestID = apiRequestID
                  )
      } yield result

    for {
      // run the task with an IRI lock
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      userIri,
                      () => userGroupMembershipRemoveRequestTask(userIri, groupIri, requestingUser, apiRequestID)
                    )
    } yield taskResult
  }

  /**
   * Updates an existing user. Should not be directly used from the receive method.
   *
   * @param userIri              the IRI of the existing user that we want to update.
   * @param userUpdatePayload    the updated information.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]].
   * @throws BadRequestException         if necessary parameters are not supplied.
   * @throws UpdateNotPerformedException if the update was not performed.
   */
  private def updateUserADM(
    userIri: IRI,
    userUpdatePayload: UserChangeRequestADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[UserOperationResponseADM] = {

    log.debug("updateUserADM - userUpdatePayload: {}", userUpdatePayload)

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
                                    stringFormatter.toSparqlEncodedString(
                                      givenName.value,
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
                                     stringFormatter.toSparqlEncodedString(
                                       familyName.value,
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
      updateUserSparqlString <- Future(
                                  org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                                    .updateUser(
                                      adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
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
                                    .toString
                                )

      // we are changing the user, so lets get rid of the cached copy
      _ <- invalidateCachedUserADM(maybeCurrentUser)

      // write the updated user to the triplestore
      _ <- appActor.ask(SparqlUpdateRequest(updateUserSparqlString)).mapTo[SparqlUpdateResponse]

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
              projects <- userProjectMembershipsGetADM(
                            userIri = userIri,
                            requestingUser = requestingUser
                          )
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
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]].
   * @throws BadRequestException         if necessary parameters are not supplied.
   * @throws UpdateNotPerformedException if the update was not performed.
   */
  private def updateUserPasswordADM(
    userIri: IRI,
    password: Password,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[UserOperationResponseADM] = {

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
      _ = invalidateCachedUserADM(maybeCurrentUser)

      // update the password
      updateUserSparqlString <- Future(
                                  org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                                    .updateUserPassword(
                                      adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
                                      userIri = userIri,
                                      newPassword = password.value
                                    )
                                    .toString
                                )

      updateResult <- appActor.ask(SparqlUpdateRequest(updateUserSparqlString)).mapTo[SparqlUpdateResponse]

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
   * @param requestingUser          a [[UserADM]] object containing information about the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a [[UserOperationResponseADM]].
   */
  private def createNewUserADM(
    userCreatePayloadADM: UserCreatePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[UserOperationResponseADM] = {

    log.debug("createNewUserADM - userCreatePayloadADM: {}", userCreatePayloadADM)

    /**
     * The actual task run with an IRI lock.
     */
    def createNewUserTask(userCreatePayloadADM: UserCreatePayloadADM) =
      for {
        // check if username is unique
        usernameTaken: Boolean <- userByUsernameExists(Some(userCreatePayloadADM.username))
        _ = if (usernameTaken) {
              throw DuplicateValueException(
                s"User with the username '${userCreatePayloadADM.username.value}' already exists"
              )
            }

        // check if email is unique
        emailTaken: Boolean <- userByEmailExists(Some(userCreatePayloadADM.email))
        _ = if (emailTaken) {
              throw DuplicateValueException(
                s"User with the email '${userCreatePayloadADM.email.value}' already exists"
              )
            }

        // check the custom IRI; if not given, create an unused IRI
        customUserIri: Option[SmartIri] = userCreatePayloadADM.id.map(_.value.toSmartIri)
        userIri: IRI                   <- checkOrCreateEntityIri(customUserIri, stringFormatter.makeRandomPersonIri)

        // hash password
        encoder        = new BCryptPasswordEncoder(responderData.appConfig.bcryptPasswordStrength)
        hashedPassword = encoder.encode(userCreatePayloadADM.password.value)

        // Create the new user.
        createNewUserSparqlString = org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                                      .createNewUser(
                                        adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
                                        userIri = userIri,
                                        userClassIri = OntologyConstants.KnoraAdmin.User,
                                        username = stringFormatter.toSparqlEncodedString(
                                          userCreatePayloadADM.username.value,
                                          errorFun = throw BadRequestException(
                                            s"The supplied username: '${userCreatePayloadADM.username.value}' is not valid."
                                          )
                                        ),
                                        email = stringFormatter.toSparqlEncodedString(
                                          userCreatePayloadADM.email.value,
                                          errorFun = throw BadRequestException(
                                            s"The supplied email: '${userCreatePayloadADM.email.value}' is not valid."
                                          )
                                        ),
                                        password = hashedPassword,
                                        givenName = stringFormatter.toSparqlEncodedString(
                                          userCreatePayloadADM.givenName.value,
                                          errorFun = throw BadRequestException(
                                            s"The supplied given name: '${userCreatePayloadADM.givenName.value}' is not valid."
                                          )
                                        ),
                                        familyName = stringFormatter.toSparqlEncodedString(
                                          userCreatePayloadADM.familyName.value,
                                          errorFun = throw BadRequestException(
                                            s"The supplied family name: '${userCreatePayloadADM.familyName.value}' is not valid."
                                          )
                                        ),
                                        status = userCreatePayloadADM.status.value,
                                        preferredLanguage = stringFormatter.toSparqlEncodedString(
                                          userCreatePayloadADM.lang.value,
                                          errorFun = throw BadRequestException(
                                            s"The supplied language: '${userCreatePayloadADM.lang.value}' is not valid."
                                          )
                                        ),
                                        systemAdmin = userCreatePayloadADM.systemAdmin.value
                                      )
                                      .toString

        _ = log.debug(s"createNewUser: $createNewUserSparqlString")

        createNewUserResponse <- appActor
                                   .ask(SparqlUpdateRequest(createNewUserSparqlString))
                                   .mapTo[SparqlUpdateResponse]

        // try to retrieve newly created user (will also add to cache)
        maybeNewUserADM: Option[UserADM] <- getSingleUserADM(
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
        _                        = log.debug("createNewUserADM - created new user: {}", newUserADM)
        userOperationResponseADM = UserOperationResponseADM(newUserADM.ofType(UserInformationTypeADM.Restricted))

      } yield userOperationResponseADM
    for {
      // run user creation with an global IRI lock
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      USERS_GLOBAL_LOCK_IRI,
                      () => createNewUserTask(userCreatePayloadADM)
                    )
    } yield taskResult
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
  ): Future[Option[UserADM]] = tracedFuture("admin-user-get-user-from-cache-or-triplestore") {
    if (cacheServiceSettings.cacheServiceEnabled) {
      // caching enabled
      getUserFromCache(identifier).flatMap {
        case None =>
          // none found in cache. getting from triplestore.
          getUserFromTriplestore(identifier = identifier).flatMap {
            case None =>
              // also none found in triplestore. finally returning none.
              log.debug("getUserFromCacheOrTriplestore - not found in cache and in triplestore")
              FastFuture.successful(None)
            case Some(user) =>
              // found a user in the triplestore. need to write to cache.
              log.debug(
                "getUserFromCacheOrTriplestore - not found in cache but found in triplestore. need to write to cache."
              )
              // writing user to cache and afterwards returning the user found in the triplestore
              writeUserADMToCache(user)
              FastFuture.successful(Some(user))
          }
        case Some(user) =>
          log.debug("getUserFromCacheOrTriplestore - found in cache. returning user.")
          FastFuture.successful(Some(user))
      }
    } else {
      // caching disabled
      log.debug("getUserFromCacheOrTriplestore - caching disabled. getting from triplestore.")
      getUserFromTriplestore(identifier = identifier)
    }
  }

  /**
   * Tries to retrieve a [[UserADM]] from the triplestore.
   *
   * @param identifier The identifier of the user (can be IRI, e-mail or username)
   * @return a [[Option[UserADM]]]
   */
  private def getUserFromTriplestore(
    identifier: UserIdentifierADM
  ): Future[Option[UserADM]] =
    for {
      sparqlQueryString <- Future(
                             org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                               .getUsers(
                                 maybeIri = identifier.toIriOption,
                                 maybeUsername = identifier.toUsernameOption,
                                 maybeEmail = identifier.toEmailOption
                               )
                               .toString()
                           )

      userQueryResponse <- appActor
                             .ask(
                               SparqlExtendedConstructRequest(
                                 sparql = sparqlQueryString
                               )
                             )
                             .mapTo[SparqlExtendedConstructResponse]

      maybeUserADM: Option[UserADM] <-
        if (userQueryResponse.statements.nonEmpty) {
          log.debug("getUserFromTriplestore - triplestore hit for: {}", identifier)
          statements2UserADM(
            statements = userQueryResponse.statements.head
          )
        } else {
          log.debug("getUserFromTriplestore - no triplestore hit for: {}", identifier)
          FastFuture.successful(None)
        }
    } yield maybeUserADM

  /**
   * Helper method used to create a [[UserADM]] from the [[SparqlExtendedConstructResponse]] containing user data.
   *
   * @param statements           result from the SPARQL query containing user data.
   * @return a [[Option[UserADM]]]
   */
  private def statements2UserADM(
    statements: (SubjectV2, Map[SmartIri, Seq[LiteralV2]])
  ): Future[Option[UserADM]] = {

    // log.debug("statements2UserADM - statements: {}", statements)

    val userIri: IRI                            = statements._1.toString
    val propsMap: Map[SmartIri, Seq[LiteralV2]] = statements._2

    // log.debug("statements2UserADM - userIri: {}", userIri)

    if (propsMap.nonEmpty) {

      /* the groups the user is member of (only explicit groups) */
      val groupIris: Seq[IRI] = propsMap.get(OntologyConstants.KnoraAdmin.IsInGroup.toSmartIri) match {
        case Some(groups) => groups.map(_.asInstanceOf[IriLiteralV2].value)
        case None         => Seq.empty[IRI]
      }

      // log.debug(s"statements2UserADM - groupIris: {}", MessageUtil.toSource(groupIris))

      /* the projects the user is member of (only explicit projects) */
      val projectIris: Seq[IRI] = propsMap.get(OntologyConstants.KnoraAdmin.IsInProject.toSmartIri) match {
        case Some(projects) => projects.map(_.asInstanceOf[IriLiteralV2].value)
        case None           => Seq.empty[IRI]
      }

      // log.debug(s"statements2UserADM - projectIris: {}", MessageUtil.toSource(projectIris))

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
        permissionData <- appActor
                            .ask(
                              PermissionDataGetADM(
                                projectIris = projectIris,
                                groupIris = groupIris,
                                isInProjectAdminGroups = isInProjectAdminGroups,
                                isInSystemAdminGroup = isInSystemAdminGroup,
                                requestingUser = KnoraSystemInstances.Users.SystemUser
                              )
                            )
                            .mapTo[PermissionsDataADM]

        maybeGroupFutures: Seq[Future[Option[GroupADM]]] = groupIris.map { groupIri =>
                                                             appActor
                                                               .ask(
                                                                 GroupGetADM(
                                                                   groupIri = groupIri
                                                                 )
                                                               )
                                                               .mapTo[Option[GroupADM]]
                                                           }
        maybeGroups: Seq[Option[GroupADM]] <- Future.sequence(maybeGroupFutures)
        groups: Seq[GroupADM]               = maybeGroups.flatten

        // _ = log.debug("statements2UserADM - groups: {}", MessageUtil.toSource(groups))

        maybeProjectFutures: Seq[Future[Option[ProjectADM]]] =
          projectIris.map { projectIri =>
            appActor
              .ask(
                ProjectGetADM(
                  identifier = ProjectIdentifierADM.Iri(projectIri)
                )
              )
              .mapTo[Option[ProjectADM]]
          }
        maybeProjects: Seq[Option[ProjectADM]] <- Future.sequence(maybeProjectFutures)
        projects: Seq[ProjectADM]               = maybeProjects.flatten

        // _ = log.debug("statements2UserADM - projects: {}", MessageUtil.toSource(projects))

        /* construct the user profile from the different parts */
        user = UserADM(
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
                 password = propsMap
                   .get(OntologyConstants.KnoraAdmin.Password.toSmartIri)
                   .map(_.head.asInstanceOf[StringLiteralV2].value),
                 token = None,
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
                     OntologyConstants.KnoraAdmin.Status.toSmartIri,
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
                 groups = groups,
                 projects = projects,
                 sessionId = None,
                 permissions = permissionData
               )
        // _ = log.debug(s"statements2UserADM - user: {}", user.toString)

        result: Option[UserADM] = Some(user)
      } yield result

    } else {
      FastFuture.successful(None)
    }
  }

  /**
   * Helper method for checking if a user exists.
   *
   * @param userIri the IRI of the user.
   * @return a [[Boolean]].
   */
  private def userExists(userIri: IRI): Future[Boolean] =
    for {
      askString <-
        Future(org.knora.webapi.messages.twirl.queries.sparql.admin.txt.checkUserExists(userIri = userIri).toString)
      // _ = log.debug("userExists - query: {}", askString)
      checkUserExistsResponse <- appActor.ask(SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
    } yield checkUserExistsResponse.result

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
  ): Future[Boolean] =
    maybeUsername match {
      case Some(username) =>
        if (maybeCurrent.contains(username.value)) {
          FastFuture.successful(true)
        } else {
          stringFormatter.validateUsername(
            username.value,
            throw BadRequestException(s"The username '${username.value}' contains invalid characters")
          )

          for {
            askString <- Future(
                           org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                             .checkUserExistsByUsername(username = username.value)
                             .toString
                         )
            // _ = log.debug("userExists - query: {}", askString)

            checkUserExistsResponse <- appActor.ask(SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
          } yield checkUserExistsResponse.result
        }

      case None => FastFuture.successful(false)
    }

  /**
   * Helper method for checking if an email is already registered.
   *
   * @param maybeEmail   the email of the user.
   * @param maybeCurrent the current email of the user.
   * @return a [[Boolean]].
   */
  private def userByEmailExists(maybeEmail: Option[Email], maybeCurrent: Option[String] = None): Future[Boolean] =
    maybeEmail match {
      case Some(email) =>
        if (maybeCurrent.contains(email.value)) {
          FastFuture.successful(true)
        } else {
          stringFormatter.validateEmailAndThrow(
            email.value,
            throw BadRequestException(s"The email address '${email.value}' is invalid")
          )

          for {
            askString <- Future(
                           org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                             .checkUserExistsByEmail(email = email.value)
                             .toString
                         )
            // _ = log.debug("userExists - query: {}", askString)

            checkUserExistsResponse <- appActor.ask(SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
          } yield checkUserExistsResponse.result
        }

      case None => FastFuture.successful(false)
    }

  /**
   * Helper method for checking if a project exists.
   *
   * @param projectIri the IRI of the project.
   * @return a [[Boolean]].
   */
  private def projectExists(projectIri: IRI): Future[Boolean] =
    for {
      askString <- Future(
                     org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                       .checkProjectExistsByIri(projectIri = projectIri)
                       .toString
                   )
      // _ = log.debug("projectExists - query: {}", askString)

      checkUserExistsResponse <- appActor.ask(SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
      result                   = checkUserExistsResponse.result

    } yield result

  /**
   * Helper method for checking if a group exists.
   *
   * @param groupIri the IRI of the group.
   * @return a [[Boolean]].
   */
  private def groupExists(groupIri: IRI): Future[Boolean] =
    for {
      askString <-
        Future(
          org.knora.webapi.messages.twirl.queries.sparql.admin.txt.checkGroupExistsByIri(groupIri = groupIri).toString
        )
      // _ = log.debug("groupExists - query: {}", askString)

      checkUserExistsResponse <- appActor.ask(SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
      result                   = checkUserExistsResponse.result

    } yield result

  /**
   * Tries to retrieve a [[UserADM]] from the cache.
   *
   * @param identifier the user's identifier (could be IRI, e-mail or username)
   * @return a [[Option[UserADM]]]
   */
  private def getUserFromCache(identifier: UserIdentifierADM): Future[Option[UserADM]] =
    tracedFuture("admin-user-get-user-from-cache") {
      val result = appActor.ask(CacheServiceGetUserADM(identifier)).mapTo[Option[UserADM]]
      result.map {
        case Some(user) =>
          log.debug("getUserFromCache - cache hit for: {}", identifier)
          Some(user)
        case None =>
          log.debug("getUserFromCache - no cache hit for: {}", identifier)
          None
      }
    }

  /**
   * Writes the user profile to cache.
   *
   * @param user a [[UserADM]].
   * @return Unit
   * @throws ApplicationCacheException when there is a problem with writing the user's profile to cache.
   */
  private def writeUserADMToCache(user: UserADM): Future[Unit] = for {
    _ <- appActor.ask(CacheServicePutUserADM(user))
    _ <- Future(log.debug(s"writeUserADMToCache done - user: ${user.id}"))
  } yield ()

  /**
   * Removes the user from cache.
   *
   * @param maybeUser the optional user which is removed from the cache
   * @return a [[Unit]]
   */
  private def invalidateCachedUserADM(maybeUser: Option[UserADM]): Future[Unit] =
    if (cacheServiceSettings.cacheServiceEnabled) {
      val keys: Set[String] = Seq(maybeUser.map(_.id), maybeUser.map(_.email), maybeUser.map(_.username)).flatten.toSet
      // only send to Redis if keys are not empty
      if (keys.nonEmpty) {
        val result = appActor.ask(CacheServiceRemoveValues(keys))
        result.map { res =>
          log.debug("invalidateCachedUserADM - result: {}", res)
        }
      } else {
        // since there was nothing to remove, we can immediately return
        FastFuture.successful(())
      }
    } else {
      // caching is turned off, so nothing to do.
      FastFuture.successful(())
    }

}
