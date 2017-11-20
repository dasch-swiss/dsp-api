/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v1

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.apache.jena.sparql.function.library.leviathan.log
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.messages.v1.responder.groupmessages.{GroupInfoByIRIGetRequest, GroupInfoResponseV1}
import org.knora.webapi.messages.v1.responder.permissionmessages.{AdministrativePermissionForProjectGroupGetResponseV1, AdministrativePermissionV1, DefaultObjectAccessPermissionGetResponseV1, DefaultObjectAccessPermissionV1, PermissionType, _}
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{KnoraIdUtil, PermissionUtilV1}

import scala.collection.immutable.Iterable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future


/**
  * Provides information about Knora users to other responders.
  */
class PermissionsResponderV1 extends Responder {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    /* Entity types used to more clearly distinguish what kind of entity is meant */
    val RESOURCE_ENTITY_TYPE = "resource"
    val PROPERTY_ENTITY_TYPE = "property"

    /**
      * Receives a message extending [[org.knora.webapi.messages.v1.responder.permissionmessages.PermissionsResponderRequestV1]].
      * If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case PermissionDataGetV1(projectIris, groupIris, isInProjectAdminGroup, isInSystemAdminGroup) => future2Message(sender(), permissionsDataGetV1(projectIris, groupIris, isInProjectAdminGroup, isInSystemAdminGroup), log)
        case AdministrativePermissionsForProjectGetRequestV1(projectIri, userProfileV1) => future2Message(sender(), administrativePermissionsForProjectGetRequestV1(projectIri, userProfileV1), log)
        case AdministrativePermissionForIriGetRequestV1(administrativePermissionIri, userProfile) => future2Message(sender(), administrativePermissionForIriGetRequestV1(administrativePermissionIri, userProfile), log)
        case AdministrativePermissionForProjectGroupGetV1(projectIri, groupIri) => future2Message(sender(), administrativePermissionForProjectGroupGetV1(projectIri, groupIri), log)
        case AdministrativePermissionForProjectGroupGetRequestV1(projectIri, groupIri, userProfile) => future2Message(sender(), administrativePermissionForProjectGroupGetRequestV1(projectIri, groupIri, userProfile), log)
        case AdministrativePermissionCreateRequestV1(newAdministrativePermissionV1, userProfileV1) => future2Message(sender(), administrativePermissionCreateRequestV1(newAdministrativePermissionV1, userProfileV1), log)
        //case AdministrativePermissionDeleteRequestV1(administrativePermissionIri, userProfileV1) => future2Message(sender(), deleteAdministrativePermissionV1(administrativePermissionIri, userProfileV1), log)
        case ObjectAccessPermissionsForResourceGetV1(resourceIri) => future2Message(sender(), objectAccessPermissionsForResourceGetV1(resourceIri), log)
        case ObjectAccessPermissionsForValueGetV1(valueIri) => future2Message(sender(), objectAccessPermissionsForValueGetV1(valueIri), log)
        case DefaultObjectAccessPermissionsForProjectGetRequestV1(projectIri, userProfileV1) => future2Message(sender(), defaultObjectAccessPermissionsForProjectGetRequestV1(projectIri, userProfileV1), log)
        case DefaultObjectAccessPermissionForIriGetRequestV1(defaultObjectAccessPermissionIri, userProfileV1) => future2Message(sender(), defaultObjectAccessPermissionForIriGetRequestV1(defaultObjectAccessPermissionIri, userProfileV1), log)
        case DefaultObjectAccessPermissionGetRequestV1(projectIri, groupIri, resourceClassIri, propertyIri, userProfile) => future2Message(sender(), defaultObjectAccessPermissionGetRequestV1(projectIri, groupIri, resourceClassIri, propertyIri, userProfile), log)
        case DefaultObjectAccessPermissionsStringForResourceClassGetV1(projectIri, resourceClassIri, permissionData) => future2Message(sender(), defaultObjectAccessPermissionsStringForEntityGetV1(projectIri, resourceClassIri, None, RESOURCE_ENTITY_TYPE, permissionData), log)
        case DefaultObjectAccessPermissionsStringForPropertyGetV1(projectIri, resourceClassIri, propertyTypeIri, permissionData) => future2Message(sender(), defaultObjectAccessPermissionsStringForEntityGetV1(projectIri, resourceClassIri, Some(propertyTypeIri), PROPERTY_ENTITY_TYPE, permissionData), log)
        //case DefaultObjectAccessPermissionCreateRequestV1(newDefaultObjectAccessPermissionV1, userProfileV1) => future2Message(sender(), createDefaultObjectAccessPermissionV1(newDefaultObjectAccessPermissionV1, userProfileV1), log)
        //case DefaultObjectAccessPermissionDeleteRequestV1(defaultObjectAccessPermissionIri, userProfileV1) => future2Message(sender(), deleteDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri, userProfileV1), log)
        //case TemplatePermissionsCreateRequestV1(projectIri, permissionsTemplate, userProfileV1) => future2Message(sender(), templatePermissionsCreateRequestV1(projectIri, permissionsTemplate, userProfileV1), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }


    ///////////////////////////////////////////////////////////////////////////
    // PERMISSION DATA
    ///////////////////////////////////////////////////////////////////////////

    /**
      * Creates the user's [[PermissionDataV1]]
      *
      * @param projectIris            the projects the user is part of.
      * @param groupIris              the groups the user is member of (without ProjectMember, ProjectAdmin, SystemAdmin)
      * @param isInProjectAdminGroups the projects in which the user is member of the ProjectAdmin group.
      * @param isInSystemAdminGroup   the flag denoting membership in the SystemAdmin group.
      * @return
      */
    def permissionsDataGetV1(projectIris: Seq[IRI], groupIris: Seq[IRI], isInProjectAdminGroups: Seq[IRI], isInSystemAdminGroup: Boolean): Future[PermissionDataV1] = {
        // find out which project each group belongs to
        //_ = log.debug("getPermissionsProfileV1 - find out to which project each group belongs to")
        val groupFutures: Seq[Future[(IRI, IRI)]] = if (groupIris.nonEmpty) {
            groupIris.map {
                groupIri =>
                    for {
                        groupInfo <- (responderManager ? GroupInfoByIRIGetRequest(groupIri, None)).mapTo[GroupInfoResponseV1]
                        res = (groupInfo.group_info.project, groupIri)
                    } yield res
            }
        } else {
            Seq.empty[Future[(IRI, IRI)]]
        }

        val groupsFuture: Future[Vector[(IRI, IRI)]] = Future.sequence(groupFutures).map(_.toVector)

        for {
            groups: Vector[(IRI, IRI)] <- groupsFuture
            //_ = log.debug(s"permissionsProfileGetV1 - groups: {}", MessageUtil.toSource(groups))

            /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#ProjectMember' group for each project */
            projectMembers: Vector[(IRI, IRI)] = if (projectIris.nonEmpty) {
                for {
                    projectIri <- projectIris.toVector
                    res = (projectIri, OntologyConstants.KnoraBase.ProjectMember)
                } yield res
            } else {
                Vector.empty[(IRI, IRI)]
            }
            //_ = log.debug(s"permissionsProfileGetV1 - projectMembers: {}", MessageUtil.toSource(projectMembers))


            /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group for each project */
            projectAdmins: Vector[(IRI, IRI)] = if (projectIris.nonEmpty) {
                for {
                    projectAdminForGroup <- isInProjectAdminGroups.toVector
                    res = (projectAdminForGroup, OntologyConstants.KnoraBase.ProjectAdmin)
                } yield res
            } else {
                Vector.empty[(IRI, IRI)]
            }
            //_ = log.debug("permissionsProfileGetV1 - projectAdmins: {}", MessageUtil.toSource(projectAdmins))

            /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#SystemAdmin' group */
            systemAdmin: Vector[(IRI, IRI)] = if (isInSystemAdminGroup) {
                Vector((OntologyConstants.KnoraBase.SystemProject, OntologyConstants.KnoraBase.SystemAdmin))
            } else {
                Vector.empty[(IRI, IRI)]
            }
            //_ = log.debug(s"permissionsProfileGetV1 - systemAdmin: {}", MessageUtil.toSource(systemAdmin))

            /* combine explicit groups with materialized implicit groups */
            /* here we don't add the KnownUser group, as this would inflate the whole thing. */
            /* we instead inject the relevant information in defaultObjectAccessPermissionsStringForEntityGetV1 */
            allGroups = groups ++ projectMembers ++ projectAdmins ++ systemAdmin
            groupsPerProject = allGroups.groupBy(_._1).map { case (k, v) => (k, v.map(_._2)) }
            // _ = log.debug(s"permissionsProfileGetV1 - groupsPerProject: {}", MessageUtil.toSource(groupsPerProject))

            /* retrieve the administrative permissions for each group per project the user is member of */
            administrativePermissionsPerProjectFuture: Future[Map[IRI, Set[PermissionV1]]] = if (projectIris.nonEmpty) {
                userAdministrativePermissionsGetV1(groupsPerProject)
            } else {
                Future(Map.empty[IRI, Set[PermissionV1]])
            }
            administrativePermissionsPerProject <- administrativePermissionsPerProjectFuture

            /* construct the permission profile from the different parts */
            result = PermissionDataV1(
                groupsPerProject = groupsPerProject,
                administrativePermissionsPerProject = administrativePermissionsPerProject,
                anonymousUser = false
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
    def userAdministrativePermissionsGetV1(groupsPerProject: Map[IRI, Seq[IRI]]): Future[Map[IRI, Set[PermissionV1]]] = {


        /* Get all permissions per project, applying permission precedence rule */
        def calculatePermission(projectIri: IRI, extendedUserGroups: Seq[IRI]): Future[(IRI, Set[PermissionV1])] = {


            /* List buffer holding default object access permissions tagged with the precedence level:
               1. ProjectAdmin > 2. CustomGroups > 3. ProjectMember > 4. KnownUser
               Permissions are added following the precedence level from the highest to the lowest. As soon as one set
               of permissions is written into the buffer, any additionally found permissions are ignored. */
            val permissionsListBuffer = ListBuffer.empty[(String, Set[PermissionV1])]

            for {
                /* Get administrative permissions for the knora-base:ProjectAdmin group */
                administrativePermissionsOnProjectAdminGroup: Set[PermissionV1] <- administrativePermissionForGroupsGetV1(projectIri, List(OntologyConstants.KnoraBase.ProjectAdmin))
                _ = if (administrativePermissionsOnProjectAdminGroup.nonEmpty) {
                    if (extendedUserGroups.contains(OntologyConstants.KnoraBase.ProjectAdmin)) {
                        permissionsListBuffer += (("ProjectAdmin", administrativePermissionsOnProjectAdminGroup))
                    }
                }
                //_ = log.debug(s"userAdministrativePermissionsGetV1 - project: $projectIri, administrativePermissionsOnProjectAdminGroup: $administrativePermissionsOnProjectAdminGroup")


                /* Get administrative permissions for custom groups (all groups other than the built-in groups) */
                administrativePermissionsOnCustomGroups: Set[PermissionV1] <- {
                    val customGroups = extendedUserGroups diff List(OntologyConstants.KnoraBase.KnownUser, OntologyConstants.KnoraBase.ProjectMember, OntologyConstants.KnoraBase.ProjectAdmin)
                    if (customGroups.nonEmpty) {
                        administrativePermissionForGroupsGetV1(projectIri, customGroups)
                    } else {
                        Future(Set.empty[PermissionV1])
                    }
                }
                _ = if (administrativePermissionsOnCustomGroups.nonEmpty) {
                    if (permissionsListBuffer.isEmpty) {
                        permissionsListBuffer += (("CustomGroups", administrativePermissionsOnCustomGroups))
                    }
                }
                //_ = log.debug(s"userAdministrativePermissionsGetV1 - project: $projectIri, administrativePermissionsOnCustomGroups: $administrativePermissionsOnCustomGroups")


                /* Get administrative permissions for the knora-base:ProjectMember group */
                administrativePermissionsOnProjectMemberGroup: Set[PermissionV1] <- administrativePermissionForGroupsGetV1(projectIri, List(OntologyConstants.KnoraBase.ProjectMember))
                _ = if (administrativePermissionsOnProjectMemberGroup.nonEmpty) {
                    if (permissionsListBuffer.isEmpty) {
                        if (extendedUserGroups.contains(OntologyConstants.KnoraBase.ProjectMember)) {
                            permissionsListBuffer += (("ProjectMember", administrativePermissionsOnProjectMemberGroup))
                        }
                    }
                }
                //_ = log.debug(s"userAdministrativePermissionsGetV1 - project: $projectIri, administrativePermissionsOnProjectMemberGroup: $administrativePermissionsOnProjectMemberGroup")


                /* Get administrative permissions for the knora-base:KnownUser group */
                administrativePermissionsOnKnownUserGroup: Set[PermissionV1] <- administrativePermissionForGroupsGetV1(projectIri, List(OntologyConstants.KnoraBase.KnownUser))
                _ = if (administrativePermissionsOnKnownUserGroup.nonEmpty) {
                    if (permissionsListBuffer.isEmpty) {
                        if (extendedUserGroups.contains(OntologyConstants.KnoraBase.KnownUser)) {
                            permissionsListBuffer += (("KnownUser", administrativePermissionsOnKnownUserGroup))
                        }
                    }
                }
                //_ = log.debug(s"userAdministrativePermissionsGetV1 - project: $projectIri, administrativePermissionsOnKnownUserGroup: $administrativePermissionsOnKnownUserGroup")


                projectAdministrativePermissions: (IRI, Set[PermissionV1]) = permissionsListBuffer.length match {
                    case 1 => {
                        log.debug(s"userAdministrativePermissionsGetV1 - project: $projectIri, precedence: ${permissionsListBuffer.head._1}, administrativePermissions: ${permissionsListBuffer.head._2}")
                        (projectIri, permissionsListBuffer.head._2)
                    }
                    case 0 => (projectIri, Set.empty[PermissionV1])
                    case _ => throw AssertionException("The permissions list buffer holding default object permissions should never be larger then 1.")
                }

            } yield projectAdministrativePermissions
        }

        val permissionsPerProject: Iterable[Future[(IRI, Set[PermissionV1])]] = for {
            (projectIri, groups) <- groupsPerProject

            /* Explicitly add 'KnownUser' group */
            extendedUserGroups = OntologyConstants.KnoraBase.KnownUser ++ groups

            result = calculatePermission(projectIri, groups)

        } yield result

        val result: Future[Map[IRI, Set[PermissionV1]]] = Future.sequence(permissionsPerProject).map(_.toMap)

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
      * @return a set of [[PermissionV1]].
      */
    private def administrativePermissionForGroupsGetV1(projectIri: IRI, groups: Seq[IRI]): Future[Set[PermissionV1]] = {

        /* Get administrative permissions for each group and combine them */
        val gpf: Seq[Future[Seq[PermissionV1]]] = for {
            groupIri <- groups
            //_ = log.debug(s"administrativePermissionForGroupsGetV1 - projectIri: $projectIri, groupIri: $groupIri")

            groupPermissions: Future[Seq[PermissionV1]] = administrativePermissionForProjectGroupGetV1(projectIri, groupIri).map {
                case Some(ap: AdministrativePermissionV1) => ap.hasPermissions.toSeq
                case None => Seq.empty[PermissionV1]
            }

        } yield groupPermissions

        val allPermissionsFuture: Future[Seq[Seq[PermissionV1]]] = Future.sequence(gpf)

        /* combines all permissions for each group and removes duplicates  */
        val result: Future[Set[PermissionV1]] = for {
            allPermissions: Seq[Seq[PermissionV1]] <- allPermissionsFuture

            // remove instances with empty PermissionV1 sets
            cleanedAllPermissions: Seq[Seq[PermissionV1]] = allPermissions.filter(_.nonEmpty)

            /* Combine permission sequences */
            combined = cleanedAllPermissions.foldLeft(Seq.empty[PermissionV1]) { (acc, seq) =>
                acc ++ seq
            }
            /* Remove possible duplicate permissions */
            result: Set[PermissionV1] = PermissionUtilV1.removeDuplicatePermissions(combined)

            //_ = log.debug(s"administrativePermissionForGroupsGetV1 - result: $result")
        } yield result
        result
    }

    /**
      * Gets all administrative permissions defined inside a project.
      *
      * @param projectIRI    the IRI of the project.
      * @param userProfileV1 the [[UserProfileV1]] of the requesting user.
      * @return a list of IRIs of [[AdministrativePermissionV1]] objects.
      */
    private def administrativePermissionsForProjectGetRequestV1(projectIRI: IRI, userProfileV1: UserProfileV1): Future[AdministrativePermissionsForProjectGetResponseV1] = {

        // FIXME: Check user's permission for operation (issue #370)

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getAdministrativePermissionsForProject(
                triplestore = settings.triplestoreType,
                projectIri = projectIRI
            ).toString())
            //_ = log.debug(s"administrativePermissionsForProjectGetRequestV1 - query: $sparqlQueryString")

            permissionsQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - result: ${MessageUtil.toSource(permissionsQueryResponse)}")

            /* extract response rows */
            permissionsQueryResponseRows: Seq[VariableResultsRow] = permissionsQueryResponse.results.bindings

            permissionsWithProperties: Map[String, Map[String, String]] = permissionsQueryResponseRows.groupBy(_.rowMap("s")).map {
                case (permissionIri: String, rows: Seq[VariableResultsRow]) => (permissionIri, rows.map(row => (row.rowMap("p"), row.rowMap("o"))).toMap)
            }
            //_ = log.debug(s"administrativePermissionsForProjectGetRequestV1 - permissionsWithProperties: $permissionsWithProperties")

            administrativePermissions: Seq[AdministrativePermissionV1] = permissionsWithProperties.map {
                case (permissionIri: IRI, propsMap: Map[String, String]) =>

                    /* parse permissions */
                    val hasPermissions: Set[PermissionV1] = PermissionUtilV1.parsePermissionsWithType(propsMap.get(OntologyConstants.KnoraBase.HasPermissions), PermissionType.AP)

                    /* construct permission object */
                    AdministrativePermissionV1(iri = permissionIri, forProject = propsMap.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Administrative Permission $permissionIri has no project attached.")), forGroup = propsMap.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Administrative Permission $permissionIri has no group attached.")), hasPermissions = hasPermissions)
            }.toSeq

            /* construct response object */
            response = AdministrativePermissionsForProjectGetResponseV1(administrativePermissions)

        } yield response
    }

