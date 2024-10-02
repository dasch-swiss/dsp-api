/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import com.typesafe.scalalogging.LazyLogging
import zio.*

import java.util.UUID
import scala.collection.mutable.ListBuffer

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.AdministrativePermissionService
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo.*
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

final case class PermissionsResponder(
  appConfig: AppConfig,
  groupService: GroupService,
  iriService: IriService,
  knoraProjectService: KnoraProjectService,
  messageRelay: MessageRelay,
  triplestore: TriplestoreService,
  auth: AuthorizationRestService,
  administrativePermissionService: AdministrativePermissionService,
)(implicit val stringFormatter: StringFormatter)
    extends MessageHandler
    with LazyLogging {

  private val PERMISSIONS_GLOBAL_LOCK_IRI = "http://rdfh.ch/permissions"
  /* Entity types used to more clearly distinguish what kind of entity is meant */
  private val ResourceEntityType = "resource"
  private val PropertyEntityType = "property"

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[PermissionsResponderRequestADM]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
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
    case PermissionByIriGetRequestADM(permissionIri, requestingUser) =>
      permissionByIriGetRequestADM(permissionIri, requestingUser)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
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
    val projectIriInternal = stringFormatter.toSmartIri(projectIri).toOntologySchema(InternalSchema).toString
    defaultObjectAccessPermissionGetADM(projectIriInternal, groupIri, resourceClassIri, propertyIri).flatMap {
      case Some(doap) => ZIO.attempt(DefaultObjectAccessPermissionGetResponseADM(doap))
      case None       =>
        /* if the query was for a property, then we need to additionally check if it is a system property */
        if (propertyIri.isDefined) {
          val systemProject = KnoraProjectRepo.builtIn.SystemProject.id.value
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
  private def getDefaultObjectAccessPermissions(projectIri: IRI, groups: Seq[IRI]): Task[Set[PermissionADM]] =
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
  ): Task[DefaultObjectAccessPermissionsStringResponseADM] =
    for {
      /* Get the groups the user is member of. */
      userGroups <-
        ZIO.attempt(targetUser.permissions.groupsPerProject.get(projectIri).map(_.toSet).getOrElse(Set.empty[IRI]))

      /* Explicitly add 'SystemAdmin' and 'KnownUser' groups. */
      extendedUserGroups: List[IRI] =
        if (targetUser.permissions.isSystemAdmin) {
          builtIn.SystemAdmin.id.value :: builtIn.KnownUser.id.value :: userGroups.toList
        } else {
          builtIn.KnownUser.id.value :: userGroups.toList
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
      defaultPermissionsOnProjectAdminGroup <- getDefaultObjectAccessPermissions(
                                                 projectIri,
                                                 List(builtIn.ProjectAdmin.id.value),
                                               )
      _ = if (defaultPermissionsOnProjectAdminGroup.nonEmpty) {
            if (
              extendedUserGroups.contains(builtIn.ProjectAdmin.id.value) || extendedUserGroups.contains(
                builtIn.SystemAdmin.id.value,
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
          val systemProject = KnoraProjectRepo.builtIn.SystemProject.id.value
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
          val systemProject = KnoraProjectRepo.builtIn.SystemProject.id.value
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
          val systemProject = KnoraProjectRepo.builtIn.SystemProject.id.value
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
            builtIn.KnownUser.id.value,
            builtIn.ProjectMember.id.value,
            builtIn.ProjectAdmin.id.value,
            builtIn.SystemAdmin.id.value,
          )
          if (customGroups.nonEmpty) {
            getDefaultObjectAccessPermissions(projectIri, customGroups)
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
          getDefaultObjectAccessPermissions(projectIri, List(builtIn.ProjectMember.id.value))
        } else {
          ZIO.attempt(Set.empty[PermissionADM])
        }
      }
      _ = if (defaultPermissionsOnProjectMemberGroup.nonEmpty) {
            if (
              extendedUserGroups.contains(builtIn.ProjectMember.id.value) || extendedUserGroups.contains(
                builtIn.SystemAdmin.id.value,
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
          getDefaultObjectAccessPermissions(projectIri, List(builtIn.KnownUser.id.value))
        } else {
          ZIO.attempt(Set.empty[PermissionADM])
        }
      }
      _ = if (defaultPermissionsOnKnownUserGroup.nonEmpty) {
            if (extendedUserGroups.contains(builtIn.KnownUser.id.value)) {
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
            PermissionADM.from(Permission.ObjectAccess.ChangeRights, builtIn.Creator.id.value),
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

    ProjectIri.from(req.forProject).getOrElse(throw BadRequestException(s"Invalid project IRI  ${req.forProject}"))

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

  def createDefaultObjectAccessPermission(
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
        _ <- auth.ensureSystemAdminOrProjectAdmin(user, project)
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

        customPermissionIri: Option[SmartIri] = createRequest.id.map(iri => stringFormatter.toSmartIri(iri))
        newPermissionIri <- iriService.checkOrCreateEntityIri(
                              customPermissionIri,
                              PermissionIri.makeNew(project.shortcode).value,
                            )
        // verify group, if any given.
        // Is a group given that is not a built-in one?
        maybeGroupIri <-
          if (createRequest.forGroup.exists(!builtIn.all.map(_.id.value).contains(_))) {
            // Yes. Check if it is a known group.
            for {
              maybeIri <- ZIO
                            .fromOption(createRequest.forGroup)
                            .orElseFail(NotFoundException("Group IRI not found."))
              groupIri <- ZIO.fromEither(GroupIri.from(maybeIri)).mapError(ValidationException(_))
              group <-
                groupService
                  .findById(groupIri)
                  .someOrFail(NotFoundException(s"Group '${createRequest.forGroup}' not found. Aborting request."))
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
        if (Permission.ObjectAccess.from(code).isEmpty) {
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
  def updatePermissionResourceClass(
    permissionIri: PermissionIri,
    changePermissionResourceClass: ChangePermissionResourceClassApiRequestADM,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM] = {
    val permissionIriInternal =
      stringFormatter.toSmartIri(permissionIri.value).toOntologySchema(InternalSchema).toString
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

  def updatePermissionProperty(
    permissionIri: PermissionIri,
    changePermissionPropertyRequest: ChangePermissionPropertyApiRequestADM,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[PermissionGetResponseADM] = {
    val permissionIriInternal =
      stringFormatter.toSmartIri(permissionIri.value).toOntologySchema(InternalSchema).toString
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
             KnoraSystemInstances.Users.SystemUser,
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
             KnoraSystemInstances.Users.SystemUser,
             UUID.randomUUID(),
           )
    } yield ()

  /**
   * Gets the default permissions for a new value.
   *
   * @param projectIri       the IRI of the project of the containing resource.
   * @param resourceClassIri the internal IRI of the resource class.
   * @param propertyIri      the internal IRI of the property that points to the value.
   * @param targetUser the user that is creating the value.
   * @return a permission string.
   */
  def getDefaultValuePermissions(
    projectIri: IRI,
    resourceClassIri: SmartIri,
    propertyIri: SmartIri,
    targetUser: User,
  ): Task[String] =
    for {
      _ <- ZIO.getOrFailWith(BadRequestException(s"Invalid project IRI $projectIri"))(
             ProjectIri.from(projectIri).toOption,
           )

      _ <- ZIO.unless(resourceClassIri.isKnoraEntityIri) {
             ZIO.fail(BadRequestException(s"Invalid resource class IRI: $resourceClassIri"))
           }

      _ <- ZIO.unless(propertyIri.isKnoraEntityIri) {
             ZIO.fail(BadRequestException(s"Invalid property IRI: $propertyIri"))
           }

      _ <- ZIO.when(targetUser.isAnonymousUser) {
             ZIO.fail(BadRequestException("Anonymous Users are not allowed."))
           }

      permissionLiteral <- defaultObjectAccessPermissionsStringForEntityGetADM(
                             projectIri = projectIri,
                             resourceClassIri = resourceClassIri.toString,
                             propertyIri = Some(propertyIri.toString),
                             entityType = PropertyEntityType,
                             targetUser = targetUser,
                           ).map {
                             _.permissionLiteral
                           }
    } yield permissionLiteral
}

object PermissionsResponder {
  val layer = ZLayer.fromZIO {
    for {
      ac      <- ZIO.service[AppConfig]
      au      <- ZIO.service[AuthorizationRestService]
      gs      <- ZIO.service[GroupService]
      is      <- ZIO.service[IriService]
      kpr     <- ZIO.service[KnoraProjectService]
      mr      <- ZIO.service[MessageRelay]
      ts      <- ZIO.service[TriplestoreService]
      sf      <- ZIO.service[StringFormatter]
      aps     <- ZIO.service[AdministrativePermissionService]
      handler <- mr.subscribe(PermissionsResponder(ac, gs, is, kpr, mr, ts, au, aps)(sf))
    } yield handler
  }
}
