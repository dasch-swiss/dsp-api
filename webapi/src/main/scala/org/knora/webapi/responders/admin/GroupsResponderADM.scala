/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

import java.util.UUID

import akka.actor.Status
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectGetADM}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserGetADM, UserInformationTypeADM}
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.KnoraIdUtil
import org.knora.webapi.{DuplicateValueException, _}

import scala.concurrent.Future


/**
  * Returns information about Knora projects.
  */
class GroupsResponderADM extends Responder with GroupsADMJsonProtocol {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    // Global lock IRI used for group creation and updating
    val GROUPS_GLOBAL_LOCK_IRI = "http://rdfh.ch/groups"

    /**
      * Receives a message extending [[ProjectsResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive: PartialFunction[Any, Unit] = {
        case GroupsGetADM(requestingUser) => future2Message(sender(), groupsGetADM(requestingUser), log)
        case GroupsGetRequestADM(requestingUser) => future2Message(sender(), groupsGetRequestADM(requestingUser), log)
        case GroupGetADM(groupIri, requestingUser) => future2Message(sender(), groupGetADM(groupIri, requestingUser), log)
        case GroupGetRequestADM(groupIri, requestingUser) => future2Message(sender(), groupGetRequestADM(groupIri, requestingUser), log)
        case GroupMembersGetRequestADM(groupIri, userProfileV1) => future2Message(sender(), groupMembersGetRequestADM(groupIri, userProfileV1), log)
        case GroupCreateRequestADM(newGroupInfo, userProfile, apiRequestID) => future2Message(sender(), createGroupADM(newGroupInfo, userProfile, apiRequestID), log)
        case GroupChangeRequestADM(groupIri, changeGroupRequest, userProfileV1, apiRequestID) => future2Message(sender(), changeGroupBasicInformationRequestADM(groupIri, changeGroupRequest, userProfileV1, apiRequestID), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Gets all the groups (without built-in groups) and returns them as a sequence of [[GroupADM]].
      *
      * @param requestingUser the user making the request.
      * @return all the groups as a sequence of [[GroupADM]].
      */
    private def groupsGetADM(requestingUser: UserADM): Future[Seq[GroupADM]] = {

        log.debug("groupsGetADM")

        for {
            sparqlQuery <- Future(queries.sparql.admin.txt.getGroups(
                triplestore = settings.triplestoreType,
                maybeIri = None
            ).toString())
            groupsResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            statements = groupsResponse.statements

            groups: Seq[Future[GroupADM]] = statements.map {
                case (groupIri: IRI, propsMap: Map[IRI, Seq[LiteralV2]]) =>

                    val projectIri: IRI = propsMap.getOrElse(OntologyConstants.KnoraBase.BelongsToProject, throw InconsistentTriplestoreDataException(s"Group $groupIri has no project attached")).head.asInstanceOf[IriLiteralV2].value

                    for {
                        maybeProjectADM: Option[ProjectADM] <- (responderManager ? ProjectGetADM(maybeIri = Some(projectIri), maybeShortname = None, maybeShortcode = None, requestingUser = KnoraSystemInstances.Users.SystemUser)).mapTo[Option[ProjectADM]]
                        projectADM: ProjectADM = maybeProjectADM match {
                            case Some(project) => project
                            case None => throw InconsistentTriplestoreDataException(s"Project $projectIri was referenced by $groupIri but was not found in the triplestore.")
                        }

                        group = GroupADM(
                            id = groupIri,
                            name = propsMap.getOrElse(OntologyConstants.KnoraBase.GroupName, throw InconsistentTriplestoreDataException(s"Group $groupIri has no name attached")).head.asInstanceOf[StringLiteralV2].value,
                            description = propsMap.getOrElse(OntologyConstants.KnoraBase.GroupDescription, throw InconsistentTriplestoreDataException(s"Group $groupIri has no description attached")).head.asInstanceOf[StringLiteralV2].value,
                            project = projectADM,
                            status = propsMap.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Group $groupIri has no status attached")).head.asInstanceOf[BooleanLiteralV2].value,
                            selfjoin = propsMap.getOrElse(OntologyConstants.KnoraBase.HasSelfJoinEnabled, throw InconsistentTriplestoreDataException(s"Group $groupIri has no status attached")).head.asInstanceOf[BooleanLiteralV2].value
                        )

                    } yield group
            }.toSeq
            result: Seq[GroupADM] <- Future.sequence(groups)
        } yield result
    }

