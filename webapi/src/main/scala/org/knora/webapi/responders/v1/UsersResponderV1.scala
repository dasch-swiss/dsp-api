/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionDataGetADM, PermissionsDataADM}
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoByIRIGetV1, ProjectInfoV1}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileTypeV1.UserProfileType
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.util.{CacheUtil, KnoraIdUtil}

import scala.concurrent.Future

/**
  * Provides information about Knora users to other responders.
  */
class UsersResponderV1(system: ActorSystem, applicationStateActor: ActorRef, responderManager: ActorRef, storeManager: ActorRef) extends Responder(system, applicationStateActor, responderManager, storeManager) {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    // The IRI used to lock user creation and update
    val USERS_GLOBAL_LOCK_IRI = "http://rdfh.ch/users"

    val USER_PROFILE_CACHE_NAME = "userProfileCache"

    /**
      * Receives a message of type [[UsersResponderRequestV1]], and returns an appropriate response message.
      */
    def receive(msg: UsersResponderRequestV1) = msg match {
        case UsersGetV1() => usersGetV1
        case UsersGetRequestV1(userProfileV1) => usersGetRequestV1(userProfileV1)
        case UserDataByIriGetV1(userIri, short) => userDataByIriGetV1(userIri, short)
        case UserProfileByIRIGetV1(userIri, profileType) => userProfileByIRIGetV1(userIri, profileType)
        case UserProfileByIRIGetRequestV1(userIri, profileType, userProfile) => userProfileByIRIGetRequestV1(userIri, profileType, userProfile)
        case UserProfileByEmailGetV1(email, profileType) => userProfileByEmailGetV1(email, profileType)
        case UserProfileByEmailGetRequestV1(email, profileType, userProfile) => userProfileByEmailGetRequestV1(email, profileType, userProfile)
        case UserProjectMembershipsGetRequestV1(userIri, userProfile, apiRequestID) => userProjectMembershipsGetRequestV1(userIri, userProfile, apiRequestID)
        case UserProjectAdminMembershipsGetRequestV1(userIri, userProfile, apiRequestID) => userProjectAdminMembershipsGetRequestV1(userIri, userProfile, apiRequestID)
        case UserGroupMembershipsGetRequestV1(userIri, userProfile, apiRequestID) => userGroupMembershipsGetRequestV1(userIri, userProfile, apiRequestID)
        case other => handleUnexpectedMessage(other, log, this.getClass.getName)
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

            maybeUserDataV1 <- userDataQueryResponse2UserDataV1(userDataQueryResponse, short)

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

                    maybeUserProfileV1 <- userDataQueryResponse2UserProfileV1(userDataQueryResponse)

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
    private def userProfileByIRIGetRequestV1(userIRI: IRI, profileType: UserProfileType, userProfile: UserProfileV1): Future[UserProfileResponseV1] = {
        for {
            maybeUserProfileToReturn <- userProfileByIRIGetV1(userIRI, profileType)
            result = maybeUserProfileToReturn match {
                case Some(up) => UserProfileResponseV1(up)
                case None => throw NotFoundException(s"User '$userIRI' not found")
            }
        } yield result
    }

    /**
      * Gets information about a Knora user, and returns it in a [[UserProfileV1]]. If possible, tries to retrieve the user profile
      * from cache. If not, it retrieves it from the triplestore and writes it to the cache.
      *
      * @param email       the email of the user.
      * @param profileType the type of the requested profile (restricted or full).
      * @return a [[UserProfileV1]] describing the user.
      */
    private def userProfileByEmailGetV1(email: String, profileType: UserProfileType): Future[Option[UserProfileV1]] = {
        // log.debug(s"userProfileByEmailGetV1: username = '{}', type = '{}'", email, profileType)

        CacheUtil.get[UserProfileV1](USER_PROFILE_CACHE_NAME, email) match {
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
                    maybeUserProfileV1 <- userDataQueryResponse2UserProfileV1(userDataQueryResponse)

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
    private def userProfileByEmailGetRequestV1(email: String, profileType: UserProfileType, userProfile: UserProfileV1): Future[UserProfileResponseV1] = {
        for {
            maybeUserProfileToReturn <- userProfileByEmailGetV1(email, profileType)
            result = maybeUserProfileToReturn match {
                case Some(up: UserProfileV1) => UserProfileResponseV1(up)
                case None => throw NotFoundException(s"User '$email' not found")
            }
        } yield result
    }

    /**
      * Returns the user's project memberships, where the result contains the IRIs of the projects the user is member of.
      *
      * @param userIri       the user's IRI.
      * @param userProfileV1 the user profile of the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return a [[UserProjectMembershipsGetResponseV1]].
      */
    def userProjectMembershipsGetRequestV1(userIri: IRI, userProfileV1: UserProfileV1, apiRequestID: UUID): Future[UserProjectMembershipsGetResponseV1] = {
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
      * Returns the user's project admin group memberships, where the result contains the IRIs of the projects the user
      * is a member of the project admin group.
      *
      * @param userIri       the user's IRI.
      * @param userProfileV1 the user profile of the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return a [[UserProjectMembershipsGetResponseV1]].
      */
    def userProjectAdminMembershipsGetRequestV1(userIri: IRI, userProfileV1: UserProfileV1, apiRequestID: UUID): Future[UserProjectAdminMembershipsGetResponseV1] = {
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
      * Returns the user's custom (without ProjectMember and ProjectAdmin) group memberships
      *
      * @param userIri       the user's IRI.
      * @param userProfileV1 the user profile of the requesting user.
      * @param apiRequestID  the unique api request ID.
      * @return a [[UserGroupMembershipsGetResponseV1]]
      */
    def userGroupMembershipsGetRequestV1(userIri: IRI, userProfileV1: UserProfileV1, apiRequestID: UUID): Future[UserGroupMembershipsGetResponseV1] = {

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
    private def userDataQueryResponse2UserDataV1(userDataQueryResponse: SparqlSelectResponse, short: Boolean): Future[Option[UserDataV1]] = {

        // log.debug("userDataQueryResponse2UserDataV1 - " + MessageUtil.toSource(userDataQueryResponse))

        if (userDataQueryResponse.results.bindings.nonEmpty) {
            val returnedUserIri = userDataQueryResponse.getFirstRow.rowMap("s")

            val groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            // _ = log.debug(s"userDataQueryResponse2UserProfileV1 - groupedUserData: ${MessageUtil.toSource(groupedUserData)}")

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
      * Helper method used to create a [[UserProfileV1]] from the [[SparqlSelectResponse]] containing user data.
      *
      * @param userDataQueryResponse a [[SparqlSelectResponse]] containing user data.
      * @return a [[UserProfileV1]] containing the user's data.
      */
    private def userDataQueryResponse2UserProfileV1(userDataQueryResponse: SparqlSelectResponse): Future[Option[UserProfileV1]] = {

        // log.debug("userDataQueryResponse2UserProfileV1 - userDataQueryResponse: {}", MessageUtil.toSource(userDataQueryResponse))

        if (userDataQueryResponse.results.bindings.nonEmpty) {
            val returnedUserIri = userDataQueryResponse.getFirstRow.rowMap("s")

            val groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            // log.debug("userDataQueryResponse2UserProfileV1 - groupedUserData: {}", MessageUtil.toSource(groupedUserData))

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

            // log.debug("userDataQueryResponse2UserProfileV1 - userDataV1: {}", MessageUtil.toSource(userDataV1))


            /* the projects the user is member of */
            val projectIris: Seq[IRI] = groupedUserData.get(OntologyConstants.KnoraBase.IsInProject) match {
                case Some(projects) => projects
                case None => Seq.empty[IRI]
            }

            // log.debug(s"userDataQueryResponse2UserProfileV1 - projectIris: ${MessageUtil.toSource(projectIris)}")

            /* the groups the user is member of (only explicit groups) */
            val groupIris = groupedUserData.get(OntologyConstants.KnoraBase.IsInGroup) match {
                case Some(groups) => groups
                case None => Seq.empty[IRI]
            }

            // log.debug(s"userDataQueryResponse2UserProfileV1 - groupIris: ${MessageUtil.toSource(groupIris)}")

            /* the projects for which the user is implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group */
            val isInProjectAdminGroups = groupedUserData.getOrElse(OntologyConstants.KnoraBase.IsInProjectAdminGroup, Vector.empty[IRI])

            /* is the user implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#SystemAdmin' group */
            val isInSystemAdminGroup = groupedUserData.get(OntologyConstants.KnoraBase.IsInSystemAdminGroup).exists(p => p.head.toBoolean)

            for {
                /* get the user's permission profile from the permissions responder */
                permissionData <- (responderManager ? PermissionDataGetADM(
                    projectIris = projectIris,
                    groupIris = groupIris,
                    isInProjectAdminGroups = isInProjectAdminGroups,
                    isInSystemAdminGroup = isInSystemAdminGroup,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )).mapTo[PermissionsDataADM]

                maybeProjectInfoFutures: Seq[Future[Option[ProjectInfoV1]]] = projectIris.map {
                    projectIri => (responderManager ? ProjectInfoByIRIGetV1(iri = projectIri, userProfileV1 = None)).mapTo[Option[ProjectInfoV1]]
                }

                maybeProjectInfos: Seq[Option[ProjectInfoV1]] <- Future.sequence(maybeProjectInfoFutures)
                projectInfos = maybeProjectInfos.flatten
                projectInfoMap: Map[IRI, ProjectInfoV1] = projectInfos.map(projectInfo => projectInfo.id -> projectInfo).toMap

                /* construct the user profile from the different parts */
                up = UserProfileV1(
                    userData = userDataV1,
                    groups = groupIris,
                    projects_info = projectInfoMap,
                    sessionId = None,
                    permissionData = permissionData
                )
                // _ = log.debug("Retrieved UserProfileV1: {}", up.toString)

                result: Option[UserProfileV1] = Some(up)
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
      * @param userProfile a [[UserProfileV1]].
      * @return true if writing was successful.
      * @throws ApplicationCacheException when there is a problem with writing the user's profile to cache.
      */
    private def writeUserProfileV1ToCache(userProfile: UserProfileV1): Boolean = {

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
