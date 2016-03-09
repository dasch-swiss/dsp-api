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

import akka.actor.Status
import akka.pattern._
import com.typesafe.scalalogging.Logger
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.projectmessages.{ProjectInfoByIRIGetRequest, ProjectInfoResponseV1, ProjectInfoType, ProjectInfoV1}
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse}
import org.knora.webapi.messages.v1respondermessages.usermessages._
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.MessageUtil

import scala.concurrent.Future

/**
  * Provides information about Knora users to other responders.
  */
class UsersResponderV1 extends ResponderV1 {

    /**
      * Receives a message extending [[UsersResponderRequestV1]], and returns a message of type [[UserProfileV1]]
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case UserProfileGetRequestV1(userIri, clean) => future2Message(sender(), getUserProfileV1(userIri, clean), log)
        case UserProfileByUsernameGetRequestV1(username, clean) => future2Message(sender(), getUserProfileByUsernameV1(username, clean), log)
        case UserCreateRequestV1(newUserData, userProfile) => future2Message(sender(), createNewUserV1(newUserData, userProfile), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Gets information about a Knora user, and returns it in a [[Option[UserProfileV1]].
      *
      * @param userIri the IRI of the user.
      * @return a [[Option[UserProfileV1]] describing the user.
      */
    private def getUserProfileV1(userIri: IRI, clean: Boolean): Future[Option[UserProfileV1]] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getUser(userIri).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            _ = if (userDataQueryResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"User '$userIri' not found")
            }

            groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            userDataV1 = UserDataV1(
                lang = groupedUserData.get(OntologyConstants.KnoraBase.PreferredLanguage) match {
                    case Some(langList) => langList.head
                    case None => settings.fallbackLanguage
                },
                user_id = Some(userIri),
                username = groupedUserData.get(OntologyConstants.KnoraBase.Username).map(_.head),
                firstname = groupedUserData.get(OntologyConstants.Foaf.GivenName).map(_.head),
                lastname = groupedUserData.get(OntologyConstants.Foaf.FamilyName).map(_.head),
                email = groupedUserData.get(OntologyConstants.KnoraBase.Email).map(_.head),
                password = groupedUserData.get(OntologyConstants.KnoraBase.Password).map(_.head),
                passwordSalt = groupedUserData.get(OntologyConstants.KnoraBase.PasswordSalt).map(_.head)
            )

            groupIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInGroup) match {
                case Some(groups) => groups
                case None => Vector.empty[IRI]
            }

            projectIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInProject) match {
                case Some(projects) => projects
                case None => Vector.empty[IRI]
            }

            //_ = log.debug(s"RAW: ${groupedUserData.toString}")

            userProfileV1 = {
                if (groupedUserData.isEmpty) {
                    None
                } else {
                    if (clean) {
                        Some(UserProfileV1(userDataV1, groupIris, projectIris).getCleanUserProfileV1)
                    } else {
                        Some(UserProfileV1(userDataV1, groupIris, projectIris))
                    }
                }
            }

        //_ = log.debug(s"${userProfileV1.toString}")

        } yield userProfileV1
    }

    /**
      * Gets information about a Knora user, and returns it in a [[Option[UserProfileV1]].
      *
      * @param username the username of the user.
      * @return a [[Option[UserProfileV1]] describing the user.
      */
    private def getUserProfileByUsernameV1(username: String, clean: Boolean): Future[Option[UserProfileV1]] = {
        log.debug(s"getUserProfileByUsernameV1('$username', '$clean') called")
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getUserByUsername(username).toString())
            userDataQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            _ = log.debug(MessageUtil.toSource(userDataQueryResponse))

            _ = if (userDataQueryResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"User '$username' not found")
            }

            userIri = userDataQueryResponse.getFirstRow.rowMap("s")

            groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            groupIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInGroup) match {
                case Some(groups) => groups
                case None => Vector.empty[IRI]
            }

            projectIris: Seq[String] = groupedUserData.get(OntologyConstants.KnoraBase.IsInProject) match {
                case Some(projects) => projects
                case None => Vector.empty[IRI]
            }

            /*
            projectInfoFutures: Seq[Future[ProjectInfoV1]] = projectIris.map {
                projectIri => (responderManager ? ProjectInfoByIRIGetRequest(projectIri, ProjectInfoType.SHORT, None)).mapTo[ProjectInfoResponseV1] map (_.project_info)
            }


            projectInfos <- Future.sequence(projectInfoFutures)
            */


            userDataV1 = UserDataV1(
                lang = groupedUserData.get(OntologyConstants.KnoraBase.PreferredLanguage) match {
                    case Some(langList) => langList.head
                    case None => settings.fallbackLanguage
                },
                user_id = Some(userIri),
                username = groupedUserData.get(OntologyConstants.KnoraBase.Username).map(_.head),
                firstname = groupedUserData.get(OntologyConstants.Foaf.GivenName).map(_.head),
                lastname = groupedUserData.get(OntologyConstants.Foaf.FamilyName).map(_.head),
                email = groupedUserData.get(OntologyConstants.KnoraBase.Email).map(_.head),
                password = groupedUserData.get(OntologyConstants.KnoraBase.Password).map(_.head),
                passwordSalt = groupedUserData.get(OntologyConstants.KnoraBase.PasswordSalt).map(_.head)
            )

            _ = log.debug(s"RAW: ${groupedUserData.toString}")

            userProfileV1 = {
                if (groupedUserData.isEmpty) {
                    None
                } else {
                    if (clean) {
                        Some(UserProfileV1(userDataV1, groupIris, projectIris).getCleanUserProfileV1)
                    } else {
                        Some(UserProfileV1(userDataV1, groupIris, projectIris))
                    }
                }
            }

            _ = log.debug(s"${userProfileV1.toString}")

        } yield userProfileV1 // Some(UserProfileV1(userDataV1, groupIris, projectIris))
    }

    /**
      * Creates a new user. Self-registration is allowed, so even the default user, i.e. with no credentials supplied,
      * is allowed to create the new user. If the projects list is not empty, then credentials need to be supplied, so
      * that the requesting user can be checked if he has the needed rights to add the new user to the listed projects.
      * If projects are listed, and the requesting user does not have the needed rights, then the user is still created
      * and only the adding-to-projects part is skipped.
      *
      * Referenced Websites:
      *                     - https://crackstation.net/hashing-security.htm
      *                     - http://blog.ircmaxell.com/2012/12/seven-ways-to-screw-up-bcrypt.html
      *
      * @param newUserData a [[NewUserDataV1]] object containing information about the new user to be created.
      * @param userProfile a [[UserProfileV1]] object containing information about the requesting user.
      * @return a future containing the [[UserOperationResponseV1]].
      */
    private def createNewUserV1(newUserData: NewUserDataV1, userProfile: UserProfileV1): Future[UserOperationResponseV1] = {
        // self-registration allowed, so no checking if the user has the right to create a new user
        // check if the supplied username and email address for the new user are unique
        // create the user

        // if projects list is not empty, check if the requesting user has the right to add the newly created user to
        // these projects. If so, then add. If not, then still return successfully but add message saying that
        // adding-to-group did fail.

        val newUserProfile = UserProfileV1(UserDataV1(lang = "de", username = Some(newUserData.username), email = Some(newUserData.email)))

        Future.successful(UserOperationResponseV1(newUserProfile, userProfile.userData))
    }

    /**
      *
      * @param user
      * @param groups
      * @param userProfile
      * @return
      */
    private def addUserToGroupV1(user: IRI, groups: Vector[IRI], userProfile: UserProfileV1): Future[UserOperationResponseV1] = ???
}
