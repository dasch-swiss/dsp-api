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

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.groupsmessages.{GroupADM, GroupGetADM}
import org.knora.webapi.messages.admin.responder.permissionsmessages
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.responders.admin.PermissionsResponderADM._
import org.knora.webapi.util.{CacheUtil, KnoraIdUtil, PermissionUtilADM}

import scala.collection.immutable.Iterable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future


/**
  * Provides information about Knora users to other responders.
  */
class PermissionsResponderADM(system: ActorSystem, applicationStateActor: ActorRef, responderManager: ActorRef, storeManager: ActorRef) extends Responder(system, applicationStateActor, responderManager, storeManager) {


    // Creates IRIs for new Knora user objects.
    private val knoraIdUtil = new KnoraIdUtil

    /* Entity types used to more clearly distinguish what kind of entity is meant */
    private val ResourceEntityType = "resource"
    private val PropertyEntityType = "property"

    /**
      * Receives a message extending [[PermissionsResponderRequestADM]], and returns an appropriate response message.
      */
    def receive(msg: PermissionsResponderRequestADM) = msg match {
        case PermissionDataGetADM(projectIris, groupIris, isInProjectAdminGroup, isInSystemAdminGroup, requestingUser) => permissionsDataGetADM(projectIris, groupIris, isInProjectAdminGroup, isInSystemAdminGroup, requestingUser)
        case AdministrativePermissionsForProjectGetRequestADM(projectIri, requestingUser) => administrativePermissionsForProjectGetRequestADM(projectIri, requestingUser)
        case AdministrativePermissionForIriGetRequestADM(administrativePermissionIri, requestingUser) => administrativePermissionForIriGetRequestADM(administrativePermissionIri, requestingUser)
        case AdministrativePermissionForProjectGroupGetADM(projectIri, groupIri, requestingUser) => administrativePermissionForProjectGroupGetADM(projectIri, groupIri, requestingUser)
        case AdministrativePermissionForProjectGroupGetRequestADM(projectIri, groupIri, requestingUser) => administrativePermissionForProjectGroupGetRequestADM(projectIri, groupIri, requestingUser)
        case AdministrativePermissionCreateRequestADM(newAdministrativePermission, requestingUser) => administrativePermissionCreateRequestADM(newAdministrativePermission, requestingUser)
        //case AdministrativePermissionDeleteRequestV1(administrativePermissionIri, requestingUser) => deleteAdministrativePermissionV1(administrativePermissionIri, requestingUser)
        case ObjectAccessPermissionsForResourceGetADM(resourceIri, requestingUser) => objectAccessPermissionsForResourceGetADM(resourceIri, requestingUser)
        case ObjectAccessPermissionsForValueGetADM(valueIri, requestingUser) => objectAccessPermissionsForValueGetADM(valueIri, requestingUser)
        case DefaultObjectAccessPermissionsForProjectGetRequestADM(projectIri, requestingUser) => defaultObjectAccessPermissionsForProjectGetRequestADM(projectIri, requestingUser)
        case DefaultObjectAccessPermissionForIriGetRequestADM(defaultObjectAccessPermissionIri, requestingUser) => defaultObjectAccessPermissionForIriGetRequestADM(defaultObjectAccessPermissionIri, requestingUser)
        case DefaultObjectAccessPermissionGetRequestADM(projectIri, groupIri, resourceClassIri, propertyIri, requestingUser) => defaultObjectAccessPermissionGetRequestADM(projectIri, groupIri, resourceClassIri, propertyIri, requestingUser)
        case DefaultObjectAccessPermissionsStringForResourceClassGetADM(projectIri, resourceClassIri, targetUser, requestingUser) => defaultObjectAccessPermissionsStringForEntityGetADM(projectIri, resourceClassIri, None, ResourceEntityType, targetUser, requestingUser)
        case DefaultObjectAccessPermissionsStringForPropertyGetADM(projectIri, resourceClassIri, propertyTypeIri, targetUser, requestingUser) => defaultObjectAccessPermissionsStringForEntityGetADM(projectIri, resourceClassIri, Some(propertyTypeIri), PropertyEntityType, targetUser, requestingUser)
        //case DefaultObjectAccessPermissionCreateRequestV1(newDefaultObjectAccessPermissionV1, requestingUser) => createDefaultObjectAccessPermissionV1(newDefaultObjectAccessPermissionV1, requestingUser)
        //case DefaultObjectAccessPermissionDeleteRequestV1(defaultObjectAccessPermissionIri, requestingUser) => deleteDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri, requestingUser)
        //case TemplatePermissionsCreateRequestV1(projectIri, permissionsTemplate, requestingUser) => templatePermissionsCreateRequestV1(projectIri, permissionsTemplate, requestingUser)
        case other => handleUnexpectedMessage(other, log, this.getClass.getName)
    }


    ///////////////////////////////////////////////////////////////////////////
    // PERMISSION DATA
    ///////////////////////////////////////////////////////////////////////////

    /**
      * Creates the user's [[PermissionsDataADM]]
      *
      * @param projectIris            the projects the user is part of.
      * @param groupIris              the groups the user is member of (without ProjectMember, ProjectAdmin, SystemAdmin)
      * @param isInProjectAdminGroups the projects in which the user is member of the ProjectAdmin group.
      * @param isInSystemAdminGroup   the flag denoting membership in the SystemAdmin group.
      * @return
      */
    private def permissionsDataGetADM(projectIris: Seq[IRI], groupIris: Seq[IRI], isInProjectAdminGroups: Seq[IRI], isInSystemAdminGroup: Boolean, requestingUser: UserADM): Future[PermissionsDataADM] = {
        // find out which project each group belongs to
        //_ = log.debug("getPermissionsProfileV1 - find out to which project each group belongs to")


        if (!requestingUser.isSystemUser) {
            FastFuture.failed(throw ForbiddenException("Request only allowed for SystemUser."))
        }

        val groupFutures: Seq[Future[(IRI, IRI)]] = if (groupIris.nonEmpty) {
            groupIris.map {
                groupIri =>
                    for {
                        maybeGroup <- (responderManager ? GroupGetADM(groupIri, KnoraSystemInstances.Users.SystemUser)).mapTo[Option[GroupADM]]
                        group = maybeGroup.getOrElse(throw InconsistentTriplestoreDataException(s"Cannot find information for group: '$groupIri'. Please report as possible bug."))
                        res = (group.project.id, groupIri)
                    } yield res
            }
        } else {
            Seq.empty[Future[(IRI, IRI)]]
        }

        val groupsFuture: Future[Seq[(IRI, IRI)]] = Future.sequence(groupFutures).map(_.toSeq)

        for {
            groups: Seq[(IRI, IRI)] <- groupsFuture
            //_ = log.debug(s"permissionsProfileGetV1 - groups: {}", MessageUtil.toSource(groups))

            /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#ProjectMember' group for each project */
            projectMembers: Seq[(IRI, IRI)] = if (projectIris.nonEmpty) {
                for {
                    projectIri <- projectIris.toVector
                    res = (projectIri, OntologyConstants.KnoraBase.ProjectMember)
                } yield res
            } else {
                Seq.empty[(IRI, IRI)]
            }
            //_ = log.debug(s"permissionsProfileGetV1 - projectMembers: {}", MessageUtil.toSource(projectMembers))


            /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group for each project */
            projectAdmins: Seq[(IRI, IRI)] = if (projectIris.nonEmpty) {
                for {
                    projectAdminForGroup <- isInProjectAdminGroups.toSeq
                    res = (projectAdminForGroup, OntologyConstants.KnoraBase.ProjectAdmin)
                } yield res
            } else {
                Seq.empty[(IRI, IRI)]
            }
            //_ = log.debug("permissionsProfileGetV1 - projectAdmins: {}", MessageUtil.toSource(projectAdmins))

            /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#SystemAdmin' group */
            systemAdmin: Seq[(IRI, IRI)] = if (isInSystemAdminGroup) {
                Seq((OntologyConstants.KnoraBase.SystemProject, OntologyConstants.KnoraBase.SystemAdmin))
            } else {
                Seq.empty[(IRI, IRI)]
            }
            //_ = log.debug(s"permissionsProfileGetV1 - systemAdmin: {}", MessageUtil.toSource(systemAdmin))

            /* combine explicit groups with materialized implicit groups */
            /* here we don't add the KnownUser group, as this would inflate the whole thing. */
            /* we instead inject the relevant information in defaultObjectAccessPermissionsStringForEntityGetV1 */
            allGroups = groups ++ projectMembers ++ projectAdmins ++ systemAdmin
            groupsPerProject = allGroups.groupBy(_._1).map { case (k, v) => (k, v.map(_._2)) }
            // _ = log.debug(s"permissionsProfileGetV1 - groupsPerProject: {}", MessageUtil.toSource(groupsPerProject))

            /* retrieve the administrative permissions for each group per project the user is member of */
            administrativePermissionsPerProjectFuture: Future[Map[IRI, Set[PermissionADM]]] = if (projectIris.nonEmpty) {
                userAdministrativePermissionsGetADM(groupsPerProject)
            } else {
                Future(Map.empty[IRI, Set[PermissionADM]])
            }
            administrativePermissionsPerProject <- administrativePermissionsPerProjectFuture

            /* construct the permission profile from the different parts */
            result = PermissionsDataADM(
                groupsPerProject = groupsPerProject,
                administrativePermissionsPerProject = administrativePermissionsPerProject
            )
            //_ = log.debug(s"permissionsDataGetV1 - resulting permissionData: {}", result)

        } yield result
    }

