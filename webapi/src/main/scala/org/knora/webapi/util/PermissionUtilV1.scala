/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.util

import org.knora.webapi.messages.v1.responder.ontologymessages.{EntityInfoV1, PropertyEntityInfoV1, ResourceEntityInfoV1}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.responders.v1.GroupedProps.{ValueLiterals, ValueProps}
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}

/**
  * A utility that responder actors use to determine a user's permissions on an RDF subject in the triplestore.
  */
object PermissionUtilV1 {

    /**
      * A [[Map]] of Knora permission abbreviations to their API v1 codes.
      */
    val permissionsToV1PermissionCodes = new ErrorHandlingMap(Map(
        OntologyConstants.KnoraBase.RestrictedViewPermission -> 1,
        OntologyConstants.KnoraBase.ViewPermission -> 2,
        OntologyConstants.KnoraBase.ModifyPermission -> 6,
        OntologyConstants.KnoraBase.DeletePermission -> 7,
        OntologyConstants.KnoraBase.ChangeRightsPermission -> 8
    ), { key: IRI => s"Unknown permission: $key" })

    /**
      * The API v1 code of the highest permission.
      */
    private val MaxPermissionCode = permissionsToV1PermissionCodes(OntologyConstants.KnoraBase.MaxPermission)

    /**
      * A [[Map]] of API v1 permission codes to Knora permission abbreviations. Used for debugging.
      */
    private val v1PermissionCodesToPermissions = permissionsToV1PermissionCodes.map(_.swap)

    /**
      * A set of assertions that are relevant for calculating permissions.
      */
    private val permissionRelevantAssertions = Set(
        OntologyConstants.KnoraBase.AttachedToUser,
        OntologyConstants.KnoraBase.AttachedToProject,
        OntologyConstants.KnoraBase.HasPermissions
    )

    /**
      * A map of IRIs for default permissions to the corresponding permission abbreviations.
      */
    private val defaultPermissions2Permissions: Map[IRI, IRI] = Map(
        OntologyConstants.KnoraBase.HasDefaultRestrictedViewPermission -> OntologyConstants.KnoraBase.RestrictedViewPermission,
        OntologyConstants.KnoraBase.HasDefaultViewPermission -> OntologyConstants.KnoraBase.ViewPermission,
        OntologyConstants.KnoraBase.HasDefaultModifyPermission -> OntologyConstants.KnoraBase.ModifyPermission,
        OntologyConstants.KnoraBase.HasDefaultDeletePermission -> OntologyConstants.KnoraBase.DeletePermission,
        OntologyConstants.KnoraBase.HasDefaultChangeRightsPermission -> OntologyConstants.KnoraBase.ChangeRightsPermission
    )

    /**
      * Checks whether a Knora API v1 integer permission code implies a particular permission property.
      *
      * @param userHasPermissionCode the Knora API v1 integer permission code that the user has, or [[None]] if the user has no permissions
      *                              (in which case this method returns `false`).
      * @param userNeedsPermission   the abbreviation of the permission that the user needs.
      * @return `true` if the user has the needed permission.
      */
    def impliesV1(userHasPermissionCode: Option[Int], userNeedsPermission: IRI): Boolean = {
        userHasPermissionCode match {
            case Some(permissionCode) => permissionCode.toInt >= permissionsToV1PermissionCodes(userNeedsPermission)
            case None => false
        }
    }

    /**
      * Determines the permissions that a user has on a `knora-base:Value`, and returns a permissions code in Knora API v1 format.
      *
      * @param valueProps  a [[ValueProps]] containing the permission-relevant predicates and objects
      *                    pertaining to the value, grouped by predicate. The predicates must include
      *                    [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToUser]], and should include
      *                    [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToProject]]
      *                    and [[org.knora.webapi.OntologyConstants.KnoraBase.HasPermissions]]. Other predicates may be
      *                    included, but they will be ignored, so there is no need to filter them before passing them to
      *                    this function.
      * @param userProfile the profile of the user making the request.
      * @return a code representing the user's permission level on the value.
      */
    def getUserPermissionV1WithValueProps(subjectIri: IRI,
                                          valueProps: ValueProps,
                                          userProfile: UserProfileV1): Option[Int] = {
        getUserPermissionV1FromAssertions(
            subjectIri = subjectIri,
            assertions = filterPermissionRelevantAssertionsFromValueProps(valueProps),
            userProfile = userProfile
        )
    }

