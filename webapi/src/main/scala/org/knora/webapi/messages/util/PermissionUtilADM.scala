/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import com.typesafe.scalalogging.LazyLogging
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import dsp.valueobjects.Iri
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupGetResponseADM
import org.knora.webapi.messages.admin.responder.groupsmessages.MultipleGroupsGetRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType
import org.knora.webapi.messages.store.triplestoremessages.LiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.util.PermissionUtilADM.formatPermissionADMs
import org.knora.webapi.messages.util.PermissionUtilADM.parsePermissions
import org.knora.webapi.slice.admin.domain.model.AdministrativePermission
import org.knora.webapi.slice.admin.domain.model.ObjectAccessPermission
import org.knora.webapi.slice.admin.domain.model.ObjectAccessPermissions
import org.knora.webapi.slice.admin.domain.model.User

/**
 * A utility that responder actors use to determine a user's permissions on an RDF entity in the triplestore.
 */
object PermissionUtilADM extends LazyLogging {

  /**
   * Calculates the highest permission level a user can be granted on a entity.
   *
   * @param entityPermissions a map of permissions on a entity to the groups they are granted to.
   * @param userGroups        the groups that the user belongs to.
   * @return the code of the highest permission the user has on the entity, or `None` if the user has no permissions
   *         on the entity.
   */
  private def calculateHighestGrantedPermissionLevel(
    entityPermissions: Map[ObjectAccessPermission, Set[IRI]],
    userGroups: Set[IRI],
  ): Option[ObjectAccessPermission] = {
    // Make a set of all the permissions the user can obtain for this entity.
    val permissionLevels: Set[ObjectAccessPermission] = entityPermissions.foldLeft(Set.empty[ObjectAccessPermission]) {
      case (acc, (permissionLevel, grantedToGroups)) =>
        if (grantedToGroups.intersect(userGroups).nonEmpty) {
          acc + permissionLevel
        } else {
          acc
        }
    }

    if (permissionLevels.nonEmpty) {
      // The user has some permissions; return the code of the highest one.
      Some(permissionLevels.max)
    } else {
      // The user has no permissions.
      None
    }
  }

  /**
   * Determines the permissions that a user has on a entity, and returns an [[ObjectAccessPermission]].
   *
   * @param entityCreator           the IRI of the user that created the entity.
   * @param entityProject           the IRI of the entity's project.
   * @param entityPermissionLiteral the literal that is the object of the entity's `knora-base:hasPermissions` predicate.
   * @param requestingUser          the user making the request.
   * @return an [[ObjectAccessPermission]] representing the user's permission level for the entity, or `None` if the user
   *         has no permissions on the entity.
   */
  def getUserPermissionADM(
    entityCreator: IRI,
    entityProject: IRI,
    entityPermissionLiteral: String,
    requestingUser: User,
  ): Option[ObjectAccessPermission] = {
    val maybePermissionLevel =
      if (
        requestingUser.isSystemUser || requestingUser.isSystemAdmin || requestingUser.permissions
          .hasProjectAdminAllPermissionFor(entityProject)
      ) {
        // If the user is the system user, is in the SystemAdmin group, or has ProjectAdminAllPermission, just give them the maximum permission.
        Some(ObjectAccessPermission.maxPermission)
      } else {
        val entityPermissions: Map[ObjectAccessPermission, Set[IRI]] = parsePermissions(entityPermissionLiteral)

        // Make a list of all the groups (both built-in and custom) that the user belongs to in relation
        // to the entity.
        val userGroups: Set[IRI] = if (requestingUser.isAnonymousUser) {
          // The user is an unknown user; put them in the UnknownUser built-in group.
          Set(OntologyConstants.KnoraAdmin.UnknownUser)
        } else {
          // The user is a known user.
          // If the user is the creator of the entity, put the user in the "creator" built-in group.
          val creatorOption = if (requestingUser.id == entityCreator) {
            Some(OntologyConstants.KnoraAdmin.Creator)
          } else {
            None
          }

          val otherGroups = requestingUser.permissions.groupsPerProject.get(entityProject) match {
            case Some(groups) => groups
            case None         => Set.empty[IRI]
          }

          // Make the complete list of the user's groups: KnownUser, the user's built-in (e.g., ProjectAdmin,
          // ProjectMember) and non-built-in groups, possibly creator, and possibly SystemAdmin.
          Set(OntologyConstants.KnoraAdmin.KnownUser) ++ otherGroups ++ creatorOption
        }

        // Find the highest permission that can be granted to the user.
        calculateHighestGrantedPermissionLevel(entityPermissions, userGroups) match {
          case Some(highestPermissionLevel) => Some(highestPermissionLevel)

          case None =>
            // If the result is that they would get no permissions, give them user whatever permission an
            // unknown user would have.
            calculateHighestGrantedPermissionLevel(entityPermissions, Set(OntologyConstants.KnoraAdmin.UnknownUser))
        }
      }

    maybePermissionLevel
  }