    /**
      * By providing all the projects and groups in which the user is a member of, calculate the user's
      * administrative permissions of each project by applying the precedence rules.
      *
      * @param groupsPerProject the groups inside each project the user is member of.
      * @return a the user's resulting set of administrative permissions for each project.
      */
    private def userAdministrativePermissionsGetADM(groupsPerProject: Map[IRI, Seq[IRI]]): Future[Map[IRI, Set[PermissionADM]]] = {


        /* Get all permissions per project, applying permission precedence rule */
        def calculatePermission(projectIri: IRI, extendedUserGroups: Seq[IRI]): Future[(IRI, Set[PermissionADM])] = {


            /* List buffer holding default object access permissions tagged with the precedence level:
               1. ProjectAdmin > 2. CustomGroups > 3. ProjectMember > 4. KnownUser
               Permissions are added following the precedence level from the highest to the lowest. As soon as one set
               of permissions is written into the buffer, any additionally found permissions are ignored. */
            val permissionsListBuffer = ListBuffer.empty[(String, Set[PermissionADM])]

            for {
                /* Get administrative permissions for the knora-base:ProjectAdmin group */
                administrativePermissionsOnProjectAdminGroup: Set[PermissionADM] <- administrativePermissionForGroupsGeADM(projectIri, List(OntologyConstants.KnoraBase.ProjectAdmin))
                _ = if (administrativePermissionsOnProjectAdminGroup.nonEmpty) {
                    if (extendedUserGroups.contains(OntologyConstants.KnoraBase.ProjectAdmin)) {
                        permissionsListBuffer += (("ProjectAdmin", administrativePermissionsOnProjectAdminGroup))
                    }
                }
                //_ = log.debug(s"userAdministrativePermissionsGetV1 - project: $projectIri, administrativePermissionsOnProjectAdminGroup: $administrativePermissionsOnProjectAdminGroup")


                /* Get administrative permissions for custom groups (all groups other than the built-in groups) */
                administrativePermissionsOnCustomGroups: Set[PermissionADM] <- {
                    val customGroups = extendedUserGroups diff List(OntologyConstants.KnoraBase.KnownUser, OntologyConstants.KnoraBase.ProjectMember, OntologyConstants.KnoraBase.ProjectAdmin)
                    if (customGroups.nonEmpty) {
                        administrativePermissionForGroupsGeADM(projectIri, customGroups)
                    } else {
                        Future(Set.empty[PermissionADM])
                    }
                }
                _ = if (administrativePermissionsOnCustomGroups.nonEmpty) {
                    if (permissionsListBuffer.isEmpty) {
                        permissionsListBuffer += (("CustomGroups", administrativePermissionsOnCustomGroups))
                    }
                }
                //_ = log.debug(s"userAdministrativePermissionsGetV1 - project: $projectIri, administrativePermissionsOnCustomGroups: $administrativePermissionsOnCustomGroups")


                /* Get administrative permissions for the knora-base:ProjectMember group */
                administrativePermissionsOnProjectMemberGroup: Set[PermissionADM] <- administrativePermissionForGroupsGeADM(projectIri, List(OntologyConstants.KnoraBase.ProjectMember))
                _ = if (administrativePermissionsOnProjectMemberGroup.nonEmpty) {
                    if (permissionsListBuffer.isEmpty) {
                        if (extendedUserGroups.contains(OntologyConstants.KnoraBase.ProjectMember)) {
                            permissionsListBuffer += (("ProjectMember", administrativePermissionsOnProjectMemberGroup))
                        }
                    }
                }
                //_ = log.debug(s"userAdministrativePermissionsGetV1 - project: $projectIri, administrativePermissionsOnProjectMemberGroup: $administrativePermissionsOnProjectMemberGroup")


                /* Get administrative permissions for the knora-base:KnownUser group */
                administrativePermissionsOnKnownUserGroup: Set[PermissionADM] <- administrativePermissionForGroupsGeADM(projectIri, List(OntologyConstants.KnoraBase.KnownUser))
                _ = if (administrativePermissionsOnKnownUserGroup.nonEmpty) {
                    if (permissionsListBuffer.isEmpty) {
                        if (extendedUserGroups.contains(OntologyConstants.KnoraBase.KnownUser)) {
                            permissionsListBuffer += (("KnownUser", administrativePermissionsOnKnownUserGroup))
                        }
                    }
                }
                //_ = log.debug(s"userAdministrativePermissionsGetV1 - project: $projectIri, administrativePermissionsOnKnownUserGroup: $administrativePermissionsOnKnownUserGroup")


                projectAdministrativePermissions: (IRI, Set[PermissionADM]) = permissionsListBuffer.length match {
                    case 1 => {
                        log.debug(s"userAdministrativePermissionsGetV1 - project: $projectIri, precedence: ${permissionsListBuffer.head._1}, administrativePermissions: ${permissionsListBuffer.head._2}")
                        (projectIri, permissionsListBuffer.head._2)
                    }
                    case 0 => (projectIri, Set.empty[PermissionADM])
                    case _ => throw AssertionException("The permissions list buffer holding default object permissions should never be larger then 1.")
                }

            } yield projectAdministrativePermissions
        }

        val permissionsPerProject: Iterable[Future[(IRI, Set[PermissionADM])]] = for {
            (projectIri, groups) <- groupsPerProject

            /* Explicitly add 'KnownUser' group */
            extendedUserGroups = OntologyConstants.KnoraBase.KnownUser ++ groups

            result = calculatePermission(projectIri, groups)

        } yield result

        val result: Future[Map[IRI, Set[PermissionADM]]] = Future.sequence(permissionsPerProject).map(_.toMap)

        log.debug(s"userAdministrativePermissionsGetV1 - result: $result")
        result
    }


