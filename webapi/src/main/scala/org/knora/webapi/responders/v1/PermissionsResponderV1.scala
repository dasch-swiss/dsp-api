/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

import akka.pattern._
import org.apache.jena.sparql.function.library.leviathan.log
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.groupmessages.{GroupInfoByIRIGetRequest, GroupInfoResponseV1}
import org.knora.webapi.messages.v1.responder.permissionmessages.{AdministrativePermissionForProjectGroupGetResponseV1, AdministrativePermissionV1, DefaultObjectAccessPermissionGetResponseV1, DefaultObjectAccessPermissionV1, PermissionType, _}
import org.knora.webapi.messages.v1.responder.projectmessages.{ProjectInfoV1, ProjectsGetV1}
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.{KnoraIdUtil, MessageUtil, PermissionUtilV1}

import scala.collection.immutable.Iterable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}


/**
  * Provides information about Knora users to other responders.
  */
class PermissionsResponderV1 extends ResponderV1 {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

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
        case ObjectAccessPermissionsForResourceGetV1(resourceIri) => future2Message(sender(), objectAccessPermissionsForResourceGetV1(resourceIri) , log)
        case ObjectAccessPermissionsForValueGetV1(valueIri) => future2Message(sender(), objectAccessPermissionsForValueGetV1(valueIri), log)
        case DefaultObjectAccessPermissionsForProjectGetRequestV1(projectIri, userProfileV1) => future2Message(sender(), defaultObjectAccessPermissionsForProjectGetRequestV1(projectIri, userProfileV1), log)
        case DefaultObjectAccessPermissionForIriGetRequestV1(defaultObjectAccessPermissionIri, userProfileV1) => future2Message(sender(), defaultObjectAccessPermissionForIriGetRequestV1(defaultObjectAccessPermissionIri, userProfileV1), log)
        case DefaultObjectAccessPermissionGetRequestV1(projectIri, groupIri, resourceClassIri, propertyIri, userProfile) => future2Message(sender(), defaultObjectAccessPermissionGetRequestV1(projectIri, groupIri, resourceClassIri, propertyIri, userProfile), log)
        case DefaultObjectAccessPermissionsStringForResourceClassGetV1(projectIri, resourceClassIri, permissionData) => future2Message(sender(), defaultObjectAccessPermissionsStringForEntityGetV1(projectIri, Some(resourceClassIri), None, permissionData), log)
        case DefaultObjectAccessPermissionsStringForPropertyGetV1(projectIri, propertyTypeIri, permissionData) => future2Message(sender(), defaultObjectAccessPermissionsStringForEntityGetV1(projectIri, None, Some(propertyTypeIri), permissionData), log)
        //case DefaultObjectAccessPermissionCreateRequestV1(newDefaultObjectAccessPermissionV1, userProfileV1) => future2Message(sender(), createDefaultObjectAccessPermissionV1(newDefaultObjectAccessPermissionV1, userProfileV1), log)
        //case DefaultObjectAccessPermissionDeleteRequestV1(defaultObjectAccessPermissionIri, userProfileV1) => future2Message(sender(), deleteDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri, userProfileV1), log)
        //case TemplatePermissionsCreateRequestV1(projectIri, permissionsTemplate, userProfileV1) => future2Message(sender(), templatePermissionsCreateRequestV1(projectIri, permissionsTemplate, userProfileV1), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }


    /*************************************************************************/
    /* PERMISSION DATA                                                       */
    /*************************************************************************/

