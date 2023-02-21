/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import com.typesafe.scalalogging.LazyLogging
import zio.ZIO
import zio._

import java.util.UUID

import dsp.errors._
import dsp.valueobjects.Group.GroupStatus
import org.knora.webapi._
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin._
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.twirl
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.util.ZioHelper

/**
 * Returns information about groups.
 */
trait GroupsResponderADM {

  /**
   * Gets all the groups (without built-in groups) and returns them as a sequence of [[GroupADM]].
   *
   * @return all the groups as a sequence of [[GroupADM]].
   */
  def groupsGetADM: Task[Seq[GroupADM]]

  /**
   * Gets all the groups and returns them as a [[GroupsGetResponseADM]].
   *
   * @return all the groups as a [[GroupsGetResponseADM]].
   */
  def groupsGetRequestADM: Task[GroupsGetResponseADM]

  /**
   * Gets the group with the given group IRI and returns the information as a [[GroupADM]].
   *
   * @param groupIri the IRI of the group requested.
   * @return information about the group as a [[GroupADM]]
   */
  def groupGetADM(groupIri: IRI): Task[Option[GroupADM]]

  /**
   * Gets the group with the given group IRI and returns the information as a [[GroupGetResponseADM]].
   *
   * @param groupIri the IRI of the group requested.
   * @return information about the group as a [[GroupGetResponseADM]].
   */
  def groupGetRequestADM(groupIri: IRI): Task[GroupGetResponseADM]

  /**
   * Gets the groups with the given IRIs and returns a set of [[GroupGetResponseADM]] objects.
   *
   * @param groupIris the IRIs of the groups being requested
   * @return information about the group as a set of [[GroupGetResponseADM]] objects.
   */
  def multipleGroupsGetRequestADM(groupIris: Set[IRI]): Task[Set[GroupGetResponseADM]]

  /**
   * Gets the group members with the given group IRI and returns the information as a [[GroupMembersGetResponseADM]].
   * Only project and system admins are allowed to access this information.
   *
   * @param groupIri       the IRI of the group.
   * @param requestingUser the user initiating the request.
   * @return A [[GroupMembersGetResponseADM]]
   */
  def groupMembersGetRequestADM(groupIri: IRI, requestingUser: UserADM): Task[GroupMembersGetResponseADM]