    /** ***********************************************************************/
    /* ADMINISTRATIVE PERMISSIONS                                            */
    /** ***********************************************************************/

    /**
      * Convenience method returning a set with combined administrative permission. Used in userAdministrativePermissionsGetV1.
      *
      * @param projectIri the IRI of the project.
      * @param groups     the list of groups for which administrative permissions are retrieved and combined.
      * @return a set of [[PermissionADM]].
      */
    private def administrativePermissionForGroupsGeADM(projectIri: IRI, groups: Seq[IRI]): Future[Set[PermissionADM]] = {

        /* Get administrative permissions for each group and combine them */
        val gpf: Seq[Future[Seq[PermissionADM]]] = for {
            groupIri <- groups
            //_ = log.debug(s"administrativePermissionForGroupsGetADM - projectIri: $projectIri, groupIri: $groupIri")

            groupPermissions: Future[Seq[PermissionADM]] = administrativePermissionForProjectGroupGetADM(projectIri, groupIri, requestingUser = KnoraSystemInstances.Users.SystemUser).map {
                case Some(ap: AdministrativePermissionADM) => ap.hasPermissions.toSeq
                case None => Seq.empty[PermissionADM]
            }

        } yield groupPermissions

        val allPermissionsFuture: Future[Seq[Seq[PermissionADM]]] = Future.sequence(gpf)

        /* combines all permissions for each group and removes duplicates  */
        val result: Future[Set[PermissionADM]] = for {
            allPermissions: Seq[Seq[PermissionADM]] <- allPermissionsFuture

            // remove instances with empty PermissionV1 sets
            cleanedAllPermissions: Seq[Seq[PermissionADM]] = allPermissions.filter(_.nonEmpty)

            /* Combine permission sequences */
            combined = cleanedAllPermissions.foldLeft(Seq.empty[PermissionADM]) { (acc, seq) =>
                acc ++ seq
            }
            /* Remove possible duplicate permissions */
            result: Set[PermissionADM] = PermissionUtilADM.removeDuplicatePermissions(combined)

            //_ = log.debug(s"administrativePermissionForGroupsGetADM - result: $result")
        } yield result
        result
    }

    /**
      * Gets all administrative permissions defined inside a project.
      *
      * @param projectIRI     the IRI of the project.
      * @param requestingUser the [[UserADM]] of the requesting user.
      * @return a list of IRIs of [[AdministrativePermissionADM]] objects.
      */
    private def administrativePermissionsForProjectGetRequestADM(projectIRI: IRI, requestingUser: UserADM): Future[AdministrativePermissionsForProjectGetResponseADM] = {

        // FIXME: Check user's permission for operation (issue #370)

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getAdministrativePermissionsForProject(
                triplestore = settings.triplestoreType,
                projectIri = projectIRI
            ).toString())
            //_ = log.debug(s"administrativePermissionsForProjectGetRequestADM - query: $sparqlQueryString")

            permissionsQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - result: ${MessageUtil.toSource(permissionsQueryResponse)}")

            /* extract response rows */
            permissionsQueryResponseRows: Seq[VariableResultsRow] = permissionsQueryResponse.results.bindings

            permissionsWithProperties: Map[String, Map[String, String]] = permissionsQueryResponseRows.groupBy(_.rowMap("s")).map {
                case (permissionIri: String, rows: Seq[VariableResultsRow]) => (permissionIri, rows.map(row => (row.rowMap("p"), row.rowMap("o"))).toMap)
            }
            //_ = log.debug(s"administrativePermissionsForProjectGetRequestADM - permissionsWithProperties: $permissionsWithProperties")