  /**
   * A trait representing a result returned by [[comparePermissionsADM]].
   */
  sealed trait PermissionComparisonResult

  /**
   * Indicates that the user would have a lower permission with permission string A.
   */
  case object ALessThanB extends PermissionComparisonResult

  /**
   * Indicates that permission strings A and B would give the user the same permission.
   */
  case object AEqualToB extends PermissionComparisonResult

  /**
   * Indicates that the user would have a higher permission with permission string A.
   */
  case object AGreaterThanB extends PermissionComparisonResult

  /**
   * Calculates the permissions that the specified user would have on an entity with two permission strings,
   * and returns:
   *
   * - [[ALessThanB]] if the user would have a lower permission with `permissionLiteralA`.
   * - [[AEqualToB]] if `permissionLiteralA` and `permissionLiteralB` would give the user the same permission.
   * - [[AGreaterThanB]] if the user would have a higher permission with `permissionLiteralA`.
   *
   * @param entityProject      the IRI of the entity's project.
   * @param permissionLiteralA the first permission string.
   * @param permissionLiteralB the second permission string.
   * @param requestingUser     the user making the request.
   * @return a [[PermissionComparisonResult]].
   */
  def comparePermissionsADM(
    entityProject: IRI,
    permissionLiteralA: String,
    permissionLiteralB: String,
    requestingUser: User,
  ): PermissionComparisonResult = {
    val maybePermissionA: Option[ObjectAccessPermission] = getUserPermissionADM(
      entityCreator = requestingUser.id,
      entityProject = entityProject,
      entityPermissionLiteral = permissionLiteralA,
      requestingUser = requestingUser,
    )

    val maybePermissionB: Option[ObjectAccessPermission] = getUserPermissionADM(
      entityCreator = requestingUser.id,
      entityProject = entityProject,
      entityPermissionLiteral = permissionLiteralB,
      requestingUser = requestingUser,
    )

    (maybePermissionA, maybePermissionB) match {
      case (None, None)    => AEqualToB
      case (None, Some(_)) => ALessThanB
      case (Some(_), None) => AGreaterThanB

      case (Some(permissionA: ObjectAccessPermission), Some(permissionB: ObjectAccessPermission)) =>
        if (permissionA == permissionB) {
          AEqualToB
        } else if (permissionA < permissionB) {
          ALessThanB
        } else {
          AGreaterThanB
        }
    }
  }

  /**
   * Given data from a [[org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse]], determines the permissions that a user has on a entity,
   * and returns an [[ObjectAccessPermission]].
   *
   * @param entityIri      the IRI of the entity.
   * @param assertions     a [[Seq]] containing all the permission-relevant predicates and objects
   *                       pertaining to the entity. The predicates must include
   *                       [[OntologyConstants.KnoraBase.AttachedToUser]] and
   *                       [[OntologyConstants.KnoraBase.AttachedToProject]], and should include
   *                       [[OntologyConstants.KnoraBase.HasPermissions]].
   *                       Other predicates may be included, but they will be ignored, so there is no need to filter
   *                       them before passing them to this function.
   * @param requestingUser the profile of the user making the request.
   * @return a code representing the user's permission level for the entity.
   */
  def getUserPermissionFromConstructAssertionsADM(
    entityIri: IRI,
    assertions: ConstructPredicateObjects,
    requestingUser: User,
  ): Option[ObjectAccessPermission] = {
    val assertionsAsStrings: Seq[(IRI, String)] = assertions.toSeq.flatMap {
      case (pred: SmartIri, objs: Seq[LiteralV2]) =>
        objs.map { obj =>
          pred.toString -> obj.toString
        }
    }

    getUserPermissionFromAssertionsADM(
      entityIri = entityIri,
      assertions = assertionsAsStrings,
      requestingUser = requestingUser,
    )
  }

