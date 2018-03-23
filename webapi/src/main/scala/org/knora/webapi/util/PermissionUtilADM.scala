/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import com.typesafe.scalalogging.Logger
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionType.PermissionType
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionADM, PermissionType}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.responders.v1.GroupedProps.{ValueLiterals, ValueProps}
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}
import org.slf4j.LoggerFactory

import scala.collection.immutable.Iterable

/**
  * A utility that responder actors use to determine a user's permissions on an RDF subject in the triplestore.
  */
object PermissionUtilADM {

    val log = Logger(LoggerFactory.getLogger(this.getClass))

    /**
      * A [[Map]] of Knora permission abbreviations to their API v1 codes.
      */
    val permissionsToV1PermissionCodes = new ErrorHandlingMap(Map(
        OntologyConstants.KnoraAdmin.RestrictedViewPermission -> 1,
        OntologyConstants.KnoraAdmin.ViewPermission -> 2,
        OntologyConstants.KnoraAdmin.ModifyPermission -> 6,
        OntologyConstants.KnoraAdmin.DeletePermission -> 7,
        OntologyConstants.KnoraAdmin.ChangeRightsPermission -> 8
    ), { key: IRI => s"Unknown permission: $key" })

    /**
      * The API v1 code of the highest permission.
      */
    private val MaxPermissionCode = permissionsToV1PermissionCodes(OntologyConstants.KnoraAdmin.MaxPermission)

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
        OntologyConstants.KnoraAdmin.HasDefaultRestrictedViewPermission -> OntologyConstants.KnoraAdmin.RestrictedViewPermission,
        OntologyConstants.KnoraAdmin.HasDefaultViewPermission -> OntologyConstants.KnoraAdmin.ViewPermission,
        OntologyConstants.KnoraAdmin.HasDefaultModifyPermission -> OntologyConstants.KnoraAdmin.ModifyPermission,
        OntologyConstants.KnoraAdmin.HasDefaultDeletePermission -> OntologyConstants.KnoraAdmin.DeletePermission,
        OntologyConstants.KnoraAdmin.HasDefaultChangeRightsPermission -> OntologyConstants.KnoraAdmin.ChangeRightsPermission
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
      * @param valueIri       the IRI of the `knora-base:Value`.
      * @param valueProps     a [[ValueProps]] containing the permission-relevant predicates and objects
      *                       pertaining to the value, grouped by predicate. The predicates must include
      *                       [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToUser]], and should include
      *                       [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToProject]]
      *                       and [[org.knora.webapi.OntologyConstants.KnoraBase.HasPermissions]]. Other predicates may be
      *                       included, but they will be ignored, so there is no need to filter them before passing them to
      *                       this function.
      * @param subjectProject if provided, the `knora-base:attachedToProject` of the resource containing the value. Otherwise,
      *                       this predicate must be in `valueProps`.
      * @param userProfile    the profile of the user making the request.
      * @return a code representing the user's permission level on the value.
      */
    def getUserPermissionV1WithValueProps(valueIri: IRI,
                                          valueProps: ValueProps,
                                          subjectProject: Option[IRI],
                                          userProfile: UserProfileV1): Option[Int] = {

        // Either subjectProject must be provided, or there must be a knora-base:attachedToProject in valueProps.

        val valuePropsAssertions: Vector[(IRI, IRI)] = filterPermissionRelevantAssertionsFromValueProps(valueProps)
        val valuePropsProject: Option[IRI] = valuePropsAssertions.find(_._1 == OntologyConstants.KnoraBase.AttachedToProject).map(_._2)
        val providedProjects = Vector(valuePropsProject, subjectProject).flatten.distinct

        if (providedProjects.isEmpty) {
            throw InconsistentTriplestoreDataException(s"No knora-base:attachedToProject was provided for subject $valueIri")
        }

        if (providedProjects.size > 1) {
            throw InconsistentTriplestoreDataException(s"Two different values of knora-base:attachedToProject were provided for subject $valueIri: ${valuePropsProject.get} and ${subjectProject.get}")
        }

        val valuePropsAssertionsWithoutProject: Vector[(IRI, IRI)] = valuePropsAssertions.filter(_._1 != OntologyConstants.KnoraBase.AttachedToProject)
        val projectAssertion: (IRI, IRI) = (OntologyConstants.KnoraBase.AttachedToProject, providedProjects.head)

        getUserPermissionV1FromAssertions(
            subjectIri = valueIri,
            assertions = valuePropsAssertionsWithoutProject :+ projectAssertion,
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
      * permissions (i.e. the permissions themselves, plus the creator and project).
      *
      * @param assertions a list containing the permission-relevant predicates and objects
      *                   pertaining to the subject. Other predicates will be filtered out.
      * @return a list of permission-relevant predicates and objects.
      */
    def filterPermissionRelevantAssertions(assertions: Seq[(IRI, IRI)]): Vector[(IRI, IRI)] = {
        assertions.filter {
            case (p, o) => isPermissionRelevant(p)
        }.toVector
    }

    /**
      * Given a [[ValueProps]] describing a `knora-base:Value`, returns the permission-relevant assertions contained
      * in the [[ValueProps]] (i.e. permission assertion, plus assertions about the subject's creator and project).
      *
      * @param valueProps a [[ValueProps]] describing a `knora-base:Value`.
      * @return a list of permission-relevant predicates and objects.
      */
    def filterPermissionRelevantAssertionsFromValueProps(valueProps: ValueProps): Vector[(IRI, IRI)] = {
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
      * Determines the permissions that a user has on a subject, and returns a permissions code in Knora API v1 format.
      *
      * @param subjectIri               the IRI of the subject.
      * @param subjectCreator           the IRI of the user that created the subject.
      * @param subjectProject           the IRI of the subject's project.
      * @param subjectPermissionLiteral the literal that is the object of the subject's `knora-base:hasPermissions` predicate.
      * @param userProfile              the profile of the user making the request.
      * @return a code representing the user's permission level for the subject.
      */
    def getUserPermissionV1(subjectIri: IRI,
                            subjectCreator: IRI,
                            subjectProject: IRI,
                            subjectPermissionLiteral: String,
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

            //log.debug(s"getUserPermissionV1 - calculateHighestGrantedPermission - subjectPermissions: ${ScalaPrettyPrinter.prettyPrint(subjectPermissions)}")

            // Make a list of the codes for all the permissions the user can obtain for this subject.
            val permissionCodes: Iterable[Int] = subjectPermissions.flatMap {
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
                log.debug(s"getUserPermissionV1 - calculateHighestGrantedPermission - permissionCodes: ${permissionCodes.toString}")
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
                // If the user is the creator of the subject, put the user in the "creator" built-in group.
                val creatorOption = if (userIri == subjectCreator) {
                    Some(OntologyConstants.KnoraAdmin.Creator)
                } else {
                    None
                }

                val systemAdminOption = if (userProfile.permissionData.isSystemAdmin) {
                    Some(OntologyConstants.KnoraAdmin.SystemAdmin)
                } else {
                    None
                }

                val otherGroups = userProfile.permissionData.groupsPerProject.get(subjectProject) match {
                    case Some(groups) => groups.toVector
                    case None => Vector.empty[IRI]
                }

                // Make the complete list of the user's groups: KnownUser, the user's built-in (e.g., ProjectAdmin,
                // ProjectMember) and non-built-in groups, possibly creator, and possibly SystemAdmin.
                Vector(OntologyConstants.KnoraAdmin.KnownUser) ++ otherGroups ++ creatorOption ++ systemAdminOption
            case None =>
                // The user is an unknown user; put them in the UnknownUser built-in group.
                Vector(OntologyConstants.KnoraAdmin.UnknownUser)
        }

        log.debug(s"getUserPermissionV1 - userGroups: $userGroups")

        val permissionCodeOption = if (userProfile.permissionData.isSystemAdmin) {
            // If the user is in the SystemAdmin group, just give them the maximum permission.
            //log.debug("getUserPermissionV1 - is in SystemAdmin group - giving max permission")
            Some(MaxPermissionCode)
        } else if (userProfile.permissionData.hasProjectAdminAllPermissionFor(subjectProject)) {
            // If the user has ProjectAdminAllPermission, just give them the maximum permission.
            //log.debug("getUserPermissionV1 - has 'ProjectAdminAllPermission' - giving max permission")
            Some(MaxPermissionCode)
        } else {
            // Find the highest permission that can be granted to the user.
            calculateHighestGrantedPermission(subjectPermissions, userGroups) match {
                case Some(highestPermissionCode) => Some(highestPermissionCode)
                case None =>
                    // If the result is that they would get no permissions, give them user whatever permission an
                    // unknown user would have.
                    calculateHighestGrantedPermission(subjectPermissions, Vector(OntologyConstants.KnoraAdmin.UnknownUser))
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
      *                    [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToUser]] and
      *                    [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToProject]], and should include
      *                    [[org.knora.webapi.OntologyConstants.KnoraBase.HasPermissions]].
      *                    Other predicates may be included, but they will be ignored, so there is no need to filter
      *                    them before passing them to this function.
      * @param userProfile the profile of the user making the request.
      * @return a code representing the user's permission level for the subject.
      */
    def getUserPermissionV1FromAssertions(subjectIri: IRI,
                                          assertions: Seq[(IRI, String)],
                                          userProfile: UserProfileV1): Option[Int] = {
        // Get the subject's creator, project, and permissions.
        val assertionMap: Map[IRI, String] = assertions.toMap

        // Anything with permissions must have an creator and a project.
        val subjectCreator: IRI = assertionMap.getOrElse(OntologyConstants.KnoraBase.AttachedToUser, throw InconsistentTriplestoreDataException(s"Subject $subjectIri has no creator"))
        val subjectProject: IRI = assertionMap.getOrElse(OntologyConstants.KnoraBase.AttachedToProject, throw InconsistentTriplestoreDataException(s"Subject $subjectIri has no project"))
        val subjectPermissionLiteral: String = assertionMap.getOrElse(OntologyConstants.KnoraBase.HasPermissions, throw InconsistentTriplestoreDataException(s"Subject $subjectIri has no knora-base:hasPermissions predicate"))

        getUserPermissionV1(subjectIri = subjectIri, subjectCreator = subjectCreator, subjectProject = subjectProject, subjectPermissionLiteral = subjectPermissionLiteral, userProfile = userProfile)
    }

    /**
      * Parses the literal object of the predicate `knora-base:hasPermissions`.
      *
      * @param permissionListStr the literal to parse.
      * @return a [[Map]] in which the keys are permission abbreviations in
      *         [[OntologyConstants.KnoraAdmin.ObjectAccessPermissionAbbreviations]], and the values are sets of
      *         user group IRIs.
      */
    def parsePermissions(permissionListStr: String): Map[String, Set[IRI]] = {
        val permissions: Seq[String] = permissionListStr.split(OntologyConstants.KnoraAdmin.PermissionListDelimiter)

        permissions.map {
            permission =>
                val splitPermission = permission.split(' ')
                val abbreviation = splitPermission(0)

                if (!OntologyConstants.KnoraAdmin.ObjectAccessPermissionAbbreviations.contains(abbreviation)) {
                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                }

                val shortGroups = splitPermission(1).split(OntologyConstants.KnoraAdmin.GroupListDelimiter).toSet
                val groups = shortGroups.map(_.replace(OntologyConstants.KnoraAdmin.KnoraAdminPrefix, OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion))

                (abbreviation, groups)
        }.toMap
    }


    /**
      * Parses the literal object of the predicate `knora-base:hasPermissions`.
      *
      * @param maybePermissionListStr the literal to parse.
      * @return a [[Map]] in which the keys are permission abbreviations in
      *         [[OntologyConstants.KnoraAdmin.ObjectAccessPermissionAbbreviations]], and the values are sets of
      *         user group IRIs.
      */
    def parsePermissionsWithType(maybePermissionListStr: Option[String], permissionType: PermissionType): Set[PermissionADM] = {
        maybePermissionListStr match {
            case Some(permissionListStr) => {
                val cleanedPermissionListStr = permissionListStr replaceAll("[<>]", "")
                val permissions: Seq[String] = cleanedPermissionListStr.split(OntologyConstants.KnoraAdmin.PermissionListDelimiter)
                log.debug(s"PermissionUtil.parsePermissionsWithType - split permissions: $permissions")
                permissions.flatMap {
                    permission =>
                        val splitPermission = permission.split(' ')
                        val abbreviation = splitPermission(0)

                        permissionType match {
                            case PermissionType.AP =>
                                if (!OntologyConstants.KnoraAdmin.AdministrativePermissionAbbreviations.contains(abbreviation)) {
                                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                                }

                                if (splitPermission.length > 1) {
                                    val shortGroups: Array[String] = splitPermission(1).split(OntologyConstants.KnoraAdmin.GroupListDelimiter)
                                    val groups: Set[IRI] = shortGroups.map(_.replace(OntologyConstants.KnoraAdmin.KnoraAdminPrefix, OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion)).toSet
                                    buildPermissionObject(abbreviation, groups)
                                } else {
                                    buildPermissionObject(abbreviation, Set.empty[IRI])
                                }

                            case PermissionType.OAP =>
                                if (!OntologyConstants.KnoraAdmin.ObjectAccessPermissionAbbreviations.contains(abbreviation)) {
                                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                                }
                                val shortGroups: Array[String] = splitPermission(1).split(OntologyConstants.KnoraAdmin.GroupListDelimiter)
                                val groups: Set[IRI] = shortGroups.map(_.replace(OntologyConstants.KnoraAdmin.KnoraAdminPrefix, OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion)).toSet
                                buildPermissionObject(abbreviation, groups)
                        }
                }
            }.toSet
            case None => Set.empty[PermissionADM]
        }
    }

    /**
      * Helper method used to convert the permission string stored inside the triplestore to a permission object.
      *
      * @param name the name of the permission.
      * @param iris the optional set of additional information (e.g., group IRIs, resource class IRIs).
      * @return a sequence of permission objects.
      */
    def buildPermissionObject(name: String, iris: Set[IRI]): Set[PermissionADM] = {
        name match {
            case OntologyConstants.KnoraAdmin.ProjectResourceCreateAllPermission => Set(PermissionADM.ProjectResourceCreateAllPermission)

            case OntologyConstants.KnoraAdmin.ProjectResourceCreateRestrictedPermission =>
                if (iris.nonEmpty) {
                    log.debug(s"buildPermissionObject - ProjectResourceCreateRestrictedPermission - iris: $iris")
                    iris.map(iri => PermissionADM.projectResourceCreateRestrictedPermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information.")
                }

            case OntologyConstants.KnoraAdmin.ProjectAdminAllPermission => Set(PermissionADM.ProjectAdminAllPermission)

            case OntologyConstants.KnoraAdmin.ProjectAdminGroupAllPermission => Set(PermissionADM.ProjectAdminGroupAllPermission)

            case OntologyConstants.KnoraAdmin.ProjectAdminGroupRestrictedPermission =>
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionADM.projectAdminGroupRestrictedPermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information.")
                }

            case OntologyConstants.KnoraAdmin.ProjectAdminRightsAllPermission => Set(PermissionADM.ProjectAdminRightsAllPermission)

            case OntologyConstants.KnoraAdmin.ProjectAdminOntologyAllPermission => Set(PermissionADM.ProjectAdminOntologyAllPermission)

            case OntologyConstants.KnoraAdmin.ChangeRightsPermission =>
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionADM.changeRightsPermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information.")
                }

            case OntologyConstants.KnoraAdmin.DeletePermission =>
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionADM.deletePermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information.")
                }

            case OntologyConstants.KnoraAdmin.ModifyPermission =>
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionADM.modifyPermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information.")
                }

            case OntologyConstants.KnoraAdmin.ViewPermission =>
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionADM.viewPermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information.")
                }

            case OntologyConstants.KnoraAdmin.RestrictedViewPermission =>
                if (iris.nonEmpty) {
                    iris.map(iri => PermissionADM.restrictedViewPermission(iri))
                } else {
                    throw InconsistentTriplestoreDataException(s"Missing additional permission information.")
                }
        }

    }

    /**
      * Helper method used to remove remove duplicate permissions.
      *
      * @param permissions the sequence of permissions with possible duplicates.
      * @return a set containing only unique permission.
      */
    def removeDuplicatePermissions(permissions: Seq[PermissionADM]): Set[PermissionADM] = {

        val result = permissions.groupBy(perm => perm.name + perm.additionalInformation).map { case (k, v) => v.head }.toSet
        //log.debug(s"removeDuplicatePermissions - result: $result")
        result
    }

    /**
      * Helper method used to remove lesser permissions, i.e. permissions which are already given by
      * the highest permission.
      *
      * @param permissions    a set of permissions possibly containing lesser permissions.
      * @param permissionType the type of permissions.
      * @return a set of permissions without possible lesser permissions.
      */
    def removeLesserPermissions(permissions: Set[PermissionADM], permissionType: PermissionType): Set[PermissionADM] = {
        permissionType match {
            case PermissionType.OAP =>
                if (permissions.nonEmpty) {
                    /* Handling object access permissions which always have 'additionalInformation' and 'v1Code' set */
                    permissions.groupBy(_.additionalInformation).map { case (groupIri, perms) =>
                        // sort in descending order and then take the first one (the highest permission)
                        perms.toArray.sortWith(_.v1Code.get > _.v1Code.get).head
                    }.toSet
                } else {
                    Set.empty[PermissionADM]
                }

            case PermissionType.AP => ???
        }
    }

    /**
      * Helper method used to transform a set of permissions into a permissions string ready to be written into the
      * triplestore as the value for the 'knora-admin:hasPermissions' property.
      *
      * @param permissions    the permissions to be formatted.
      * @param permissionType a [[PermissionType]] indicating the type of permissions to be formatted.
      * @return
      */
    def formatPermissions(permissions: Set[PermissionADM], permissionType: PermissionType): String = {
        permissionType match {
            case PermissionType.OAP =>
                if (permissions.nonEmpty) {

                    /* a map with permission names, shortened groups, and full group names. */
                    val groupedPermissions: Map[String, String] = permissions.groupBy(_.name).map { case (name, perms) =>
                        val shortGroupsString = perms.foldLeft("") { (acc, perm) =>
                            if (acc.isEmpty) {
                                acc + perm.additionalInformation.get.replace(OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion, OntologyConstants.KnoraAdmin.KnoraAdminPrefix)
                            } else {
                                acc + OntologyConstants.KnoraAdmin.GroupListDelimiter + perm.additionalInformation.get.replace(OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion, OntologyConstants.KnoraAdmin.KnoraAdminPrefix)
                            }
                        }
                        (name, shortGroupsString)
                    }

                    /* Sort permissions in descending order */
                    val sortedPermissions = groupedPermissions.toArray.sortWith {
                        (left, right) => PermissionUtilADM.permissionsToV1PermissionCodes(left._1) > PermissionUtilADM.permissionsToV1PermissionCodes(right._1)
                    }

                    /* create the permissions string */
                    sortedPermissions.foldLeft("") { (acc, perm) =>
                        if (acc.isEmpty) {
                            acc + perm._1 + " " + perm._2
                        } else {
                            acc + OntologyConstants.KnoraAdmin.PermissionListDelimiter + perm._1 + " " + perm._2
                        }
                    }
                } else {
                    throw InconsistentTriplestoreDataException("Permissions cannot be empty")
                }
        }
    }

    /**
      * Formats the literal object of the predicate `knora-admin:hasPermissions`.
      *
      * @param permissions a [[Map]] in which the keys are permission abbreviations in
      *                    [[OntologyConstants.KnoraAdmin.ObjectAccessPermissionAbbreviations]], and the values are sets of
      *                    user group IRIs.
      * @return a formatted string literal that can be used as the object of the predicate `knora-base:hasPermissions`.
      */
    def formatPermissions(permissions: Map[String, Set[IRI]]): String = {
        if (permissions.nonEmpty) {
            val permissionsLiteral = new StringBuilder

            val currentPermissionsSorted = permissions.toVector.sortBy {
                case (abbreviation, groups) => permissionsToV1PermissionCodes(abbreviation)
            }

            for ((abbreviation, groups) <- currentPermissionsSorted) {
                if (!OntologyConstants.KnoraAdmin.ObjectAccessPermissionAbbreviations.contains(abbreviation)) {
                    throw InconsistentTriplestoreDataException(s"Unrecognized permission abbreviation '$abbreviation'")
                }

                if (permissionsLiteral.nonEmpty) {
                    permissionsLiteral.append(OntologyConstants.KnoraAdmin.PermissionListDelimiter)
                }

                permissionsLiteral.append(abbreviation).append(" ")
                val shortGroups = groups.map(_.replace(OntologyConstants.KnoraAdmin.KnoraAdminPrefixExpansion, OntologyConstants.KnoraAdmin.KnoraAdminPrefix))
                val delimitedGroups = shortGroups.toVector.mkString(OntologyConstants.KnoraAdmin.GroupListDelimiter.toString)
                permissionsLiteral.append(delimitedGroups)
            }

            permissionsLiteral.toString
        } else {
            throw InconsistentTriplestoreDataException("Permissions cannot be empty")
        }
    }


}