    /**
      * Given the IRI of an RDF property, returns `true` if the property is relevant to calculating permissions. This
      * is the case if the property is [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToUser]],
      * [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToProject]], or
      * or [[org.knora.webapi.OntologyConstants.KnoraBase.HasPermissions]].
      *
      * @param p the IRI of the property.
      * @return `true` if the property is relevant to calculating permissions.
      */
    def isPermissionRelevant(p: IRI): Boolean = permissionRelevantAssertions.contains(p)

    /**
      * Given a list of predicates and objects pertaining to a subject, returns only the ones that are relevant to
      * permissions (i.e. the permissions themselves, plus the owner and project).
      *
      * @param assertions a list containing the permission-relevant predicates and objects
      *                   pertaining to the subject. Other predicates will be filtered out.
      * @return a list of permission-relevant predicates and objects.
      */
    def filterPermissionRelevantAssertions(assertions: Seq[(IRI, IRI)]): Seq[(IRI, IRI)] = {
        assertions.filter {
            case (p, o) => isPermissionRelevant(p)
        }.toVector
    }

    /**
      * Given a [[ValueProps]] describing a `knora-base:Value`, returns the permission-relevant assertions contained
      * in the [[ValueProps]] (i.e. permission assertion, plus assertions about the subject's owner and project).
      *
      * @param valueProps a [[ValueProps]] describing a `knora-base:Value`.
      * @return a list of permission-relevant predicates and objects.
      */
    def filterPermissionRelevantAssertionsFromValueProps(valueProps: ValueProps): Seq[(IRI, IRI)] = {
        valueProps.literalData.foldLeft(Vector.empty[(IRI, IRI)]) {
            case (acc, (predicate: IRI, ValueLiterals(literals))) =>
                if (isPermissionRelevant(predicate)) {
                    acc ++ literals.map(literal => (predicate, literal))
                } else {
                    acc
                }
        }
    }

    /**
      * Given an [[EntityInfoV1]], gets the entity's default permissions and converts them to permissions that
      * can be assigned to an instance of the entity. An [[EntityInfoV1]] is either a [[ResourceEntityInfoV1]] or a [[PropertyEntityInfoV1]].
      *
      * @param entityInfo an [[EntityInfoV1]] describing an ontology entity.
      * @return a formatted string literal that can be used as the object of the predicate `knora-base:hasPermissions`.
      */
    def makePermissionsFromEntityDefaults(entityInfo: EntityInfoV1): Option[String] = {
        // Convert the entity's default permissions to permission abbreviations.
        val permissions: Map[IRI, Set[String]] = entityInfo.predicates.filterKeys {
            key => OntologyConstants.KnoraBase.DefaultPermissionProperties(key)
        }.map {
            case (predicateIri, predicateInfo) =>
                (defaultPermissions2Permissions(predicateIri), predicateInfo.objects)
        }

        // Format them as a string literal.
        formatPermissions(permissions)
    }

