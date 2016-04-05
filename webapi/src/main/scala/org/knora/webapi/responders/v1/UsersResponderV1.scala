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
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, SparqlUpdateRequest, SparqlUpdateResponse}
import org.knora.webapi.messages.v1respondermessages.usermessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{CacheUtil, KnoraIriUtil, SparqlUtil}
import org.mindrot.jbcrypt.BCrypt

import scala.concurrent.Future


/**
  * Provides information about Knora users to other responders.
  */
class UsersResponderV1 extends ResponderV1 {

    // Creates IRIs for new Knora user objects.
    val knoraIriUtil = new KnoraIriUtil

    /**
      * Receives a message extending [[UsersResponderRequestV1]], and returns a message of type [[UserProfileV1]]
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case UserProfileByIRIGetRequestV1(userIri, clean) => future2Message(sender(), getUserProfileByIRIV1(userIri, clean), log)
        case UserProfileByUsernameGetRequestV1(username, clean) => future2Message(sender(), getUserProfileByUsernameV1(username, clean), log)
        case UserCreateRequestV1(newUserData, userProfile, apiRequestID) => future2Message(sender(), createNewUserV1(newUserData, userProfile, apiRequestID), log)
        case UserUpdateRequestV1(userIri, propertyIri, newValue, userProfile, apiRequestID) => future2Message(sender(), updateUserV1(userIri, propertyIri, newValue, userProfile, apiRequestID), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Gets information about a Knora user, and returns it in a [[Option[UserProfileV1]].
      *
      * @param userIri the IRI of the user.
      * @return a [[Option[UserProfileV1]] describing the user.
      */
    private def getUserProfileByIRIV1(userIri: IRI, clean: Boolean): Future[UserProfileV1] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getUser(userIri).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            //_ = log.debug(MessageUtil.toSource(userDataQueryResponse))

