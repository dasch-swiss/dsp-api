/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.*

import java.util.UUID

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType.DOAP
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.AdministrativePermission
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionPart
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionPart.Simple
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.DefaultObjectAccessPermissionPart
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat.Group
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat.Property
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat.ResourceClass
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermission.ForWhat.ResourceClassAndProperty
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative.ProjectAdminAll
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative.ProjectResourceCreateAll
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.AdministrativePermissionService
import org.knora.webapi.slice.admin.domain.service.DefaultObjectAccessPermissionService
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo.*
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.repo.service.DefaultObjectAccessPermissionRepoLive
import org.knora.webapi.slice.api.admin.PermissionEndpointsRequests.ChangeDoapRequest
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.service.IriConverter

final class PermissionsResponder(
  groupService: KnoraGroupService,
  iriService: IriService,
  knoraProjectService: KnoraProjectService,
  administrativePermissionService: AdministrativePermissionService,
  iriConverter: IriConverter,
  doapService: DefaultObjectAccessPermissionService,
)(implicit val stringFormatter: StringFormatter) {

  private val PERMISSIONS_GLOBAL_LOCK_IRI = "http://rdfh.ch/permissions"

  private enum EntityType {
    case Resource
    case Property
  }

  def getPermissionsApByProjectIri(projectIri: ProjectIri): Task[AdministrativePermissionsForProjectGetResponseADM] =
    administrativePermissionService
      .findByProject(projectIri)
      .map(_.map(AdministrativePermissionADM.from))
      .map(AdministrativePermissionsForProjectGetResponseADM(_))

  def createAdministrativePermission(
    createRequest: CreateAdministrativePermissionAPIRequestADM,
    apiRequestID: UUID,
  ): Task[AdministrativePermissionCreateResponseADM] =
    IriLocker.runWithIriLock(apiRequestID, PERMISSIONS_GLOBAL_LOCK_IRI) {
      for {
        project <-
          knoraProjectService
            .findById(createRequest.forProject)
            .someOrFail(NotFoundException(s"Project '${createRequest.forProject}' not found. Aborting request."))
        group <- groupService
                   .findById(createRequest.forGroup)
                   .someOrFail(NotFoundException(s"Group '${createRequest.forGroup}' not found. Aborting request."))
        newPermissionIri <- iriService
                              .checkOrCreateEntityIri(
                                createRequest.id.map(_.value.toSmartIri),
                                PermissionIri.makeNew(project.shortcode).value,
                              )
                              .map(PermissionIri.unsafeFrom)

        parts <- ZIO.foreach(Chunk.fromIterable(createRequest.hasPermissions))(adm =>
                   ZIO.fromEither(AdministrativePermissionPart.from(adm)).mapError(BadRequestException(_)),
                 )
        _ <- ZIO.fail(BadRequestException("Admin Permissions need to be supplied.")).when(parts.isEmpty)

        _ <- administrativePermissionService.create(
               AdministrativePermission(newPermissionIri, group.id, project.id, parts),
             )

        // try to retrieve the newly created permission
        created <- administrativePermissionService
                     .findById(newPermissionIri)
                     .someOrFail(NotFoundException(s"Administrative permission $newPermissionIri not found."))
      } yield AdministrativePermissionCreateResponseADM(AdministrativePermissionADM.from(created))
    }

  ///////////////////////////////////////////////////////////////////////////
  // DEFAULT OBJECT ACCESS PERMISSIONS
  ///////////////////////////////////////////////////////////////////////////

  def getPermissionsDaopByProjectIri(
    projectIri: ProjectIri,
  ): Task[DefaultObjectAccessPermissionsForProjectGetResponseADM] =
    doapService
      .findByProject(projectIri)
      .map(_.map(DefaultObjectAccessPermissionADM.from))
      .map(DefaultObjectAccessPermissionsForProjectGetResponseADM(_))

  /**
   * Convenience method returning a set with combined max default object access permissions.
   *
   * @param projectIri the IRI of the project.
   * @param groups     the list of groups for which default object access permissions are retrieved and combined.
   * @return a set of [[PermissionADM]].
   */
  private def findDoapPermissionADM(
    projectIri: ProjectIri,
    groups: Seq[GroupIri],
  ): Task[Set[PermissionADM]] = ZIO
    .foreach(groups.map(ForWhat(_)))(findDoapPermissionADM(projectIri, _))
    .map(_.flatten)
    .map(PermissionUtilADM.removeDuplicatePermissions)

  private def findDoapPermissionADM(
    projectIri: ProjectIri,
    forWhat: ForWhat,
  ): Task[Set[PermissionADM]] =
    doapService
      .findByProjectAndForWhat(projectIri, forWhat)
      .map(_.map(DefaultObjectAccessPermissionADM.from).toSet.flatMap(_.hasPermissions))

  /**
   * Returns a string containing default object permissions statements ready for usage during creation of a new resource.
   * The permissions include any default object access permissions defined for the resource class and on any groups the
   * user is member of.
   * The default object access permissions are determined with the following precedence:
   *
   *  1. ProjectAdmin
   *  2. ProjectEntity
   *  3. SystemEntity
   *  4. CustomGroups
   *  5. ProjectMember
   *  6. KnownUser
   *
   * @param projectIri       the IRI of the project.
   * @param resourceClassIri the IRI of the resource class for which the default object access permissions are requested.
   * @param propertyIri      the IRI of the property for which the default object access permissions are requested.
   * @param targetUser       the user for which the permissions need to be calculated.
   * @return an optional string with object access permission statements
   */
  private def defaultObjectAccessPermissionsStringForEntityGetADM(
    projectIri: ProjectIri,
    resourceClassIri: ResourceClassIri,
    propertyIri: Option[PropertyIri],
    entityType: EntityType,
    targetUser: User,
  ): Task[DefaultObjectAccessPermissionsStringResponseADM] = {

    def calculatePermissionWithPrecedence(
      permissionsTasksInOrderOfPrecedence: List[Task[Option[Set[PermissionADM]]]],
    ): Task[Option[Set[PermissionADM]]] =
      ZIO.foldLeft(permissionsTasksInOrderOfPrecedence)(None: Option[Set[PermissionADM]]) { (acc, task) =>
        if (acc.isDefined) {
          ZIO.succeed(acc)
        } else {
          task.flatMap {
            (acc, _) match
              case (None, Some(permissions)) if permissions.nonEmpty => ZIO.some(permissions)
              case _                                                 => ZIO.succeed(acc)
          }
        }
      }

    val projectAdmin =
      findDoapPermissionADM(projectIri, List(builtIn.ProjectAdmin.id))
        .when(targetUser.isProjectAdmin(projectIri) || targetUser.isSystemAdmin)

    val resourceClassProperty = ZIO
      .when(entityType == EntityType.Property)(
        ZIO
          .fromOption(propertyIri)
          .orElseFail(BadRequestException("Property IRI needs to be supplied."))
          .flatMap(p =>
            findDoapPermissionADM(
              projectIri,
              ForWhat(resourceClassIri, p),
            ),
          ),
      )

    val resourceClass = ZIO
      .when(entityType == EntityType.Resource)(
        findDoapPermissionADM(projectIri, ForWhat(resourceClassIri)),
      )

    val property = ZIO
      .when(entityType == EntityType.Property) {
        ZIO
          .fromOption(propertyIri)
          .orElseFail(BadRequestException("Property IRI needs to be supplied."))
          .flatMap(p => findDoapPermissionADM(projectIri, ForWhat(p)))
      }

    val customGroups = {
      val otherGroups =
        targetUser.permissions.groupsPerProject.getOrElse(projectIri.value, Seq.empty).map(GroupIri.unsafeFrom) diff
          List(builtIn.KnownUser.id, builtIn.ProjectMember.id, builtIn.ProjectAdmin.id, builtIn.SystemAdmin.id)
      ZIO.when(otherGroups.distinct.nonEmpty)(findDoapPermissionADM(projectIri, otherGroups))
    }

    val projectMembers = ZIO
      .when(targetUser.isProjectMember(projectIri) || targetUser.isSystemAdmin)(
        findDoapPermissionADM(projectIri, List(builtIn.ProjectMember.id)),
      )

    val knownUser = ZIO.when(!targetUser.isAnonymousUser)(
      findDoapPermissionADM(projectIri, List(builtIn.KnownUser.id)),
    )

    val permissionTasks: List[Task[Option[Set[PermissionADM]]]] =
      List(
        projectAdmin,
        resourceClassProperty,
        resourceClass,
        property,
        customGroups,
        projectMembers,
        knownUser,
      )

    for {
      permissions <- calculatePermissionWithPrecedence(permissionTasks)
      result       = permissions
                 .getOrElse(Set(PermissionADM.from(Permission.ObjectAccess.ChangeRights, builtIn.Creator.id.value)))
      resultStr = PermissionUtilADM.formatPermissionADMs(result, DOAP)
    } yield DefaultObjectAccessPermissionsStringResponseADM(resultStr)
  }

  private def validate(
    req: CreateDefaultObjectAccessPermissionAPIRequestADM,
  ): IO[String, DefaultObjectAccessPermission] =
    for {
      project       <- knoraProjectService.findById(req.forProject).orDie.someOrFail("Project not found")
      permissionIri <-
        ZIO
          .foreach(req.id)(iriConverter.asSmartIri)
          .flatMap(iriService.checkOrCreateEntityIri(_, PermissionIri.makeNew(project.shortcode).value))
          .mapBoth(_.getMessage, PermissionIri.unsafeFrom)

      groupIri         <- ZIO.foreach(req.forGroup)(checkGroupExists).mapError(_.getMessage)
      resourceClassIri <- ZIO.foreach(req.forResourceClass)(checkResourceClassIri).mapError(_.getMessage)
      propertyIri      <- ZIO.foreach(req.forProperty)(checkPropertyIri).mapError(_.getMessage)
      forWhat          <- ZIO.fromEither(ForWhat.fromIris(groupIri, resourceClassIri, propertyIri))
      _                <- ZIO.fail("Permissions needs to be supplied.").when(req.hasPermissions.isEmpty)
      doap             <- ZIO.fromEither(DefaultObjectAccessPermission.from(permissionIri, project.id, forWhat, req.hasPermissions))
    } yield doap

  def createDefaultObjectAccessPermission(
    createRequest: CreateDefaultObjectAccessPermissionAPIRequestADM,
    apiRequestID: UUID,
  ): Task[DefaultObjectAccessPermissionCreateResponseADM] = {
    val createPermissionTask =
      for {
        doap <- validate(createRequest).mapError(BadRequestException(_))
        _    <- doapService.findByProjectAndForWhat(doap.forProject, doap.forWhat).flatMap {
               case Some(existing: DefaultObjectAccessPermission) =>
                 val msg = existing.forWhat match
                   case Group(g)                           => s"and group: '${g.value}' "
                   case ResourceClass(rc)                  => s"and resourceClass: '${rc.value}' "
                   case Property(prop)                     => s"and property: '${prop.value}' "
                   case ResourceClassAndProperty(rc, prop) =>
                     s"and resourceClass: '${rc.value}' and property: '${prop.value}' "
                 ZIO.fail(
                   DuplicateValueException(
                     s"A default object access permission for project: '${doap.forProject.value}' " +
                       msg + "combination already exists. " +
                       s"This permission currently has the scope '${DefaultObjectAccessPermissionRepoLive.toStringLiteral(existing.permission)}'. " +
                       s"Use its IRI ${existing.id.value} to modify it, if necessary.",
                   ),
                 )
               case None => ZIO.unit
             }
        _ <- doapService.save(doap)
      } yield DefaultObjectAccessPermissionCreateResponseADM(DefaultObjectAccessPermissionADM.from(doap))

    IriLocker.runWithIriLock(apiRequestID, PERMISSIONS_GLOBAL_LOCK_IRI)(createPermissionTask)
  }

  def verifyHasPermissionsDOAP(hasPermissions: Set[PermissionADM]): Task[Set[PermissionADM]] = ZIO.attempt {
    validateDOAPHasPermissions(hasPermissions)
    hasPermissions.map { permission =>
      val code: Int = permission.permissionCode match {
        case None       => Permission.ObjectAccess.codeByToken(permission.name)
        case Some(code) => code
      }
      val name = if (permission.name.isEmpty) {
        val nameCodeSet: Option[(String, Int)] = Permission.ObjectAccess.codeByToken.find { case (_, code) =>
          code == permission.permissionCode.get
        }
        nameCodeSet.get._1
      } else {
        permission.name
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
      if (permission.name.nonEmpty && !Permission.ObjectAccess.allTokens(permission.name))
        throw BadRequestException(
          s"Invalid value for name parameter of hasPermissions: ${permission.name}, it should be one of " +
            s"${Permission.ObjectAccess.allTokens.mkString(", ")}",
        )
      if (permission.permissionCode.nonEmpty) {
        val code = permission.permissionCode.get
        if (Permission.ObjectAccess.from(code).isLeft) {
          throw BadRequestException(
            s"Invalid value for permissionCode parameter of hasPermissions: $code, it should be one of " +
              s"${Permission.ObjectAccess.allCodes.mkString(", ")}",
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
        if (!Permission.ObjectAccess.fromToken(permission.name).map(_.code).contains(code)) {
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
  def getPermissionsByProjectIri(projectIri: ProjectIri): Task[PermissionsForProjectGetResponseADM] =
    findAllPermissionsByProjectIri(projectIri).map { case (aps, doaps) =>
      (
        aps.map(p => (p.id, KnoraAdmin.AdministrativePermission)) ++
          doaps.map(p => (p.id, KnoraAdmin.DefaultObjectAccessPermission))
      ).map(PermissionInfoADM.apply).toSet
    }.map(PermissionsForProjectGetResponseADM.apply)

  private def findAllPermissionsByProjectIri(
    projectIri: ProjectIri,
  ): Task[(Chunk[AdministrativePermission], Chunk[DefaultObjectAccessPermission])] =
    administrativePermissionService.findByProject(projectIri) <&> doapService.findByProject(projectIri)

  def updateDoap(
    permissionIri: PermissionIri,
    req: ChangeDoapRequest,
    uuid: UUID,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] =
    val task = updateDoapInternal(permissionIri, req)
      .flatMap(makeExternal)
      .map(DefaultObjectAccessPermissionGetResponseADM.apply)
    IriLocker.runWithIriLock(uuid, permissionIri.value)(task)

  private def updateDoapInternal(
    permissionIri: PermissionIri,
    req: ChangeDoapRequest,
  ): Task[DefaultObjectAccessPermissionADM] =
    for {
      doap <-
        doapService.findById(permissionIri).someOrFail(NotFoundException(s"DOAP ${permissionIri.value} not found."))
      group         <- ZIO.foreach(req.forGroup)(checkGroupExists)
      resourceClass <- ZIO.foreach(req.forResourceClass)(checkResourceClassIri)
      property      <- ZIO.foreach(req.forProperty)(checkPropertyIri)
      newForWhat    <- ZIO
                      .fromEither(ForWhat.fromIris(group, resourceClass, property))
                      .when(req.hasForWhat)
                      .mapError(BadRequestException.apply)

      newPermissions <- ZIO.foreach(req.hasPermissions)(permissions =>
                          ZIO
                            .fromEither(DefaultObjectAccessPermissionPart.from(permissions.toSeq))
                            .mapBoth(BadRequestException.apply, Chunk.fromIterable),
                        )

      update = doap.copy(
                 forWhat = newForWhat.getOrElse(doap.forWhat),
                 permission = newPermissions.getOrElse(doap.permission),
               )

      newDoap <- doapService.save(update)
    } yield DefaultObjectAccessPermissionADM.from(newDoap)

  private def checkGroupExists(groupIri: GroupIri): Task[GroupIri] = groupService
    .findById(groupIri)
    .someOrFail(BadRequestException(s"Group $groupIri not found."))
    .as(groupIri)

  private def checkPropertyIri(propertyIri: IRI): Task[PropertyIri] =
    iriConverter
      .asPropertyIri(propertyIri)
      .filterOrFail(_.smartIri.isKnoraEntityIri)(())
      .orElseFail(BadRequestException(s"<$propertyIri> is not a Knora property IRI"))

  private def checkResourceClassIri(resourceClassIri: IRI): Task[ResourceClassIri] = for {
    smartIri <- iriConverter.asSmartIri(resourceClassIri).mapError(BadRequestException.apply)
    rcIri    <- ZIO.fromEither(ResourceClassIri.from(smartIri)).mapError(BadRequestException.apply)
  } yield rcIri

  private def makeExternal(doap: DefaultObjectAccessPermissionADM): Task[DefaultObjectAccessPermissionADM] = for {
    forResourceClass <- ZIO.foreach(doap.forResourceClass)(iriConverter.asExternalIri)
    forProperty      <- ZIO.foreach(doap.forProperty)(iriConverter.asExternalIri)
  } yield doap.copy(forResourceClass = forResourceClass, forProperty = forProperty)

  def updatePermissionsGroup(
    permissionIri: PermissionIri,
    groupIri: GroupIri,
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM] = IriLocker.runWithIriLock(apiRequestID, permissionIri) {
    for {
      _      <- groupService.findById(groupIri).someOrFail(NotFoundException.from(groupIri))
      perm   <- findPermissionByIri(permissionIri)
      result <- perm match {
                  case Left(ap: AdministrativePermission) =>
                    administrativePermissionService
                      .setForGroup(ap, groupIri)
                      .map(AdministrativePermissionADM.from)
                      .map(AdministrativePermissionGetResponseADM(_))
                  case Right(doap) =>
                    doapService
                      .setForWhat(doap, ForWhat(groupIri))
                      .map(DefaultObjectAccessPermissionADM.from)
                      .map(DefaultObjectAccessPermissionGetResponseADM(_))
                }
    } yield result
  }

  private def findPermissionByIri(
    permissionIri: PermissionIri,
  ): Task[Either[AdministrativePermission, DefaultObjectAccessPermission]] =
    administrativePermissionService.findById(permissionIri).flatMap {
      case Some(adminPerm) => ZIO.left(adminPerm)
      case None            => doapService.findById(permissionIri).someOrFail(NotFoundException.from(permissionIri)).map(Right(_))
    }

  /**
   * Update a permission's set of hasPermissions.
   *
   * @param permissionIri               the IRI of the permission.
   * @param newHasPermissions           the request to change hasPermissions.
   * @param apiRequestID                the API request ID.
   * @return [[PermissionGetResponseADM]].
   *         fails with an UpdateNotPerformedException if something has gone wrong.
   */
  def updatePermissionHasPermissions(
    permissionIri: PermissionIri,
    newHasPermissions: NonEmptyChunk[PermissionADM],
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM] = IriLocker.runWithIriLock(apiRequestID, permissionIri.value)(for {
    apOrDoap <- findPermissionByIri(permissionIri)
    result   <- apOrDoap match {
                case Left(ap) =>
                  for {
                    parts <- ZIO.foreach(newHasPermissions)(p =>
                               ZIO.fromEither(AdministrativePermissionPart.from(p)).mapError(BadRequestException(_)),
                             )
                    saved <- administrativePermissionService.setParts(ap, parts)
                  } yield AdministrativePermissionGetResponseADM(AdministrativePermissionADM.from(saved))
                case Right(doap) =>
                  for {
                    parts <- ZIO
                               .fromEither(DefaultObjectAccessPermissionPart.from(newHasPermissions))
                               .mapBoth(BadRequestException(_), Chunk.fromIterable)
                    saved <- doapService.setParts(doap, parts)
                  } yield DefaultObjectAccessPermissionGetResponseADM(DefaultObjectAccessPermissionADM.from(saved))
              }
  } yield result)

  /**
   * Update a doap permission's resource class.
   *
   * @param permission                    the IRI of the permission.
   * @param changeRequest                 the request to change hasPermissions.
   * @param apiRequestID                  the API request ID.
   * @return [[DefaultObjectAccessPermissionGetResponseADM]].
   */
  def updatePermissionResourceClass(
    permission: PermissionIri,
    changeRequest: ChangePermissionResourceClassApiRequestADM,
    apiRequestID: UUID,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] = IriLocker.runWithIriLock(apiRequestID, permission.value)(
    updateDoapInternal(permission, ChangeDoapRequest(forResourceClass = Some(changeRequest.forResourceClass)))
      .map(DefaultObjectAccessPermissionGetResponseADM.apply),
  )

  def updatePermissionProperty(
    permission: PermissionIri,
    changeRequest: ChangePermissionPropertyApiRequestADM,
    apiRequestID: UUID,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] = IriLocker.runWithIriLock(apiRequestID, permission)(
    updateDoapInternal(permission, ChangeDoapRequest(forProperty = Some(changeRequest.forProperty)))
      .map(DefaultObjectAccessPermissionGetResponseADM.apply),
  )

  def deletePermission(
    permissionIri: PermissionIri,
    apiRequestID: UUID,
  ): Task[PermissionDeleteResponseADM] = IriLocker.runWithIriLock(apiRequestID, permissionIri)(for {
    apOrDoap <- findPermissionByIri(permissionIri)
    _        <- apOrDoap match {
           case Left(ap)    => administrativePermissionService.delete(ap)
           case Right(doap) => doapService.delete(doap)
         }
  } yield PermissionDeleteResponseADM(permissionIri))

  def createPermissionsForAdminsAndMembersOfNewProject(project: KnoraProject): Task[Unit] =
    for {
      // Give the admins of the new project rights for any operation in project level, and rights to create resources.
      _ <- administrativePermissionService.create(
             project,
             builtIn.ProjectAdmin,
             Chunk(Simple.unsafeFrom(ProjectAdminAll), Simple.unsafeFrom(ProjectResourceCreateAll)),
           )
      // Give the members of the new project rights to create resources.
      _ <- administrativePermissionService.create(
             project,
             builtIn.ProjectMember,
             Chunk(Simple.unsafeFrom(ProjectResourceCreateAll)),
           )

      // Create default object access permissions for SystemAdmin of the new project
      _ <- createDefaultObjectAccessPermission(
             CreateDefaultObjectAccessPermissionAPIRequestADM(
               forProject = project.id,
               forGroup = Some(builtIn.ProjectAdmin.id),
               hasPermissions = Set(
                 PermissionADM.from(Permission.ObjectAccess.ChangeRights, builtIn.ProjectAdmin.id.value),
                 PermissionADM.from(Permission.ObjectAccess.Delete, builtIn.ProjectMember.id.value),
               ),
             ),
             UUID.randomUUID(),
           )

      // Create default object access permissions for ProjectAdmin of the new project
      _ <- createDefaultObjectAccessPermission(
             CreateDefaultObjectAccessPermissionAPIRequestADM(
               forProject = project.id,
               forGroup = Some(builtIn.ProjectMember.id),
               hasPermissions = Set(
                 PermissionADM.from(Permission.ObjectAccess.ChangeRights, builtIn.ProjectAdmin.id.value),
                 PermissionADM.from(Permission.ObjectAccess.Delete, builtIn.ProjectMember.id.value),
               ),
             ),
             UUID.randomUUID(),
           )
    } yield ()

  /**
   * Gets the default permissions for new values.
   *
   * @param projectIri       the IRI of the project of the containing resource.
   * @param resourceClassIri the internal IRI of the resource class.
   * @param propertyIris     the internal IRIs of the properties that points to the values.
   * @param targetUser       the user that is creating the value.
   * @return a permission string.
   */
  def newValueDefaultObjectAccessPermissions(
    projectIri: ProjectIri,
    resourceClassIri: SmartIri,
    propertyIris: Set[SmartIri],
    targetUser: User,
  ): Task[Map[SmartIri, DefaultObjectAccessPermissionsStringResponseADM]] =
    ZIO
      .foreach(propertyIris) { propertyIri =>
        newValueDefaultObjectAccessPermissions(projectIri, resourceClassIri, propertyIri, targetUser)
          .map(propertyIri -> _)
      }
      .map(_.toMap)

  /**
   * Gets the default permissions for a new value.
   *
   * @param projectIri       the IRI of the project of the containing resource.
   * @param resourceClassIri the internal IRI of the resource class.
   * @param propertyIri      the internal IRI of the property that points to the value.
   * @param targetUser       the user that is creating the value.
   * @return a permission string.
   */
  def newValueDefaultObjectAccessPermissions(
    projectIri: ProjectIri,
    resourceClassIri: SmartIri,
    propertyIri: SmartIri,
    targetUser: User,
  ): Task[DefaultObjectAccessPermissionsStringResponseADM] =
    for {
      _ <- ZIO.unless(resourceClassIri.isKnoraEntityIri) {
             ZIO.fail(BadRequestException(s"Invalid resource class IRI: $resourceClassIri"))
           }
      _ <- ZIO.unless(propertyIri.isKnoraEntityIri) {
             ZIO.fail(BadRequestException(s"Invalid property IRI: $propertyIri"))
           }
      _ <- ZIO.when(targetUser.isAnonymousUser) {
             ZIO.fail(BadRequestException("Anonymous Users are not allowed."))
           }
      permission <- defaultObjectAccessPermissionsStringForEntityGetADM(
                      projectIri,
                      ResourceClassIri.unsafeFrom(resourceClassIri),
                      Some(PropertyIri.unsafeFrom(propertyIri)),
                      EntityType.Property,
                      targetUser,
                    )
    } yield permission

  def newResourceDefaultObjectAccessPermissions(
    projectIri: ProjectIri,
    resourceClassIri: SmartIri,
    targetUser: User,
  ): Task[DefaultObjectAccessPermissionsStringResponseADM] =
    ZIO
      .fail(BadRequestException(s"Invalid resource class IRI: $resourceClassIri"))
      .when(!resourceClassIri.isKnoraEntityIri) *>
      defaultObjectAccessPermissionsStringForEntityGetADM(
        projectIri,
        ResourceClassIri.unsafeFrom(resourceClassIri),
        None,
        EntityType.Resource,
        targetUser,
      )
}

object PermissionsResponder {
  val layer = ZLayer.derive[PermissionsResponder]
}