    /**
      * Determines the permissions that a user has on a subject, and returns a permissions code in Knora API v1 format.
      *
      * @param subjectIri               the IRI of the subject.
      * @param subjectOwner             the IRI of the user that owns the subject.
      * @param subjectProject           the IRI of the subject's project.
      * @param subjectPermissionLiteral the literal that is the object of the subject's `knora-base:hasPermissions` predicate.
      * @param userProfile              the profile of the user making the request.
      * @return a code representing the user's permission level for the subject.
      */
    def getUserPermissionV1(subjectIri: IRI,
                            subjectOwner: IRI,
                            subjectProject: IRI,
                            subjectPermissionLiteral: Option[String],
                            userProfile: UserProfileV1): Option[Int] = {
        /**
          * Calculates the highest permission a user can be granted on a subject.
          *
          * @param subjectPermissions tuples of permissions on a subject and the groups they are granted to.
          * @param userGroups         the groups that the user belongs to.
          * @return the code of the highest permission the user has on the subject, or `None` if the user has no permissions
          *         on the subject.
          */
        def calculateHighestGrantedPermission(subjectPermissions: Map[String, Set[IRI]], userGroups: Seq[IRI]): Option[Int] = {
            // Make a list of the codes for all the permissions the user can obtain for this subject.
            val permissionCodes = subjectPermissions.flatMap {
                case (permission, grantedToGroups) =>
                    grantedToGroups.foldLeft(Vector.empty[Int]) {
                        case (acc, grantedToGroup) =>
                            if (userGroups.contains(grantedToGroup)) {
                                permissionsToV1PermissionCodes(permission) +: acc
                            } else {
                                acc
                            }
                    }
            }

            if (permissionCodes.nonEmpty) {
                // The user has some permissions; return the code of the highest one.
                Some(permissionCodes.max)
            } else {
                // The user has no permissions.
                None
            }
        }

        val subjectPermissions: Map[String, Set[IRI]] = parsePermissions(subjectPermissionLiteral)

        // Make a list of all the groups (both built-in and custom) that the user belongs to in relation
        // to the subject.
        val userGroups: Seq[IRI] = userProfile.userData.user_id match {
            case Some(userIri) =>
                // The user is a known user.
                // If the user owns the subject, put the user in the "owner" built-in group.
                val ownerOption = if (userIri == subjectOwner) {
                    Some(OntologyConstants.KnoraBase.Owner)
                } else {
                    None
                }

                // If the user is a member of the project that the subject belongs to, put the user
                // in the "projectMember" built-in group.
                val projectMemberOption = if (userProfile.projects.contains(subjectProject)) {
                    Some(OntologyConstants.KnoraBase.ProjectMember)
                } else {
                    None
                }

                // Make the complete list of the user's groups: the built-in "knownUser" group, plus the user's
                // non-built-in groups, and possibly the "owner" and "projectMember" groups.
                Vector(OntologyConstants.KnoraBase.KnownUser) ++
                    userProfile.groups ++ ownerOption ++ projectMemberOption
            case None =>
                // The user is an unknown user; put them in the "unknownUser" built-in group.
                Vector(OntologyConstants.KnoraBase.UnknownUser)
        }

        // println(s"User groups: ${ScalaPrettyPrinter.prettyPrint(userGroups)}")

        // If the user is in the "owner" group, don't bother calculating permissions, just give them the maximum
        // permission.
        val permissionCodeOption = if (userGroups.contains(OntologyConstants.KnoraBase.Owner)) {
            Some(MaxPermissionCode)
        } else {
            // Find the highest permission that can be granted to the user.
            calculateHighestGrantedPermission(subjectPermissions, userGroups) match {
                case Some(highestPermissionCode) => Some(highestPermissionCode)
                case None =>
                    // If the result is that they would get no permissions, give them user whatever permission an
                    // unknown user would have.
                    calculateHighestGrantedPermission(subjectPermissions, Vector(OntologyConstants.KnoraBase.UnknownUser))
            }
        }

        /*
        println(s"User permission: ${permissionCodeOption.map(code => v1PermissionCodesToPermissions(code.toInt))}")
        println(s"User permission code: $permissionCodeOption")
        */

        permissionCodeOption
    }

