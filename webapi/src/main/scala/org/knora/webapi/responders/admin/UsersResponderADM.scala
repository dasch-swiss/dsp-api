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

import java.util.UUID

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi.exceptions._
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.instrumentation.InstrumentationSupport
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.admin.responder.groupsmessages.{GroupADM, GroupGetADM}
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionDataGetADM, PermissionsDataADM}
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectGetADM, ProjectIdentifierADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM.UserInformationTypeADM
import org.knora.webapi.messages.admin.responder.usersmessages.{UserUpdatePayloadADM, _}
import org.knora.webapi.messages.store.cacheservicemessages.{CacheServiceGetUserADM, CacheServicePutUserADM, CacheServiceRemoveValues}
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.{KnoraSystemInstances, ResponderData}
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.{OntologyConstants, SmartIri}
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.{exceptions, _}
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

import scala.concurrent.Future

/**
 * Provides information about Knora users to other responders.
 */
class UsersResponderADM(responderData: ResponderData) extends Responder(responderData) with InstrumentationSupport {

    // The IRI used to lock user creation and update
    private val USERS_GLOBAL_LOCK_IRI = "http://rdfh.ch/users"

    /**
     * Receives a message extending [[UsersResponderRequestV1]], and returns an appropriate message.
     */
    def receive(msg: UsersResponderRequestADM) = msg match {
        case UsersGetADM(userInformationTypeADM, featureFactoryConfig, requestingUser) => getAllUserADM(userInformationTypeADM, featureFactoryConfig, requestingUser)
        case UsersGetRequestADM(userInformationTypeADM, featureFactoryConfig, requestingUser) => getAllUserADMRequest(userInformationTypeADM, featureFactoryConfig, requestingUser)
        case UserGetADM(identifier, userInformationTypeADM, featureFactoryConfig, requestingUser) => getSingleUserADM(identifier, userInformationTypeADM, featureFactoryConfig, requestingUser)
        case UserGetRequestADM(identifier, userInformationTypeADM, featureFactoryConfig, requestingUser) => getSingleUserADMRequest(identifier, userInformationTypeADM, featureFactoryConfig, requestingUser)
        case UserCreateRequestADM(createRequest, featureFactoryConfig, requestingUser, apiRequestID) => createNewUserADM(createRequest, featureFactoryConfig, requestingUser, apiRequestID)
        case UserChangeBasicUserInformationRequestADM(userIri, changeUserRequest, featureFactoryConfig, requestingUser, apiRequestID) => changeBasicUserInformationADM(userIri, changeUserRequest, featureFactoryConfig, requestingUser, apiRequestID)
        case UserChangePasswordRequestADM(userIri, changeUserRequest, featureFactoryConfig, requestingUser, apiRequestID) => changePasswordADM(userIri, changeUserRequest, featureFactoryConfig, requestingUser, apiRequestID)
        case UserChangeStatusRequestADM(userIri, changeUserRequest, featureFactoryConfig, requestingUser, apiRequestID) => changeUserStatusADM(userIri, changeUserRequest, featureFactoryConfig, requestingUser, apiRequestID)
        case UserChangeSystemAdminMembershipStatusRequestADM(userIri, changeSystemAdminMembershipStatusRequest, featureFactoryConfig, requestingUser, apiRequestID) => changeUserSystemAdminMembershipStatusADM(userIri, changeSystemAdminMembershipStatusRequest, featureFactoryConfig, requestingUser, apiRequestID)
        case UserProjectMembershipsGetRequestADM(userIri, featureFactoryConfig, requestingUser) => userProjectMembershipsGetRequestADM(userIri, featureFactoryConfig, requestingUser)
        case UserProjectMembershipAddRequestADM(userIri, projectIri, featureFactoryConfig, requestingUser, apiRequestID) => userProjectMembershipAddRequestADM(userIri, projectIri, featureFactoryConfig, requestingUser, apiRequestID)
        case UserProjectMembershipRemoveRequestADM(userIri, projectIri, featureFactoryConfig, requestingUser, apiRequestID) => userProjectMembershipRemoveRequestADM(userIri, projectIri, featureFactoryConfig, requestingUser, apiRequestID)
        case UserProjectAdminMembershipsGetRequestADM(userIri, featureFactoryConfig, requestingUser, apiRequestID) => userProjectAdminMembershipsGetRequestADM(userIri, featureFactoryConfig, requestingUser, apiRequestID)
        case UserProjectAdminMembershipAddRequestADM(userIri, projectIri, featureFactoryConfig, requestingUser, apiRequestID) => userProjectAdminMembershipAddRequestADM(userIri, projectIri, featureFactoryConfig, requestingUser, apiRequestID)
        case UserProjectAdminMembershipRemoveRequestADM(userIri, projectIri, featureFactoryConfig, requestingUser, apiRequestID) => userProjectAdminMembershipRemoveRequestADM(userIri, projectIri, featureFactoryConfig, requestingUser, apiRequestID)
        case UserGroupMembershipsGetRequestADM(userIri, featureFactoryConfig, requestingUser) => userGroupMembershipsGetRequestADM(userIri, featureFactoryConfig, requestingUser)
        case UserGroupMembershipAddRequestADM(userIri, projectIri, featureFactoryConfig, requestingUser, apiRequestID) => userGroupMembershipAddRequestADM(userIri, projectIri, featureFactoryConfig, requestingUser, apiRequestID)
        case UserGroupMembershipRemoveRequestADM(userIri, projectIri, featureFactoryConfig, requestingUser, apiRequestID) => userGroupMembershipRemoveRequestADM(userIri, projectIri, featureFactoryConfig, requestingUser, apiRequestID)
        case other => handleUnexpectedMessage(other, log, this.getClass.getName)
    }


    /**
     * Gets all the users and returns them as a sequence of [[UserADM]].
     *
     * @param userInformationType  the extent of the information returned.
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the user initiating the request.
     * @return all the users as a sequence of [[UserADM]].
     */
    private def getAllUserADM(userInformationType: UserInformationTypeADM,
                              featureFactoryConfig: FeatureFactoryConfig,
                              requestingUser: UserADM): Future[Seq[UserADM]] = {

        //log.debug("getAllUserADM")

        for {
            _ <- Future(
                if (!requestingUser.permissions.isSystemAdmin && !requestingUser.permissions.isProjectAdminInAnyProject() && !requestingUser.isSystemUser) {
                    throw ForbiddenException("ProjectAdmin or SystemAdmin permissions are required.")
                }
            )

            sparqlQueryString <- Future(org.knora.webapi.messages.twirl.queries.sparql.admin.txt.getUsers(
                triplestore = settings.triplestoreType,
                maybeIri = None,
                maybeUsername = None,
                maybeEmail = None
            ).toString())

            usersResponse <- (storeManager ? SparqlExtendedConstructRequest(
                sparql = sparqlQueryString,
                featureFactoryConfig = featureFactoryConfig
            )).mapTo[SparqlExtendedConstructResponse]

            statements = usersResponse.statements.toList

            // _ = log.debug("getAllUserADM - statements: {}", statements)

            users: Seq[UserADM] = statements.map {
                case (userIri: SubjectV2, propsMap: Map[SmartIri, Seq[LiteralV2]]) =>

                    UserADM(
                        id = userIri.toString,
                        username = propsMap.getOrElse(OntologyConstants.KnoraAdmin.Username.toSmartIri, throw InconsistentRepositoryDataException(s"User: $userIri has no 'username' defined.")).head.asInstanceOf[StringLiteralV2].value,
                        email = propsMap.getOrElse(OntologyConstants.KnoraAdmin.Email.toSmartIri, throw InconsistentRepositoryDataException(s"User: $userIri has no 'email' defined.")).head.asInstanceOf[StringLiteralV2].value,
                        givenName = propsMap.getOrElse(OntologyConstants.KnoraAdmin.GivenName.toSmartIri, throw InconsistentRepositoryDataException(s"User: $userIri has no 'givenName' defined.")).head.asInstanceOf[StringLiteralV2].value,
                        familyName = propsMap.getOrElse(OntologyConstants.KnoraAdmin.FamilyName.toSmartIri, throw InconsistentRepositoryDataException(s"User: $userIri has no 'familyName' defined.")).head.asInstanceOf[StringLiteralV2].value,
                        status = propsMap.getOrElse(OntologyConstants.KnoraAdmin.Status.toSmartIri, throw InconsistentRepositoryDataException(s"User: $userIri has no 'status' defined.")).head.asInstanceOf[BooleanLiteralV2].value,
                        lang = propsMap.getOrElse(OntologyConstants.KnoraAdmin.PreferredLanguage.toSmartIri, throw InconsistentRepositoryDataException(s"User: $userIri has no 'preferedLanguage' defined.")).head.asInstanceOf[StringLiteralV2].value)
            }

        } yield users.sorted
    }

