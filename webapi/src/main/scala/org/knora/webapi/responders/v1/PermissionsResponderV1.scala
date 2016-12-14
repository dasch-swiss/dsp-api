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

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.groupmessages.{GroupInfoByIRIGetRequest, GroupInfoResponseV1, GroupInfoType}
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionType.PermissionType
import org.knora.webapi.messages.v1.responder.permissionmessages.{AdministrativePermissionForProjectGroupGetResponseV1, AdministrativePermissionV1, DefaultObjectAccessPermissionGetResponseV1, DefaultObjectAccessPermissionV1, PermissionType, _}
import org.knora.webapi.messages.v1.responder.usermessages._
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.KnoraIdUtil

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
        case ObjectAccessPermissionsForResourceGetV1(resourceIri, projectIri) => future2Message(sender(), objectAccessPermissionsForResourceGetV1(resourceIri, projectIri) , log)
        case ObjectAccessPermissionsForValueGetV1(valueIri, projectIri) => future2Message(sender(), objectAccessPermissionsForValueGetV1(valueIri, projectIri), log)
        case DefaultObjectAccessPermissionsForProjectGetRequestV1(projectIri, userProfileV1) => future2Message(sender(), defaultObjectAccessPermissionsForProjectGetRequestV1(projectIri, userProfileV1), log)
        case DefaultObjectAccessPermissionForIriGetRequestV1(defaultObjectAccessPermissionIri, userProfileV1) => future2Message(sender(), defaultObjectAccessPermissionForIriGetRequestV1(defaultObjectAccessPermissionIri, userProfileV1), log)
        case DefaultObjectAccessPermissionGetRequestV1(projectIri, groupIri, resourceClassIri, propertyIri, userProfile) => future2Message(sender(), defaultObjectAccessPermissionGetRequestV1(projectIri, groupIri, resourceClassIri, propertyIri, userProfile), log)
        case DefaultObjectAccessPermissionsStringForResourceClassGetV1(projectIri, resourceClassIri, permissionData) => future2Message(sender(), defaultObjectAccessPermissionsStringForEntityGetV1(projectIri, Some(resourceClassIri), None, permissionData), log)
        case DefaultObjectAccessPermissionsStringForPropertyGetV1(projectIri,propertyTypeIri, permissionData) => future2Message(sender(), defaultObjectAccessPermissionsStringForEntityGetV1(projectIri, None, Some(propertyTypeIri), permissionData), log)
        //case DefaultObjectAccessPermissionCreateRequestV1(newDefaultObjectAccessPermissionV1, userProfileV1) => future2Message(sender(), createDefaultObjectAccessPermissionV1(newDefaultObjectAccessPermissionV1, userProfileV1), log)
        //case DefaultObjectAccessPermissionDeleteRequestV1(defaultObjectAccessPermissionIri, userProfileV1) => future2Message(sender(), deleteDefaultObjectAccessPermissionV1(defaultObjectAccessPermissionIri, userProfileV1), log)
        //case TemplatePermissionsCreateRequestV1(projectIri, permissionsTemplate, userProfileV1) => future2Message(sender(), templatePermissionsCreateRequestV1(projectIri, permissionsTemplate, userProfileV1), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
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
            a <- Future("")

            /* If the user is member of the SystemAdmin group, then we also need to return the SystemProject ProjectInfo */
            extendedProjectIris: Seq[IRI] = if (isInSystemAdminGroup) {
                projectIris.toList ::: List(OntologyConstants.KnoraBase.SystemProject)
            } else {
                projectIris
            }
            //_ = log.debug(s"permissionsProfileGetV1 - extendedProjectIris: $extendedProjectIris")

            // find out to which project each group belongs to
            //_ = log.debug("getPermissionsProfileV1 - find out to which project each group belongs to")
            groups: List[(IRI, IRI)] = if (groupIris.nonEmpty) {
                groupIris.map {
                    groupIri => {
                        val resFuture = for {
                            groupInfo <- (responderManager ? GroupInfoByIRIGetRequest(groupIri, GroupInfoType.SAFE, None)).mapTo[GroupInfoResponseV1]
                            res = (groupInfo.group_info.belongsToProject, groupIri)
                        } yield res
                        Await.result(resFuture, 1.seconds)
                    }
                }.toList
            } else {
                List.empty[(IRI, IRI)]
            }
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


            //ToDo: Maybe we need to add KnownUser group for all other projects
            /* combine explicit groups with materialized implicit groups */
            allGroups = groups ::: projectMembers ::: projectAdmins ::: systemAdmin
            groupsPerProject = allGroups.groupBy(_._1).map { case (k,v) => (k,v.map(_._2))}
            //_= log.debug(s"permissionsProfileGetV1 - groupsPerProject: ${MessageUtil.toSource(groupsPerProject)}")

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
                //projectInfos = projectInfos,
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
            //_ = log.debug(s"getUserAdministrativePermissionsRequestV1 - projectIri: $projectIri, groupIri: $groupIri")
            projectPermission: Future[(IRI, Seq[PermissionV1])] = administrativePermissionForProjectGroupGetV1(projectIri, groupIri).map {
                case Some(ap: AdministrativePermissionV1) => (projectIri, ap.hasPermissions.toSeq)
                case None => (projectIri, Seq.empty[PermissionV1])
            }

        } yield projectPermission

        val allPermissionsFuture: Future[Iterable[(IRI, Seq[PermissionV1])]] = Future.sequence(ppf)

        /* combines all permissions for each project and removes duplicate permissions inside a project  */
        val result: Future[Map[IRI, Set[PermissionV1]]] = for {
            allPermission <- allPermissionsFuture
            result = allPermission.groupBy(_._1).map { case (k, v) =>

                /* Combine permission sequences */
                val combined = v.foldLeft(Seq.empty[PermissionV1]) { (acc, seq) =>
                    acc ++ seq._2
                }
                /* Remove possible duplicate permissions */
                val squashed: Set[PermissionV1] = removeDuplicatePermissions(combined)
                (k, squashed)
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
            //_ = log.debug(s"getUserAdministrativePermissionsRequestV1 - projectIri: $projectIri, groupIri: $groupIri")
            projectPermission: Future[(IRI, Seq[PermissionV1])] = defaultObjectAccessPermissionGetV1(projectIRI = projectIri, groupIRI = Some(groupIri), resourceClassIRI = None, propertyIRI = None).map {
                case Some(doap: DefaultObjectAccessPermissionV1) => (projectIri, doap.hasPermissions.toSeq)
                case None => (projectIri, Seq.empty[PermissionV1])
            }

        } yield projectPermission

        val allPermissionsFuture: Future[Iterable[(IRI, Seq[PermissionV1])]] = Future.sequence(ppf)

        /* combines all permissions for each project and removes duplicate permissions inside a project  */
        val result: Future[Map[IRI, Set[PermissionV1]]] = for {
            allPermission <- allPermissionsFuture
            result = allPermission.groupBy(_._1).map { case (k, v) =>

                /* Combine permission sequences */
                val combined = v.foldLeft(Seq.empty[PermissionV1]) { (acc, seq) =>
                    acc ++ seq._2
                }
                /* Remove possible duplicate permissions */
                val squashed: Set[PermissionV1] = removeDuplicatePermissions(combined)
                (k, squashed)
            }
        //_ = log.debug(s"userDefaultObjectAccessPermissionsGetV1 - result: $result")
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

        // FIXME: Check user's permission for operation

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getAdministrativePermissionsForProject(
                triplestore = settings.triplestoreType,
                projectIri = projectIRI
            ).toString())
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - query: $sparqlQueryString")

            permissionsQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - result: ${MessageUtil.toSource(permissionsQueryResponse)}")

            /* extract response rows */
            permissionsQueryResponseRows: Seq[VariableResultsRow] = permissionsQueryResponse.results.bindings

            permissionsWithProperties: Map[String, Map[String, String]] = permissionsQueryResponseRows.groupBy(_.rowMap("s")).map {
                case (permissionIri: String, rows: Seq[VariableResultsRow]) => (permissionIri, rows.map {
                    case row => (row.rowMap("p"), row.rowMap("o"))
                }.toMap)
            }
            //_ = log.debug(s"getAdministrativePermissionsForProjectV1 - permissionsWithProperties: $permissionsWithProperties")

            administrativePermissions: Seq[AdministrativePermissionV1] = permissionsWithProperties.map {
                case (permissionIri: IRI, propsMap: Map[String, String]) =>

                    /* parse permissions */
                    val hasPermissions: Set[PermissionV1] = parsePermissions(propsMap.get(OntologyConstants.KnoraBase.HasPermissions), PermissionType.AP)

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
      * @param administrativePermissionIRI the IRI of the administrative permission.
      * @return a single [[AdministrativePermissionV1]] object.
      */
    def administrativePermissionForIriGetRequestV1(administrativePermissionIRI: IRI, userProfileV1: UserProfileV1): Future[AdministrativePermissionForIriGetResponseV1] = {

        // FIXME: Check user's permission for operation

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getAdministrativePermissionByIRI(
                triplestore = settings.triplestoreType,
                administrativePermissionIri = administrativePermissionIRI
            ).toString())
            //_ = log.debug(s"getAdministrativePermissionForIriV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getAdministrativePermissionForIriV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            /* extract response rows */
            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }

            //_ = log.debug(s"getAdministrativePermissionForIriV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

            /* check if we have found something */
            _ = if (groupedPermissionsQueryResponse.isEmpty) throw NotFoundException(s"Administrative permission $administrativePermissionIRI could not be found.")

            /* extract the permission */
            hasPermissions = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.AP)
            //_ = log.debug(s"getAdministrativePermissionForIriV1 - hasPermissions: ${MessageUtil.toSource(hasPermissions)}")

            /* construct the permission object */
            permission = AdministrativePermissionV1(iri = administrativePermissionIRI, forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $administrativePermissionIRI has no project attached")).head, forGroup = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Permission $administrativePermissionIRI has no group attached")).head, hasPermissions = hasPermissions)

            /* construct the response object */
            response = AdministrativePermissionForIriGetResponseV1(permission)

        } yield response
    }

    /**
      * Gets a single administrative permission identified by project and group.
      *
      * @param projectIRI the project.
      * @param groupIRI   the group.
      * @return an option containing an [[AdministrativePermissionV1]]
      */
    private def administrativePermissionForProjectGroupGetV1(projectIRI: IRI, groupIRI: IRI): Future[Option[AdministrativePermissionV1]] = {
        for {
            a <- Future("")

            // check if necessary field are not empty.
            _ = if (projectIRI.isEmpty) throw BadRequestException("Project cannot be empty")
            _ = if (groupIRI.isEmpty) throw BadRequestException("Group cannot be empty")

            sparqlQueryString <- Future(queries.sparql.v1.txt.getAdministrativePermissionForProjectAndGroup(
                triplestore = settings.triplestoreType,
                projectIri = projectIRI,
                groupIri = groupIRI
            ).toString())
            //_ = log.debug(s"administrativePermissionForProjectGroupGetV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getAdministrativePermissionForProjectGroupV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            permission: Option[AdministrativePermissionV1] = if (permissionQueryResponseRows.nonEmpty) {

                /* check if we only got one administrative permission back */
                val apCount: Int = permissionQueryResponseRows.groupBy(_.rowMap("s")).size
                if (apCount > 1) throw InconsistentTriplestoreDataException(s"Only one administrative permission instance allowed for project: $projectIRI and group: $groupIRI combination, but found $apCount.")

                /* get the iri of the retrieved permission */
                val returnedPermissionIri = permissionQueryResponse.getFirstRow.rowMap("s")

                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.AP)
                Some(
                    AdministrativePermissionV1(iri = returnedPermissionIri, forProject = projectIRI, forGroup = groupIRI, hasPermissions = hasPermissions)
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
      * @param projectIRI the project.
      * @param groupIRI   the group.
      * @return an [[AdministrativePermissionForProjectGroupGetResponseV1]]
      */
    private def administrativePermissionForProjectGroupGetRequestV1(projectIRI: IRI, groupIRI: IRI, userProfile: UserProfileV1): Future[AdministrativePermissionForProjectGroupGetResponseV1] = {

        // FIXME: Check user's permission for operation

        for {
            ap <- administrativePermissionForProjectGroupGetV1(projectIRI, groupIRI)
            result = ap match {
                case Some(ap) => AdministrativePermissionForProjectGroupGetResponseV1(ap)
                case None => throw NotFoundException(s"No Administrative Permission found for project: $projectIRI, group: $groupIRI combination")
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

        // FIXME: Check user's permission for operation

        for {
            a <- Future("")

            // check if necessary field are not empty.
            _ = if (newAdministrativePermissionV1.forProject.isEmpty) throw BadRequestException("Project cannot be empty")
            _ = if (newAdministrativePermissionV1.forGroup.isEmpty) throw BadRequestException("Group cannot be empty")
            _ = if (newAdministrativePermissionV1.hasNewPermissions.isEmpty) throw BadRequestException("New permissions cannot be empty")

            checkResult <- administrativePermissionForProjectGroupGetV1(newAdministrativePermissionV1.forProject, newAdministrativePermissionV1.forGroup)

            _ = checkResult match {
                case Some(ap) => throw DuplicateValueException(s"Permission for project: '${newAdministrativePermissionV1.forProject}' and group: '${newAdministrativePermissionV1.forGroup}' combination already exists.")
                case None =>
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
      * @param resourceIRI the IRI of the resource.
      * @param projectIri the IRI of the project.
      * @return a sequence of [[PermissionV1]]
      */
    def objectAccessPermissionsForResourceGetV1(resourceIRI: IRI, projectIri: IRI): Future[Option[ObjectAccessPermissionV1]] = {
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getObjectAccessPermission(
                triplestore = settings.triplestoreType,
                resourceIri = resourceIRI,
                valueIri = ""
            ).toString())
            //_ = log.debug(s"getObjectAccessPermission - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

            permission: Option[ObjectAccessPermissionV1] = if (permissionQueryResponseRows.nonEmpty) {

                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions: Set[PermissionV1] = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.OAP)
                Some(
                    ObjectAccessPermissionV1(forResource = Some(resourceIRI), forValue = None, hasPermissions = hasPermissions)
                )
            } else {
                None
            }
        } yield permission
    }

    /**
      *Gets all permissions attached to the value.
      *
      * @param valueIRI the IRI of the value.
      * @param projectIri the IRI of the project.
      * @return a sequence of [[PermissionV1]]
      */
    def objectAccessPermissionsForValueGetV1(valueIRI: IRI, projectIri: IRI): Future[Option[ObjectAccessPermissionV1]] = {
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getObjectAccessPermission(
                triplestore = settings.triplestoreType,
                resourceIri = "",
                valueIri = valueIRI
            ).toString())
            //_ = log.debug(s"getObjectAccessPermission - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

            permission: Option[ObjectAccessPermissionV1] = if (permissionQueryResponseRows.nonEmpty) {

                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions: Set[PermissionV1] = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.OAP)
                Some(
                    ObjectAccessPermissionV1(forResource = None, forValue = Some(valueIRI), hasPermissions = hasPermissions)
                )
            } else {
                None
            }
        } yield permission
    }


    /*************************************************************************/
    /* DEFAULT OBJECT ACCESS PERMISSIONS                                     */
    /*************************************************************************/

    /**
      * Gets all IRI's of all default object access permissions defined inside a project.
      *
      * @param projectIRI    the IRI of the project.
      * @param userProfile the [[UserProfileV1]] of the requesting user.
      * @return a list of IRIs of [[DefaultObjectAccessPermissionV1]] objects.
      */
    private def defaultObjectAccessPermissionsForProjectGetRequestV1(projectIRI: IRI, userProfile: UserProfileV1): Future[DefaultObjectAccessPermissionsForProjectGetResponseV1] = {

        // FIXME: Check user's permission for operation

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getDefaultObjectAccessPermissionsForProject(
                triplestore = settings.triplestoreType,
                projectIri = projectIRI
            ).toString())
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - query: $sparqlQueryString")

            permissionsQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getProjectAdministrativePermissionsV1 - result: ${MessageUtil.toSource(permissionsQueryResponse)}")

            /* extract response rows */
            permissionsQueryResponseRows: Seq[VariableResultsRow] = permissionsQueryResponse.results.bindings

            permissionsWithProperties: Map[String, Map[String, String]] = permissionsQueryResponseRows.groupBy(_.rowMap("s")).map {
                case (permissionIri: String, rows: Seq[VariableResultsRow]) => (permissionIri, rows.map {
                    case row => (row.rowMap("p"), row.rowMap("o"))
                }.toMap)
            }
            //_ = log.debug(s"getAdministrativePermissionsForProjectV1 - permissionsWithProperties: $permissionsWithProperties")

            permissions: Seq[DefaultObjectAccessPermissionV1] = permissionsWithProperties.map {
                case (permissionIri: IRI, propsMap: Map[String, String]) =>

                    /* parse permissions */
                    val hasPermissions: Set[PermissionV1] = parsePermissions(propsMap.get(OntologyConstants.KnoraBase.HasPermissions), PermissionType.OAP)

                    /* construct permission object */
                    DefaultObjectAccessPermissionV1(iri = permissionIri, forProject = propsMap.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no project.")), forGroup = propsMap.get(OntologyConstants.KnoraBase.ForGroup), forResourceClass = propsMap.get(OntologyConstants.KnoraBase.ForResourceClass), forProperty = propsMap.get(OntologyConstants.KnoraBase.ForProperty), hasPermissions = hasPermissions)
            }.toSeq

            /* construct response object */
            response = DefaultObjectAccessPermissionsForProjectGetResponseV1(permissions)

        } yield response

    }

    /**
      * Gets a single default object access permission identified by it's IRI.
      *
      * @param permissionIri the IRI of the default object access permission.
      * @return a single [[DefaultObjectAccessPermissionV1]] object.
      */
    private def defaultObjectAccessPermissionForIriGetRequestV1(permissionIri: IRI, userProfileV1: UserProfileV1): Future[DefaultObjectAccessPermissionForIriGetResponseV1] = {

        // FIXME: Check user's permission for operation

        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getDefaultObjectAccessPermissionByIRI(
                triplestore = settings.triplestoreType,
                defaultObjectAccessPermissionIri = permissionIri
            ).toString())
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            _ = if (permissionQueryResponseRows.isEmpty) {
                throw NotFoundException(s"Permission '$permissionIri' not found")
            }

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

            hasPermissions = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.OAP)

            defaultObjectAccessPermission = DefaultObjectAccessPermissionV1(iri = permissionIri, forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no project.")).head, forGroup = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForGroup).map(_.head), forResourceClass = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForResourceClass).map(_.head), forProperty = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.ForProperty).map(_.head), hasPermissions = hasPermissions)

            result = DefaultObjectAccessPermissionForIriGetResponseV1(defaultObjectAccessPermission)

        } yield result
    }

    /**
      * Gets a single default object access permission identified by project and either group / resource class / property.
      *
      * @param projectIRI the project's IRI.
      * @param groupIRI the group's IRI.
      * @param resourceClassIRI the resource's class IRI
      * @param propertyIRI the property's IRI.
      * @return an optional [[DefaultObjectAccessPermissionV1]]
      */
    def defaultObjectAccessPermissionGetV1(projectIRI: IRI, groupIRI: Option[IRI], resourceClassIRI: Option[IRI], propertyIRI: Option[IRI]): Future[Option[DefaultObjectAccessPermissionV1]] = {
        for {
            a <- Future("")

            // check if necessary field are not empty.
            _ = if (projectIRI.isEmpty) throw BadRequestException("Project cannot be empty")

            // check supplied parameters.
            parametersSupplied = List(groupIRI, resourceClassIRI, propertyIRI).flatten.size
            _ = if (parametersSupplied != 1) throw BadRequestException("Either groupIri or resourceClassIri or propertyTypeIri can be supplied")

            sparqlQueryString <- Future(queries.sparql.v1.txt.getDefaultObjectAccessPermission(
                triplestore = settings.triplestoreType,
                projectIri = projectIRI,
                groupIri = groupIRI.getOrElse(""),
                resourceClassIri = resourceClassIRI.getOrElse(""),
                propertyIri = propertyIRI.getOrElse("")
            ).toString())
            //_ = log.debug(s"defaultObjectAccessPermissionForProjectGroupGetV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getAdministrativePermissionForProjectGroupV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            permission: Option[DefaultObjectAccessPermissionV1] = if (permissionQueryResponseRows.nonEmpty) {

                /* check if we only got one default object access permission back */
                val doapCount: Int = permissionQueryResponseRows.groupBy(_.rowMap("s")).size
                if (doapCount > 1) throw InconsistentTriplestoreDataException(s"Only one default object permission instance allowed for project: $projectIRI and group: $groupIRI combination, but found $doapCount.")

                /* get the iri of the retrieved permission */
                val permissionIri = permissionQueryResponse.getFirstRow.rowMap("s")

                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions: Set[PermissionV1] = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.OAP)
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
      * @param projectIRI
      * @param groupIRI
      * @param resourceClassIri
      * @param propertyIri
      * @param userProfile
      * @return a [[DefaultObjectAccessPermissionGetResponseV1]]
      */
    def defaultObjectAccessPermissionGetRequestV1(projectIRI: IRI, groupIRI: Option[IRI], resourceClassIri: Option[IRI], propertyIri: Option[IRI], userProfile: UserProfileV1): Future[DefaultObjectAccessPermissionGetResponseV1] = {

        // FIXME: Check user's permission for operation

        defaultObjectAccessPermissionGetV1(projectIRI, groupIRI, resourceClassIri, propertyIri)
                .mapTo[Option[DefaultObjectAccessPermissionV1]]
                .flatMap {
                    case Some(doap) => Future(DefaultObjectAccessPermissionGetResponseV1(doap))
                    case None => {
                        /* if the query was for a property, then we need to additionally check if it is a system property */
                        if (propertyIri.isDefined) {
                            val systemProject = OntologyConstants.KnoraBase.SystemProject
                            val doapF = defaultObjectAccessPermissionGetV1(systemProject, groupIRI, resourceClassIri, propertyIri)
                            doapF.mapTo[Option[DefaultObjectAccessPermissionV1]].map {
                                case Some(systemDoap) => DefaultObjectAccessPermissionGetResponseV1(systemDoap)
                                case None => throw NotFoundException(s"No Default Object Access Permission found for project: $projectIRI, group: $groupIRI, resourceClassIri: $resourceClassIri, propertyIri: $propertyIri combination")
                            }
                        } else {
                            throw NotFoundException(s"No Default Object Access Permission found for project: $projectIRI, group: $groupIRI, resourceClassIri: $resourceClassIri, propertyIri: $propertyIri combination")
                        }
                    }
        }
    }

    /**
      * Returns a string containing default object permissions statements ready for usage during creation of a new resource.
      * The permissions include any default object access permissions defined for the resource class and on any groups the
      * user is member of.
      *
      * @param projectIRI the IRI of the project.
      * @param resourceClassIRI the IRI of the resource class for which the default object access permissions are requested.
      * @param propertyIRI the IRI of the property for which the default object access permissions are requested.
      * @param permissionData the permission data of the user for which the default object access permissions are requested.
      * @return an optional string with object access permission statements
      */
    def defaultObjectAccessPermissionsStringForEntityGetV1(projectIRI: IRI, resourceClassIRI: Option[IRI], propertyIRI: Option[IRI], permissionData: PermissionDataV1): Future[Option[String]] = {

        for {
            a <- Future("")

            // check if necessary field are defined.
            _ = if (projectIRI.isEmpty) throw BadRequestException("Project cannot be empty")
            _ = if (resourceClassIRI.isEmpty && propertyIRI.isEmpty) throw BadRequestException("Either resourceClassIri or propertyTypeIri need to be supplied")
            _ = if (resourceClassIRI.isDefined && propertyIRI.isDefined) throw BadRequestException("Not allowed to supply both resourceClassIri and propertyTypeIri")

            /* Get the user's max default object access permissions from the user's permission data inside the user's profile */
            defaultPermissionsOnProjectGroups: Set[PermissionV1] = permissionData.defaultObjectAccessPermissionsPerProject.getOrElse(projectIRI, Set.empty[PermissionV1])

            /* Get the default object access permissions defined on the resource class for the current project */
            defaultPermissionsOnProjectEntityOption: Option[DefaultObjectAccessPermissionV1] <- defaultObjectAccessPermissionGetV1(projectIRI = projectIRI, groupIRI = None, resourceClassIRI = resourceClassIRI, propertyIRI = propertyIRI)

            defaultPermissionsOnProjectEntity: Set[PermissionV1] = defaultPermissionsOnProjectEntityOption match {
                case Some(doap) => doap.hasPermissions
                case None => Set.empty[PermissionV1]
            }

            // Since we also have default object access permissions defined in the SystemProject,
            // we need to check there too, but only in the case that we didn't find anything
            // on the project level, since the project level definition overrides the system level definition.
            systemProject = OntologyConstants.KnoraBase.SystemProject
            defaultPermissionsOnSystemEntityOptionF: Future[Option[DefaultObjectAccessPermissionV1]] = if (defaultPermissionsOnProjectEntity.isEmpty) {
                defaultObjectAccessPermissionGetV1(projectIRI = systemProject, groupIRI = None, resourceClassIRI = resourceClassIRI, propertyIRI = propertyIRI)
            } else {
                Future(None)
            }
            defaultPermissionsOnSystemEntityOption <- defaultPermissionsOnSystemEntityOptionF
            defaultPermissionsOnSystemEntity: Set[PermissionV1] = defaultPermissionsOnSystemEntityOption match {
                case Some(doap) => doap.hasPermissions
                case None => Set.empty[PermissionV1]
            }

            /* Combine all three together and return most permissive */
            allDefaultPermissions: Seq[PermissionV1] = defaultPermissionsOnProjectGroups.toSeq ++ defaultPermissionsOnProjectEntity.toSeq ++ defaultPermissionsOnSystemEntity.toSeq
            squashedAllDefaultPermissions = removeDuplicatePermissions(allDefaultPermissions)


            // FIXME: Need to actually do something here
            result = ???
        } yield result
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




    ///////////////////////////////////////////////////////////////////////////
    // Helper methods
    ///////////////////////////////////////////////////////////////////////////

    /**
      * -> Need this for reading permission literals.
      *
      * Parses the literal object of the predicate `knora-base:hasPermissions`.
      *
      * @param maybePermissionListStr the literal to parse.
      * @return a [[Map]] in which the keys are permission abbreviations in
      *         [[OntologyConstants.KnoraBase.ObjectAccessPermissionAbbreviations]], and the values are sets of
      *         user group IRIs.
      */
    def parsePermissions(maybePermissionListStr: Option[String], permissionType: PermissionType): Set[PermissionV1] = {
        maybePermissionListStr match {
            case Some(permissionListStr) => {
                val permissions: Seq[String] = permissionListStr.split(OntologyConstants.KnoraBase.PermissionListDelimiter)
                //log.debug(s"parsePermissions - split permissions: $permissions")
                permissions.flatMap {
                    permission =>
                        val splitPermission = permission.split(' ')
                        val abbreviation = splitPermission(0)

                        permissionType match {
                            case PermissionType.AP => {
                                if (!OntologyConstants.KnoraBase.AdministrativePermissionAbbreviations.contains(abbreviation)) {
                                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                                }
                                if (splitPermission.length > 1) {
                                    val shortGroups: Array[String] = splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter)
                                    val groups: Set[IRI] = shortGroups.map(_.replace(OntologyConstants.KnoraBase.KnoraBasePrefix, OntologyConstants.KnoraBase.KnoraBasePrefixExpansion)).toSet
                                    buildPermissionObject(abbreviation, groups)
                                } else {
                                    buildPermissionObject(abbreviation, Set.empty[IRI])
                                }
                            }
                            case PermissionType.OAP => {
                                if (!OntologyConstants.KnoraBase.ObjectAccessPermissionAbbreviations.contains(abbreviation)) {
                                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                                }
                                val shortGroups: Array[String] = splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter)
                                val groups: Set[IRI] = shortGroups.map(_.replace(OntologyConstants.KnoraBase.KnoraBasePrefix, OntologyConstants.KnoraBase.KnoraBasePrefixExpansion)).toSet
                                buildPermissionObject(abbreviation, groups)
                            }
                        }
                }
            }.toSet
            case None => Set.empty[PermissionV1]
        }
    }

    /**
      * Helper method used to convert the permission string stored inside the triplestore to a permission object.
      *
      * @param name the name of the permission.
      * @param iris the optional set of additional information (e.g., group IRIs, resource class IRIs).
      * @return a sequence of permission objects.
      */
    def buildPermissionObject(name: String, iris: Set[IRI]): Set[PermissionV1] = {
        name match {
            case OntologyConstants.KnoraBase.ProjectResourceCreateAllPermission => Set(PermissionV1.ProjectResourceCreateAllPermission)
            case OntologyConstants.KnoraBase.ProjectResourceCreateRestrictedPermission => {
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionV1.ProjectResourceCreateRestrictedPermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information!")
                }

            }
            case OntologyConstants.KnoraBase.ProjectAdminAllPermission => Set(PermissionV1.ProjectAdminAllPermission)
            case OntologyConstants.KnoraBase.ProjectAdminGroupAllPermission => Set(PermissionV1.ProjectAdminGroupAllPermission)
            case OntologyConstants.KnoraBase.ProjectAdminGroupRestrictedPermission => {
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionV1.ProjectAdminGroupRestrictedPermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information!")
                }
            }
            case OntologyConstants.KnoraBase.ProjectAdminRightsAllPermission => Set(PermissionV1.ProjectAdminRightsAllPermission)
            case OntologyConstants.KnoraBase.ProjectAdminOntologyAllPermission => Set(PermissionV1.ProjectAdminOntologyAllPermission)
            case OntologyConstants.KnoraBase.ChangeRightsPermission => {
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionV1.ChangeRightsPermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information!")
                }
            }
            case OntologyConstants.KnoraBase.DeletePermission => {
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionV1.DeletePermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information!")
                }
            }
            case OntologyConstants.KnoraBase.ModifyPermission => {
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionV1.ModifyPermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information!")
                }
            }
            case OntologyConstants.KnoraBase.ViewPermission => {
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionV1.ViewPermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information!")
                }
            }
            case OntologyConstants.KnoraBase.RestrictedViewPermission => {
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionV1.RestrictedViewPermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information!")
                }
            }
        }

    }

    /**
      * Helper method used to remove remove duplicate permissions.
      *
      * @param permissions the sequence of permissions with possible duplicates.
      * @return a set containing only unique permission.
      */
    def removeDuplicatePermissions(permissions: Seq[PermissionV1]): Set[PermissionV1] = {

        val result = permissions.groupBy(_.name).map { case (k, v) =>
            k match {
                case rest => v.head
            }
        }.toSet
        //log.debug(s"squashPermissions - result: $result")
        result
    }
}
