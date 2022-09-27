/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import akka.http.scaladsl.util.FastFuture
import akka.pattern._

import java.util.UUID
import scala.concurrent.Future

import dsp.errors._
import dsp.valueobjects.Group.GroupStatus
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.Responder.handleUnexpectedMessage

/**
 * Returns information about Knora projects.
 */
class GroupsResponderADM(responderData: ResponderData) extends Responder(responderData) with GroupsADMJsonProtocol {

  // Global lock IRI used for group creation and updating
  private val GROUPS_GLOBAL_LOCK_IRI: IRI = "http://rdfh.ch/groups"

  /**
   * Receives a message extending [[ProjectsResponderRequestV1]], and returns an appropriate response message
   */
  def receive(msg: GroupsResponderRequestADM) = msg match {
    case GroupsGetADM()        => groupsGetADM
    case GroupsGetRequestADM() => groupsGetRequestADM
    case GroupGetADM(groupIri, requestingUser) =>
      groupGetADM(groupIri, requestingUser)
    case MultipleGroupsGetRequestADM(groupIris, requestingUser) =>
      multipleGroupsGetRequestADM(groupIris, requestingUser)
    case GroupGetRequestADM(groupIri, requestingUser) =>
      groupGetRequestADM(groupIri, requestingUser)
    case GroupMembersGetRequestADM(groupIri, requestingUser) =>
      groupMembersGetRequestADM(groupIri, requestingUser)
    case GroupCreateRequestADM(newGroupInfo, requestingUser, apiRequestID) =>
      createGroupADM(newGroupInfo, requestingUser, apiRequestID)
    case GroupChangeRequestADM(groupIri, changeGroupRequest, requestingUser, apiRequestID) =>
      changeGroupBasicInformationRequestADM(
        groupIri,
        changeGroupRequest,
        requestingUser,
        apiRequestID
      )
    case GroupChangeStatusRequestADM(
          groupIri,
          changeGroupRequest,
          requestingUser,
          apiRequestID
        ) =>
      changeGroupStatusRequestADM(groupIri, changeGroupRequest, requestingUser, apiRequestID)
    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  /**
   * Gets all the groups (without built-in groups) and returns them as a sequence of [[GroupADM]].
   *
   * @return all the groups as a sequence of [[GroupADM]].
   */
  private def groupsGetADM: Future[Seq[GroupADM]] = {

    log.debug("groupsGetADM")

    for {
      sparqlQuery <- Future(
                       org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                         .getGroups(
                           maybeIri = None
                         )
                         .toString()
                     )

      groupsResponse <- appActor
                          .ask(
                            SparqlExtendedConstructRequest(
                              sparql = sparqlQuery
                            )
                          )
                          .mapTo[SparqlExtendedConstructResponse]

      statements = groupsResponse.statements

      groups: Seq[Future[GroupADM]] =
        statements.map { case (groupIri: SubjectV2, propsMap: Map[SmartIri, Seq[LiteralV2]]) =>
          val projectIri: IRI = propsMap
            .getOrElse(
              OntologyConstants.KnoraAdmin.BelongsToProject.toSmartIri,
              throw InconsistentRepositoryDataException(
                s"Group $groupIri has no project attached"
              )
            )
            .head
            .asInstanceOf[IriLiteralV2]
            .value

          for {
            maybeProjectADM <-
              appActor
                .ask(
                  ProjectGetADM(
                    identifier = ProjectIdentifierADM(
                      maybeIri = Some(projectIri)
                    )
                  )
                )
                .mapTo[Option[ProjectADM]]

            projectADM =
              maybeProjectADM match {
                case Some(project) => project
                case None =>
                  throw InconsistentRepositoryDataException(
                    s"Project $projectIri was referenced by $groupIri but was not found in the triplestore."
                  )
              }

            group =
              GroupADM(
                id = groupIri.toString,
                name = propsMap
                  .getOrElse(
                    OntologyConstants.KnoraAdmin.GroupName.toSmartIri,
                    throw InconsistentRepositoryDataException(
                      s"Group $groupIri has no name attached"
                    )
                  )
                  .head
                  .asInstanceOf[StringLiteralV2]
                  .value,
                descriptions = propsMap
                  .getOrElse(
                    OntologyConstants.KnoraAdmin.GroupDescriptions.toSmartIri,
                    throw InconsistentRepositoryDataException(
                      s"Group $groupIri has no descriptions attached"
                    )
                  )
                  .map(l =>
                    l.asStringLiteral(
                      throw InconsistentRepositoryDataException(
                        s"Expected StringLiteralV2 but got ${l.getClass}"
                      )
                    )
                  ),
                project = projectADM,
                status = propsMap
                  .getOrElse(
                    OntologyConstants.KnoraAdmin.Status.toSmartIri,
                    throw InconsistentRepositoryDataException(
                      s"Group $groupIri has no status attached"
                    )
                  )
                  .head
                  .asInstanceOf[BooleanLiteralV2]
                  .value,
                selfjoin = propsMap
                  .getOrElse(
                    OntologyConstants.KnoraAdmin.HasSelfJoinEnabled.toSmartIri,
                    throw InconsistentRepositoryDataException(
                      s"Group $groupIri has no status attached"
                    )
                  )
                  .head
                  .asInstanceOf[BooleanLiteralV2]
                  .value
              )

          } yield group
        }.toSeq
      result: Seq[GroupADM] <- Future.sequence(groups)
    } yield result.sorted
  }

  /**
   * Gets all the groups and returns them as a [[GroupsGetResponseADM]].
   *
   * @return all the groups as a [[GroupsGetResponseADM]].
   */
  private def groupsGetRequestADM: Future[GroupsGetResponseADM] =
    for {
      maybeGroupsListToReturn <-
        groupsGetADM

      result = maybeGroupsListToReturn match {
                 case groups: Seq[GroupADM] if groups.nonEmpty => GroupsGetResponseADM(groups = groups)
                 case _                                        => throw NotFoundException(s"No groups found")
               }
    } yield result

  /**
   * Gets the group with the given group IRI and returns the information as a [[GroupADM]].
   *
   * @param groupIri       the IRI of the group requested.
   * @param requestingUser the user initiating the request.
   * @return information about the group as a [[GroupADM]]
   */
  private def groupGetADM(
    groupIri: IRI,
    requestingUser: UserADM
  ): Future[Option[GroupADM]] =
    for {
      sparqlQuery <- Future(
                       org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                         .getGroups(
                           maybeIri = Some(groupIri)
                         )
                         .toString()
                     )

      groupResponse <-
        appActor
          .ask(
            SparqlExtendedConstructRequest(
              sparql = sparqlQuery
            )
          )
          .mapTo[SparqlExtendedConstructResponse]

      maybeGroup: Option[GroupADM] <-
        if (groupResponse.statements.isEmpty) {
          FastFuture.successful(None)
        } else {
          statements2GroupADM(
            statements = groupResponse.statements.head,
            requestingUser = requestingUser
          )
        }

      _ = log.debug("groupGetADM - result: {}", maybeGroup)

    } yield maybeGroup

  /**
   * Gets the group with the given group IRI and returns the information as a [[GroupGetResponseADM]].
   *
   * @param groupIri             the IRI of the group requested.
   * @param requestingUser       the user initiating the request.
   * @return information about the group as a [[GroupGetResponseADM]].
   */
  private def groupGetRequestADM(
    groupIri: IRI,
    requestingUser: UserADM
  ): Future[GroupGetResponseADM] =
    for {
      maybeGroupADM <-
        groupGetADM(
          groupIri = groupIri,
          requestingUser = requestingUser
        )

      result = maybeGroupADM match {
                 case Some(group) => GroupGetResponseADM(group = group)
                 case None        => throw NotFoundException(s"Group <$groupIri> not found")
               }
    } yield result

  /**
   * Gets the groups with the given IRIs and returns a set of [[GroupGetResponseADM]] objects.
   *
   * @param groupIris      the IRIs of the groups being requested.
   * @param requestingUser the user initiating the request.
   * @return information about the group as a set of [[GroupGetResponseADM]] objects.
   */
  private def multipleGroupsGetRequestADM(
    groupIris: Set[IRI],
    requestingUser: UserADM
  ): Future[Set[GroupGetResponseADM]] = {
    val groupResponseFutures: Set[Future[GroupGetResponseADM]] = groupIris.map { groupIri =>
      groupGetRequestADM(
        groupIri = groupIri,
        requestingUser = requestingUser
      )
    }

    Future.sequence(groupResponseFutures)
  }

  /**
   * Gets the members with the given group IRI and returns the information as a sequence of [[UserADM]].
   *
   * @param groupIri             the IRI of the group.
   * @param requestingUser       the user initiating the request.
   * @return A sequence of [[UserADM]]
   */
  private def groupMembersGetADM(
    groupIri: IRI,
    requestingUser: UserADM
  ): Future[Seq[UserADM]] = {

    log.debug("groupMembersGetADM - groupIri: {}", groupIri)

    for {
      maybeGroupADM <-
        groupGetADM(
          groupIri = groupIri,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )

      _ = maybeGroupADM match {
            case Some(group) =>
              // check if the requesting user is allowed to access the information
              if (
                !requestingUser.permissions.isProjectAdmin(
                  group.project.id
                ) && !requestingUser.permissions.isSystemAdmin && !requestingUser.isSystemUser
              ) {
                // not a project admin and not a system admin
                throw ForbiddenException("Project members can only be retrieved by a project or system admin.")
              }
            case None =>
              throw NotFoundException(s"Group <$groupIri> not found")
          }

      sparqlQueryString <- Future(
                             org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                               .getGroupMembersByIri(
                                 groupIri
                               )
                               .toString()
                           )
      // _ = log.debug(s"groupMembersByIRIGetRequestV1 - query: $sparqlQueryString")

      groupMembersResponse <- appActor.ask(SparqlSelectRequest(sparqlQueryString)).mapTo[SparqlSelectResult]
      // _ = log.debug(s"groupMembersByIRIGetRequestV1 - result: {}", MessageUtil.toSource(groupMembersResponse))

      // get project member IRI from results rows
      groupMemberIris =
        if (groupMembersResponse.results.bindings.nonEmpty) {
          groupMembersResponse.results.bindings.map(_.rowMap("s"))
        } else {
          Seq.empty[IRI]
        }

      _ = log.debug("groupMembersGetRequestADM - groupMemberIris: {}", groupMemberIris)

      maybeUsersFutures: Seq[Future[Option[UserADM]]] =
        groupMemberIris.map { userIri =>
          appActor
            .ask(
              UserGetADM(
                UserIdentifierADM(maybeIri = Some(userIri)),
                userInformationTypeADM = UserInformationTypeADM.Restricted,
                requestingUser = KnoraSystemInstances.Users.SystemUser
              )
            )
            .mapTo[Option[UserADM]]
        }
      maybeUsers         <- Future.sequence(maybeUsersFutures)
      users: Seq[UserADM] = maybeUsers.flatten

      _ = log.debug("groupMembersGetRequestADM - users: {}", users)

    } yield users
  }

  /**
   * Gets the group members with the given group IRI and returns the information as a [[GroupMembersGetResponseADM]].
   * Only project and system admins are allowed to access this information.
   *
   * @param groupIri             the IRI of the group.
   * @param requestingUser       the user initiating the request.
   * @return A [[GroupMembersGetResponseADM]]
   */
  private def groupMembersGetRequestADM(
    groupIri: IRI,
    requestingUser: UserADM
  ): Future[GroupMembersGetResponseADM] = {

    log.debug("groupMembersGetRequestADM - groupIri: {}", groupIri)

    for {
      maybeMembersListToReturn <-
        groupMembersGetADM(
          groupIri = groupIri,
          requestingUser = requestingUser
        )

      result = maybeMembersListToReturn match {
                 case members: Seq[UserADM] if members.nonEmpty => GroupMembersGetResponseADM(members = members)
                 case _                                         => throw NotFoundException(s"No members found.")
               }
    } yield result
  }

  /**
   * Create a new group.
   *
   * @param createRequest        the create request information.
   * @param requestingUser       the user making the request.
   * @param apiRequestID         the unique request ID.
   * @return a [[GroupOperationResponseADM]]
   */
  private def createGroupADM(
    createRequest: GroupCreatePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[GroupOperationResponseADM] = {

    log.debug("createGroupADM - createRequest: {}", createRequest)

    def createGroupTask(
      createRequest: GroupCreatePayloadADM,
      requestingUser: UserADM,
      apiRequestID: UUID
    ): Future[GroupOperationResponseADM] =
      for {
        /* check if the requesting user is allowed to create group */
        _ <- Future(
               if (
                 !requestingUser.permissions
                   .isProjectAdmin(createRequest.project.value) && !requestingUser.permissions.isSystemAdmin
               ) {
                 // not a project admin and not a system admin
                 throw ForbiddenException("A new group can only be created by a project or system admin.")
               }
             )

        nameExists <- groupByNameAndProjectExists(
                        name = createRequest.name.value,
                        projectIri = createRequest.project.value
                      )
        _ = if (nameExists) {
              throw DuplicateValueException(s"Group with the name '${createRequest.name.value}' already exists")
            }

        maybeProjectADM <-
          appActor
            .ask(
              ProjectGetADM(
                identifier = ProjectIdentifierADM(maybeIri = Some(createRequest.project.value))
              )
            )
            .mapTo[Option[ProjectADM]]

        projectADM: ProjectADM =
          maybeProjectADM match {
            case Some(p) => p
            case None =>
              throw NotFoundException(
                s"Cannot create group inside project <${createRequest.project}>. The project was not found."
              )
          }

        // check the custom IRI; if not given, create an unused IRI
        customGroupIri: Option[SmartIri] = createRequest.id.map(_.value).map(iri => iri.toSmartIri)
        groupIri: IRI <- checkOrCreateEntityIri(
                           customGroupIri,
                           stringFormatter.makeRandomGroupIri(projectADM.shortcode)
                         )

        /* create the group */
        createNewGroupSparqlString =
          org.knora.webapi.messages.twirl.queries.sparql.admin.txt
            .createNewGroup(
              adminNamedGraphIri = OntologyConstants.NamedGraphs.AdminNamedGraph,
              groupIri,
              groupClassIri = OntologyConstants.KnoraAdmin.UserGroup,
              name = createRequest.name.value,
              descriptions = createRequest.descriptions.value,
              projectIri = createRequest.project.value,
              status = createRequest.status.value,
              hasSelfJoinEnabled = createRequest.selfjoin.value
            )
            .toString

        _ <- appActor
               .ask(SparqlUpdateRequest(createNewGroupSparqlString))
               .mapTo[SparqlUpdateResponse]

        /* Verify that the group was created and updated  */
        maybeCreatedGroup <-
          groupGetADM(
            groupIri = groupIri,
            requestingUser = KnoraSystemInstances.Users.SystemUser
          )

        createdGroup: GroupADM =
          maybeCreatedGroup.getOrElse(
            throw UpdateNotPerformedException(s"Group was not created. Please report this as a possible bug.")
          )

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
   * @param groupIri             the IRI of the group we want to change.
   * @param changeGroupRequest   the change request.
   * @param requestingUser       the user making the request.
   * @param apiRequestID         the unique request ID.
   * @return a [[GroupOperationResponseADM]].
   */
  private def changeGroupBasicInformationRequestADM(
    groupIri: IRI,
    changeGroupRequest: GroupUpdatePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[GroupOperationResponseADM] = {

    /**
     * The actual change group task run with an IRI lock.
     */
    def changeGroupTask(
      groupIri: IRI,
      changeGroupRequest: GroupUpdatePayloadADM,
      requestingUser: UserADM
    ): Future[GroupOperationResponseADM] =
      for {

        _ <- Future(
               // check if necessary information is present
               if (groupIri.isEmpty) throw BadRequestException("Group IRI cannot be empty")
             )

        /* Get the project IRI which also verifies that the group exists. */
        maybeGroupADM <-
          groupGetADM(
            groupIri = groupIri,
            requestingUser = KnoraSystemInstances.Users.SystemUser
          )

        groupADM = maybeGroupADM.getOrElse(
                     throw NotFoundException(s"Group <$groupIri> not found. Aborting update request.")
                   )

        /* check if the requesting user is allowed to perform updates */
        _ =
          if (
            !requestingUser.permissions.isProjectAdmin(groupADM.project.id) && !requestingUser.permissions.isSystemAdmin
          ) {
            // not a project admin and not a system admin
            throw ForbiddenException("Group's information can only be changed by a project or system admin.")
          }

        /* create the update request */
        groupUpdatePayload = GroupUpdatePayloadADM(
                               name = changeGroupRequest.name,
                               descriptions = changeGroupRequest.descriptions,
                               status = changeGroupRequest.status,
                               selfjoin = changeGroupRequest.selfjoin
                             )

        result <- updateGroupADM(
                    groupIri = groupIri,
                    groupUpdatePayload = groupUpdatePayload,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                  )

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
   * Change group's basic information.
   *
   * @param groupIri             the IRI of the group we want to change.
   * @param changeGroupRequest   the change request.
   * @param requestingUser       the user making the request.
   * @param apiRequestID         the unique request ID.
   * @return a [[GroupOperationResponseADM]].
   */
  private def changeGroupStatusRequestADM(
    groupIri: IRI,
    changeGroupRequest: ChangeGroupApiRequestADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Future[GroupOperationResponseADM] = {

    /**
     * The actual change group task run with an IRI lock.
     */
    def changeGroupStatusTask(
      groupIri: IRI,
      changeGroupRequest: ChangeGroupApiRequestADM,
      requestingUser: UserADM
    ): Future[GroupOperationResponseADM] =
      for {

        _ <- Future(
               // check if necessary information is present
               if (groupIri.isEmpty) throw BadRequestException("Group IRI cannot be empty")
             )

        /* Get the project IRI which also verifies that the group exists. */
        maybeGroupADM <-
          groupGetADM(
            groupIri = groupIri,
            requestingUser = KnoraSystemInstances.Users.SystemUser
          )

        groupADM = maybeGroupADM.getOrElse(
                     throw NotFoundException(s"Group <$groupIri> not found. Aborting update request.")
                   )

        /* check if the requesting user is allowed to perform updates */
        _ =
          if (
            !requestingUser.permissions.isProjectAdmin(groupADM.project.id) && !requestingUser.permissions.isSystemAdmin
          ) {
            // not a project admin and not a system admin
            throw ForbiddenException("Group's status can only be changed by a project or system admin.")
          }

        maybeStatus = changeGroupRequest.status match {
                        case Some(value) =>
                          Some(GroupStatus.make(value).fold(e => throw e.head, v => v))
                        case None => None
                      }

        /* create the update request */
        groupUpdatePayload = GroupUpdatePayloadADM(
                               status = maybeStatus
                             )

        // update group status
        updateGroupResult: GroupOperationResponseADM <-
          updateGroupADM(
            groupIri = groupIri,
            groupUpdatePayload = groupUpdatePayload,
            requestingUser = KnoraSystemInstances.Users.SystemUser
          )

        // remove all members from group if status is false
        operationResponse <-
          removeGroupMembersIfNecessary(
            changedGroup = updateGroupResult.group,
            apiRequestID = apiRequestID
          )

      } yield operationResponse

    for {
      // run the change status task with an IRI lock
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID,
                      groupIri,
                      () => changeGroupStatusTask(groupIri, changeGroupRequest, requestingUser)
                    )
    } yield taskResult

  }

  /**
   * Main group update method.
   *
   * @param groupIri             the IRI of the group we are updating.
   * @param groupUpdatePayload   the payload holding the information which we want to update.
   * @param requestingUser       the profile of the user making the request.
   * @return a [[GroupOperationResponseADM]]
   */
  private def updateGroupADM(
    groupIri: IRI,
    groupUpdatePayload: GroupUpdatePayloadADM,
    requestingUser: UserADM
  ): Future[GroupOperationResponseADM] = {

    log.debug("updateGroupADM - groupIri: {}, groupUpdatePayload: {}", groupIri, groupUpdatePayload)

    val parametersCount: Int = List(
      groupUpdatePayload.name,
      groupUpdatePayload.descriptions,
      groupUpdatePayload.status,
      groupUpdatePayload.selfjoin
    ).flatten.size

    if (parametersCount == 0) throw BadRequestException("No data would be changed. Aborting update request.")

    for {
      /* Verify that the group exists. */
      maybeGroupADM <-
        groupGetADM(
          groupIri = groupIri,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )

      groupADM: GroupADM =
        maybeGroupADM.getOrElse(
          throw NotFoundException(s"Group <$groupIri> not found. Aborting update request.")
        )

      /* Verify that the potentially new name is unique */
      groupByNameAlreadyExists <-
        if (groupUpdatePayload.name.nonEmpty) {
          val newName = groupUpdatePayload.name.get
          groupByNameAndProjectExists(newName.value, groupADM.project.id)
        } else {
          FastFuture.successful(false)
        }

      _ = if (groupByNameAlreadyExists) {
            log.debug("updateGroupADM - about to throw an exception. Group with that name already exists.")
            throw BadRequestException(s"Group with the name '${groupUpdatePayload.name.get}' already exists.")
          }

      /* Update group */
      updateGroupSparqlString <-
        Future(
          org.knora.webapi.messages.twirl.queries.sparql.admin.txt
            .updateGroup(
              adminNamedGraphIri = "http://www.knora.org/data/admin",
              groupIri,
              maybeName = groupUpdatePayload.name.map(_.value),
              maybeDescriptions = groupUpdatePayload.descriptions.map(_.value),
              maybeProject = None, // maybe later we want to allow moving of a group to another project
              maybeStatus = groupUpdatePayload.status.map(_.value),
              maybeSelfjoin = groupUpdatePayload.selfjoin.map(_.value)
            )
            .toString
        )
      // _ = log.debug(s"updateProjectV1 - query: {}",updateProjectSparqlString)

      _ <- appActor.ask(SparqlUpdateRequest(updateGroupSparqlString)).mapTo[SparqlUpdateResponse]

      /* Verify that the project was updated. */
      maybeUpdatedGroup <-
        groupGetADM(
          groupIri = groupIri,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )

      updatedGroup: GroupADM =
        maybeUpdatedGroup.getOrElse(
          throw UpdateNotPerformedException("Group was not updated. Please report this as a possible bug.")
        )

      // _ = log.debug("updateProjectV1 - projectUpdatePayload: {} /  updatedProject: {}", projectUpdatePayload, updatedProject)

    } yield GroupOperationResponseADM(group = updatedGroup)

  }

  ////////////////////
  // Helper Methods //
  ////////////////////

  /**
   * Helper method that turns SPARQL result rows into a [[GroupADM]].
   *
   * @param statements           results from the SPARQL query representing information about the group.
   * @param requestingUser       the user that is making the request.
   * @return a [[GroupADM]] representing information about the group.
   */
  private def statements2GroupADM(
    statements: (SubjectV2, Map[SmartIri, Seq[LiteralV2]]),
    requestingUser: UserADM
  ): Future[Option[GroupADM]] = {

    log.debug("statements2GroupADM - statements: {}", statements)

    val groupIri: IRI                           = statements._1.toString
    val propsMap: Map[SmartIri, Seq[LiteralV2]] = statements._2

    log.debug("statements2GroupADM - groupIri: {}", groupIri)

    val maybeProjectIri = propsMap.get(OntologyConstants.KnoraAdmin.BelongsToProject.toSmartIri)
    val projectIriFuture: Future[IRI] = maybeProjectIri match {
      case Some(iri) => FastFuture.successful(iri.head.asInstanceOf[IriLiteralV2].value)
      case None =>
        FastFuture.failed(throw InconsistentRepositoryDataException(s"Group $groupIri has no project attached"))
    }

    if (propsMap.nonEmpty) {
      for {
        projectIri <- projectIriFuture
        maybeProject <-
          appActor
            .ask(
              ProjectGetADM(
                identifier = ProjectIdentifierADM(maybeIri = Some(projectIri))
              )
            )
            .mapTo[Option[ProjectADM]]

        project = maybeProject.getOrElse(
                    throw InconsistentRepositoryDataException(s"Group $groupIri has no project attached.")
                  )

        groupADM: GroupADM =
          GroupADM(
            id = groupIri,
            name = propsMap
              .getOrElse(
                OntologyConstants.KnoraAdmin.GroupName.toSmartIri,
                throw InconsistentRepositoryDataException(
                  s"Group $groupIri has no groupName attached"
                )
              )
              .head
              .asInstanceOf[StringLiteralV2]
              .value,
            descriptions = propsMap
              .getOrElse(
                OntologyConstants.KnoraAdmin.GroupDescriptions.toSmartIri,
                throw InconsistentRepositoryDataException(
                  s"Group $groupIri has no descriptions attached"
                )
              )
              .map(l =>
                l.asStringLiteral(
                  throw InconsistentRepositoryDataException(
                    s"Expected StringLiteralV2 but got ${l.getClass}"
                  )
                )
              ),
            project = project,
            status = propsMap
              .getOrElse(
                OntologyConstants.KnoraAdmin.Status.toSmartIri,
                throw InconsistentRepositoryDataException(s"Group $groupIri has no status attached")
              )
              .head
              .asInstanceOf[BooleanLiteralV2]
              .value,
            selfjoin = propsMap
              .getOrElse(
                OntologyConstants.KnoraAdmin.HasSelfJoinEnabled.toSmartIri,
                throw InconsistentRepositoryDataException(
                  s"Group $groupIri has no selfJoin attached"
                )
              )
              .head
              .asInstanceOf[BooleanLiteralV2]
              .value
          )
      } yield Some(groupADM)
    } else {
      FastFuture.successful(None)
    }
  }

  /**
   * Helper method for checking if a group identified by name / project IRI exists.
   *
   * @param name       the name of the group.
   * @param projectIri the IRI of the project.
   * @return a [[Boolean]].
   */
  private def groupByNameAndProjectExists(name: String, projectIri: IRI): Future[Boolean] =
    for {
      askString <- Future(
                     org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                       .checkGroupExistsByName(projectIri, name)
                       .toString
                   )
      // _ = log.debug("groupExists - query: {}", askString)

      checkUserExistsResponse <- appActor.ask(SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
      result                   = checkUserExistsResponse.result

      _ = log.debug("groupByNameAndProjectExists - name: {}, projectIri: {}, result: {}", name, projectIri, result)
    } yield result

  /**
   * In the case that the group was deactivated (status = false), the
   * group members need to be removed from the group.
   *
   * @param changedGroup         the group with the new status.
   * @param apiRequestID         the unique request ID.
   * @return a [[GroupOperationResponseADM]]
   */
  private def removeGroupMembersIfNecessary(
    changedGroup: GroupADM,
    apiRequestID: UUID
  ): Future[GroupOperationResponseADM] =
    if (changedGroup.status) {
      // group active. no need to remove members.
      log.debug("removeGroupMembersIfNecessary - group active. no need to remove members.")
      FastFuture.successful(GroupOperationResponseADM(changedGroup))
    } else {
      // group deactivated. need to remove members.
      log.debug("removeGroupMembersIfNecessary - group deactivated. need to remove members.")
      for {
        members <-
          groupMembersGetADM(
            groupIri = changedGroup.id,
            requestingUser = KnoraSystemInstances.Users.SystemUser
          )

        seqOfFutures: Seq[Future[UserOperationResponseADM]] =
          members.map { user: UserADM =>
            appActor
              .ask(
                UserGroupMembershipRemoveRequestADM(
                  userIri = user.id,
                  groupIri = changedGroup.id,
                  requestingUser = KnoraSystemInstances.Users.SystemUser,
                  apiRequestID = apiRequestID
                )
              )
              .mapTo[UserOperationResponseADM]
          }

        _: Seq[UserOperationResponseADM] <- Future.sequence(seqOfFutures)

      } yield GroupOperationResponseADM(group = changedGroup)
    }
}
