/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import com.typesafe.scalalogging.LazyLogging
import zio.*

import java.util.UUID

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType.DOAP
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.api.PermissionEndpointsRequests.ChangeDoapRequest
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
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.AdministrativePermissionService
import org.knora.webapi.slice.admin.domain.service.DefaultObjectAccessPermissionService
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo.*
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.repo.service.DefaultObjectAccessPermissionRepoLive
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

final case class PermissionsResponder(
  private val groupService: GroupService,
  private val iriService: IriService,
  private val knoraProjectService: KnoraProjectService,
  private val triplestore: TriplestoreService,
  private val auth: AuthorizationRestService,
  private val administrativePermissionService: AdministrativePermissionService,
  private val iriConverter: IriConverter,
  private val ontologyRepo: OntologyRepo,
  private val doapService: DefaultObjectAccessPermissionService,
)(implicit val stringFormatter: StringFormatter)
    extends LazyLogging {

  private val PERMISSIONS_GLOBAL_LOCK_IRI = "http://rdfh.ch/permissions"

  private enum EntityType {
    case Resource
    case Property
  }

  def getPermissionsApByProjectIri(projectIRI: IRI): Task[AdministrativePermissionsForProjectGetResponseADM] =
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

  private def validate(req: CreateAdministrativePermissionAPIRequestADM): Task[Unit] = ZIO.attempt {
    req.id.foreach(iri => PermissionIri.from(iri).fold(msg => throw BadRequestException(msg), _ => ()))

    ProjectIri.from(req.forProject).fold(msg => throw BadRequestException(msg), _ => ())

    if (req.hasPermissions.isEmpty) throw BadRequestException("Permissions needs to be supplied.")

    if (!builtIn.all.map(_.id.value).contains(req.forGroup) && GroupIri.from(req.forGroup).isLeft) {
      throw BadRequestException(s"Invalid group IRI ${req.forGroup}")
    }

    verifyHasPermissionsAP(req.hasPermissions)

  }.unit

  /**
   * For administrative permission we only need the name parameter of each PermissionADM given in hasPermissions collection.
   * This method validates the content of hasPermissions collection by only keeping the values of name params.
   * @param hasPermissions       Set of the permissions.
   */
  private def verifyHasPermissionsAP(hasPermissions: Set[PermissionADM]): Set[PermissionADM] =
    hasPermissions
      .map(_.name)
      .map { name =>
        Permission.Administrative
          .fromToken(name)
          .getOrElse(
            throw BadRequestException(
              s"Invalid value for name parameter of hasPermissions: $name, it should be one of " + s"${Permission.Administrative.allTokens
                  .mkString(", ")}",
            ),
          )
      }
      .map(PermissionADM.from)

  def createAdministrativePermission(
    createRequest: CreateAdministrativePermissionAPIRequestADM,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[AdministrativePermissionCreateResponseADM] = {
    val createAdministrativePermissionTask =
      for {
        _ <- validate(createRequest)
        // does the permission already exist
        projectId <- ZIO.fromEither(ProjectIri.from(createRequest.forProject)).mapError(BadRequestException.apply)
        _ <- // ensure that no permission already exists for project and group
          administrativePermissionService
            .findByGroupAndProject(GroupIri.unsafeFrom(createRequest.forGroup), projectId)
            .map(_.map(AdministrativePermissionADM.from))
            .flatMap {
              case Some(ap: AdministrativePermissionADM) =>
                ZIO.fail(
                  DuplicateValueException(
                    s"An administrative permission for project: '${createRequest.forProject}' and group: '${createRequest.forGroup}' combination already exists. " +
                      s"This permission currently has the scope '${PermissionUtilADM
                          .formatPermissionADMs(ap.hasPermissions, PermissionType.AP)}'. " +
                      s"Use its IRI ${ap.iri} to modify it, if necessary.",
                  ),
                )
              case None => ZIO.unit
            }

        project <-
          knoraProjectService
            .findById(projectId)
            .someOrFail(NotFoundException(s"Project '${createRequest.forProject}' not found. Aborting request."))

        // get group
        groupIri <-
          if (builtIn.all.map(_.id.value).contains(createRequest.forGroup)) {
            ZIO.succeed(createRequest.forGroup)
          } else {
            for {
              iri <- ZIO.fromEither(GroupIri.from(createRequest.forGroup)).mapError(ValidationException(_))
              group <-
                groupService
                  .findById(iri)
                  .someOrFail(NotFoundException(s"Group '${createRequest.forGroup}' not found. Aborting request."))
            } yield group.id
          }

        customPermissionIri: Option[SmartIri] = createRequest.id.map(iri => stringFormatter.toSmartIri(iri))
        newPermissionIri <- iriService.checkOrCreateEntityIri(
                              customPermissionIri,
                              PermissionIri.makeNew(project.shortcode).value,
                            )

        // Create the administrative permission.
        createAdministrativePermissionSparql = sparql.admin.txt.createNewAdministrativePermission(
                                                 AdminConstants.permissionsDataNamedGraph.value,
                                                 permissionClassIri =
                                                   OntologyConstants.KnoraAdmin.AdministrativePermission,
                                                 permissionIri = newPermissionIri,
                                                 projectIri = project.id.value,
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
      result <- administrativePermission match {
                  case ap: AdministrativePermissionADM =>
                    ZIO.succeed(AdministrativePermissionGetResponseADM(ap))
                  case _ =>
                    ZIO.fail(BadRequestException(s"$administrativePermissionIri is not an administrative permission."))
                }
    } yield result

  ///////////////////////////////////////////////////////////////////////////
  // DEFAULT OBJECT ACCESS PERMISSIONS
  ///////////////////////////////////////////////////////////////////////////

  def getPermissionsDaopByProjectIri(
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
    projectIri: ProjectIri,
    groupIri: Option[IRI],
    resourceClassIri: Option[IRI],
    propertyIri: Option[IRI],
  ): Task[Option[DefaultObjectAccessPermissionADM]] =
    triplestore
      .query(
        Select(
          sparql.admin.txt.getDefaultObjectAccessPermission(projectIri.value, groupIri, resourceClassIri, propertyIri),
        ),
      )
      .flatMap(toDefaultObjectAccessPermission(_, projectIri, groupIri, resourceClassIri, propertyIri))

  private def toDefaultObjectAccessPermission(
    result: SparqlSelectResult,
    projectIri: ProjectIri,
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
            s"Only one default object permission instance allowed for project: ${projectIri.value} and combination of group: $groupIri, resourceClass: $resourceClassIri, property: $propertyIri combination, but found: $doapCount.",
          )

        /* get the iri of the retrieved permission */
        val permissionIri = result.getFirstRowOrThrow.rowMap("s")

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
   * Convenience method returning a set with combined max default object access permissions.
   *
   * @param projectIri the IRI of the project.
   * @param groups     the list of groups for which default object access permissions are retrieved and combined.
   * @return a set of [[PermissionADM]].
   */
  private def getDefaultObjectAccessPermissions(projectIri: ProjectIri, groups: Seq[IRI]): Task[Set[PermissionADM]] =
    ZIO
      .foreach(groups) { groupIri =>
        defaultObjectAccessPermissionGetADM(projectIri, Some(groupIri), None, None).map {
          _.map(_.hasPermissions).getOrElse(Set.empty[PermissionADM])
        }
      }
      .map(_.flatten)
      .map(PermissionUtilADM.removeDuplicatePermissions)

  /**
   * Convenience method returning a set with default object access permissions defined on a resource class.
   *
   * @param projectIri       the IRI of the project.
   * @param resourceClassIri the resource's class IRI
   * @return a set of [[PermissionADM]].
   */
  private def defaultObjectAccessPermissionsForResourceClassGetADM(
    projectIri: ProjectIri,
    resourceClassIri: IRI,
  ): Task[Set[PermissionADM]] =
    defaultObjectAccessPermissionGetADM(projectIri, None, Some(resourceClassIri), None).map {
      case Some(doap) => doap.hasPermissions
      case None       => Set.empty
    }

  /**
   * Convenience method returning a set with default object access permissions defined on a resource class / property combination.
   *
   * @param projectIri       the IRI of the project.
   * @param resourceClassIri the resource's class IRI
   * @param propertyIri      the property's IRI.
   * @return a set of [[PermissionADM]].
   */
  private def defaultObjectAccessPermissionsForResourceClassPropertyGetADM(
    projectIri: ProjectIri,
    resourceClassIri: IRI,
    propertyIri: IRI,
  ): Task[Set[PermissionADM]] =
    defaultObjectAccessPermissionGetADM(projectIri, None, Some(resourceClassIri), Some(propertyIri)).map {
      case Some(doap) => doap.hasPermissions
      case None       => Set.empty[PermissionADM]
    }

  /**
   * Convenience method returning a set with default object access permissions defined on a property.
   *
   * @param projectIri  the IRI of the project.
   * @param propertyIri the property's IRI.
   * @return a set of [[PermissionADM]].
   */
  private def defaultObjectAccessPermissionsForPropertyGetADM(
    projectIri: ProjectIri,
    propertyIri: IRI,
  ): Task[Set[PermissionADM]] =
    defaultObjectAccessPermissionGetADM(projectIri, None, None, Some(propertyIri)).map {
      case Some(doap) => doap.hasPermissions
      case None       => Set.empty[PermissionADM]
    }

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
    resourceClassIri: IRI,
    propertyIri: Option[IRI],
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
      getDefaultObjectAccessPermissions(projectIri, List(builtIn.ProjectAdmin.id.value))
        .when(targetUser.isProjectAdmin(projectIri) || targetUser.isSystemAdmin)

    val resourceClassProperty = ZIO
      .when(entityType == EntityType.Property)(
        ZIO
          .fromOption(propertyIri)
          .orElseFail(BadRequestException("Property IRI needs to be supplied."))
          .flatMap(defaultObjectAccessPermissionsForResourceClassPropertyGetADM(projectIri, resourceClassIri, _)),
      )

    val resourceClass = ZIO
      .when(entityType == EntityType.Resource)(
        defaultObjectAccessPermissionsForResourceClassGetADM(projectIri, resourceClassIri),
      )

    val property = ZIO
      .when(entityType == EntityType.Property) {
        ZIO
          .fromOption(propertyIri)
          .orElseFail(BadRequestException("Property IRI needs to be supplied."))
          .flatMap(defaultObjectAccessPermissionsForPropertyGetADM(projectIri, _))
      }

    val customGroups = {
      val otherGroups = targetUser.permissions.groupsPerProject.getOrElse(projectIri.value, Seq.empty) diff
        List(builtIn.KnownUser.id, builtIn.ProjectMember.id, builtIn.ProjectAdmin.id, builtIn.SystemAdmin.id)
          .map(_.value)
      ZIO.when(otherGroups.distinct.nonEmpty)(getDefaultObjectAccessPermissions(projectIri, otherGroups))
    }

    val projectMembers = ZIO
      .when(targetUser.isProjectMember(projectIri) || targetUser.isSystemAdmin)(
        getDefaultObjectAccessPermissions(projectIri, List(builtIn.ProjectMember.id.value)),
      )

    val knownUser = ZIO.when(!targetUser.isAnonymousUser)(
      getDefaultObjectAccessPermissions(projectIri, List(builtIn.KnownUser.id.value)),
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
      result = permissions
                 .getOrElse(Set(PermissionADM.from(Permission.ObjectAccess.ChangeRights, builtIn.Creator.id.value)))
      resultStr = PermissionUtilADM.formatPermissionADMs(result, DOAP)
    } yield DefaultObjectAccessPermissionsStringResponseADM(resultStr)
  }

  private def validate(
    req: CreateDefaultObjectAccessPermissionAPIRequestADM,
  ): IO[String, DefaultObjectAccessPermission] =
    for {
      projectIri <- ZIO.fromEither(ProjectIri.from(req.forProject))
      project    <- knoraProjectService.findById(projectIri).orDie.someOrFail("Project not found")
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
      doap             <- ZIO.fromEither(DefaultObjectAccessPermission.from(permissionIri, projectIri, forWhat, req.hasPermissions))
    } yield doap

  def createDefaultObjectAccessPermission(
    createRequest: CreateDefaultObjectAccessPermissionAPIRequestADM,
    apiRequestID: UUID,
  ): Task[DefaultObjectAccessPermissionCreateResponseADM] = {
    val createPermissionTask =
      for {
        doap <- validate(createRequest).mapError(BadRequestException(_))
        _ <- doapService.findByProjectAndForWhat(doap.forProject, doap.forWhat).flatMap {
               case Some(existing: DefaultObjectAccessPermission) =>
                 val msg = existing.forWhat match
                   case Group(g)          => s"and group: '${g.value}' "
                   case ResourceClass(rc) => s"and resourceClass: '${rc.value}' "
                   case Property(prop)    => s"and property: '${prop.value}' "
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
      } yield DefaultObjectAccessPermissionCreateResponseADM(doapService.asDefaultObjectAccessPermissionADM(doap))

    IriLocker.runWithIriLock(apiRequestID, PERMISSIONS_GLOBAL_LOCK_IRI, createPermissionTask)
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

  def updateDoap(
    permissionIri: PermissionIri,
    req: ChangeDoapRequest,
    uuid: UUID,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] =
    val task = updateDoapInternal(permissionIri, req)
      .flatMap(makeExternal)
      .map(DefaultObjectAccessPermissionGetResponseADM.apply)
    IriLocker.runWithIriLock(uuid, permissionIri.value, task)

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
      newForWhat <- ZIO
                      .fromEither(ForWhat.fromIris(group, resourceClass, property))
                      .when(req.hasForWhat)
                      .mapError(BadRequestException.apply)

      newPermissions <- ZIO.foreach(req.hasPermissions)(asDefaultObjectAccessPermissionParts)

      update = doap.copy(
                 forWhat = newForWhat.getOrElse(doap.forWhat),
                 permission = newPermissions.getOrElse(doap.permission),
               )

      newDoap <- doapService.save(update)
    } yield doapService.asDefaultObjectAccessPermissionADM(newDoap)

  private def checkGroupExists(groupIri: IRI): Task[GroupIri] = for {
    gIri <- ZIO.fromEither(GroupIri.from(groupIri)).mapError(BadRequestException.apply)
    _    <- groupService.findById(gIri).someOrFail(BadRequestException(s"Group ${groupIri} not found."))
  } yield gIri

  private def checkPropertyIri(propertyIri: IRI): Task[PropertyIri] = for {
    smartIri <- iriConverter.asSmartIri(propertyIri).mapError(BadRequestException.apply)
    pIri     <- ZIO.fromEither(PropertyIri.from(smartIri)).mapError(BadRequestException.apply)
  } yield pIri

  private def checkResourceClassIri(resourceClassIri: IRI): Task[ResourceClassIri] = for {
    smartIri <- iriConverter.asSmartIri(resourceClassIri).mapError(BadRequestException.apply)
    rcIri    <- ZIO.fromEither(ResourceClassIri.from(smartIri)).mapError(BadRequestException.apply)
  } yield rcIri

  private def asDefaultObjectAccessPermissionParts(
    permissions: Set[PermissionADM],
  ): IO[BadRequestException, Chunk[DefaultObjectAccessPermissionPart]] =
    ZIO
      .foreach(permissions)(p =>
        ZIO
          .fromEither(DefaultObjectAccessPermissionPart.from(p))
          .mapError(BadRequestException.apply),
      )
      .map(Chunk.fromIterable)

  private def makeExternal(doap: DefaultObjectAccessPermissionADM): Task[DefaultObjectAccessPermissionADM] = for {
    forResourceClass <- ZIO.foreach(doap.forResourceClass)(iriConverter.asExternalIri)
    forProperty      <- ZIO.foreach(doap.forProperty)(iriConverter.asExternalIri)
  } yield doap.copy(forResourceClass = forResourceClass, forProperty = forProperty)

  def updatePermissionsGroup(
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
        response <-
          permission match {
            // Is permission an administrative permission?
            case ap: AdministrativePermissionADM =>
              // Yes. Update the group
              for {
                _                 <- updatePermission(permissionIri = ap.iri, maybeGroup = Some(groupIri.value))
                updatedPermission <- verifyPermissionGroupUpdate
              } yield AdministrativePermissionGetResponseADM(
                updatedPermission.asInstanceOf[AdministrativePermissionADM],
              )
            case _: DefaultObjectAccessPermissionADM =>
              updateDoapInternal(permissionIri, ChangeDoapRequest(forGroup = Some(groupIri.value)))
                .map(DefaultObjectAccessPermissionGetResponseADM.apply)
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
  def updatePermissionHasPermissions(
    permissionIri: PermissionIri,
    newHasPermissions: NonEmptyChunk[PermissionADM],
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM] = {
    val permissionIriInternal =
      stringFormatter.toSmartIri(permissionIri.value).toOntologySchema(InternalSchema).toString
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
                          verifyHasPermissionsAP(newHasPermissions.toSet)
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
                      case _: DefaultObjectAccessPermissionADM =>
                        val request = ChangeDoapRequest(hasPermissions = Some(newHasPermissions.toSet))
                        updateDoapInternal(permissionIri, request)
                          .map(DefaultObjectAccessPermissionGetResponseADM.apply)
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
   * @param permission                    the IRI of the permission.
   * @param changeRequest                 the request to change hasPermissions.
   * @param apiRequestID                  the API request ID.
   * @return [[DefaultObjectAccessPermissionGetResponseADM]].
   */
  def updatePermissionResourceClass(
    permission: PermissionIri,
    changeRequest: ChangePermissionResourceClassApiRequestADM,
    apiRequestID: UUID,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] = {
    val task: Task[DefaultObjectAccessPermissionGetResponseADM] =
      for {
        doap          <- doapService.findById(permission).someOrFail(NotFoundException(s"DOAP ${permission.value} not found."))
        resourceClass <- checkResourceClassIri(changeRequest.forResourceClass)
        newDoap       <- doapService.save(doap.copy(forWhat = ResourceClass(resourceClass.toInternal)))
      } yield DefaultObjectAccessPermissionGetResponseADM(doapService.asDefaultObjectAccessPermissionADM(newDoap))

    IriLocker.runWithIriLock(apiRequestID, permission.value, task)
  }

  def updatePermissionProperty(
    permission: PermissionIri,
    changeRequest: ChangePermissionPropertyApiRequestADM,
    apiRequestID: UUID,
  ): Task[DefaultObjectAccessPermissionGetResponseADM] = {
    val task: Task[DefaultObjectAccessPermissionGetResponseADM] =
      for {
        doap     <- doapService.findById(permission).someOrFail(NotFoundException(s"DOAP ${permission.value} not found."))
        property <- checkPropertyIri(changeRequest.forProperty)
        newDoap  <- doapService.save(doap.copy(forWhat = Property(property.toInternal)))
      } yield DefaultObjectAccessPermissionGetResponseADM(doapService.asDefaultObjectAccessPermissionADM(newDoap))

    IriLocker.runWithIriLock(apiRequestID, permission.value, task)
  }

  def deletePermission(
    permissionIri: PermissionIri,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionDeleteResponseADM] = {
    val permissionIriInternal =
      stringFormatter.toSmartIri(permissionIri.value).toOntologySchema(InternalSchema).toString
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
    if (
      !requestingUser.isSystemUser && !requestingUser.isSystemAdmin && !requestingUser.permissions.isProjectAdmin(
        projectIri,
      )
    ) {

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

  def createPermissionsForAdminsAndMembersOfNewProject(projectIri: ProjectIri): Task[Unit] =
    for {
      // Give the admins of the new project rights for any operation in project level, and rights to create resources.
      _ <- createAdministrativePermission(
             CreateAdministrativePermissionAPIRequestADM(
               forProject = projectIri.value,
               forGroup = builtIn.ProjectAdmin.id.value,
               hasPermissions = Set(
                 PermissionADM.from(Permission.Administrative.ProjectAdminAll),
                 PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll),
               ),
             ),
             KnoraSystemInstances.Users.SystemUser,
             UUID.randomUUID(),
           )

      // Give the members of the new project rights to create resources.
      _ <- createAdministrativePermission(
             CreateAdministrativePermissionAPIRequestADM(
               forProject = projectIri.value,
               forGroup = builtIn.ProjectMember.id.value,
               hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)),
             ),
             KnoraSystemInstances.Users.SystemUser,
             UUID.randomUUID(),
           )

      // Create default object access permissions for SystemAdmin of the new project
      _ <- createDefaultObjectAccessPermission(
             CreateDefaultObjectAccessPermissionAPIRequestADM(
               forProject = projectIri.value,
               forGroup = Some(builtIn.ProjectAdmin.id.value),
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
               forProject = projectIri.value,
               forGroup = Some(builtIn.ProjectMember.id.value),
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
                      resourceClassIri.toString,
                      Some(propertyIri.toString),
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
        resourceClassIri.toString,
        None,
        EntityType.Resource,
        targetUser,
      )
}

object PermissionsResponder {
  val layer = ZLayer.derive[PermissionsResponder]
}