    /**
      * Gets all the groups and returns them as a [[GroupsGetResponseADM]].
      *
      * @param requestingUser the user initiating the request.
      * @return all the groups as a [[GroupsGetResponseADM]].
      */
    private def groupsGetRequestADM(requestingUser: UserADM): Future[GroupsGetResponseADM] = {
        for {
            maybeGroupsListToReturn <- groupsGetADM(requestingUser)
            result = maybeGroupsListToReturn match {
                case groups: Seq[GroupADM] if groups.nonEmpty => GroupsGetResponseADM(groups = groups)
                case _ => throw NotFoundException(s"No groups found")
            }
        } yield result
    }


    /**
      * Gets the group with the given group IRI and returns the information as a [[GroupADM]].
      *
      * @param groupIri    the IRI of the group requested.
      * @param requestingUser the user initiating the request.
      * @return information about the group as a [[GroupADM]]
      */
    private def groupGetADM(groupIri: IRI, requestingUser: UserADM): Future[Option[GroupADM]] = {

        for {
            sparqlQuery <- Future(queries.sparql.admin.txt.getGroups(
                triplestore = settings.triplestoreType,
                maybeIri = Some(groupIri)

            ).toString())
            groupResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            _ = if (groupResponse.statements.isEmpty) {

            }

            maybeGroup: Option[GroupADM] <- if (groupResponse.statements.isEmpty) {
                FastFuture.successful(None)
            } else {
                statements2GroupADM(statements = groupResponse.statements.head, requestingUser = requestingUser)
            }

            _ = log.debug("groupGetADM - result: {}", maybeGroup)

        } yield maybeGroup
    }

    /**
      * Gets the group with the given group IRI and returns the information as a [[GroupGetResponseADM]].
      *
      * @param groupIri    the IRI of the group requested.
      * @param requestingUser the user initiating the request.
      * @return information about the group as a [[GroupGetResponseADM]].
      */
    private def groupGetRequestADM(groupIri: IRI, requestingUser: UserADM): Future[GroupGetResponseADM] = {

        for {
            maybeGroupADM: Option[GroupADM] <- groupGetADM(groupIri, requestingUser)
            result = maybeGroupADM match {
                case Some(group) => GroupGetResponseADM(group = group)
                case None => throw NotFoundException(s"For the given group iri '$groupIri' no information was found")
            }
        } yield result
    }

    /**
      * Gets the group members with the given grop IRI and returns the information as a [[GroupMembersGetResponseADM]]
      *
      * @param groupIri the IRI of the group.
      * @param requestingUser the user initiating the request.
      * @return
      */
    def groupMembersGetRequestADM(groupIri: IRI, requestingUser: UserADM): Future[GroupMembersGetResponseADM] = {

        //log.debug("groupMembersByIRIGetRequestV1 - groupIri: {}", groupIri)

        for {
            groupExists: Boolean <- groupExists(groupIri)

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

            _ = log.debug("groupMembersGetRequestADM - groupMemberIris: {}", groupMemberIris)

            maybeUsersFutures: Seq[Future[Option[UserADM]]] = groupMemberIris.map {
                userIri => (responderManager ? UserGetADM(maybeIri = Some(userIri), maybeEmail = None, userInformationTypeADM = UserInformationTypeADM.RESTRICTED, requestingUser = KnoraSystemInstances.Users.SystemUser)).mapTo[Option[UserADM]]
            }
            maybeUsers: Seq[Option[UserADM]] <- Future.sequence(maybeUsersFutures)
            users: Seq[UserADM] = maybeUsers.flatten

            _ = log.debug("groupMembersGetRequestADM - users: {}", users)

        } yield GroupMembersGetResponseADM(members = users)
    }