    /**
      * Determines the permissions that a user has on a subject, and returns a permissions code in Knora API v1 format.
      *
      * @param subjectIri  the IRI of the subject.
      * @param assertions  a [[Seq]] containing all the permission-relevant predicates and objects
      *                    pertaining to the subject. The predicates must include
      *                    [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToUser]], and should include
      *                    [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToProject]]
      *                    and [[org.knora.webapi.OntologyConstants.KnoraBase.HasPermissions]].
      *                    Other predicates may be included, but they will be ignored, so there is no need to filter
      *                    them before passing them to this function.
      * @param userProfile the profile of the user making the request.
      * @return a code representing the user's permission level for the subject.
      */
    def getUserPermissionV1FromAssertions(subjectIri: IRI,
                                          assertions: Seq[(IRI, String)],
                                          userProfile: UserProfileV1): Option[Int] = {
        // Get the subject's owner, project, and permissions.
        val assertionMap: Map[IRI, String] = assertions.toMap

        // Anything with permissions must have an owner and a project.
        val subjectOwner: IRI = assertionMap.getOrElse(OntologyConstants.KnoraBase.AttachedToUser, throw InconsistentTriplestoreDataException(s"Subject $subjectIri has no owner"))
        val subjectProject: IRI = assertionMap.getOrElse(OntologyConstants.KnoraBase.AttachedToProject, throw InconsistentTriplestoreDataException(s"Subject $subjectIri has no project"))
        val subjectPermissionLiteral: Option[String] = assertionMap.get(OntologyConstants.KnoraBase.HasPermissions)

        getUserPermissionV1(
            subjectIri = subjectIri,
            subjectOwner = subjectOwner,
            subjectProject = subjectProject,
            subjectPermissionLiteral = subjectPermissionLiteral,
            userProfile = userProfile
        )
    }

    /**
      * Determines the permissions that a user has on a `knora-base:LinkValue`, and returns a permissions code in Knora API v1 format.
      * If the `rdf:predicate` of the `LinkValue` is [[OntologyConstants.KnoraBase.HasStandoffLinkTo]], this method always returns
      * view permission. Otherwise, it returns the value returned by [[getUserPermissionV1WithValueProps]].
      *
      * @param linkValueIri the IRI of the `LinkValue`.
      * @param predicateIri the `rdf:predicate` of the `LinkValue`.
      * @param valueProps   a [[ValueProps]] containing the permission-relevant predicates and objects
      *                     pertaining to the value, grouped by predicate.
      *                     Other predicates may be included, but they will be ignored, so there is no need to filter
      *                     them before passing them to this function.
      * @param userProfile  the profile of the user making the request.
      * @return a code representing the user's permission level on the value.
      */
    def getUserPermissionOnLinkValueV1WithValueProps(linkValueIri: IRI,
                                                     predicateIri: IRI,
                                                     valueProps: ValueProps,
                                                     userProfile: UserProfileV1): Option[Int] = {
        if (predicateIri == OntologyConstants.KnoraBase.HasStandoffLinkTo) {
            Some(permissionsToV1PermissionCodes(OntologyConstants.KnoraBase.ViewPermission))
        } else {
            getUserPermissionV1WithValueProps(
                subjectIri = linkValueIri,
                valueProps = valueProps,
                userProfile = userProfile
            )
        }
    }

