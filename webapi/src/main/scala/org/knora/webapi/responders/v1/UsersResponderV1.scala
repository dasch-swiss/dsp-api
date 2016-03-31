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
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.ontologymessages.{EntityInfoGetRequestV1, EntityInfoGetResponseV1}
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, SparqlUpdateRequest, SparqlUpdateResponse}
import org.knora.webapi.messages.v1respondermessages.usermessages._
import org.knora.webapi.messages.v1respondermessages.valuemessages.{CreateMultipleValuesRequestV1, CreateMultipleValuesResponseV1}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.MessageUtil
import org.knora.webapi.util.KnoraIriUtil

import scala.concurrent.Future

import org.mindrot.jbcrypt.BCrypt

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
        // self-registration allowed, so no checking if the user has the right to create a new user

        // check if username is not empty
        if (newUserData.username.isEmpty) throw BadRequestException("Username cannot be empty")

        if (newUserData.password.isEmpty) throw BadRequestException("Password cannot be empty")

        for {
            // check if the supplied username for the new user is unique, i.e. not already registered
            sparqlQuery <- Future(queries.sparql.v1.txt.getUserByUsername(newUserData.username).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            //_ = log.debug(MessageUtil.toSource(userDataQueryResponse))

            _ = if (userDataQueryResponse.results.bindings.nonEmpty) {
                throw DuplicateValueException(s"User with the username: '${newUserData.username}' already exists")
            }

            userIri = knoraIriUtil.makeRandomPersonIri

            hashedPassword = BCrypt.hashpw(newUserData.password, BCrypt.gensalt())

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

    /**
      *
      *
      * @param user
      * @param groups
      * @param userProfile
      * @return
      */
    private def addUserToGroupV1(user: IRI, groups: Vector[IRI], userProfile: UserProfileV1): Future[UserOperationResponseV1] = ???

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
            lang = groupedUserData.get(OntologyConstants.KnoraBase.PreferredLanguage) match {
                case Some(langList) => langList.head
                case None => settings.fallbackLanguage
            },
            user_id = Some(returnedUserIri),
            username = groupedUserData.get(OntologyConstants.KnoraBase.Username).map(_.head),
            firstname = groupedUserData.get(OntologyConstants.Foaf.GivenName).map(_.head),
            lastname = groupedUserData.get(OntologyConstants.Foaf.FamilyName).map(_.head),
            email = groupedUserData.get(OntologyConstants.KnoraBase.Email).map(_.head),
            hashedpassword = groupedUserData.get(OntologyConstants.KnoraBase.Password).map(_.head)
        )

        val groupIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInGroup) match {
            case Some(groups) => groups
            case None => Vector.empty[IRI]
        }

        val projectIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInProject) match {
            case Some(projects) => projects
            case None => Vector.empty[IRI]
        }

        val userProfileV1 = {
            if (clean) {
                UserProfileV1(userDataV1, groupIris, projectIris).getCleanUserProfileV1
            } else {
                UserProfileV1(userDataV1, groupIris, projectIris)
            }
        }

        log.debug(s"UserProfileV1: ${userProfileV1.toString}")

        userProfileV1
    }

}