            administrativePermissions: Seq[AdministrativePermissionADM] = permissionsWithProperties.map {
                case (permissionIri: IRI, propsMap: Map[String, String]) =>

                    /* parse permissions */
                    val hasPermissions: Set[PermissionADM] = PermissionUtilADM.parsePermissionsWithType(propsMap.get(OntologyConstants.KnoraBase.HasPermissions), PermissionType.AP)

                    /* construct permission object */
                    AdministrativePermissionADM(iri = permissionIri, forProject = propsMap.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Administrative Permission $permissionIri has no project attached.")), forGroup = propsMap.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Administrative Permission $permissionIri has no group attached.")), hasPermissions = hasPermissions)
            }.toSeq

            /* construct response object */
            response = permissionsmessages.AdministrativePermissionsForProjectGetResponseADM(administrativePermissions)

        } yield response
    }

    /**
      * Gets a single administrative permission identified by it's IRI.
      *
      * @param administrativePermissionIri the IRI of the administrative permission.
      * @param requestingUser              the requesting user.
      * @return a single [[AdministrativePermissionADM]] object.
      */
    private def administrativePermissionForIriGetRequestADM(administrativePermissionIri: IRI, requestingUser: UserADM): Future[AdministrativePermissionForIriGetResponseADM] = {

        // FIXME: Check user's permission for operation (issue #370)

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getAdministrativePermissionByIri(
                triplestore = settings.triplestoreType,
                administrativePermissionIri = administrativePermissionIri
            ).toString())
            //_ = log.debug(s"administrativePermissionForIriGetRequestV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getAdministrativePermissionForIriV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            /* extract response rows */
            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            //_ = log.debug(s"administrativePermissionForIriGetRequestV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

            /* check if we have found something */
            _ = if (groupedPermissionsQueryResponse.isEmpty) throw NotFoundException(s"Administrative permission $administrativePermissionIri could not be found.")

            /* extract the permission */
            hasPermissions = PermissionUtilADM.parsePermissionsWithType(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.AP)
            //_ = log.debug(s"administrativePermissionForIriGetRequestV1 - hasPermissions: ${MessageUtil.toSource(hasPermissions)}")

            /* construct the permission object */
            permission = permissionsmessages.AdministrativePermissionADM(iri = administrativePermissionIri, forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $administrativePermissionIri has no project attached")).head, forGroup = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Permission $administrativePermissionIri has no group attached")).head, hasPermissions = hasPermissions)

            /* construct the response object */
            response = AdministrativePermissionForIriGetResponseADM(permission)

        } yield response
    }

    /**
      * Gets a single administrative permission identified by project and group.
      *
      * @param projectIri the project.
      * @param groupIri   the group.
      * @return an option containing an [[AdministrativePermissionADM]]
      */
    private def administrativePermissionForProjectGroupGetADM(projectIri: IRI, groupIri: IRI, requestingUser: UserADM): Future[Option[AdministrativePermissionADM]] = {
        for {
            // check if necessary field are not empty.
            _ <- Future(if (projectIri.isEmpty) throw BadRequestException("Project cannot be empty"))
            _ = if (groupIri.isEmpty) throw BadRequestException("Group cannot be empty")

            sparqlQueryString <- Future(queries.sparql.v1.txt.getAdministrativePermissionForProjectAndGroup(
                triplestore = settings.triplestoreType,
                projectIri = projectIri,
                groupIri = groupIri
            ).toString())
            //_ = log.debug(s"administrativePermissionForProjectGroupGetADM - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"administrativePermissionForProjectGroupGetADM - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            permission: Option[AdministrativePermissionADM] = if (permissionQueryResponseRows.nonEmpty) {

                /* check if we only got one administrative permission back */
                val apCount: Int = permissionQueryResponseRows.groupBy(_.rowMap("s")).size
                if (apCount > 1) throw InconsistentTriplestoreDataException(s"Only one administrative permission instance allowed for project: $projectIri and group: $groupIri combination, but found $apCount.")

                /* get the iri of the retrieved permission */
                val returnedPermissionIri = permissionQueryResponse.getFirstRow.rowMap("s")

                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions = PermissionUtilADM.parsePermissionsWithType(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.AP)
                Some(
                    permissionsmessages.AdministrativePermissionADM(iri = returnedPermissionIri, forProject = projectIri, forGroup = groupIri, hasPermissions = hasPermissions)
                )
            } else {
                None
            }
            //_ = log.debug(s"administrativePermissionForProjectGroupGetADM - projectIri: $projectIRI, groupIri: $groupIRI, administrativePermission: $permission")
        } yield permission
    }

    /**
      * Gets a single administrative permission identified by project and group.
      *
      * @param projectIri the project.
      * @param groupIri   the group.
      * @return an [[AdministrativePermissionForProjectGroupGetResponseADM]]
      */
    private def administrativePermissionForProjectGroupGetRequestADM(projectIri: IRI, groupIri: IRI, requestingUser: UserADM): Future[AdministrativePermissionForProjectGroupGetResponseADM] = {

        // FIXME: Check user's permission for operation (issue #370)

        for {
            ap <- administrativePermissionForProjectGroupGetADM(projectIri, groupIri, requestingUser = KnoraSystemInstances.Users.SystemUser)
            result = ap match {
                case Some(ap) => permissionsmessages.AdministrativePermissionForProjectGroupGetResponseADM(ap)
                case None => throw NotFoundException(s"No Administrative Permission found for project: $projectIri, group: $groupIri combination")
            }
        } yield result
    }

    /**
      *
      * @param newAdministrativePermissionV1
      * @param userProfileV1
      * @return
      */
    private def administrativePermissionCreateRequestADM(newAdministrativePermissionV1: NewAdministrativePermissionADM, userProfileV1: UserADM): Future[AdministrativePermissionCreateResponseADM] = {
        //log.debug("administrativePermissionCreateRequestADM")

        // FIXME: Check user's permission for operation (issue #370)

        for {
            _ <- Future {
                // check if necessary field are not empty.
                if (newAdministrativePermissionV1.forProject.isEmpty) throw BadRequestException("Project cannot be empty")
                if (newAdministrativePermissionV1.forGroup.isEmpty) throw BadRequestException("Group cannot be empty")
                if (newAdministrativePermissionV1.hasNewPermissions.isEmpty) throw BadRequestException("New permissions cannot be empty")
            }

            checkResult <- administrativePermissionForProjectGroupGetADM(newAdministrativePermissionV1.forProject, newAdministrativePermissionV1.forGroup, requestingUser = KnoraSystemInstances.Users.SystemUser)

            _ = checkResult match {
                case Some(ap) => throw DuplicateValueException(s"Permission for project: '${newAdministrativePermissionV1.forProject}' and group: '${newAdministrativePermissionV1.forGroup}' combination already exists.")
                case None => ()
            }

            // FIXME: Implement
            response = AdministrativePermissionCreateResponseADM(administrativePermission = AdministrativePermissionADM(iri = "mock-iri", forProject = "mock-project", forGroup = "mock-group", hasPermissions = Set.empty[PermissionADM]))

        } yield response


    }

    /*
        private def deleteAdministrativePermissionV1(administrativePermissionIri: IRI, userProfileV1: UserProfileV1): Future[AdministrativePermissionOperationResponseV1] = ???
    */

    ///////////////////////////////////////////////////////////////////////////
    // OBJECT ACCESS PERMISSIONS
    ///////////////////////////////////////////////////////////////////////////

    /**
      * Gets all permissions attached to the resource.
      *
      * @param resourceIri the IRI of the resource.
      * @return a sequence of [[PermissionADM]]
      */
    private def objectAccessPermissionsForResourceGetADM(resourceIri: IRI, requestingUser: UserADM): Future[Option[ObjectAccessPermissionADM]] = {
        log.debug(s"objectAccessPermissionsForResourceGetV1 - resourceIRI: $resourceIri")
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getObjectAccessPermission(
                triplestore = settings.triplestoreType,
                resourceIri = Some(resourceIri),
                valueIri = None
            ).toString())
            //_ = log.debug(s"objectAccessPermissionsForResourceGetV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"objectAccessPermissionsForResourceGetV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            permission: Option[ObjectAccessPermissionADM] = if (permissionQueryResponseRows.nonEmpty) {

                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions: Set[PermissionADM] = PermissionUtilADM.parsePermissionsWithType(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.OAP)
                Some(
                    ObjectAccessPermissionADM(forResource = Some(resourceIri), forValue = None, hasPermissions = hasPermissions)
                )
            } else {
                None
            }
            _ = log.debug(s"objectAccessPermissionsForResourceGetV1 - permission: $permission")
        } yield permission
    }

    /**
      * Gets all permissions attached to the value.
      *
      * @param valueIri the IRI of the value.
      * @return a sequence of [[PermissionADM]]
      */
    private def objectAccessPermissionsForValueGetADM(valueIri: IRI, requestingUser: UserADM): Future[Option[ObjectAccessPermissionADM]] = {
        log.debug(s"objectAccessPermissionsForValueGetV1 - resourceIRI: $valueIri")
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getObjectAccessPermission(
                triplestore = settings.triplestoreType,
                resourceIri = None,
                valueIri = Some(valueIri)
            ).toString())
            //_ = log.debug(s"objectAccessPermissionsForValueGetV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"objectAccessPermissionsForValueGetV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            permission: Option[ObjectAccessPermissionADM] = if (permissionQueryResponseRows.nonEmpty) {

                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions: Set[PermissionADM] = PermissionUtilADM.parsePermissionsWithType(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.OAP)
                Some(
                    ObjectAccessPermissionADM(forResource = None, forValue = Some(valueIri), hasPermissions = hasPermissions)
                )
            } else {
                None
            }
            _ = log.debug(s"objectAccessPermissionsForValueGetV1 - permission: $permission")
        } yield permission
    }


    ///////////////////////////////////////////////////////////////////////////
    // DEFAULT OBJECT ACCESS PERMISSIONS
    ///////////////////////////////////////////////////////////////////////////

    /**
      * Gets all IRI's of all default object access permissions defined inside a project.
      *
      * @param projectIri     the IRI of the project.
      * @param requestingUser the [[UserADM]] of the requesting user.
      * @return a list of IRIs of [[DefaultObjectAccessPermissionADM]] objects.
      */
    private def defaultObjectAccessPermissionsForProjectGetRequestADM(projectIri: IRI, requestingUser: UserADM): Future[DefaultObjectAccessPermissionsForProjectGetResponseADM] = {

        // FIXME: Check user's permission for operation (issue #370)

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getDefaultObjectAccessPermissionsForProject(
                triplestore = settings.triplestoreType,
                projectIri = projectIri
            ).toString())
            //_ = log.debug(s"defaultObjectAccessPermissionsForProjectGetRequestADM - query: $sparqlQueryString")

            permissionsQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"defaultObjectAccessPermissionsForProjectGetRequestADM - result: ${MessageUtil.toSource(permissionsQueryResponse)}")

            /* extract response rows */
            permissionsQueryResponseRows: Seq[VariableResultsRow] = permissionsQueryResponse.results.bindings

            permissionsWithProperties: Map[String, Map[String, String]] = permissionsQueryResponseRows.groupBy(_.rowMap("s")).map {
                case (permissionIri: String, rows: Seq[VariableResultsRow]) => (permissionIri, rows.map(row => (row.rowMap("p"), row.rowMap("o"))).toMap)
            }
            //_ = log.debug(s"defaultObjectAccessPermissionsForProjectGetRequestADM - permissionsWithProperties: $permissionsWithProperties")

            permissions: Seq[DefaultObjectAccessPermissionADM] = permissionsWithProperties.map {
                case (permissionIri: IRI, propsMap: Map[String, String]) =>

                    /* parse permissions */
                    val hasPermissions: Set[PermissionADM] = PermissionUtilADM.parsePermissionsWithType(propsMap.get(OntologyConstants.KnoraBase.HasPermissions), PermissionType.OAP)

                    /* construct permission object */
                    DefaultObjectAccessPermissionADM(iri = permissionIri, forProject = propsMap.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no project.")), forGroup = propsMap.get(OntologyConstants.KnoraBase.ForGroup), forResourceClass = propsMap.get(OntologyConstants.KnoraBase.ForResourceClass), forProperty = propsMap.get(OntologyConstants.KnoraBase.ForProperty), hasPermissions = hasPermissions)
            }.toSeq

            /* construct response object */
            response = DefaultObjectAccessPermissionsForProjectGetResponseADM(permissions)

        } yield response

    }

    /**
      * Gets a single default object access permission identified by its IRI.
      *
      * @param permissionIri the IRI of the default object access permission.
      * @return a single [[DefaultObjectAccessPermissionADM]] object.
      */
    private def defaultObjectAccessPermissionForIriGetRequestADM(permissionIri: IRI, requestingUser: UserADM): Future[DefaultObjectAccessPermissionForIriGetResponseADM] = {

        // FIXME: Check user's permission for operation (issue #370)

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getDefaultObjectAccessPermissionByIri(
                triplestore = settings.triplestoreType,
                defaultObjectAccessPermissionIri = permissionIri
            ).toString())
            //_ = log.debug(s"defaultObjectAccessPermissionForIriGetRequestADM - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"defaultObjectAccessPermissionForIriGetRequestADM - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            _ = if (permissionQueryResponseRows.isEmpty) {
                throw NotFoundException(s"Permission '$permissionIri' not found")
            }

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }
            //_ = log.debug(s"defaultObjectAccessPermissionForIriGetRequestADM - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

            hasPermissions = PermissionUtilADM.parsePermissionsWithType(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.OAP)

            defaultObjectAccessPermission = permissionsmessages.DefaultObjectAccessPermissionADM(iri = permissionIri, forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no project.")).head, forGroup = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForGroup).map(_.head), forResourceClass = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForResourceClass).map(_.head), forProperty = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForProperty).map(_.head), hasPermissions = hasPermissions)

            result = DefaultObjectAccessPermissionForIriGetResponseADM(defaultObjectAccessPermission)

        } yield result
    }

    /**
      * Gets a single default object access permission identified by project and either:
      * - group
      * - resource class
      * - resource class and property
      * - property
      *
      * @param projectIri       the project's IRI.
      * @param groupIri         the group's IRI.
      * @param resourceClassIri the resource's class IRI
      * @param propertyIri      the property's IRI.
      * @return an optional [[DefaultObjectAccessPermissionADM]]
      */
    private def defaultObjectAccessPermissionGetADM(projectIri: IRI, groupIri: Option[IRI], resourceClassIri: Option[IRI], propertyIri: Option[IRI]): Future[Option[DefaultObjectAccessPermissionADM]] = {

        val key = getDefaultObjectAccessPermissionADMKey(projectIri, groupIri, resourceClassIri, propertyIri)
        val permissionFromCache = CacheUtil.get[DefaultObjectAccessPermissionADM](PermissionsCacheName, key)

        val maybeDefaultObjectAccessPermissionADM: Future[Option[DefaultObjectAccessPermissionADM]] = permissionFromCache match {
            case Some(permission) =>
                // found permission in the cache
                log.debug("defaultObjectAccessPermissionGetADM - cache hit for key: {}", key)
                FastFuture.successful(Some(permission))

            case None =>
                // not found permission in the cache

                for {
                    // check if necessary field are not empty.
                    _ <- Future(if (projectIri.isEmpty) throw BadRequestException("Project cannot be empty"))

                    /* check supplied parameters */
                    _ = if (groupIri.isDefined && resourceClassIri.isDefined) throw BadRequestException("Not allowed to supply groupIri and resourceClassIri together")
                    _ = if (groupIri.isDefined && propertyIri.isDefined) throw BadRequestException("Not allowed to supply groupIri and propertyIri together")

                    sparqlQueryString = queries.sparql.v1.txt.getDefaultObjectAccessPermission(
                        triplestore = settings.triplestoreType,
                        projectIri = projectIri,
                        maybeGroupIri = groupIri,
                        maybeResourceClassIri = resourceClassIri,
                        maybePropertyIri = propertyIri
                    ).toString()
                    // _ = log.debug(s"defaultObjectAccessPermissionGetADM - query: $sparqlQueryString")

                    permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
                    // _ = log.debug(s"defaultObjectAccessPermissionGetADM - result: ${MessageUtil.toSource(permissionQueryResponse)}")

                    permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

                    permission: Option[DefaultObjectAccessPermissionADM] = if (permissionQueryResponseRows.nonEmpty) {

                        /* check if we only got one default object access permission back */
                        val doapCount: Int = permissionQueryResponseRows.groupBy(_.rowMap("s")).size
                        if (doapCount > 1) throw InconsistentTriplestoreDataException(s"Only one default object permission instance allowed for project: $projectIri and combination of group: $groupIri, resourceClass: $resourceClassIri, property: $propertyIri combination, but found: $doapCount.")

                        /* get the iri of the retrieved permission */
                        val permissionIri = permissionQueryResponse.getFirstRow.rowMap("s")

                        val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                            case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                        }
                        val hasPermissions: Set[PermissionADM] = PermissionUtilADM.parsePermissionsWithType(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.OAP)
                        val doap: DefaultObjectAccessPermissionADM = DefaultObjectAccessPermissionADM(iri = permissionIri, forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission has no project.")).head, forGroup = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForGroup).map(_.head), forResourceClass = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForResourceClass).map(_.head), forProperty = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForProperty).map(_.head), hasPermissions = hasPermissions)

                        // write permission to cache
                        writeDefaultObjectAccessPermissionADMToCache(doap)

                        Some(doap)
                    } else {
                        None
                    }
                    _ = log.debug(s"defaultObjectAccessPermissionGetADM - p: $projectIri, g: $groupIri, r: $resourceClassIri, p: $propertyIri, permission: $permission")
                } yield permission
        }

        // _ = log.debug("defaultObjectAccessPermissionGetADM - permission: {}", MessageUtil.toSource(maybeDefaultObjectAccessPermissionADM))
        maybeDefaultObjectAccessPermissionADM
    }

    /**
      * Gets a single default object access permission identified by project and either group / resource class / property.
      * In the case of properties, an additional check is performed against the 'SystemProject', as some 'knora-base'
      * properties can carry default object access permissions. Note that default access permissions defined for a system
      * property inside the 'SystemProject' can be overridden by defining them for its own project.
      *
      * @param projectIri
      * @param groupIri
      * @param resourceClassIri
      * @param propertyIri
      * @param requestingUser
      * @return a [[DefaultObjectAccessPermissionGetResponseADM]]
      */
    private def defaultObjectAccessPermissionGetRequestADM(projectIri: IRI, groupIri: Option[IRI], resourceClassIri: Option[IRI], propertyIri: Option[IRI], requestingUser: UserADM): Future[DefaultObjectAccessPermissionGetResponseADM] = {

        // FIXME: Check user's permission for operation (issue #370)

        defaultObjectAccessPermissionGetADM(projectIri, groupIri, resourceClassIri, propertyIri)
                .mapTo[Option[DefaultObjectAccessPermissionADM]]
                .flatMap {
                    case Some(doap) => Future(DefaultObjectAccessPermissionGetResponseADM(doap))
                    case None => {
                        /* if the query was for a property, then we need to additionally check if it is a system property */
                        if (propertyIri.isDefined) {
                            val systemProject = OntologyConstants.KnoraBase.SystemProject
                            val doapF = defaultObjectAccessPermissionGetADM(systemProject, groupIri, resourceClassIri, propertyIri)
                            doapF.mapTo[Option[DefaultObjectAccessPermissionADM]].map {
                                case Some(systemDoap) => DefaultObjectAccessPermissionGetResponseADM(systemDoap)
                                case None => throw NotFoundException(s"No Default Object Access Permission found for project: $projectIri, group: $groupIri, resourceClassIri: $resourceClassIri, propertyIri: $propertyIri combination")
                            }
                        } else {
                            throw NotFoundException(s"No Default Object Access Permission found for project: $projectIri, group: $groupIri, resourceClassIri: $resourceClassIri, propertyIri: $propertyIri combination")
                        }
                    }
                }
    }

    /**
      * Convenience method returning a set with combined max default object access permissions.
      *
      * @param projectIri the IRI of the project.
      * @param groups     the list of groups for which default object access permissions are retrieved and combined.
      * @return a set of [[PermissionADM]].
      */
    private def defaultObjectAccessPermissionsForGroupsGetADM(projectIri: IRI, groups: Seq[IRI]): Future[Set[PermissionADM]] = {

        /* Get default object access permissions for each group and combine them */
        val gpf: Seq[Future[Seq[PermissionADM]]] = for {
            groupIri <- groups
            //_ = log.debug(s"userDefaultObjectAccessPermissionsGetV1 - projectIri: $projectIri, groupIri: $groupIri")

            groupPermissions: Future[Seq[PermissionADM]] = defaultObjectAccessPermissionGetADM(projectIri = projectIri, groupIri = Some(groupIri), resourceClassIri = None, propertyIri = None).map {
                case Some(doap: DefaultObjectAccessPermissionADM) => doap.hasPermissions.toSeq
                case None => Seq.empty[PermissionADM]
            }

        } yield groupPermissions

        val allPermissionsFuture: Future[Seq[Seq[PermissionADM]]] = Future.sequence(gpf)

        /* combines all permissions for each group and removes duplicates  */
        val result: Future[Set[PermissionADM]] = for {
            allPermissions: Seq[Seq[PermissionADM]] <- allPermissionsFuture

            // remove instances with empty PermissionV1 sets
            cleanedAllPermissions: Seq[Seq[PermissionADM]] = allPermissions.filter(_.nonEmpty)

            /* Combine permission sequences */
            combined = cleanedAllPermissions.foldLeft(Seq.empty[PermissionADM]) { (acc, seq) =>
                acc ++ seq
            }
            /* Remove possible duplicate permissions */
            result: Set[PermissionADM] = PermissionUtilADM.removeDuplicatePermissions(combined)

            //_ = log.debug(s"defaultObjectAccessPermissionsForGroupsGetV1 - result: $result")
        } yield result
        result
    }

    /**
      * Convenience method returning a set with default object access permissions defined on a resource class.
      *
      * @param projectIri       the IRI of the project.
      * @param resourceClassIri the resource's class IRI
      * @return a set of [[PermissionADM]].
      */
    private def defaultObjectAccessPermissionsForResourceClassGetADM(projectIri: IRI, resourceClassIri: IRI): Future[Set[PermissionADM]] = {
        for {
            defaultPermissionsOption: Option[DefaultObjectAccessPermissionADM] <- defaultObjectAccessPermissionGetADM(projectIri = projectIri, groupIri = None, resourceClassIri = Some(resourceClassIri), propertyIri = None)
            defaultPermissions: Set[PermissionADM] = defaultPermissionsOption match {
                case Some(doap) => doap.hasPermissions
                case None => Set.empty[PermissionADM]
            }
        } yield defaultPermissions
    }

    /**
      * Convenience method returning a set with default object access permissions defined on a resource class / property combination.
      *
      * @param projectIri       the IRI of the project.
      * @param resourceClassIri the resource's class IRI
      * @param propertyIri      the property's IRI.
      * @return a set of [[PermissionADM]].
      */
    private def defaultObjectAccessPermissionsForResourceClassPropertyGetADM(projectIri: IRI, resourceClassIri: IRI, propertyIri: IRI): Future[Set[PermissionADM]] = {
        for {
            defaultPermissionsOption: Option[DefaultObjectAccessPermissionADM] <- defaultObjectAccessPermissionGetADM(projectIri = projectIri, groupIri = None, resourceClassIri = Some(resourceClassIri), propertyIri = Some(propertyIri))
            defaultPermissions: Set[PermissionADM] = defaultPermissionsOption match {
                case Some(doap) => doap.hasPermissions
                case None => Set.empty[PermissionADM]
            }
        } yield defaultPermissions
    }

    /**
      * Convenience method returning a set with default object access permissions defined on a property.
      *
      * @param projectIri  the IRI of the project.
      * @param propertyIri the property's IRI.
      * @return a set of [[PermissionADM]].
      */
    private def defaultObjectAccessPermissionsForPropertyGetADM(projectIri: IRI, propertyIri: IRI): Future[Set[PermissionADM]] = {
        for {
            defaultPermissionsOption: Option[DefaultObjectAccessPermissionADM] <- defaultObjectAccessPermissionGetADM(projectIri = projectIri, groupIri = None, resourceClassIri = None, propertyIri = Some(propertyIri))
            defaultPermissions: Set[PermissionADM] = defaultPermissionsOption match {
                case Some(doap) => doap.hasPermissions
                case None => Set.empty[PermissionADM]
            }
        } yield defaultPermissions
    }

    /**
      * Returns a string containing default object permissions statements ready for usage during creation of a new resource.
      * The permissions include any default object access permissions defined for the resource class and on any groups the
      * user is member of.
      *
      * @param projectIri       the IRI of the project.
      * @param resourceClassIri the IRI of the resource class for which the default object access permissions are requested.
      * @param propertyIri      the IRI of the property for which the default object access permissions are requested.
      * @param targetUser       the user for which the permissions need to be calculated.
      * @param requestingUser   the user initiating the request.
      * @return an optional string with object access permission statements
      */
    private def defaultObjectAccessPermissionsStringForEntityGetADM(projectIri: IRI, resourceClassIri: IRI, propertyIri: Option[IRI], entityType: String, targetUser: UserADM, requestingUser: UserADM): Future[DefaultObjectAccessPermissionsStringResponseADM] = {

        //log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - projectIRI: $projectIRI, resourceClassIRI: $resourceClassIRI, propertyIRI: $propertyIRI, permissionData:$permissionData")
        for {
            // check if necessary field are defined.
            _ <- Future(if (projectIri.isEmpty) throw BadRequestException("Project cannot be empty"))
            _ = if (entityType == PropertyEntityType && propertyIri.isEmpty) {
                throw BadRequestException("PropertyTypeIri needs to be supplied")
            }
            _ = if (targetUser.isAnonymousUser) throw BadRequestException("Anonymous Users are not allowed.")


            /* Get the groups the user is member of. */
            userGroupsOption: Option[Seq[IRI]] = targetUser.permissions.groupsPerProject.get(projectIri)
            userGroups: Seq[IRI] = userGroupsOption match {
                case Some(groups) => groups
                case None => Seq.empty[IRI]
            }

            /* Explicitly add 'SystemAdmin' and 'KnownUser' groups. */
            extendedUserGroups: List[IRI] = if (targetUser.permissions.isSystemAdmin) {
                OntologyConstants.KnoraBase.SystemAdmin :: OntologyConstants.KnoraBase.KnownUser :: userGroups.toList
            } else {
                OntologyConstants.KnoraBase.KnownUser :: userGroups.toList
            }

            // _ = log.debug("defaultObjectAccessPermissionsStringForEntityGetV1 - extendedUserGroups: {}", extendedUserGroups)

            /* List buffer holding default object access permissions tagged with the precedence level:
               0. ProjectAdmin > 1. ProjectEntity > 2. SystemEntity > 3. CustomGroups > 4. ProjectMember > 5. KnownUser
               Permissions are added following the precedence level from the highest to the lowest. As soon as one set
               of permissions is written into the buffer, any additionally found permissions are ignored. */
            permissionsListBuffer = ListBuffer.empty[(String, Set[PermissionADM])]


            /* Get the default object access permissions for the knora-base:ProjectAdmin group */
            defaultPermissionsOnProjectAdminGroup: Set[PermissionADM] <- defaultObjectAccessPermissionsForGroupsGetADM(projectIri, List(OntologyConstants.KnoraBase.ProjectAdmin))
            _ = if (defaultPermissionsOnProjectAdminGroup.nonEmpty) {
                if (extendedUserGroups.contains(OntologyConstants.KnoraBase.ProjectAdmin) || extendedUserGroups.contains(OntologyConstants.KnoraBase.SystemAdmin)) {
                    permissionsListBuffer += (("ProjectAdmin", defaultPermissionsOnProjectAdminGroup))
                    // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnProjectAdminGroup: $defaultPermissionsOnProjectAdminGroup")
                }
            }

            ///////////////////////////////
            // RESOURCE CLASS / PROPERTY
            ///////////////////////////////
            /* project resource class / property combination */
            defaultPermissionsOnProjectResourceClassProperty: Set[PermissionADM] <- {
                if (entityType == PropertyEntityType && permissionsListBuffer.isEmpty) {
                    defaultObjectAccessPermissionsForResourceClassPropertyGetADM(projectIri = projectIri, resourceClassIri = resourceClassIri, propertyIri = propertyIri.getOrElse(throw BadRequestException("PropertyIri needs to be supplied.")))
                } else {
                    Future(Set.empty[PermissionADM])
                }
            }
            _ = if (defaultPermissionsOnProjectResourceClassProperty.nonEmpty) {
                permissionsListBuffer += (("ProjectResourceClassProperty", defaultPermissionsOnProjectResourceClassProperty))
                // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnProjectResourceClassProperty: {}", defaultPermissionsOnProjectResourceClassProperty)
            }

            /* system resource class / property combination */
            defaultPermissionsOnSystemResourceClassProperty: Set[PermissionADM] <- {
                if (entityType == PropertyEntityType && permissionsListBuffer.isEmpty) {
                    val systemProject = OntologyConstants.KnoraBase.SystemProject
                    defaultObjectAccessPermissionsForResourceClassPropertyGetADM(projectIri = systemProject, resourceClassIri = resourceClassIri, propertyIri = propertyIri.getOrElse(throw BadRequestException("PropertyIri needs to be supplied.")))
                } else {
                    Future(Set.empty[PermissionADM])
                }
            }
            _ = if (defaultPermissionsOnSystemResourceClassProperty.nonEmpty) {
                permissionsListBuffer += (("SystemResourceClassProperty", defaultPermissionsOnSystemResourceClassProperty))
                // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnSystemResourceClassProperty: {}", defaultPermissionsOnSystemResourceClassProperty)
            }

            ///////////////////////
            // RESOURCE CLASS
            ///////////////////////
            /* Get the default object access permissions defined on the resource class for the current project */
            defaultPermissionsOnProjectResourceClass: Set[PermissionADM] <- {
                if (entityType == ResourceEntityType && permissionsListBuffer.isEmpty) {
                    defaultObjectAccessPermissionsForResourceClassGetADM(projectIri = projectIri, resourceClassIri = resourceClassIri)
                } else {
                    Future(Set.empty[PermissionADM])
                }
            }
            _ = if (defaultPermissionsOnProjectResourceClass.nonEmpty) {
                permissionsListBuffer += (("ProjectResourceClass", defaultPermissionsOnProjectResourceClass))
                // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnProjectResourceClass: {}", defaultPermissionsOnProjectResourceClass)
            }

            /* Get the default object access permissions defined on the resource class inside the SystemProject */
            defaultPermissionsOnSystemResourceClass: Set[PermissionADM] <- {
                if (entityType == ResourceEntityType && permissionsListBuffer.isEmpty) {
                    val systemProject = OntologyConstants.KnoraBase.SystemProject
                    defaultObjectAccessPermissionsForResourceClassGetADM(projectIri = systemProject, resourceClassIri = resourceClassIri)
                } else {
                    Future(Set.empty[PermissionADM])
                }
            }
            _ = if (defaultPermissionsOnSystemResourceClass.nonEmpty) {
                permissionsListBuffer += (("SystemResourceClass", defaultPermissionsOnSystemResourceClass))
                // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnSystemResourceClass: {}", defaultPermissionsOnSystemResourceClass)
            }

            ///////////////////////
            // PROPERTY
            ///////////////////////
            /* project property */
            defaultPermissionsOnProjectProperty: Set[PermissionADM] <- {
                if (entityType == PropertyEntityType && permissionsListBuffer.isEmpty) {
                    defaultObjectAccessPermissionsForPropertyGetADM(projectIri = projectIri, propertyIri = propertyIri.getOrElse(throw BadRequestException("PropertyIri needs to be supplied.")))
                } else {
                    Future(Set.empty[PermissionADM])
                }
            }
            _ = if (defaultPermissionsOnProjectProperty.nonEmpty) {
                permissionsListBuffer += (("ProjectProperty", defaultPermissionsOnProjectProperty))
                // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnProjectProperty: {}", defaultPermissionsOnProjectProperty)
            }

            /* system property */
            defaultPermissionsOnSystemProperty: Set[PermissionADM] <- {
                if (entityType == PropertyEntityType && permissionsListBuffer.isEmpty) {
                    val systemProject = OntologyConstants.KnoraBase.SystemProject
                    defaultObjectAccessPermissionsForPropertyGetADM(projectIri = systemProject, propertyIri = propertyIri.getOrElse(throw BadRequestException("PropertyIri needs to be supplied.")))
                } else {
                    Future(Set.empty[PermissionADM])
                }
            }
            _ = if (defaultPermissionsOnSystemProperty.nonEmpty) {
                permissionsListBuffer += (("SystemProperty", defaultPermissionsOnSystemProperty))
                // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnSystemProperty: {}", defaultPermissionsOnSystemProperty)
            }

            ///////////////////////
            // CUSTOM GROUPS
            ///////////////////////
            /* Get the default object access permissions for custom groups (all groups other than the built-in groups) */
            defaultPermissionsOnCustomGroups: Set[PermissionADM] <- {
                if (extendedUserGroups.nonEmpty && permissionsListBuffer.isEmpty) {
                    val customGroups: List[IRI] = extendedUserGroups.toList diff List(OntologyConstants.KnoraBase.KnownUser, OntologyConstants.KnoraBase.ProjectMember, OntologyConstants.KnoraBase.ProjectAdmin, OntologyConstants.KnoraBase.SystemAdmin)
                    if (customGroups.nonEmpty) {
                        defaultObjectAccessPermissionsForGroupsGetADM(projectIri, customGroups)
                    } else {
                        Future(Set.empty[PermissionADM])
                    }
                } else {
                    // case where non SystemAdmin from outside of project
                    Future(Set.empty[PermissionADM])
                }
            }
            _ = if (defaultPermissionsOnCustomGroups.nonEmpty) {
                permissionsListBuffer += (("CustomGroups", defaultPermissionsOnCustomGroups))
                // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnCustomGroups: $defaultPermissionsOnCustomGroups")
            }

            ///////////////////////
            // PROJECT MEMBER
            ///////////////////////
            /* Get the default object access permissions for the knora-base:ProjectMember group */
            defaultPermissionsOnProjectMemberGroup: Set[PermissionADM] <- {
                if (permissionsListBuffer.isEmpty) {
                    defaultObjectAccessPermissionsForGroupsGetADM(projectIri, List(OntologyConstants.KnoraBase.ProjectMember))
                } else {
                    Future(Set.empty[PermissionADM])
                }
            }
            _ = if (defaultPermissionsOnProjectMemberGroup.nonEmpty) {
                if (extendedUserGroups.contains(OntologyConstants.KnoraBase.ProjectMember) || extendedUserGroups.contains(OntologyConstants.KnoraBase.SystemAdmin)) {
                    permissionsListBuffer += (("ProjectMember", defaultPermissionsOnProjectMemberGroup))
                }
                // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnProjectMemberGroup: $defaultPermissionsOnProjectMemberGroup")
            }

            ///////////////////////
            // KNOWN USER
            ///////////////////////
            /* Get the default object access permissions for the knora-base:KnownUser group */
            defaultPermissionsOnKnownUserGroup: Set[PermissionADM] <- {
                if (permissionsListBuffer.isEmpty) {
                    defaultObjectAccessPermissionsForGroupsGetADM(projectIri, List(OntologyConstants.KnoraBase.KnownUser))
                } else {
                    Future(Set.empty[PermissionADM])
                }
            }
            _ = if (defaultPermissionsOnKnownUserGroup.nonEmpty) {
                if (extendedUserGroups.contains(OntologyConstants.KnoraBase.KnownUser)) {
                    permissionsListBuffer += (("KnownUser", defaultPermissionsOnKnownUserGroup))
                    // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnKnownUserGroup: $defaultPermissionsOnKnownUserGroup")
                }
            }

            ///////////////////////
            // FALLBACK PERMISSION IF NONE COULD BE FOUND
            ///////////////////////
            /* Set 'CR knora-base:Creator' as the fallback permission */
            _ = if (permissionsListBuffer.isEmpty) {
                val defaultFallbackPermission = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraBase.Creator))
                permissionsListBuffer += (("Fallback", defaultFallbackPermission))
                // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultFallbackPermission: $defaultFallbackPermission")
            } else {
                FastFuture.successful(Set.empty[PermissionADM])
            }

            /* Create permissions string */
            result = permissionsListBuffer.length match {
                case 1 => {
                    PermissionUtilADM.formatPermissionADMs(permissionsListBuffer.head._2, PermissionType.OAP)
                }
                case _ => throw AssertionException("The permissions list buffer holding default object permissions should never be larger then 1.")
            }
            _ = log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - project: {}, precedence: {}, defaultObjectAccessPermissions: {}", projectIri, permissionsListBuffer.head._1, result)
        } yield permissionsmessages.DefaultObjectAccessPermissionsStringResponseADM(result)
    }

    /*

        private def createDefaultObjectAccessPermissionV1(newDefaultObjectAccessPermissionV1: NewDefaultObjectAccessPermissionV1, userProfileV1: UserProfileV1): Future[DefaultObjectAccessPermissionOperationResponseV1] = ???

        private def deleteDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri: IRI, userProfileV1: UserProfileV1): Future[DefaultObjectAccessPermissionOperationResponseV1] = ???
    */

    /*
    private def templatePermissionsCreateRequestV1(projectIri: IRI, permissionsTemplate: PermissionsTemplate, userProfileV1: UserProfileV1): Future[TemplatePermissionsCreateResponseV1] = {
        for {
        /* find and delete all administrative permissions */
            administrativePermissionsIris <- getAdministrativePermissionIrisForProjectV1(projectIri, userProfileV1)
            _ = administrativePermissionsIris.foreach { iri =>
                deleteAdministrativePermissionV1(iri, userProfileV1)
            }

            /* find and delete all default object access permissions */
            defaultObjectAccessPermissionIris <- getDefaultObjectAccessPermissionIrisForProjectV1(projectIri, userProfileV1)
            _ = defaultObjectAccessPermissionIris.foreach { iri =>
                deleteDefaultObjectAccessPermissionV1(iri, userProfileV1)
            }

            _ = if (permissionsTemplate == PermissionsTemplate.OPEN) {
                /* create administrative permissions */
                /* is the user a SystemAdmin then skip adding project admin permissions*/
                if (!userProfileV1.isSystemAdmin) {

                }

                /* create default object access permissions */
            }

            templatePermissionsCreateResponse = TemplatePermissionsCreateResponseV1(
                success = true,
                administrativePermissions = Vector.empty[AdministrativePermissionV1],
                defaultObjectAccessPermissions = Vector.empty[DefaultObjectAccessPermissionV1],
                msg = "OK"
            )

        } yield templatePermissionsCreateResponse

    }
    */


}

