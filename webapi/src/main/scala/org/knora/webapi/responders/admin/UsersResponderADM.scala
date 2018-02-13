/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

import akka.actor.Status
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.groupsmessages.{GroupADM, GroupGetADM}
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionDataGetADM, PermissionsDataADM}
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectGetADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM.UserInformationTypeADM
import org.knora.webapi.messages.admin.responder.usersmessages.{UserUpdatePayloadADM, _}
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{CacheUtil, KnoraIdUtil}
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder

import scala.concurrent.Future

/**
  * Provides information about Knora users to other responders.
  */
class UsersResponderADM extends Responder {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    // The IRI used to lock user creation and update
    val USERS_GLOBAL_LOCK_IRI = "http://rdfh.ch/users"

    val USER_ADM_CACHE_NAME = "userADMCache"

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1]], and returns a message of type [[UserADM]]
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case UsersGetADM(userInformationTypeADM, requestingUser) => future2Message(sender(), usersGetADM(userInformationTypeADM, requestingUser), log)
        case UsersGetRequestADM(userInformationTypeADM, requestingUser) => future2Message(sender(), usersGetRequestADM(userInformationTypeADM, requestingUser), log)
        case UserGetADM(maybeUserIri, maybeEmail, userInformationTypeADM, requestingUser) => future2Message(sender(), userGetADM(maybeUserIri, maybeEmail, userInformationTypeADM, requestingUser), log)
        case UserGetRequestADM(maybeUserIri, maybeEmail, userInformationTypeADM, requestingUser) => future2Message(sender(), userGetRequestADM(maybeUserIri, maybeEmail, userInformationTypeADM, requestingUser), log)
        case UserCreateRequestADM(createRequest, requestingUser, apiRequestID) => future2Message(sender(), createNewUserADM(createRequest, requestingUser, apiRequestID), log)
        case UserChangeBasicUserInformationRequestADM(userIri, changeUserRequest, requestingUser, apiRequestID) => future2Message(sender(), changeBasicUserInformationADM(userIri, changeUserRequest, requestingUser, apiRequestID), log)
        case UserChangePasswordRequestADM(userIri, changeUserRequest, requestingUser, apiRequestID) => future2Message(sender(), changePasswordADM(userIri, changeUserRequest, requestingUser, apiRequestID), log)
        case UserChangeStatusRequestADM(userIri, changeUserRequest, requestingUser, apiRequestID) => future2Message(sender(), changeUserStatusADM(userIri, changeUserRequest, requestingUser, apiRequestID), log)
        case UserChangeSystemAdminMembershipStatusRequestADM(userIri, changeSystemAdminMembershipStatusRequest, requestingUser, apiRequestID) => future2Message(sender(), changeUserSystemAdminMembershipStatusADM(userIri, changeSystemAdminMembershipStatusRequest, requestingUser, apiRequestID), log)
        case UserProjectMembershipsGetRequestADM(userIri, requestingUser, apiRequestID) => future2Message(sender(), userProjectMembershipsGetRequestADM(userIri, requestingUser, apiRequestID), log)
        case UserProjectMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID) => future2Message(sender(), userProjectMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID), log)
        case UserProjectMembershipRemoveRequestADM(userIri, projectIri, requestingUser, apiRequestID) => future2Message(sender(), userProjectMembershipRemoveRequestADM(userIri, projectIri, requestingUser, apiRequestID), log)
        case UserProjectAdminMembershipsGetRequestADM(userIri, requestingUser, apiRequestID) => future2Message(sender(), userProjectAdminMembershipsGetRequestADM(userIri, requestingUser, apiRequestID), log)
        case UserProjectAdminMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID) => future2Message(sender(), userProjectAdminMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID), log)
        case UserProjectAdminMembershipRemoveRequestADM(userIri, projectIri, requestingUser, apiRequestID) => future2Message(sender(), userProjectAdminMembershipRemoveRequestADM(userIri, projectIri, requestingUser, apiRequestID), log)
        case UserGroupMembershipsGetRequestADM(userIri, requestingUser, apiRequestID) => future2Message(sender(), userGroupMembershipsGetRequestADM(userIri, requestingUser, apiRequestID), log)
        case UserGroupMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID) => future2Message(sender(), userGroupMembershipAddRequestADM(userIri, projectIri, requestingUser, apiRequestID), log)
        case UserGroupMembershipRemoveRequestADM(userIri, projectIri, requestingUser, apiRequestID) => future2Message(sender(), userGroupMembershipRemoveRequestADM(userIri, projectIri, requestingUser, apiRequestID), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }


    /**
      * Gets all the users and returns them as a sequence of [[UserADM]].
      *
      * @param userInformationType the extent of the information returned.
      * @param requestingUser the user initiating the request.
      * @return all the users as a sequence of [[UserADM]].
      */
    private def usersGetADM(userInformationType: UserInformationTypeADM, requestingUser: UserADM): Future[Seq[UserADM]] = {

        //log.debug("usersGetV1")

        for {
            sparqlQueryString <- Future(queries.sparql.admin.txt.getUsers(
                triplestore = settings.triplestoreType,
                maybeIri = None,
                maybeEmail = None
            ).toString())

            usersResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQueryString)).mapTo[SparqlExtendedConstructResponse]

            statements = usersResponse.statements.toList

            // _ = log.debug("usersGetADM - statements: {}", statements)

            users: Seq[UserADM] = statements.map {
                case (userIri: SubjectV2, propsMap: Map[IRI, Seq[LiteralV2]]) =>

                    UserADM(
                        id = userIri.toString,
                        email = propsMap.getOrElse(OntologyConstants.KnoraBase.Email, throw InconsistentTriplestoreDataException(s"User: $userIri has no 'email' defined.")).head.asInstanceOf[StringLiteralV2].value,
                        givenName = propsMap.getOrElse(OntologyConstants.KnoraBase.GivenName, throw InconsistentTriplestoreDataException(s"User: $userIri has no 'givenName' defined.")).head.asInstanceOf[StringLiteralV2].value,
                        familyName = propsMap.getOrElse(OntologyConstants.KnoraBase.FamilyName, throw InconsistentTriplestoreDataException(s"User: $userIri has no 'familyName' defined.")).head.asInstanceOf[StringLiteralV2].value,
                        status = propsMap.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"User: $userIri has no 'status' defined.")).head.asInstanceOf[BooleanLiteralV2].value,
                        lang = propsMap.getOrElse(OntologyConstants.KnoraBase.PreferredLanguage, throw InconsistentTriplestoreDataException(s"User: $userIri has no 'preferedLanguage' defined.")).head.asInstanceOf[StringLiteralV2].value
                    )
            }

        } yield users.sorted
    }

    /**
      * Gets all the users and returns them as a [[UsersGetResponseV1]].
      *
      * @param userInformationType the extent of the information returned.
      * @param requestingUser the user initiating the request.
      * @return all the users as a [[UsersGetResponseV1]].
      */
    private def usersGetRequestADM(userInformationType: UserInformationTypeADM, requestingUser: UserADM): Future[UsersGetResponseADM] = {
        for {
            maybeUsersListToReturn <- usersGetADM(userInformationType, requestingUser)
            result = maybeUsersListToReturn match {
                case users: Seq[UserADM] if users.nonEmpty => UsersGetResponseADM(users = users)
                case _ => throw NotFoundException(s"No users found")
            }
        } yield result
    }

    /**
      * ~ CACHED ~
      * Gets information about a Knora user, and returns it as a [[UserADM]]. If possible, tries to retrieve it
      * from the cache. If not, it retrieves it from the triplestore and writes it to the cache. Writes to the cache
      * are always `UserInformationTypeADM.FULL`.
      *
      * @param maybeUserIri     the IRI of the user.
      * @param maybeUserEmail the email of the user.
      * @param userInformationType the type of the requested profile (restricted of full).
      * @param requestingUser the user initiating the request.
      * @return a [[UserADM]] describing the user.
      */
    private def userGetADM(maybeUserIri: Option[IRI], maybeUserEmail: Option[String], userInformationType: UserInformationTypeADM, requestingUser: UserADM): Future[Option[UserADM]] = {
        // log.debug(s"userGetADM: maybeUserIri: {}, maybeUserEmail: {}, userInformationType: {}, requestingUser: {}", maybeUserIri, maybeUserEmail, userInformationType, requestingUser )

        val userFromCache = if (maybeUserIri.nonEmpty) {
            CacheUtil.get[UserADM](USER_ADM_CACHE_NAME, maybeUserIri.get)
        } else if (maybeUserEmail.nonEmpty) {
            CacheUtil.get[UserADM](USER_ADM_CACHE_NAME, maybeUserEmail.get)
        } else {
            throw BadRequestException("Need to provide the user IRI and/or email.")
        }

        val user = userFromCache match {
            case Some(user) =>
                // found a user profile in the cache
                log.debug("userGetADM - cache hit for: {}", List(maybeUserIri, maybeUserEmail).flatten.head)
                FastFuture.successful(Some(user.ofType(userInformationType)))
            case None => {
                // didn't find a user profile in the cache
                log.debug("userGetADM - no cache hit for: {}", List(maybeUserIri, maybeUserEmail).flatten.head)
                for {
                    sparqlQueryString <- Future(queries.sparql.admin.txt.getUsers(
                        triplestore = settings.triplestoreType,
                        maybeIri = maybeUserIri,
                        maybeEmail = maybeUserEmail
                    ).toString())

                    userQueryResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQueryString)).mapTo[SparqlExtendedConstructResponse]

                    maybeUserADM: Option[UserADM] <- if (userQueryResponse.statements.nonEmpty) {
                        statements2UserADM(userQueryResponse.statements.head, requestingUser)
                    } else {
                        FastFuture.successful(None)
                    }

                    _ = if (maybeUserADM.nonEmpty) {
                        writeUserADMToCache(maybeUserADM.get)
                    }

                    result = maybeUserADM.map(_.ofType(userInformationType))

                } yield result
            }
        }

        // _ = log.debug("userGetADM - user: {}", MessageUtil.toSource(user))
        user
    }

    /**
      * Gets information about a Knora user, and returns it as a [[UserProfileResponseV1]].
      *
      * @param maybeUserIri     the IRI of the user.
      * @param maybeUserEmail the email of the user.
      * @param userInformationType the type of the requested profile (restricted of full).
      * @param requestingUser the user initiating the request.
      * @return a [[UserResponseADM]]
      */
    private def userGetRequestADM(maybeUserIri: Option[IRI], maybeUserEmail: Option[String], userInformationType: UserInformationTypeADM, requestingUser: UserADM): Future[UserResponseADM] = {
        for {
            maybeUserADM <- userGetADM(maybeUserIri, maybeUserEmail, userInformationType, requestingUser)
            result = maybeUserADM match {
                case Some(user) => UserResponseADM(user = user)
                case None => throw NotFoundException(s"User '${Seq(maybeUserIri, maybeUserEmail).flatten.head}' not found")
            }
        } yield result
    }

    /**
      * Creates a new user. Self-registration is allowed, so even the default user, i.e. with no credentials supplied,
      * is allowed to create a new user.
      *
      * Referenced Websites:
      *                     - https://crackstation.net/hashing-security.htm
      *                     - http://blog.ircmaxell.com/2012/12/seven-ways-to-screw-up-bcrypt.html
      *
      * @param createRequest a [[CreateUserApiRequestADM]] object containing information about the new user to be created.
      * @param requestingUser   a [[UserADM]] object containing information about the requesting user.
      * @return a future containing the [[UserOperationResponseADM]].
      */
    private def createNewUserADM(createRequest: CreateUserApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = {

        def createNewUserTask(createRequest: CreateUserApiRequestADM, requestingUser: UserADM, apiRequestID: UUID) = for {
            // check if required information is supplied
            _ <- Future(if (createRequest.email.isEmpty) throw BadRequestException("Email cannot be empty"))
            _ = if (createRequest.password.isEmpty) throw BadRequestException("Password cannot be empty")
            _ = if (createRequest.givenName.isEmpty) throw BadRequestException("Given name cannot be empty")
            _ = if (createRequest.familyName.isEmpty) throw BadRequestException("Family name cannot be empty")

            // check if the supplied email for the new user is unique, i.e. not already registered
            sparqlQueryString <- Future(queries.sparql.v1.txt.getUserByEmail(
                triplestore = settings.triplestoreType,
                email = createRequest.email
            ).toString())
            //_ = log.debug(s"createNewUser - check duplicate email: $sparqlQueryString")
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            //_ = log.debug(MessageUtil.toSource(userDataQueryResponse))

            _ = if (userDataQueryResponse.results.bindings.nonEmpty) {
                throw DuplicateValueException(s"User with the email: '${createRequest.email}' already exists")
            }

            userIri = knoraIdUtil.makeRandomPersonIri

            encoder = new SCryptPasswordEncoder
            hashedPassword = encoder.encode(createRequest.password)

            // Create the new user.
            createNewUserSparqlString = queries.sparql.admin.txt.createNewUser(
                adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
                triplestore = settings.triplestoreType,
                userIri = userIri,
                userClassIri = OntologyConstants.KnoraBase.User,
                email = createRequest.email,
                password = hashedPassword,
                givenName = createRequest.givenName,
                familyName = createRequest.familyName,
                status = createRequest.status,
                preferredLanguage = createRequest.lang,
                systemAdmin = createRequest.systemAdmin
            ).toString
            //_ = log.debug(s"createNewUser: $createNewUserSparqlString")
            createResourceResponse <- (storeManager ? SparqlUpdateRequest(createNewUserSparqlString)).mapTo[SparqlUpdateResponse]


            // Verify that the user was created.
            sparqlQuery = queries.sparql.admin.txt.getUsers(
                triplestore = settings.triplestoreType,
                maybeIri = Some(userIri),
                maybeEmail = None
            ).toString()
            userDataQueryResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            // create the user profile
            maybeNewUserADM <- statements2UserADM(userDataQueryResponse.statements.head, requestingUser)

            newUserADM = maybeNewUserADM.getOrElse(throw UpdateNotPerformedException(s"User $userIri was not created. Please report this as a possible bug."))

            // write the newly created user profile to cache
            _ = writeUserADMToCache(newUserADM)

            // create the user operation response
            userOperationResponseV1 = UserOperationResponseADM(newUserADM.ofType(UserInformationTypeADM.RESTRICTED))

        } yield userOperationResponseV1

        for {
            // run user creation with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                USERS_GLOBAL_LOCK_IRI,
                () => createNewUserTask(createRequest, requestingUser, apiRequestID)
            )
        } yield taskResult
    }

    /**
      * Updates an existing user. Only basic user data information (email, givenName, familyName, lang)
      * can be changed. For changing the password or user status, use the separate methods.
      *
      * @param userIri           the IRI of the existing user that we want to update.
      * @param changeUserRequest the updated information.
      * @param requestingUser    the requesting user.
      * @param apiRequestID      the unique api request ID.
      * @return a future containing a [[UserOperationResponseADM]].
      * @throws BadRequestException if the necessary parameters are not supplied.
      * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
      */
    private def changeBasicUserInformationADM(userIri: IRI, changeUserRequest: ChangeUserApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = {

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

            parametersCount = List(changeUserRequest.email, changeUserRequest.givenName, changeUserRequest.familyName, changeUserRequest.lang).flatten.size
            _ = if (parametersCount == 0) throw BadRequestException("At least one parameter needs to be supplied. No data would be changed. Aborting request for changing of basic user data.")

            userUpdatePayload = UserUpdatePayloadADM(
                email = changeUserRequest.email,
                givenName = changeUserRequest.givenName,
                familyName = changeUserRequest.familyName,
                lang = changeUserRequest.lang
            )

            // send change request as SystemUser
            result <- updateUserADM(userIri, userUpdatePayload, KnoraSystemInstances.Users.SystemUser, apiRequestID)
        } yield result

        for {
            // run the user update with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => changeBasicUserDataTask(userIri, changeUserRequest, requestingUser, apiRequestID)
            )
        } yield taskResult
    }


    /**
      * Change the users password. The old password needs to be supplied for security purposes.
      *
      * @param userIri           the IRI of the existing user that we want to update.
      * @param changeUserRequest the old and new password.
      * @param requestingUser    the requesting user.
      * @param apiRequestID      the unique api request ID.
      * @return a future containing a [[UserOperationResponseADM]].
      * @throws BadRequestException if necessary parameters are not supplied.
      * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
      * @throws ForbiddenException  if the supplied old password doesn't match with the user's current password.
      * @throws NotFoundException   if the user is not found.
      */
    private def changePasswordADM(userIri: IRI, changeUserRequest: ChangeUserApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = {

        //log.debug(s"changePasswordV1: changePasswordRequest: {}", changeUserRequest)

        /**
          * The actual change password task run with an IRI lock.
          */
        def changePasswordTask(userIri: IRI, changeUserRequest: ChangeUserApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty"))
            _ = if (changeUserRequest.oldPassword.isEmpty || changeUserRequest.newPassword.isEmpty) throw BadRequestException("The user's old and new password need to be both supplied")

            // check if the requesting user is allowed to perform updates
            _ = if (!requestingUser.id.equalsIgnoreCase(userIri)) {
                // not the user
                //log.debug("same user: {}", userProfile.userData.user_id.contains(userIri))
                throw ForbiddenException("User's password can only be changed by the user itself")
            }

            // check if old password matches current user password
            maybeUserADM <- userGetADM(maybeUserIri = Some(userIri), maybeUserEmail = None, requestingUser = KnoraSystemInstances.Users.SystemUser, userInformationType = UserInformationTypeADM.FULL)
            userADM = maybeUserADM.getOrElse(throw NotFoundException(s"User '$userIri' not found"))
            _ = if (!userADM.passwordMatch(changeUserRequest.oldPassword.get)) {
                log.debug("supplied oldPassword: {}, current hash: {}", changeUserRequest.oldPassword.get, userADM.password.get)
                throw ForbiddenException("The supplied old password does not match the current users password.")
            }

            // create the update request
            encoder = new SCryptPasswordEncoder
            newHashedPassword = encoder.encode(changeUserRequest.newPassword.get)
            userUpdatePayload = UserUpdatePayloadADM(password = Some(newHashedPassword))

            // update the users password as SystemUser
            result <- updateUserADM(userIri, userUpdatePayload, KnoraSystemInstances.Users.SystemUser, apiRequestID)

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
      * @param userIri           the IRI of the existing user that we want to update.
      * @param changeUserRequest the new status.
      * @param requestingUser    the requesting user.
      * @param apiRequestID      the unique api request ID.
      * @return a future containing a [[UserOperationResponseADM]].
      * @throws BadRequestException if necessary parameters are not supplied.
      * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
      */
    private def changeUserStatusADM(userIri: IRI, changeUserRequest: ChangeUserApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = {

        //log.debug(s"changeUserStatusV1: changeUserRequest: {}", changeUserRequest)

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

            result <- updateUserADM(userIri, userUpdatePayload, KnoraSystemInstances.Users.SystemUser, apiRequestID)

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
      * @param userIri           the IRI of the existing user that we want to update.
      * @param changeUserRequest the new status.
      * @param requestingUser    the user profile of the requesting user.
      * @param apiRequestID      the unique api request ID.
      * @return a future containing a [[UserOperationResponseADM]].
      * @throws BadRequestException if necessary parameters are not supplied.
      * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
      */
    private def changeUserSystemAdminMembershipStatusADM(userIri: IRI, changeUserRequest: ChangeUserApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = {

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

            result <- updateUserADM(userIri, userUpdatePayload, KnoraSystemInstances.Users.SystemUser, apiRequestID)

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
      * @param userIri the IRI of the user.
      * @param requestingUser the requesting user.
      * @param apiRequestID the unique api request ID.
      * @return a sequence of [[ProjectADM]]
      */
    def userProjectMembershipsGetADM(userIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[Seq[ProjectADM]] = {
        for {
            maybeUser <- userGetADM(maybeUserIri = Some(userIri), maybeUserEmail = None, userInformationType = UserInformationTypeADM.FULL, requestingUser = KnoraSystemInstances.Users.SystemUser)
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
      * @param apiRequestID   the unique api request ID.
      * @return a [[UserProjectMembershipsGetResponseADM]].
      */
    def userProjectMembershipsGetRequestADM(userIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserProjectMembershipsGetResponseADM] = {

        for {
            userExists <- userExists(userIri)
            _ = if (!userExists) {
                throw BadRequestException(s"User $userIri does not exist.")
            }

            projects: Seq[ProjectADM] <- userProjectMembershipsGetADM(userIri, requestingUser, apiRequestID = apiRequestID)
            result = UserProjectMembershipsGetResponseADM(projects = projects)
        } yield result
    }

    /**
      * Adds a user to a project.
      *
      * @param userIri       the user's IRI.
      * @param projectIri    the project's IRI.
      * @param requestingUser the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return
      */
    def userProjectMembershipAddRequestADM(userIri: IRI, projectIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = {

        // log.debug(s"userProjectMembershipAddRequestV1: userIri: {}, projectIri: {}", userIri, projectIri)

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
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                apiRequestID = apiRequestID
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

            result <- updateUserADM(userIri, userUpdatePayload, requestingUser, apiRequestID)

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
      * @param userIri       the user's IRI.
      * @param projectIri    the project's IRI.
      * @param requestingUser the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return
      */
    def userProjectMembershipRemoveRequestADM(userIri: IRI, projectIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = {

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
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                apiRequestID = apiRequestID
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

            result <- updateUserADM(userIri = userIri, userUpdatePayload = userUpdatePayload, requestingUser = KnoraSystemInstances.Users.SystemUser, apiRequestID = apiRequestID)

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
      * @param userIri       the user's IRI.
      * @param requestingUser the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return a [[UserProjectMembershipsGetResponseV1]].
      */
    def userProjectAdminMembershipsGetADM(userIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[Seq[ProjectADM]] = {

        // ToDo: only allow system user
        // ToDo: this is a bit of a hack since the ProjectAdmin group doesn't really exist.

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getUserByIri(
                triplestore = settings.triplestoreType,
                userIri = userIri
            ).toString())

            //_ = log.debug("userDataByIRIGetV1 - sparqlQueryString: {}", sparqlQueryString)

            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            /* the projects the user is member of */
            projectIris: Seq[IRI] = groupedUserData.get(OntologyConstants.KnoraBase.IsInProjectAdminGroup) match {
                case Some(projects) => projects
                case None => Seq.empty[IRI]
            }

            maybeProjectFutures: Seq[Future[Option[ProjectADM]]] = projectIris.map {
                projectIri => (responderManager ? ProjectGetADM(maybeIri = Some(projectIri), maybeShortcode = None, maybeShortname = None, requestingUser = KnoraSystemInstances.Users.SystemUser)).mapTo[Option[ProjectADM]]
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
      * @param userIri       the user's IRI.
      * @param requestingUser the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return a [[UserProjectMembershipsGetResponseV1]].
      */
    def userProjectAdminMembershipsGetRequestADM(userIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserProjectAdminMembershipsGetResponseADM] = {

        // ToDo: which user is allowed to do this operation?
        // ToDo: check permissions

        for {
            userExists <- userExists(userIri)
            _ = if (!userExists) {
                throw BadRequestException(s"User $userIri does not exist.")
            }

            projects: Seq[ProjectADM] <- userProjectAdminMembershipsGetADM(userIri = userIri, requestingUser = KnoraSystemInstances.Users.SystemUser, apiRequestID = apiRequestID)
        } yield UserProjectAdminMembershipsGetResponseADM(projects = projects)
    }

    /**
      * Adds a user to the project admin group of a project.
      *
      * @param userIri       the user's IRI.
      * @param projectIri    the project's IRI.
      * @param requestingUser the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return
      */
    def userProjectAdminMembershipAddRequestADM(userIri: IRI, projectIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = {

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

            result <- updateUserADM(userIri, userUpdatePayload, requestingUser = KnoraSystemInstances.Users.SystemUser, apiRequestID)

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
      * @param userIri       the user's IRI.
      * @param projectIri    the project's IRI.
      * @param requestingUser the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return
      */
    def userProjectAdminMembershipRemoveRequestADM(userIri: IRI, projectIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = {

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

            result <- updateUserADM(userIri, userUpdatePayload, requestingUser = KnoraSystemInstances.Users.SystemUser, apiRequestID)

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
      *
      * @param userIri the IRI of the user.
      * @param requestingUser the requesting user.
      * @param apiRequestID the unique api request ID.
      * @return a sequence of [[GroupADM]].
      */
    def userGroupMembershipsGetADM(userIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[Seq[GroupADM]] = {

        for {
            maybeUserADM: Option[UserADM] <- userGetADM(maybeUserIri = Some(userIri), maybeUserEmail = None, userInformationType = UserInformationTypeADM.FULL, requestingUser = KnoraSystemInstances.Users.SystemUser)
            groups: Seq[GroupADM] = maybeUserADM match {
                case Some(user) => user.groups
                case None => Seq.empty[GroupADM]
            }

        } yield groups
    }

    /**
      * Returns the user's group memberships as a [[UserGroupMembershipsGetResponseADM]]
      *
      *
      * @param userIri the IRI of the user.
      * @param requestingUser the requesting user.
      * @param apiRequestID the unique api request ID.
      * @return a [[UserGroupMembershipsGetResponseADM]].
      */
    def userGroupMembershipsGetRequestADM(userIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserGroupMembershipsGetResponseADM] = {

        for {
            groups: Seq[GroupADM] <- userGroupMembershipsGetADM(userIri, requestingUser, apiRequestID)

        } yield UserGroupMembershipsGetResponseADM(groups = groups)

    }

    /**
      * Adds a user to a group.
      *
      * @param userIri the user's IRI.
      * @param groupIri the group IRI.
      * @param requestingUser the requesting user.
      * @param apiRequestID the unique api request ID.
      * @return a [[UserOperationResponseADM]].
      */
    def userGroupMembershipAddRequestADM(userIri: IRI, groupIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = {

        // log.debug(s"userGroupMembershipAddRequestV1: userIri: {}, groupIri: {}", userIri, groupIri)

        /**
          * The actual task run with an IRI lock.
          */
        def userGroupMembershipAddRequestTask(userIri: IRI, groupIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty."))
            _ = if (groupIri.isEmpty) throw BadRequestException("Group IRI cannot be empty")

            // check if user exists
            userExists <- userExists(userIri)
            _ = if (!userExists) throw NotFoundException(s"The user $userIri does not exist.")

            // check if group exists
            groupExists <- groupExists(groupIri)
            _ = if (!groupExists) throw NotFoundException(s"The group $groupIri does not exist.")

            // get group's info. we need the project IRI.
            maybeGroupADM <- (responderManager ? GroupGetADM(groupIri, KnoraSystemInstances.Users.SystemUser)).mapTo[Option[GroupADM]]
            projectIri = maybeGroupADM.getOrElse(throw webapi.InconsistentTriplestoreDataException(s"Group $groupIri does not exist")).project.id

            // check if the requesting user is allowed to perform updates
            _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
                // not a project or system admin
                // log.debug("project admin: {}, system admin: {}", userProfileV1.permissionData.isProjectAdmin(projectIri), userProfileV1.permissionData.isSystemAdmin)
                throw ForbiddenException("User's group membership can only be changed by a project or system administrator")
            }

            // get users current group membership list
            currentGroupMemberships <- userGroupMembershipsGetADM(
                userIri = userIri,
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                apiRequestID = apiRequestID
            )

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

            result <- updateUserADM(userIri, userUpdatePayload, requestingUser = KnoraSystemInstances.Users.SystemUser, apiRequestID)

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

    def userGroupMembershipRemoveRequestADM(userIri: IRI, groupIri: IRI, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = {

        // log.debug(s"userGroupMembershipRemoveRequestV1: userIri: {}, groupIri: {}", userIri, groupIri)

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
            maybeGroupADM <- (responderManager ? GroupGetADM(groupIri, KnoraSystemInstances.Users.SystemUser)).mapTo[Option[GroupADM]]
            projectIri = maybeGroupADM.getOrElse(throw webapi.InconsistentTriplestoreDataException(s"Group $groupIri does not exist")).project.id

            // check if the requesting user is allowed to perform updates
            _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
                // not a project or system admin
                //log.debug("project admin: {}, system admin: {}", userProfileV1.permissionData.isProjectAdmin(projectIri), userProfileV1.permissionData.isSystemAdmin)
                throw ForbiddenException("User's group membership can only be changed by a project or system administrator")
            }

            // get users current project membership list
            currentGroupMemberships <- userGroupMembershipsGetRequestADM(
                userIri = userIri,
                requestingUser = KnoraSystemInstances.Users.SystemUser,
                apiRequestID = apiRequestID
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

            result <- updateUserADM(userIri, userUpdatePayload, requestingUser, apiRequestID)

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
      * @param userIri           the IRI of the existing user that we want to update.
      * @param userUpdatePayload the updated information.
      * @param requestingUser    the requesting user.
      * @param apiRequestID      the unique api request ID.
      * @return a future containing a [[UserOperationResponseADM]].
      * @throws BadRequestException         if necessary parameters are not supplied.
      * @throws UpdateNotPerformedException if the update was not performed.
      */
    private def updateUserADM(userIri: IRI, userUpdatePayload: UserUpdatePayloadADM, requestingUser: UserADM, apiRequestID: UUID): Future[UserOperationResponseADM] = {

        // log.debug("updateUserV1 - userUpdatePayload: {}", userUpdatePayload)

        /* Remember: some checks on UserUpdatePayloadV1 are implemented in the case class */

        if (userUpdatePayload.email.nonEmpty) {
            // changing email address, so we need to invalidate the cached profile under this email
            invalidateCachedUserADM(email = userUpdatePayload.email)
        }

        for {
            /* Update the user */
            updateUserSparqlString <- Future(queries.sparql.admin.txt.updateUser(
                adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
                triplestore = settings.triplestoreType,
                userIri = userIri,
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
            //_ = log.debug(s"updateUserV1 - query: $updateUserSparqlString")
            createResourceResponse <- (storeManager ? SparqlUpdateRequest(updateUserSparqlString)).mapTo[SparqlUpdateResponse]

            // need to invalidate cached user profile
            _ = invalidateCachedUserADM(Some(userIri), userUpdatePayload.email)

            /* Verify that the user was updated. */
            maybeUpdatedUserADM <- userGetADM(maybeUserIri = Some(userIri), maybeUserEmail = None, requestingUser = requestingUser, userInformationType = UserInformationTypeADM.FULL)
            updatedUserADM = maybeUpdatedUserADM.getOrElse(throw UpdateNotPerformedException("User was not updated. Please report this as a possible bug."))

            //_ = log.debug(s"apiUpdateRequest: $apiUpdateRequest /  updatedUserdata: $updatedUserData")

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

        } yield UserOperationResponseADM(updatedUserADM.ofType(UserInformationTypeADM.RESTRICTED))
    }


    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Helper method used to create a [[UserDataV1]] from the [[SparqlSelectResponse]] containing user data.
      *
      * @param userDataQueryResponse a [[SparqlSelectResponse]] containing user data.
      * @param short                 denotes if all information should be returned. If short == true, then no token and password should be returned.
      * @return a [[UserDataV1]] containing the user's basic data.
      */
    private def userDataQueryResponse2UserData(userDataQueryResponse: SparqlSelectResponse, short: Boolean): Future[Option[UserDataV1]] = {

        // log.debug("userDataQueryResponse2UserData - " + MessageUtil.toSource(userDataQueryResponse))

        if (userDataQueryResponse.results.bindings.nonEmpty) {
            val returnedUserIri = userDataQueryResponse.getFirstRow.rowMap("s")

            val groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            // _ = log.debug(s"userDataQueryResponse2UserProfile - groupedUserData: ${MessageUtil.toSource(groupedUserData)}")

            val userDataV1 = UserDataV1(
                lang = groupedUserData.get(OntologyConstants.KnoraBase.PreferredLanguage) match {
                    case Some(langList) => langList.head
                    case None => settings.fallbackLanguage
                },
                user_id = Some(returnedUserIri),
                email = groupedUserData.get(OntologyConstants.KnoraBase.Email).map(_.head),
                firstname = groupedUserData.get(OntologyConstants.KnoraBase.GivenName).map(_.head),
                lastname = groupedUserData.get(OntologyConstants.KnoraBase.FamilyName).map(_.head),
                password = if (!short) {
                    groupedUserData.get(OntologyConstants.KnoraBase.Password).map(_.head)
                } else None,
                status = groupedUserData.get(OntologyConstants.KnoraBase.Status).map(_.head.toBoolean)
            )
            // _ = log.debug(s"userDataQueryResponse - userDataV1: {}", MessageUtil.toSource(userDataV1)")
            FastFuture.successful(Some(userDataV1))
        } else {
            FastFuture.successful(None)
        }
    }

    /**
      * Helper method used to create a [[UserADM]] from the [[SparqlExtendedConstructResponse]] containing user data.
      *
      * @param statements result from the SPARQL query containing user data.
      * @return a [[UserADM]] containing the user's data.
      */
    private def statements2UserADM(statements: (SubjectV2, Map[IRI, Seq[LiteralV2]]), requestingUser: UserADM): Future[Option[UserADM]] = {

        // log.debug("statements2UserADM - statements: {}", statements)

        val userIri: IRI = statements._1.toString
        val propsMap: Map[IRI, Seq[LiteralV2]] = statements._2

        log.debug("statements2UserADM - userIri: {}", userIri)

        if (propsMap.nonEmpty) {

            /* the groups the user is member of (only explicit groups) */
            val groupIris: Seq[IRI] = propsMap.get(OntologyConstants.KnoraBase.IsInGroup) match {
                case Some(groups) => groups.map(_.asInstanceOf[IriLiteralV2].value)
                case None => Seq.empty[IRI]
            }

            // log.debug(s"statements2UserADM - groupIris: {}", MessageUtil.toSource(groupIris))

            /* the projects the user is member of (only explicit projects) */
            val projectIris: Seq[IRI] = propsMap.get(OntologyConstants.KnoraBase.IsInProject) match {
                case Some(projects) => projects.map(_.asInstanceOf[IriLiteralV2].value)
                case None => Seq.empty[IRI]
            }

            // log.debug(s"statements2UserADM - projectIris: {}", MessageUtil.toSource(projectIris))

            /* the projects for which the user is implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group */
            val isInProjectAdminGroups: Seq[IRI] = propsMap.getOrElse(OntologyConstants.KnoraBase.IsInProjectAdminGroup, Vector.empty[IRI]).map(_.asInstanceOf[IriLiteralV2].value)

            /* is the user implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#SystemAdmin' group */
            val isInSystemAdminGroup = propsMap.get(OntologyConstants.KnoraBase.IsInSystemAdminGroup).exists(p => p.head.asInstanceOf[BooleanLiteralV2].value)

            for {
                /* get the user's permission profile from the permissions responder */
                permissionData <- (responderManager ? PermissionDataGetADM(projectIris = projectIris,
                    groupIris = groupIris,
                    isInProjectAdminGroups = isInProjectAdminGroups,
                    isInSystemAdminGroup = isInSystemAdminGroup,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )).mapTo[PermissionsDataADM]

                maybeGroupFutures: Seq[Future[Option[GroupADM]]] = groupIris.map {
                    groupIri => (responderManager ? GroupGetADM(groupIri = groupIri, requestingUser = KnoraSystemInstances.Users.SystemUser)).mapTo[Option[GroupADM]]
                }
                maybeGroups: Seq[Option[GroupADM]] <- Future.sequence(maybeGroupFutures)
                groups: Seq[GroupADM] = maybeGroups.flatten

                // _ = log.debug("statements2UserADM - groups: {}", MessageUtil.toSource(groups))

                maybeProjectFutures: Seq[Future[Option[ProjectADM]]] = projectIris.map {
                    projectIri => (responderManager ? ProjectGetADM(maybeIri = Some(projectIri), maybeShortcode = None, maybeShortname = None, requestingUser = KnoraSystemInstances.Users.SystemUser)).mapTo[Option[ProjectADM]]
                }
                maybeProjects: Seq[Option[ProjectADM]] <- Future.sequence(maybeProjectFutures)
                projects: Seq[ProjectADM] = maybeProjects.flatten

                // _ = log.debug("statements2UserADM - projects: {}", MessageUtil.toSource(projects))

                /* construct the user profile from the different parts */
                user = UserADM(
                    id = userIri,
                    email = propsMap.getOrElse(OntologyConstants.KnoraBase.Email, throw InconsistentTriplestoreDataException(s"User: $userIri has no 'email' defined.")).head.asInstanceOf[StringLiteralV2].value,
                    password = propsMap.get(OntologyConstants.KnoraBase.Password).map(_.head.asInstanceOf[StringLiteralV2].value),
                    token = None,
                    givenName = propsMap.getOrElse(OntologyConstants.KnoraBase.GivenName, throw InconsistentTriplestoreDataException(s"User: $userIri has no 'givenName' defined.")).head.asInstanceOf[StringLiteralV2].value,
                    familyName = propsMap.getOrElse(OntologyConstants.KnoraBase.FamilyName, throw InconsistentTriplestoreDataException(s"User: $userIri has no 'familyName' defined.")).head.asInstanceOf[StringLiteralV2].value,
                    status = propsMap.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"User: $userIri has no 'status' defined.")).head.asInstanceOf[BooleanLiteralV2].value,
                    lang = propsMap.getOrElse(OntologyConstants.KnoraBase.PreferredLanguage, throw InconsistentTriplestoreDataException(s"User: $userIri has no 'preferredLanguage' defined.")).head.asInstanceOf[StringLiteralV2].value,
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
    def userExists(userIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.v1.txt.checkUserExists(userIri = userIri).toString)
            // _ = log.debug("userExists - query: {}", askString)

            checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkUserExistsResponse.result

        } yield result
    }

    /**
      * Helper method for checking if a project exists.
      *
      * @param projectIri the IRI of the project.
      * @return a [[Boolean]].
      */
    def projectExists(projectIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkProjectExistsByIri(projectIri = projectIri).toString)
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
    def groupExists(groupIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkGroupExistsByIri(groupIri = groupIri).toString)
            // _ = log.debug("groupExists - query: {}", askString)

            checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkUserExistsResponse.result

        } yield result
    }

    /**
      * Writes the user profile to cache.
      *
      * @param user a [[UserADM]].
      * @return true if writing was successful.
      * @throws ApplicationCacheException when there is a problem with writing the user's profile to cache.
      */
    def writeUserADMToCache(user: UserADM): Boolean = {

        val iri = user.id

        val email = user.email

        CacheUtil.put(USER_ADM_CACHE_NAME, iri, user)

        if (CacheUtil.get(USER_ADM_CACHE_NAME, iri).isEmpty) {
            throw ApplicationCacheException("Writing the user's profile to cache was not successful.")
        }

        CacheUtil.put(USER_ADM_CACHE_NAME, email, user)
        if (CacheUtil.get(USER_ADM_CACHE_NAME, email).isEmpty) {
            throw ApplicationCacheException("Writing the user's profile to cache was not successful.")
        }

        true
    }

    /**
      * Removes the user profile from cache.
      *
      * @param userIri the user's IRI und which a profile could be cached.
      * @param email   the user's email under which a profile could be cached.
      */
    def invalidateCachedUserADM(userIri: Option[IRI] = None, email: Option[String] = None): Unit = {

        if (userIri.nonEmpty) {
            CacheUtil.remove(USER_ADM_CACHE_NAME, userIri.get)
        }

        if (email.nonEmpty) {
            CacheUtil.remove(USER_ADM_CACHE_NAME, email.get)
        }
    }

}