            _ = if (userDataQueryResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"User '$userIri' not found")
            }

            userProfileV1 = userDataQueryResponse2UserProfile(userDataQueryResponse, clean)

        } yield userProfileV1 // UserProfileV1(userDataV1, groupIris, projectIris)
    }

    /**
      * Gets information about a Knora user, and returns it in a [[Option[UserProfileV1]].
      *
      * @param username the username of the user.
      * @return a [[Option[UserProfileV1]] describing the user.
      */
    private def getUserProfileByUsernameV1(username: String, clean: Boolean): Future[UserProfileV1] = {
        log.debug(s"getUserProfileByUsernameV1('$username', '$clean') called")
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getUserByUsername(username).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            //_ = log.debug(MessageUtil.toSource(userDataQueryResponse))

            _ = if (userDataQueryResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"User '$username' not found")
            }

            userProfileV1 = userDataQueryResponse2UserProfile(userDataQueryResponse, clean)

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
            sparqlQuery <- Future(queries.sparql.v1.txt.getUserByUsername(newUserData.username).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            //_ = log.debug(MessageUtil.toSource(userDataQueryResponse))

            _ = if (userDataQueryResponse.results.bindings.nonEmpty) {
                throw DuplicateValueException(s"User with the username: '${newUserData.username}' already exists")
            }

            userIri = knoraIriUtil.makeRandomPersonIri

            hashedPassword = BCrypt.hashpw(newUserData.password, BCrypt.gensalt())

            // perform update
            createResourceResponse <- TransactionUtil.runInUpdateTransaction({
                apiRequestID =>
                    for {
                        // Create the user.
                        createNewUserSparql <- Future(queries.sparql.v1.txt.createNewUser(
                            adminNamedGraphIri = "http://www.knora.org/data/admin",
                            triplestore = settings.triplestoreType,
                            userIri = userIri,
                            userClassIri = OntologyConstants.KnoraBase.User,
                            username = newUserData.username,
                            password = hashedPassword,
                            givenName = newUserData.givenName,
                            familyName = newUserData.familyName,
                            email = newUserData.email,
                            preferredLanguage = newUserData.lang).toString)
                        // _ = println(createNewUserSparql)
                        createResourceResponse <- (storeManager ? SparqlUpdateRequest(apiRequestID, createNewUserSparql)).mapTo[SparqlUpdateResponse]
                    } yield createResourceResponse
            }, storeManager)

            // Verify that the user was created.

            sparqlQuery <- Future(queries.sparql.v1.txt.getUser(userIri = userIri).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            _ = if (userDataQueryResponse.results.bindings.isEmpty) {
                throw UpdateNotPerformedException(s"User $userIri was not created. Please report this as a possible bug.")
            }

            // create the user profile
            newUserProfile = userDataQueryResponse2UserProfile(userDataQueryResponse, true)

            // create the user operation response
            userOperationResponseV1 = UserOperationResponseV1(newUserProfile, userProfile.userData)

        } yield userOperationResponseV1

    }


    private def updateUserV1(userIri: webapi.IRI, propertyIri: webapi.IRI, newValue: Any, userProfile: UserProfileV1, apiRequestID: UUID): Future[UserOperationResponseV1] = for {
            a <- Future("")

            // check if necessary information is present
            _ = if (userIri.isEmpty) throw BadRequestException("User IRI cannot be empty")

            // check if the requesting user is allowed to perform updates
            _ = if (!userProfile.userData.user_id.contains(userIri) && !userProfile.userData.isSystemAdmin.contains(true)) {
                // not the user and not a system admin
                throw ForbiddenException("User information can only be changed by the user itself or a system administrator")
            }
            _ = if (propertyIri.equals(OntologyConstants.KnoraBase.IsSystemAdmin) && !userProfile.userData.isSystemAdmin.contains(true)) {
                // the operation of promoting to system admin is only allowed by another system admin
                throw ForbiddenException("Giving an user system admin rights can only be performed by another system admin")
            }

            // get current value.
            sparqlQuery <- Future(queries.sparql.v1.txt.getUser(userIri = userIri).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            // create the user profile including sensitive information
            currentUserProfile = userDataQueryResponse2UserProfile(userDataQueryResponse, false)

            // get the current value
            currentValue = propertyIri match {
                case OntologyConstants.KnoraBase.Username => currentUserProfile.userData.username.getOrElse("")
                case OntologyConstants.Foaf.GivenName => currentUserProfile.userData.firstname.getOrElse("")
                case OntologyConstants.Foaf.FamilyName => currentUserProfile.userData.lastname.getOrElse("")
                case OntologyConstants.KnoraBase.Email => currentUserProfile.userData.email.getOrElse("")
                case OntologyConstants.KnoraBase.Password => currentUserProfile.userData.hashedpassword.getOrElse("")
                case OntologyConstants.KnoraBase.IsActiveUser => currentUserProfile.userData.isActiveUser.getOrElse(false)
                case OntologyConstants.KnoraBase.IsSystemAdmin => currentUserProfile.userData.isSystemAdmin.getOrElse(false)
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

            // perform the update
            createResourceResponse <- TransactionUtil.runInUpdateTransaction({
                apiRequestID =>
                    for {
                        // Update user property.
                        updateUserSparql <- Future(queries.sparql.v1.txt.updateUser(
                            adminNamedGraphIri = "http://www.knora.org/data/admin",
                            triplestore = settings.triplestoreType,
                            userIri = userIri,
                            propertyIri = propertyIri,
                            currentValue = currentValueLiteral,
                            newValue = newValueLiteral
                        ).toString)
                        _ = println(updateUserSparql)
                        createResourceResponse <- (storeManager ? SparqlUpdateRequest(apiRequestID, updateUserSparql)).mapTo[SparqlUpdateResponse]
                    } yield createResourceResponse
            }, storeManager)

            // Verify that the user was updated.
            sparqlQuery <- Future(queries.sparql.v1.txt.getUser(userIri = userIri).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]


            // create the user profile including sensitive information
            updatedUserProfile = userDataQueryResponse2UserProfile(userDataQueryResponse, false)

            // check if what we wanted to update actually got updated
            _ = log.debug(s"currentValue: ${currentValue.toString}, newValue: ${newValue.toString}")
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
                    if (!updatedUserProfile.userData.hashedpassword.contains(newValue.asInstanceOf[String])) {
                        throw UpdateNotPerformedException("User's 'password' was not updated. Please report this as a possible bug.")
                    }
                }
                case OntologyConstants.KnoraBase.IsActiveUser => {
                    if (!updatedUserProfile.userData.isActiveUser.contains(newValue.asInstanceOf[Boolean])) {
                        throw UpdateNotPerformedException("User's 'active status' was not updated. Please report this as a possible bug.")
                    }
                }
                case OntologyConstants.KnoraBase.IsSystemAdmin => {
                    if (!updatedUserProfile.userData.isSystemAdmin.contains(newValue.asInstanceOf[Boolean])) {
                        throw UpdateNotPerformedException("User's 'admin status' was not updated. Please report this as a possible bug.")
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

                UserOperationResponseV1(updatedUserProfile.getCleanUserProfileV1, updatedUserProfile.getCleanUserProfileV1.userData)
            } else {
                UserOperationResponseV1(updatedUserProfile.getCleanUserProfileV1, userProfile.userData)
            }
        } yield userOperationResponseV1



    /**
      * Helper method used to create a [[UserProfileV1]] from the [[SparqlSelectResponse]] containing user data.
      *
      * @param userDataQueryResponse a [[SparqlSelectResponse]] containing user data.
      * @param clean a flag denoting if sensitive information should be stripped from the returned [[UserProfileV1]]
      * @return a [[UserProfileV1]] containing the user's data.
      */
    private def userDataQueryResponse2UserProfile(userDataQueryResponse: SparqlSelectResponse, clean: Boolean): UserProfileV1 = {

        //log.debug(MessageUtil.toSource(userDataQueryResponse))

        val returnedUserIri = userDataQueryResponse.getFirstRow.rowMap("s")

        val groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
            case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
        }

        //log.debug(s"RAW: ${groupedUserData.toString}")

        val userDataV1 = UserDataV1(
            user_id = Some(returnedUserIri),
            username = groupedUserData.get(OntologyConstants.KnoraBase.Username).map(_.head),
            firstname = groupedUserData.get(OntologyConstants.Foaf.GivenName).map(_.head),
            lastname = groupedUserData.get(OntologyConstants.Foaf.FamilyName).map(_.head),
            email = groupedUserData.get(OntologyConstants.KnoraBase.Email).map(_.head),
            hashedpassword = groupedUserData.get(OntologyConstants.KnoraBase.Password).map(_.head),
            isActiveUser = groupedUserData.get(OntologyConstants.KnoraBase.IsActiveUser).map(_.head.toBoolean),
            isSystemAdmin = groupedUserData.get(OntologyConstants.KnoraBase.IsSystemAdmin).map(_.head.toBoolean),
            lang = groupedUserData.get(OntologyConstants.KnoraBase.PreferredLanguage) match {
                case Some(langList) => langList.head
                case None => settings.fallbackLanguage
            }
        )

        val groupIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInGroup) match {
            case Some(groups) => groups
            case None => Vector.empty[IRI]
        }

        val projectIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInProject) match {
            case Some(projects) => projects
            case None => Vector.empty[IRI]
        }

        val isGroupAdminForIris = groupedUserData.get(OntologyConstants.KnoraBase.IsGroupAdmin) match {
            case Some(groups) => groups
            case None => Vector.empty[IRI]
        }

        val isProjectAdminForIris = groupedUserData.get(OntologyConstants.KnoraBase.IsProjectAdmin) match {
            case Some(projects) => projects
            case None => Vector.empty[IRI]
        }

        val up = UserProfileV1(
            userData = userDataV1,
            groups = groupIris,
            projects = projectIris,
            isGroupAdminFor = isGroupAdminForIris,
            isProjectAdminFor = isProjectAdminForIris
        )
        log.debug(s"UserProfileV1: ${up.toString}")

        if (clean) {
            up.getCleanUserProfileV1
        } else {
            up
        }
    }

}