  /**
   * Determines the permissions that a user has on a entity, and returns an [[ObjectAccessPermission]].
   *
   * @param entityIri      the IRI of the entity.
   * @param assertions     a [[Seq]] containing all the permission-relevant predicates and objects
   *                       pertaining to the entity. The predicates must include
   *                       [[OntologyConstants.KnoraBase.AttachedToUser]] and
   *                       [[OntologyConstants.KnoraBase.AttachedToProject]], and should include
   *                       [[OntologyConstants.KnoraBase.HasPermissions]].
   *                       Other predicates may be included, but they will be ignored, so there is no need to filter
   *                       them before passing them to this function.
   * @param requestingUser the profile of the user making the request.
   * @return a code representing the user's permission level for the entity.
   */
  def getUserPermissionFromAssertionsADM(
    entityIri: IRI,
    assertions: Seq[(IRI, String)],
    requestingUser: User,
  ): Option[ObjectAccessPermission] = {
    // Get the entity's creator, project, and permissions.
    val assertionMap: Map[IRI, String] = assertions.toMap

    // Anything with permissions must have an creator and a project.
    val entityCreator: IRI = assertionMap.getOrElse(
      OntologyConstants.KnoraBase.AttachedToUser,
      throw InconsistentRepositoryDataException(s"Entity $entityIri has no creator"),
    )
    val entityProject: IRI = assertionMap.getOrElse(
      OntologyConstants.KnoraBase.AttachedToProject,
      throw InconsistentRepositoryDataException(s"Entity $entityIri has no project"),
    )
    val entityPermissionLiteral: String = assertionMap.getOrElse(
      OntologyConstants.KnoraBase.HasPermissions,
      throw InconsistentRepositoryDataException(s"Entity $entityIri has no knora-base:hasPermissions predicate"),
    )

    getUserPermissionADM(
      entityCreator = entityCreator,
      entityProject = entityProject,
      entityPermissionLiteral = entityPermissionLiteral,
      requestingUser = requestingUser,
    )
  }