    /**
      * Create a new group.
      *
      * @param createRequest the create request information.
      * @param requestingUser   the user making the request.
      * @param apiRequestID  the unique request ID.
      * @return a [[GroupOperationResponseADM]]
      */
    private def createGroupADM(createRequest: CreateGroupApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[GroupOperationResponseADM] = {

        log.debug("createGroupADM - createRequest: {}", createRequest)

        def createGroupTask(createRequest: CreateGroupApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[GroupOperationResponseADM] = for {
            /* check if username or password are not empty */
            _ <- Future(if (createRequest.name.isEmpty) throw BadRequestException("Group name cannot be empty"))
            _ = if (createRequest.project.isEmpty) throw BadRequestException("Project IRI cannot be empty")

            /* check if the requesting user is allowed to create group */
            _ = if (!requestingUser.permissions.isProjectAdmin(createRequest.project) && !requestingUser.permissions.isSystemAdmin) {
                // not a project admin and not a system admin
                throw ForbiddenException("A new group can only be created by a project or system admin.")
            }

            nameExists <- groupByNameAndProjectExists(name = createRequest.name, projectIri = createRequest.project)
            _ = if (nameExists) {
                throw DuplicateValueException(s"Group with the name: '${createRequest.name}' already exists")
            }

            maybeProjectADM: Option[ProjectADM] <- (responderManager ? ProjectGetADM(maybeIri = Some(createRequest.project), maybeShortcode = None, maybeShortname = None, requestingUser = KnoraSystemInstances.Users.SystemUser)).mapTo[Option[ProjectADM]]

            projectADM: ProjectADM = maybeProjectADM match {
                case Some(p) => p
                case None => throw NotFoundException(s"Cannot create group inside project: '${createRequest.project}. The project was not found.")
            }

            /* generate a new random group IRI */
            groupIri = knoraIdUtil.makeRandomGroupIri(projectADM.shortcode)

            /* create the group */
            createNewGroupSparqlString = queries.sparql.admin.txt.createNewGroup(
                adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
                triplestore = settings.triplestoreType,
                groupIri = groupIri,
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
            /* Verify that the project was updated. */
            maybeCreatedGroup <- groupGetADM(groupIri = groupIri, requestingUser = KnoraSystemInstances.Users.SystemUser)
            createdGroup: GroupADM = maybeCreatedGroup.getOrElse(throw UpdateNotPerformedException(s"Group was not created. Please report this as a possible bug."))

        } yield GroupOperationResponseADM(group = createdGroup)

        for {
            // run user creation with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                GROUPS_GLOBAL_LOCK_IRI,
                () => createGroupTask(createRequest, requestingUser, apiRequestID)
            )
        } yield taskResult
    }


    /**
      * Change group's basic information.
      *
      * @param groupIri           the IRI of the group we want to change.
      * @param changeGroupRequest the change request.
      * @param requestingUser     the user making the request.
      * @param apiRequestID       the unique request ID.
      * @return a [[GroupOperationResponseADM]].
      */
    private def changeGroupBasicInformationRequestADM(groupIri: IRI, changeGroupRequest: ChangeGroupApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[GroupOperationResponseADM] = {

        /**
          * The actual change group task run with an IRI lock.
          */
        def changeGroupTask(groupIri: IRI, changeGroupRequest: ChangeGroupApiRequestADM, requestingUser: UserADM): Future[GroupOperationResponseADM] = for {

            _ <- Future(
                // check if necessary information is present
                if (groupIri.isEmpty) throw BadRequestException("Group IRI cannot be empty")
            )

            /* Get the project IRI which also verifies that the group exists. */
            maybeGroupADM <- groupGetADM(groupIri, KnoraSystemInstances.Users.SystemUser)
            groupADM: GroupADM = maybeGroupADM.getOrElse(throw NotFoundException(s"Group '$groupIri' not found. Aborting update request."))

            /* check if the requesting user is allowed to perform updates */
            _ = if (!requestingUser.permissions.isProjectAdmin(groupADM.project.id) && !requestingUser.permissions.isSystemAdmin) {
                // not a project admin and not a system admin
                throw ForbiddenException("Group's information can only be changed by a project or system admin.")
            }

            /* create the update request */
            groupUpdatePayload = GroupUpdatePayloadADM(
                name = changeGroupRequest.name,
                description = changeGroupRequest.description,
                status = changeGroupRequest.status,
                selfjoin = changeGroupRequest.selfjoin
            )

            result <- updateGroupADM(groupIri, groupUpdatePayload, KnoraSystemInstances.Users.SystemUser)

        } yield result

        for {
            // run the change status task with an IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                groupIri,
                () => changeGroupTask(groupIri, changeGroupRequest, requestingUser)
            )
        } yield taskResult

    }

    /**
      * Main group update method.
      *
      * @param groupIri           the IRI of the group we are updating.
      * @param groupUpdatePayload the payload holding the information which we want to update.
      * @param requestingUser        the profile of the user making the request.
      * @return a [[GroupOperationResponseADM]]
      */
    private def updateGroupADM(groupIri: IRI, groupUpdatePayload: GroupUpdatePayloadADM, requestingUser: UserADM): Future[GroupOperationResponseADM] = {

        log.debug("updateGroupADM - groupIri: {}, groupUpdatePayload: {}", groupIri, groupUpdatePayload)

        val parametersCount: Int = List(
            groupUpdatePayload.name,
            groupUpdatePayload.description,
            groupUpdatePayload.status,
            groupUpdatePayload.selfjoin).flatten.size

        if (parametersCount == 0) throw BadRequestException("No data would be changed. Aborting update request.")


        for {
            /* Verify that the group exists. */
            maybeGroupADM <- groupGetADM(groupIri = groupIri, requestingUser = KnoraSystemInstances.Users.SystemUser)
            groupADM: GroupADM = maybeGroupADM.getOrElse(throw NotFoundException(s"Group '$groupIri' not found. Aborting update request."))

            /* Verify that the potentially new name is unique */
            groupByNameAlreadyExists <- if (groupUpdatePayload.name.nonEmpty) {
                val newName = groupUpdatePayload.name.get
                groupByNameAndProjectExists(newName, groupADM.project.id)
            } else {
                FastFuture.successful(false)
            }

            _ = if (groupByNameAlreadyExists) {
                log.debug("updateGroupADM - about to throw an exception. Group with that name already exists.")
                throw BadRequestException(s"Group with the name: '${groupUpdatePayload.name.get}' already exists.")
            }


            /* Update group */
            updateProjectSparqlString <- Future(queries.sparql.admin.txt.updateGroup(
                adminNamedGraphIri = "http://www.knora.org/data/admin",
                triplestore = settings.triplestoreType,
                groupIri = groupIri,
                maybeName = groupUpdatePayload.name,
                maybeDescription = groupUpdatePayload.description,
                maybeProject = None, // maybe later we want to allow moving of a group to another project
                maybeStatus = groupUpdatePayload.status,
                maybeSelfjoin = groupUpdatePayload.selfjoin
            ).toString)
            //_ = log.debug(s"updateProjectV1 - query: {}",updateProjectSparqlString)

            updateGroupResponse <- (storeManager ? SparqlUpdateRequest(updateProjectSparqlString)).mapTo[SparqlUpdateResponse]

            /* Verify that the project was updated. */
            maybeUpdatedGroup <- groupGetADM(groupIri = groupIri, requestingUser = KnoraSystemInstances.Users.SystemUser)
            updatedGroup: GroupADM = maybeUpdatedGroup.getOrElse(throw UpdateNotPerformedException("Group was not updated. Please report this as a possible bug."))

            //_ = log.debug("updateProjectV1 - projectUpdatePayload: {} /  updatedProject: {}", projectUpdatePayload, updatedProject)

            _ = if (groupUpdatePayload.name.isDefined) {
                if (updatedGroup.name != groupUpdatePayload.name.get) throw UpdateNotPerformedException("Group's 'name' was not updated. Please report this as a possible bug.")
            }

            _ = if (groupUpdatePayload.description.isDefined) {
                if (updatedGroup.description != groupUpdatePayload.description.get) throw UpdateNotPerformedException("Group's 'description' was not updated. Please report this as a possible bug.")
            }

            /*
            _ = if (groupUpdatePayload.project.isDefined) {
                if (updatedGroup.project != groupUpdatePayload.project.get) throw UpdateNotPerformedException("Group's 'project' was not updated. Please report this as a possible bug.")
            }
            */

            _ = if (groupUpdatePayload.status.isDefined) {
                if (updatedGroup.status != groupUpdatePayload.status.get) throw UpdateNotPerformedException("Group's 'status' was not updated. Please report this as a possible bug.")
            }

            _ = if (groupUpdatePayload.selfjoin.isDefined) {
                if (updatedGroup.selfjoin != groupUpdatePayload.selfjoin.get) throw UpdateNotPerformedException("Group's 'selfjoin' status was not updated. Please report this as a possible bug.")
            }

        } yield GroupOperationResponseADM(group = updatedGroup)

    }

    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Helper method that turns SPARQL result rows into a [[GroupADM]].
      *
      * @param statements results from the SPARQL query representing information about the group.
      * @param requestingUser the user that is making the request.
      * @return a [[GroupADM]] representing information about the group.
      */
    private def statements2GroupADM(statements: (IRI, Map[IRI, Seq[LiteralV2]]), requestingUser: UserADM): Future[Option[GroupADM]] = {

        log.debug("statements2GroupADM - statements: {}", statements)

        val groupIri: IRI = statements._1
        val propsMap: Map[IRI, Seq[LiteralV2]] = statements._2

        log.debug("statements2GroupADM - groupIri: {}", groupIri)

        val maybeProjectIri = propsMap.get(OntologyConstants.KnoraBase.BelongsToProject)
        val projectIriFuture: Future[IRI] = maybeProjectIri match {
            case Some(iri) => FastFuture.successful(iri.head.asInstanceOf[IriLiteralV2].value)
            case None => FastFuture.failed(throw InconsistentTriplestoreDataException(s"Group $groupIri has no project attached"))
        }

        if (propsMap.nonEmpty) {
            for {
                projectIri <- projectIriFuture
                maybeProject: Option[ProjectADM] <- (responderManager ? ProjectGetADM(maybeIri = Some(projectIri), maybeShortcode = None, maybeShortname = None, requestingUser = KnoraSystemInstances.Users.SystemUser)).mapTo[Option[ProjectADM]]
                project: ProjectADM = maybeProject.getOrElse(throw InconsistentTriplestoreDataException(s"Group $groupIri has no project attached."))

                groupADM: GroupADM = GroupADM(
                    id = groupIri,
                    name = propsMap.getOrElse(OntologyConstants.KnoraBase.GroupName, throw InconsistentTriplestoreDataException(s"Group $groupIri has no groupName attached")).head.asInstanceOf[StringLiteralV2].value,
                    description = propsMap.getOrElse(OntologyConstants.KnoraBase.GroupDescription, throw InconsistentTriplestoreDataException(s"Group $groupIri has no description attached")).head.asInstanceOf[StringLiteralV2].value,
                    project = project,
                    status = propsMap.getOrElse(OntologyConstants.KnoraBase.Status, throw InconsistentTriplestoreDataException(s"Group $groupIri has no status attached")).head.asInstanceOf[BooleanLiteralV2].value,
                    selfjoin = propsMap.getOrElse(OntologyConstants.KnoraBase.HasSelfJoinEnabled, throw InconsistentTriplestoreDataException(s"Group $groupIri has no selfJoin attached")).head.asInstanceOf[BooleanLiteralV2].value
                )
            } yield Some(groupADM)
        } else {
            FastFuture.successful(None)
        }
    }

    /**
      * Helper method for checking if a group identified by IRI exists.
      *
      * @param groupIri the IRI of the group.
      * @return a [[Boolean]].
      */
    def groupExists(groupIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkGroupExistsByIri(groupIri = groupIri).toString)
            //_ = log.debug("groupExists - query: {}", askString)

            checkGroupExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkGroupExistsResponse.result

        } yield result
    }

    /**
      * Helper method for checking if a group identified by name / project IRI exists.
      *
      * @param name       the name of the group.
      * @param projectIri the IRI of the project.
      * @return a [[Boolean]].
      */
    def groupByNameAndProjectExists(name: String, projectIri: IRI): Future[Boolean] = {

        for {
            askString <- Future(queries.sparql.admin.txt.checkGroupExistsByName(projectIri = projectIri, name = name).toString)
            //_ = log.debug("groupExists - query: {}", askString)

            checkUserExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkUserExistsResponse.result

            _ = log.debug("groupByNameAndProjectExists - name: {}, projectIri: {}, result: {}", name, projectIri, result)
        } yield result

    }


}