/**
  * Companion object providing helper methods.
  */
object PermissionsResponderADM {

    val PermissionsCacheName = "permissionsCache"

    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Creates a key representing the supplied parameters.
      *
      * @param projectIri       the project IRI
      * @param groupIri         the group IRI
      * @param resourceClassIri the resource class IRI
      * @param propertyIri      the property IRI
      * @return a string.
      */
    def getDefaultObjectAccessPermissionADMKey(projectIri: IRI, groupIri: Option[IRI], resourceClassIri: Option[IRI], propertyIri: Option[IRI]): String = {

        projectIri.toString + " | " + groupIri.toString + " | " + resourceClassIri.toString + " | " + propertyIri.toString
    }

    /**
      * Writes a [[DefaultObjectAccessPermissionADM]] object to cache.
      *
      * @param doap a [[DefaultObjectAccessPermissionADM]].
      * @return true if writing was successful.
      * @throws ApplicationCacheException when there is a problem with writing to cache.
      */
    def writeDefaultObjectAccessPermissionADMToCache(doap: DefaultObjectAccessPermissionADM): Boolean = {

        val key = doap.cacheKey

        CacheUtil.put(PermissionsCacheName, key, doap)

        if (CacheUtil.get(PermissionsCacheName, key).isEmpty) {
            throw ApplicationCacheException("Writing the permission to cache was not successful.")
        }

        true
    }

    /**
      * Removes a [[DefaultObjectAccessPermissionADM]] object from cache.
      *
      * @param projectIri       the project IRI
      * @param groupIri         the group IRI
      * @param resourceClassIri the resource class IRI
      * @param propertyIri      the property IRI
      */
    def invalidateCachedDefaultObjectAccessPermissionADM(projectIri: IRI, groupIri: Option[IRI], resourceClassIri: Option[IRI], propertyIri: Option[IRI]): Unit = {

        val key = getDefaultObjectAccessPermissionADMKey(projectIri, groupIri, resourceClassIri, propertyIri)

        CacheUtil.remove(PermissionsCacheName, key)
    }
}