    /**
      * Determines the permissions that a user has on a `knora-base:LinkValue`, and returns a permissions code in Knora API v1 format.
      * If the `rdf:predicate` of the `LinkValue` is [[OntologyConstants.KnoraBase.HasStandoffLinkTo]], this method always returns
      * view permission. Otherwise, it returns the value returned by [[getUserPermissionV1WithValueProps]].
      *
      * @param linkValueIri               the IRI of the link value.
      * @param predicateIri               the IRI of the link value's `rdf:predicate`.
      * @param linkValueOwner             the IRI of the link value's owner.
      * @param linkValueProject           the IRI of the link value's project.
      * @param linkValuePermissionLiteral the literal object of the link value's `knora-base:hasPermissions` predicate.
      * @param userProfile                the profile of the user making the request.
      * @return a code representing the user's permission level on the value.
      */
    def getUserPermissionOnLinkValueV1(linkValueIri: IRI,
                                       predicateIri: IRI,
                                       linkValueOwner: IRI,
                                       linkValueProject: IRI,
                                       linkValuePermissionLiteral: Option[String],
                                       userProfile: UserProfileV1): Option[Int] = {
        if (predicateIri == OntologyConstants.KnoraBase.HasStandoffLinkTo) {
            Some(permissionsToV1PermissionCodes(OntologyConstants.KnoraBase.ViewPermission))
        } else {
            getUserPermissionV1(
                subjectIri = linkValueIri,
                subjectOwner = linkValueOwner,
                subjectProject = linkValueProject,
                subjectPermissionLiteral = linkValuePermissionLiteral,
                userProfile = userProfile
            )
        }
    }

    /**
      * Parses the literal object of the predicate `knora-base:hasPermissions`.
      *
      * @param maybePermissionListStr the literal to parse.
      * @return a [[Map]] in which the keys are permission abbreviations in
      *         [[OntologyConstants.KnoraBase.ObjectAccessPermissionAbbreviations]], and the values are sets of
      *         user group IRIs.
      */
    def parsePermissions(maybePermissionListStr: Option[String]): Map[String, Set[IRI]] = {
        maybePermissionListStr match {
            case Some(permissionListStr) =>
                val permissions: Seq[String] = permissionListStr.split(OntologyConstants.KnoraBase.PermissionListDelimiter)

                permissions.map {
                    permission =>
                        val splitPermission = permission.split(' ')
                        val abbreviation = splitPermission(0)

                        if (!OntologyConstants.KnoraBase.ObjectAccessPermissionAbbreviations.contains(abbreviation)) {
                            throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                        }

                        val shortGroups = splitPermission(1).split(OntologyConstants.KnoraBase.GroupListDelimiter).toSet
                        val groups = shortGroups.map(_.replace(OntologyConstants.KnoraBase.KnoraBasePrefix, OntologyConstants.KnoraBase.KnoraBasePrefixExpansion))

                        (abbreviation, groups)
                }.toMap

            case None => Map.empty[String, Set[IRI]]
        }
    }

    /**
      * Formats the literal object of the predicate `knora-base:hasPermissions`.
      *
      * @param permissions a [[Map]] in which the keys are permission abbreviations in
      *                    [[OntologyConstants.KnoraBase.ObjectAccessPermissionAbbreviations]], and the values are sets of
      *                    user group IRIs.
      * @return a formatted string literal that can be used as the object of the predicate `knora-base:hasPermissions`.
      */
    def formatPermissions(permissions: Map[String, Set[IRI]]): Option[String] = {
        if (permissions.nonEmpty) {
            val permissionsLiteral = new StringBuilder

            val currentPermissionsSorted = permissions.toVector.sortBy {
                case (abbreviation, groups) => permissionsToV1PermissionCodes(abbreviation)
            }

            for ((abbreviation, groups) <- currentPermissionsSorted) {
                if (!OntologyConstants.KnoraBase.ObjectAccessPermissionAbbreviations.contains(abbreviation)) {
                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                }

                if (permissionsLiteral.nonEmpty) {
                    permissionsLiteral.append(OntologyConstants.KnoraBase.PermissionListDelimiter)
                }

                permissionsLiteral.append(abbreviation).append(" ")
                val shortGroups = groups.map(_.replace(OntologyConstants.KnoraBase.KnoraBasePrefixExpansion, OntologyConstants.KnoraBase.KnoraBasePrefix))
                val delimitedGroups = shortGroups.toVector.mkString(OntologyConstants.KnoraBase.GroupListDelimiter.toString)
                permissionsLiteral.append(delimitedGroups)
            }

            Some(permissionsLiteral.toString)
        } else {
            None
        }
    }
}