  /**
   * Parses the literal object of the predicate `knora-base:hasPermissions`.
   *
   * @param permissionLiteral the literal to parse.
   * @return a [[Map]] in which the keys are permission tokens, and the values are sets of user group IRIs.
   */
  def parsePermissions(
    permissionLiteral: String,
    errorFun: String => Nothing = { (permissionLiteral: String) =>
      throw InconsistentRepositoryDataException(s"invalid permission literal: $permissionLiteral")
    },
  ): Map[ObjectAccessPermission, Set[IRI]] = {
    val permissions: Seq[String] =
      permissionLiteral.split(OntologyConstants.KnoraBase.PermissionListDelimiter).toIndexedSeq

    permissions.map { permission =>
      val splitPermission: Array[String] = permission.split(' ')

      if (splitPermission.length != 2) {
        errorFun(permissionLiteral)
      }

      val abbreviation: String = splitPermission(0)
      val perm = ObjectAccessPermission
        .fromToken(abbreviation)
        .getOrElse(errorFun(permissionLiteral))

      val shortGroups: Set[String] = splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter).toSet
      val groups = shortGroups.map(
        _.replace(OntologyConstants.KnoraAdmin.KnoraAdminPrefix, OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion),
      )
      (perm, groups)
    }.toMap
  }

  /**
   * Parses the literal object of the predicate `knora-base:hasPermissions`.
   *
   * @param maybePermissionListStr the literal to parse.
   * @return a [[Map]] in which the keys are permission tokens, and the values are sets of
   *         user group IRIs.
   */
  def parsePermissionsWithType(
    maybePermissionListStr: Option[String],
    permissionType: PermissionType,
  ): Set[PermissionADM] =
    maybePermissionListStr match {
      case Some(permissionListStr) =>
        {
          val cleanedPermissionListStr = permissionListStr.replaceAll("[<>]", "")
          val permissions: Seq[String] =
            cleanedPermissionListStr.split(OntologyConstants.KnoraBase.PermissionListDelimiter).toIndexedSeq
          logger.debug(s"PermissionUtil.parsePermissionsWithType - split permissions: $permissions")
          permissions.flatMap { permission =>
            val splitPermission = permission.split(' ')
            val abbreviation    = splitPermission(0)

            permissionType match {
              case PermissionType.AP =>
                if (AdministrativePermission.fromToken(abbreviation).isEmpty) {
                  throw InconsistentRepositoryDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                }

                if (splitPermission.length > 1) {
                  val shortGroups: Array[String] =
                    splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter)
                  val groups: Set[IRI] = shortGroups
                    .map(
                      _.replace(
                        OntologyConstants.KnoraAdmin.KnoraAdminPrefix,
                        OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion,
                      ),
                    )
                    .toSet
                  buildPermissionObject(abbreviation, groups)
                } else {
                  buildPermissionObject(abbreviation, Set.empty[IRI])
                }

              case PermissionType.OAP =>
                if (!ObjectAccessPermissions.allTokens.contains(abbreviation)) {
                  throw InconsistentRepositoryDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                }
                val shortGroups: Array[String] =
                  splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter)
                val groups: Set[IRI] = shortGroups
                  .map(
                    _.replace(
                      OntologyConstants.KnoraAdmin.KnoraAdminPrefix,
                      OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion,
                    ),
                  )
                  .toSet
                buildPermissionObject(abbreviation, groups)
              case PermissionType.DOAP => ???
            }
          }
        }.toSet
      case None => Set.empty[PermissionADM]
    }

  /**
   * Helper method used to convert the permission string stored inside the triplestore to a permission object.
   *
   * @param name the name of the permission.
   * @param iris the optional set of additional information (e.g., group IRIs, resource class IRIs).
   * @return a sequence of permission objects.
   */
  def buildPermissionObject(name: String, iris: Set[IRI]): Set[PermissionADM] =
    name match {
      case AdministrativePermission.ProjectResourceCreateAll.token =>
        Set(PermissionADM.from(AdministrativePermission.ProjectResourceCreateAll))

      case AdministrativePermission.ProjectResourceCreateRestricted.token =>
        if (iris.nonEmpty) {
          logger.debug(s"buildPermissionObject - ProjectResourceCreateRestrictedPermission - iris: $iris")
          iris.map(iri => PermissionADM.from(AdministrativePermission.ProjectResourceCreateRestricted, iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }

      case AdministrativePermission.ProjectAdminAll.token =>
        Set(PermissionADM.from(AdministrativePermission.ProjectAdminAll))

      case AdministrativePermission.ProjectAdminGroupAll.token =>
        Set(PermissionADM.from(AdministrativePermission.ProjectAdminGroupAll))

      case AdministrativePermission.ProjectAdminGroupRestricted.token =>
        if (iris.nonEmpty) {
          iris.map(PermissionADM.from(AdministrativePermission.ProjectAdminGroupRestricted, _))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }

      case AdministrativePermission.ProjectAdminRightsAll.token =>
        Set(PermissionADM.from(AdministrativePermission.ProjectAdminRightsAll))

      case ObjectAccessPermission.ChangeRights.token =>
        if (iris.nonEmpty) {
          iris.map(iri => PermissionADM.from(ObjectAccessPermission.ChangeRights, iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }

      case ObjectAccessPermission.Delete.token =>
        if (iris.nonEmpty) {
          iris.map(iri => PermissionADM.from(ObjectAccessPermission.Delete, iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }

      case ObjectAccessPermission.Modify.token =>
        if (iris.nonEmpty) {
          iris.map(iri => PermissionADM.from(ObjectAccessPermission.Modify, iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }

      case ObjectAccessPermission.View.token =>
        if (iris.nonEmpty) {
          iris.map(iri => PermissionADM.from(ObjectAccessPermission.View, iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }

      case ObjectAccessPermission.RestrictedView.token =>
        if (iris.nonEmpty) {
          iris.map(iri => PermissionADM.from(ObjectAccessPermission.RestrictedView, iri))
        } else {
          throw InconsistentRepositoryDataException(s"Missing additional permission information.")
        }
    }

  /**
   * Helper method used to remove remove duplicate permissions.
   *
   * @param permissions the sequence of permissions with possible duplicates.
   * @return a set containing only unique permission.
   */
  def removeDuplicatePermissions(permissions: Seq[PermissionADM]): Set[PermissionADM] =
    permissions.groupBy(perm => perm.name + perm.additionalInformation).map { case (_, v) => v.head }.toSet

  /**
   * Helper method used to transform a set of permissions into a permissions string ready to be written into the
   * triplestore as the value for the 'knora-base:hasPermissions' property.
   *
   * @param permissions    the permissions to be formatted.
   * @param permissionType a [[PermissionType]] indicating the type of permissions to be formatted.
   * @return
   */
  def formatPermissionADMs(permissions: Set[PermissionADM], permissionType: PermissionType): String =
    permissionType match {
      case PermissionType.OAP =>
        if (permissions.nonEmpty) {

          /* a levelsByToken with permission names, shortened groups, and full group names. */
          val groupedPermissions: Map[String, String] =
            permissions.groupBy(_.name).map { case (name: String, perms: Set[PermissionADM]) =>
              val shortGroupsString = perms.toVector.sortBy(_.additionalInformation.get).foldLeft("") {
                case (acc: String, perm: PermissionADM) =>
                  if (acc.isEmpty) {
                    acc + perm.additionalInformation.get.replace(
                      OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion,
                      OntologyConstants.KnoraAdmin.KnoraAdminPrefix,
                    )
                  } else {
                    acc + OntologyConstants.KnoraBase.GroupListDelimiter + perm.additionalInformation.get.replace(
                      OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion,
                      OntologyConstants.KnoraAdmin.KnoraAdminPrefix,
                    )
                  }
              }
              (name, shortGroupsString)
            }

          /* Sort permissions in descending order */
          val sortedPermissions: Array[(String, String)] = groupedPermissions.toArray.sortWith { (left, right) =>
            ObjectAccessPermissions.codeByToken(left._1) > ObjectAccessPermissions.codeByToken(right._1)
          }

          /* create the permissions string */
          sortedPermissions.foldLeft("") { (acc, perm: (String, String)) =>
            if (acc.isEmpty) {
              acc + perm._1 + " " + perm._2
            } else {
              acc + OntologyConstants.KnoraBase.PermissionListDelimiter + perm._1 + " " + perm._2
            }
          }
        } else {
          throw InconsistentRepositoryDataException("Permissions cannot be empty")
        }
      case PermissionType.AP =>
        if (permissions.nonEmpty) {

          val permNames: Set[String] = permissions.map(_.name)

          /* creates the permissions string. something like "ProjectResourceCreateAllPermission|ProjectAdminAllPermission" */
          permNames.foldLeft("") { (acc, perm: String) =>
            if (acc.isEmpty) {
              acc + perm
            } else {
              acc + OntologyConstants.KnoraBase.PermissionListDelimiter + perm
            }
          }

        } else {
          throw InconsistentRepositoryDataException("Permissions cannot be empty")
        }
      case PermissionType.DOAP => ???
    }
}

trait PermissionUtilADM {

  /**
   * Given a permission literal, checks that it refers to valid permissions and groups.
   *
   * @param permissionLiteral the permission literal.
   * @return the validated permission literal, normalised and reformatted.
   */
  def validatePermissions(permissionLiteral: String): Task[String]
}

final case class PermissionUtilADMLive(messageRelay: MessageRelay, stringFormatter: StringFormatter)
    extends PermissionUtilADM {

  /**
   * Given a permission literal, checks that it refers to valid permissions and groups.
   *
   * @param permissionLiteral the permission literal.
   * @return the validated permission literal, normalised and reformatted.
   */
  override def validatePermissions(permissionLiteral: String): Task[String] =
    for {
      // Parse the permission literal.
      parsedPermissions <-
        ZIO.attempt(
          parsePermissions(
            permissionLiteral = permissionLiteral,
            errorFun = { literal =>
              throw BadRequestException(s"Invalid permission literal: $literal")
            },
          ),
        )

      // Get the group IRIs that are mentioned, minus the built-in groups.
      projectSpecificGroupIris: Set[IRI] =
        parsedPermissions.values.flatten.toSet -- OntologyConstants.KnoraAdmin.BuiltInGroups

      validatedProjectSpecificGroupIris <-
        ZIO.attempt(
          projectSpecificGroupIris.map(iri =>
            Iri.validateAndEscapeIri(iri).getOrElse(throw BadRequestException(s"Invalid group IRI: $iri")),
          ),
        )

      // Check that those groups exist.
      _ <- messageRelay.ask[Set[GroupGetResponseADM]](MultipleGroupsGetRequestADM(validatedProjectSpecificGroupIris))

      // Reformat the permission literal.
      permissionADMs: Set[PermissionADM] = parsedPermissions.flatMap { case (entityPermission, groupIris) =>
                                             groupIris.map(PermissionADM.from(entityPermission, _))
                                           }.toSet
    } yield formatPermissionADMs(permissions = permissionADMs, permissionType = PermissionType.OAP)
}

object PermissionUtilADMLive {
  val layer = ZLayer.derive[PermissionUtilADMLive]
}