    /**
      * Gets a single administrative permission identified by it's IRI.
      *
      * @param administrativePermissionIri the IRI of the administrative permission.
      * @return a single [[AdministrativePermissionV1]] object.
      */
    def administrativePermissionForIriGetRequestV1(administrativePermissionIri: IRI, userProfileV1: UserProfileV1): Future[AdministrativePermissionForIriGetResponseV1] = {

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
            hasPermissions = PermissionUtilV1.parsePermissionsWithType(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.AP)
            //_ = log.debug(s"administrativePermissionForIriGetRequestV1 - hasPermissions: ${MessageUtil.toSource(hasPermissions)}")

            /* construct the permission object */
            permission = AdministrativePermissionV1(iri = administrativePermissionIri, forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $administrativePermissionIri has no project attached")).head, forGroup = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Permission $administrativePermissionIri has no group attached")).head, hasPermissions = hasPermissions)

            /* construct the response object */
            response = AdministrativePermissionForIriGetResponseV1(permission)

        } yield response
    }

    /**
      * Gets a single administrative permission identified by project and group.
      *
      * @param projectIri the project.
      * @param groupIri   the group.
      * @return an option containing an [[AdministrativePermissionV1]]
      */
    private def administrativePermissionForProjectGroupGetV1(projectIri: IRI, groupIri: IRI): Future[Option[AdministrativePermissionV1]] = {
        for {
            // check if necessary field are not empty.
            _ <- Future(if (projectIri.isEmpty) throw BadRequestException("Project cannot be empty"))
            _ = if (groupIri.isEmpty) throw BadRequestException("Group cannot be empty")

            sparqlQueryString <- Future(queries.sparql.v1.txt.getAdministrativePermissionForProjectAndGroup(
                triplestore = settings.triplestoreType,
                projectIri = projectIri,
                groupIri = groupIri
            ).toString())
            //_ = log.debug(s"administrativePermissionForProjectGroupGetV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"administrativePermissionForProjectGroupGetV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            permission: Option[AdministrativePermissionV1] = if (permissionQueryResponseRows.nonEmpty) {

                /* check if we only got one administrative permission back */
                val apCount: Int = permissionQueryResponseRows.groupBy(_.rowMap("s")).size
                if (apCount > 1) throw InconsistentTriplestoreDataException(s"Only one administrative permission instance allowed for project: $projectIri and group: $groupIri combination, but found $apCount.")

                /* get the iri of the retrieved permission */
                val returnedPermissionIri = permissionQueryResponse.getFirstRow.rowMap("s")

                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions = PermissionUtilV1.parsePermissionsWithType(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.AP)
                Some(
                    AdministrativePermissionV1(iri = returnedPermissionIri, forProject = projectIri, forGroup = groupIri, hasPermissions = hasPermissions)
                )
            } else {
                None
            }
            //_ = log.debug(s"administrativePermissionForProjectGroupGetV1 - projectIri: $projectIRI, groupIri: $groupIRI, administrativePermission: $permission")
        } yield permission
    }

    /**
      * Gets a single administrative permission identified by project and group.
      *
      * @param projectIri the project.
      * @param groupIri   the group.
      * @return an [[AdministrativePermissionForProjectGroupGetResponseV1]]
      */
    private def administrativePermissionForProjectGroupGetRequestV1(projectIri: IRI, groupIri: IRI, userProfile: UserProfileV1): Future[AdministrativePermissionForProjectGroupGetResponseV1] = {

        // FIXME: Check user's permission for operation (issue #370)

        for {
            ap <- administrativePermissionForProjectGroupGetV1(projectIri, groupIri)
            result = ap match {
                case Some(ap) => AdministrativePermissionForProjectGroupGetResponseV1(ap)
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
    private def administrativePermissionCreateRequestV1(newAdministrativePermissionV1: NewAdministrativePermissionV1, userProfileV1: UserProfileV1): Future[AdministrativePermissionCreateResponseV1] = {
        //log.debug("administrativePermissionCreateRequestV1")

        // FIXME: Check user's permission for operation (issue #370)

        for {
            _ <- Future {
                // check if necessary field are not empty.
                if (newAdministrativePermissionV1.forProject.isEmpty) throw BadRequestException("Project cannot be empty")
                if (newAdministrativePermissionV1.forGroup.isEmpty) throw BadRequestException("Group cannot be empty")
                if (newAdministrativePermissionV1.hasNewPermissions.isEmpty) throw BadRequestException("New permissions cannot be empty")
            }

            checkResult <- administrativePermissionForProjectGroupGetV1(newAdministrativePermissionV1.forProject, newAdministrativePermissionV1.forGroup)

            _ = checkResult match {
                case Some(ap) => throw DuplicateValueException(s"Permission for project: '${newAdministrativePermissionV1.forProject}' and group: '${newAdministrativePermissionV1.forGroup}' combination already exists.")
                case None => ()
            }

            // FIXME: Implement
            response = AdministrativePermissionCreateResponseV1(administrativePermission = AdministrativePermissionV1(iri = "mock-iri", forProject = "mock-project", forGroup = "mock-group", hasPermissions = Set.empty[PermissionV1]))

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
      * @return a sequence of [[PermissionV1]]
      */
    def objectAccessPermissionsForResourceGetV1(resourceIri: IRI): Future[Option[ObjectAccessPermissionV1]] = {
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

            permission: Option[ObjectAccessPermissionV1] = if (permissionQueryResponseRows.nonEmpty) {

                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions: Set[PermissionV1] = PermissionUtilV1.parsePermissionsWithType(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.OAP)
                Some(
                    ObjectAccessPermissionV1(forResource = Some(resourceIri), forValue = None, hasPermissions = hasPermissions)
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
      * @return a sequence of [[PermissionV1]]
      */
    def objectAccessPermissionsForValueGetV1(valueIri: IRI): Future[Option[ObjectAccessPermissionV1]] = {
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

            permission: Option[ObjectAccessPermissionV1] = if (permissionQueryResponseRows.nonEmpty) {

                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions: Set[PermissionV1] = PermissionUtilV1.parsePermissionsWithType(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.OAP)
                Some(
                    ObjectAccessPermissionV1(forResource = None, forValue = Some(valueIri), hasPermissions = hasPermissions)
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
      * @param projectIri  the IRI of the project.
      * @param userProfile the [[UserProfileV1]] of the requesting user.
      * @return a list of IRIs of [[DefaultObjectAccessPermissionV1]] objects.
      */
    private def defaultObjectAccessPermissionsForProjectGetRequestV1(projectIri: IRI, userProfile: UserProfileV1): Future[DefaultObjectAccessPermissionsForProjectGetResponseV1] = {

        // FIXME: Check user's permission for operation (issue #370)

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getDefaultObjectAccessPermissionsForProject(
                triplestore = settings.triplestoreType,
                projectIri = projectIri
            ).toString())
            //_ = log.debug(s"defaultObjectAccessPermissionsForProjectGetRequestV1 - query: $sparqlQueryString")

            permissionsQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"defaultObjectAccessPermissionsForProjectGetRequestV1 - result: ${MessageUtil.toSource(permissionsQueryResponse)}")

            /* extract response rows */
            permissionsQueryResponseRows: Seq[VariableResultsRow] = permissionsQueryResponse.results.bindings

            permissionsWithProperties: Map[String, Map[String, String]] = permissionsQueryResponseRows.groupBy(_.rowMap("s")).map {
                case (permissionIri: String, rows: Seq[VariableResultsRow]) => (permissionIri, rows.map(row => (row.rowMap("p"), row.rowMap("o"))).toMap)
            }
            //_ = log.debug(s"defaultObjectAccessPermissionsForProjectGetRequestV1 - permissionsWithProperties: $permissionsWithProperties")

            permissions: Seq[DefaultObjectAccessPermissionV1] = permissionsWithProperties.map {
                case (permissionIri: IRI, propsMap: Map[String, String]) =>

                    /* parse permissions */
                    val hasPermissions: Set[PermissionV1] = PermissionUtilV1.parsePermissionsWithType(propsMap.get(OntologyConstants.KnoraBase.HasPermissions), PermissionType.OAP)

                    /* construct permission object */
                    DefaultObjectAccessPermissionV1(iri = permissionIri, forProject = propsMap.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no project.")), forGroup = propsMap.get(OntologyConstants.KnoraBase.ForGroup), forResourceClass = propsMap.get(OntologyConstants.KnoraBase.ForResourceClass), forProperty = propsMap.get(OntologyConstants.KnoraBase.ForProperty), hasPermissions = hasPermissions)
            }.toSeq

            /* construct response object */
            response = DefaultObjectAccessPermissionsForProjectGetResponseV1(permissions)

        } yield response

    }

    /**
      * Gets a single default object access permission identified by its IRI.
      *
      * @param permissionIri the IRI of the default object access permission.
      * @return a single [[DefaultObjectAccessPermissionV1]] object.
      */
    private def defaultObjectAccessPermissionForIriGetRequestV1(permissionIri: IRI, userProfileV1: UserProfileV1): Future[DefaultObjectAccessPermissionForIriGetResponseV1] = {

        // FIXME: Check user's permission for operation (issue #370)

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getDefaultObjectAccessPermissionByIri(
                triplestore = settings.triplestoreType,
                defaultObjectAccessPermissionIri = permissionIri
            ).toString())
            //_ = log.debug(s"defaultObjectAccessPermissionForIriGetRequestV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"defaultObjectAccessPermissionForIriGetRequestV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            _ = if (permissionQueryResponseRows.isEmpty) {
                throw NotFoundException(s"Permission '$permissionIri' not found")
            }

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }
            //_ = log.debug(s"defaultObjectAccessPermissionForIriGetRequestV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

            hasPermissions = PermissionUtilV1.parsePermissionsWithType(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.OAP)

            defaultObjectAccessPermission = DefaultObjectAccessPermissionV1(iri = permissionIri, forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no project.")).head, forGroup = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForGroup).map(_.head), forResourceClass = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForResourceClass).map(_.head), forProperty = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForProperty).map(_.head), hasPermissions = hasPermissions)

            result = DefaultObjectAccessPermissionForIriGetResponseV1(defaultObjectAccessPermission)

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
      * @return an optional [[DefaultObjectAccessPermissionV1]]
      */
    def defaultObjectAccessPermissionGetV1(projectIri: IRI, groupIri: Option[IRI], resourceClassIri: Option[IRI], propertyIri: Option[IRI]): Future[Option[DefaultObjectAccessPermissionV1]] = {
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
            // _ = log.debug(s"defaultObjectAccessPermissionGetV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            // _ = log.debug(s"defaultObjectAccessPermissionGetV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            permission: Option[DefaultObjectAccessPermissionV1] = if (permissionQueryResponseRows.nonEmpty) {

                /* check if we only got one default object access permission back */
                val doapCount: Int = permissionQueryResponseRows.groupBy(_.rowMap("s")).size
                if (doapCount > 1) throw InconsistentTriplestoreDataException(s"Only one default object permission instance allowed for project: $projectIri and combination of group: $groupIri, resourceClass: $resourceClassIri, property: $propertyIri combination, but found: $doapCount.")

                /* get the iri of the retrieved permission */
                val permissionIri = permissionQueryResponse.getFirstRow.rowMap("s")

                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions: Set[PermissionV1] = PermissionUtilV1.parsePermissionsWithType(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.OAP)
                Some(
                    DefaultObjectAccessPermissionV1(iri = permissionIri, forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission has no project.")).head, forGroup = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForGroup).map(_.head), forResourceClass = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForResourceClass).map(_.head), forProperty = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForProperty).map(_.head), hasPermissions = hasPermissions)
                )
            } else {
                None
            }
            _ = log.debug(s"defaultObjectAccessPermissionGetV1 - p: $projectIri, g: $groupIri, r: $resourceClassIri, p: $propertyIri, permission: $permission")
        } yield permission
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
      * @param userProfile
      * @return a [[DefaultObjectAccessPermissionGetResponseV1]]
      */
    def defaultObjectAccessPermissionGetRequestV1(projectIri: IRI, groupIri: Option[IRI], resourceClassIri: Option[IRI], propertyIri: Option[IRI], userProfile: UserProfileV1): Future[DefaultObjectAccessPermissionGetResponseV1] = {

        // FIXME: Check user's permission for operation (issue #370)

        defaultObjectAccessPermissionGetV1(projectIri, groupIri, resourceClassIri, propertyIri)
            .mapTo[Option[DefaultObjectAccessPermissionV1]]
            .flatMap {
                case Some(doap) => Future(DefaultObjectAccessPermissionGetResponseV1(doap))
                case None => {
                    /* if the query was for a property, then we need to additionally check if it is a system property */
                    if (propertyIri.isDefined) {
                        val systemProject = OntologyConstants.KnoraBase.SystemProject
                        val doapF = defaultObjectAccessPermissionGetV1(systemProject, groupIri, resourceClassIri, propertyIri)
                        doapF.mapTo[Option[DefaultObjectAccessPermissionV1]].map {
                            case Some(systemDoap) => DefaultObjectAccessPermissionGetResponseV1(systemDoap)
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
      * @return a set of [[PermissionV1]].
      */
    def defaultObjectAccessPermissionsForGroupsGetV1(projectIri: IRI, groups: Seq[IRI]): Future[Set[PermissionV1]] = {

        /* Get default object access permissions for each group and combine them */
        val gpf: Seq[Future[Seq[PermissionV1]]] = for {
            groupIri <- groups
            //_ = log.debug(s"userDefaultObjectAccessPermissionsGetV1 - projectIri: $projectIri, groupIri: $groupIri")

            groupPermissions: Future[Seq[PermissionV1]] = defaultObjectAccessPermissionGetV1(projectIri = projectIri, groupIri = Some(groupIri), resourceClassIri = None, propertyIri = None).map {
                case Some(doap: DefaultObjectAccessPermissionV1) => doap.hasPermissions.toSeq
                case None => Seq.empty[PermissionV1]
            }

        } yield groupPermissions

        val allPermissionsFuture: Future[Seq[Seq[PermissionV1]]] = Future.sequence(gpf)

        /* combines all permissions for each group and removes duplicates  */
        val result: Future[Set[PermissionV1]] = for {
            allPermissions: Seq[Seq[PermissionV1]] <- allPermissionsFuture

            // remove instances with empty PermissionV1 sets
            cleanedAllPermissions: Seq[Seq[PermissionV1]] = allPermissions.filter(_.nonEmpty)

            /* Combine permission sequences */
            combined = cleanedAllPermissions.foldLeft(Seq.empty[PermissionV1]) { (acc, seq) =>
                acc ++ seq
            }
            /* Remove possible duplicate permissions */
            result: Set[PermissionV1] = PermissionUtilV1.removeDuplicatePermissions(combined)

            //_ = log.debug(s"defaultObjectAccessPermissionsForGroupsGetV1 - result: $result")
        } yield result
        result
    }

    /**
      * Convenience method returning a set with default object access permissions defined on a resource class.
      *
      * @param projectIri       the IRI of the project.
      * @param resourceClassIri the resource's class IRI
      * @return a set of [[PermissionV1]].
      */
    def defaultObjectAccessPermissionsForResourceClassGetV1(projectIri: IRI, resourceClassIri: IRI): Future[Set[PermissionV1]] = {
        for {
            defaultPermissionsOption: Option[DefaultObjectAccessPermissionV1] <- defaultObjectAccessPermissionGetV1(projectIri = projectIri, groupIri = None, resourceClassIri = Some(resourceClassIri), propertyIri = None)
            defaultPermissions: Set[PermissionV1] = defaultPermissionsOption match {
                case Some(doap) => doap.hasPermissions
                case None => Set.empty[PermissionV1]
            }
        } yield defaultPermissions
    }

    /**
      * Convenience method returning a set with default object access permissions defined on a resource class / property combination.
      *
      * @param projectIri       the IRI of the project.
      * @param resourceClassIri the resource's class IRI
      * @param propertyIri      the property's IRI.
      * @return a set of [[PermissionV1]].
      */
    def defaultObjectAccessPermissionsForResourceClassPropertyGetV1(projectIri: IRI, resourceClassIri: IRI, propertyIri: IRI): Future[Set[PermissionV1]] = {
        for {
            defaultPermissionsOption: Option[DefaultObjectAccessPermissionV1] <- defaultObjectAccessPermissionGetV1(projectIri = projectIri, groupIri = None, resourceClassIri = Some(resourceClassIri), propertyIri = Some(propertyIri))
            defaultPermissions: Set[PermissionV1] = defaultPermissionsOption match {
                case Some(doap) => doap.hasPermissions
                case None => Set.empty[PermissionV1]
            }
        } yield defaultPermissions
    }

    /**
      * Convenience method returning a set with default object access permissions defined on a property.
      *
      * @param projectIri  the IRI of the project.
      * @param propertyIri the property's IRI.
      * @return a set of [[PermissionV1]].
      */
    def defaultObjectAccessPermissionsForPropertyGetV1(projectIri: IRI, propertyIri: IRI): Future[Set[PermissionV1]] = {
        for {
            defaultPermissionsOption: Option[DefaultObjectAccessPermissionV1] <- defaultObjectAccessPermissionGetV1(projectIri = projectIri, groupIri = None, resourceClassIri = None, propertyIri = Some(propertyIri))
            defaultPermissions: Set[PermissionV1] = defaultPermissionsOption match {
                case Some(doap) => doap.hasPermissions
                case None => Set.empty[PermissionV1]
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
      * @param permissionData   the permission data of the user for which the default object access permissions are requested.
      * @return an optional string with object access permission statements
      */
    def defaultObjectAccessPermissionsStringForEntityGetV1(projectIri: IRI, resourceClassIri: IRI, propertyIri: Option[IRI], entityType: String, permissionData: PermissionDataV1): Future[DefaultObjectAccessPermissionsStringResponseV1] = {

        //log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - projectIRI: $projectIRI, resourceClassIRI: $resourceClassIRI, propertyIRI: $propertyIRI, permissionData:$permissionData")
        for {
            // check if necessary field are defined.
            _ <- Future(if (projectIri.isEmpty) throw BadRequestException("Project cannot be empty"))
            _ = if (entityType == PROPERTY_ENTITY_TYPE && propertyIri.isEmpty) {
                throw BadRequestException("PropertyTypeIri needs to be supplied")
            }
            _ = if (permissionData.anonymousUser) throw BadRequestException("Anonymous Users are not allowed.")


            /* Get the groups the user is member of. */
            userGroupsOption: Option[Seq[IRI]] = permissionData.groupsPerProject.get(projectIri)
            userGroups: Seq[IRI] = userGroupsOption match {
                case Some(groups) => groups
                case None => Seq.empty[IRI]
            }

            /* Explicitly add 'SystemAdmin' and 'KnownUser' groups. */
            extendedUserGroups: List[IRI] = if (permissionData.isSystemAdmin) {
                OntologyConstants.KnoraBase.SystemAdmin :: OntologyConstants.KnoraBase.KnownUser :: userGroups.toList
            } else {
                OntologyConstants.KnoraBase.KnownUser :: userGroups.toList
            }

            // _ = log.debug("defaultObjectAccessPermissionsStringForEntityGetV1 - extendedUserGroups: {}", extendedUserGroups)

            /* List buffer holding default object access permissions tagged with the precedence level:
               0. ProjectAdmin > 1. ProjectEntity > 2. SystemEntity > 3. CustomGroups > 4. ProjectMember > 5. KnownUser
               Permissions are added following the precedence level from the highest to the lowest. As soon as one set
               of permissions is written into the buffer, any additionally found permissions are ignored. */
            permissionsListBuffer = ListBuffer.empty[(String, Set[PermissionV1])]


            /* Get the default object access permissions for the knora-base:ProjectAdmin group */
            defaultPermissionsOnProjectAdminGroup: Set[PermissionV1] <- defaultObjectAccessPermissionsForGroupsGetV1(projectIri, List(OntologyConstants.KnoraBase.ProjectAdmin))
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
            defaultPermissionsOnProjectResourceClassProperty: Set[PermissionV1] <- {
                if (entityType == PROPERTY_ENTITY_TYPE && permissionsListBuffer.isEmpty) {
                    defaultObjectAccessPermissionsForResourceClassPropertyGetV1(projectIri = projectIri, resourceClassIri = resourceClassIri, propertyIri = propertyIri.getOrElse(throw BadRequestException("PropertyIri needs to be supplied.")))
                } else {
                    Future(Set.empty[PermissionV1])
                }
            }
            _ = if (defaultPermissionsOnProjectResourceClassProperty.nonEmpty) {
                permissionsListBuffer += (("ProjectResourceClassProperty", defaultPermissionsOnProjectResourceClassProperty))
                // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnProjectResourceClassProperty: {}", defaultPermissionsOnProjectResourceClassProperty)
            }

            /* system resource class / property combination */
            defaultPermissionsOnSystemResourceClassProperty: Set[PermissionV1] <- {
                if (entityType == PROPERTY_ENTITY_TYPE && permissionsListBuffer.isEmpty) {
                    val systemProject = OntologyConstants.KnoraBase.SystemProject
                    defaultObjectAccessPermissionsForResourceClassPropertyGetV1(projectIri = systemProject, resourceClassIri = resourceClassIri, propertyIri = propertyIri.getOrElse(throw BadRequestException("PropertyIri needs to be supplied.")))
                } else {
                    Future(Set.empty[PermissionV1])
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
            defaultPermissionsOnProjectResourceClass: Set[PermissionV1] <- {
                if (entityType == RESOURCE_ENTITY_TYPE && permissionsListBuffer.isEmpty) {
                    defaultObjectAccessPermissionsForResourceClassGetV1(projectIri = projectIri, resourceClassIri = resourceClassIri)
                } else {
                    Future(Set.empty[PermissionV1])
                }
            }
            _ = if (defaultPermissionsOnProjectResourceClass.nonEmpty) {
                permissionsListBuffer += (("ProjectResourceClass", defaultPermissionsOnProjectResourceClass))
                // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnProjectResourceClass: {}", defaultPermissionsOnProjectResourceClass)
            }

            /* Get the default object access permissions defined on the resource class inside the SystemProject */
            defaultPermissionsOnSystemResourceClass: Set[PermissionV1] <- {
                if (entityType == RESOURCE_ENTITY_TYPE && permissionsListBuffer.isEmpty) {
                    val systemProject = OntologyConstants.KnoraBase.SystemProject
                    defaultObjectAccessPermissionsForResourceClassGetV1(projectIri = systemProject, resourceClassIri = resourceClassIri)
                } else {
                    Future(Set.empty[PermissionV1])
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
            defaultPermissionsOnProjectProperty: Set[PermissionV1] <- {
                if (entityType == PROPERTY_ENTITY_TYPE && permissionsListBuffer.isEmpty) {
                    defaultObjectAccessPermissionsForPropertyGetV1(projectIri = projectIri, propertyIri = propertyIri.getOrElse(throw BadRequestException("PropertyIri needs to be supplied.")))
                } else {
                    Future(Set.empty[PermissionV1])
                }
            }
            _ = if (defaultPermissionsOnProjectProperty.nonEmpty) {
                permissionsListBuffer += (("ProjectProperty", defaultPermissionsOnProjectProperty))
                // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnProjectProperty: {}", defaultPermissionsOnProjectProperty)
            }

            /* system property */
            defaultPermissionsOnSystemProperty: Set[PermissionV1] <- {
                if (entityType == PROPERTY_ENTITY_TYPE && permissionsListBuffer.isEmpty) {
                    val systemProject = OntologyConstants.KnoraBase.SystemProject
                    defaultObjectAccessPermissionsForPropertyGetV1(projectIri = systemProject, propertyIri = propertyIri.getOrElse(throw BadRequestException("PropertyIri needs to be supplied.")))
                } else {
                    Future(Set.empty[PermissionV1])
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
            defaultPermissionsOnCustomGroups: Set[PermissionV1] <- {
                if (extendedUserGroups.nonEmpty && permissionsListBuffer.isEmpty) {
                    val customGroups: List[IRI] = extendedUserGroups.toList diff List(OntologyConstants.KnoraBase.KnownUser, OntologyConstants.KnoraBase.ProjectMember, OntologyConstants.KnoraBase.ProjectAdmin, OntologyConstants.KnoraBase.SystemAdmin)
                    if (customGroups.nonEmpty) {
                        defaultObjectAccessPermissionsForGroupsGetV1(projectIri, customGroups)
                    } else {
                        Future(Set.empty[PermissionV1])
                    }
                } else {
                    // case where non SystemAdmin from outside of project
                    Future(Set.empty[PermissionV1])
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
            defaultPermissionsOnProjectMemberGroup: Set[PermissionV1] <- {
                if (permissionsListBuffer.isEmpty) {
                    defaultObjectAccessPermissionsForGroupsGetV1(projectIri, List(OntologyConstants.KnoraBase.ProjectMember))
                } else {
                    Future(Set.empty[PermissionV1])
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
            defaultPermissionsOnKnownUserGroup: Set[PermissionV1] <- {
                if (permissionsListBuffer.isEmpty) {
                    defaultObjectAccessPermissionsForGroupsGetV1(projectIri, List(OntologyConstants.KnoraBase.KnownUser))
                } else {
                    Future(Set.empty[PermissionV1])
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
                    val defaultFallbackPermission = Set(PermissionV1.changeRightsPermission(OntologyConstants.KnoraBase.Creator))
                    permissionsListBuffer += (("Fallback", defaultFallbackPermission))
                    // log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultFallbackPermission: $defaultFallbackPermission")
                } else {
                    FastFuture.successful(Set.empty[PermissionV1])
                }
            
            /* Create permissions string */
            result = permissionsListBuffer.length match {
                case 1 => {
                    PermissionUtilV1.formatPermissions(permissionsListBuffer.head._2, PermissionType.OAP)
                }
                case _ => throw AssertionException("The permissions list buffer holding default object permissions should never be larger then 1.")
            }
            _ = log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - project: {}, precedence: {}, defaultObjectAccessPermissions: {}", projectIri, permissionsListBuffer.head._1, result)
        } yield DefaultObjectAccessPermissionsStringResponseV1(result)
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