    /**
     * Gets all the users and returns them as a [[UsersGetResponseADM]].
     *
     * @param userInformationType  the extent of the information returned.
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the user initiating the request.
     * @return all the users as a [[UsersGetResponseV1]].
     */
    private def getAllUserADMRequest(userInformationType: UserInformationTypeADM,
                                     featureFactoryConfig: FeatureFactoryConfig,
                                     requestingUser: UserADM): Future[UsersGetResponseADM] = {
        for {
            maybeUsersListToReturn <- getAllUserADM(
                userInformationType = userInformationType,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = requestingUser
            )

            result = maybeUsersListToReturn match {
                case users: Seq[UserADM] if users.nonEmpty =>
                    UsersGetResponseADM(users = users)
                case _ =>
                    throw NotFoundException(s"No users found")
            }
        } yield result
    }

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
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the user initiating the request.
     * @param skipCache            the flag denotes to skip the cache and instead
     *                             get data from the triplestore
     * @return a [[UserADM]] describing the user.
     */
    private def getSingleUserADM(identifier: UserIdentifierADM,
                                 userInformationType: UserInformationTypeADM,
                                 featureFactoryConfig: FeatureFactoryConfig,
                                 requestingUser: UserADM,
                                 skipCache: Boolean = false): Future[Option[UserADM]] = tracedFuture("admin-get-user") {

        log.debug(s"getSingleUserADM - id: {}, type: {}, requester: {}, skipCache: {}",
            identifier.value,
            userInformationType,
            requestingUser.username,
            skipCache)

        for {
            maybeUserADM <- if (skipCache) {
                // getting directly from triplestore
                getUserFromTriplestore(identifier = identifier, featureFactoryConfig = featureFactoryConfig)
            } else {
                // getting from cache or triplestore
                getUserFromCacheOrTriplestore(identifier, featureFactoryConfig)
            }

            // return the correct amount of information depending on either the request or user permission
            finalResponse: Option[UserADM] = if (requestingUser.permissions.isSystemAdmin || requestingUser.isSelf(identifier) || requestingUser.isSystemUser) {
                // return everything or what was requested
                maybeUserADM.map(user => user.ofType(userInformationType))
            } else {
                // return only public information
                maybeUserADM.map(user => user.ofType(UserInformationTypeADM.PUBLIC))
            }

            _ = if (finalResponse.nonEmpty) {
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
    private def getSingleUserADMRequest(identifier: UserIdentifierADM,
                                        userInformationType: UserInformationTypeADM,
                                        featureFactoryConfig: FeatureFactoryConfig,
                                        requestingUser: UserADM): Future[UserResponseADM] = {
        for {
            maybeUserADM <- getSingleUserADM(
                identifier = identifier,
                userInformationType = userInformationType,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = requestingUser
            )

            result = maybeUserADM match {
                case Some(user) => UserResponseADM(user = user)
                case None => throw NotFoundException(s"User '${identifier.value}' not found")
            }
        } yield result
    }


    /**
     * Updates an existing user. Only basic user data information (username, email, givenName, familyName, lang)
     * can be changed. For changing the password or user status, use the separate methods.
     *
     * @param userIri              the IRI of the existing user that we want to update.
     * @param changeUserRequest    the updated information.
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @param apiRequestID         the unique api request ID.
     * @return a future containing a [[UserOperationResponseADM]].
     * @throws BadRequestException if the necessary parameters are not supplied.
     * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
     */
    private def changeBasicUserInformationADM(userIri: IRI,
                                              changeUserRequest: ChangeUserApiRequestADM,
                                              featureFactoryConfig: FeatureFactoryConfig,
                                              requestingUser: UserADM,
                                              apiRequestID: UUID): Future[UserOperationResponseADM] = {

        //log.debug(s"changeBasicUserDataV1: changeUserRequest: {}", changeUserRequest)

        /**
         * The actual change basic user data task run with an IRI lock.
         */
        def changeBasicUserDataTask(userIri: IRI, changeUserRequest: ChangeUserApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = for {

            // check if the requesting user is allowed to perform updates
            _ <- Future(
                if (!requestingUser.id.equalsIgnoreCase(userIri) && !requestingUser.permissions.isSystemAdmin) {
                    // not the user or a system admin
                    //log.debug("same user: {}, system admin: {}", userProfile.userData.user_id.contains(userIri), userProfile.permissionData.isSystemAdmin)
                    throw ForbiddenException("User information can only be changed by the user itself or a system administrator")
                }
            )

            // check if necessary information is present
            _ = if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

            parametersCount = List(changeUserRequest.username, changeUserRequest.email, changeUserRequest.givenName, changeUserRequest.familyName, changeUserRequest.lang).flatten.size
            _ = if (parametersCount == 0) throw BadRequestException("At least one parameter needs to be supplied. No data would be changed. Aborting request for changing of basic user data.")

            // get current user information
            currentUserInformation: Option[UserADM] <- getSingleUserADM(
                identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                userInformationType = UserInformationTypeADM.FULL,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser
            )

            // check if user exists
            _ = if (currentUserInformation.isEmpty) {
                throw BadRequestException(s"User ${userIri} does not exist")
            }

            // check if we want to change the email
            emailTaken: Boolean <- userByEmailExists(changeUserRequest.email, Some(currentUserInformation.get.email))
            _ = if (emailTaken) {
                throw DuplicateValueException(s"User with the email '${changeUserRequest.email.get}' already exists")
            }

            // check if we want to change the username
            usernameTaken: Boolean <- userByUsernameExists(changeUserRequest.username, Some(currentUserInformation.get.username))
            _ = if (usernameTaken) {
                throw DuplicateValueException(s"User with the username '${changeUserRequest.username.get}' already exists")
            }

            userUpdatePayload = UserUpdatePayloadADM(
                username = changeUserRequest.username,
                email = changeUserRequest.email,
                givenName = changeUserRequest.givenName,
                familyName = changeUserRequest.familyName,
                lang = changeUserRequest.lang
            )

            // send change request as SystemUser
            result <- updateUserADM(
                userIri = userIri,
                userUpdatePayload = userUpdatePayload,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                apiRequestID = apiRequestID
            )
        } yield result

        for {
            // run the user update with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                USERS_GLOBAL_LOCK_IRI,
                () => changeBasicUserDataTask(userIri, changeUserRequest, requestingUser, apiRequestID)
            )
        } yield taskResult
    }


    /**
     * Change the users password. The old password needs to be supplied for security purposes.
     *
     * @param userIri              the IRI of the existing user that we want to update.
     * @param changeUserRequest    the current password of the requesting user and the new password.
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @param apiRequestID         the unique api request ID.
     * @return a future containing a [[UserOperationResponseADM]].
     * @throws BadRequestException if necessary parameters are not supplied.
     * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
     * @throws ForbiddenException  if the supplied old password doesn't match with the user's current password.
     * @throws NotFoundException   if the user is not found.
     */
    private def changePasswordADM(userIri: IRI,
                                  changeUserRequest: ChangeUserApiRequestADM,
                                  featureFactoryConfig: FeatureFactoryConfig,
                                  requestingUser: UserADM,
                                  apiRequestID: UUID): Future[UserOperationResponseADM] = {

        log.debug(s"changePasswordADM - userIri: {}", userIri)
        log.debug(s"changePasswordADM - changeUserRequest: {}", changeUserRequest)
        log.debug(s"changePasswordADM - requestingUser: {}", requestingUser)

        /**
         * The actual change password task run with an IRI lock.
         */
        def changePasswordTask(userIri: IRI, changeUserRequest: ChangeUserApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty"))
            _ = if (changeUserRequest.requesterPassword.isEmpty || changeUserRequest.newPassword.isEmpty) throw BadRequestException("The user's old and new password need to be both supplied")

            // check if the requesting user is allowed to perform password change. it needs to be either the user himself, or a system admin
            _ = if (!requestingUser.id.equalsIgnoreCase(userIri) && !requestingUser.permissions.isSystemAdmin) {
                // not the user or system admin
                throw ForbiddenException("User's password can only be changed by the user itself or a system admin.")
            }

            // check if supplied password matches requesting user's password
            _ = log.debug(s"changePasswordADM - requesterPassword: {}", changeUserRequest.requesterPassword.get)
            _ = if (!requestingUser.passwordMatch(changeUserRequest.requesterPassword.get)) {
                throw ForbiddenException("The supplied password does not match the requesting user's password.")
            }

            // create the update request
            encoder = new BCryptPasswordEncoder(settings.bcryptPasswordStrength)
            newHashedPassword = encoder.encode(changeUserRequest.newPassword.get)
            userUpdatePayload = UserUpdatePayloadADM(password = Some(newHashedPassword))

            // update the users password as SystemUser
            result <- updateUserADM(
                userIri = userIri,
                userUpdatePayload = userUpdatePayload,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                apiRequestID = apiRequestID
            )

        } yield result

        for {
            // run the change password task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => changePasswordTask(userIri, changeUserRequest, requestingUser, apiRequestID)
            )
        } yield taskResult
    }

    /**
     * Change the user's status (active / inactive).
     *
     * @param userIri              the IRI of the existing user that we want to update.
     * @param changeUserRequest    the new status.
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @param apiRequestID         the unique api request ID.
     * @return a future containing a [[UserOperationResponseADM]].
     * @throws BadRequestException if necessary parameters are not supplied.
     * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
     */
    private def changeUserStatusADM(userIri: IRI,
                                    changeUserRequest: ChangeUserApiRequestADM,
                                    featureFactoryConfig: FeatureFactoryConfig,
                                    requestingUser: UserADM,
                                    apiRequestID: UUID): Future[UserOperationResponseADM] = {

        log.debug(s"changeUserStatusADM - changeUserRequest: {}", changeUserRequest)

        /**
         * The actual change user status task run with an IRI lock.
         */
        def changeUserStatusTask(userIri: IRI, changeUserRequest: ChangeUserApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = for {

            _ <- Future(
                // check if necessary information is present
                if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")
            )
            _ = if (changeUserRequest.status.isEmpty) throw BadRequestException("New user status cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!requestingUser.id.equalsIgnoreCase(userIri) && !requestingUser.permissions.isSystemAdmin) {
                // not the user or a system admin
                // log.debug("same user: {}, system admin: {}", userProfile.userData.user_id.contains(userIri), userProfile.permissionData.isSystemAdmin)
                throw ForbiddenException("User's status can only be changed by the user itself or a system administrator")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadADM(status = changeUserRequest.status)

            result <- updateUserADM(
                userIri = userIri,
                userUpdatePayload = userUpdatePayload,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                apiRequestID = apiRequestID
            )

        } yield result

        for {
            // run the change status task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => changeUserStatusTask(userIri, changeUserRequest, requestingUser, apiRequestID)
            )
        } yield taskResult
    }

    /**
     * Change the user's system admin membership status (active / inactive).
     *
     * @param userIri              the IRI of the existing user that we want to update.
     * @param changeUserRequest    the new status.
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the user profile of the requesting user.
     * @param apiRequestID         the unique api request ID.
     * @return a future containing a [[UserOperationResponseADM]].
     * @throws BadRequestException if necessary parameters are not supplied.
     * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
     */
    private def changeUserSystemAdminMembershipStatusADM(userIri: IRI,
                                                         changeUserRequest: ChangeUserApiRequestADM,
                                                         featureFactoryConfig: FeatureFactoryConfig,
                                                         requestingUser: UserADM,
                                                         apiRequestID: UUID): Future[UserOperationResponseADM] = {

        //log.debug(s"changeUserSystemAdminMembershipStatusV1: changeUserRequest: {}", changeUserRequest)

        /**
         * The actual change user status task run with an IRI lock.
         */
        def changeUserSystemAdminMembershipStatusTask(userIri: IRI, changeUserRequest: ChangeUserApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty"))
            _ = if (changeUserRequest.systemAdmin.isEmpty) throw BadRequestException("New user system admin membership status cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!requestingUser.permissions.isSystemAdmin) {
                // not a system admin
                // log.debug("system admin: {}", userProfile.permissionData.isSystemAdmin)
                throw ForbiddenException("User's system admin membership can only be changed by a system administrator")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadADM(systemAdmin = changeUserRequest.systemAdmin)

            result <- updateUserADM(
                userIri = userIri,
                userUpdatePayload = userUpdatePayload,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                apiRequestID = apiRequestID
            )

        } yield result


        for {
            // run the change status task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => changeUserSystemAdminMembershipStatusTask(userIri, changeUserRequest, requestingUser, apiRequestID)
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
    private def userProjectMembershipsGetADM(userIri: IRI,
                                             featureFactoryConfig: FeatureFactoryConfig,
                                             requestingUser: UserADM): Future[Seq[ProjectADM]] = {
        for {
            maybeUser <- getSingleUserADM(
                identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                userInformationType = UserInformationTypeADM.FULL,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser
            )

            result = maybeUser match {
                case Some(userADM) => userADM.projects
                case None => Seq.empty[ProjectADM]
            }

            // _ = log.debug("userProjectMembershipsGetADM - userIri: {}, projects: {}", userIri, result)
        } yield result
    }

    /**
     * Returns the user's project memberships as [[UserProjectMembershipsGetResponseADM]].
     *
     * @param userIri        the user's IRI.
     * @param requestingUser the requesting user.
     * @return a [[UserProjectMembershipsGetResponseADM]].
     */
    private def userProjectMembershipsGetRequestADM(userIri: IRI,
                                                    featureFactoryConfig: FeatureFactoryConfig,
                                                    requestingUser: UserADM): Future[UserProjectMembershipsGetResponseADM] = {

        for {
            userExists <- userExists(userIri)
            _ = if (!userExists) {
                throw BadRequestException(s"User $userIri does not exist.")
            }

            projects: Seq[ProjectADM] <- userProjectMembershipsGetADM(
                userIri = userIri,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = requestingUser
            )

            result = UserProjectMembershipsGetResponseADM(projects = projects)
        } yield result
    }

    /**
     * Adds a user to a project.
     *
     * @param userIri              the user's IRI.
     * @param projectIri           the project's IRI.
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @param apiRequestID         the unique api request ID.
     * @return
     */
    private def userProjectMembershipAddRequestADM(userIri: IRI,
                                                   projectIri: IRI,
                                                   featureFactoryConfig: FeatureFactoryConfig,
                                                   requestingUser: UserADM,
                                                   apiRequestID: UUID): Future[UserOperationResponseADM] = {

        log.debug(s"userProjectMembershipAddRequestADM: userIri: {}, projectIri: {}", userIri, projectIri)

        /**
         * The actual task run with an IRI lock.
         */
        def userProjectMembershipAddRequestTask(userIri: IRI, projectIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty."))
            _ = if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
                // not a project or system admin
                // log.debug("project admin: {}, system admin: {}", userProfileV1.permissionData.isProjectAdmin(projectIri), userProfileV1.permissionData.isSystemAdmin)
                throw ForbiddenException("User's project membership can only be changed by a project or system administrator")
            }

            // check if user exists
            userExists <- userExists(userIri)
            _ = if (!userExists) throw NotFoundException(s"The user $userIri does not exist.")

            // check if project exists
            projectExists <- projectExists(projectIri)
            _ = if (!projectExists) throw NotFoundException(s"The project $projectIri does not exist.")

            // get users current project membership list
            currentProjectMemberships <- userProjectMembershipsGetRequestADM(
                userIri = userIri,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser
            )

            currentProjectMembershipIris: Seq[IRI] = currentProjectMemberships.projects.map(_.id)

            // check if user is already member and if not then append to list
            updatedProjectMembershipIris = if (!currentProjectMembershipIris.contains(projectIri)) {
                currentProjectMembershipIris :+ projectIri
            } else {
                throw BadRequestException(s"User $userIri is already member of project $projectIri.")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadADM(projects = Some(updatedProjectMembershipIris))

            result <- updateUserADM(
                userIri = userIri,
                userUpdatePayload = userUpdatePayload,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = requestingUser,
                apiRequestID = apiRequestID
            )
        } yield result


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
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @param apiRequestID         the unique api request ID.
     * @return
     */
    private def userProjectMembershipRemoveRequestADM(userIri: IRI,
                                                      projectIri: IRI,
                                                      featureFactoryConfig: FeatureFactoryConfig,
                                                      requestingUser: UserADM,
                                                      apiRequestID: UUID): Future[UserOperationResponseADM] = {

        // log.debug(s"userProjectMembershipRemoveRequestV1: userIri: {}, projectIri: {}", userIri, projectIri)

        /**
         * The actual task run with an IRI lock.
         */
        def userProjectMembershipRemoveRequestTask(userIri: IRI, projectIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty."))
            _ = if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
                // not a project or system admin
                // log.debug("project admin: {}, system admin: {}", userProfileV1.permissionData.isProjectAdmin(projectIri), userProfileV1.permissionData.isSystemAdmin)
                throw ForbiddenException("User's project membership can only be changed by a project or system administrator")
            }

            // check if user exists
            userExists <- userExists(userIri)
            _ = if (!userExists) throw NotFoundException(s"The user $userIri does not exist.")

            // check if project exists
            projectExists <- projectExists(projectIri)
            _ = if (!projectExists) throw NotFoundException(s"The project $projectIri does not exist.")

            // get users current project membership list
            currentProjectMemberships <- userProjectMembershipsGetADM(
                userIri = userIri,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser
            )
            currentProjectMembershipIris = currentProjectMemberships.map(_.id)

            // check if user is not already a member and if he is then remove the project from to list
            updatedProjectMembershipIris = if (currentProjectMembershipIris.contains(projectIri)) {
                currentProjectMembershipIris diff Seq(projectIri)
            } else {
                throw BadRequestException(s"User $userIri is not member of project $projectIri.")
            }

            // create the update request by using the SystemUser
            userUpdatePayload = UserUpdatePayloadADM(projects = Some(updatedProjectMembershipIris))

            result <- updateUserADM(
                userIri = userIri,
                userUpdatePayload = userUpdatePayload,
                featureFactoryConfig = featureFactoryConfig,
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
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @param apiRequestID         the unique api request ID.
     * @return a [[UserProjectMembershipsGetResponseV1]].
     */
    private def userProjectAdminMembershipsGetADM(userIri: IRI,
                                                  featureFactoryConfig: FeatureFactoryConfig,
                                                  requestingUser: UserADM,
                                                  apiRequestID: UUID): Future[Seq[ProjectADM]] = {

        // ToDo: only allow system user
        // ToDo: this is a bit of a hack since the ProjectAdmin group doesn't really exist.

        for {
            sparqlQueryString <- Future(org.knora.webapi.messages.twirl.queries.sparql.v1.txt.getUserByIri(
                triplestore = settings.triplestoreType,
                userIri = userIri
            ).toString())

            //_ = log.debug("userDataByIRIGetV1 - sparqlQueryString: {}", sparqlQueryString)

            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResult]

            groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            /* the projects the user is member of */
            projectIris: Seq[IRI] = groupedUserData.get(OntologyConstants.KnoraAdmin.IsInProjectAdminGroup) match {
                case Some(projects) => projects
                case None => Seq.empty[IRI]
            }

            maybeProjectFutures: Seq[Future[Option[ProjectADM]]] = projectIris.map {
                projectIri =>
                    (responderManager ? ProjectGetADM(
                        identifier = ProjectIdentifierADM(maybeIri = Some(projectIri)),
                        featureFactoryConfig = featureFactoryConfig,
                        requestingUser = KnoraSystemInstances.Users.SystemUser
                    )).mapTo[Option[ProjectADM]]
            }
            maybeProjects: Seq[Option[ProjectADM]] <- Future.sequence(maybeProjectFutures)
            projects: Seq[ProjectADM] = maybeProjects.flatten

            // _ = log.debug("userProjectAdminMembershipsGetRequestV1 - userIri: {}, projectIris: {}", userIri, projectIris)
        } yield projects
    }

    /**
     * Returns the user's project admin group memberships, where the result contains the IRIs of the projects the user
     * is a member of the project admin group.
     *
     * @param userIri              the user's IRI.
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @param apiRequestID         the unique api request ID.
     * @return a [[UserProjectMembershipsGetResponseV1]].
     */
    private def userProjectAdminMembershipsGetRequestADM(userIri: IRI,
                                                         featureFactoryConfig: FeatureFactoryConfig,
                                                         requestingUser: UserADM,
                                                         apiRequestID: UUID): Future[UserProjectAdminMembershipsGetResponseADM] = {

        // ToDo: which user is allowed to do this operation?
        // ToDo: check permissions

        for {
            userExists <- userExists(userIri)
            _ = if (!userExists) {
                throw BadRequestException(s"User $userIri does not exist.")
            }

            projects: Seq[ProjectADM] <- userProjectAdminMembershipsGetADM(
                userIri = userIri,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                apiRequestID = apiRequestID
            )
        } yield UserProjectAdminMembershipsGetResponseADM(projects = projects)
    }

    /**
     * Adds a user to the project admin group of a project.
     *
     * @param userIri              the user's IRI.
     * @param projectIri           the project's IRI.
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @param apiRequestID         the unique api request ID.
     * @return
     */
    private def userProjectAdminMembershipAddRequestADM(userIri: IRI,
                                                        projectIri: IRI,
                                                        featureFactoryConfig: FeatureFactoryConfig,
                                                        requestingUser: UserADM,
                                                        apiRequestID: UUID): Future[UserOperationResponseADM] = {

        // log.debug(s"userProjectAdminMembershipAddRequestV1: userIri: {}, projectIri: {}", userIri, projectIri)

        /**
         * The actual task run with an IRI lock.
         */
        def userProjectAdminMembershipAddRequestTask(userIri: IRI, projectIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty."))
            _ = if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
                // not a project or system admin
                // log.debug("project admin: {}, system admin: {}", userProfileV1.permissionData.isProjectAdmin(projectIri), userProfileV1.permissionData.isSystemAdmin)
                throw ForbiddenException("User's project admin membership can only be changed by a project or system administrator")
            }

            // check if user exists
            userExists <- userExists(userIri)
            _ = if (!userExists) throw NotFoundException(s"The user $userIri does not exist.")

            // check if project exists
            projectExists <- projectExists(projectIri)
            _ = if (!projectExists) throw NotFoundException(s"The project $projectIri does not exist.")

            // get users current project membership list
            currentProjectAdminMemberships <- userProjectAdminMembershipsGetADM(
                userIri = userIri,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                apiRequestID = apiRequestID
            )

            currentProjectAdminMembershipIris: Seq[IRI] = currentProjectAdminMemberships.map(_.id)

            // check if user is already member and if not then append to list
            updatedProjectAdminMembershipIris = if (!currentProjectAdminMembershipIris.contains(projectIri)) {
                currentProjectAdminMembershipIris :+ projectIri
            } else {
                throw BadRequestException(s"User $userIri is already a project admin for project $projectIri.")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadADM(projectsAdmin = Some(updatedProjectAdminMembershipIris))

            result <- updateUserADM(
                userIri = userIri,
                userUpdatePayload = userUpdatePayload,
                featureFactoryConfig = featureFactoryConfig,
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
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @param apiRequestID         the unique api request ID.
     * @return
     */
    private def userProjectAdminMembershipRemoveRequestADM(userIri: IRI,
                                                           projectIri: IRI,
                                                           featureFactoryConfig: FeatureFactoryConfig,
                                                           requestingUser: UserADM,
                                                           apiRequestID: UUID): Future[UserOperationResponseADM] = {

        // log.debug(s"userProjectAdminMembershipRemoveRequestV1: userIri: {}, projectIri: {}", userIri, projectIri)

        /**
         * The actual task run with an IRI lock.
         */
        def userProjectAdminMembershipRemoveRequestTask(userIri: IRI, projectIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty."))
            _ = if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
                // not a project or system admin
                // log.debug("project admin: {}, system admin: {}", userProfileV1.permissionData.isProjectAdmin(projectIri), userProfileV1.permissionData.isSystemAdmin)
                throw ForbiddenException("User's project admin membership can only be changed by a project or system administrator")
            }

            // check if user exists
            userExists <- userExists(userIri)
            _ = if (!userExists) throw NotFoundException(s"The user $userIri does not exist.")

            // check if project exists
            projectExists <- projectExists(projectIri)
            _ = if (!projectExists) throw NotFoundException(s"The project $projectIri does not exist.")

            // get users current project membership list
            currentProjectAdminMemberships <- userProjectAdminMembershipsGetADM(
                userIri = userIri,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                apiRequestID = apiRequestID
            )

            currentProjectAdminMembershipIris: Seq[IRI] = currentProjectAdminMemberships.map(_.id)

            // check if user is not already a member and if he is then remove the project from to list
            updatedProjectAdminMembershipIris = if (currentProjectAdminMembershipIris.contains(projectIri)) {
                currentProjectAdminMembershipIris diff Seq(projectIri)
            } else {
                throw BadRequestException(s"User $userIri is not a project admin of project $projectIri.")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadADM(projectsAdmin = Some(updatedProjectAdminMembershipIris))

            result <- updateUserADM(
                userIri = userIri,
                userUpdatePayload = userUpdatePayload,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                apiRequestID = apiRequestID
            )
        } yield result

        for {
            // run the task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
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
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @return a sequence of [[GroupADM]].
     */
    private def userGroupMembershipsGetADM(userIri: IRI,
                                           featureFactoryConfig: FeatureFactoryConfig,
                                           requestingUser: UserADM): Future[Seq[GroupADM]] = {

        for {
            maybeUserADM: Option[UserADM] <- getSingleUserADM(
                identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                userInformationType = UserInformationTypeADM.FULL,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser
            )

            groups: Seq[GroupADM] = maybeUserADM match {
                case Some(user) =>
                    log.debug("userGroupMembershipsGetADM - user found. Returning his groups: {}.", user.groups)
                    user.groups
                case None =>
                    log.debug("userGroupMembershipsGetADM - user not found. Returning empty seq.")
                    Seq.empty[GroupADM]
            }

        } yield groups
    }

    /**
     * Returns the user's group memberships as a [[UserGroupMembershipsGetResponseADM]]
     *
     * @param userIri              the IRI of the user.
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @return a [[UserGroupMembershipsGetResponseADM]].
     */
    private def userGroupMembershipsGetRequestADM(userIri: IRI,
                                                  featureFactoryConfig: FeatureFactoryConfig,
                                                  requestingUser: UserADM): Future[UserGroupMembershipsGetResponseADM] = {

        for {
            groups: Seq[GroupADM] <- userGroupMembershipsGetADM(
                userIri = userIri,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = requestingUser,
            )
        } yield UserGroupMembershipsGetResponseADM(groups = groups)

    }

    /**
     * Adds a user to a group.
     *
     * @param userIri              the user's IRI.
     * @param groupIri             the group IRI.
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @param apiRequestID         the unique api request ID.
     * @return a [[UserOperationResponseADM]].
     */
    private def userGroupMembershipAddRequestADM(userIri: IRI,
                                                 groupIri: IRI,
                                                 featureFactoryConfig: FeatureFactoryConfig,
                                                 requestingUser: UserADM,
                                                 apiRequestID: UUID): Future[UserOperationResponseADM] = {

        log.debug(s"userGroupMembershipAddRequestADM - userIri: {}, groupIri: {}", userIri, groupIri)

        /**
         * The actual task run with an IRI lock.
         */
        def userGroupMembershipAddRequestTask(userIri: IRI, groupIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty."))
            _ = if (groupIri.isEmpty) throw BadRequestException("Group IRI cannot be empty")

            // check if user exists
            maybeUser <- getSingleUserADM(
                UserIdentifierADM(maybeIri = Some(userIri)),
                UserInformationTypeADM.FULL,
                featureFactoryConfig = featureFactoryConfig,
                KnoraSystemInstances.Users.SystemUser,
                skipCache = true
            )

            userToChange: UserADM = maybeUser match {
                case Some(user) => user
                case None => throw NotFoundException(s"The user $userIri does not exist.")
            }

            // check if group exists
            groupExists <- groupExists(groupIri)
            _ = if (!groupExists) throw NotFoundException(s"The group $groupIri does not exist.")

            // get group's info. we need the project IRI.
            maybeGroupADM <- (responderManager ? GroupGetADM(
                groupIri = groupIri,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser
            )).mapTo[Option[GroupADM]]
            projectIri = maybeGroupADM.getOrElse(throw InconsistentRepositoryDataException(s"Group $groupIri does not exist")).project.id

            // check if the requesting user is allowed to perform updates
            _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
                // not a project or system admin
                // log.debug("project admin: {}, system admin: {}", userProfileV1.permissionData.isProjectAdmin(projectIri), userProfileV1.permissionData.isSystemAdmin)
                throw ForbiddenException("User's group membership can only be changed by a project or system administrator")
            }

            // get users current group membership list
            currentGroupMemberships = userToChange.groups

            currentGroupMembershipIris: Seq[IRI] = currentGroupMemberships.map(_.id)

            _ = log.debug("userGroupMembershipAddRequestADM - currentGroupMembershipIris: {}", currentGroupMembershipIris)

            // check if user is already member and if not then append to list
            updatedGroupMembershipIris = if (!currentGroupMembershipIris.contains(groupIri)) {
                currentGroupMembershipIris :+ groupIri
            } else {
                throw BadRequestException(s"User $userIri is already member of group $groupIri.")
            }

            _ = log.debug("userGroupMembershipAddRequestADM - updatedGroupMembershipIris: {}", updatedGroupMembershipIris)

            // create the update request
            userUpdatePayload = UserUpdatePayloadADM(groups = Some(updatedGroupMembershipIris))

            result <- updateUserADM(
                userIri = userIri,
                userUpdatePayload = userUpdatePayload,
                featureFactoryConfig = featureFactoryConfig,
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

    private def userGroupMembershipRemoveRequestADM(userIri: IRI,
                                                    groupIri: IRI,
                                                    featureFactoryConfig: FeatureFactoryConfig,
                                                    requestingUser: UserADM,
                                                    apiRequestID: UUID): Future[UserOperationResponseADM] = {

        log.debug(s"userGroupMembershipRemoveRequestADM - userIri: {}, groupIri: {}", userIri, groupIri)

        /**
         * The actual task run with an IRI lock.
         */
        def userGroupMembershipRemoveRequestTask(userIri: IRI, groupIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty."))
            _ = if (groupIri.isEmpty) throw BadRequestException("Group IRI cannot be empty")

            // check if user exists
            userExists <- userExists(userIri)
            _ = if (!userExists) throw NotFoundException(s"The user $userIri does not exist.")

            // check if group exists
            projectExists <- groupExists(groupIri)
            _ = if (!projectExists) throw NotFoundException(s"The group $groupIri does not exist.")

            // get group's info. we need the project IRI.
            maybeGroupADM <- (responderManager ? GroupGetADM(
                groupIri = groupIri,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser
            )).mapTo[Option[GroupADM]]

            projectIri = maybeGroupADM.getOrElse(throw exceptions.InconsistentRepositoryDataException(s"Group $groupIri does not exist")).project.id

            // check if the requesting user is allowed to perform updates
            _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin && !requestingUser.isSystemUser) {
                // not a project or system admin
                //log.debug("project admin: {}, system admin: {}", userProfileV1.permissionData.isProjectAdmin(projectIri), userProfileV1.permissionData.isSystemAdmin)
                throw ForbiddenException("User's group membership can only be changed by a project or system administrator")
            }

            // get users current project membership list
            currentGroupMemberships <- userGroupMembershipsGetRequestADM(
                userIri = userIri,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser
            )

            currentGroupMembershipIris: Seq[IRI] = currentGroupMemberships.groups.map(_.id)

            _ = log.debug("userGroupMembershipRemoveRequestADM - currentGroupMembershipIris: {}", currentGroupMembershipIris)

            // check if user is not already a member and if he is then remove the project from to list
            updatedGroupMembershipIris = if (currentGroupMembershipIris.contains(groupIri)) {
                currentGroupMembershipIris diff Seq(groupIri)
            } else {
                throw BadRequestException(s"User $userIri is not member of group $groupIri.")
            }

            _ = log.debug("userGroupMembershipRemoveRequestADM - updatedGroupMembershipIris: {}", updatedGroupMembershipIris)

            // create the update request
            userUpdatePayload = UserUpdatePayloadADM(groups = Some(updatedGroupMembershipIris))

            result <- updateUserADM(
                userIri = userIri,
                userUpdatePayload = userUpdatePayload,
                featureFactoryConfig = featureFactoryConfig,
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
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       the requesting user.
     * @param apiRequestID         the unique api request ID.
     * @return a future containing a [[UserOperationResponseADM]].
     * @throws BadRequestException         if necessary parameters are not supplied.
     * @throws UpdateNotPerformedException if the update was not performed.
     */
    private def updateUserADM(userIri: IRI,
                              userUpdatePayload: UserUpdatePayloadADM,
                              featureFactoryConfig: FeatureFactoryConfig,
                              requestingUser: UserADM,
                              apiRequestID: UUID): Future[UserOperationResponseADM] = {

        log.debug("updateUserADM - userUpdatePayload: {}", userUpdatePayload)

        /* Remember: some checks on UserUpdatePayloadV1 are implemented in the case class */

        if (userIri.contains(KnoraSystemInstances.Users.SystemUser.id) || userIri.contains(KnoraSystemInstances.Users.AnonymousUser.id)) {
            throw BadRequestException("Changes to built-in users are not allowed.")
        }

        for {
            maybeCurrentUser <- getSingleUserADM(
                identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = requestingUser,
                userInformationType = UserInformationTypeADM.FULL,
                skipCache = true
            )

            _ = if (maybeCurrentUser.isEmpty) {
                throw NotFoundException(s"User '$userIri' not found. Aborting update request.")
            }
            // we are changing the user, so lets get rid of the cached copy
            _ = invalidateCachedUserADM(maybeCurrentUser)

            /* Update the user */
            updateUserSparqlString <- Future(org.knora.webapi.messages.twirl.queries.sparql.admin.txt.updateUser(
                adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
                triplestore = settings.triplestoreType,
                userIri = userIri,
                maybeUsername = userUpdatePayload.username,
                maybeEmail = userUpdatePayload.email,
                maybeGivenName = userUpdatePayload.givenName,
                maybeFamilyName = userUpdatePayload.familyName,
                maybePassword = userUpdatePayload.password,
                maybeStatus = userUpdatePayload.status,
                maybeLang = userUpdatePayload.lang,
                maybeProjects = userUpdatePayload.projects,
                maybeProjectsAdmin = userUpdatePayload.projectsAdmin,
                maybeGroups = userUpdatePayload.groups,
                maybeSystemAdmin = userUpdatePayload.systemAdmin
            ).toString)
            // _ = log.debug(s"updateUserV1 - query: $updateUserSparqlString")
            updateResult <- (storeManager ? SparqlUpdateRequest(updateUserSparqlString)).mapTo[SparqlUpdateResponse]

            /* Verify that the user was updated. */
            maybeUpdatedUserADM <- getSingleUserADM(
                identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = requestingUser,
                userInformationType = UserInformationTypeADM.FULL,
                skipCache = true
            )

            updatedUserADM: UserADM = maybeUpdatedUserADM.getOrElse(throw UpdateNotPerformedException("User was not updated. Please report this as a possible bug."))

            // _ = log.debug(s"===>>> apiUpdateRequest: $userUpdatePayload /  updatedUserADM: $updatedUserADM")

            _ = if (userUpdatePayload.username.isDefined) {
                if (updatedUserADM.username != userUpdatePayload.username.get) throw UpdateNotPerformedException("User's 'username' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.email.isDefined) {
                if (updatedUserADM.email != userUpdatePayload.email.get) throw UpdateNotPerformedException("User's 'email' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.givenName.isDefined) {
                if (updatedUserADM.givenName != userUpdatePayload.givenName.get) throw UpdateNotPerformedException("User's 'givenName' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.familyName.isDefined) {
                if (updatedUserADM.familyName != userUpdatePayload.familyName.get) throw UpdateNotPerformedException("User's 'familyName' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.password.isDefined) {
                if (updatedUserADM.password != userUpdatePayload.password) throw UpdateNotPerformedException("User's 'password' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.status.isDefined) {
                if (updatedUserADM.status != userUpdatePayload.status.get) throw UpdateNotPerformedException("User's 'status' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.lang.isDefined) {
                if (updatedUserADM.lang != userUpdatePayload.lang.get) throw UpdateNotPerformedException("User's 'lang' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.systemAdmin.isDefined) {
                if (updatedUserADM.permissions.isSystemAdmin != userUpdatePayload.systemAdmin.get) throw UpdateNotPerformedException("User's 'isInSystemAdminGroup' status was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.groups.isDefined) {
                if (updatedUserADM.groups.map(_.id) != userUpdatePayload.groups.get) throw UpdateNotPerformedException("User's 'group' memberships where not updated. Please report this as a possible bug.")
            }

        } yield UserOperationResponseADM(updatedUserADM.ofType(UserInformationTypeADM.RESTRICTED))
    }

    /**
     * Creates a new user. Self-registration is allowed, so even the default user, i.e. with no credentials supplied,
     * is allowed to create a new user.
     *
     * Referenced Websites:
     *                     - https://crackstation.net/hashing-security.htm
     *                     - http://blog.ircmaxell.com/2012/12/seven-ways-to-screw-up-bcrypt.html
     *
     * @param createRequest        a [[CreateUserApiRequestADM]] object containing information about the new user to be created.
     * @param featureFactoryConfig the feature factory configuration.
     * @param requestingUser       a [[UserADM]] object containing information about the requesting user.
     * @return a future containing the [[UserOperationResponseADM]].
     */
    private def createNewUserADM(createRequest: CreateUserApiRequestADM,
                                 featureFactoryConfig: FeatureFactoryConfig,
                                 requestingUser: UserADM,
                                 apiRequestID: UUID): Future[UserOperationResponseADM] = {

        log.debug("createNewUserADM - createRequest: {}", createRequest)

        /**
         * The actual task run with an IRI lock.
         */
        def createNewUserTask(createRequest: CreateUserApiRequestADM, requestingUser: UserADM, apiRequestID: UUID) = for {
            // check username
            _ <- Future(if (createRequest.username.isEmpty) throw BadRequestException("Username cannot be empty"))
            _ = stringFormatter.validateUsername(createRequest.username, throw BadRequestException(s"The username '${createRequest.username}' contains invalid characters"))

            // check email
            _ = if (createRequest.email.isEmpty) throw BadRequestException("Email cannot be empty")
            _ = stringFormatter.validateEmailAndThrow(createRequest.email, throw BadRequestException(s"The email '${createRequest.email}' is invalid"))

            // check other
            _ = if (createRequest.password.isEmpty) throw BadRequestException("Password cannot be empty")
            _ = if (createRequest.givenName.isEmpty) throw BadRequestException("Given name cannot be empty")
            _ = if (createRequest.familyName.isEmpty) throw BadRequestException("Family name cannot be empty")

            usernameTaken: Boolean <- userByUsernameExists(Some(createRequest.username))
            _ = if (usernameTaken) {
                throw DuplicateValueException(s"User with the username '${createRequest.username}' already exists")
            }

            emailTaken: Boolean <- userByEmailExists(Some(createRequest.email))
            _ = if (emailTaken) {
                throw DuplicateValueException(s"User with the email '${createRequest.email}' already exists")
            }

            // check the custom IRI; if not given, create an unused IRI
            customUserIri: Option[SmartIri] = createRequest.id.map(iri => iri.toSmartIri)
            userIri: IRI <- checkOrCreateEntityIri(customUserIri, stringFormatter.makeRandomPersonIri)

            encoder = new BCryptPasswordEncoder(settings.bcryptPasswordStrength)
            hashedPassword = encoder.encode(createRequest.password)

            // Create the new user.
            createNewUserSparqlString = org.knora.webapi.messages.twirl.queries.sparql.admin.txt.createNewUser(
                adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
                triplestore = settings.triplestoreType,
                userIri = userIri,
                userClassIri = OntologyConstants.KnoraAdmin.User,
                username = stringFormatter.validateAndEscapeUsername(createRequest.username, throw BadRequestException(s"The username '${createRequest.username}' contains invalid characters")),
                email = createRequest.email,
                password = hashedPassword,
                givenName = createRequest.givenName,
                familyName = createRequest.familyName,
                status = createRequest.status,
                preferredLanguage = createRequest.lang,
                systemAdmin = createRequest.systemAdmin
            ).toString
            // _ = log.debug(s"createNewUser: $createNewUserSparqlString")
            createNewUserResponse <- (storeManager ? SparqlUpdateRequest(createNewUserSparqlString)).mapTo[SparqlUpdateResponse]

            // try to retrieve newly created user (will also add to cache)
            maybeNewUserADM: Option[UserADM] <- getSingleUserADM(
                identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                userInformationType = UserInformationTypeADM.FULL,
                skipCache = true
            )

            // check to see if we could retrieve the new user
            newUserADM = maybeNewUserADM.getOrElse(
                throw UpdateNotPerformedException(s"User $userIri was not created. Please report this as a possible bug.")
            )

            // create the user operation response
            _ = log.debug("createNewUserADM - created new user: {}", newUserADM)
            userOperationResponseADM = UserOperationResponseADM(newUserADM.ofType(UserInformationTypeADM.RESTRICTED))

        } yield userOperationResponseADM

        for {
            // run user creation with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                USERS_GLOBAL_LOCK_IRI,
                () => createNewUserTask(createRequest, requestingUser, apiRequestID)
            )
        } yield taskResult
    }

    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
     * Tries to retrieve a [[UserADM]] either from triplestore or cache if caching is enabled.
     * If user is not found in cache but in triplestore, then user is written to cache.
     */
    private def getUserFromCacheOrTriplestore(identifier: UserIdentifierADM, featureFactoryConfig: FeatureFactoryConfig): Future[Option[UserADM]] = {
        if (settings.cacheServiceEnabled) {
            // caching enabled
            getUserFromCache(identifier)
                .flatMap {
                    case None =>
                        // none found in cache. getting from triplestore.
                        getUserFromTriplestore(identifier = identifier, featureFactoryConfig = featureFactoryConfig)
                            .flatMap {
                                case None =>
                                    // also none found in triplestore. finally returning none.
                                    log.debug("getUserFromCacheOrTriplestore - not found in cache and in triplestore")
                                    FastFuture.successful(None)
                                case Some(user) =>
                                    // found a user in the triplestore. need to write to cache.
                                    log.debug("getUserFromCacheOrTriplestore - not found in cache but found in triplestore. need to write to cache.")
                                    // writing user to cache and afterwards returning the user found in the triplestore
                                    writeUserADMToCache(user).map(res => Some(user))
                            }
                    case Some(user) =>
                        log.debug("getUserFromCacheOrTriplestore - found in cache. returning user.")
                        FastFuture.successful(Some(user))
                }
        } else {
            // caching disabled
            log.debug("getUserFromCacheOrTriplestore - caching disabled. getting from triplestore.")
            getUserFromTriplestore(identifier = identifier, featureFactoryConfig = featureFactoryConfig)
        }
    }

    /**
     * Tries to retrieve a [[UserADM]] from the triplestore.
     */
    private def getUserFromTriplestore(identifier: UserIdentifierADM,
                                       featureFactoryConfig: FeatureFactoryConfig): Future[Option[UserADM]] = for {
        sparqlQueryString <- Future(org.knora.webapi.messages.twirl.queries.sparql.admin.txt.getUsers(
            triplestore = settings.triplestoreType,
            maybeIri = identifier.toIriOption,
            maybeUsername = identifier.toUsernameOption,
            maybeEmail = identifier.toEmailOption
        ).toString())

        userQueryResponse <- (storeManager ? SparqlExtendedConstructRequest(
            sparql = sparqlQueryString,
            featureFactoryConfig = featureFactoryConfig
        )).mapTo[SparqlExtendedConstructResponse]

        maybeUserADM: Option[UserADM] <- if (userQueryResponse.statements.nonEmpty) {
            log.debug("getUserFromTriplestore - triplestore hit for: {}", identifier)
            statements2UserADM(
                statements = userQueryResponse.statements.head,
                featureFactoryConfig = featureFactoryConfig
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
     * @param featureFactoryConfig the feature factory configuration.
     * @return a [[UserADM]] containing the user's data.
     */
    private def statements2UserADM(statements: (SubjectV2, Map[SmartIri, Seq[LiteralV2]]),
                                   featureFactoryConfig: FeatureFactoryConfig): Future[Option[UserADM]] = {

        // log.debug("statements2UserADM - statements: {}", statements)

        val userIri: IRI = statements._1.toString
        val propsMap: Map[SmartIri, Seq[LiteralV2]] = statements._2

        // log.debug("statements2UserADM - userIri: {}", userIri)

        if (propsMap.nonEmpty) {

            /* the groups the user is member of (only explicit groups) */
            val groupIris: Seq[IRI] = propsMap.get(OntologyConstants.KnoraAdmin.IsInGroup.toSmartIri) match {
                case Some(groups) => groups.map(_.asInstanceOf[IriLiteralV2].value)
                case None => Seq.empty[IRI]
            }

            // log.debug(s"statements2UserADM - groupIris: {}", MessageUtil.toSource(groupIris))

            /* the projects the user is member of (only explicit projects) */
            val projectIris: Seq[IRI] = propsMap.get(OntologyConstants.KnoraAdmin.IsInProject.toSmartIri) match {
                case Some(projects) => projects.map(_.asInstanceOf[IriLiteralV2].value)
                case None => Seq.empty[IRI]
            }

            // log.debug(s"statements2UserADM - projectIris: {}", MessageUtil.toSource(projectIris))

            /* the projects for which the user is implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group */
            val isInProjectAdminGroups: Seq[IRI] = propsMap.getOrElse(OntologyConstants.KnoraAdmin.IsInProjectAdminGroup.toSmartIri, Vector.empty[IRI]).map(_.asInstanceOf[IriLiteralV2].value)

            /* is the user implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#SystemAdmin' group */
            val isInSystemAdminGroup = propsMap.get(OntologyConstants.KnoraAdmin.IsInSystemAdminGroup.toSmartIri).exists(p => p.head.asInstanceOf[BooleanLiteralV2].value)

            for {
                /* get the user's permission profile from the permissions responder */
                permissionData <- (responderManager ? PermissionDataGetADM(projectIris = projectIris,
                    groupIris = groupIris,
                    isInProjectAdminGroups = isInProjectAdminGroups,
                    isInSystemAdminGroup = isInSystemAdminGroup,
                    featureFactoryConfig = featureFactoryConfig,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )).mapTo[PermissionsDataADM]

                maybeGroupFutures: Seq[Future[Option[GroupADM]]] = groupIris.map {
                    groupIri =>
                        (responderManager ? GroupGetADM(
                            groupIri = groupIri,
                            featureFactoryConfig = featureFactoryConfig,
                            requestingUser = KnoraSystemInstances.Users.SystemUser
                        )).mapTo[Option[GroupADM]]
                }
                maybeGroups: Seq[Option[GroupADM]] <- Future.sequence(maybeGroupFutures)
                groups: Seq[GroupADM] = maybeGroups.flatten

                // _ = log.debug("statements2UserADM - groups: {}", MessageUtil.toSource(groups))

                maybeProjectFutures: Seq[Future[Option[ProjectADM]]] = projectIris.map {
                    projectIri =>
                        (responderManager ? ProjectGetADM(
                            ProjectIdentifierADM(maybeIri = Some(projectIri)),
                            featureFactoryConfig = featureFactoryConfig,
                            requestingUser = KnoraSystemInstances.Users.SystemUser
                        )).mapTo[Option[ProjectADM]]
                }
                maybeProjects: Seq[Option[ProjectADM]] <- Future.sequence(maybeProjectFutures)
                projects: Seq[ProjectADM] = maybeProjects.flatten

                // _ = log.debug("statements2UserADM - projects: {}", MessageUtil.toSource(projects))

                /* construct the user profile from the different parts */
                user = UserADM(
                    id = userIri,
                    username = propsMap.getOrElse(OntologyConstants.KnoraAdmin.Username.toSmartIri, throw InconsistentRepositoryDataException(s"User: $userIri has no 'username' defined.")).head.asInstanceOf[StringLiteralV2].value,
                    email = propsMap.getOrElse(OntologyConstants.KnoraAdmin.Email.toSmartIri, throw InconsistentRepositoryDataException(s"User: $userIri has no 'email' defined.")).head.asInstanceOf[StringLiteralV2].value,
                    password = propsMap.get(OntologyConstants.KnoraAdmin.Password.toSmartIri).map(_.head.asInstanceOf[StringLiteralV2].value),
                    token = None,
                    givenName = propsMap.getOrElse(OntologyConstants.KnoraAdmin.GivenName.toSmartIri, throw InconsistentRepositoryDataException(s"User: $userIri has no 'givenName' defined.")).head.asInstanceOf[StringLiteralV2].value,
                    familyName = propsMap.getOrElse(OntologyConstants.KnoraAdmin.FamilyName.toSmartIri, throw InconsistentRepositoryDataException(s"User: $userIri has no 'familyName' defined.")).head.asInstanceOf[StringLiteralV2].value,
                    status = propsMap.getOrElse(OntologyConstants.KnoraAdmin.Status.toSmartIri, throw InconsistentRepositoryDataException(s"User: $userIri has no 'status' defined.")).head.asInstanceOf[BooleanLiteralV2].value,
                    lang = propsMap.getOrElse(OntologyConstants.KnoraAdmin.PreferredLanguage.toSmartIri, throw InconsistentRepositoryDataException(s"User: $userIri has no 'preferredLanguage' defined.")).head.asInstanceOf[StringLiteralV2].value, groups = groups, projects = projects,
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
    private def userExists(userIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(org.knora.webapi.messages.twirl.queries.sparql.admin.txt.checkUserExists(userIri = userIri).toString)
            // _ = log.debug("userExists - query: {}", askString)

            checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkUserExistsResponse.result

        } yield result
    }

    /**
     * Helper method for checking if an username is already registered.
     *
     * @param maybeUsername the username of the user.
     * @param maybeCurrent  the current username of the user.
     * @return a [[Boolean]].
     */
    private def userByUsernameExists(maybeUsername: Option[String], maybeCurrent: Option[String] = None): Future[Boolean] = {
        maybeUsername match {
            case Some(username) =>
                if (maybeCurrent.contains(username)) {
                    FastFuture.successful(true)
                } else {
                    stringFormatter.validateUsername(username, throw BadRequestException(s"The username '$username' contains invalid characters"))

                    for {
                        askString <- Future(org.knora.webapi.messages.twirl.queries.sparql.admin.txt.checkUserExistsByUsername(username = username).toString)
                        // _ = log.debug("userExists - query: {}", askString)

                        checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
                    } yield checkUserExistsResponse.result
                }

            case None => FastFuture.successful(false)
        }
    }

    /**
     * Helper method for checking if an email is already registered.
     *
     * @param maybeEmail   the email of the user.
     * @param maybeCurrent the current email of the user.
     * @return a [[Boolean]].
     */
    private def userByEmailExists(maybeEmail: Option[String], maybeCurrent: Option[String] = None): Future[Boolean] = {
        maybeEmail match {
            case Some(email) =>
                if (maybeCurrent.contains(email)) {
                    FastFuture.successful(true)
                } else {
                    stringFormatter.validateEmailAndThrow(email, throw BadRequestException(s"The email address '$email' is invalid"))

                    for {
                        askString <- Future(org.knora.webapi.messages.twirl.queries.sparql.admin.txt.checkUserExistsByEmail(email = email).toString)
                        // _ = log.debug("userExists - query: {}", askString)

                        checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
                    } yield checkUserExistsResponse.result
                }

            case None => FastFuture.successful(false)
        }
    }

    /**
     * Helper method for checking if a project exists.
     *
     * @param projectIri the IRI of the project.
     * @return a [[Boolean]].
     */
    private def projectExists(projectIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(org.knora.webapi.messages.twirl.queries.sparql.admin.txt.checkProjectExistsByIri(projectIri = projectIri).toString)
            // _ = log.debug("projectExists - query: {}", askString)

            checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkUserExistsResponse.result

        } yield result
    }

    /**
     * Helper method for checking if a group exists.
     *
     * @param groupIri the IRI of the group.
     * @return a [[Boolean]].
     */
    private def groupExists(groupIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(org.knora.webapi.messages.twirl.queries.sparql.admin.txt.checkGroupExistsByIri(groupIri = groupIri).toString)
            // _ = log.debug("groupExists - query: {}", askString)

            checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkUserExistsResponse.result

        } yield result
    }

    /**
     * Tries to retrieve a [[UserADM]] from the cache.
     */
    private def getUserFromCache(identifier: UserIdentifierADM): Future[Option[UserADM]] = {
        val result = (storeManager ? CacheServiceGetUserADM(identifier)).mapTo[Option[UserADM]]
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
     * @return true if writing was successful.
     * @throws ApplicationCacheException when there is a problem with writing the user's profile to cache.
     */
    private def writeUserADMToCache(user: UserADM): Future[Boolean] = {
        val result = (storeManager ? CacheServicePutUserADM(user)).mapTo[Boolean]
        result.map { res =>
            log.debug("writeUserADMToCache - result: {}", result)
            res
        }
    }

    /**
     * Removes the user from cache.
     */
    private def invalidateCachedUserADM(maybeUser: Option[UserADM]): Future[Boolean] = {
        if (settings.cacheServiceEnabled) {
            val keys: Set[String] = Seq(maybeUser.map(_.id), maybeUser.map(_.email), maybeUser.map(_.username)).flatten.toSet
            // only send to Redis if keys are not empty
            if (keys.nonEmpty) {
                val result = (storeManager ? CacheServiceRemoveValues(keys)).mapTo[Boolean]
                result.map { res =>
                    log.debug("invalidateCachedUserADM - result: {}", res)
                    res
                }
            } else {
                // since there was nothing to remove, we can immediately return
                FastFuture.successful(true)
            }
        } else {
            // caching is turned off, so nothing to do.
            FastFuture.successful(true)
        }

    }

}
