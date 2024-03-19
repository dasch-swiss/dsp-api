/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import com.typesafe.scalalogging.LazyLogging
import zio.*

import java.util.UUID
import scala.collection.immutable.Iterable
import scala.collection.mutable.ListBuffer
import dsp.errors.*
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraBase.EntityPermissionAbbreviations
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupGetADM
import org.knora.webapi.messages.admin.responder.permissionsmessages
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsMessagesUtilADM.PermissionTypeAndCodes
import org.knora.webapi.messages.admin.responder.projectsmessages.Project
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.Group
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.util.ZioHelper

/**
 * Provides information about permissions to other responders.
 */
trait PermissionsResponderADM {

  /**
   * By providing all the projects and groups in which the user is a member of, calculate the user's
   * administrative permissions of each project by applying the precedence rules.
   *
   * @param groupsPerProject the groups inside each project the user is member of.
   * @return a the user's resulting set of administrative permissions for each project.
   */
  def userAdministrativePermissionsGetADM(groupsPerProject: Map[IRI, Seq[IRI]]): Task[Map[IRI, Set[PermissionADM]]]

  /**
   * Adds a new administrative permission (internal use).
   *
   * @param createRequest  the administrative permission to add.
   * @param requestingUser the requesting user.
   * @param apiRequestID   the API request ID.
   * @return an optional [[AdministrativePermissionADM]]
   */
  def createAdministrativePermission(
    createRequest: CreateAdministrativePermissionAPIRequestADM,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[AdministrativePermissionCreateResponseADM]

  /**
   * Gets a single administrative permission identified by project and group.
   *
   * @param projectIri the project.
   * @param groupIri   the group.
   * @return an [[AdministrativePermissionGetResponseADM]]
   */
  def getPermissionsApByProjectAndGroupIri(
    projectIri: IRI,
    groupIri: IRI,
  ): Task[AdministrativePermissionGetResponseADM]

  /**
   * Gets all administrative permissions defined inside a project.
   *
   * @param projectIRI the IRI of the project.
   * @return a list of IRIs of [[AdministrativePermissionADM]] objects.
   */
  def getPermissionsApByProjectIri(projectIRI: IRI): Task[AdministrativePermissionsForProjectGetResponseADM]

  /**
   * Delete a permission.
   *
   * @param permissionIri  the IRI of the permission.
   * @param requestingUser the [[UserADM]] of the requesting user.
   * @param apiRequestID   the API request ID.
   * @return [[PermissionDeleteResponseADM]].
   *         fails with an UpdateNotPerformedException if permission was in use and could not be deleted or something else went wrong.
   *         fails with a NotFoundException if no permission is found for the given IRI.
   */
  def deletePermission(
    permissionIri: PermissionIri,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionDeleteResponseADM]

  def createDefaultObjectAccessPermission(
    createRequest: CreateDefaultObjectAccessPermissionAPIRequestADM,
    user: User,
    apiRequestID: UUID,
  ): Task[DefaultObjectAccessPermissionCreateResponseADM]

  /**
   * For default object access permission, we need to make sure that the value given for the permissionCode matches
   * the value of name parameter.
   * This method, validates the content of hasPermissions collection by verifying that both permissionCode and name
   * indicate the same type of permission.
   *
   * @param hasPermissions Set of the permissions.
   */
  def verifyHasPermissionsDOAP(hasPermissions: Set[PermissionADM]): Task[Set[PermissionADM]]

  /**
   * Gets all IRI's of all default object access permissions defined inside a project.
   *
   * @param projectIri the IRI of the project.
   * @return a list of IRIs of [[DefaultObjectAccessPermissionADM]] objects.
   */
  def getPermissionsDaopByProjectIri(
    projectIri: ProjectIri,
  ): Task[DefaultObjectAccessPermissionsForProjectGetResponseADM]

  /**
   * Gets all permissions defined inside a project.
   *
   * @param projectIri the IRI of the project.
   * @return a list of of [[PermissionInfoADM]] objects.
   */
  def getPermissionsByProjectIri(projectIri: ProjectIri): Task[PermissionsForProjectGetResponseADM]

  /**
   * Update a permission's group
   *
   * @param permissionIri                the IRI of the permission.
   * @param groupIri                     the [[GroupIri]] to change.
   * @param requestingUser               the [[User]] of the requesting user.
   * @param apiRequestID                 the API request ID.
   * @return [[PermissionGetResponseADM]].
   *         fails with an UpdateNotPerformedException if something has gone wrong.
   */
  def updatePermissionsGroup(
    permissionIri: PermissionIri,
    groupIri: GroupIri,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM]

  /**
   * Update a permission's set of hasPermissions.
   *
   * @param permissionIri     the IRI of the permission.
   * @param newHasPermissions the request to change hasPermissions.
   * @param requestingUser    the [[User]] of the requesting user.
   * @param apiRequestID      the API request ID.
   * @return [[PermissionGetResponseADM]].
   *         fails with an UpdateNotPerformedException if something has gone wrong.
   */
  def updatePermissionHasPermissions(
    permissionIri: PermissionIri,
    newHasPermissions: NonEmptyChunk[PermissionADM],
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM]

  /**
   * Update a doap permission's resource class.
   *
   * @param permissionIri                 the IRI of the permission.
   * @param changePermissionResourceClass the request to change hasPermissions.
   * @param requestingUser                the [[User]] of the requesting user.
   * @param apiRequestID                  the API request ID.
   * @return [[PermissionGetResponseADM]].
   *         fails with an UpdateNotPerformedException if something has gone wrong.
   */
  def updatePermissionResourceClass(
    permissionIri: PermissionIri,
    changePermissionResourceClass: ChangePermissionResourceClassApiRequestADM,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM]

  /**
   * Update a doap permission's property.
   *
   * @param permissionIri                   the IRI of the permission.
   * @param changePermissionPropertyRequest the request to change hasPermissions.
   * @param requestingUser                  the [[User]] of the requesting user.
   * @param apiRequestID                    the API request ID.
   * @return [[PermissionGetResponseADM]].
   *         fails with an UpdateNotPerformedException if something has gone wrong.
   */
  def updatePermissionProperty(
    permissionIri: PermissionIri,
    changePermissionPropertyRequest: ChangePermissionPropertyApiRequestADM,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM]

  /**
   * Creates the user's [[PermissionsDataADM]]
   *
   * @param projectIris            the projects the user is part of.
   * @param groupIris              the groups the user is member of (without ProjectMember, ProjectAdmin, SystemAdmin)
   * @param isInProjectAdminGroups the projects in which the user is member of the ProjectAdmin group.
   * @param isInSystemAdminGroup   the flag denoting membership in the SystemAdmin group.
   * @return
   */
  def permissionsDataGetADM(
    projectIris: Seq[IRI],
    groupIris: Seq[IRI],
    isInProjectAdminGroups: Seq[IRI],
    isInSystemAdminGroup: Boolean,
  ): Task[PermissionsDataADM]
}

final case class PermissionsResponderADMLive(
  appConfig: AppConfig,
  iriService: IriService,
  messageRelay: MessageRelay,
  triplestore: TriplestoreService,
  knoraProjectService: KnoraProjectService,
  auth: AuthorizationRestService,
  implicit val stringFormatter: StringFormatter,
) extends PermissionsResponderADM
    with MessageHandler
    with LazyLogging {

  private val PERMISSIONS_GLOBAL_LOCK_IRI = "http://rdfh.ch/permissions"
  /* Entity types used to more clearly distinguish what kind of entity is meant */
  private val ResourceEntityType = "resource"
  private val PropertyEntityType = "property"

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[PermissionsResponderRequestADM]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case PermissionDataGetADM(
          projectIris,
          groupIris,
          isInProjectAdminGroup,
          isInSystemAdminGroup,
          _,
        ) =>
      permissionsDataGetADM(projectIris, groupIris, isInProjectAdminGroup, isInSystemAdminGroup)
    case AdministrativePermissionForIriGetRequestADM(administrativePermissionIri, requestingUser, _) =>
      administrativePermissionForIriGetRequestADM(administrativePermissionIri, requestingUser)
    case AdministrativePermissionForProjectGroupGetADM(projectIri, groupIri, _) =>
      administrativePermissionForProjectGroupGetADM(projectIri, groupIri)
    case ObjectAccessPermissionsForResourceGetADM(resourceIri, requestingUser) =>
      objectAccessPermissionsForResourceGetADM(resourceIri, requestingUser)
    case ObjectAccessPermissionsForValueGetADM(valueIri, requestingUser) =>
      objectAccessPermissionsForValueGetADM(valueIri, requestingUser)
    case DefaultObjectAccessPermissionForIriGetRequestADM(
          defaultObjectAccessPermissionIri,
          requestingUser,
          _,
        ) =>
      defaultObjectAccessPermissionForIriGetRequestADM(defaultObjectAccessPermissionIri, requestingUser)
    case DefaultObjectAccessPermissionGetRequestADM(
          projectIri,
          groupIri,
          resourceClassIri,
          propertyIri,
          _,
        ) =>
      defaultObjectAccessPermissionGetRequestADM(projectIri, groupIri, resourceClassIri, propertyIri)
    case DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri,
          resourceClassIri,
          targetUser,
          _,
        ) =>
      defaultObjectAccessPermissionsStringForEntityGetADM(
        projectIri,
        resourceClassIri,
        None,
        ResourceEntityType,
        targetUser,
      )
    case DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri,
          resourceClassIri,
          propertyTypeIri,
          targetUser,
          _,
        ) =>
      defaultObjectAccessPermissionsStringForEntityGetADM(
        projectIri,
        resourceClassIri,
        Some(propertyTypeIri),
        PropertyEntityType,
        targetUser,
      )
    case PermissionByIriGetRequestADM(permissionIri, requestingUser) =>
      permissionByIriGetRequestADM(permissionIri, requestingUser)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
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
  override def permissionsDataGetADM(
    projectIris: Seq[IRI],
    groupIris: Seq[IRI],
    isInProjectAdminGroups: Seq[IRI],
    isInSystemAdminGroup: Boolean,
  ): Task[PermissionsDataADM] = {
    // find out which project each group belongs to

    val groupFutures: Seq[Task[(IRI, IRI)]] = if (groupIris.nonEmpty) {
      groupIris.map { groupIri =>
        for {
          maybeGroup <- messageRelay.ask[Option[Group]](GroupGetADM(groupIri))

          group = maybeGroup.getOrElse(
                    throw InconsistentRepositoryDataException(
                      s"Cannot find information for group: '$groupIri'. Please report as possible bug.",
                    ),
                  )
          res = (group.project.id, groupIri)
        } yield res
      }
    } else {
      Seq.empty[Task[(IRI, IRI)]]
    }

    for {
      groups <- ZioHelper.sequence(groupFutures).map(_.toSeq)

      /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#ProjectMember' group for each project */
      projectMembers =
        if (projectIris.nonEmpty) {
          for {
            projectIri <- projectIris.toVector
            res         = (projectIri, OntologyConstants.KnoraAdmin.ProjectMember)
          } yield res
        } else {
          Seq.empty[(IRI, IRI)]
        }

      /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#ProjectAdmin' group for each project */
      projectAdmins =
        if (projectIris.nonEmpty) {
          for {
            projectAdminForGroup <- isInProjectAdminGroups
            res                   = (projectAdminForGroup, OntologyConstants.KnoraAdmin.ProjectAdmin)
          } yield res
        } else {
          Seq.empty[(IRI, IRI)]
        }

      /* materialize implicit membership in 'http://www.knora.org/ontology/knora-base#SystemAdmin' group */
      systemAdmin =
        if (isInSystemAdminGroup) {
          Seq((OntologyConstants.KnoraAdmin.SystemProject, OntologyConstants.KnoraAdmin.SystemAdmin))
        } else {
          Seq.empty[(IRI, IRI)]
        }

      /* combine explicit groups with materialized implicit groups */
      /* here we don't add the KnownUser group, as this would inflate the whole thing. */
      allGroups        = groups ++ projectMembers ++ projectAdmins ++ systemAdmin
      groupsPerProject = allGroups.groupBy(_._1).map { case (k, v) => (k, v.map(_._2)) }

      /* retrieve the administrative permissions for each group per project the user is member of */
      administrativePermissionsPerProjectFuture: Task[Map[IRI, Set[PermissionADM]]] =
        if (projectIris.nonEmpty) {
          userAdministrativePermissionsGetADM(groupsPerProject)
        } else {
          ZIO.attempt(Map.empty[IRI, Set[PermissionADM]])
        }
      administrativePermissionsPerProject <- administrativePermissionsPerProjectFuture

      /* construct the permission profile from the different parts */
      result = PermissionsDataADM(
                 groupsPerProject = groupsPerProject,
                 administrativePermissionsPerProject = administrativePermissionsPerProject,
               )

    } yield result
  }

  /**
   * By providing all the projects and groups in which the user is a member of, calculate the user's
   * administrative permissions of each project by applying the precedence rules.
   *
   * @param groupsPerProject the groups inside each project the user is member of.
   * @return a the user's resulting set of administrative permissions for each project.
   */
  override def userAdministrativePermissionsGetADM(
    groupsPerProject: Map[IRI, Seq[IRI]],
  ): Task[Map[IRI, Set[PermissionADM]]] = {

    /* Get all permissions per project, applying permission precedence rule */
    def calculatePermission(projectIri: IRI, extendedUserGroups: Seq[IRI]): Task[(IRI, Set[PermissionADM])] = {

      /* List buffer holding default object access permissions tagged with the precedence level:
         1. ProjectAdmin > 2. CustomGroups > 3. ProjectMember > 4. KnownUser
         Permissions are added following the precedence level from the highest to the lowest. As soon as one set
         of permissions is written into the buffer, any additionally found permissions are ignored. */
      val permissionsListBuffer = ListBuffer.empty[(String, Set[PermissionADM])]

      for {
        /* Get administrative permissions for the knora-base:ProjectAdmin group */
        administrativePermissionsOnProjectAdminGroup <-
          administrativePermissionForGroupsGetADM(
            projectIri,
            List(OntologyConstants.KnoraAdmin.ProjectAdmin),
          )
        _ = if (administrativePermissionsOnProjectAdminGroup.nonEmpty) {
              if (extendedUserGroups.contains(OntologyConstants.KnoraAdmin.ProjectAdmin)) {
                permissionsListBuffer += (("ProjectAdmin", administrativePermissionsOnProjectAdminGroup))
              }
            }

        /* Get administrative permissions for custom groups (all groups other than the built-in groups) */
        administrativePermissionsOnCustomGroups <- {
          val customGroups = extendedUserGroups diff List(
            OntologyConstants.KnoraAdmin.KnownUser,
            OntologyConstants.KnoraAdmin.ProjectMember,
            OntologyConstants.KnoraAdmin.ProjectAdmin,
          )
          if (customGroups.nonEmpty) {
            administrativePermissionForGroupsGetADM(projectIri, customGroups)
          } else {
            ZIO.attempt(Set.empty[PermissionADM])
          }
        }
        _ = if (administrativePermissionsOnCustomGroups.nonEmpty) {
              if (permissionsListBuffer.isEmpty) {
                permissionsListBuffer += (("CustomGroups", administrativePermissionsOnCustomGroups))
              }
            }

        /* Get administrative permissions for the knora-base:ProjectMember group */
        administrativePermissionsOnProjectMemberGroup <-
          administrativePermissionForGroupsGetADM(
            projectIri,
            List(OntologyConstants.KnoraAdmin.ProjectMember),
          )
        _ = if (administrativePermissionsOnProjectMemberGroup.nonEmpty) {
              if (permissionsListBuffer.isEmpty) {
                if (extendedUserGroups.contains(OntologyConstants.KnoraAdmin.ProjectMember)) {
                  permissionsListBuffer += (("ProjectMember", administrativePermissionsOnProjectMemberGroup))
                }
              }
            }

        /* Get administrative permissions for the knora-base:KnownUser group */
        administrativePermissionsOnKnownUserGroup <- administrativePermissionForGroupsGetADM(
                                                       projectIri,
                                                       List(OntologyConstants.KnoraAdmin.KnownUser),
                                                     )
        _ = if (administrativePermissionsOnKnownUserGroup.nonEmpty) {
              if (permissionsListBuffer.isEmpty) {
                if (extendedUserGroups.contains(OntologyConstants.KnoraAdmin.KnownUser)) {
                  permissionsListBuffer += (("KnownUser", administrativePermissionsOnKnownUserGroup))
                }
              }
            }

        projectAdministrativePermissions: (IRI, Set[PermissionADM]) = permissionsListBuffer.length match {
                                                                        case 1 =>
                                                                          (projectIri, permissionsListBuffer.head._2)

                                                                        case 0 => (projectIri, Set.empty[PermissionADM])
                                                                        case _ =>
                                                                          throw AssertionException(
                                                                            "The permissions list buffer holding default object permissions should never be larger then 1.",
                                                                          )
                                                                      }

      } yield projectAdministrativePermissions
    }

    val permissionsPerProject: Iterable[Task[(IRI, Set[PermissionADM])]] = for {
      (projectIri, groups) <- groupsPerProject

      /* Explicitly add 'KnownUser' group */
      extendedUserGroups = groups :+ OntologyConstants.KnoraAdmin.KnownUser

      result = calculatePermission(projectIri, extendedUserGroups)

    } yield result

    val result: Task[Map[IRI, Set[PermissionADM]]] = ZioHelper.sequence(permissionsPerProject.toSeq).map(_.toMap)

    result
  }

  /**
   * **********************************************************************
   */
  /* ADMINISTRATIVE PERMISSIONS                                            */
  /**
   * **********************************************************************
   */
  /**
   * Convenience method returning a set with combined administrative permission.
   *
   * @param projectIri the IRI of the project.
   * @param groups     the list of groups for which administrative permissions are retrieved and combined.
   * @return a set of [[PermissionADM]].
   */
  private def administrativePermissionForGroupsGetADM(projectIri: IRI, groups: Seq[IRI]): Task[Set[PermissionADM]] = {

    /* Get administrative permissions for each group and combine them */
    val gpf: Seq[Task[Seq[PermissionADM]]] = for {
      groupIri <- groups

      groupPermissions: Task[Seq[PermissionADM]] =
        administrativePermissionForProjectGroupGetADM(projectIri, groupIri).map {
          case Some(ap: AdministrativePermissionADM) =>
            ap.hasPermissions.toSeq
          case None => Seq.empty[PermissionADM]
        }

    } yield groupPermissions

    val allPermissionsFuture: Task[Seq[Seq[PermissionADM]]] = ZioHelper.sequence(gpf)

    /* combines all permissions for each group and removes duplicates  */
    val result: Task[Set[PermissionADM]] = for {
      allPermissions <- allPermissionsFuture

      // remove instances with empty PermissionADM sets
      cleanedAllPermissions = allPermissions.filter(_.nonEmpty)

      /* Combine permission sequences */
      combined = cleanedAllPermissions.foldLeft(Seq.empty[PermissionADM]) { (acc, seq) =>
                   acc ++ seq
                 }
      /* Remove possible duplicate permissions */
      result: Set[PermissionADM] = PermissionUtilADM.removeDuplicatePermissions(combined)

    } yield result
    result
  }

  override def getPermissionsApByProjectIri(projectIRI: IRI): Task[AdministrativePermissionsForProjectGetResponseADM] =
    for {
      permissionsQueryResponseRows <-
        triplestore
          .query(Select(sparql.admin.txt.getAdministrativePermissionsForProject(projectIRI)))
          .map(_.results.bindings)

      permissionsWithProperties =
        permissionsQueryResponseRows
          .groupBy(_.rowMap("s"))
          .map { case (permissionIri: String, rows: Seq[VariableResultsRow]) =>
            (permissionIri, rows.map(row => (row.rowMap("p"), row.rowMap("o"))).toMap)
          }

      administrativePermissions =
        permissionsWithProperties.map { case (permissionIri: IRI, propsMap: Map[String, String]) =>
          /* parse permissions */
          val hasPermissions: Set[PermissionADM] =
            PermissionUtilADM.parsePermissionsWithType(
              propsMap.get(OntologyConstants.KnoraBase.HasPermissions),
              PermissionType.AP,
            )

          /* construct permission object */
          AdministrativePermissionADM(
            iri = permissionIri,
            forProject = propsMap.getOrElse(
              OntologyConstants.KnoraAdmin.ForProject,
              throw InconsistentRepositoryDataException(
                s"Administrative Permission $permissionIri has no project attached.",
              ),
            ),
            forGroup = propsMap.getOrElse(
              OntologyConstants.KnoraAdmin.ForGroup,
              throw InconsistentRepositoryDataException(
                s"Administrative Permission $permissionIri has no group attached.",
              ),
            ),
            hasPermissions = hasPermissions,
          )
        }.toSeq

      /* construct response object */
      response = permissionsmessages.AdministrativePermissionsForProjectGetResponseADM(administrativePermissions)

    } yield response

  /**
   * Gets a single administrative permission identified by it's IRI.
   *
   * @param administrativePermissionIri the IRI of the administrative permission.
   * @param requestingUser              the requesting user.
   * @return a single [[AdministrativePermissionADM]] object.
   */
  private def administrativePermissionForIriGetRequestADM(administrativePermissionIri: IRI, requestingUser: User) =
    for {
      administrativePermission <- permissionGetADM(administrativePermissionIri, requestingUser)
      result = administrativePermission match {
                 case ap: AdministrativePermissionADM => AdministrativePermissionGetResponseADM(ap)
                 case _ =>
                   throw BadRequestException(s"$administrativePermissionIri is not an administrative permission.")
               }
    } yield result

  /**
   * Gets a single administrative permission identified by project and group.
   *
   * @param projectIri     the project.
   * @param groupIri       the group.
   * @return an option containing an [[AdministrativePermissionADM]]
   */
  private def administrativePermissionForProjectGroupGetADM(projectIri: IRI, groupIri: IRI) =
    for {
      permissionQueryResponse <-
        triplestore.query(Select(sparql.admin.txt.getAdministrativePermissionForProjectAndGroup(projectIri, groupIri)))

      permissionQueryResponseRows = permissionQueryResponse.results.bindings

      permission =
        if (permissionQueryResponseRows.nonEmpty) {

          /* check if we only got one administrative permission back */
          val apCount: Int = permissionQueryResponseRows.groupBy(_.rowMap("s")).size
          if (apCount > 1)
            throw InconsistentRepositoryDataException(
              s"Only one administrative permission instance allowed for project: $projectIri and group: $groupIri combination, but found $apCount.",
            )

          /* get the iri of the retrieved permission */
          val returnedPermissionIri = permissionQueryResponse.getFirstRow.rowMap("s")

          val groupedPermissionsQueryResponse: Map[String, Seq[String]] =
            permissionQueryResponseRows.groupBy(_.rowMap("p")).map { case (predicate, rows) =>
              predicate -> rows.map(_.rowMap("o"))
            }
          val hasPermissions = PermissionUtilADM.parsePermissionsWithType(
            groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head),
            PermissionType.AP,
          )
          Some(
            permissionsmessages.AdministrativePermissionADM(
              iri = returnedPermissionIri,
              forProject = projectIri,
              forGroup = groupIri,
              hasPermissions = hasPermissions,
            ),
          )
        } else {
          None
        }
    } yield permission

  override def getPermissionsApByProjectAndGroupIri(
    projectIri: IRI,
    groupIri: IRI,
  ): Task[AdministrativePermissionGetResponseADM] =
    for {
      ap <- administrativePermissionForProjectGroupGetADM(projectIri, groupIri)
      result = ap match {
                 case Some(ap) => permissionsmessages.AdministrativePermissionGetResponseADM(ap)
                 case None =>
                   throw NotFoundException(
                     s"No Administrative Permission found for project: $projectIri, group: $groupIri combination",
                   )
               }
    } yield result

  private def validate(req: CreateAdministrativePermissionAPIRequestADM): Task[Unit] = ZIO.attempt {
    req.id.foreach(iri => PermissionIri.from(iri).fold(msg => throw BadRequestException(msg), _ => ()))

    ProjectIri.from(req.forProject).fold(msg => throw BadRequestException(msg), _ => ())

    if (req.hasPermissions.isEmpty) throw BadRequestException("Permissions needs to be supplied.")

    if (!OntologyConstants.KnoraAdmin.BuiltInGroups.contains(req.forGroup)) {
      GroupIri.from(req.forGroup).getOrElse(throw BadRequestException(s"Invalid group IRI ${req.forGroup}"))
    }

    PermissionsMessagesUtilADM.verifyHasPermissionsAP(req.hasPermissions)
  }.unit

  override def createAdministrativePermission(
    createRequest: CreateAdministrativePermissionAPIRequestADM,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[AdministrativePermissionCreateResponseADM] = {
    val createAdministrativePermissionTask =
      for {
        _ <- validate(createRequest)
        // does the permission already exist
        checkResult <- administrativePermissionForProjectGroupGetADM(createRequest.forProject, createRequest.forGroup)

        _ = checkResult match {
              case Some(ap: AdministrativePermissionADM) =>
                throw DuplicateValueException(
                  s"An administrative permission for project: '${createRequest.forProject}' and group: '${createRequest.forGroup}' combination already exists. " +
                    s"This permission currently has the scope '${PermissionUtilADM
                        .formatPermissionADMs(ap.hasPermissions, PermissionType.AP)}'. " +
                    s"Use its IRI ${ap.iri} to modify it, if necessary.",
                )
              case None => ()
            }

        // get project
        maybeProject <-
          messageRelay
            .ask[Option[Project]](
              ProjectGetADM(
                identifier = IriIdentifier
                  .fromString(createRequest.forProject)
                  .getOrElseWith(e => throw BadRequestException(e.head.getMessage)),
              ),
            )

        // if it doesnt exist then throw an error
        project: Project =
          maybeProject.getOrElse(
            throw NotFoundException(s"Project '${createRequest.forProject}' not found. Aborting request."),
          )

        // get group
        groupIri <-
          if (OntologyConstants.KnoraAdmin.BuiltInGroups.contains(createRequest.forGroup)) {
            ZIO.succeed(createRequest.forGroup)
          } else {
            for {
              maybeGroup <- messageRelay.ask[Option[Group]](GroupGetADM(createRequest.forGroup))

              // if it does not exist then throw an error
              group: Group =
                maybeGroup.getOrElse(
                  throw NotFoundException(s"Group '${createRequest.forGroup}' not found. Aborting request."),
                )
            } yield group.id
          }

        customPermissionIri: Option[SmartIri] = createRequest.id.map(iri => iri.toSmartIri)
        newPermissionIri <- iriService.checkOrCreateEntityIri(
                              customPermissionIri,
                              PermissionIri.makeNew(Shortcode.unsafeFrom(project.shortcode)).value,
                            )

        // Create the administrative permission.
        createAdministrativePermissionSparql = sparql.admin.txt.createNewAdministrativePermission(
                                                 AdminConstants.permissionsDataNamedGraph.value,
                                                 permissionClassIri =
                                                   OntologyConstants.KnoraAdmin.AdministrativePermission,
                                                 permissionIri = newPermissionIri,
                                                 projectIri = project.id,
                                                 groupIri = groupIri,
                                                 permissions = PermissionUtilADM.formatPermissionADMs(
                                                   createRequest.hasPermissions,
                                                   PermissionType.AP,
                                                 ),
                                               )
        _ <- triplestore.query(Update(createAdministrativePermissionSparql))

        // try to retrieve the newly created permission
        created <- administrativePermissionForIriGetRequestADM(newPermissionIri, requestingUser)
      } yield AdministrativePermissionCreateResponseADM(created.administrativePermission)

    IriLocker.runWithIriLock(apiRequestID, PERMISSIONS_GLOBAL_LOCK_IRI, createAdministrativePermissionTask)
  }

  ///////////////////////////////////////////////////////////////////////////
  // OBJECT ACCESS PERMISSIONS
  ///////////////////////////////////////////////////////////////////////////

  /**
   * Gets all permissions attached to the resource.
   *
   * @param resourceIri    the IRI of the resource.
   * @param requestingUser the requesting user.
   * @return a sequence of [[PermissionADM]]
   */
  private def objectAccessPermissionsForResourceGetADM(
    resourceIri: IRI,
    requestingUser: User,
  ): Task[Option[ObjectAccessPermissionADM]] =
    for {
      projectIri <- getProjectOfEntity(resourceIri)
      // Check user's permission for the operation
      _ = if (
            !requestingUser.isSystemAdmin
            && !requestingUser.permissions.isProjectAdmin(projectIri)
            && !requestingUser.isSystemUser
          ) {
            throw ForbiddenException("Object access permissions can only be queried by system and project admin.")
          }
      permissionQueryResponse <-
        triplestore.query(Select(sparql.admin.txt.getObjectAccessPermission(Some(resourceIri), None)))

      permissionQueryResponseRows = permissionQueryResponse.results.bindings

      permission =
        if (permissionQueryResponseRows.nonEmpty) {

          val groupedPermissionsQueryResponse: Map[String, Seq[String]] =
            permissionQueryResponseRows.groupBy(_.rowMap("p")).map { case (predicate, rows) =>
              predicate -> rows.map(_.rowMap("o"))
            }
          val hasPermissions: Set[PermissionADM] = PermissionUtilADM.parsePermissionsWithType(
            groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head),
            PermissionType.OAP,
          )
          Some(
            ObjectAccessPermissionADM(
              forResource = Some(resourceIri),
              forValue = None,
              hasPermissions = hasPermissions,
            ),
          )
        } else {
          None
        }
    } yield permission

  /**
   * Gets all permissions attached to the value.
   *
   * @param valueIri       the IRI of the value.
   * @param requestingUser the requesting user.
   * @return a sequence of [[PermissionADM]]
   */
  private def objectAccessPermissionsForValueGetADM(
    valueIri: IRI,
    requestingUser: User,
  ): Task[Option[ObjectAccessPermissionADM]] =
    for {
      projectIri <- getProjectOfEntity(valueIri)
      // Check user's permission for the operation
      _ <- ZIO.when(
             !requestingUser.isSystemAdmin
               && !requestingUser.permissions.isProjectAdmin(projectIri)
               && !requestingUser.isSystemUser,
           ) {
             ZIO.fail(ForbiddenException("Object access permissions can only be queried by system and project admin."))
           }
      permissionQueryResponse <-
        triplestore.query(
          Select(sparql.admin.txt.getObjectAccessPermission(resourceIri = None, valueIri = Some(valueIri))),
        )

      permissionQueryResponseRows = permissionQueryResponse.results.bindings

      permission =
        if (permissionQueryResponseRows.nonEmpty) {

          val groupedPermissionsQueryResponse: Map[String, Seq[String]] =
            permissionQueryResponseRows.groupBy(_.rowMap("p")).map { case (predicate, rows) =>
              predicate -> rows.map(_.rowMap("o"))
            }
          val hasPermissions: Set[PermissionADM] = PermissionUtilADM.parsePermissionsWithType(
            groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head),
            PermissionType.OAP,
          )
          Some(
            ObjectAccessPermissionADM(
              forResource = None,
              forValue = Some(valueIri),
              hasPermissions = hasPermissions,
            ),
          )
        } else {
          None
        }
    } yield permission

  ///////////////////////////////////////////////////////////////////////////
  // DEFAULT OBJECT ACCESS PERMISSIONS
  ///////////////////////////////////////////////////////////////////////////

  override def getPermissionsDaopByProjectIri(
    projectIri: ProjectIri,
  ): Task[DefaultObjectAccessPermissionsForProjectGetResponseADM] =
    for {
      permissionsQueryResponse <-
        triplestore.query(Select(sparql.admin.txt.getDefaultObjectAccessPermissionsForProject(projectIri.value)))

      /* extract response rows */
      permissionsQueryResponseRows = permissionsQueryResponse.results.bindings

      permissionsWithProperties =
        permissionsQueryResponseRows
          .groupBy(_.rowMap("s"))
          .map { case (permissionIri: String, rows: Seq[VariableResultsRow]) =>
            (permissionIri, rows.map(row => (row.rowMap("p"), row.rowMap("o"))).toMap)
          }

      permissions =
        permissionsWithProperties.map { case (permissionIri: IRI, propsMap: Map[String, String]) =>
          /* parse permissions */
          val hasPermissions: Set[PermissionADM] =
            PermissionUtilADM.parsePermissionsWithType(
              propsMap.get(
                OntologyConstants.KnoraBase.HasPermissions,
              ),
              PermissionType.OAP,
            )

          /* construct permission object */
          DefaultObjectAccessPermissionADM(
            iri = permissionIri,
            forProject = propsMap.getOrElse(
              OntologyConstants.KnoraAdmin.ForProject,
              throw InconsistentRepositoryDataException(
                s"Permission $permissionIri has no project.",
              ),
            ),
            forGroup = propsMap.get(OntologyConstants.KnoraAdmin.ForGroup),
            forResourceClass = propsMap.get(
              OntologyConstants.KnoraAdmin.ForResourceClass,
            ),
            forProperty = propsMap.get(
              OntologyConstants.KnoraAdmin.ForProperty,
            ),
            hasPermissions = hasPermissions,
          )
        }.toSeq

      /* construct response object */
      response = DefaultObjectAccessPermissionsForProjectGetResponseADM(permissions)

    } yield response

  /**
   * Gets a single default object access permission identified by its IRI.
   *
   * @param permissionIri  the IRI of the default object access permission.
   * @param requestingUser the [[User]] of the requesting user.
   * @return a single [[DefaultObjectAccessPermissionADM]] object.
   */
  private def defaultObjectAccessPermissionForIriGetRequestADM(
    permissionIri: IRI,
    requestingUser: User,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] =
    for {
      defaultObjectAccessPermission <- permissionGetADM(permissionIri, requestingUser)
      result = defaultObjectAccessPermission match {
                 case doap: DefaultObjectAccessPermissionADM =>
                   DefaultObjectAccessPermissionGetResponseADM(doap)
                 case _ => throw BadRequestException(s"$permissionIri is not a default object access permission.")
               }
    } yield result

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
  private def defaultObjectAccessPermissionGetADM(
    projectIri: IRI,
    groupIri: Option[IRI],
    resourceClassIri: Option[IRI],
    propertyIri: Option[IRI],
  ): Task[Option[DefaultObjectAccessPermissionADM]] =
    triplestore
      .query(
        Select(sparql.admin.txt.getDefaultObjectAccessPermission(projectIri, groupIri, resourceClassIri, propertyIri)),
      )
      .flatMap(toDefaultObjectAccessPermission(_, projectIri, groupIri, resourceClassIri, propertyIri))

  private def toDefaultObjectAccessPermission(
    result: SparqlSelectResult,
    projectIri: IRI,
    groupIri: Option[IRI],
    resourceClassIri: Option[IRI],
    propertyIri: Option[IRI],
  ): Task[Option[DefaultObjectAccessPermissionADM]] =
    ZIO.attempt {
      val rows = result.results.bindings
      if (rows.isEmpty) {
        None
      } else {
        /* check if we only got one default object access permission back */
        val doapCount: Int = rows.groupBy(_.rowMap("s")).size
        if (doapCount > 1)
          throw InconsistentRepositoryDataException(
            s"Only one default object permission instance allowed for project: $projectIri and combination of group: $groupIri, resourceClass: $resourceClassIri, property: $propertyIri combination, but found: $doapCount.",
          )

        /* get the iri of the retrieved permission */
        val permissionIri = result.getFirstRow.rowMap("s")

        val groupedPermissionsQueryResponse: Map[IRI, Seq[IRI]] =
          rows.groupBy(_.rowMap("p")).map { case (predicate, rows) =>
            predicate -> rows.map(_.rowMap("o"))
          }
        val hasPermissions: Set[PermissionADM] = PermissionUtilADM.parsePermissionsWithType(
          groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head),
          PermissionType.OAP,
        )
        val doap: DefaultObjectAccessPermissionADM = DefaultObjectAccessPermissionADM(
          iri = permissionIri,
          forProject = groupedPermissionsQueryResponse
            .getOrElse(
              OntologyConstants.KnoraAdmin.ForProject,
              throw InconsistentRepositoryDataException(s"Permission has no project."),
            )
            .head,
          forGroup = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraAdmin.ForGroup).map(_.head),
          forResourceClass =
            groupedPermissionsQueryResponse.get(OntologyConstants.KnoraAdmin.ForResourceClass).map(_.head),
          forProperty = groupedPermissionsQueryResponse.get(OntologyConstants.KnoraAdmin.ForProperty).map(_.head),
          hasPermissions = hasPermissions,
        )
        Some(doap)
      }
    }

  /**
   * Gets a single default object access permission identified by project and either group / resource class / property.
   * In the case of properties, an additional check is performed against the 'SystemProject', as some 'knora-base'
   * properties can carry default object access permissions. Note that default access permissions defined for a system
   * property inside the 'SystemProject' can be overridden by defining them for its own project.
   *
   * @param projectIri        The project's IRI in which the default object access permission is defined.
   * @param groupIri          The group's IRI for which the default object access permission is defined.
   * @param resourceClassIri  The resource's class IRI for which the default object access permission is defined.
   * @param propertyIri       The property's IRI for which the default object access permission is defined.
   * @return a [[DefaultObjectAccessPermissionGetResponseADM]]
   */
  private def defaultObjectAccessPermissionGetRequestADM(
    projectIri: IRI,
    groupIri: Option[IRI],
    resourceClassIri: Option[IRI],
    propertyIri: Option[IRI],
  ): Task[DefaultObjectAccessPermissionGetResponseADM] = {
    val projectIriInternal = projectIri.toSmartIri.toOntologySchema(InternalSchema).toString
    defaultObjectAccessPermissionGetADM(projectIriInternal, groupIri, resourceClassIri, propertyIri).flatMap {
      case Some(doap) => ZIO.attempt(DefaultObjectAccessPermissionGetResponseADM(doap))
      case None       =>
        /* if the query was for a property, then we need to additionally check if it is a system property */
        if (propertyIri.isDefined) {
          val systemProject = OntologyConstants.KnoraAdmin.SystemProject
          defaultObjectAccessPermissionGetADM(systemProject, groupIri, resourceClassIri, propertyIri).map {
            case Some(systemDoap) => DefaultObjectAccessPermissionGetResponseADM(systemDoap)
            case None =>
              throw NotFoundException(
                s"No Default Object Access Permission found for project: $projectIriInternal, group: $groupIri, resourceClassIri: $resourceClassIri, propertyIri: $propertyIri combination",
              )
          }
        } else {
          throw NotFoundException(
            s"No Default Object Access Permission found for project: $projectIriInternal, group: $groupIri, resourceClassIri: $resourceClassIri, propertyIri: $propertyIri combination",
          )
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
  private def defaultObjectAccessPermissionsForGroupsGetADM(
    projectIri: IRI,
    groups: Seq[IRI],
  ): Task[Set[PermissionADM]] = {

    /* Get default object access permissions for each group and combine them */
    val gpf: Seq[Task[Seq[PermissionADM]]] = for {
      groupIri <- groups

      groupPermissions: Task[Seq[PermissionADM]] = defaultObjectAccessPermissionGetADM(
                                                     projectIri = projectIri,
                                                     groupIri = Some(groupIri),
                                                     resourceClassIri = None,
                                                     propertyIri = None,
                                                   ).map {
                                                     case Some(doap: DefaultObjectAccessPermissionADM) =>
                                                       doap.hasPermissions.toSeq
                                                     case None => Seq.empty[PermissionADM]
                                                   }

    } yield groupPermissions

    /* combines all permissions for each group and removes duplicates  */
    ZioHelper.sequence(gpf).map(_.flatten).map(PermissionUtilADM.removeDuplicatePermissions(_))
  }

  /**
   * Convenience method returning a set with default object access permissions defined on a resource class.
   *
   * @param projectIri       the IRI of the project.
   * @param resourceClassIri the resource's class IRI
   * @return a set of [[PermissionADM]].
   */
  private def defaultObjectAccessPermissionsForResourceClassGetADM(
    projectIri: IRI,
    resourceClassIri: IRI,
  ): Task[Set[PermissionADM]] =
    for {
      defaultPermissionsOption <- defaultObjectAccessPermissionGetADM(
                                    projectIri = projectIri,
                                    groupIri = None,
                                    resourceClassIri = Some(resourceClassIri),
                                    propertyIri = None,
                                  )
      defaultPermissions: Set[PermissionADM] = defaultPermissionsOption match {
                                                 case Some(doap) => doap.hasPermissions
                                                 case None       => Set.empty[PermissionADM]
                                               }
    } yield defaultPermissions

  /**
   * Convenience method returning a set with default object access permissions defined on a resource class / property combination.
   *
   * @param projectIri       the IRI of the project.
   * @param resourceClassIri the resource's class IRI
   * @param propertyIri      the property's IRI.
   * @return a set of [[PermissionADM]].
   */
  private def defaultObjectAccessPermissionsForResourceClassPropertyGetADM(
    projectIri: IRI,
    resourceClassIri: IRI,
    propertyIri: IRI,
  ): Task[Set[PermissionADM]] =
    for {
      defaultPermissionsOption <- defaultObjectAccessPermissionGetADM(
                                    projectIri = projectIri,
                                    groupIri = None,
                                    resourceClassIri = Some(resourceClassIri),
                                    propertyIri = Some(propertyIri),
                                  )
      defaultPermissions: Set[PermissionADM] = defaultPermissionsOption match {
                                                 case Some(doap) => doap.hasPermissions
                                                 case None       => Set.empty[PermissionADM]
                                               }
    } yield defaultPermissions

  /**
   * Convenience method returning a set with default object access permissions defined on a property.
   *
   * @param projectIri  the IRI of the project.
   * @param propertyIri the property's IRI.
   * @return a set of [[PermissionADM]].
   */
  private def defaultObjectAccessPermissionsForPropertyGetADM(
    projectIri: IRI,
    propertyIri: IRI,
  ): Task[Set[PermissionADM]] =
    for {
      defaultPermissionsOption <- defaultObjectAccessPermissionGetADM(
                                    projectIri = projectIri,
                                    groupIri = None,
                                    resourceClassIri = None,
                                    propertyIri = Some(propertyIri),
                                  )
      defaultPermissions: Set[PermissionADM] = defaultPermissionsOption match {
                                                 case Some(doap) => doap.hasPermissions
                                                 case None       => Set.empty[PermissionADM]
                                               }
    } yield defaultPermissions

  /**
   * Returns a string containing default object permissions statements ready for usage during creation of a new resource.
   * The permissions include any default object access permissions defined for the resource class and on any groups the
   * user is member of.
   *
   * @param projectIri       the IRI of the project.
   * @param resourceClassIri the IRI of the resource class for which the default object access permissions are requested.
   * @param propertyIri      the IRI of the property for which the default object access permissions are requested.
   * @param targetUser       the user for which the permissions need to be calculated.
   * @return an optional string with object access permission statements
   */
  private def defaultObjectAccessPermissionsStringForEntityGetADM(
    projectIri: IRI,
    resourceClassIri: IRI,
    propertyIri: Option[IRI],
    entityType: IRI,
    targetUser: User,
  ) =
    for {
      /* Get the groups the user is member of. */
      userGroups <-
        ZIO.attempt(targetUser.permissions.groupsPerProject.get(projectIri).map(_.toSet).getOrElse(Set.empty[IRI]))

      /* Explicitly add 'SystemAdmin' and 'KnownUser' groups. */
      extendedUserGroups: List[IRI] =
        if (targetUser.permissions.isSystemAdmin) {
          OntologyConstants.KnoraAdmin.SystemAdmin :: OntologyConstants.KnoraAdmin.KnownUser :: userGroups.toList
        } else {
          OntologyConstants.KnoraAdmin.KnownUser :: userGroups.toList
        }

      /* List buffer holding default object access permissions tagged with the precedence level:
         0. ProjectAdmin > 1. ProjectEntity > 2. SystemEntity > 3. CustomGroups > 4. ProjectMember > 5. KnownUser
         Permissions are added following the precedence level from the highest to the lowest. As soon as one set
         of permissions is written into the buffer, any additionally found permissions are ignored. */
      permissionsListBuffer = ListBuffer.empty[(String, Set[PermissionADM])]

      ///////////////////////
      // PROJECT ADMIN
      ///////////////////////
      /* Get the default object access permissions for the knora-base:ProjectAdmin group */
      defaultPermissionsOnProjectAdminGroup <- defaultObjectAccessPermissionsForGroupsGetADM(
                                                 projectIri,
                                                 List(OntologyConstants.KnoraAdmin.ProjectAdmin),
                                               )
      _ = if (defaultPermissionsOnProjectAdminGroup.nonEmpty) {
            if (
              extendedUserGroups.contains(OntologyConstants.KnoraAdmin.ProjectAdmin) || extendedUserGroups.contains(
                OntologyConstants.KnoraAdmin.SystemAdmin,
              )
            ) {
              permissionsListBuffer += (("ProjectAdmin", defaultPermissionsOnProjectAdminGroup))
            }
          }

      ///////////////////////////////
      // RESOURCE CLASS / PROPERTY
      ///////////////////////////////
      /* project resource class / property combination */
      defaultPermissionsOnProjectResourceClassProperty <- {
        if (entityType == PropertyEntityType && permissionsListBuffer.isEmpty) {
          defaultObjectAccessPermissionsForResourceClassPropertyGetADM(
            projectIri = projectIri,
            resourceClassIri = resourceClassIri,
            propertyIri = propertyIri.getOrElse(throw BadRequestException("PropertyIri needs to be supplied.")),
          )
        } else {
          ZIO.attempt(Set.empty[PermissionADM])
        }
      }
      _ = if (defaultPermissionsOnProjectResourceClassProperty.nonEmpty) {
            permissionsListBuffer += (
              (
                "ProjectResourceClassProperty",
                defaultPermissionsOnProjectResourceClassProperty,
              )
            )
          }

      /* system resource class / property combination */
      defaultPermissionsOnSystemResourceClassProperty <- {
        if (entityType == PropertyEntityType && permissionsListBuffer.isEmpty) {
          val systemProject = OntologyConstants.KnoraAdmin.SystemProject
          defaultObjectAccessPermissionsForResourceClassPropertyGetADM(
            projectIri = systemProject,
            resourceClassIri = resourceClassIri,
            propertyIri = propertyIri.getOrElse(throw BadRequestException("PropertyIri needs to be supplied.")),
          )
        } else {
          ZIO.attempt(Set.empty[PermissionADM])
        }
      }
      _ = if (defaultPermissionsOnSystemResourceClassProperty.nonEmpty) {
            permissionsListBuffer += (("SystemResourceClassProperty", defaultPermissionsOnSystemResourceClassProperty))
          }

      ///////////////////////
      // RESOURCE CLASS
      ///////////////////////
      /* Get the default object access permissions defined on the resource class for the current project */
      defaultPermissionsOnProjectResourceClass <- {
        if (entityType == ResourceEntityType && permissionsListBuffer.isEmpty) {
          defaultObjectAccessPermissionsForResourceClassGetADM(
            projectIri = projectIri,
            resourceClassIri = resourceClassIri,
          )
        } else {
          ZIO.attempt(Set.empty[PermissionADM])
        }
      }
      _ = if (defaultPermissionsOnProjectResourceClass.nonEmpty) {
            permissionsListBuffer += (("ProjectResourceClass", defaultPermissionsOnProjectResourceClass))
          }

      /* Get the default object access permissions defined on the resource class inside the SystemProject */
      defaultPermissionsOnSystemResourceClass <- {
        if (entityType == ResourceEntityType && permissionsListBuffer.isEmpty) {
          val systemProject = OntologyConstants.KnoraAdmin.SystemProject
          defaultObjectAccessPermissionsForResourceClassGetADM(
            projectIri = systemProject,
            resourceClassIri = resourceClassIri,
          )
        } else {
          ZIO.attempt(Set.empty[PermissionADM])
        }
      }
      _ = if (defaultPermissionsOnSystemResourceClass.nonEmpty) {
            permissionsListBuffer += (("SystemResourceClass", defaultPermissionsOnSystemResourceClass))
          }

      ///////////////////////
      // PROPERTY
      ///////////////////////
      /* project property */
      defaultPermissionsOnProjectProperty <- {
        if (entityType == PropertyEntityType && permissionsListBuffer.isEmpty) {
          defaultObjectAccessPermissionsForPropertyGetADM(
            projectIri = projectIri,
            propertyIri = propertyIri.getOrElse(throw BadRequestException("PropertyIri needs to be supplied.")),
          )
        } else {
          ZIO.attempt(Set.empty[PermissionADM])
        }
      }
      _ = if (defaultPermissionsOnProjectProperty.nonEmpty) {
            permissionsListBuffer += (("ProjectProperty", defaultPermissionsOnProjectProperty))
          }

      /* system property */
      defaultPermissionsOnSystemProperty <- {
        if (entityType == PropertyEntityType && permissionsListBuffer.isEmpty) {
          val systemProject = OntologyConstants.KnoraAdmin.SystemProject
          defaultObjectAccessPermissionsForPropertyGetADM(
            projectIri = systemProject,
            propertyIri = propertyIri.getOrElse(throw BadRequestException("PropertyIri needs to be supplied.")),
          )
        } else {
          ZIO.attempt(Set.empty[PermissionADM])
        }
      }
      _ = if (defaultPermissionsOnSystemProperty.nonEmpty) {
            permissionsListBuffer += (("SystemProperty", defaultPermissionsOnSystemProperty))
          }

      ///////////////////////
      // CUSTOM GROUPS
      ///////////////////////
      /* Get the default object access permissions for custom groups (all groups other than the built-in groups) */
      defaultPermissionsOnCustomGroups <- {
        if (extendedUserGroups.nonEmpty && permissionsListBuffer.isEmpty) {
          val customGroups: List[IRI] = extendedUserGroups diff List(
            OntologyConstants.KnoraAdmin.KnownUser,
            OntologyConstants.KnoraAdmin.ProjectMember,
            OntologyConstants.KnoraAdmin.ProjectAdmin,
            OntologyConstants.KnoraAdmin.SystemAdmin,
          )
          if (customGroups.nonEmpty) {
            defaultObjectAccessPermissionsForGroupsGetADM(projectIri, customGroups)
          } else {
            ZIO.attempt(Set.empty[PermissionADM])
          }
        } else {
          // case where non SystemAdmin from outside of project
          ZIO.attempt(Set.empty[PermissionADM])
        }
      }
      _ = if (defaultPermissionsOnCustomGroups.nonEmpty) {
            permissionsListBuffer += (("CustomGroups", defaultPermissionsOnCustomGroups))
          }

      ///////////////////////
      // PROJECT MEMBER
      ///////////////////////
      /* Get the default object access permissions for the knora-base:ProjectMember group */
      defaultPermissionsOnProjectMemberGroup <- {
        if (permissionsListBuffer.isEmpty) {
          defaultObjectAccessPermissionsForGroupsGetADM(projectIri, List(OntologyConstants.KnoraAdmin.ProjectMember))
        } else {
          ZIO.attempt(Set.empty[PermissionADM])
        }
      }
      _ = if (defaultPermissionsOnProjectMemberGroup.nonEmpty) {
            if (
              extendedUserGroups.contains(OntologyConstants.KnoraAdmin.ProjectMember) || extendedUserGroups.contains(
                OntologyConstants.KnoraAdmin.SystemAdmin,
              )
            ) {
              permissionsListBuffer += (("ProjectMember", defaultPermissionsOnProjectMemberGroup))
            }
          }

      ///////////////////////
      // KNOWN USER
      ///////////////////////
      /* Get the default object access permissions for the knora-base:KnownUser group */
      defaultPermissionsOnKnownUserGroup <- {
        if (permissionsListBuffer.isEmpty) {
          defaultObjectAccessPermissionsForGroupsGetADM(projectIri, List(OntologyConstants.KnoraAdmin.KnownUser))
        } else {
          ZIO.attempt(Set.empty[PermissionADM])
        }
      }
      _ = if (defaultPermissionsOnKnownUserGroup.nonEmpty) {
            if (extendedUserGroups.contains(OntologyConstants.KnoraAdmin.KnownUser)) {
              permissionsListBuffer += (("KnownUser", defaultPermissionsOnKnownUserGroup))
            }
          }

      ///////////////////////
      // FALLBACK PERMISSION IF NONE COULD BE FOUND
      ///////////////////////
      /* Set 'CR knora-base:Creator' as the fallback permission */
      _ =
        if (permissionsListBuffer.isEmpty) {
          val defaultFallbackPermission = Set(
            PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          )
          permissionsListBuffer += (("Fallback", defaultFallbackPermission))
        } else {
          ZIO.succeed(Set.empty[PermissionADM])
        }

      /* Create permissions string */
      result = permissionsListBuffer.length match {
                 case 1 => PermissionUtilADM.formatPermissionADMs(permissionsListBuffer.head._2, PermissionType.OAP)
                 case _ =>
                   throw AssertionException(
                     "The permissions list buffer holding default object permissions should never be larger then 1.",
                   )
               }
      _ =
        logger.debug(
          s"defaultObjectAccessPermissionsStringForEntityGetADM (result) - project: $projectIri, precedence: ${permissionsListBuffer.head._1}, defaultObjectAccessPermissions: $result",
        )
    } yield permissionsmessages.DefaultObjectAccessPermissionsStringResponseADM(result)

  /**
   * Gets a single permission identified by its IRI.
   *
   * @param permissionIri  the IRI of the permission.
   * @param requestingUser the [[User]] of the requesting user.
   * @return a single [[DefaultObjectAccessPermissionADM]] object.
   */
  private def permissionByIriGetRequestADM(
    permissionIri: IRI,
    requestingUser: User,
  ): Task[PermissionGetResponseADM] =
    for {
      permission <- permissionGetADM(permissionIri, requestingUser)
      result = permission match {
                 case doap: DefaultObjectAccessPermissionADM =>
                   DefaultObjectAccessPermissionGetResponseADM(doap)
                 case ap: AdministrativePermissionADM =>
                   AdministrativePermissionGetResponseADM(ap)
                 case _ =>
                   throw BadRequestException(
                     s"$permissionIri is not a default object access or an administrative permission.",
                   )
               }
    } yield result

  private def validate(req: CreateDefaultObjectAccessPermissionAPIRequestADM) = ZIO.attempt {
    val sf: StringFormatter = StringFormatter.getInstanceForConstantOntologies

    req.id.foreach(iri => PermissionIri.from(iri).fold(msg => throw BadRequestException(msg), _ => ()))

    Iri
      .validateAndEscapeProjectIri(req.forProject)
      .getOrElse(throw BadRequestException(s"Invalid project IRI ${req.forProject}"))

    (req.forGroup, req.forResourceClass, req.forProperty) match {
      case (None, None, None) =>
        throw BadRequestException(
          "Either a group, a resource class, a property, or a combination of resource class and property must be given.",
        )
      case (Some(_), Some(_), _) =>
        throw BadRequestException("Not allowed to supply groupIri and resourceClassIri together.")
      case (Some(_), _, Some(_)) =>
        throw BadRequestException("Not allowed to supply groupIri and propertyIri together.")
      case (Some(groupIri), None, None) =>
        GroupIri.from(groupIri).getOrElse(throw BadRequestException(s"Invalid group IRI $groupIri"))
      case (None, resourceClassIriMaybe, propertyIriMaybe) =>
        resourceClassIriMaybe.foreach { resourceClassIri =>
          if (!sf.toSmartIri(resourceClassIri).isKnoraEntityIri) {
            throw BadRequestException(s"Invalid resource class IRI: $resourceClassIri")
          }
        }
        propertyIriMaybe.foreach { propertyIri =>
          if (!sf.toSmartIri(propertyIri).isKnoraEntityIri) {
            throw BadRequestException(s"Invalid property IRI: $propertyIri")
          }
        }
      case _ => ()
    }

    if (req.hasPermissions.isEmpty) throw BadRequestException("Permissions needs to be supplied.")
  }

  override def createDefaultObjectAccessPermission(
    createRequest: CreateDefaultObjectAccessPermissionAPIRequestADM,
    user: User,
    apiRequestID: UUID,
  ): Task[DefaultObjectAccessPermissionCreateResponseADM] = {

    /**
     * The actual change project task run with an IRI lock.
     */
    val createPermissionTask =
      for {
        _ <- validate(createRequest)
        projectIri <- ZIO
                        .fromEither(ProjectIri.from(createRequest.forProject))
                        .mapError(BadRequestException.apply)
        project <- knoraProjectService
                     .findById(projectIri)
                     .someOrFail(NotFoundException(s"Project ${projectIri.value} not found"))
        _ <- auth.ensureSystemAdminSystemUserOrProjectAdmin(user, project)
        checkResult <- defaultObjectAccessPermissionGetADM(
                         createRequest.forProject,
                         createRequest.forGroup,
                         createRequest.forResourceClass,
                         createRequest.forProperty,
                       )

        _ = checkResult match {
              case Some(doap: DefaultObjectAccessPermissionADM) =>
                val errorMessage = if (doap.forGroup.nonEmpty) {
                  s"and group: '${doap.forGroup.get}' "
                } else {
                  val resourceClassExists = if (doap.forResourceClass.nonEmpty) {
                    s"and resourceClass: '${doap.forResourceClass.get}' "
                  } else ""
                  val propExists = if (doap.forProperty.nonEmpty) {
                    s"and property: '${doap.forProperty.get}' "
                  } else ""
                  resourceClassExists + propExists
                }
                throw DuplicateValueException(
                  s"A default object access permission for project: '${createRequest.forProject}' " +
                    errorMessage + "combination already exists. " +
                    s"This permission currently has the scope '${PermissionUtilADM
                        .formatPermissionADMs(doap.hasPermissions, PermissionType.OAP)}'. " +
                    s"Use its IRI ${doap.iri} to modify it, if necessary.",
                )
              case None => ()
            }

        customPermissionIri: Option[SmartIri] = createRequest.id.map(iri => iri.toSmartIri)
        newPermissionIri <- iriService.checkOrCreateEntityIri(
                              customPermissionIri,
                              PermissionIri.makeNew(project.shortcode).value,
                            )
        // verify group, if any given.
        // Is a group given that is not a built-in one?
        maybeGroupIri <-
          if (createRequest.forGroup.exists(!OntologyConstants.KnoraAdmin.BuiltInGroups.contains(_))) {
            // Yes. Check if it is a known group.
            for {
              maybeGroup <-
                messageRelay
                  .ask[Option[Group]](
                    GroupGetADM(
                      groupIri = createRequest.forGroup.get,
                    ),
                  )

              group: Group =
                maybeGroup.getOrElse(
                  throw NotFoundException(s"Group '${createRequest.forGroup}' not found. Aborting request."),
                )
            } yield Some(group.id)
          } else {
            // No, return given group as it is. That means:
            // If given group is a built-in one, no verification is necessary, return it as it is.
            // In case no group IRI is given, returns None.
            ZIO.succeed(createRequest.forGroup)
          }

        // Create the default object access permission.
        permissions <- verifyHasPermissionsDOAP(createRequest.hasPermissions)
        createNewDefaultObjectAccessPermissionSparqlString = sparql.admin.txt.createNewDefaultObjectAccessPermission(
                                                               AdminConstants.permissionsDataNamedGraph.value,
                                                               permissionIri = newPermissionIri,
                                                               permissionClassIri =
                                                                 OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission,
                                                               projectIri = project.id.value,
                                                               maybeGroupIri = maybeGroupIri,
                                                               maybeResourceClassIri = createRequest.forResourceClass,
                                                               maybePropertyIri = createRequest.forProperty,
                                                               permissions = PermissionUtilADM.formatPermissionADMs(
                                                                 permissions,
                                                                 PermissionType.OAP,
                                                               ),
                                                             )
        _ <- triplestore.query(Update(createNewDefaultObjectAccessPermissionSparqlString))

        // try to retrieve the newly created permission
        maybePermission <- defaultObjectAccessPermissionGetADM(
                             createRequest.forProject,
                             createRequest.forGroup,
                             createRequest.forResourceClass,
                             createRequest.forProperty,
                           )

        newDefaultObjectAcessPermission: DefaultObjectAccessPermissionADM =
          maybePermission.getOrElse(
            throw BadRequestException(
              "Requested default object access permission could not be created, report this as a possible bug.",
            ),
          )

      } yield DefaultObjectAccessPermissionCreateResponseADM(defaultObjectAccessPermission =
        newDefaultObjectAcessPermission,
      )

    IriLocker.runWithIriLock(apiRequestID, PERMISSIONS_GLOBAL_LOCK_IRI, createPermissionTask)
  }

  override def verifyHasPermissionsDOAP(hasPermissions: Set[PermissionADM]): Task[Set[PermissionADM]] = ZIO.attempt {
    validateDOAPHasPermissions(hasPermissions)
    hasPermissions.map { permission =>
      val code: Int = permission.permissionCode match {
        case None       => PermissionTypeAndCodes(permission.name)
        case Some(code) => code
      }
      val name = permission.name.isEmpty match {
        case true =>
          val nameCodeSet: Option[(String, Int)] = PermissionTypeAndCodes.find { case (_, code) =>
            code == permission.permissionCode.get
          }
          nameCodeSet.get._1
        case false => permission.name
      }
      PermissionADM(
        name = name,
        additionalInformation = permission.additionalInformation,
        permissionCode = Some(code),
      )
    }
  }

  /**
   * Validates the parameters of the `hasPermissions` collections of a DOAP.
   *
   * @param hasPermissions       Set of the permissions.
   */
  private def validateDOAPHasPermissions(hasPermissions: Set[PermissionADM]): Unit =
    hasPermissions.foreach { permission =>
      if (permission.additionalInformation.isEmpty) {
        throw BadRequestException(s"additionalInformation of a default object access permission type cannot be empty.")
      }
      if (permission.name.nonEmpty && !EntityPermissionAbbreviations.contains(permission.name))
        throw BadRequestException(
          s"Invalid value for name parameter of hasPermissions: ${permission.name}, it should be one of " +
            s"${EntityPermissionAbbreviations.toString}",
        )
      if (permission.permissionCode.nonEmpty) {
        val code = permission.permissionCode.get
        if (!PermissionTypeAndCodes.values.toSet.contains(code)) {
          throw BadRequestException(
            s"Invalid value for permissionCode parameter of hasPermissions: $code, it should be one of " +
              s"${PermissionTypeAndCodes.values.toString}",
          )
        }
      }
      if (permission.permissionCode.isEmpty && permission.name.isEmpty) {
        throw BadRequestException(
          s"One of permission code or permission name must be provided for a default object access permission.",
        )
      }
      if (permission.permissionCode.nonEmpty && permission.name.nonEmpty) {
        val code = permission.permissionCode.get
        if (PermissionTypeAndCodes(permission.name) != code) {
          throw BadRequestException(
            s"Given permission code $code and permission name ${permission.name} are not consistent.",
          )
        }
      }
    }

  /**
   * Gets all permissions defined inside a project.
   *
   * @param projectIri           the IRI of the project.
   * @return a list of of [[PermissionInfoADM]] objects.
   */
  override def getPermissionsByProjectIri(projectIri: ProjectIri): Task[PermissionsForProjectGetResponseADM] =
    for {
      permissionsQueryResponseStatements <-
        triplestore
          .query(Construct(sparql.admin.txt.getProjectPermissions(projectIri.value)))
          .map(_.statements)
      _ <- ZIO.when(permissionsQueryResponseStatements.isEmpty) {
             ZIO.fail(NotFoundException(s"No permission could be found for ${projectIri.value}."))
           }
      permissionsInfo =
        permissionsQueryResponseStatements.map { statement =>
          val permissionIri       = statement._1
          val (_, permissionType) = statement._2.filter(_._1 == OntologyConstants.Rdf.Type).head
          PermissionInfoADM(iri = permissionIri, permissionType = permissionType)
        }.toSet
    } yield PermissionsForProjectGetResponseADM(permissionsInfo)

  override def updatePermissionsGroup(
    permissionIri: PermissionIri,
    groupIri: GroupIri,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM] = {
    /* verify that the permission group is updated */
    val verifyPermissionGroupUpdate =
      for {
        updatedPermission <- permissionGetADM(permissionIri.value, requestingUser)
        _ = updatedPermission match {
              case ap: AdministrativePermissionADM =>
                if (ap.forGroup != groupIri.value)
                  throw UpdateNotPerformedException(
                    s"The group of permission ${permissionIri.value} was not updated. Please report this as a bug.",
                  )
              case doap: DefaultObjectAccessPermissionADM =>
                if (doap.forGroup.get != groupIri.value) {
                  throw UpdateNotPerformedException(
                    s"The group of permission ${permissionIri.value} was not updated. Please report this as a bug.",
                  )
                } else {
                  if (doap.forProperty.isDefined || doap.forResourceClass.isDefined)
                    throw UpdateNotPerformedException(
                      s"The ${permissionIri.value} is not correctly updated. Please report this as a bug.",
                    )
                }
            }
      } yield updatedPermission

    /**
     * The actual task run with an IRI lock.
     */
    val permissionGroupChangeTask: Task[PermissionGetResponseADM] =
      for {
        // get permission
        permission <- permissionGetADM(permissionIri.value, requestingUser)
        response <- permission match {
                      // Is permission an administrative permission?
                      case ap: AdministrativePermissionADM =>
                        // Yes. Update the group
                        for {
                          _                 <- updatePermission(permissionIri = ap.iri, maybeGroup = Some(groupIri.value))
                          updatedPermission <- verifyPermissionGroupUpdate
                        } yield AdministrativePermissionGetResponseADM(
                          updatedPermission.asInstanceOf[AdministrativePermissionADM],
                        )
                      case doap: DefaultObjectAccessPermissionADM =>
                        // No. It is a default object access permission
                        for {
                          // if a doap permission has a group defined, it cannot have either resourceClass or property
                          _                 <- updatePermission(permissionIri = doap.iri, maybeGroup = Some(groupIri.value))
                          updatedPermission <- verifyPermissionGroupUpdate
                        } yield DefaultObjectAccessPermissionGetResponseADM(
                          updatedPermission.asInstanceOf[DefaultObjectAccessPermissionADM],
                        )
                    }
      } yield response

    IriLocker.runWithIriLock(apiRequestID, permissionIri.value, permissionGroupChangeTask)
  }

  /**
   * Update a permission's set of hasPermissions.
   *
   * @param permissionIri               the IRI of the permission.
   * @param newHasPermissions           the request to change hasPermissions.
   * @param requestingUser              the [[User]] of the requesting user.
   * @param apiRequestID                the API request ID.
   * @return [[PermissionGetResponseADM]].
   *         fails with an UpdateNotPerformedException if something has gone wrong.
   */
  override def updatePermissionHasPermissions(
    permissionIri: PermissionIri,
    newHasPermissions: NonEmptyChunk[PermissionADM],
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM] = {
    val permissionIriInternal = permissionIri.value.toSmartIri.toOntologySchema(InternalSchema).toString
    /*Verify that hasPermissions is updated successfully*/
    def verifyUpdateOfHasPermissions(expectedPermissions: Set[PermissionADM]): Task[PermissionItemADM] =
      for {
        updatedPermission <- permissionGetADM(permissionIriInternal, requestingUser)

        /*Verify that update was successful*/
        _ = updatedPermission match {
              case ap: AdministrativePermissionADM =>
                if (!ap.hasPermissions.equals(expectedPermissions))
                  throw UpdateNotPerformedException(
                    s"The hasPermissions set of permission $permissionIriInternal was not updated. Please report this as a bug.",
                  )
              case doap: DefaultObjectAccessPermissionADM =>
                if (!doap.hasPermissions.equals(expectedPermissions)) {
                  throw UpdateNotPerformedException(
                    s"The hasPermissions set of permission $permissionIriInternal was not updated. Please report this as a bug.",
                  )
                }
              case _ => None
            }
      } yield updatedPermission

    /**
     * The actual task run with an IRI lock.
     */
    val permissionHasPermissionsChangeTask =
      for {
        // get permission
        permission <- permissionGetADM(permissionIriInternal, requestingUser)
        response <- permission match {
                      // Is permission an administrative permission?
                      case ap: AdministrativePermissionADM =>
                        // Yes.
                        val verifiedPermissions =
                          PermissionsMessagesUtilADM.verifyHasPermissionsAP(newHasPermissions.toSet)
                        for {
                          formattedPermissions <-
                            ZIO.attempt(
                              PermissionUtilADM.formatPermissionADMs(verifiedPermissions, PermissionType.AP),
                            )
                          _ <-
                            updatePermission(permissionIri = ap.iri, maybeHasPermissions = Some(formattedPermissions))
                          updatedPermission <- verifyUpdateOfHasPermissions(verifiedPermissions)
                        } yield AdministrativePermissionGetResponseADM(
                          updatedPermission.asInstanceOf[AdministrativePermissionADM],
                        )
                      case doap: DefaultObjectAccessPermissionADM =>
                        // No. It is a default object access permission.
                        for {
                          verifiedPermissions <- verifyHasPermissionsDOAP(newHasPermissions.toSet)
                          formattedPermissions <-
                            ZIO.attempt(
                              PermissionUtilADM.formatPermissionADMs(verifiedPermissions, PermissionType.OAP),
                            )
                          _ <-
                            updatePermission(permissionIri = doap.iri, maybeHasPermissions = Some(formattedPermissions))
                          updatedPermission <- verifyUpdateOfHasPermissions(verifiedPermissions)
                        } yield DefaultObjectAccessPermissionGetResponseADM(
                          updatedPermission.asInstanceOf[DefaultObjectAccessPermissionADM],
                        )
                      case _ =>
                        throw UpdateNotPerformedException(
                          s"Permission ${permissionIri.value} was not updated. Please report this as a bug.",
                        )
                    }
      } yield response

    IriLocker.runWithIriLock(apiRequestID, permissionIri.value, permissionHasPermissionsChangeTask)
  }

  /**
   * Update a doap permission's resource class.
   *
   * @param permissionIri                 the IRI of the permission.
   * @param changePermissionResourceClass the request to change hasPermissions.
   * @param requestingUser                the [[User]] of the requesting user.
   * @param apiRequestID                  the API request ID.
   * @return [[PermissionGetResponseADM]].
   *         fails with an UpdateNotPerformedException if something has gone wrong.
   */
  override def updatePermissionResourceClass(
    permissionIri: PermissionIri,
    changePermissionResourceClass: ChangePermissionResourceClassApiRequestADM,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM] = {
    val permissionIriInternal = permissionIri.value.toSmartIri.toOntologySchema(InternalSchema).toString
    /*Verify that resource class of doap is updated successfully*/
    val verifyUpdateOfResourceClass =
      for {
        updatedPermission <- permissionGetADM(permissionIriInternal, requestingUser)

        /*Verify that update was successful*/
        _ <- ZIO.attempt(updatedPermission match {
               case doap: DefaultObjectAccessPermissionADM =>
                 if (doap.forResourceClass.get != changePermissionResourceClass.forResourceClass)
                   throw UpdateNotPerformedException(
                     s"The resource class of ${doap.iri} was not updated. Please report this as a bug.",
                   )

                 if (doap.forGroup.isDefined)
                   throw UpdateNotPerformedException(
                     s"The $permissionIriInternal is not correctly updated. Please report this as a bug.",
                   )

               case _ =>
                 throw UpdateNotPerformedException(
                   s"Incorrect permission type returned for $permissionIriInternal. Please report this as a bug.",
                 )
             })
      } yield updatedPermission

    /**
     * The actual task run with an IRI lock.
     */
    val permissionResourceClassChangeTask: Task[PermissionGetResponseADM] =
      for {
        // get permission
        permission <- permissionGetADM(permissionIri.value, requestingUser)
        response <- permission match {
                      // Is permission an administrative permission?
                      case ap: AdministrativePermissionADM =>
                        // Yes.
                        ZIO.fail(
                          ForbiddenException(
                            s"Permission ${ap.iri} is of type administrative permission. " +
                              s"Only a default object access permission defined for a resource class can be updated.",
                          ),
                        )
                      case doap: DefaultObjectAccessPermissionADM =>
                        // No. It is a default object access permission.
                        for {
                          _ <- updatePermission(
                                 permissionIri = doap.iri,
                                 maybeResourceClass = Some(changePermissionResourceClass.forResourceClass),
                               )
                          updatedPermission <- verifyUpdateOfResourceClass
                        } yield DefaultObjectAccessPermissionGetResponseADM(
                          updatedPermission.asInstanceOf[DefaultObjectAccessPermissionADM],
                        )
                      case _ =>
                        ZIO.fail(
                          UpdateNotPerformedException(
                            s"Permission ${permissionIri.value} was not updated. Please report this as a bug.",
                          ),
                        )
                    }
      } yield response

    IriLocker.runWithIriLock(apiRequestID, permissionIri.value, permissionResourceClassChangeTask)
  }

  override def updatePermissionProperty(
    permissionIri: PermissionIri,
    changePermissionPropertyRequest: ChangePermissionPropertyApiRequestADM,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM] = {
    val permissionIriInternal = permissionIri.value.toSmartIri.toOntologySchema(InternalSchema).toString
    /*Verify that property of doap is updated successfully*/
    def verifyUpdateOfProperty: Task[PermissionItemADM] =
      for {
        updatedPermission <- permissionGetADM(permissionIriInternal, requestingUser)

        /*Verify that update was successful*/
        _ <- ZIO.attempt(updatedPermission match {
               case doap: DefaultObjectAccessPermissionADM =>
                 if (doap.forProperty.get != changePermissionPropertyRequest.forProperty)
                   throw UpdateNotPerformedException(
                     s"The property of ${doap.iri} was not updated. Please report this as a bug.",
                   )

                 if (doap.forGroup.isDefined)
                   throw UpdateNotPerformedException(
                     s"The $permissionIriInternal is not correctly updated. Please report this as a bug.",
                   )

               case _ =>
                 throw UpdateNotPerformedException(
                   s"Incorrect permission type returned for $permissionIriInternal. Please report this as a bug.",
                 )
             })
      } yield updatedPermission

    /**
     * The actual task run with an IRI lock.
     */
    val permissionPropertyChangeTask =
      for {
        // get permission
        permission <- permissionGetADM(permissionIri.value, requestingUser)
        response <- permission match {
                      // Is permission an administrative permission?
                      case ap: AdministrativePermissionADM =>
                        // Yes.
                        ZIO.fail(
                          ForbiddenException(
                            s"Permission ${ap.iri} is of type administrative permission. " +
                              s"Only a default object access permission defined for a property can be updated.",
                          ),
                        )
                      case doap: DefaultObjectAccessPermissionADM =>
                        // No. It is a default object access permission.
                        for {
                          _ <- updatePermission(
                                 permissionIri = doap.iri,
                                 maybeProperty = Some(changePermissionPropertyRequest.forProperty),
                               )
                          updatedPermission <- verifyUpdateOfProperty
                        } yield DefaultObjectAccessPermissionGetResponseADM(
                          updatedPermission.asInstanceOf[DefaultObjectAccessPermissionADM],
                        )
                      case _ =>
                        ZIO.fail(
                          UpdateNotPerformedException(
                            s"Permission $permissionIri was not updated. Please report this as a bug.",
                          ),
                        )
                    }
      } yield response

    IriLocker.runWithIriLock(apiRequestID, permissionIri.value, permissionPropertyChangeTask)
  }

  override def deletePermission(
    permissionIri: PermissionIri,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionDeleteResponseADM] = {
    val permissionIriInternal = permissionIri.value.toSmartIri.toOntologySchema(InternalSchema).toString
    def permissionDeleteTask(): Task[PermissionDeleteResponseADM] =
      for {
        // check that there is a permission with a given IRI
        _ <- permissionGetADM(permissionIriInternal, requestingUser)
        // Is permission in use?
        _ <-
          ZIO
            .fail(UpdateNotPerformedException(s"Permission $permissionIriInternal is in use and cannot be deleted."))
            .whenZIO(triplestore.query(Ask(sparql.admin.txt.isEntityUsed(permissionIri.value))))
        _          <- deletePermission(permissionIriInternal)
        sf          = StringFormatter.getGeneralInstance
        iriExternal = sf.toSmartIri(permissionIri.value).toOntologySchema(ApiV2Complex).toString

      } yield PermissionDeleteResponseADM(iriExternal, deleted = true)

    IriLocker.runWithIriLock(apiRequestID, permissionIri.value, permissionDeleteTask())
  }

  /**
   * *************
   */
  /*Helper Methods*/
  /**
   * ************
   */
  /**
   * Checks that requesting user has right for the permission operation
   *
   * @param requestingUser the [[User]] of the requesting user.
   * @param projectIri      the IRI of the project the permission is attached to.
   * @param permissionIri the IRI of the permission.
   *
   *                      throws ForbiddenException if the user is not a project or system admin
   */
  private def verifyUsersRightForOperation(requestingUser: User, projectIri: IRI, permissionIri: IRI): Unit =
    if (!requestingUser.isSystemAdmin && !requestingUser.permissions.isProjectAdmin(projectIri)) {

      throw ForbiddenException(
        s"Permission $permissionIri can only be queried/updated/deleted by system or project admin.",
      )
    }

  /**
   * Get a permission.
   *
   * @param permissionIri  the IRI of the permission.
   * @param requestingUser the [[User]] of the requesting user.
   * @return [[PermissionItemADM]].
   */
  private def permissionGetADM(permissionIri: IRI, requestingUser: User): Task[PermissionItemADM] =
    for {
      // SPARQL query statement to get permission by IRI.
      permissionQueryResponse <- triplestore.query(Select(sparql.admin.txt.getPermissionByIRI(permissionIri)))

      /* extract response rows */
      permissionQueryResponseRows = permissionQueryResponse.results.bindings
      groupedPermissionsQueryResponse = permissionQueryResponseRows.groupBy(_.rowMap("p")).map {
                                          case (predicate, rows) => predicate -> rows.map(_.rowMap("o"))
                                        }

      /* check if we have found something */
      _ = if (groupedPermissionsQueryResponse.isEmpty)
            throw NotFoundException(s"Permission with given IRI: $permissionIri not found.")

      projectIri = groupedPermissionsQueryResponse
                     .getOrElse(
                       OntologyConstants.KnoraAdmin.ForProject,
                       throw InconsistentRepositoryDataException(s"Permission $permissionIri has no project attached"),
                     )
                     .head

      // Before returning the permission check that the requesting user has permission to see it
      _ = verifyUsersRightForOperation(
            requestingUser = requestingUser,
            projectIri = projectIri,
            permissionIri = permissionIri,
          )

      permissionType = groupedPermissionsQueryResponse
                         .getOrElse(
                           OntologyConstants.Rdf.Type,
                           throw InconsistentRepositoryDataException(s"RDF type is not returned."),
                         )
                         .headOption
      permission = permissionType match {
                     case Some(OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission) =>
                       val hasPermissions = PermissionUtilADM.parsePermissionsWithType(
                         groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head),
                         PermissionType.OAP,
                       )
                       val forGroup =
                         groupedPermissionsQueryResponse.get(OntologyConstants.KnoraAdmin.ForGroup).map(_.head)
                       val forResourceClass =
                         groupedPermissionsQueryResponse.get(OntologyConstants.KnoraAdmin.ForResourceClass).map(_.head)
                       val forProperty =
                         groupedPermissionsQueryResponse.get(OntologyConstants.KnoraAdmin.ForProperty).map(_.head)
                       DefaultObjectAccessPermissionADM(
                         iri = permissionIri,
                         forProject = projectIri,
                         forGroup = forGroup,
                         forResourceClass = forResourceClass,
                         forProperty = forProperty,
                         hasPermissions = hasPermissions,
                       )
                     case Some(OntologyConstants.KnoraAdmin.AdministrativePermission) =>
                       val forGroup = groupedPermissionsQueryResponse
                         .getOrElse(
                           OntologyConstants.KnoraAdmin.ForGroup,
                           throw InconsistentRepositoryDataException(s"Permission $permissionIri has no group attached"),
                         )
                         .head
                       val hasPermissions = PermissionUtilADM.parsePermissionsWithType(
                         groupedPermissionsQueryResponse.get(OntologyConstants.KnoraBase.HasPermissions).map(_.head),
                         PermissionType.AP,
                       )

                       AdministrativePermissionADM(
                         iri = permissionIri,
                         forProject = projectIri,
                         forGroup = forGroup,
                         hasPermissions = hasPermissions,
                       )
                     case _ =>
                       throw BadRequestException(s"Invalid permission type returned, please report this as a bug.")
                   }
    } yield permission

  /**
   * Update an existing permission with a given parameter.
   *
   * @param permissionIri       the IRI of the permission.
   * @param maybeGroup          the IRI of the new group.
   * @param maybeHasPermissions the new set of permissions formatted according to permission type as string.
   * @param maybeResourceClass  the new resource class IRI of a doap permission.
   * @param maybeProperty       the new property IRI of a doap permission.
   */
  private def updatePermission(
    permissionIri: IRI,
    maybeGroup: Option[IRI] = None,
    maybeHasPermissions: Option[String] = None,
    maybeResourceClass: Option[IRI] = None,
    maybeProperty: Option[IRI] = None,
  ): Task[Unit] = {
    // Generate SPARQL for changing the permission.
    val sparqlChangePermission = sparql.admin.txt.updatePermission(
      AdminConstants.permissionsDataNamedGraph.value,
      permissionIri = permissionIri,
      maybeGroup = maybeGroup,
      maybeHasPermissions = maybeHasPermissions,
      maybeResourceClass = maybeResourceClass,
      maybeProperty = maybeProperty,
    )
    triplestore.query(Update(sparqlChangePermission))
  }

  /**
   * Delete an existing permission with a given IRI.
   *
   * @param permissionIri       the IRI of the permission.
   */
  def deletePermission(permissionIri: IRI): Task[Unit] =
    for {
      _ <- triplestore.query(
             Update(sparql.admin.txt.deletePermission(AdminConstants.permissionsDataNamedGraph.value, permissionIri)),
           )
      _ <- triplestore
             .query(Ask(sparql.admin.txt.checkIriExists(permissionIri)))
      permissionStillExists <- triplestore.query(Ask(sparql.admin.txt.checkIriExists(permissionIri)))

      _ = if (permissionStillExists) {
            throw UpdateNotPerformedException(
              s"Permission <$permissionIri> was not erased. Please report this as a possible bug.",
            )
          }
    } yield ()

  private def getProjectOfEntity(entityIri: IRI): Task[IRI] =
    for {
      response <- triplestore.query(Select(sparql.admin.txt.getProjectOfEntity(entityIri)))
      rows      = response.results.bindings
      projectIri =
        if (rows.isEmpty) {
          throw BadRequestException(
            s"<$entityIri> is not attached to a project, please verify that IRI is of a knora entity.",
          )
        } else {
          val projectOption = rows.head.rowMap.get("projectIri")
          projectOption.getOrElse(throw BadRequestException(s"No Project found for the given <$entityIri>"))
        }

    } yield projectIri

}

object PermissionsResponderADMLive {
  val layer: URLayer[
    AppConfig & AuthorizationRestService & IriService & KnoraProjectService & MessageRelay & StringFormatter & TriplestoreService,
    PermissionsResponderADMLive,
  ] = ZLayer.fromZIO {
    for {
      au      <- ZIO.service[AuthorizationRestService]
      ac      <- ZIO.service[AppConfig]
      is      <- ZIO.service[IriService]
      kpr     <- ZIO.service[KnoraProjectService]
      mr      <- ZIO.service[MessageRelay]
      ts      <- ZIO.service[TriplestoreService]
      sf      <- ZIO.service[StringFormatter]
      handler <- mr.subscribe(PermissionsResponderADMLive(ac, is, mr, ts, kpr, au, sf))
    } yield handler
  }
}
