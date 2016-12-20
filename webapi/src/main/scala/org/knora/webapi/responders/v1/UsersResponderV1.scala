/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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
import akka.pattern._
import org.knora.webapi
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.permissionmessages._
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, SparqlUpdateRequest, SparqlUpdateResponse}
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{CacheUtil, KnoraIdUtil, SparqlUtil}
import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.Future

/**
  * Provides information about Knora users to other responders.
  */
class UsersResponderV1 extends ResponderV1 {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.usermessages.UsersResponderRequestV1]], and returns a message of type [[UserProfileV1]]
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case UserProfileByIRIGetRequestV1(userIri, profileType) => future2Message(sender(), getUserProfileByIRIV1(userIri, profileType), log)
        case UserProfileByUsernameGetRequestV1(username, profileType) => future2Message(sender(), getUserProfileByUsernameV1(username, profileType), log)
        case UserCreateRequestV1(newUserData, userProfile, apiRequestID) => future2Message(sender(), createNewUserV1(newUserData, userProfile, apiRequestID), log)
        case UserUpdateRequestV1(userIri, propertyIri, newValue, userProfile, apiRequestID) => future2Message(sender(), updateUserV1(userIri, propertyIri, newValue, userProfile, apiRequestID), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Gets information about a Knora user, and returns it in a [[UserProfileV1]].
      *
      * @param userIri the IRI of the user.
      * @return a [[UserProfileV1]] describing the user.
      */
    private def getUserProfileByIRIV1(userIRI: IRI, profileType: UserProfileType.Value): Future[UserProfileV1] = {
        // TODO: add caching of user profiles that was removed from [[Authenticator]]
        //log.debug(s"getUserProfileByIRIV1: userIri = $userIRI', clean = '$clean'")
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getUserByIri(
                triplestore = settings.triplestoreType,
                userIri = userIRI
            ).toString())

            //_ = log.debug(s"getUserProfileByIRIV1 - sparqlQueryString: $sparqlQueryString")

            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            _ = if (userDataQueryResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"User '$userIRI' not found")
            }

            userProfileV1 <- userDataQueryResponse2UserProfile(userDataQueryResponse, profileType)

        } yield userProfileV1 // UserProfileV1(userDataV1, groupIris, projectIris)
    }

    /**
      * Gets information about a Knora user, and returns it in a [[UserProfileV1]].
      *
      * @param username the username of the user.
      * @return a [[UserProfileV1]] describing the user.
      */
    private def getUserProfileByUsernameV1(username: String, profileType: UserProfileType.Value): Future[UserProfileV1] = {
        // TODO: add caching of user profiles that was removed from [[Authenticator]]
        log.debug(s"getUserProfileByUsernameV1: username = '$username', clean = '$profileType'")
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getUserByUsername(
                triplestore = settings.triplestoreType,
                username = SparqlUtil.any2SparqlLiteral(username)
            ).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            //_ = log.debug(MessageUtil.toSource(userDataQueryResponse))

            _ = if (userDataQueryResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"User '$username' not found")
            }

            userProfileV1 <- userDataQueryResponse2UserProfile(userDataQueryResponse, profileType)

        } yield userProfileV1 // UserProfileV1(userDataV1, groupIris, projectIris)
    }

    /**
      * Creates a new user. Self-registration is allowed, so even the default user, i.e. with no credentials supplied,
      * is allowed to create the new user.
      *
      * Referenced Websites:
      *                     - https://crackstation.net/hashing-security.htm
      *                     - http://blog.ircmaxell.com/2012/12/seven-ways-to-screw-up-bcrypt.html
      *
      * @param newUserData a [[NewUserDataV1]] object containing information about the new user to be created.
      * @param userProfile a [[UserProfileV1]] object containing information about the requesting user.
      * @return a future containing the [[UserOperationResponseV1]].
      */
    private def createNewUserV1(newUserData: NewUserDataV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = {
        for {
            a <- Future("")

            // check if username or password are not empty
            _ = if (newUserData.username.isEmpty) throw BadRequestException("Username cannot be empty")
            _ = if (newUserData.password.isEmpty) throw BadRequestException("Password cannot be empty")

            // check if the supplied username for the new user is unique, i.e. not already registered
            sparqlQueryString = queries.sparql.v1.txt.getUserByUsername(
                triplestore = settings.triplestoreType,
                username = SparqlUtil.any2SparqlLiteral(newUserData.username)
            ).toString()
            //_ = log.debug(s"createNewUser - check duplicate name: $sparqlQueryString")
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

            //_ = log.debug(MessageUtil.toSource(userDataQueryResponse))

            _ = if (userDataQueryResponse.results.bindings.nonEmpty) {
                throw DuplicateValueException(s"User with the username: '${newUserData.username}' already exists")
            }

            userIri = knoraIdUtil.makeRandomPersonIri

            hashedPassword = BCrypt.hashpw(newUserData.password, BCrypt.gensalt())

            // Create the new user.
            createNewUserSparqlString = queries.sparql.v1.txt.createNewUser(
                adminNamedGraphIri = "http://www.knora.org/data/admin",
                triplestore = settings.triplestoreType,
                userIri = userIri,
                userClassIri = OntologyConstants.KnoraBase.User,
                username = SparqlUtil.any2SparqlLiteral(newUserData.username),
                password = SparqlUtil.any2SparqlLiteral(hashedPassword),
                givenName = SparqlUtil.any2SparqlLiteral(newUserData.givenName),
                familyName = SparqlUtil.any2SparqlLiteral(newUserData.familyName),
                email = SparqlUtil.any2SparqlLiteral(newUserData.email),
                preferredLanguage = SparqlUtil.any2SparqlLiteral(newUserData.lang)
            ).toString
            //_ = log.debug(s"createNewUser: $createNewUserSparqlString")
            createResourceResponse <- (storeManager ? SparqlUpdateRequest(createNewUserSparqlString)).mapTo[SparqlUpdateResponse]


            // Verify that the user was created.
            sparqlQuery = queries.sparql.v1.txt.getUserByIri(
                triplestore = settings.triplestoreType,
                userIri = userIri
            ).toString()
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            _ = if (userDataQueryResponse.results.bindings.isEmpty) {
                throw UpdateNotPerformedException(s"User $userIri was not created. Please report this as a possible bug.")
            }

            // create the user profile
            newUserProfile <- userDataQueryResponse2UserProfile(userDataQueryResponse, UserProfileType.SAFE)

            // create the user operation response
            userOperationResponseV1 = UserOperationResponseV1(newUserProfile, userProfile.userData)

        } yield userOperationResponseV1

    }


    private def updateUserV1(userIri: webapi.IRI, propertyIri: webapi.IRI, newValue: Any, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = for {
        a <- Future("")

        // check if necessary information is present
        _ = if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

        // check if the requesting user is allowed to perform updates
        _ = if (!userProfile.userData.user_id.contains(userIri) && !userProfile.permissionData.isSystemAdmin) {
            // not the user and not a system admin
            throw ForbiddenException("User information can only be changed by the user itself or a system administrator")
        }

        /*
        Check needs to be moved into GroupsResponder.
        _ = if (newValue.equals(OntologyConstants.KnoraBase.SystemAdmin) && !userProfile.isSystemAdmin) {
            // the operation of promoting to system admin is only allowed by another system admin
            throw ForbiddenException("Giving an user system admin rights can only be performed by another system admin")
        }
        */

        // get current value.
        sparqlQueryString = queries.sparql.v1.txt.getUserByIri(
            triplestore = settings.triplestoreType,
            userIri = userIri
        ).toString()
        userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]

        // create the user profile including sensitive information
        currentUserProfile <- userDataQueryResponse2UserProfile(userDataQueryResponse, UserProfileType.FULL)

        // get the current value
        currentValue = propertyIri match {
            case OntologyConstants.KnoraBase.Username => currentUserProfile.userData.username.getOrElse("")
            case OntologyConstants.Foaf.GivenName => currentUserProfile.userData.firstname.getOrElse("")
            case OntologyConstants.Foaf.FamilyName => currentUserProfile.userData.lastname.getOrElse("")
            case OntologyConstants.KnoraBase.Email => currentUserProfile.userData.email.getOrElse("")
            case OntologyConstants.KnoraBase.Password => currentUserProfile.userData.password.getOrElse("")
            case OntologyConstants.KnoraBase.IsActiveUser => currentUserProfile.userData.isActiveUser.getOrElse(false)
            case OntologyConstants.KnoraBase.PreferredLanguage => currentUserProfile.userData.lang
            case x => throw BadRequestException(s"The property $propertyIri is not allowed")
        }

        // get the current value as a string for adding directly into SPARQL
        currentValueLiteral = SparqlUtil.any2SparqlLiteral(currentValue)


        // get the new value as a literal string for directly adding into SPARQL
        newValueLiteral = if (propertyIri.equals(OntologyConstants.KnoraBase.Password)) {
            // if password then first create hash
            val hashedPassword = BCrypt.hashpw(newValue.asInstanceOf[String], BCrypt.gensalt())
            SparqlUtil.any2SparqlLiteral(hashedPassword)
        } else {
            SparqlUtil.any2SparqlLiteral(newValue)
        }

        // Update the user
        updateUserSparqlString = queries.sparql.v1.txt.updateUser(
            adminNamedGraphIri = "http://www.knora.org/data/admin",
            triplestore = settings.triplestoreType,
            userIri = userIri,
            propertyIri = propertyIri,
            currentValue = currentValueLiteral,
            newValue = newValueLiteral
        ).toString
        //_ = log.debug(s"updateUserV1 - query: $updateUserSparqlString")
        createResourceResponse <- (storeManager ? SparqlUpdateRequest(updateUserSparqlString)).mapTo[SparqlUpdateResponse]

        // Verify that the user was updated.
        sparqlQueryString = queries.sparql.v1.txt.getUserByIri(
            triplestore = settings.triplestoreType,
            userIri = userIri
        ).toString()
        userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]


        // create the user profile including sensitive information
        updatedUserProfile <- userDataQueryResponse2UserProfile(userDataQueryResponse, UserProfileType.FULL)

        // check if what we wanted to update actually got updated
        //_ = log.debug(s"currentValue: ${currentValue.toString}, newValue: ${newValue.toString}")
        _ = propertyIri match {
            case OntologyConstants.KnoraBase.Username => {
                if (!updatedUserProfile.userData.username.contains(newValue.asInstanceOf[String])) {
                    throw UpdateNotPerformedException("User's 'username' was not updated. Please report this as a possible bug.")
                }
            }
            case OntologyConstants.Foaf.GivenName => {
                if (!updatedUserProfile.userData.firstname.contains(newValue.asInstanceOf[String])) {
                    throw UpdateNotPerformedException("User's 'given name' was not updated. Please report this as a possible bug.")
                }
            }
            case OntologyConstants.Foaf.FamilyName => {
                if (!updatedUserProfile.userData.lastname.contains(newValue.asInstanceOf[String])) {
                    throw UpdateNotPerformedException("User's 'family name' was not updated. Please report this as a possible bug.")
                }
            }
            case OntologyConstants.KnoraBase.Email => {
                if (!updatedUserProfile.userData.email.contains(newValue.asInstanceOf[String])) {
                    throw UpdateNotPerformedException("User's 'email' was not updated. Please report this as a possible bug.")
                }
            }
            case OntologyConstants.KnoraBase.Password => {
                if (!updatedUserProfile.userData.password.contains(newValue.asInstanceOf[String])) {
                    throw UpdateNotPerformedException("User's 'password' was not updated. Please report this as a possible bug.")
                }
            }
            case OntologyConstants.KnoraBase.IsActiveUser => {
                if (!updatedUserProfile.userData.isActiveUser.contains(newValue.asInstanceOf[Boolean])) {
                    throw UpdateNotPerformedException("User's 'active status' was not updated. Please report this as a possible bug.")
                }
            }
            case OntologyConstants.KnoraBase.PreferredLanguage => {
                if (!updatedUserProfile.userData.lang.equals(newValue.asInstanceOf[String])) {
                    throw UpdateNotPerformedException("User's 'preferred language' was not updated. Please report this as a possible bug.")
                }
            }
        }

        // create the user operation response
        userOperationResponseV1 = if (userIri == userProfile.userData.user_id.get) {
            // the user is updating itself

            // update cache if session id is available
            userProfile.sessionId match {
                case Some(sessionId) => CacheUtil.put[UserProfileV1](Authenticator.cacheName, sessionId, updatedUserProfile.setSessionId(sessionId))
                case None => // user has not session id, so no cache to update
            }

            UserOperationResponseV1(updatedUserProfile.ofType(UserProfileType.SAFE), updatedUserProfile.ofType(UserProfileType.SAFE).userData)
        } else {
            UserOperationResponseV1(updatedUserProfile.ofType(UserProfileType.SAFE), userProfile.userData)
        }
    } yield userOperationResponseV1


    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Helper method used to create a [[UserProfileV1]] from the [[SparqlSelectResponse]] containing user data.
      *
      * @param userDataQueryResponse a [[SparqlSelectResponse]] containing user data.
      * @param userProfileType a flag denoting if sensitive information should be stripped from the returned [[UserProfileV1]]
      * @return a [[UserProfileV1]] containing the user's data.
      */
    private def userDataQueryResponse2UserProfile(userDataQueryResponse: SparqlSelectResponse, userProfileType: UserProfileType.Value): Future[UserProfileV1] = {

        //log.debug("userDataQueryResponse2UserProfile - " + MessageUtil.toSource(userDataQueryResponse))

        for {
            a <- Future("")

            returnedUserIri = userDataQueryResponse.getFirstRow.rowMap("s")

            groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            //_ = log.debug(s"userDataQueryResponse2UserProfile - groupedUserData: ${MessageUtil.toSource(groupedUserData)}")

            userDataV1 = UserDataV1(
                lang = groupedUserData.get(OntologyConstants.KnoraBase.PreferredLanguage) match {
                    case Some(langList) => langList.head
                    case None => settings.fallbackLanguage
                },
                user_id = Some(returnedUserIri),
                username = groupedUserData.get(OntologyConstants.KnoraBase.Username).map(_.head),
                firstname = groupedUserData.get(OntologyConstants.KnoraBase.GivenName).map(_.head),
                lastname = groupedUserData.get(OntologyConstants.KnoraBase.FamilyName).map(_.head),
                email = groupedUserData.get(OntologyConstants.KnoraBase.Email).map(_.head),
                password = groupedUserData.get(OntologyConstants.KnoraBase.Password).map(_.head),
                isActiveUser = groupedUserData.get(OntologyConstants.KnoraBase.IsActiveUser).map(_.head.toBoolean),
                active_project = groupedUserData.get(OntologyConstants.KnoraBase.UsersActiveProject).map(_.head)
            )
            //_ = log.debug(s"userDataQueryResponse2UserProfile - userDataV1: ${MessageUtil.toSource(userDataV1)}")


            /* the projects the user is member of */
            projectIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInProject) match {
                case Some(projects) => projects
                case None => Vector.empty[IRI]
            }
            //_ = log.debug(s"userDataQueryResponse2UserProfile - projectIris: ${MessageUtil.toSource(projectIris)}")

            /* the groups the user is member of (only explicit groups) */
            groupIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInGroup) match {
                case Some(groups) => groups
                case None => Vector.empty[IRI]
            }
            //_ = log.debug(s"userDataQueryResponse2UserProfile - groupIris: ${MessageUtil.toSource(groupIris)}")


            /* the projects for which the user is implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group */
            isInProjectAdminGroups = groupedUserData.getOrElse(OntologyConstants.KnoraBase.IsInProjectAdminGroup, Vector.empty[IRI])

            /* is the user implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#SystemAdmin' group */
            isInSystemAdminGroup = groupedUserData.get(OntologyConstants.KnoraBase.IsInSystemAdminGroup).exists(p => p.head.toBoolean)

            /* get the user's permission profile from the permissions responder */
            permissionDataFuture = (responderManager ? PermissionDataGetV1(projectIris = projectIris, groupIris = groupIris, isInProjectAdminGroups = isInProjectAdminGroups, isInSystemAdminGroup = isInSystemAdminGroup)).mapTo[PermissionDataV1]
            permissionData <- permissionDataFuture

            /* construct the user profile from the different parts */
            up = UserProfileV1(
                userData = userDataV1,
                groups = groupIris,
                projects = projectIris,
                sessionId = None,
                permissionData = permissionData
            )
            //_ = log.debug(s"Retrieved UserProfileV1: ${up.toString}")

            result = up.ofType(userProfileType)
        } yield result

    }

}