    /**
      * Creates the user's [[PermissionDataV1]]
      *
      * @param projectIris the projects the user is part of.
      * @param groupIris the groups the user is member of (without ProjectMember, ProjectAdmin, SystemAdmin)
      * @param isInProjectAdminGroups the projects in which the user is member of the ProjectAdmin group.
      * @param isInSystemAdminGroup the flag denoting membership in the SystemAdmin group.
      * @return
      */
    def permissionsDataGetV1(projectIris: Seq[IRI], groupIris: Seq[IRI], isInProjectAdminGroups: Seq[IRI], isInSystemAdminGroup: Boolean): Future[PermissionDataV1] = {

        for {
            // find out to which project each group belongs to
            //_ = log.debug("getPermissionsProfileV1 - find out to which project each group belongs to")
            groups: List[(IRI, IRI)] <- Future(if (groupIris.nonEmpty) {
                groupIris.map {
                    groupIri => {
                        val resFuture = for {
                            groupInfo <- (responderManager ? GroupInfoByIRIGetRequest(groupIri, None)).mapTo[GroupInfoResponseV1]
                            res = (groupInfo.group_info.belongsToProject, groupIri)
                        } yield res
                        Await.result(resFuture, 1.seconds)
                    }
                }.toList
            } else {
                List.empty[(IRI, IRI)]
            })
            //_ = log.debug(s"permissionsProfileGetV1 - groups: ${MessageUtil.toSource(groups)}")

            /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#ProjectMember' group for each project */
            projectMembers: List[(IRI, IRI)] = if (projectIris.nonEmpty) {
                for {
                    projectIri <- projectIris.toList
                    res = (projectIri, OntologyConstants.KnoraBase.ProjectMember)
                } yield res
            } else {
                List.empty[(IRI, IRI)]
            }
            //_ = log.debug(s"permissionsProfileGetV1 - projectMembers: ${MessageUtil.toSource(projectMembers)}")


            /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group for each project */
            projectAdmins: List[(IRI, IRI)] = if (projectIris.nonEmpty) {
                for {
                    projectAdminForGroup <- isInProjectAdminGroups.toList
                    res = (projectAdminForGroup, OntologyConstants.KnoraBase.ProjectAdmin)
                } yield res
            } else {
                List.empty[(IRI, IRI)]
            }
            //_ = log.debug(s"permissionsProfileGetV1 - projectAdmins: ${MessageUtil.toSource(projectAdmins)}")


            /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#SystemAdmin' group */
            systemAdmin: List[(IRI, IRI)] = if (isInSystemAdminGroup) {
                List((OntologyConstants.KnoraBase.SystemProject, OntologyConstants.KnoraBase.SystemAdmin))
            } else {
                List.empty[(IRI, IRI)]
            }
            //_ = log.debug(s"permissionsProfileGetV1 - systemAdmin: ${MessageUtil.toSource(systemAdmin)}")

            /* combine explicit groups with materialized implicit groups */
            /* here we don't add the KnownUser group, as this would inflate the whole thing. */
            /* we instead inject the relevant information in defaultObjectAccessPermissionsStringForEntityGetV1 */
            allGroups = groups ::: projectMembers ::: projectAdmins ::: systemAdmin
            groupsPerProject = allGroups.groupBy(_._1).map { case (k,v) => (k,v.map(_._2))}
            _= log.debug(s"permissionsProfileGetV1 - groupsPerProject: ${MessageUtil.toSource(groupsPerProject)}")

            /* retrieve the administrative permissions for each group per project the user is member of */
            administrativePermissionsPerProjectFuture: Future[Map[IRI, Set[PermissionV1]]] = if (projectIris.nonEmpty) {
                userAdministrativePermissionsGetV1(groupsPerProject)
            } else {
                Future(Map.empty[IRI, Set[PermissionV1]])
            }
            administrativePermissionsPerProject <- administrativePermissionsPerProjectFuture

            /* retrieve the default object access permissions for each group per project the user is member of */
            defaultObjectAccessPermissionsPerProjectFuture: Future[Map[IRI, Set[PermissionV1]]] = if (projectIris.nonEmpty) {
                userDefaultObjectAccessPermissionsGetV1(groupsPerProject)
            } else {
                Future(Map.empty[IRI, Set[PermissionV1]])
            }
            defaultObjectAccessPermissionsPerProject <- defaultObjectAccessPermissionsPerProjectFuture

            /* construct the permission profile from the different parts */
            result = PermissionDataV1(
                groupsPerProject = groupsPerProject,
                administrativePermissionsPerProject = administrativePermissionsPerProject,
                defaultObjectAccessPermissionsPerProject = defaultObjectAccessPermissionsPerProject
            )
            //_ = log.debug(s"permissionsDataGetV1 - resulting permissionData: $result")

        } yield result
    }

