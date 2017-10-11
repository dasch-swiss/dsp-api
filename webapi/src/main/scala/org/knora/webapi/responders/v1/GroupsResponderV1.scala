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

import java.util.UUID

import akka.actor.Status
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi.messages.v1.responder.groupmessages._
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.KnoraIdUtil
import org.knora.webapi.{DuplicateValueException, _}

import scala.concurrent.Future


/**
  * Returns information about Knora projects.
  */
class GroupsResponderV1 extends Responder with GroupV1JsonProtocol {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    // Global lock IRI used for group creation and updating
    val GROUPS_GLOBAL_LOCK_IRI = "http://data.knora.org/users"

    val UNKNOWN_USER = "UnknownUser"
    val KNOWN_USER = "KnownUser"
    val CREATOR = "Creator"
    val PROJECT_MEMBER = "ProjectMember"
    val PROJECT_ADMIN = "ProjectAdmin"
    val SYSTEM_ADMIN = "SystemAdmin"

    /**
      * Receives a message extending [[ProjectsResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case GroupsGetRequestV1(userProfile) => future2Message(sender(), groupsGetRequestV1(userProfile), log)
        case GroupInfoByIRIGetRequest(iri, userProfile) => future2Message(sender(), groupInfoByIRIGetRequestV1(iri, userProfile), log)
        case GroupInfoByNameGetRequest(projectIri, groupName, userProfile) => future2Message(sender(), groupInfoByNameGetRequest(projectIri, groupName, userProfile), log)
        case GroupMembersByIRIGetRequestV1(groupIri, userProfileV1) => future2Message(sender(), groupMembersByIRIGetRequestV1(groupIri, userProfileV1), log)
        case GroupMembersByNameGetRequestV1(projectIri, groupName, userProfileV1) => future2Message(sender(), groupMembersByNameRequestV1(projectIri, groupName, userProfileV1), log)
        case GroupCreateRequestV1(newGroupInfo, userProfile, apiRequestID) => future2Message(sender(), createGroupV1(newGroupInfo, userProfile, apiRequestID), log)
        case GroupChangeRequestV1(groupIri, changeGroupRequest, userProfileV1, apiRequestID) => future2Message(sender(), changeBasicInformationRequestV1(groupIri, changeGroupRequest, userProfileV1, apiRequestID), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Gets all the groups (without built-in groups) and returns them as a [[GroupsResponseV1]].
      *
      * @param userProfile the profile of the user that is making the request.
      * @return all the groups as a [[GroupsResponseV1]].
      */
    private def groupsGetRequestV1(userProfile: Option[UserProfileV1]): Future[GroupsResponseV1] = {

        log.debug("groupsGetRequestV1")

        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getGroups(
                triplestore = settings.triplestoreType
            ).toString())
            groupsResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            groupsResponseRows: Seq[VariableResultsRow] = groupsResponse.results.bindings

            groupsWithProperties: Map[String, Map[String, String]] = groupsResponseRows.groupBy(_.rowMap("s")).map {
                case (groupIri: String, rows: Seq[VariableResultsRow]) => (groupIri, rows.map {
                    case row => (row.rowMap("p"), row.rowMap("o"))
                }.toMap)
            }