  /**
   * Create a new group.
   *
   * @param createRequest  the create request information.
   * @param requestingUser the user making the request.
   * @param apiRequestID   the unique request ID.
   * @return a [[GroupOperationResponseADM]]
   */
  def createGroupADM(
    createRequest: GroupCreatePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[GroupOperationResponseADM]

  /**
   * Change group's basic information.
   *
   * @param groupIri           the IRI of the group we want to change.
   * @param changeGroupRequest the change request.
   * @param requestingUser     the user making the request.
   * @param apiRequestID       the unique request ID.
   * @return a [[GroupOperationResponseADM]].
   */
  def changeGroupBasicInformationRequestADM(
    groupIri: IRI,
    changeGroupRequest: GroupUpdatePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[GroupOperationResponseADM]

  /**
   * Change group's basic information.
   *
   * @param groupIri           the IRI of the group we want to change.
   * @param changeGroupRequest the change request.
   * @param requestingUser     the user making the request.
   * @param apiRequestID       the unique request ID.
   * @return a [[GroupOperationResponseADM]].
   */
  def changeGroupStatusRequestADM(
    groupIri: IRI,
    changeGroupRequest: ChangeGroupApiRequestADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[GroupOperationResponseADM]
}

final case class GroupsResponderADMLive(
  triplestoreService: TriplestoreService,
  messageRelay: MessageRelay,
  iriService: IriService,
  implicit val stringFormatter: StringFormatter
) extends GroupsResponderADM
    with MessageHandler
    with GroupsADMJsonProtocol
    with LazyLogging {

  // Global lock IRI used for group creation and updating
  private val GROUPS_GLOBAL_LOCK_IRI: IRI = "http://rdfh.ch/groups"

  override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[GroupsResponderRequestADM]

  /**
   * Receives a message extending [[GroupsResponderRequestADM]], and returns an appropriate response message
   */
  def handle(msg: ResponderRequest): Task[Any] = msg match {
    case _: GroupsGetADM                => groupsGetADM
    case _: GroupsGetRequestADM         => groupsGetRequestADM
    case r: GroupGetADM                 => groupGetADM(r.groupIri)
    case r: MultipleGroupsGetRequestADM => multipleGroupsGetRequestADM(r.groupIris)
    case r: GroupGetRequestADM          => groupGetRequestADM(r.groupIri)
    case r: GroupMembersGetRequestADM   => groupMembersGetRequestADM(r.groupIri, r.requestingUser)
    case r: GroupCreateRequestADM       => createGroupADM(r.createRequest, r.requestingUser, r.apiRequestID)
    case r: GroupChangeRequestADM =>
      changeGroupBasicInformationRequestADM(r.groupIri, r.changeGroupRequest, r.requestingUser, r.apiRequestID)
    case r: GroupChangeStatusRequestADM =>
      changeGroupStatusRequestADM(r.groupIri, r.changeGroupRequest, r.requestingUser, r.apiRequestID)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Gets all the groups (without built-in groups) and returns them as a sequence of [[GroupADM]].
   *
   * @return all the groups as a sequence of [[GroupADM]].
   */
  override def groupsGetADM: Task[Seq[GroupADM]] = {
    val query = twirl.queries.sparql.admin.txt.getGroups(None)
    for {
      groupsResponse <- triplestoreService.sparqlHttpExtendedConstruct(query.toString())
      groups          = groupsResponse.statements.map(convertStatementsToGroupADM)
      result         <- ZioHelper.sequence(groups.toSeq)
    } yield result.sorted
  }

  private def convertStatementsToGroupADM(statements: (SubjectV2, ConstructPredicateObjects)): Task[GroupADM] = {
    val groupIri: SubjectV2                      = statements._1
    val propertiesMap: ConstructPredicateObjects = statements._2
    def getOption[A <: LiteralV2](key: IRI): UIO[Option[Seq[A]]] =
      ZIO.succeed(propertiesMap.get(key.toSmartIri).map(_.map(_.asInstanceOf[A])))
    def getOrFail[A <: LiteralV2](key: IRI): Task[Seq[A]] =
      getOption[A](key)
        .flatMap(ZIO.fromOption(_))
        .orElseFail(InconsistentRepositoryDataException(s"Project: $groupIri has no $key defined."))
    def getFirstValueOrFail[A <: LiteralV2](key: IRI): Task[A] = getOrFail[A](key).map(_.head)
    for {
      projectIri <- getFirstValueOrFail[IriLiteralV2](BelongsToProject).map(_.value)
      projectADM <- getProjectGetADMOrFail(
                      projectIri,
                      InconsistentRepositoryDataException(
                        s"Project $projectIri was referenced by $groupIri but was not found in the triplestore."
                      )
                    )
      name         <- getFirstValueOrFail[StringLiteralV2](GroupName).map(_.value)
      descriptions <- getOrFail[StringLiteralV2](GroupDescriptions)
      status       <- getFirstValueOrFail[BooleanLiteralV2](Status).map(_.value)
      selfjoin     <- getFirstValueOrFail[BooleanLiteralV2](HasSelfJoinEnabled).map(_.value)
    } yield GroupADM(groupIri.toString, name, descriptions, projectADM, status, selfjoin)
  }
  private def getProjectGetADMOrFail(iri: String, failReason: Throwable): Task[ProjectADM] =
    for {
      id     <- IriIdentifier.fromString(iri).toZIO.mapError(e => BadRequestException(e.getMessage))
      result <- getProjectGetADMOrFail(id, failReason)
    } yield result

  private def getProjectGetADMOrFail(id: ProjectIdentifierADM, failReason: Throwable): Task[ProjectADM] =
    messageRelay
      .ask[Option[ProjectADM]](ProjectGetADM(id))
      .flatMap(ZIO.fromOption(_))
      .orElseFail(failReason)

  /**
   * Gets all the groups and returns them as a [[GroupsGetResponseADM]].
   *
   * @return all the groups as a [[GroupsGetResponseADM]].
   */
  override def groupsGetRequestADM: Task[GroupsGetResponseADM] = groupsGetADM.map(GroupsGetResponseADM)

  /**
   * Gets the group with the given group IRI and returns the information as a [[GroupADM]].
   *
   * @param groupIri       the IRI of the group requested.
   * @return information about the group as a [[GroupADM]]
   */
  override def groupGetADM(groupIri: IRI): Task[Option[GroupADM]] = {
    val query = twirl.queries.sparql.admin.txt.getGroups(maybeIri = Some(groupIri))
    for {
      statements <- triplestoreService.sparqlHttpExtendedConstruct(query.toString()).map(_.statements.headOption)
      maybeGroup <- statements.map(convertStatementsToGroupADM).map(_.map(Some(_))).getOrElse(ZIO.succeed(None))
    } yield maybeGroup
  }

  /**
   * Gets the group with the given group IRI and returns the information as a [[GroupGetResponseADM]].
   *
   * @param groupIri             the IRI of the group requested.
   * @return information about the group as a [[GroupGetResponseADM]].
   */
  override def groupGetRequestADM(groupIri: IRI): Task[GroupGetResponseADM] =
    groupGetADM(groupIri)
      .flatMap(ZIO.fromOption(_))
      .mapBoth(_ => NotFoundException(s"Group <$groupIri> not found."), GroupGetResponseADM)

  /**
   * Gets the groups with the given IRIs and returns a set of [[GroupGetResponseADM]] objects.
   *
   * @param groupIris      the IRIs of the groups being requested
   * @return information about the group as a set of [[GroupGetResponseADM]] objects.
   */
  override def multipleGroupsGetRequestADM(groupIris: Set[IRI]): Task[Set[GroupGetResponseADM]] =
    ZioHelper.sequence(groupIris.map(groupGetADM)).map(_.flatten.map(GroupGetResponseADM))

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
  ): Task[Seq[UserADM]] =
    for {
      maybeGroupADM <- groupGetADM(groupIri)

      userPermissions = requestingUser.permissions
      _ = maybeGroupADM match {
            case Some(group) =>
              // check if the requesting user is allowed to access the information
              if (
                !userPermissions.isProjectAdmin(group.project.id) &&
                !userPermissions.isSystemAdmin && !requestingUser.isSystemUser
              ) {
                // not a project admin and not a system admin
                throw ForbiddenException("Project members can only be retrieved by a project or system admin.")
              }
            case None =>
              throw NotFoundException(s"Group <$groupIri> not found")
          }

      query                 = twirl.queries.sparql.v1.txt.getGroupMembersByIri(groupIri)
      groupMembersResponse <- triplestoreService.sparqlHttpSelect(query.toString())

      // get project member IRI from results rows
      groupMemberIris =
        if (groupMembersResponse.results.bindings.nonEmpty) {
          groupMembersResponse.results.bindings.map(_.rowMap("s"))
        } else {
          Seq.empty[IRI]
        }

      usersMaybeTasks: Seq[Task[Option[UserADM]]] =
        groupMemberIris.map { userIri =>
          messageRelay
            .ask[Option[UserADM]](
              UserGetADM(
                UserIdentifierADM(maybeIri = Some(userIri)),
                userInformationTypeADM = UserInformationTypeADM.Restricted,
                requestingUser = KnoraSystemInstances.Users.SystemUser
              )
            )
        }
      users <- ZioHelper.sequence(usersMaybeTasks).map(_.flatten)
    } yield users

  /**
   * Gets the group members with the given group IRI and returns the information as a [[GroupMembersGetResponseADM]].
   * Only project and system admins are allowed to access this information.
   *
   * @param groupIri             the IRI of the group.
   * @param requestingUser       the user initiating the request.
   * @return A [[GroupMembersGetResponseADM]]
   */
  override def groupMembersGetRequestADM(groupIri: IRI, requestingUser: UserADM): Task[GroupMembersGetResponseADM] =
    groupMembersGetADM(groupIri, requestingUser).map(GroupMembersGetResponseADM)

  /**
   * Create a new group.
   *
   * @param createRequest        the create request information.
   * @param requestingUser       the user making the request.
   * @param apiRequestID         the unique request ID.
   * @return a [[GroupOperationResponseADM]]
   */
  override def createGroupADM(
    createRequest: GroupCreatePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[GroupOperationResponseADM] = {
    def createGroupTask(
      createRequest: GroupCreatePayloadADM,
      requestingUser: UserADM
    ): Task[GroupOperationResponseADM] =
      for {
        /* check if the requesting user is allowed to create group */
        _ <- ZIO.attempt {
               val userPermissions = requestingUser.permissions
               if (
                 !userPermissions
                   .isProjectAdmin(createRequest.project.value) && !userPermissions.isSystemAdmin
               ) {
                 // not a project admin and not a system admin
                 throw ForbiddenException("A new group can only be created by a project or system admin.")
               }
             }

        iri = createRequest.project.value
        nameExists <- groupByNameAndProjectExists(
                        name = createRequest.name.value,
                        projectIri = iri
                      )
        _ = if (nameExists) {
              throw DuplicateValueException(s"Group with the name '${createRequest.name.value}' already exists")
            }

        projectADM <- getProjectGetADMOrFail(
                        iri,
                        NotFoundException(
                          s"Cannot create group inside project <${createRequest.project}>. The project was not found."
                        )
                      )

        // check the custom IRI; if not given, create an unused IRI
        customGroupIri: Option[SmartIri] = createRequest.id.map(_.value).map(iri => iri.toSmartIri)
        groupIri <- iriService.checkOrCreateEntityIriTask(
                      customGroupIri,
                      stringFormatter.makeRandomGroupIri(projectADM.shortcode)
                    )

        /* create the group */
        createNewGroupSparqlString =
          twirl.queries.sparql.admin.txt
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

        _ <- triplestoreService.sparqlHttpUpdate(createNewGroupSparqlString.toString())

        /* Verify that the group was created and updated  */
        createdGroup <-
          groupGetADM(groupIri)
            .flatMap(ZIO.fromOption(_))
            .orElseFail(UpdateNotPerformedException(s"Group was not created. Please report this as a possible bug."))

      } yield GroupOperationResponseADM(createdGroup)

    val task = createGroupTask(createRequest, requestingUser)
    IriLocker.runWithIriLock(apiRequestID, GROUPS_GLOBAL_LOCK_IRI, task)
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
  override def changeGroupBasicInformationRequestADM(
    groupIri: IRI,
    changeGroupRequest: GroupUpdatePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[GroupOperationResponseADM] = {

    /**
     * The actual change group task run with an IRI lock.
     */
    def changeGroupTask(
      groupIri: IRI,
      changeGroupRequest: GroupUpdatePayloadADM,
      requestingUser: UserADM
    ): Task[GroupOperationResponseADM] =
      for {
        _ <- ZIO.attempt(
               // check if necessary information is present
               if (groupIri.isEmpty) throw BadRequestException("Group IRI cannot be empty")
             )

        /* Get the project IRI which also verifies that the group exists. */
        groupADMOpt <- groupGetADM(groupIri)
        groupADM <- ZIO
                      .fromOption(groupADMOpt)
                      .orElseFail(NotFoundException(s"Group <$groupIri> not found. Aborting update request."))

        /* check if the requesting user is allowed to perform updates */
        userPermissions = requestingUser.permissions
        _ = if (!userPermissions.isProjectAdmin(groupADM.project.id) && !userPermissions.isSystemAdmin) {
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
        result <- updateGroupADM(groupIri, groupUpdatePayload, KnoraSystemInstances.Users.SystemUser)
      } yield result

    val task = changeGroupTask(groupIri, changeGroupRequest, requestingUser)
    IriLocker.runWithIriLock(apiRequestID, groupIri, task)
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
  override def changeGroupStatusRequestADM(
    groupIri: IRI,
    changeGroupRequest: ChangeGroupApiRequestADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[GroupOperationResponseADM] = {

    /**
     * The actual change group task run with an IRI lock.
     */
    def changeGroupStatusTask(
      groupIri: IRI,
      changeGroupRequest: ChangeGroupApiRequestADM,
      requestingUser: UserADM
    ): Task[GroupOperationResponseADM] =
      for {

        _ <- ZIO.attempt(
               // check if necessary information is present
               if (groupIri.isEmpty) throw BadRequestException("Group IRI cannot be empty")
             )

        /* Get the project IRI which also verifies that the group exists. */
        groupADM <- groupGetADM(groupIri)
                      .flatMap(ZIO.fromOption(_))
                      .orElseFail(NotFoundException(s"Group <$groupIri> not found. Aborting update request."))

        /* check if the requesting user is allowed to perform updates */
        userPermissions = requestingUser.permissions
        _ =
          if (!userPermissions.isProjectAdmin(groupADM.project.id) && !userPermissions.isSystemAdmin) {
            // not a project admin and not a system admin
            throw ForbiddenException("Group's status can only be changed by a project or system admin.")
          }

        maybeStatus = changeGroupRequest.status match {
                        case Some(value) =>
                          Some(GroupStatus.make(value).fold(e => throw e.head, v => v))
                        case None => None
                      }

        /* create the update request */
        groupUpdatePayload = GroupUpdatePayloadADM(status = maybeStatus)

        // update group status
        updateGroupResult <- updateGroupADM(groupIri, groupUpdatePayload, KnoraSystemInstances.Users.SystemUser)

        // remove all members from group if status is false
        operationResponse <-
          removeGroupMembersIfNecessary(
            changedGroup = updateGroupResult.group,
            apiRequestID = apiRequestID
          )

      } yield operationResponse

    val task = changeGroupStatusTask(groupIri, changeGroupRequest, requestingUser)
    IriLocker.runWithIriLock(apiRequestID, groupIri, task)
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
  ): Task[GroupOperationResponseADM] = {

    logger.debug("updateGroupADM - groupIri: {}, groupUpdatePayload: {}", groupIri, groupUpdatePayload)

    val parametersCount: Int = List(
      groupUpdatePayload.name,
      groupUpdatePayload.descriptions,
      groupUpdatePayload.status,
      groupUpdatePayload.selfjoin
    ).flatten.size

    if (parametersCount == 0) throw BadRequestException("No data would be changed. Aborting update request.")

    for {
      /* Verify that the group exists. */
      groupADM <- groupGetADM(groupIri)
                    .flatMap(ZIO.fromOption(_))
                    .orElseFail(NotFoundException(s"Group <$groupIri> not found. Aborting update request."))

      /* Verify that the potentially new name is unique */
      groupByNameAlreadyExists <-
        if (groupUpdatePayload.name.nonEmpty) {
          val newName = groupUpdatePayload.name.get
          groupByNameAndProjectExists(newName.value, groupADM.project.id)
        } else {
          ZIO.succeed(false)
        }

      _ = if (groupByNameAlreadyExists) {
            throw BadRequestException(s"Group with the name '${groupUpdatePayload.name.get}' already exists.")
          }

      /* Update group */
      updateGroupSparqlString =
        twirl.queries.sparql.admin.txt
          .updateGroup(
            adminNamedGraphIri = "http://www.knora.org/data/admin",
            groupIri,
            maybeName = groupUpdatePayload.name.map(_.value),
            maybeDescriptions = groupUpdatePayload.descriptions.map(_.value),
            maybeProject = None, // maybe later we want to allow moving of a group to another project
            maybeStatus = groupUpdatePayload.status.map(_.value),
            maybeSelfjoin = groupUpdatePayload.selfjoin.map(_.value)
          )

      _ <- triplestoreService.sparqlHttpUpdate(updateGroupSparqlString.toString())

      /* Verify that the project was updated. */
      updatedGroup <-
        groupGetADM(groupIri)
          .flatMap(ZIO.fromOption(_))
          .orElseFail(UpdateNotPerformedException("Group was not updated. Please report this as a possible bug."))
    } yield GroupOperationResponseADM(updatedGroup)

  }

  ////////////////////
  // Helper Methods //
  ////////////////////

  /**
   * Helper method for checking if a group identified by name / project IRI exists.
   *
   * @param name       the name of the group.
   * @param projectIri the IRI of the project.
   * @return a [[Boolean]].
   */
  private def groupByNameAndProjectExists(name: String, projectIri: IRI): Task[Boolean] = {
    val query = twirl.queries.sparql.admin.txt.checkGroupExistsByName(projectIri, name)
    triplestoreService.sparqlHttpAsk(query.toString()).map(_.result)
  }

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
  ): Task[GroupOperationResponseADM] =
    if (changedGroup.status) {
      // group active. no need to remove members.
      logger.debug("removeGroupMembersIfNecessary - group active. no need to remove members.")
      ZIO.succeed(GroupOperationResponseADM(changedGroup))
    } else {
      // group deactivated. need to remove members.
      logger.debug("removeGroupMembersIfNecessary - group deactivated. need to remove members.")
      for {
        members <-
          groupMembersGetADM(
            groupIri = changedGroup.id,
            requestingUser = KnoraSystemInstances.Users.SystemUser
          )

        seqOfFutures: Seq[Task[UserOperationResponseADM]] =
          members.map { user: UserADM =>
            messageRelay
              .ask[UserOperationResponseADM](
                UserGroupMembershipRemoveRequestADM(
                  userIri = user.id,
                  groupIri = changedGroup.id,
                  requestingUser = KnoraSystemInstances.Users.SystemUser,
                  apiRequestID = apiRequestID
                )
              )
          }

        _ <- ZioHelper.sequence(seqOfFutures)

      } yield GroupOperationResponseADM(group = changedGroup)
    }
}

object GroupsResponderADMLive {
  val layer: URLayer[
    MessageRelay with StringFormatter with IriService with TriplestoreService,
    GroupsResponderADM
  ] = ZLayer.fromZIO {
    for {
      ts      <- ZIO.service[TriplestoreService]
      iris    <- ZIO.service[IriService]
      sf      <- ZIO.service[StringFormatter]
      mr      <- ZIO.service[MessageRelay]
      handler <- mr.subscribe(GroupsResponderADMLive(ts, mr, iris, sf))
    } yield handler
  }
}
