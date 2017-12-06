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

package org.knora.webapi.responders.v1

import java.util.UUID

import akka.actor.Status
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionDataGetADM, PermissionsDataADM}
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.groupmessages.{GroupInfoByIRIGetRequestV1, GroupInfoResponseV1}
import org.knora.webapi.messages.v1.responder.permissionmessages._
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoByIRIGetV1, ProjectInfoV1}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileTypeV1.UserProfileType
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{CacheUtil, KnoraIdUtil}
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder

import scala.concurrent.Future

/**
  * Provides information about Knora users to other responders.
  */
class UsersResponderV1 extends Responder {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    // The IRI used to lock user creation and update
    val USERS_GLOBAL_LOCK_IRI = "http://rdfh.ch/users"

    val USER_PROFILE_CACHE_NAME = "userProfileCache"

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1]], and returns a message of type [[UserADM]]
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case UsersGetV1() => future2Message(sender(), usersGetV1, log)
        case UsersGetRequestV1(userProfileV1) => future2Message(sender(), usersGetRequestV1(userProfileV1), log)
        case UserDataByIriGetV1(userIri, short) => future2Message(sender(), userDataByIriGetV1(userIri, short), log)
        case UserProfileByIRIGetV1(userIri, profileType) => future2Message(sender(), userProfileByIRIGetV1(userIri, profileType), log)
        case UserProfileByIRIGetRequestV1(userIri, profileType, userProfile) => future2Message(sender(), userProfileByIRIGetRequestV1(userIri, profileType, userProfile), log)
        case UserProfileByEmailGetV1(email, profileType) => future2Message(sender(), userProfileByEmailGetV1(email, profileType), log)
        case UserProfileByEmailGetRequestV1(email, profileType, userProfile) => future2Message(sender(), userProfileByEmailGetRequestV1(email, profileType, userProfile), log)
        case UserCreateRequestV1(createRequest, userProfile, apiRequestID) => future2Message(sender(), createNewUserV1(createRequest, userProfile, apiRequestID), log)
        case UserChangeBasicUserDataRequestV1(userIri, changeUserRequest, userProfile, apiRequestID) => future2Message(sender(), changeBasicUserDataV1(userIri, changeUserRequest, userProfile, apiRequestID), log)
        case UserChangePasswordRequestV1(userIri, changeUserRequest, userProfile, apiRequestID) => future2Message(sender(), changePasswordV1(userIri, changeUserRequest, userProfile, apiRequestID), log)
        case UserChangeStatusRequestV1(userIri, changeUserRequest, userProfile, apiRequestID) => future2Message(sender(), changeUserStatusV1(userIri, changeUserRequest, userProfile, apiRequestID), log)
        case UserChangeSystemAdminMembershipStatusRequestV1(userIri, changeSystemAdminMembershipStatusRequest, userProfile, apiRequestID) => future2Message(sender(), changeUserSystemAdminMembershipStatusV1(userIri, changeSystemAdminMembershipStatusRequest, userProfile, apiRequestID), log)
        case UserProjectMembershipsGetRequestV1(userIri, userProfile, apiRequestID) => future2Message(sender(), userProjectMembershipsGetRequestV1(userIri, userProfile, apiRequestID), log)
        case UserProjectMembershipAddRequestV1(userIri, projectIri, userProfile, apiRequestID) => future2Message(sender(), userProjectMembershipAddRequestV1(userIri, projectIri, userProfile, apiRequestID), log)
        case UserProjectMembershipRemoveRequestV1(userIri, projectIri, userProfile, apiRequestID) => future2Message(sender(), userProjectMembershipRemoveRequestV1(userIri, projectIri, userProfile, apiRequestID), log)
        case UserProjectAdminMembershipsGetRequestV1(userIri, userProfile, apiRequestID) => future2Message(sender(), userProjectAdminMembershipsGetRequestV1(userIri, userProfile, apiRequestID), log)
        case UserProjectAdminMembershipAddRequestV1(userIri, projectIri, userProfile, apiRequestID) => future2Message(sender(), userProjectAdminMembershipAddRequestV1(userIri, projectIri, userProfile, apiRequestID), log)
        case UserProjectAdminMembershipRemoveRequestV1(userIri, projectIri, userProfile, apiRequestID) => future2Message(sender(), userProjectAdminMembershipRemoveRequestV1(userIri, projectIri, userProfile, apiRequestID), log)
        case UserGroupMembershipsGetRequestV1(userIri, userProfile, apiRequestID) => future2Message(sender(), userGroupMembershipsGetRequestV1(userIri, userProfile, apiRequestID), log)
        case UserGroupMembershipAddRequestV1(userIri, projectIri, userProfile, apiRequestID) => future2Message(sender(), userGroupMembershipAddRequestV1(userIri, projectIri, userProfile, apiRequestID), log)
        case UserGroupMembershipRemoveRequestV1(userIri, projectIri, userProfile, apiRequestID) => future2Message(sender(), userGroupMembershipRemoveRequestV1(userIri, projectIri, userProfile, apiRequestID), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }


    /**
      * Gets all the users and returns them as a sequence of [[UserDataV1]].
      *
      * @return all the users as a sequence of [[UserDataV1]].
      */
    private def usersGetV1: Future[Seq[UserDataV1]] = {

        //log.debug("usersGetV1")

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getUsers(
                triplestore = settings.triplestoreType
            ).toString())

            usersResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            usersResponseRows: Seq[VariableResultsRow] = usersResponse.results.bindings

            usersWithProperties: Map[String, Map[String, String]] = usersResponseRows.groupBy(_.rowMap("s")).map {
                case (userIri: IRI, rows: Seq[VariableResultsRow]) => (userIri, rows.map(row => (row.rowMap("p"), row.rowMap("o"))).toMap)
            }

            users = usersWithProperties.map {
                case (userIri: IRI, propsMap: Map[String, String]) =>

                    UserDataV1(
                        lang = propsMap.get(OntologyConstants.KnoraBase.PreferredLanguage) match {
                            case Some(langList) => langList
                            case None => settings.fallbackLanguage
                        },
                        user_id = Some(userIri),
                        email = propsMap.get(OntologyConstants.KnoraBase.Email),
                        firstname = propsMap.get(OntologyConstants.KnoraBase.GivenName),
                        lastname = propsMap.get(OntologyConstants.KnoraBase.FamilyName),
                        status = propsMap.get(OntologyConstants.KnoraBase.Status).map(_.toBoolean)
                    )
            }.toSeq

        } yield users
    }

    /**
      * Gets all the users and returns them as a [[UsersGetResponseV1]].
      *
      * @param userProfileV1 the type of the requested profile (restricted of full).
      * @return all the users as a [[UsersGetResponseV1]].
      */
    private def usersGetRequestV1(userProfileV1: UserProfileV1): Future[UsersGetResponseV1] = {
        for {
            maybeUsersListToReturn <- usersGetV1
            result = maybeUsersListToReturn match {
                case users: Seq[UserDataV1] if users.nonEmpty => UsersGetResponseV1(users = users)
                case _ => throw NotFoundException(s"No users found")
            }
        } yield result
    }

    /**
      * Gets basic information about a Knora user, and returns it in a [[UserDataV1]].
      *
      * @param userIri the IRI of the user.
      * @return a [[UserDataV1]] describing the user.
      */
    private def userDataByIriGetV1(userIri: IRI, short: Boolean): Future[Option[UserDataV1]] = {
        //log.debug("userDataByIriGetV1 - userIri: {}", userIri)

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getUserByIri(
                triplestore = settings.triplestoreType,
                userIri = userIri
            ).toString())

            // _ = log.debug("userDataByIRIGetV1 - sparqlQueryString: {}", sparqlQueryString)

            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            maybeUserDataV1 <- userDataQueryResponse2UserData(userDataQueryResponse, short)

            // _ = log.debug("userDataByIriGetV1 - maybeUserDataV1: {}", maybeUserDataV1)

        } yield maybeUserDataV1

    }

    /**
      * Gets information about a Knora user, and returns it in a [[UserProfileV1]]. If possible, tries to retrieve the
      * user profile from cache. If not, it retrieves it from the triplestore and writes it to the cache.
      *
      * @param userIri     the IRI of the user.
      * @param profileType the type of the requested profile (restricted of full).
      * @return a [[UserProfileV1]] describing the user.
      */
    private def userProfileByIRIGetV1(userIri: IRI, profileType: UserProfileType): Future[Option[UserProfileV1]] = {
        // log.debug(s"userProfileByIRIGetV1: userIri = $userIRI', clean = '$profileType'")

        CacheUtil.get[UserProfileV1](USER_PROFILE_CACHE_NAME, userIri) match {
            case Some(userProfile) =>
                // found a user profile in the cache
                log.debug(s"userProfileByIRIGetV1 - cache hit: $userProfile")
                FastFuture.successful(Some(userProfile.ofType(profileType)))
            case None => {
                for {
                    sparqlQueryString <- Future(queries.sparql.v1.txt.getUserByIri(
                        triplestore = settings.triplestoreType,
                        userIri = userIri
                    ).toString())

                    // _ = log.debug(s"userProfileByIRIGetV1 - sparqlQueryString: {}", sparqlQueryString)

                    userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

                    maybeUserProfileV1 <- userDataQueryResponse2UserProfile(userDataQueryResponse)

                    _ = if (maybeUserProfileV1.nonEmpty) {
                        writeUserProfileV1ToCache(maybeUserProfileV1.get)
                    }

                    result = maybeUserProfileV1.map(_.ofType(profileType))

                    // _ = log.debug("userProfileByIRIGetV1 - maybeUserProfileV1: {}", MessageUtil.toSource(maybeUserProfileV1))
                } yield result // UserProfileV1(userData, groups, projects_info, sessionId, isSystemUser, permissionData)
            }
        }
    }

    /**
      * Gets information about a Knora user, and returns it as a [[UserProfileResponseV1]].
      *
      * @param userIRI     the IRI of the user.
      * @param profileType the type of the requested profile (restriced or full).
      * @param userProfile the requesting user's profile.
      * @return a [[UserProfileResponseV1]]
      */
    private def userProfileByIRIGetRequestV1(userIRI: IRI, profileType: UserProfileType, userProfile: UserADM): Future[UserProfileResponseV1] = {
        for {
            maybeUserProfileToReturn <- userProfileByIRIGetV1(userIRI, profileType)
            result = maybeUserProfileToReturn match {
                case Some(up) => UserProfileResponseV1(up)
                case None => throw NotFoundException(s"User '$userIRI' not found")
            }
        } yield result
    }

    /**
      * Gets information about a Knora user, and returns it in a [[UserADM]]. If possible, tries to retrieve the user profile
      * from cache. If not, it retrieves it from the triplestore and writes it to the cache.
      *
      * @param email       the email of the user.
      * @param profileType the type of the requested profile (restricted or full).
      * @return a [[UserADM]] describing the user.
      */
    private def userProfileByEmailGetV1(email: String, profileType: UserProfileType): Future[Option[UserADM]] = {
        // log.debug(s"userProfileByEmailGetV1: username = '{}', type = '{}'", email, profileType)

        CacheUtil.get[UserADM](USER_PROFILE_CACHE_NAME, email) match {
            case Some(userProfile) =>
                // found a user profile in the cache
                log.debug(s"userProfileByIRIGetV1 - cache hit: $userProfile")
                FastFuture.successful(Some(userProfile.ofType(profileType)))
            case None => {
                for {
                    sparqlQueryString <- Future(queries.sparql.v1.txt.getUserByEmail(
                        triplestore = settings.triplestoreType,
                        email = email
                    ).toString())
                    //_ = log.debug(s"userProfileByEmailGetV1 - sparqlQueryString: $sparqlQueryString")

                    userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

                    //_ = log.debug(MessageUtil.toSource(userDataQueryResponse))
                    maybeUserProfileV1 <- userDataQueryResponse2UserProfile(userDataQueryResponse)

                    _ = if (maybeUserProfileV1.nonEmpty) {
                        writeUserProfileV1ToCache(maybeUserProfileV1.get)
                    }

                    result = maybeUserProfileV1.map(_.ofType(profileType))

                    // _ = log.debug("userProfileByEmailGetV1 - maybeUserProfileV1: {}", MessageUtil.toSource(maybeUserProfileV1))

                } yield result // UserProfileV1(userDataV1, groupIris, projectIris)
            }
        }


    }

    /**
      * Gets information about a Knora user, and returns it as a [[UserProfileResponseV1]].
      *
      * @param email       the email of the user.
      * @param profileType the type of the requested profile (restricted or full).
      * @param userProfile the requesting user's profile.
      * @return a [[UserProfileResponseV1]]
      * @throws NotFoundException if the user with the supplied email is not found.
      */
    private def userProfileByEmailGetRequestV1(email: String, profileType: UserProfileType, userProfile: UserADM): Future[UserProfileResponseV1] = {
        for {
            maybeUserProfileToReturn <- userProfileByEmailGetV1(email, profileType)
            result = maybeUserProfileToReturn match {
                case Some(up: UserADM) => UserProfileResponseV1(up)
                case None => throw NotFoundException(s"User '$email' not found")
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
      * @param createRequest a [[CreateUserApiRequestV1]] object containing information about the new user to be created.
      * @param userProfile   a [[UserADM]] object containing information about the requesting user.
      * @return a future containing the [[UserOperationResponseV1]].
      */
    private def createNewUserV1(createRequest: CreateUserApiRequestV1, userProfile: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        def createNewUserTask(createRequest: CreateUserApiRequestV1, userProfile: UserADM, apiRequestID: UUID) = for {
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
            createNewUserSparqlString = queries.sparql.v1.txt.createNewUser(
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
            sparqlQuery = queries.sparql.v1.txt.getUserByIri(
                triplestore = settings.triplestoreType,
                userIri = userIri
            ).toString()
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            // create the user profile
            maybeNewUserProfile <- userDataQueryResponse2UserProfile(userDataQueryResponse)

            newUserProfile = maybeNewUserProfile.getOrElse(throw UpdateNotPerformedException(s"User $userIri was not created. Please report this as a possible bug."))

            // write the newly created user profile to cache
            _ = writeUserProfileV1ToCache(newUserProfile)

            // create the user operation response
            userOperationResponseV1 = UserOperationResponseV1(newUserProfile.ofType(UserProfileTypeV1.RESTRICTED))

        } yield userOperationResponseV1

        for {
            // run user creation with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                USERS_GLOBAL_LOCK_IRI,
                () => createNewUserTask(createRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }

    /**
      * Updates an existing user. Only basic user data information (email, givenName, familyName, lang)
      * can be changed. For changing the password or user status, use the separate methods.
      *
      * @param userIri           the IRI of the existing user that we want to update.
      * @param changeUserRequest the updated information.
      * @param userProfile       the user profile of the requesting user.
      * @param apiRequestID      the unique api request ID.
      * @return a future containing a [[UserOperationResponseV1]].
      * @throws BadRequestException if the necessary parameters are not supplied.
      * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
      */
    private def changeBasicUserDataV1(userIri: IRI, changeUserRequest: ChangeUserApiRequestV1, userProfile: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        //log.debug(s"changeBasicUserDataV1: changeUserRequest: {}", changeUserRequest)

        /**
          * The actual change basic user data task run with an IRI lock.
          */
        def changeBasicUserDataTask(userIri: IRI, changeUserRequest: ChangeUserApiRequestV1, userProfile: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = for {

            // check if the requesting user is allowed to perform updates
            _ <- Future(
                if (!userProfile.userData.user_id.contains(userIri) && !userProfile.permissionData.isSystemAdmin) {
                    // not the user or a system admin
                    //log.debug("same user: {}, system admin: {}", userProfile.userData.user_id.contains(userIri), userProfile.permissionData.isSystemAdmin)
                    throw ForbiddenException("User information can only be changed by the user itself or a system administrator")
                }
            )

            // check if necessary information is present
            _ = if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

            parametersCount = List(changeUserRequest.email, changeUserRequest.givenName, changeUserRequest.familyName, changeUserRequest.lang).flatten.size
            _ = if (parametersCount == 0) throw BadRequestException("At least one parameter needs to be supplied. No data would be changed. Aborting request for changing of basic user data.")

            userUpdatePayload = UserUpdatePayloadV1(
                email = changeUserRequest.email,
                givenName = changeUserRequest.givenName,
                familyName = changeUserRequest.familyName,
                lang = changeUserRequest.lang
            )

            result <- updateUserV1(userIri, userUpdatePayload, userProfile, apiRequestID)
        } yield result

        for {
            // run the user update with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => changeBasicUserDataTask(userIri, changeUserRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }


    /**
      * Change the users password. The old password needs to be supplied for security purposes.
      *
      * @param userIri           the IRI of the existing user that we want to update.
      * @param changeUserRequest the old and new password.
      * @param userProfile       the user profile of the requesting user.
      * @param apiRequestID      the unique api request ID.
      * @return a future containing a [[UserOperationResponseV1]].
      * @throws BadRequestException if necessary parameters are not supplied.
      * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
      * @throws ForbiddenException  if the supplied old password doesn't match with the user's current password.
      * @throws NotFoundException   if the user is not found.
      */
    private def changePasswordV1(userIri: IRI, changeUserRequest: ChangeUserApiRequestV1, userProfile: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        //log.debug(s"changePasswordV1: changePasswordRequest: {}", changeUserRequest)

        /**
          * The actual change password task run with an IRI lock.
          */
        def changePasswordTask(userIri: IRI, changeUserRequest: ChangeUserApiRequestV1, userProfile: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty"))
            _ = if (changeUserRequest.oldPassword.isEmpty || changeUserRequest.newPassword.isEmpty) throw BadRequestException("The user's old and new password need to be both supplied")

            // check if the requesting user is allowed to perform updates
            _ = if (!userProfile.userData.user_id.contains(userIri)) {
                // not the user
                //log.debug("same user: {}", userProfile.userData.user_id.contains(userIri))
                throw ForbiddenException("User's password can only be changed by the user itself")
            }

            // check if old password matches current user password
            maybeUserProfile <- userProfileByIRIGetV1(userIri, UserProfileTypeV1.FULL)
            userProfile = maybeUserProfile.getOrElse(throw NotFoundException(s"User '$userIri' not found"))
            _ = if (!userProfile.passwordMatch(changeUserRequest.oldPassword.get)) {
                log.debug("supplied oldPassword: {}, current hash: {}", changeUserRequest.oldPassword.get, userProfile.userData.password.get)
                throw ForbiddenException("The supplied old password does not match the current users password.")
            }

            // create the update request
            encoder = new SCryptPasswordEncoder
            newHashedPassword = encoder.encode(changeUserRequest.newPassword.get)
            userUpdatePayload = UserUpdatePayloadV1(password = Some(newHashedPassword))

            // update the users password
            result <- updateUserV1(userIri, userUpdatePayload, userProfile, apiRequestID)

        } yield result

        for {
            // run the change password task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => changePasswordTask(userIri, changeUserRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }

    /**
      * Change the user's status (active / inactive).
      *
      * @param userIri           the IRI of the existing user that we want to update.
      * @param changeUserRequest the new status.
      * @param userProfile       the user profile of the requesting user.
      * @param apiRequestID      the unique api request ID.
      * @return a future containing a [[UserOperationResponseV1]].
      * @throws BadRequestException if necessary parameters are not supplied.
      * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
      */
    private def changeUserStatusV1(userIri: IRI, changeUserRequest: ChangeUserApiRequestV1, userProfile: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        //log.debug(s"changeUserStatusV1: changeUserRequest: {}", changeUserRequest)

        /**
          * The actual change user status task run with an IRI lock.
          */
        def changeUserStatusTask(userIri: IRI, changeUserRequest: ChangeUserApiRequestV1, userProfile: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = for {

            _ <- Future(
                // check if necessary information is present
                if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")
            )
            _ = if (changeUserRequest.status.isEmpty) throw BadRequestException("New user status cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!userProfile.userData.user_id.contains(userIri) && !userProfile.permissionData.isSystemAdmin) {
                // not the user or a system admin
                // log.debug("same user: {}, system admin: {}", userProfile.userData.user_id.contains(userIri), userProfile.permissionData.isSystemAdmin)
                throw ForbiddenException("User's status can only be changed by the user itself or a system administrator")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadV1(status = changeUserRequest.status)

            result <- updateUserV1(userIri, userUpdatePayload, userProfile, apiRequestID)

        } yield result

        for {
            // run the change status task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => changeUserStatusTask(userIri, changeUserRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }

    /**
      * Change the user's system admin membership status (active / inactive).
      *
      * @param userIri           the IRI of the existing user that we want to update.
      * @param changeUserRequest the new status.
      * @param userProfile       the user profile of the requesting user.
      * @param apiRequestID      the unique api request ID.
      * @return a future containing a [[UserOperationResponseV1]].
      * @throws BadRequestException if necessary parameters are not supplied.
      * @throws ForbiddenException  if the user doesn't hold the necessary permission for the operation.
      */
    private def changeUserSystemAdminMembershipStatusV1(userIri: IRI, changeUserRequest: ChangeUserApiRequestV1, userProfile: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        //log.debug(s"changeUserSystemAdminMembershipStatusV1: changeUserRequest: {}", changeUserRequest)

        /**
          * The actual change user status task run with an IRI lock.
          */
        def changeUserSystemAdminMembershipStatusTask(userIri: IRI, changeUserRequest: ChangeUserApiRequestV1, userProfile: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty"))
            _ = if (changeUserRequest.systemAdmin.isEmpty) throw BadRequestException("New user system admin membership status cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!userProfile.permissionData.isSystemAdmin) {
                // not a system admin
                // log.debug("system admin: {}", userProfile.permissionData.isSystemAdmin)
                throw ForbiddenException("User's system admin membership can only be changed by a system administrator")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadV1(systemAdmin = changeUserRequest.systemAdmin)

            result <- updateUserV1(userIri, userUpdatePayload, userProfile, apiRequestID)

        } yield result


        for {
            // run the change status task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => changeUserSystemAdminMembershipStatusTask(userIri, changeUserRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }


    /**
      * Returns the user's project memberships, where the result contains the IRIs of the projects the user is member of.
      *
      * @param userIri       the user's IRI.
      * @param userProfileV1 the user profile of the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return a [[UserProjectMembershipsGetResponseV1]].
      */
    def userProjectMembershipsGetRequestV1(userIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserProjectMembershipsGetResponseV1] = {
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
            projectIris: Seq[IRI] = groupedUserData.get(OntologyConstants.KnoraBase.IsInProject) match {
                case Some(projects) => projects
                case None => Seq.empty[IRI]
            }

            // _ = log.debug("userProjectMembershipsGetRequestV1 - userIri: {}, projectIris: {}", userIri, projectIris)
        } yield UserProjectMembershipsGetResponseV1(projects = projectIris)
    }

    /**
      * Adds a user to a project.
      *
      * @param userIri       the user's IRI.
      * @param projectIri    the project's IRI.
      * @param userProfileV1 the user profile of the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return
      */
    def userProjectMembershipAddRequestV1(userIri: IRI, projectIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        // log.debug(s"userProjectMembershipAddRequestV1: userIri: {}, projectIri: {}", userIri, projectIri)

        /**
          * The actual task run with an IRI lock.
          */
        def userProjectMembershipAddRequestTask(userIri: IRI, projectIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty."))
            _ = if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!userProfileV1.permissionData.isProjectAdmin(projectIri) && !userProfileV1.permissionData.isSystemAdmin) {
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
            currentProjectMemberships <- userProjectMembershipsGetRequestV1(
                userIri = userIri,
                userProfileV1 = userProfileV1,
                apiRequestID = apiRequestID
            )

            currentProjectMembershipIris: Seq[IRI] = currentProjectMemberships.projects

            // check if user is already member and if not then append to list
            updatedProjectMembershipIris = if (!currentProjectMembershipIris.contains(projectIri)) {
                currentProjectMembershipIris :+ projectIri
            } else {
                throw BadRequestException(s"User $userIri is already member of project $projectIri.")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadV1(projects = Some(updatedProjectMembershipIris))

            result <- updateUserV1(userIri, userUpdatePayload, userProfileV1, apiRequestID)

        } yield result


        for {
            // run the task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => userProjectMembershipAddRequestTask(userIri, projectIri, userProfileV1, apiRequestID)
            )
        } yield taskResult

    }

    /**
      * Removes a user from a project.
      *
      * @param userIri       the user's IRI.
      * @param projectIri    the project's IRI.
      * @param userProfileV1 the user profile of the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return
      */
    def userProjectMembershipRemoveRequestV1(userIri: IRI, projectIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        // log.debug(s"userProjectMembershipRemoveRequestV1: userIri: {}, projectIri: {}", userIri, projectIri)

        /**
          * The actual task run with an IRI lock.
          */
        def userProjectMembershipRemoveRequestTask(userIri: IRI, projectIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty."))
            _ = if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!userProfileV1.permissionData.isProjectAdmin(projectIri) && !userProfileV1.permissionData.isSystemAdmin) {
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
            currentProjectMemberships <- userProjectMembershipsGetRequestV1(
                userIri = userIri,
                userProfileV1 = userProfileV1,
                apiRequestID = apiRequestID
            )

            currentProjectMembershipIris: Seq[IRI] = currentProjectMemberships.projects

            // check if user is not already a member and if he is then remove the project from to list
            updatedProjectMembershipIris = if (currentProjectMembershipIris.contains(projectIri)) {
                currentProjectMembershipIris diff Seq(projectIri)
            } else {
                throw BadRequestException(s"User $userIri is not member of project $projectIri.")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadV1(projects = Some(updatedProjectMembershipIris))

            result <- updateUserV1(userIri, userUpdatePayload, userProfileV1, apiRequestID)

        } yield result


        for {
            // run the task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => userProjectMembershipRemoveRequestTask(userIri, projectIri, userProfileV1, apiRequestID)
            )
        } yield taskResult
    }

    /**
      * Returns the user's project admin group memberships, where the result contains the IRIs of the projects the user
      * is a member of the project admin group.
      *
      * @param userIri       the user's IRI.
      * @param userProfileV1 the user profile of the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return a [[UserProjectMembershipsGetResponseV1]].
      */
    def userProjectAdminMembershipsGetRequestV1(userIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserProjectAdminMembershipsGetResponseV1] = {
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

            // _ = log.debug("userProjectAdminMembershipsGetRequestV1 - userIri: {}, projectIris: {}", userIri, projectIris)
        } yield UserProjectAdminMembershipsGetResponseV1(projects = projectIris)
    }

    /**
      * Adds a user to the project admin group of a project.
      *
      * @param userIri       the user's IRI.
      * @param projectIri    the project's IRI.
      * @param userProfileV1 the user profile of the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return
      */
    def userProjectAdminMembershipAddRequestV1(userIri: IRI, projectIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        // log.debug(s"userProjectAdminMembershipAddRequestV1: userIri: {}, projectIri: {}", userIri, projectIri)

        /**
          * The actual task run with an IRI lock.
          */
        def userProjectAdminMembershipAddRequestTask(userIri: IRI, projectIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty."))
            _ = if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!userProfileV1.permissionData.isProjectAdmin(projectIri) && !userProfileV1.permissionData.isSystemAdmin) {
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
            currentProjectAdminMemberships <- userProjectAdminMembershipsGetRequestV1(
                userIri = userIri,
                userProfileV1 = userProfileV1,
                apiRequestID = apiRequestID
            )

            currentProjectAdminMembershipIris: Seq[IRI] = currentProjectAdminMemberships.projects

            // check if user is already member and if not then append to list
            updatedProjectAdminMembershipIris = if (!currentProjectAdminMembershipIris.contains(projectIri)) {
                currentProjectAdminMembershipIris :+ projectIri
            } else {
                throw BadRequestException(s"User $userIri is already a project admin for project $projectIri.")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadV1(projectsAdmin = Some(updatedProjectAdminMembershipIris))

            result <- updateUserV1(userIri, userUpdatePayload, userProfileV1, apiRequestID)

        } yield result


        for {
            // run the task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => userProjectAdminMembershipAddRequestTask(userIri, projectIri, userProfileV1, apiRequestID)
            )
        } yield taskResult

    }

    /**
      * Removes a user from project admin group of a project.
      *
      * @param userIri       the user's IRI.
      * @param projectIri    the project's IRI.
      * @param userProfileV1 the user profile of the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return
      */
    def userProjectAdminMembershipRemoveRequestV1(userIri: IRI, projectIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        // log.debug(s"userProjectAdminMembershipRemoveRequestV1: userIri: {}, projectIri: {}", userIri, projectIri)

        /**
          * The actual task run with an IRI lock.
          */
        def userProjectAdminMembershipRemoveRequestTask(userIri: IRI, projectIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = for {

            // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty."))
            _ = if (projectIri.isEmpty) throw BadRequestException("Project IRI cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!userProfileV1.permissionData.isProjectAdmin(projectIri) && !userProfileV1.permissionData.isSystemAdmin) {
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
            currentProjectAdminMemberships <- userProjectAdminMembershipsGetRequestV1(
                userIri = userIri,
                userProfileV1 = userProfileV1,
                apiRequestID = apiRequestID
            )

            currentProjectAdminMembershipIris: Seq[IRI] = currentProjectAdminMemberships.projects

            // check if user is not already a member and if he is then remove the project from to list
            updatedProjectAdminMembershipIris = if (currentProjectAdminMembershipIris.contains(projectIri)) {
                currentProjectAdminMembershipIris diff Seq(projectIri)
            } else {
                throw BadRequestException(s"User $userIri is not a project admin of project $projectIri.")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadV1(projectsAdmin = Some(updatedProjectAdminMembershipIris))

            result <- updateUserV1(userIri, userUpdatePayload, userProfileV1, apiRequestID)

        } yield result


        for {
            // run the task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => userProjectAdminMembershipRemoveRequestTask(userIri, projectIri, userProfileV1, apiRequestID)
            )
        } yield taskResult
    }

    def userGroupMembershipsGetRequestV1(userIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserGroupMembershipsGetResponseV1] = {

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

            /* the groups the user is member of */
            groupIris: Seq[IRI] = groupedUserData.get(OntologyConstants.KnoraBase.IsInGroup) match {
                case Some(projects) => projects
                case None => Seq.empty[IRI]
            }
            //_ = log.debug("userDataByIriGetV1 - maybeUserDataV1: {}", maybeUserDataV1)

        } yield UserGroupMembershipsGetResponseV1(groups = groupIris)

    }

    def userGroupMembershipAddRequestV1(userIri: IRI, groupIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        // log.debug(s"userGroupMembershipAddRequestV1: userIri: {}, groupIri: {}", userIri, groupIri)

        /**
          * The actual task run with an IRI lock.
          */
        def userGroupMembershipAddRequestTask(userIri: IRI, groupIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = for {

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
            groupInfo <- (responderManager ? GroupInfoByIRIGetRequestV1(groupIri, None)).mapTo[GroupInfoResponseV1]
            projectIri = groupInfo.group_info.project

            // check if the requesting user is allowed to perform updates
            _ = if (!userProfileV1.permissionData.isProjectAdmin(projectIri) && !userProfileV1.permissionData.isSystemAdmin) {
                // not a project or system admin
                // log.debug("project admin: {}, system admin: {}", userProfileV1.permissionData.isProjectAdmin(projectIri), userProfileV1.permissionData.isSystemAdmin)
                throw ForbiddenException("User's group membership can only be changed by a project or system administrator")
            }

            // get users current group membership list
            currentGroupMemberships <- userGroupMembershipsGetRequestV1(
                userIri = userIri,
                userProfileV1 = userProfileV1,
                apiRequestID = apiRequestID
            )

            currentGroupMembershipIris: Seq[IRI] = currentGroupMemberships.groups

            // check if user is already member and if not then append to list
            updatedGroupMembershipIris = if (!currentGroupMembershipIris.contains(groupIri)) {
                currentGroupMembershipIris :+ groupIri
            } else {
                throw BadRequestException(s"User $userIri is already member of group $groupIri.")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadV1(groups = Some(updatedGroupMembershipIris))

            result <- updateUserV1(userIri, userUpdatePayload, userProfileV1, apiRequestID)

        } yield result


        for {
            // run the task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => userGroupMembershipAddRequestTask(userIri, groupIri, userProfileV1, apiRequestID)
            )
        } yield taskResult

    }

    def userGroupMembershipRemoveRequestV1(userIri: IRI, groupIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        // log.debug(s"userGroupMembershipRemoveRequestV1: userIri: {}, groupIri: {}", userIri, groupIri)

        /**
          * The actual task run with an IRI lock.
          */
        def userGroupMembershipRemoveRequestTask(userIri: IRI, groupIri: IRI, userProfileV1: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = for {

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
            groupInfo <- (responderManager ? GroupInfoByIRIGetRequestV1(groupIri, None)).mapTo[GroupInfoResponseV1]
            projectIri = groupInfo.group_info.project

            // check if the requesting user is allowed to perform updates
            _ = if (!userProfileV1.permissionData.isProjectAdmin(projectIri) && !userProfileV1.permissionData.isSystemAdmin) {
                // not a project or system admin
                //log.debug("project admin: {}, system admin: {}", userProfileV1.permissionData.isProjectAdmin(projectIri), userProfileV1.permissionData.isSystemAdmin)
                throw ForbiddenException("User's group membership can only be changed by a project or system administrator")
            }

            // get users current project membership list
            currentGroupMemberships <- userGroupMembershipsGetRequestV1(
                userIri = userIri,
                userProfileV1 = userProfileV1,
                apiRequestID = apiRequestID
            )

            currentGroupMembershipIris: Seq[IRI] = currentGroupMemberships.groups

            // check if user is not already a member and if he is then remove the project from to list
            updatedGroupMembershipIris = if (currentGroupMembershipIris.contains(groupIri)) {
                currentGroupMembershipIris diff Seq(groupIri)
            } else {
                throw BadRequestException(s"User $userIri is not member of group $groupIri.")
            }

            // create the update request
            userUpdatePayload = UserUpdatePayloadV1(groups = Some(updatedGroupMembershipIris))

            result <- updateUserV1(userIri, userUpdatePayload, userProfileV1, apiRequestID)

        } yield result


        for {
            // run the task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => userGroupMembershipRemoveRequestTask(userIri, groupIri, userProfileV1, apiRequestID)
            )
        } yield taskResult
    }


    /**
      * Updates an existing user. Should not be directly used from the receive method.
      *
      * @param userIri           the IRI of the existing user that we want to update.
      * @param userUpdatePayload the updated information.
      * @param userProfile       the user profile of the requesting user.
      * @param apiRequestID      the unique api request ID.
      * @return a future containing a [[UserOperationResponseV1]].
      * @throws BadRequestException         if necessary parameters are not supplied.
      * @throws UpdateNotPerformedException if the update was not performed.
      */
    private def updateUserV1(userIri: IRI, userUpdatePayload: UserUpdatePayloadV1, userProfile: UserADM, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        // log.debug("updateUserV1 - userUpdatePayload: {}", userUpdatePayload)

        /* Remember: some checks on UserUpdatePayloadV1 are implemented in the case class */

        if (userUpdatePayload.email.nonEmpty) {
            // changing email address, so we need to invalidate the cached profile under this email
            invalidateCachedUserProfileV1(email = userUpdatePayload.email)
        }

        for {
            /* Update the user */
            updateUserSparqlString <- Future(queries.sparql.v1.txt.updateUser(
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
            _ = invalidateCachedUserProfileV1(Some(userIri), userUpdatePayload.email)

            /* Verify that the user was updated. */
            maybeUpdatedUserProfile <- userProfileByIRIGetV1(userIri, UserProfileTypeV1.FULL)
            updatedUserProfile = maybeUpdatedUserProfile.getOrElse(throw UpdateNotPerformedException("User was not updated. Please report this as a possible bug."))
            updatedUserData = updatedUserProfile.userData

            //_ = log.debug(s"apiUpdateRequest: $apiUpdateRequest /  updatedUserdata: $updatedUserData")

            _ = if (userUpdatePayload.email.isDefined) {
                if (updatedUserData.email != userUpdatePayload.email) throw UpdateNotPerformedException("User's 'email' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.givenName.isDefined) {
                if (updatedUserData.firstname != userUpdatePayload.givenName) throw UpdateNotPerformedException("User's 'givenName' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.familyName.isDefined) {
                if (updatedUserData.lastname != userUpdatePayload.familyName) throw UpdateNotPerformedException("User's 'familyName' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.password.isDefined) {
                if (updatedUserData.password != userUpdatePayload.password) throw UpdateNotPerformedException("User's 'password' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.status.isDefined) {
                if (updatedUserData.status != userUpdatePayload.status) throw UpdateNotPerformedException("User's 'status' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.lang.isDefined) {
                if (updatedUserData.lang != userUpdatePayload.lang.get) throw UpdateNotPerformedException("User's 'lang' was not updated. Please report this as a possible bug.")
            }

            _ = if (userUpdatePayload.systemAdmin.isDefined) {
                if (updatedUserProfile.permissionData.isSystemAdmin != userUpdatePayload.systemAdmin.get) throw UpdateNotPerformedException("User's 'isInSystemAdminGroup' status was not updated. Please report this as a possible bug.")
            }

            // create the user operation response
            userOperationResponseV1 = UserOperationResponseV1(updatedUserProfile.ofType(UserProfileTypeV1.RESTRICTED))

        } yield userOperationResponseV1
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
      * Helper method used to create a [[UserADM]] from the [[SparqlSelectResponse]] containing user data.
      *
      * @param userDataQueryResponse a [[SparqlSelectResponse]] containing user data.
      * @return a [[UserADM]] containing the user's data.
      */
    private def userDataQueryResponse2UserProfile(userDataQueryResponse: SparqlSelectResponse): Future[Option[UserADM]] = {

        // log.debug("userDataQueryResponse2UserProfile - userDataQueryResponse: {}", MessageUtil.toSource(userDataQueryResponse))

        if (userDataQueryResponse.results.bindings.nonEmpty) {
            val returnedUserIri = userDataQueryResponse.getFirstRow.rowMap("s")

            val groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            // log.debug("userDataQueryResponse2UserProfile - groupedUserData: {}", MessageUtil.toSource(groupedUserData))

            val userDataV1 = UserDataV1(
                lang = groupedUserData.get(OntologyConstants.KnoraBase.PreferredLanguage) match {
                    case Some(langList) => langList.head
                    case None => settings.fallbackLanguage
                },
                user_id = Some(returnedUserIri),
                email = groupedUserData.get(OntologyConstants.KnoraBase.Email).map(_.head),
                firstname = groupedUserData.get(OntologyConstants.KnoraBase.GivenName).map(_.head),
                lastname = groupedUserData.get(OntologyConstants.KnoraBase.FamilyName).map(_.head),
                password = groupedUserData.get(OntologyConstants.KnoraBase.Password).map(_.head),
                status = groupedUserData.get(OntologyConstants.KnoraBase.Status).map(_.head.toBoolean)
            )
            // log.debug("userDataQueryResponse2UserProfile - userDataV1: {}", MessageUtil.toSource(userDataV1)")


            /* the projects the user is member of */
            val projectIris: Seq[IRI] = groupedUserData.get(OntologyConstants.KnoraBase.IsInProject) match {
                case Some(projects) => projects
                case None => Seq.empty[IRI]
            }

            /* the groups the user is member of (only explicit groups) */
            val groupIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInGroup) match {
                case Some(groups) => groups
                case None => Vector.empty[IRI]
            }

            // log.debug(s"userDataQueryResponse2UserProfile - groupIris: ${MessageUtil.toSource(groupIris)}")

            /* the projects for which the user is implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group */
            val isInProjectAdminGroups = groupedUserData.getOrElse(OntologyConstants.KnoraBase.IsInProjectAdminGroup, Vector.empty[IRI])

            /* is the user implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#SystemAdmin' group */
            val isInSystemAdminGroup = groupedUserData.get(OntologyConstants.KnoraBase.IsInSystemAdminGroup).exists(p => p.head.toBoolean)

            for {
                /* get the user's permission profile from the permissions responder */
                permissionData <- (responderManager ? PermissionDataGetADM(projectIris = projectIris, groupIris = groupIris, isInProjectAdminGroups = isInProjectAdminGroups, isInSystemAdminGroup = isInSystemAdminGroup)).mapTo[PermissionsDataADM]

                maybeProjectInfoFutures: Seq[Future[Option[ProjectInfoV1]]] = projectIris.map {
                    projectIri => (responderManager ? ProjectInfoByIRIGetV1(iri = projectIri, userProfileV1 = None)).mapTo[Option[ProjectInfoV1]]
                }

                maybeProjectInfos: Seq[Option[ProjectInfoV1]] <- Future.sequence(maybeProjectInfoFutures)
                projectInfos = maybeProjectInfos.flatten
                projectInfoMap: Map[IRI, ProjectInfoV1] = projectInfos.map(projectInfo => projectInfo.id -> projectInfo).toMap

                /* construct the user profile from the different parts */
                up = UserADM(
                    userData = userDataV1,
                    groups = groupIris,
                    projects_info = projectInfoMap,
                    sessionId = None,
                    permissionData = permissionData
                )
                // _ = log.debug(s"Retrieved UserProfileV1: ${up.toString}")

                result: Option[UserADM] = Some(up)
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
            askString <- Future(queries.sparql.v1.txt.checkProjectExistsByIri(projectIri = projectIri).toString)
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
            askString <- Future(queries.sparql.v1.txt.checkGroupExistsByIri(groupIri = groupIri).toString)
            // _ = log.debug("groupExists - query: {}", askString)

            checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkUserExistsResponse.result

        } yield result
    }

    /**
      * Writes the user profile to cache.
      *
      * @param userProfile a [[UserADM]].
      * @return true if writing was successful.
      * @throws ApplicationCacheException when there is a problem with writing the user's profile to cache.
      */
    private def writeUserProfileV1ToCache(userProfile: UserADM): Boolean = {

        val iri = if (userProfile.userData.user_id.nonEmpty) {
            userProfile.userData.user_id.get
        } else {
            throw ApplicationCacheException("A user profile without an IRI is invalid. Not writing to cache.")
        }

        val email = if (userProfile.userData.email.nonEmpty) {
            userProfile.userData.email.get
        } else {
            throw ApplicationCacheException("A user profile without an email is invalid. Not writing to cache.")
        }

        CacheUtil.put(USER_PROFILE_CACHE_NAME, iri, userProfile)

        if (CacheUtil.get(USER_PROFILE_CACHE_NAME, iri).isEmpty) {
            throw ApplicationCacheException("Writing the user's profile to cache was not successful.")
        }

        CacheUtil.put(USER_PROFILE_CACHE_NAME, email, userProfile)
        if (CacheUtil.get(USER_PROFILE_CACHE_NAME, email).isEmpty) {
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
    private def invalidateCachedUserProfileV1(userIri: Option[IRI] = None, email: Option[String] = None): Unit = {

        if (userIri.nonEmpty) {
            CacheUtil.remove(USER_PROFILE_CACHE_NAME, userIri.get)
        }

        if (email.nonEmpty) {
            CacheUtil.remove(USER_PROFILE_CACHE_NAME, email.get)
        }
    }

}
