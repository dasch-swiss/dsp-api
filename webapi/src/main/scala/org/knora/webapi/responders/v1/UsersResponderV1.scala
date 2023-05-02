/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v1

import com.typesafe.scalalogging.LazyLogging
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio._

import java.util.UUID

import dsp.errors.ApplicationCacheException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDataGetADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoByIRIGetV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileTypeV1.UserProfileType
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.util.cache.CacheUtil

/**
 * Provides information about Knora users to other responders.
 */
trait UsersResponderV1
final case class UsersResponderV1Live(
  appConfig: AppConfig,
  messageRelay: MessageRelay,
  triplestoreService: TriplestoreService
) extends UsersResponderV1
    with MessageHandler
    with LazyLogging {

  val USER_PROFILE_CACHE_NAME = "userProfileCache"

  override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[UsersResponderRequestV1]

  /**
   * Receives a message of type [[UsersResponderRequestV1]], and returns an appropriate response message.
   */
  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case UsersGetV1(userProfile)            => usersGetV1(userProfile)
    case UsersGetRequestV1(userProfileV1)   => usersGetRequestV1(userProfileV1)
    case UserDataByIriGetV1(userIri, short) => userDataByIriGetV1(userIri, short)
    case UserProfileByIRIGetV1(userIri, profileType) =>
      userProfileByIRIGetV1(userIri, profileType)
    case UserProfileByIRIGetRequestV1(userIri, profileType, userProfile) =>
      userProfileByIRIGetRequestV1(userIri, profileType, userProfile)
    case UserProfileByEmailGetV1(email, profileType) =>
      userProfileByEmailGetV1(email, profileType)
    case UserProfileByEmailGetRequestV1(email, profileType, userProfile) =>
      userProfileByEmailGetRequestV1(email, profileType, userProfile)
    case UserProjectMembershipsGetRequestV1(userIri, userProfile, apiRequestID) =>
      userProjectMembershipsGetRequestV1(userIri, userProfile, apiRequestID)
    case UserProjectAdminMembershipsGetRequestV1(userIri, userProfile, apiRequestID) =>
      userProjectAdminMembershipsGetRequestV1(userIri, userProfile, apiRequestID)
    case UserGroupMembershipsGetRequestV1(userIri, userProfile, apiRequestID) =>
      userGroupMembershipsGetRequestV1(userIri, userProfile, apiRequestID)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Gets all the users and returns them as a sequence of [[UserDataV1]].
   *
   * @return all the users as a sequence of [[UserDataV1]].
   */
  private def usersGetV1(userProfileV1: UserProfileV1): Task[Seq[UserDataV1]] =
    for {
      _ <-
        ZIO
          .fail(ForbiddenException("SystemAdmin permissions are required."))
          .when(!userProfileV1.permissionData.isSystemAdmin)

      sparqlQueryString <- ZIO.attempt(
                             org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                               .getUsers()
                               .toString()
                           )

      usersResponse <- triplestoreService.sparqlHttpSelect(sparqlQueryString)

      usersResponseRows: Seq[VariableResultsRow] = usersResponse.results.bindings

      usersWithProperties: Map[String, Map[String, String]] =
        usersResponseRows.groupBy(_.rowMap("s")).map { case (userIri: IRI, rows: Seq[VariableResultsRow]) =>
          (userIri, rows.map(row => (row.rowMap("p"), row.rowMap("o"))).toMap)
        }

      users = usersWithProperties.map { case (userIri: IRI, propsMap: Map[String, String]) =>
                UserDataV1(
                  lang = propsMap.get(OntologyConstants.KnoraAdmin.PreferredLanguage) match {
                    case Some(langList) => langList
                    case None           => appConfig.fallbackLanguage
                  },
                  user_id = Some(userIri),
                  email = propsMap.get(OntologyConstants.KnoraAdmin.Email),
                  firstname = propsMap.get(OntologyConstants.KnoraAdmin.GivenName),
                  lastname = propsMap.get(OntologyConstants.KnoraAdmin.FamilyName),
                  status = propsMap.get(OntologyConstants.KnoraAdmin.Status).map(_.toBoolean)
                )
              }.toSeq

    } yield users

  /**
   * Gets all the users and returns them as a [[UsersGetResponseV1]].
   *
   * @param userProfileV1 the type of the requested profile (restricted of full).
   * @return all the users as a [[UsersGetResponseV1]].
   */
  private def usersGetRequestV1(userProfileV1: UserProfileV1): Task[UsersGetResponseV1] =
    for {
      maybeUsersListToReturn <- usersGetV1(userProfileV1)
      result <-
        maybeUsersListToReturn match {
          case users: Seq[UserDataV1] if users.nonEmpty => ZIO.succeed(UsersGetResponseV1(users))
          case _                                        => ZIO.fail(NotFoundException(s"No users found"))
        }
    } yield result

  /**
   * Gets basic information about a Knora user, and returns it in a [[UserDataV1]].
   *
   * @param userIri the IRI of the user.
   * @return a [[UserDataV1]] describing the user.
   */
  private def userDataByIriGetV1(userIri: IRI, short: Boolean): Task[Option[UserDataV1]] =
    for {
      sparqlQueryString <- ZIO.attempt(
                             org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                               .getUserByIri(
                                 userIri = userIri
                               )
                               .toString()
                           )

      userDataQueryResponse <- triplestoreService.sparqlHttpSelect(sparqlQueryString)
      maybeUserDataV1       <- userDataQueryResponse2UserDataV1(userDataQueryResponse, short)

    } yield maybeUserDataV1

  /**
   * Gets information about a Knora user, and returns it in a [[UserProfileV1]]. If possible, tries to retrieve the
   * user profile from cache. If not, it retrieves it from the triplestore and writes it to the cache.
   *
   * @param userIri              the IRI of the user.
   * @param profileType          the type of the requested profile (restricted of full).
   *
   * @return a [[UserProfileV1]] describing the user.
   */
  private def userProfileByIRIGetV1(
    userIri: IRI,
    profileType: UserProfileType
  ): Task[Option[UserProfileV1]] =
    CacheUtil.get[UserProfileV1](USER_PROFILE_CACHE_NAME, userIri) match {
      case Some(userProfile) =>
        // found a user profile in the cache
        logger.debug(s"userProfileByIRIGetV1 - cache hit: $userProfile")
        ZIO.succeed(Some(userProfile.ofType(profileType)))

      case None =>
        for {
          sparqlQueryString <- ZIO.attempt(
                                 org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                                   .getUserByIri(
                                     userIri = userIri
                                   )
                                   .toString()
                               )

          userDataQueryResponse <- triplestoreService.sparqlHttpSelect(sparqlQueryString)
          maybeUserProfileV1 <- userDataQueryResponse2UserProfileV1(
                                  userDataQueryResponse = userDataQueryResponse
                                )

          _ <- ZIO.foreachDiscard(maybeUserProfileV1)(writeUserProfileV1ToCache)

          result = maybeUserProfileV1.map(_.ofType(profileType))
        } yield result // UserProfileV1(userData, groups, projects_info, sessionId, isSystemUser, permissionData)
    }

  /**
   * Gets information about a Knora user, and returns it as a [[UserProfileResponseV1]].
   *
   * @param userIRI              the IRI of the user.
   * @param profileType          the type of the requested profile (restriced or full).
   *
   * @param userProfile          the requesting user's profile.
   * @return a [[UserProfileResponseV1]]
   */
  private def userProfileByIRIGetRequestV1(
    userIRI: IRI,
    profileType: UserProfileType,
    userProfile: UserProfileV1
  ): Task[UserProfileResponseV1] =
    for {
      _ <- ZIO
             .fail(ForbiddenException("SystemAdmin permissions are required."))
             .when(!userProfile.permissionData.isSystemAdmin && !userProfile.userData.user_id.contains(userIRI))

      maybeUserProfileToReturn <- userProfileByIRIGetV1(userIRI, profileType)

      result <- ZIO
                  .fromOption(maybeUserProfileToReturn)
                  .mapBoth(_ => NotFoundException(s"User '$userIRI' not found"), UserProfileResponseV1)
    } yield result

  /**
   * Gets information about a Knora user, and returns it in a [[UserProfileV1]]. If possible, tries to retrieve the user profile
   * from cache. If not, it retrieves it from the triplestore and writes it to the cache.
   *
   * @param email                the email of the user.
   * @param profileType          the type of the requested profile (restricted or full).
   *
   * @return a [[UserProfileV1]] describing the user.
   */
  private def userProfileByEmailGetV1(
    email: String,
    profileType: UserProfileType
  ): Task[Option[UserProfileV1]] =
    CacheUtil.get[UserProfileV1](USER_PROFILE_CACHE_NAME, email) match {
      case Some(userProfile) =>
        // found a user profile in the cache
        logger.debug(s"userProfileByIRIGetV1 - cache hit: $userProfile")
        ZIO.succeed(Some(userProfile.ofType(profileType)))

      case None =>
        for {
          sparqlQueryString <- ZIO.attempt(
                                 org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                                   .getUserByEmail(
                                     email = email
                                   )
                                   .toString()
                               )

          userDataQueryResponse <- triplestoreService.sparqlHttpSelect(sparqlQueryString)
          maybeUserProfileV1 <- userDataQueryResponse2UserProfileV1(
                                  userDataQueryResponse = userDataQueryResponse
                                )

          _ <- ZIO.foreachDiscard(maybeUserProfileV1)(writeUserProfileV1ToCache)

          result = maybeUserProfileV1.map(_.ofType(profileType))
        } yield result // UserProfileV1(userDataV1, groupIris, projectIris)
    }

  /**
   * Gets information about a Knora user, and returns it as a [[UserProfileResponseV1]].
   *
   * @param email                the email of the user.
   * @param profileType          the type of the requested profile (restricted or full).
   * @param userProfile          the requesting user's profile.
   * @return a [[UserProfileResponseV1]]
   */
  private def userProfileByEmailGetRequestV1(
    email: String,
    profileType: UserProfileType,
    userProfile: UserProfileV1
  ): Task[UserProfileResponseV1] =
    for {
      profile <- userProfileByEmailGetV1(email, profileType)
      result <-
        ZIO.fromOption(profile).mapBoth(_ => NotFoundException(s"User '$email' not found"), UserProfileResponseV1)
    } yield result

  /**
   * Returns the user's project memberships, where the result contains the IRIs of the projects the user is member of.
   *
   * @param userIri       the user's IRI.
   * @param userProfileV1 the user profile of the requesting user.
   * @param apiRequestID  the unique api request ID.
   * @return a [[UserProjectMembershipsGetResponseV1]].
   */
  def userProjectMembershipsGetRequestV1(
    userIri: IRI,
    userProfileV1: UserProfileV1,
    apiRequestID: UUID
  ): Task[UserProjectMembershipsGetResponseV1] =
    for {
      sparqlQueryString <- ZIO.attempt(
                             org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                               .getUserByIri(
                                 userIri = userIri
                               )
                               .toString()
                           )

      userDataQueryResponse <- triplestoreService.sparqlHttpSelect(sparqlQueryString)

      groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                                                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                                                  }

      /* the projects the user is member of */
      projectIris: Seq[IRI] = groupedUserData.get(OntologyConstants.KnoraAdmin.IsInProject) match {
                                case Some(projects) => projects
                                case None           => Seq.empty[IRI]
                              }
    } yield UserProjectMembershipsGetResponseV1(projects = projectIris)

  /**
   * Returns the user's project admin group memberships, where the result contains the IRIs of the projects the user
   * is a member of the project admin group.
   *
   * @param userIri       the user's IRI.
   * @param userProfileV1 the user profile of the requesting user.
   * @param apiRequestID  the unique api request ID.
   * @return a [[UserProjectMembershipsGetResponseV1]].
   */
  def userProjectAdminMembershipsGetRequestV1(
    userIri: IRI,
    userProfileV1: UserProfileV1,
    apiRequestID: UUID
  ): Task[UserProjectAdminMembershipsGetResponseV1] =
    for {
      sparqlQueryString <- ZIO.attempt(
                             org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                               .getUserByIri(
                                 userIri = userIri
                               )
                               .toString()
                           )

      userDataQueryResponse <- triplestoreService.sparqlHttpSelect(sparqlQueryString)

      groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                                                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                                                  }

      /* the projects the user is member of */
      projectIris: Seq[IRI] = groupedUserData.get(OntologyConstants.KnoraAdmin.IsInProjectAdminGroup) match {
                                case Some(projects) => projects
                                case None           => Seq.empty[IRI]
                              }

    } yield UserProjectAdminMembershipsGetResponseV1(projects = projectIris)

  /**
   * Returns the user's custom (without ProjectMember and ProjectAdmin) group memberships
   *
   * @param userIri       the user's IRI.
   * @param userProfileV1 the user profile of the requesting user.
   * @param apiRequestID  the unique api request ID.
   * @return a [[UserGroupMembershipsGetResponseV1]]
   */
  def userGroupMembershipsGetRequestV1(
    userIri: IRI,
    userProfileV1: UserProfileV1,
    apiRequestID: UUID
  ): Task[UserGroupMembershipsGetResponseV1] =
    for {
      sparqlQueryString <- ZIO.attempt(
                             org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                               .getUserByIri(
                                 userIri = userIri
                               )
                               .toString()
                           )

      userDataQueryResponse <- triplestoreService.sparqlHttpSelect(sparqlQueryString)

      groupedUserData: Map[String, Seq[String]] = userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map {
                                                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                                                  }

      /* the groups the user is member of */
      groupIris: Seq[IRI] = groupedUserData.get(OntologyConstants.KnoraAdmin.IsInGroup) match {
                              case Some(projects) => projects
                              case None           => Seq.empty[IRI]
                            }

    } yield UserGroupMembershipsGetResponseV1(groups = groupIris)

  ////////////////////
  // Helper Methods //
  ////////////////////

  /**
   * Helper method used to create a [[UserDataV1]] from the [[SparqlSelectResult]] containing user data.
   *
   * @param userDataQueryResponse a [[SparqlSelectResult]] containing user data.
   * @param short                 denotes if all information should be returned. If short == true, then no token and password should be returned.
   * @return a [[UserDataV1]] containing the user's basic data.
   */
  private def userDataQueryResponse2UserDataV1(
    userDataQueryResponse: SparqlSelectResult,
    short: Boolean
  ): Task[Option[UserDataV1]] =
    if (userDataQueryResponse.results.bindings.nonEmpty) {
      val returnedUserIri = userDataQueryResponse.getFirstRow.rowMap("s")

      val groupedUserData: Map[String, Seq[String]] =
        userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map { case (predicate, rows) =>
          predicate -> rows.map(_.rowMap("o"))
        }

      val userDataV1 = UserDataV1(
        lang = groupedUserData.get(OntologyConstants.KnoraAdmin.PreferredLanguage) match {
          case Some(langList) => langList.head
          case None           => appConfig.fallbackLanguage
        },
        user_id = Some(returnedUserIri),
        email = groupedUserData.get(OntologyConstants.KnoraAdmin.Email).map(_.head),
        firstname = groupedUserData.get(OntologyConstants.KnoraAdmin.GivenName).map(_.head),
        lastname = groupedUserData.get(OntologyConstants.KnoraAdmin.FamilyName).map(_.head),
        password = if (!short) {
          groupedUserData.get(OntologyConstants.KnoraAdmin.Password).map(_.head)
        } else None,
        status = groupedUserData.get(OntologyConstants.KnoraAdmin.Status).map(_.head.toBoolean)
      )

      ZIO.succeed(Some(userDataV1))
    } else {
      ZIO.succeed(None)
    }

  /**
   * Helper method used to create a [[UserProfileV1]] from the [[SparqlSelectResult]] containing user data.
   *
   * @param userDataQueryResponse a [[SparqlSelectResult]] containing user data.
   * @return a [[UserProfileV1]] containing the user's data.
   */
  private def userDataQueryResponse2UserProfileV1(
    userDataQueryResponse: SparqlSelectResult
  ): Task[Option[UserProfileV1]] =
    if (userDataQueryResponse.results.bindings.nonEmpty) {
      val returnedUserIri = userDataQueryResponse.getFirstRow.rowMap("s")

      val groupedUserData: Map[String, Seq[String]] =
        userDataQueryResponse.results.bindings.groupBy(_.rowMap("p")).map { case (predicate, rows) =>
          predicate -> rows.map(_.rowMap("o"))
        }

      val userDataV1 = UserDataV1(
        lang = groupedUserData.get(OntologyConstants.KnoraAdmin.PreferredLanguage) match {
          case Some(langList) => langList.head
          case None           => appConfig.fallbackLanguage
        },
        user_id = Some(returnedUserIri),
        email = groupedUserData.get(OntologyConstants.KnoraAdmin.Email).map(_.head),
        firstname = groupedUserData.get(OntologyConstants.KnoraAdmin.GivenName).map(_.head),
        lastname = groupedUserData.get(OntologyConstants.KnoraAdmin.FamilyName).map(_.head),
        password = groupedUserData.get(OntologyConstants.KnoraAdmin.Password).map(_.head),
        status = groupedUserData.get(OntologyConstants.KnoraAdmin.Status).map(_.head.toBoolean)
      )

      /* the projects the user is member of */
      val projectIris: Seq[IRI] = groupedUserData.get(OntologyConstants.KnoraAdmin.IsInProject) match {
        case Some(projects) => projects
        case None           => Seq.empty[IRI]
      }

      /* the groups the user is member of (only explicit groups) */
      val groupIris = groupedUserData.get(OntologyConstants.KnoraAdmin.IsInGroup) match {
        case Some(groups) => groups
        case None         => Seq.empty[IRI]
      }
      /* the projects for which the user is implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group */
      val isInProjectAdminGroups =
        groupedUserData.getOrElse(OntologyConstants.KnoraAdmin.IsInProjectAdminGroup, Vector.empty[IRI])

      /* is the user implicitly considered a member of the 'http://www.knora.org/ontology/knora-base#SystemAdmin' group */
      val isInSystemAdminGroup =
        groupedUserData.get(OntologyConstants.KnoraAdmin.IsInSystemAdminGroup).exists(p => p.head.toBoolean)

      for {
        /* get the user's permission profile from the permissions responder */
        permissionData <-
          messageRelay
            .ask[PermissionsDataADM](
              PermissionDataGetADM(
                projectIris = projectIris,
                groupIris = groupIris,
                isInProjectAdminGroups = isInProjectAdminGroups,
                isInSystemAdminGroup = isInSystemAdminGroup,
                requestingUser = KnoraSystemInstances.Users.SystemUser
              )
            )

        maybeProjectInfoFutures: Seq[Task[Option[ProjectInfoV1]]] =
          projectIris.map { projectIri =>
            messageRelay
              .ask(
                ProjectInfoByIRIGetV1(
                  iri = projectIri,
                  userProfileV1 = None
                )
              )
          }

        maybeProjectInfos                      <- ZIO.collectAll(maybeProjectInfoFutures)
        projectInfos                            = maybeProjectInfos.flatten
        projectInfoMap: Map[IRI, ProjectInfoV1] = projectInfos.map(projectInfo => projectInfo.id -> projectInfo).toMap

        /* construct the user profile from the different parts */
        up = UserProfileV1(
               userData = userDataV1,
               groups = groupIris,
               projects_info = projectInfoMap,
               sessionId = None,
               permissionData = permissionData
             )

        result: Option[UserProfileV1] = Some(up)
      } yield result

    } else {
      ZIO.succeed(None)
    }

  /**
   * Helper method for checking if a user exists.
   *
   * @param userIri the IRI of the user.
   * @return a [[Boolean]].
   */
  def userExists(userIri: IRI): Task[Boolean] =
    for {
      askString <- ZIO.attempt(
                     org.knora.webapi.messages.twirl.queries.sparql.v1.txt.checkUserExists(userIri = userIri).toString
                   )

      checkUserExistsResponse <- triplestoreService.sparqlHttpAsk(askString)
      result                   = checkUserExistsResponse.result

    } yield result

  /**
   * Writes the user profile to cache.
   *
   * @param userProfile a [[UserProfileV1]].
   * @return true if writing was successful.
   */
  private def writeUserProfileV1ToCache(userProfile: UserProfileV1): Task[Unit] = for {
    iri <- ZIO
             .fromOption(userProfile.userData.user_id)
             .orElseFail(ApplicationCacheException("A user profile without an IRI is invalid. Not writing to cache."))

    email <-
      ZIO
        .fromOption(userProfile.userData.email)
        .orElseFail(ApplicationCacheException("A user profile without an email is invalid. Not writing to cache."))

    _ = CacheUtil.put(USER_PROFILE_CACHE_NAME, iri, userProfile)
    _ <- ZIO
           .fail(ApplicationCacheException("Writing the user's profile to cache was not successful."))
           .when(CacheUtil.get(USER_PROFILE_CACHE_NAME, iri).isEmpty)

    _ = CacheUtil.put(USER_PROFILE_CACHE_NAME, email, userProfile)
    _ <- ZIO
           .fail(ApplicationCacheException("Writing the user's profile to cache was not successful."))
           .when(CacheUtil.get(USER_PROFILE_CACHE_NAME, email).isEmpty)
  } yield ()
}

object UsersResponderV1Live {
  val layer: URLayer[AppConfig with MessageRelay with TriplestoreService, UsersResponderV1] = ZLayer.fromZIO {
    for {
      ac      <- ZIO.service[AppConfig]
      mr      <- ZIO.service[MessageRelay]
      ts      <- ZIO.service[TriplestoreService]
      handler <- mr.subscribe(UsersResponderV1Live(ac, mr, ts))
    } yield handler
  }
}
