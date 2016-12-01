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
import org.knora.webapi.messages.v1.responder.permissionmessages.{AdministrativePermissionForProjectGroupGetResponseV1, AdministrativePermissionV1, PermissionType, _}
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
        case AdministrativePermissionForIriGetRequestV1(administrativePermissionIri, userProfileV1) => future2Message(sender(), administrativePermissionForIriGetRequestV1(administrativePermissionIri, userProfileV1), log)
        case AdministrativePermissionForProjectGroupGetV1(projectIri, groupIri, userProfileV1) => future2Message(sender(), administrativePermissionForProjectGroupGetV1(projectIri, groupIri), log)
        case AdministrativePermissionForProjectGroupGetRequestV1(projectIri, groupIri, userProfileV1) => future2Message(sender(), administrativePermissionForProjectGroupGetRequestV1(projectIri, groupIri), log)
        case AdministrativePermissionCreateRequestV1(newAdministrativePermissionV1, userProfileV1) => future2Message(sender(), createAdministrativePermissionV1(newAdministrativePermissionV1, userProfileV1), log)
        //case AdministrativePermissionDeleteRequestV1(administrativePermissionIri, userProfileV1) => future2Message(sender(), deleteAdministrativePermissionV1(administrativePermissionIri, userProfileV1), log)
        case ObjectAccessPermissionsForResourceGetV1(resourceIri, projectIri, userProfile) => future2Message(sender(), objectAccessPermissionsForResourceGetV1(resourceIri, projectIri, userProfile) , log)
        case ObjectAccessPermissionsForValueGetV1(valueIri, projectIri, userProfile) => future2Message(sender(), objectAccessPermissionsForValueGetV1(valueIri, projectIri, userProfile), log)
        case DefaultObjectAccessPermissionsForProjectGetRequestV1(projectIri, userProfileV1) => future2Message(sender(), defaultObjectAccessPermissionsForProjectGetRequestV1(projectIri, userProfileV1), log)
        case DefaultObjectAccessPermissionForIriGetRequestV1(defaultObjectAccessPermissionIri, userProfileV1) => future2Message(sender(), defaultObjectAccessPermissionForIriGetRequestV1(defaultObjectAccessPermissionIri, userProfileV1), log)
        case DefaultObjectAccessPermissionForProjectGroupGetRequestV1(projectIri, groupIri, userProfileV1) => future2Message(sender(), defaultObjectAccessPermissionForProjectGroupGetRequestV1(projectIri, groupIri, userProfileV1), log)
        case DefaultObjectAccessPermissionsForResourceClassGetV1(projectIri, resourceClassIri, userProfile) => future2Message(sender(), defaultObjectAccessPermissionsForResourceClassGetV1(projectIri, resourceClassIri, userProfile), log)
        case DefaultObjectAccessPermissionsForPropertyTypeGetV1(projectIri,propertyTypeIri, userProfile) => future2Message(sender(), defaultObjectAccessPermissionsForPropertyTypeGetV1(projectIri, propertyTypeIri, userProfile) , log)
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

            /* FIXME: I don't think that we need this anymore, as nothing pertaining to permissions is stored here
             * Retrieve short ProjectInfoV1s for each project the user is part of (now including the SystemProject
             */
            /*
            projectInfoFutures: Seq[Future[ProjectInfoV1]] = extendedProjectIris.map {
                projectIri => (responderManager ? ProjectInfoByIRIGetRequestV1(projectIri, ProjectInfoType.SHORT, None)).mapTo[ProjectInfoResponseV1] map (_.project_info)
            }

            projectInfos <- Future.sequence(projectInfoFutures)
            //_ = log.debug(s"permissionsProfileGetV1 - projectInfos: ${MessageUtil.toSource(projectInfos)}")
            */

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
            _ = log.debug(s"permissionsDataGetV1 - resulting permissionData: $result")

        } yield result



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
                    val hasPermissions: Seq[PermissionV1] = parsePermissions(propsMap.get(OntologyConstants.KnoraBase.HasPermissions), PermissionType.AP)

                    /* construct permission object */
                    AdministrativePermissionV1(
                        forProject = propsMap.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Administrative Permission $permissionIri has no project attached.")),
                        forGroup = propsMap.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Administrative Permission $permissionIri has no group attached.")),
                        hasPermissions = hasPermissions
                    )
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
            permission = AdministrativePermissionV1(
                    forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $administrativePermissionIRI has no project attached")).head,
                    forGroup = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Permission $administrativePermissionIRI has no group attached")).head,
                    hasPermissions = hasPermissions
                )

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

            /* check if we only got one administrative permission back */
            apCount: Int = permissionQueryResponseRows.groupBy(_.rowMap("s")).size
            _ = if (apCount > 1) throw InconsistentTriplestoreDataException(s"Only one administrative permission instance allowed for project: $projectIRI and group: $groupIRI combination, but found $apCount.")

            permission: Option[AdministrativePermissionV1] = if (permissionQueryResponseRows.nonEmpty) {
                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.AP)
                Some(
                    AdministrativePermissionV1(
                        forProject = projectIRI,
                        forGroup = groupIRI,
                        hasPermissions = hasPermissions
                    )
                )
            } else {
                None
            }
            //_ = log.debug(s"getAdministrativePermissionForProjectGroupV1 - administrativePermission: $administrativePermission")
        } yield permission
    }

    /**
      * Gets a single administrative permission identified by project and group.
      *
      * @param projectIRI the project.
      * @param groupIRI   the group.
      * @return an [[AdministrativePermissionForProjectGroupGetResponseV1]]
      */
    private def administrativePermissionForProjectGroupGetRequestV1(projectIRI: IRI, groupIRI: IRI): Future[AdministrativePermissionForProjectGroupGetResponseV1] = {
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
    private def createAdministrativePermissionV1(newAdministrativePermissionV1: NewAdministrativePermissionV1, userProfileV1: UserProfileV1): Future[AdministrativePermissionCreateResponseV1] = {
        //log.debug("createAdministrativePermissionV1")
        for {
            a <- Future("")

            // check if necessary field are not empty.
            _ = if (newAdministrativePermissionV1.forProject.isEmpty) throw BadRequestException("Project cannot be empty")
            _ = if (newAdministrativePermissionV1.forGroup.isEmpty) throw BadRequestException("Group cannot be empty")
            _ = if (newAdministrativePermissionV1.hasPermissions.isEmpty) throw BadRequestException("Permissions cannot be empty")

            checkResult <- administrativePermissionForProjectGroupGetV1(newAdministrativePermissionV1.forProject, newAdministrativePermissionV1.forGroup)

            _ = checkResult match {
                case Some(ap) => throw DuplicateValueException(s"Permission for project: '${newAdministrativePermissionV1.forProject}' and group: '${newAdministrativePermissionV1.forGroup}' combination already exists.")
                case None =>
            }

            response = AdministrativePermissionCreateResponseV1(administrativePermission = AdministrativePermissionV1(forProject = "mock-project", forGroup = "mock-group", hasPermissions = Seq.empty[PermissionV1]))

        } yield response


    }

/*
    private def deleteAdministrativePermissionV1(administrativePermissionIri: IRI, userProfileV1: UserProfileV1): Future[AdministrativePermissionOperationResponseV1] = ???
*/

    /*************************************************************************/
    /* OBJECT ACCESS PERMISSIONS                                             */
    /*************************************************************************/

    /**
      *
      * @param resourceIri
      * @param projectIri
      * @param userProfile
      * @return
      */
    def objectAccessPermissionsForResourceGetV1(resourceIri: IRI, projectIri: IRI, userProfile: UserProfileV1): Future[Seq[PermissionV1]] = {
        Future(Seq.empty[PermissionV1])
    }

    /**
      *
      * @param valueIri
      * @param projectIri
      * @param userProfile
      * @return
      */
    def objectAccessPermissionsForValueGetV1(valueIri: IRI, projectIri: IRI, userProfile: UserProfileV1): Future[Seq[PermissionV1]] = {
        Future(Seq.empty[PermissionV1])
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
                    val hasPermissions: Seq[PermissionV1] = parsePermissions(propsMap.get(OntologyConstants.KnoraBase.HasPermissions), PermissionType.DOAP)

                    /* construct permission object */
                    DefaultObjectAccessPermissionV1(
                        forProject = propsMap.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no project.")),
                        forGroup = propsMap.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no group.")),
                        forResourceClass = propsMap.getOrElse(OntologyConstants.KnoraBase.ForResourceClass, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no resource class.")),
                        forProperty = propsMap.getOrElse(OntologyConstants.KnoraBase.ForProperty, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no property.")),
                        hasPermissions = hasPermissions
                    )
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
        for {
            sparqlQueryString <- Future(queries.sparql.v1.txt.getDefaultObjectAccessPermissionByIRI(
                triplestore = settings.triplestoreType,
                defaultObjectAccessPermissionIri = permissionIri
            ).toString())
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
            }
            //_ = log.debug(s"getDefaultObjectAccessPermissionV1 - groupedResult: ${MessageUtil.toSource(groupedPermissionsQueryResponse)}")

            hasPermissions = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.DOAP)

            // TODO: Handle IRI not found, i.e. should return Option
            defaultObjectAccessPermission = DefaultObjectAccessPermissionV1(
                forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no project.")).head,
                forGroup = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no group.")).head,
                forResourceClass = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForResourceClass, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no resource class.")).head,
                forProperty = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProperty, throw InconsistentTriplestoreDataException(s"Permission $permissionIri has no property.")).head,
                hasPermissions = hasPermissions
            )

            result = DefaultObjectAccessPermissionForIriGetResponseV1(defaultObjectAccessPermission)

        } yield result
    }

    def defaultObjectAccessPermissionForProjectGroupGetV1(projectIRI: IRI, groupIRI: IRI, userProfileV1: UserProfileV1): Future[Option[DefaultObjectAccessPermissionV1]] = {
        for {
            a <- Future("")

            // check if necessary field are not empty.
            _ = if (projectIRI.isEmpty) throw BadRequestException("Project cannot be empty")
            _ = if (groupIRI.isEmpty) throw BadRequestException("Group cannot be empty")

            sparqlQueryString <- Future(queries.sparql.v1.txt.getDefaultObjectAccessPermissionForProjectAndGroup(
                triplestore = settings.triplestoreType,
                projectIri = projectIRI,
                groupIri = groupIRI
            ).toString())
            //_ = log.debug(s"defaultObjectAccessPermissionForProjectGroupGetV1 - query: $sparqlQueryString")

            permissionQueryResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"getAdministrativePermissionForProjectGroupV1 - result: ${MessageUtil.toSource(permissionQueryResponse)}")

            permissionQueryResponseRows: Seq[VariableResultsRow] = permissionQueryResponse.results.bindings

            /* check if we only got one administrative permission back */
            doapCount: Int = permissionQueryResponseRows.groupBy(_.rowMap("s")).size
            _ = if (doapCount > 1) throw InconsistentTriplestoreDataException(s"Only one default object permission instance allowed for project: $projectIRI and group: $groupIRI combination, but found $doapCount.")

            permission: Option[DefaultObjectAccessPermissionV1] = if (permissionQueryResponseRows.nonEmpty) {
                val groupedPermissionsQueryResponse: Map[String, Seq[String]] = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                }
                val hasPermissions = parsePermissions(groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head), PermissionType.DOAP)
                Some(
                    DefaultObjectAccessPermissionV1(
                        forProject = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProject, throw InconsistentTriplestoreDataException(s"Permission has no project.")).head,
                        forGroup = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForGroup, throw InconsistentTriplestoreDataException(s"Permission has no group.")).head,
                        forResourceClass = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForResourceClass, throw InconsistentTriplestoreDataException(s"Permission has no resource class.")).head,
                        forProperty = groupedPermissionsQueryResponse.getOrElse(OntologyConstants.KnoraBase.ForProperty, throw InconsistentTriplestoreDataException(s"Permission has no property.")).head,
                        hasPermissions = hasPermissions
                    )
                )
            } else {
                None
            }
        //_ = log.debug(s"getAdministrativePermissionForProjectGroupV1 - administrativePermission: $administrativePermission")
        } yield permission
    }

    def defaultObjectAccessPermissionForProjectGroupGetRequestV1(projectIRI: IRI, groupIRI: IRI, userProfile: UserProfileV1): Future[DefaultObjectAccessPermissionForProjectGroupGetResponseV1] = {
        for {
            doap <- defaultObjectAccessPermissionForProjectGroupGetV1(projectIRI, groupIRI, userProfile)
            result = doap match {
                case Some(doap) => DefaultObjectAccessPermissionForProjectGroupGetResponseV1(doap)
                case None => throw NotFoundException(s"No Default Object Access Permission found for project: $projectIRI, group: $groupIRI combination")
            }
        } yield result
    }

    /**
      * Returns a string containing default object permissions statements ready for usage during creation of a new resource.
      * The permissions include any default object access permissions defined for the resource class and on any groups the
      * user is member of.
      *
      * @param projectIri the IRI of the project.
      * @param resourceClassIri the IRI of the resource class for which the default object access permissions are requested.
      * @param userProfile the user for which the default object access permissions are requested.
      * @return an optional string with object access permission statements
      */
    def defaultObjectAccessPermissionsForResourceClassGetV1(projectIri: IRI, resourceClassIri: IRI, userProfile: UserProfileV1): Future[Option[String]] = {
        Future(None)
    }

    /**
      *
      * @param projectIri the IRI of the project inside which the resource is created.
      * @param propertyTypeIri the IRI of the property type for which the default object access permissions are requested.
      * @param userProfile the user for which the default object access permissions are requested.
      * @return an optional string with object access permission statements.
      */
    def defaultObjectAccessPermissionsForPropertyTypeGetV1(projectIri: IRI, propertyTypeIri: IRI, userProfile: UserProfileV1): Future[Option[String]] = {
        Future(None)
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

    /**
      * By providing the all the projects and groups in which the user is a member of, calculate the user's max
      * administrative permissions of each project.
      *
      * @param groupsPerProject the groups in each project the user is member of.
      * @return a the user's max permissions for each project.
      */
    def userAdministrativePermissionsGetV1(groupsPerProject: Map[IRI, List[IRI]]): Future[Map[IRI, Set[PermissionV1]]] = {

        //FixMe: loop through each project the user is part of and retrieve the administrative permissions attached to each group he is in, calculate max permissions, package everything in a neat little object, and return it back


        val pp: Iterable[Future[(IRI, Seq[PermissionV1])]] = for {
            (projectIri, groups) <- groupsPerProject
            groupIri <- groups
            //_ = log.debug(s"getUserAdministrativePermissionsRequestV1 - projectIri: $projectIri, groupIri: $groupIri")
            projectPermission: Future[(IRI, Seq[PermissionV1])] = administrativePermissionForProjectGroupGetV1(projectIri, groupIri).map {
                case Some(ap: AdministrativePermissionV1) => (projectIri, ap.hasPermissions)
                case None => (projectIri, Seq.empty[PermissionV1])
            }

        } yield projectPermission

        val allPermissionsFuture: Future[Iterable[(IRI, Seq[PermissionV1])]] = Future.sequence(pp)

        val result: Future[Map[IRI, Set[PermissionV1]]] = for {
            allPermission <- allPermissionsFuture
            result = allPermission.groupBy(_._1).map { case (k, v) =>

                /* Combine permission sequences */
                val combined = v.foldLeft(Seq.empty[PermissionV1]) { (acc, seq) =>
                    acc ++ seq._2
                }
                /* Squash the resulting permission sequence */
                val squashed: Set[PermissionV1] = squashPermissions(combined)
                (k, squashed)
            }
            //_ = log.debug(s"getUserAdministrativePermissionsRequestV1 - result: $result")
        } yield result
        result
    }

    def userDefaultObjectAccessPermissionsGetV1(groupsPerProject: Map[IRI, List[IRI]]): Future[Map[IRI, Set[PermissionV1]]] = {

        //FixMe: loop through each project the user is part of and retrieve the default object access permissions attached to each group he is in, calculate max permissions, package everything in a neat little object, and return it back
        val result = Map.empty[IRI, Set[PermissionV1]]
        //log.debug(s"getUserDefaultObjectAccessPermissionsRequestV1 - result: $result")
        Future(result)
    }


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
    def parsePermissions(maybePermissionListStr: Option[String], permissionType: PermissionType): Seq[PermissionV1] = {
        maybePermissionListStr match {
            case Some(permissionListStr) => {
                val permissions: Seq[String] = permissionListStr.split(OntologyConstants.KnoraBase.PermissionListDelimiter)
                //log.debug(s"parsePermissions - split permissions: $permissions")
                permissions.map {
                    permission =>
                        val splitPermission = permission.split(' ')
                        val abbreviation = splitPermission(0)

                        permissionType match {
                            case PermissionType.AP => {
                                if (!OntologyConstants.KnoraBase.AdministrativePermissionAbbreviations.contains(abbreviation)) {
                                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                                }
                                if (splitPermission.length > 1) {
                                    val shortGroups = splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter).toSet
                                    val groups = shortGroups.map(_.replace(OntologyConstants.KnoraBase.KnoraBasePrefix, OntologyConstants.KnoraBase.KnoraBasePrefixExpansion))
                                    buildPermissionObject(abbreviation, Some(groups))
                                } else {
                                    buildPermissionObject(abbreviation, None)
                                }
                            }
                            case PermissionType.DOAP => {
                                if (!OntologyConstants.KnoraBase.DefaultObjectAccessPermissionAbbreviations.contains(abbreviation)) {
                                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                                }
                                val shortGroups = splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter).toSet
                                val groups = shortGroups.map(_.replace(OntologyConstants.KnoraBase.KnoraBasePrefix, OntologyConstants.KnoraBase.KnoraBasePrefixExpansion))
                                buildPermissionObject(abbreviation, Some(groups))
                            }
                        }
                }
            }
            case None => Seq.empty[PermissionV1]
        }
    }

    /**
      * Helper method used to convert the permission string stored inside the triplestore to a permission object.
      *
      * @param name the name of the permission.
      * @param iris the optional additional information.
      * @return a permission object.
      */
    def buildPermissionObject(name: String, iris: Option[Set[IRI]]): PermissionV1 = {
        name match {
            case OntologyConstants.KnoraBase.ProjectResourceCreateAllPermission => PermissionV1.ProjectResourceCreateAllPermission
            case OntologyConstants.KnoraBase.ProjectResourceCreateRestrictedPermission => PermissionV1.ProjectResourceCreateRestrictedPermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
            case OntologyConstants.KnoraBase.ProjectAdminAllPermission => PermissionV1.ProjectAdminAllPermission
            case OntologyConstants.KnoraBase.ProjectAdminGroupAllPermission => PermissionV1.ProjectAdminGroupAllPermission
            case OntologyConstants.KnoraBase.ProjectAdminGroupRestrictedPermission => PermissionV1.ProjectAdminGroupRestrictedPermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
            case OntologyConstants.KnoraBase.ProjectAdminRightsAllPermission => PermissionV1.ProjectAdminRightsAllPermission
            case OntologyConstants.KnoraBase.ProjectAdminOntologyAllPermission => PermissionV1.ProjectAdminOntologyAllPermission
            case OntologyConstants.KnoraBase.DefaultChangeRightsPermission => PermissionV1.DefaultChangeRightsPermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
            case OntologyConstants.KnoraBase.DefaultDeletePermission => PermissionV1.DefaultDeletePermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
            case OntologyConstants.KnoraBase.DefaultModifyPermission => PermissionV1.DefaultModifyPermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
            case OntologyConstants.KnoraBase.DefaultViewPermission => PermissionV1.DefaultViewPermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
            case OntologyConstants.KnoraBase.DefaultRestrictedViewPermission => PermissionV1.DefaultRestrictedViewPermission(iris.getOrElse(throw InconsistentTriplestoreDataException(s"Missing additional permission information!")))
        }

    }

    /**
      * Helper method used to squash a sequence of [[PermissionV1]] objects, by combining the duplicates where
      * applicable, before they are removed.
      *
      * @param permissions the sequence of permissions.
      * @return a squashed sequence containing only unique permission.
      */
    def squashPermissions(permissions: Seq[PermissionV1]): Set[PermissionV1] = {

        val result = permissions.groupBy(_.name).map { case (k, v) =>
            k match {
                case OntologyConstants.KnoraBase.ProjectResourceCreateRestrictedPermission => {
                    val combinedRestrictions: Set[IRI] = v.foldLeft(Set.empty[IRI]) { (acc, perm) =>
                        acc ++ perm.restrictions
                    }
                    PermissionV1.ProjectResourceCreateRestrictedPermission(combinedRestrictions)
                }
                case OntologyConstants.KnoraBase.ProjectAdminGroupRestrictedPermission => {
                    val combinedRestrictions: Set[IRI] = v.foldLeft(Set.empty[IRI]) { (acc, perm) =>
                        acc ++ perm.restrictions
                    }
                    PermissionV1.ProjectAdminGroupRestrictedPermission(combinedRestrictions)
                }
                case rest => v.head
            }
        }.toSet
        //log.debug(s"squashPermissions - result: $result")
        result
    }
}
