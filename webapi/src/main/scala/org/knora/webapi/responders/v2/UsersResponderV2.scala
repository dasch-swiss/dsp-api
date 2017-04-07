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

package org.knora.webapi.responders.v2

import java.util.UUID

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.permissionmessages._
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoByIRIGetV1, ProjectInfoV1}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileTypeV1.UserProfileTypeV1
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{CacheUtil, KnoraIdUtil}
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder

import scala.concurrent.Future

/**
  * Provides information about Knora users to other responders.
  */
class UsersResponderV2 extends ResponderV2 {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    // The IRI used to lock user creation and update
    val USERS_GLOBAL_LOCK_IRI = "http://data.knora.org/users"

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1]], and returns a message of type [[UserProfileV1]]
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case UsersGetV1() => future2Message(sender(), usersGetV1, log)
        case UsersGetRequestV1(userProfileV1) => future2Message(sender(), usersGetRequestV1(userProfileV1), log)
        case UserProfileByIRIGetV1(userIri, profileType) => future2Message(sender(), userProfileByIRIGetV1(userIri, profileType), log)
        case UserProfileByIRIGetRequestV1(userIri, profileType, userProfile) => future2Message(sender(), userProfileByIRIGetRequestV1(userIri, profileType, userProfile), log)
        case UserProfileByEmailGetV1(email, profileType) => future2Message(sender(), userProfileByEmailGetV1(email, profileType), log)
        case UserProfileByEmailGetRequestV1(email, profileType, userProfile) => future2Message(sender(), userProfileByEmailGetRequestV1(email, profileType, userProfile), log)
        case UserCreateRequestV1(createRequest, userProfile, apiRequestID) => future2Message(sender(), createNewUserV1(createRequest, userProfile, apiRequestID), log)
        case UserUpdateRequestV1(userIri, changeUserData, userProfile, apiRequestID) => future2Message(sender(), updateBasicUserDataV1(userIri, changeUserData, userProfile, apiRequestID), log)
        case UserChangePasswordRequestV1(userIri, changePasswordRequest, userProfile, apiRequestID) => future2Message(sender(), changePasswordV1(userIri, changePasswordRequest, userProfile, apiRequestID), log)
        case UserChangeStatusRequestV1(userIri, changeStatusRequest, userProfile, apiRequestID) => future2Message(sender(), changeUserStatusV1(userIri, changeStatusRequest, userProfile, apiRequestID), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }


    /**
      * Gets all the users and returns them as a sequence of [[UserDataV1]].
      *
      * @return all the users as a sequence of [[UserDataV1]].
      */
    private def usersGetV1: Future[Seq[UserDataV1]] = {
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
                        isActiveUser = propsMap.get(OntologyConstants.KnoraBase.Status).map(_.toBoolean)
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
      * Gets information about a Knora user, and returns it in a [[UserProfileV1]].
      *
      * @param userIRI     the IRI of the user.
      * @param profileType the type of the requested profile (restricted of full).
      * @return a [[UserProfileV1]] describing the user.
      */
    private def userProfileByIRIGetV1(userIRI: IRI, profileType: UserProfileTypeV1): Future[Option[UserProfileV1]] = {
        //log.debug(s"userProfileByIRIGetV1: userIri = $userIRI', clean = '$profileType'")
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getUserByIri(
                triplestore = settings.triplestoreType,
                userIri = userIRI
            ).toString())

            //_ = log.debug(s"userProfileByIRIGetV1 - sparqlQueryString: $sparqlQueryString")

            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            maybeUserProfileV1 <- userDataQueryResponse2UserProfile(userDataQueryResponse, profileType)

        } yield maybeUserProfileV1 // UserProfileV1(userDataV1, groupIris, projectIris)
    }

    /**
      * Gets information about a Knora user, and returns it as a [[UserProfileResponseV1]].
      *
      * @param userIRI     the IRI of the user.
      * @param profileType the type of the requested profile (restriced or full).
      * @param userProfile the requesting user's profile.
      * @return a [[UserProfileResponseV1]]
      */
    private def userProfileByIRIGetRequestV1(userIRI: IRI, profileType: UserProfileTypeV1, userProfile: UserProfileV1): Future[UserProfileResponseV1] = {
        for {
            maybeUserProfileToReturn <- userProfileByIRIGetV1(userIRI, profileType)
            result = maybeUserProfileToReturn match {
                case Some(up) => UserProfileResponseV1(up)
                case None => throw NotFoundException(s"User '$userIRI' not found")
            }
        } yield result
    }

    /**
      * Gets information about a Knora user, and returns it in a [[UserProfileV1]].
      *
      * @param email       the email of the user.
      * @param profileType the type of the requested profile (restricted or full).
      * @return a [[UserProfileV1]] describing the user.
      */
    private def userProfileByEmailGetV1(email: String, profileType: UserProfileTypeV1): Future[Option[UserProfileV1]] = {
        //log.debug(s"userProfileByEmailGetV1: username = '$email', type = '$profileType'")
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getUserByEmail(
                triplestore = settings.triplestoreType,
                email = email
            ).toString())
            //_ = log.debug(s"userProfileByEmailGetV1 - sparqlQueryString: $sparqlQueryString")

            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            //_ = log.debug(MessageUtil.toSource(userDataQueryResponse))
            maybeUserProfileV1 <- userDataQueryResponse2UserProfile(userDataQueryResponse, profileType)

        } yield maybeUserProfileV1 // UserProfileV1(userDataV1, groupIris, projectIris)
    }

    /**
      * Gets information about a Knora user, and returns it as a [[UserProfileResponseV1]].
      *
      * @param email       the email of the user.
      * @param profileType the type of the requested profile (restricted or full).
      * @param userProfile the requesting user's profile.
      * @return a [[UserProfileResponseV1]]
      */
    private def userProfileByEmailGetRequestV1(email: String, profileType: UserProfileTypeV1, userProfile: UserProfileV1): Future[UserProfileResponseV1] = {
        for {
            maybeUserProfileToReturn <- userProfileByEmailGetV1(email, profileType)
            result = maybeUserProfileToReturn match {
                case Some(up: UserProfileV1) => UserProfileResponseV1(up)
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
      * @param userProfile   a [[UserProfileV1]] object containing information about the requesting user.
      * @return a future containing the [[UserOperationResponseV1]].
      */
    private def createNewUserV1(createRequest: CreateUserApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        def createNewUserTask(createRequest: CreateUserApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID) = for {
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
            hashedPassword =  encoder.encode(createRequest.password)

            // Create the new user.
            createNewUserSparqlString = queries.sparql.v1.txt.createNewUser(
                adminNamedGraphIri = "http://www.knora.org/data/admin",
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
            maybeNewUserProfile <- userDataQueryResponse2UserProfile(userDataQueryResponse, UserProfileTypeV1.RESTRICTED)

            newUserProfile = maybeNewUserProfile.getOrElse(throw UpdateNotPerformedException(s"User $userIri was not created. Please report this as a possible bug."))

            // create the user operation response
            userOperationResponseV1 = UserOperationResponseV1(newUserProfile)

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
      * @param updateUserRequest the updated information.
      * @param userProfile       the user profile of the requesting user.
      * @param apiRequestID      the unique api request ID.
      * @return a [[UserOperationResponseV1]]
      */
    private def updateBasicUserDataV1(userIri: IRI, updateUserRequest: UpdateUserApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = for {

    // check if the requesting user is allowed to perform updates
        _ <- Future(
            if (!userProfile.userData.user_id.contains(userIri) && !userProfile.permissionData.isSystemAdmin) {
                // not the user and not a system admin
                throw ForbiddenException("User information can only be changed by the user itself or a system administrator")
            }
        )

        // check if necessary information is present
        _ = if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        // check that we don't want to change the password / status / system admin group membership
        _ = if (updateUserRequest.password.isDefined) throw BadRequestException("The password cannot be changed by this method.")
        _ = if (updateUserRequest.status.isDefined) throw BadRequestException("The status cannot be changed by this method.")
        _ = if (updateUserRequest.systemAdmin.isDefined) throw BadRequestException("The system admin group membership cannot be changed by this method.")

        // run the user update with an global IRI lock
        taskResult <- IriLocker.runWithIriLock(
            apiRequestID,
            USERS_GLOBAL_LOCK_IRI,
            () => updateUserDataV1(userIri, updateUserRequest, userProfile, apiRequestID)
        )
    } yield taskResult


    /**
      * Change the users password. The old password needs to be supplied for security purposes.
      *
      * @param userIri               the IRI of the existing user that we want to update.
      * @param changePasswordRequest the old and new password.
      * @param userProfile           the user profile of the requesting user.
      * @param apiRequestID          the unique api request ID.
      * @return
      */
    private def changePasswordV1(userIri: IRI, changePasswordRequest: ChangeUserPasswordApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        /**
          * The actual change password task run with an IRI lock.
          */
        def changePaswordTask(userIri: IRI, changePasswordRequest: ChangeUserPasswordApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = for {

        // check if old password matches current user password
            maybeUserProfile <- userProfileByIRIGetV1(userIri, UserProfileTypeV1.FULL)
            userProfile = maybeUserProfile.getOrElse(throw NotFoundException(s"User '$userIri' not found"))
            _ = if (!userProfile.passwordMatch(changePasswordRequest.oldPassword)) throw BadRequestException("The supplied old password does not match the current users password.")

            // create the update request
            updateUserRequest = UpdateUserApiRequestV1(password = Some(changePasswordRequest.newPassword))

            // update the users password
            result <- updateUserDataV1(userIri, updateUserRequest, userProfile, apiRequestID)

        } yield result

        for {
        // check if necessary information is present
            _ <- Future(if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty"))

            // check if the requesting user is allowed to perform updates
            _ = if (!userProfile.userData.user_id.contains(userIri) && !userProfile.permissionData.isSystemAdmin) {
                // not the user and not a system admin
                throw ForbiddenException("User information can only be changed by the user itself or a system administrator")
            }

            // run the change password task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => changePaswordTask(userIri, changePasswordRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }

    /**
      * Change the user's status (active / inactive).
      *
      * @param userIri             the IRI of the existing user that we want to update.
      * @param changeStatusRequest the new status.
      * @param userProfile         the user profile of the requesting user.
      * @param apiRequestID        the unique api request ID.
      * @return
      */
    private def changeUserStatusV1(userIri: IRI, changeStatusRequest: ChangeUserStatusApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        // check if necessary information is present
        if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        // check if the requesting user is allowed to perform updates
        if (!userProfile.userData.user_id.contains(userIri) && !userProfile.permissionData.isSystemAdmin) {
            // not the user and not a system admin
            throw ForbiddenException("User information can only be changed by the user itself or a system administrator")
        }

        // create the update user request
        val updateUserRequest = UpdateUserApiRequestV1(status = Some(changeStatusRequest.newStatus))

        for {
        // run the change status task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                userIri,
                () => updateUserDataV1(userIri, updateUserRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }

    private def changeUserSystemAdminMembershipStatusV1(user: IRI, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = ???

    /**
      * Updates an existing user. Should not be directly used from the receive method.
      *
      * @param userIri          the IRI of the existing user that we want to update.
      * @param apiUpdateRequest the updated information.
      * @param userProfile      the user profile of the requesting user.
      * @param apiRequestID     the unique api request ID.
      * @return a [[UserOperationResponseV1]]
      */
    private def updateUserDataV1(userIri: IRI, apiUpdateRequest: UpdateUserApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = {

        for {
        /* Update the user */
            updateUserSparqlString <- Future(queries.sparql.v1.txt.updateUser(
                adminNamedGraphIri = "http://www.knora.org/data/admin",
                triplestore = settings.triplestoreType,
                userIri = userIri,
                maybeEmail = apiUpdateRequest.email,
                maybeGivenName = apiUpdateRequest.givenName,
                maybeFamilyName = apiUpdateRequest.familyName,
                maybePassword = apiUpdateRequest.password,
                maybeStatus = apiUpdateRequest.status,
                maybeLang = apiUpdateRequest.lang,
                maybeSystemAdmin = apiUpdateRequest.systemAdmin
            ).toString)
            //_ = log.debug(s"updateUserDataV1 - query: $updateUserSparqlString")
            createResourceResponse <- (storeManager ? SparqlUpdateRequest(updateUserSparqlString)).mapTo[SparqlUpdateResponse]

            /* Verify that the user was updated. */
            maybeUpdatedUserProfile <- userProfileByIRIGetV1(userIri, UserProfileTypeV1.FULL)
            updatedUserProfile = maybeUpdatedUserProfile.getOrElse(throw UpdateNotPerformedException("User was not updated. Please report this as a possible bug."))
            updatedUserData = updatedUserProfile.userData

            //_ = log.debug(s"apiUpdateRequest: $apiUpdateRequest /  updatedUserdata: $updatedUserData")

            _ = if (apiUpdateRequest.email.isDefined) {
                if (updatedUserData.email != apiUpdateRequest.email) throw UpdateNotPerformedException("User's 'email' was not updated. Please report this as a possible bug.")
            }

            _ = if (apiUpdateRequest.givenName.isDefined) {
                if (updatedUserData.firstname != apiUpdateRequest.givenName) throw UpdateNotPerformedException("User's 'givenName' was not updated. Please report this as a possible bug.")
            }

            _ = if (apiUpdateRequest.familyName.isDefined) {
                if (updatedUserData.lastname != apiUpdateRequest.familyName) throw UpdateNotPerformedException("User's 'familyName' was not updated. Please report this as a possible bug.")
            }

            _ = if (apiUpdateRequest.password.isDefined) {
                if (updatedUserData.password != apiUpdateRequest.password) throw UpdateNotPerformedException("User's 'password' was not updated. Please report this as a possible bug.")
            }

            _ = if (apiUpdateRequest.status.isDefined) {
                if (updatedUserData.isActiveUser != apiUpdateRequest.status) throw UpdateNotPerformedException("User's 'status' was not updated. Please report this as a possible bug.")
            }

            _ = if (apiUpdateRequest.lang.isDefined) {
                if (updatedUserData.lang != apiUpdateRequest.lang.get) throw UpdateNotPerformedException("User's 'lang' was not updated. Please report this as a possible bug.")
            }

            _ = if (apiUpdateRequest.systemAdmin.isDefined) {
                if (updatedUserData.isActiveUser != apiUpdateRequest.systemAdmin) throw UpdateNotPerformedException("User's 'isInSystemAdminGroup' status was not updated. Please report this as a possible bug.")
            }

            // create the user operation response
            userOperationResponseV1 = if (userIri == userProfile.userData.user_id.get) {
                // the user is updating itself

                // update cache if session id is available
                userProfile.sessionId match {
                    case Some(sessionId) => CacheUtil.put[UserProfileV1](Authenticator.AUTHENTICATION_CACHE_NAME, sessionId, updatedUserProfile.setSessionId(sessionId))
                    case None => // user has not session id, so no cache to update
                }

                UserOperationResponseV1(updatedUserProfile.ofType(UserProfileTypeV1.RESTRICTED))
            } else {
                UserOperationResponseV1(updatedUserProfile.ofType(UserProfileTypeV1.RESTRICTED))
            }
        } yield userOperationResponseV1
    }


    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Helper method used to create a [[UserProfileV1]] from the [[SparqlSelectResponse]] containing user data.
      *
      * @param userDataQueryResponse a [[SparqlSelectResponse]] containing user data.
      * @param userProfileType       a flag denoting if sensitive information should be stripped from the returned [[UserProfileV1]]
      * @return a [[UserProfileV1]] containing the user's data.
      */
    private def userDataQueryResponse2UserProfile(userDataQueryResponse: SparqlSelectResponse, userProfileType: UserProfileTypeV1.Value): Future[Option[UserProfileV1]] = {

        //log.debug("userDataQueryResponse2UserProfile - " + MessageUtil.toSource(userDataQueryResponse))

        if (userDataQueryResponse.results.bindings.nonEmpty) {
            val returnedUserIri = userDataQueryResponse.getFirstRow.rowMap("s")

            val groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            //_ = log.debug(s"userDataQueryResponse2UserProfile - groupedUserData: ${MessageUtil.toSource(groupedUserData)}")

            /* the projects the user is member of */
            val projectIris: Seq[IRI] = groupedUserData.get(OntologyConstants.KnoraBase.IsInProject) match {
                case Some(projects) => projects
                case None => Seq.empty[IRI]
            }

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
            isActiveUser = groupedUserData.get(OntologyConstants.KnoraBase.Status).map(_.head.toBoolean)
        )
        //_ = log.debug(s"userDataQueryResponse2UserProfile - userDataV1: ${MessageUtil.toSource(userDataV1)}")

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
                permissionData <- if (userProfileType != UserProfileTypeV1.SHORT) {
                    (responderManager ? PermissionDataGetV1(projectIris = projectIris, groupIris = groupIris, isInProjectAdminGroups = isInProjectAdminGroups, isInSystemAdminGroup = isInSystemAdminGroup)).mapTo[PermissionDataV1]
                } else {
                    Future(PermissionDataV1(anonymousUser = false))
                }

            projectInfoFutures: Seq[Future[ProjectInfoV1]] = projectIris.map {
                projectIri => (responderManager ? ProjectInfoByIRIGetV1(iri = projectIri, userProfileV1 = None)).mapTo[ProjectInfoV1]
            }

            projectInfos: Seq[ProjectInfoV1] <- Future.sequence(projectInfoFutures)
            projectInfoMap: Map[IRI, ProjectInfoV1] = projectInfos.map(projectInfo => projectInfo.id -> projectInfo).toMap

            /* construct the user profile from the different parts */
            up = UserProfileV1(
                userData = userDataV1,
                groups = groupIris,
                projects_info = projectInfoMap,
                sessionId = None,
                permissionData = permissionData
            )
            //_ = log.debug(s"Retrieved UserProfileV1: ${up.toString}")

                result: Option[UserProfileV1] = Some(up.ofType(userProfileType))
            } yield result

        } else {
            Future(None)
        }
    }

}