    /**
      * By providing all the projects and groups in which the user is a member of, calculate the user's
      * max administrative permissions of each project.
      *
      * @param groupsPerProject the groups in each project the user is member of.
      * @return a the user's max permissions for each project.
      */
    def userAdministrativePermissionsGetV1(groupsPerProject: Map[IRI, List[IRI]]): Future[Map[IRI, Set[PermissionV1]]] = {

        /* Get all permissions per project, combining them from all groups */
        val ppf: Iterable[Future[(IRI, Seq[PermissionV1])]] = for {
            (projectIri, groups) <- groupsPerProject
            groupIri <- groups
            //_ = log.debug(s"userAdministrativePermissionsGetV1 - projectIri: $projectIri, groupIri: $groupIri")
            projectPermission: Future[(IRI, Seq[PermissionV1])] = administrativePermissionForProjectGroupGetV1(projectIri, groupIri).map {
                case Some(ap: AdministrativePermissionV1) => (projectIri, ap.hasPermissions.toSeq)
                case None => (projectIri, Seq.empty[PermissionV1])
            }

        } yield projectPermission

        val allPermissionsFuture: Future[Iterable[(IRI, Seq[PermissionV1])]] = Future.sequence(ppf)

        /* combines all permissions for each project and removes duplicate permissions inside a project  */
        val result: Future[Map[IRI, Set[PermissionV1]]] = for {
            allPermissions: Iterable[(IRI, Seq[PermissionV1])] <- allPermissionsFuture

            // remove instances with empty PermissionV1 sets
            cleanedAllPermissions: Iterable[(IRI, Seq[PermissionV1])] = allPermissions.filter(_._2.nonEmpty)

            result = cleanedAllPermissions.groupBy(_._1).map { case (projectIri: IRI, projectPermissions: Iterable[(IRI, Seq[PermissionV1])]) =>

                /* Combine permission sequences */
                val combined = projectPermissions.foldLeft(Seq.empty[PermissionV1]) { (acc, seq) =>
                    acc ++ seq._2
                }
                /* Remove possible duplicate permissions */
                val squashed: Set[PermissionV1] = PermissionUtilV1.removeDuplicatePermissions(combined)
                (projectIri, squashed)
            }
        //_ = log.debug(s"userAdministrativePermissionsGetV1 - result: $result")
        } yield result
        result
    }

    /**
      * By providing all the projects and groups in which the user is a member of, calculate the user's
      * max default object permissions for each project.
      * @param groupsPerProject
      * @return
      */
    def userDefaultObjectAccessPermissionsGetV1(groupsPerProject: Map[IRI, List[IRI]]): Future[Map[IRI, Set[PermissionV1]]] = {

        /* Get all default object access permissions per project, combining them from all groups */
        val ppf: Iterable[Future[(IRI, Seq[PermissionV1])]] = for {
            (projectIri, groups) <- groupsPerProject
            groupIri <- groups
            _ = log.debug(s"userDefaultObjectAccessPermissionsGetV1 - projectIri: $projectIri, groupIri: $groupIri")

            projectPermission: Future[(IRI, Seq[PermissionV1])] = defaultObjectAccessPermissionGetV1(projectIri = projectIri, groupIri = Some(groupIri), resourceClassIri = None, propertyIri = None).map {
                case Some(doap: DefaultObjectAccessPermissionV1) => (projectIri, doap.hasPermissions.toSeq)
                case None => (projectIri, Seq.empty[PermissionV1])
            }

        } yield projectPermission

        val allPermissionsFuture: Future[Iterable[(IRI, Seq[PermissionV1])]] = Future.sequence(ppf)

        /* combines all permissions for each project and removes duplicate permissions inside a project  */
        val result: Future[Map[IRI, Set[PermissionV1]]] = for {
            allPermissions: Iterable[(IRI, Seq[PermissionV1])] <- allPermissionsFuture

            // remove instances with empty PermissionV1 sets
            cleanedAllPermissions: Iterable[(IRI, Seq[PermissionV1])] = allPermissions.filter(_._2.nonEmpty)

            result = cleanedAllPermissions.groupBy(_._1).map { case (projectIri: IRI, projectPermissions: Iterable[(IRI, Seq[PermissionV1])]) =>

                /* Combine permission sequences */
                val combined = projectPermissions.foldLeft(Seq.empty[PermissionV1]) { (acc, seq) =>
                    acc ++ seq._2
                }
                /* Remove possible duplicate permissions */
                val squashed: Set[PermissionV1] = PermissionUtilV1.removeDuplicatePermissions(combined)
                (projectIri, squashed)
            }
        _ = log.debug(s"userDefaultObjectAccessPermissionsGetV1 - result: $result")
        } yield result
        result
    }