            groups = groupsWithProperties.map {
                case (groupIri: IRI, propsMap: Map[String, String]) =>

                    GroupInfoV1(
                        id = groupIri,
                        name = propsMap.getOrElse(OntologyConstants.KnoraBase.GroupName, throw InconsistentTriplestoreDataException(s"Group $groupIri has no name attached")),
                        description = propsMap.get(OntologyConstants.KnoraBase.GroupDescription),
                        project = propsMap.getOrElse(OntologyConstants.KnoraBase.BelongsToProject, throw InconsistentTriplestoreDataException(s"Group $groupIri has no project attached")),
                        status = propsMap(OntologyConstants.KnoraBase.Status).toBoolean,
                        selfjoin = propsMap(OntologyConstants.KnoraBase.HasSelfJoinEnabled).toBoolean
                    )
            }.toVector
        } yield GroupsResponseV1(
            groups = groups
        )
    }

    /**
      * Gets the group with the given group IRI and returns the information as a [[GroupInfoResponseV1]].
      *
      * @param groupIRI    the IRI of the group requested.
      * @param userProfile the profile of user that is making the request.
      * @return information about the group as a [[GroupInfoResponseV1]].
      */
    private def groupInfoByIRIGetRequestV1(groupIRI: IRI, userProfile: Option[UserProfileV1] = None): Future[GroupInfoResponseV1] = {

        for {
            maybeGroupInfo: Option[GroupInfoV1] <- groupInfoByIRIGetV1(groupIRI, userProfile)
            groupInfo = maybeGroupInfo match {
                case Some(gi) => gi
                case None => throw NotFoundException(s"For the given group iri '$groupIRI' no information was found")
            }
        } yield GroupInfoResponseV1(
            group_info = groupInfo
        )
    }

    /**
      * Gets the group with the given group IRI and returns the information as a [[GroupInfoV1]].
      *
      * @param groupIri    the IRI of the group requested.
      * @param userProfile the profile of the user that is making the request.
      * @return information about the group as a [[GroupInfoV1]]
      */
    private def groupInfoByIRIGetV1(groupIri: IRI, userProfile: Option[UserProfileV1] = None): Future[Option[GroupInfoV1]] = {

        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getGroupByIri(
                triplestore = settings.triplestoreType,
                groupIri = groupIri
            ).toString())
            groupResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            // check group response
            groupInfo = if (groupResponse.results.bindings.nonEmpty) {
                Some(createGroupInfoV1FromGroupResponse(groupResponse = groupResponse.results.bindings, groupIri = groupIri, userProfile))
            } else {
                None
            }
        } yield groupInfo
    }


    /**
      * Gets the group with the given name and returns the information as a [[GroupInfoResponseV1]].
      *
      * @param projectIRI  the IRI of the project, the group is part of.
      * @param groupName   the name of the group requested.
      * @param userProfile the profile of user that is making the request.
      * @return information about the group as a [[GroupInfoResponseV1]].
      */
    private def groupInfoByNameGetRequest(projectIRI: IRI, groupName: String, userProfile: Option[UserProfileV1]): Future[GroupInfoResponseV1] = {

        /* Check to see if it is a built-in implicit group and skip sparql query */

        groupName match {
            case UNKNOWN_USER => {
                FastFuture.successful(
                    GroupInfoResponseV1(GroupInfoV1(
                        id = "-",
                        name = UNKNOWN_USER,
                        description = Some("Built-in Unknowmn User Group"),
                        project = "-",
                        status = true,
                        selfjoin = false))
                )
            }
            case KNOWN_USER => {
                FastFuture.successful(
                    GroupInfoResponseV1(GroupInfoV1(
                        id = "-",
                        name = KNOWN_USER,
                        description = Some("Built-in Known User Group"),
                        project = "-",
                        status = true,
                        selfjoin = false))
                )
            }
            case CREATOR => {
                FastFuture.successful(
                    GroupInfoResponseV1(GroupInfoV1(
                        id = "-",
                        name = CREATOR,
                        description = Some("Built-in Creator Group"),
                        project = "-",
                        status = true,
                        selfjoin = false))
                )
            }
            case PROJECT_MEMBER => {
                FastFuture.successful(
                    GroupInfoResponseV1(GroupInfoV1(
                        id = "-",
                        name = "ProjectMember",
                        description = Some("Built-in Project Member Group"),
                        project = projectIRI,
                        status = true,
                        selfjoin = false))
                )
            }
            case PROJECT_ADMIN => {
                FastFuture.successful(
                    GroupInfoResponseV1(GroupInfoV1(
                        id = "-",
                        name = "ProjectAdmin",
                        description = Some("Default Project Admin Group"),
                        project = projectIRI,
                        status = true,
                        selfjoin = false))
                )
            }
            case SYSTEM_ADMIN => {
                FastFuture.successful(
                    GroupInfoResponseV1(GroupInfoV1(
                        id = "-",
                        name = "SystemAdmin",
                        description = Some("Default System Admin Group"),
                        project = projectIRI,
                        status = true,
                        selfjoin = false))
                )
            }
            case _ => groupInfoByNameFromTriplestoreV1(projectIRI, groupName, userProfile)
        }
    }

    private def groupInfoByNameFromTriplestoreV1(projectIRI: IRI, groupName: String, userProfile: Option[UserProfileV1]): Future[GroupInfoResponseV1] = {
        for {
            _ <- Future(if (projectIRI.isEmpty || groupName.isEmpty) throw BadRequestException("Both projectIri and group name are required parameters."))

            sparqlQuery <- Future(queries.sparql.v1.txt.getGroupByName(
                triplestore = settings.triplestoreType,
                name = groupName,
                projectIri = projectIRI
            ).toString())
            //_ = log.debug(s"groupInfoByNameGetRequest - getGroupByName: $sparqlQuery")
            groupResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            //_ = log.debug(s"group response: ${groupResponse.toString}")

            // check group response and get group IRI
            groupIri: IRI = if (groupResponse.results.bindings.nonEmpty) {
                groupResponse.results.bindings.head.rowMap("s")
            } else {
                throw NotFoundException(s"For the given group name '$groupName' no information was found")
            }

            // get group info
            groupInfo = createGroupInfoV1FromGroupResponse(
                groupResponse = groupResponse.results.bindings,
                groupIri = groupIri,
                userProfile
            )

            groupInfoResponse = GroupInfoResponseV1(
                group_info = groupInfo
            )
        } yield groupInfoResponse
    }

    def groupMembersByIRIGetRequestV1(groupIri: IRI, userProfileV1: UserProfileV1): Future[GroupMembersResponseV1] = {

        //log.debug("groupMembersByIRIGetRequestV1 - groupIri: {}", groupIri)

        for {
            groupExists: Boolean <- groupByIriExists(groupIri)

            _ = if (!groupExists) throw NotFoundException(s"Group '$groupIri' not found.")

            sparqlQueryString <- Future(queries.sparql.v1.txt.getGroupMembersByIri(
                triplestore = settings.triplestoreType,
                groupIri = groupIri
            ).toString())
            //_ = log.debug(s"groupMembersByIRIGetRequestV1 - query: $sparqlQueryString")

            groupMembersResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"groupMembersByIRIGetRequestV1 - result: {}", MessageUtil.toSource(groupMembersResponse))

            // get project member IRI from results rows
            groupMemberIris: Seq[IRI] = if (groupMembersResponse.results.bindings.nonEmpty) {
                groupMembersResponse.results.bindings.map(_.rowMap("s"))
            } else {
                Seq.empty[IRI]
            }
        //_ = log.debug(s"groupMembersByIRIGetRequestV1 - groupMemberIris: $groupMemberIris")

        } yield GroupMembersResponseV1(members = groupMemberIris)
    }

    def groupMembersByNameRequestV1(projectIri: IRI, groupName: String, userProfileV1: UserProfileV1): Future[GroupMembersResponseV1] = {

        //log.debug("groupMembersByNameRequestV1 - projectIri: {}, shortname: {}", projectIri, groupName)

        for {
            groupExists: Boolean <- groupByNameExists(projectIri, Some(groupName))

            _ = if (!groupExists) throw NotFoundException(s"Group '$groupName' not found.")

            sparqlQueryString <- Future(queries.sparql.v1.txt.getGroupMembersByName(
                triplestore = settings.triplestoreType,
                projectIri = projectIri,
                name = groupName).toString())
            //_ = log.debug(s"groupMembersByNameRequestV1 - query: $sparqlQueryString")

            groupMembersResponse <- (storeManager ? SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResponse]
            //_ = log.debug(s"projectMembersByShortnameGetRequestV1 - result: ${MessageUtil.toSource(projectMembersResponse)}")


            // get project member IRI from results rows
            groupMemberIris: Seq[IRI] = if (groupMembersResponse.results.bindings.nonEmpty) {
                groupMembersResponse.results.bindings.map(_.rowMap("s"))
            } else {
                Seq.empty[IRI]
            }
        //_ = log.debug(s"groupMembersByNameRequestV1 - groupMemberIris: $groupMemberIris")

        } yield GroupMembersResponseV1(members = groupMemberIris)
    }


    /**
      * Create a new group.
      *
      * @param createRequest the create request information.
      * @param userProfile   the profile of the user making the request.
      * @param apiRequestID  the unique request ID.
      * @return a [[GroupOperationResponseV1]]
      */
    private def createGroupV1(createRequest: CreateGroupApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[GroupOperationResponseV1] = {

        log.debug("createGroupV1 - createRequest: {}", createRequest)

        def createGroupTask(createRequest: CreateGroupApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[GroupOperationResponseV1] = for {
            /* check if username or password are not empty */
            _ <- Future(if (createRequest.name.isEmpty) throw BadRequestException("Group name cannot be empty"))
            _ = if (createRequest.project.isEmpty) throw BadRequestException("Project IRI cannot be empty")

            /* check if the requesting user is allowed to create group */
            _ = if (!userProfile.permissionData.isProjectAdmin(createRequest.project) && !userProfile.permissionData.isSystemAdmin) {
                // not a project admin and not a system admin
                throw ForbiddenException("A new group can only be created by a project or system admin.")
            }

            nameExists <- groupByNameExists(createRequest.project, Some(createRequest.name))
            _ = if (nameExists) {
                throw DuplicateValueException(s"Group with the name: '${createRequest.name}' already exists")
            }

            /* generate a new random group IRI */
            groupIRI = knoraIdUtil.makeRandomGroupIri

            /* create the group */
            createNewGroupSparqlString = queries.sparql.v1.txt.createNewGroup(
                adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
                triplestore = settings.triplestoreType,
                groupIri = groupIRI,
                groupClassIri = OntologyConstants.KnoraBase.UserGroup,
                name = createRequest.name,
                maybeDescription = createRequest.description,
                projectIri = createRequest.project,
                status = createRequest.status,
                hasSelfJoinEnabled = createRequest.selfjoin
            ).toString
            //_ = log.debug(s"createGroupV1 - createNewGroup: $createNewGroupSparqlString")
            createGroupResponse <- (storeManager ? SparqlUpdateRequest(createNewGroupSparqlString)).mapTo[SparqlUpdateResponse]

            /* Verify that the group was created */
            sparqlQuery <- Future(queries.sparql.v1.txt.getGroupByIri(
                triplestore = settings.triplestoreType,
                groupIri = groupIRI
            ).toString())
            groupResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            // check group response
            _ = if (groupResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"User $groupIRI was not created. Please report this as a possible bug.")
            }

            // create group info from group response
            groupInfo = createGroupInfoV1FromGroupResponse(
                groupResponse = groupResponse.results.bindings,
                groupIri = groupIRI,
                Some(userProfile)
            )

            /* create the group operation response */
            groupOperationResponseV1 = GroupOperationResponseV1(groupInfo)

        } yield groupOperationResponseV1

        for {
        // run user creation with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                GROUPS_GLOBAL_LOCK_IRI,
                () => createGroupTask(createRequest, userProfile, apiRequestID)
            )
        } yield taskResult
    }


    /**
      * Change group's basic information.
      *
      * @param groupIri           the IRI of the group we want to change.
      * @param changeGroupRequest the change request.
      * @param userProfile        the profile of the user making the request.
      * @param apiRequestID       the unique request ID.
      * @return a [[GroupOperationResponseV1]].
      */
    private def changeBasicInformationRequestV1(groupIri: IRI, changeGroupRequest: ChangeGroupApiRequestV1, userProfile: UserProfileV1, apiRequestID: UUID): Future[GroupOperationResponseV1] = {

        /**
          * The actual change group task run with an IRI lock.
          */
        def changeGroupTask(groupIri: IRI, changeGroupRequest: ChangeGroupApiRequestV1, userProfileV1: UserProfileV1): Future[GroupOperationResponseV1] = for {

            _ <- Future(
                // check if necessary information is present
                if (groupIri.isEmpty) throw BadRequestException("Group IRI cannot be empty")
            )

            /* Get the project IRI which also verifies that the group exists. */
            maybeGroupInfo <- groupInfoByIRIGetV1(groupIri, Some(userProfile))
            groupInfo: GroupInfoV1 = maybeGroupInfo.getOrElse(throw NotFoundException(s"Group '$groupIri' not found. Aborting update request."))

            /* check if the requesting user is allowed to perform updates */
            _ = if (!userProfileV1.permissionData.isProjectAdmin(groupInfo.project) && !userProfileV1.permissionData.isSystemAdmin) {
                // not a project admin and not a system admin
                throw ForbiddenException("Group's information can only be changed by a project or system admin.")
            }

            /* create the update request */
            groupUpdatePayload = GroupUpdatePayloadV1(
                name = changeGroupRequest.name,
                description = changeGroupRequest.description,
                status = changeGroupRequest.status,
                selfjoin = changeGroupRequest.selfjoin
            )

            result <- updateGroupV1(groupIri, groupUpdatePayload, userProfileV1)

        } yield result

        for {
        // run the change status task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                groupIri,
                () => changeGroupTask(groupIri, changeGroupRequest, userProfile)
            )
        } yield taskResult

    }

    /**
      * Main group update method.
      *
      * @param groupIri           the IRI of the group we are updating.
      * @param groupUpdatePayload the payload holding the information which we want to update.
      * @param userProfile        the profile of the user making the request.
      * @return a [[GroupOperationResponseV1]]
      */
    private def updateGroupV1(groupIri: IRI, groupUpdatePayload: GroupUpdatePayloadV1, userProfile: UserProfileV1): Future[GroupOperationResponseV1] = {

        log.debug("updateGroupV1 - groupIri: {}, groupUpdatePayload: {}", groupIri, groupUpdatePayload)

        val parametersCount: Int = List(
            groupUpdatePayload.name,
            groupUpdatePayload.description,
            groupUpdatePayload.project,
            groupUpdatePayload.status,
            groupUpdatePayload.selfjoin).flatten.size

        if (parametersCount == 0) throw BadRequestException("No data would be changed. Aborting update request.")


        for {
            /* Verify that the group exists. */
            maybeGroupInfo <- groupInfoByIRIGetV1(groupIri, Some(userProfile))
            groupInfo: GroupInfoV1 = maybeGroupInfo.getOrElse(throw NotFoundException(s"Group '$groupIri' not found. Aborting update request."))

            /* Verify that the potentially new name is unique */
            maybeNewNameExists <- groupByNameExists(groupInfo.project, groupUpdatePayload.name)
            _ = if (maybeNewNameExists && groupUpdatePayload.name.isDefined) {
                throw BadRequestException(s"Group with the name: '${groupUpdatePayload.name.get}' already exists.")
            }

            /* Update group */
            updateProjectSparqlString <- Future(queries.sparql.v1.txt.updateGroup(
                adminNamedGraphIri = "http://www.knora.org/data/admin",
                triplestore = settings.triplestoreType,
                groupIri = groupIri,
                maybeName = groupUpdatePayload.name,
                maybeDescription = groupUpdatePayload.description,
                maybeProject = groupUpdatePayload.project,
                maybeStatus = groupUpdatePayload.status,
                maybeSelfjoin = groupUpdatePayload.selfjoin
            ).toString)
            //_ = log.debug(s"updateProjectV1 - query: {}",updateProjectSparqlString)

            updateGroupResponse <- (storeManager ? SparqlUpdateRequest(updateProjectSparqlString)).mapTo[SparqlUpdateResponse]

            /* Verify that the project was updated. */
            maybeUpdatedGroup <- groupInfoByIRIGetV1(groupIri, Some(userProfile))
            updatedGroup: GroupInfoV1 = maybeUpdatedGroup.getOrElse(throw UpdateNotPerformedException("Group was not updated. Please report this as a possible bug."))

            //_ = log.debug("updateProjectV1 - projectUpdatePayload: {} /  updatedProject: {}", projectUpdatePayload, updatedProject)

            _ = if (groupUpdatePayload.name.isDefined) {
                if (updatedGroup.name != groupUpdatePayload.name.get) throw UpdateNotPerformedException("Group's 'name' was not updated. Please report this as a possible bug.")
            }

            _ = if (groupUpdatePayload.description.isDefined) {
                if (updatedGroup.description != groupUpdatePayload.description) throw UpdateNotPerformedException("Group's 'description' was not updated. Please report this as a possible bug.")
            }

            _ = if (groupUpdatePayload.project.isDefined) {
                if (updatedGroup.project != groupUpdatePayload.project.get) throw UpdateNotPerformedException("Group's 'project' was not updated. Please report this as a possible bug.")
            }

            _ = if (groupUpdatePayload.status.isDefined) {
                if (updatedGroup.status != groupUpdatePayload.status.get) throw UpdateNotPerformedException("Group's 'status' was not updated. Please report this as a possible bug.")
            }

            _ = if (groupUpdatePayload.selfjoin.isDefined) {
                if (updatedGroup.selfjoin != groupUpdatePayload.selfjoin.get) throw UpdateNotPerformedException("Group's 'selfjoin' status was not updated. Please report this as a possible bug.")
            }

            // create the project operation response
            groupOperationResponseV1 = GroupOperationResponseV1(group_info = updatedGroup)
        } yield groupOperationResponseV1

    }

    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Helper method that turns SPARQL result rows into a [[GroupInfoV1]].
      *
      * @param groupResponse results from the SPARQL query representing information about the group.
      * @param groupIri      the IRI of the group the querid information belong to.
      * @param userProfile   the profile of the user that is making the request.
      * @return a [[GroupInfoV1]] representing information about the group.
      */
    private def createGroupInfoV1FromGroupResponse(groupResponse: Seq[VariableResultsRow], groupIri: IRI, userProfile: Option[UserProfileV1]): GroupInfoV1 = {

        val groupProperties = groupResponse.foldLeft(Map.empty[IRI, String]) {
            case (acc, row: VariableResultsRow) =>
                acc + (row.rowMap("p") -> row.rowMap("o"))
        }

        //log.debug(s"group properties: ${groupProperties.toString}")

        GroupInfoV1(
            id = groupIri,
            name = groupProperties.getOrElse(OntologyConstants.KnoraBase.GroupName, throw InconsistentTriplestoreDataException(s"Group $groupIri has no groupName attached")),
            description = groupProperties.get(OntologyConstants.KnoraBase.GroupDescription),
            project = groupProperties.getOrElse(OntologyConstants.KnoraBase.BelongsToProject, throw InconsistentTriplestoreDataException(s"Group $groupIri has no project attached")),
            status = groupProperties(OntologyConstants.KnoraBase.Status).toBoolean,
            selfjoin = groupProperties(OntologyConstants.KnoraBase.HasSelfJoinEnabled).toBoolean)
    }

    /**
      * Helper method for checking if a group identified by IRI exists.
      *
      * @param groupIri the IRI of the group.
      * @return a [[Boolean]].
      */
    def groupByIriExists(groupIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.v1.txt.checkGroupExistsByIri(groupIri = groupIri).toString)
            //_ = log.debug("groupExists - query: {}", askString)

            checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkUserExistsResponse.result

        } yield result
    }

    /**
      * Helper method for checking if a group identified by project IRI / name exists.
      *
      * @param projectIri the IRI of the project.
      * @param name       the name of the group.
      * @return a [[Boolean]].
      */
    def groupByNameExists(projectIri: IRI, name: Option[String]): Future[Boolean] = {
        for {
            result: Boolean <- if (name.isDefined) {
                for {
                    askString <- Future(queries.sparql.v1.txt.checkGroupExistsByName(projectIri = projectIri, name = name.get).toString)
                    //_ = log.debug("groupExists - query: {}", askString)

                    checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
                    checkResult = checkUserExistsResponse.result
                } yield checkResult

            } else {
                Future(false)
            }
        } yield result
    }


}