    /*************************************************************************/
    /* ADMINISTRATIVE PERMISSIONS                                            */
    /*************************************************************************/

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
                case (permissionIri: String, rows: Seq[VariableResultsRow]) => (permissionIri, rows.map {
                    case row => (row.rowMap("p"), row.rowMap("o"))
                }.toMap)
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
            _ <- Future{
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

    /*************************************************************************/
    /* OBJECT ACCESS PERMISSIONS                                             */
    /*************************************************************************/

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

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }
            //_ = log.debug(s"objectAccessPermissionsForResourceGetV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

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
      *Gets all permissions attached to the value.
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

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }
            //_ = log.debug(s"objectAccessPermissionsForValueGetV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

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


    /*************************************************************************/
    /* DEFAULT OBJECT ACCESS PERMISSIONS                                     */
    /*************************************************************************/

    /**
      * Gets all IRI's of all default object access permissions defined inside a project.
      *
      * @param projectIri    the IRI of the project.
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
      * Gets a single default object access permission identified by project and either group / resource class / property.
      *
      * @param projectIri the project's IRI.
      * @param groupIri the group's IRI.
      * @param resourceClassIri the resource's class IRI
      * @param propertyIri the property's IRI.
      * @return an optional [[DefaultObjectAccessPermissionV1]]
      */
    def defaultObjectAccessPermissionGetV1(projectIri: IRI, groupIri: Option[IRI], resourceClassIri: Option[IRI], propertyIri: Option[IRI]): Future[Option[DefaultObjectAccessPermissionV1]] = {
        for {
            // check if necessary field are not empty.
            _ <- Future(if (projectIri.isEmpty) throw BadRequestException("Project cannot be empty"))

            // check supplied parameters.
            parametersSupplied = List(groupIri, resourceClassIri, propertyIri).flatten.size
            _ = if (parametersSupplied != 1) throw BadRequestException("Either groupIri or resourceClassIri or propertyTypeIri can be supplied")

            sparqlQueryString = queries.sparql.v1.txt.getDefaultObjectAccessPermission(
                triplestore = settings.triplestoreType,
                projectIri = projectIri,
                maybeGroupIri= groupIri,
                maybeResourceClassIri = resourceClassIri,
                maybePropertyIri = propertyIri
            ).toString()
            //_ = log.debug(s"defaultObjectAccessPermissionGetV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"defaultObjectAccessPermissionGetV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            permission: Option[DefaultObjectAccessPermissionV1] = if (permissionQueryResponseRows.nonEmpty) {

                /* check if we only got one default object access permission back */
                val doapCount: Int = permissionQueryResponseRows.groupBy(_.rowMap("s")).size
                if (doapCount > 1) throw InconsistentTriplestoreDataException(s"Only one default object permission instance allowed for project: $projectIri and group: $groupIri combination, but found $doapCount.")

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
            //_ = log.debug(s"defaultObjectAccessPermissionGetV1 - p: $projectIRI, g: $groupIRI, r: $resourceClassIRI, p: $propertyIRI, permission: $permission")
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
      * Returns a string containing default object permissions statements ready for usage during creation of a new resource.
      * The permissions include any default object access permissions defined for the resource class and on any groups the
      * user is member of.
      *
      * @param projectIri the IRI of the project.
      * @param resourceClassIri the IRI of the resource class for which the default object access permissions are requested.
      * @param propertyIri the IRI of the property for which the default object access permissions are requested.
      * @param permissionData the permission data of the user for which the default object access permissions are requested.
      * @return an optional string with object access permission statements
      */
    def defaultObjectAccessPermissionsStringForEntityGetV1(projectIri: IRI, resourceClassIri: Option[IRI], propertyIri: Option[IRI], permissionData: PermissionDataV1): Future[DefaultObjectAccessPermissionsStringResponseV1] = {

        //log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - projectIRI: $projectIRI, resourceClassIRI: $resourceClassIRI, propertyIRI: $propertyIRI, permissionData:$permissionData")
        for {
            // check if necessary field are defined.
            _ <- Future(if (projectIri.isEmpty) throw BadRequestException("Project cannot be empty"))
            _ = if (resourceClassIri.isEmpty && propertyIri.isEmpty) throw BadRequestException("Either resourceClassIri or propertyTypeIri need to be supplied")
            _ = if (resourceClassIri.isDefined && propertyIri.isDefined) throw BadRequestException("Not allowed to supply both resourceClassIri and propertyTypeIri")

            /* Get the default object access permissions for the knora-base:KnownUser group */
            defaultPermissionsOnKnownUserOption: Option[DefaultObjectAccessPermissionV1] <- defaultObjectAccessPermissionGetV1(projectIri = projectIri, groupIri = Some(OntologyConstants.KnoraBase.KnownUser), resourceClassIri = None, propertyIri = None)

            defaultPermissionsOnKnownUser: Set[PermissionV1] = defaultPermissionsOnKnownUserOption match {
                case Some(doap) => doap.hasPermissions
                case None => Set.empty[PermissionV1]
            }
            _ = log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnKnownUser: $defaultPermissionsOnKnownUser")

            /* Get the user's max default object access permissions from the user's permission data inside the user's profile */
            defaultPermissionsOnProjectGroups: Set[PermissionV1] = permissionData.defaultObjectAccessPermissionsPerProject.getOrElse(projectIri, Set.empty[PermissionV1])
            _ = log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnProjectGroups: $defaultPermissionsOnProjectGroups")

            /* Get the default object access permissions defined on the resource class for the current project */
            defaultPermissionsOnProjectEntityOption: Option[DefaultObjectAccessPermissionV1] <- defaultObjectAccessPermissionGetV1(projectIri = projectIri, groupIri = None, resourceClassIri = resourceClassIri, propertyIri = propertyIri)

            defaultPermissionsOnProjectEntity: Set[PermissionV1] = defaultPermissionsOnProjectEntityOption match {
                case Some(doap) => doap.hasPermissions
                case None => Set.empty[PermissionV1]
            }
            _ = log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnProjectEntity: $defaultPermissionsOnProjectEntity")

            /* Since we also have default object access permissions defined in the SystemProject,
               we need to check there too, but only in the case that we didn't find anything
               on the project level, since the project level definition overrides the system level definition. */
            systemProject = OntologyConstants.KnoraBase.SystemProject
            defaultPermissionsOnSystemEntityOptionF: Future[Option[DefaultObjectAccessPermissionV1]] = if (defaultPermissionsOnProjectEntity.isEmpty) {
                defaultObjectAccessPermissionGetV1(projectIri = systemProject, groupIri = None, resourceClassIri = resourceClassIri, propertyIri = propertyIri)
            } else {
                Future(None)
            }
            defaultPermissionsOnSystemEntityOption <- defaultPermissionsOnSystemEntityOptionF
            defaultPermissionsOnSystemEntity: Set[PermissionV1] = defaultPermissionsOnSystemEntityOption match {
                case Some(doap) => doap.hasPermissions
                case None => Set.empty[PermissionV1]
            }
            _ = log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsOnSystemEntity: $defaultPermissionsOnSystemEntity")

            /* Get the default permissions defined for the SystemAdmin group. This is a kind of fallback if the
               SystemAdmin user is not part of the project and could result in an empty permissions string. */
            defaultPermissionsForSystemAdminOption: Option[DefaultObjectAccessPermissionV1] <- defaultObjectAccessPermissionGetV1(projectIri = OntologyConstants.KnoraBase.SystemProject, groupIri = Some(OntologyConstants.KnoraBase.SystemAdmin), resourceClassIri = None, propertyIri = None)
            defaultPermissionsForSystemAdmin = if (permissionData.isSystemAdmin) {
                defaultPermissionsForSystemAdminOption match {
                    case Some(doap) => doap.hasPermissions
                    case None => Set.empty[PermissionV1]
                }
            } else {
                Set.empty[PermissionV1]
            }
            _ = log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissionsForSystemAdmin: $defaultPermissionsForSystemAdmin")

            /* Combine all five together and return most permissive */
            allDefaultPermissions: Seq[PermissionV1] = defaultPermissionsOnKnownUser.toSeq ++ defaultPermissionsOnProjectGroups.toSeq ++ defaultPermissionsOnProjectEntity.toSeq ++ defaultPermissionsOnSystemEntity.toSeq ++ defaultPermissionsForSystemAdmin.toSeq
            //_ = log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - allDefaultPermissions: $allDefaultPermissions")

            /* Remove duplicate permissions */
            deduplicatedDefaultPermissions = PermissionUtilV1.removeDuplicatePermissions(allDefaultPermissions)
            //_ = log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - deduplicatedDefaultPermissions: $deduplicatedDefaultPermissions")

            /* Remove lesser permissions contained in higher ones */
            defaultPermissions = PermissionUtilV1.removeLesserPermissions(deduplicatedDefaultPermissions, PermissionType.OAP)
            //_ = log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - defaultPermissions: $defaultPermissions")

            /* Create permissions string */
            result = PermissionUtilV1.formatPermissions(defaultPermissions, PermissionType.OAP)
            // _ = log.debug(s"defaultObjectAccessPermissionsStringForEntityGetV1 - result: $result")
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
