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

package org.knora.webapi.responders.v1

import org.knora.webapi.messages.v1.responder.ontologymessages.{EntityInfoV1, PredicateInfoV1, PropertyEntityInfoV1, ResourceEntityInfoV1}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.responders.v1.GroupedProps.{ValueLiterals, ValueProps}
import org.knora.webapi.util.ErrorHandlingMap
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}

/**
  * A utility that responder actors use to determine a user's permissions on an RDF subject in the triplestore.
  */
object PermissionUtilV1 {

    // Todo: Add an explain method.

    /**
      * A [[Map]] of Knora permission property IRIs to their API v1 codes.
      */
    private val permissionIrisToV1PermissionCodes = new ErrorHandlingMap(Map(
        OntologyConstants.KnoraBase.HasRestrictedViewPermission -> 1,
        OntologyConstants.KnoraBase.HasViewPermission -> 2,
        OntologyConstants.KnoraBase.HasModifyPermission -> 6,
        OntologyConstants.KnoraBase.HasDeletePermission -> 7,
        OntologyConstants.KnoraBase.HasChangeRightsPermission -> 8
    ), { key: IRI => s"Unknown permission: $key" })

    /**
      * A [[Map]] of API v1 permission codes to Knora permission property IRIs. Used for debugging.
      */
    private val v1PermissionCodesToPermissionIris = permissionIrisToV1PermissionCodes.map(_.swap)

    /**
      * A [[Set]] of the subproperties of `knora-admin:hasPermission`.
      */
    private val permissionProperties = permissionIrisToV1PermissionCodes.keySet

    /**
      * The API v1 code of the highest permission.
      */
    private val MaxPermissionCode = permissionIrisToV1PermissionCodes(OntologyConstants.KnoraBase.HasMaxPermission)

    /**
      * Checks whether a Knora API v1 integer permission code implies a particular permission property.
      * @param userHasPermissionCode the Knora API v1 integer permission code that the user has, or [[None]] if the user has no permissions
      *                              (in which case this method returns `false`).
      * @param userNeedsPermissionIri the IRI of the permission (a subproperty of `knora-admin:hasPermission`) that the user needs.
      * @return `true` if the user has the needed permission.
      */
    def impliesV1(userHasPermissionCode: Option[Int], userNeedsPermissionIri: IRI): Boolean = {
        userHasPermissionCode match {
            case Some(permissionCode) => permissionCode.toInt >= permissionIrisToV1PermissionCodes(userNeedsPermissionIri)
            case None => false
        }
    }

    /**
      * Determines the permissions that a user has on a `knora-base:Value`, and returns a permissions code in Knora API v1 format.
      * @param valueProps a [[ValueProps]] containing the permission-relevant predicates and objects
      *                   pertaining to the value, grouped by predicate. The predicates must include
      *                   [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToUser]], and should include
      *                   [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToProject]]
      *                   and any subproperties of `knora-admin:hasPermission`. The [[ValueProps]] must also contain
      *                   an `rdf:type` predicate.
      *                   Other predicates may be included, but they will be ignored, so there is no need to filter
      *                   them before passing them to this function.
      * @param userProfile the profile of the current user.
      * @return a code representing the user's permission level for the value.
      */
    def getUserPermissionV1WithValueProps(subjectIri: IRI,
                                          valueProps: ValueProps,
                                          userProfile: UserProfileV1): Option[Int] = {
        getUserPermissionV1(subjectIri, filterPermissionRelevantAssertionsFromValueProps(valueProps), userProfile)
    }

    /**
      * Given the IRI of an RDF property, returns `true` if the property is relevant to calculating permissions. This
      * is the case if the property is a subproperty of `knora-admin:hasPermission` or is `knora-admin:attachedToUser`
      * or `knora-admin:attachedToProject`.
      * @param p the IRI of the property.
      * @return `true` if the property is relevant to calculating permissions.
      */
    def isPermissionRelevant(p: IRI): Boolean = {
        p == OntologyConstants.KnoraBase.AttachedToUser ||
            p == OntologyConstants.KnoraBase.AttachedToProject ||
            permissionProperties.contains(p)
    }

    /**
      * Given a list of predicates and objects pertaining to a subject, returns only the ones that are relevant to
      * permissions (i.e. the permissions themselves, plus the owner and project).
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
      * in the [[ValueProps]] (i.e. permission assertions, plus assertions about the subject's owner and project).
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
      * Given a list of predicates and objects pertaining to a subject, returns only the ones that are actually
      * permissions (i.e. in which the predicate is a subproperty of `knora-admin:hasPermission`).
      * @param assertions a list containing the permissions on a subject. Other items will be filtered out.
      * @return a list of permissions.
      */
    def filterPermissions(assertions: Seq[(IRI, IRI)]): Seq[(IRI, IRI)] = {
        assertions.filter {
            case (p, o) => permissionProperties.contains(p)
        }.toVector
    }

    /**
      * A map of IRIs for default permissions to the corresponding IRIs for permissions.
      */
    private val defaultPermissions2Permissions: Map[IRI, IRI] = Map(
        OntologyConstants.KnoraBase.HasDefaultRestrictedViewPermission -> OntologyConstants.KnoraBase.HasRestrictedViewPermission,
        OntologyConstants.KnoraBase.HasDefaultViewPermission -> OntologyConstants.KnoraBase.HasViewPermission,
        OntologyConstants.KnoraBase.HasDefaultModifyPermission -> OntologyConstants.KnoraBase.HasModifyPermission,
        OntologyConstants.KnoraBase.HasDefaultDeletePermission -> OntologyConstants.KnoraBase.HasDeletePermission,
        OntologyConstants.KnoraBase.HasDefaultChangeRightsPermission -> OntologyConstants.KnoraBase.HasChangeRightsPermission
    )

    /**
      * Given an [[EntityInfoV1]], gets the entity's default permissions and converts them to permission assertions that
      * can be assigned to an instance of the entity. An [[EntityInfoV1]] is either a [[ResourceEntityInfoV1]] or a [[PropertyEntityInfoV1]].
      * @param entityInfo an [[EntityInfoV1]] describing an ontology entity.
      * @return a list of assertions describing permissions based on the entity's defaults.
      */
    def makePermissionsFromEntityDefaults(entityInfo: EntityInfoV1): Seq[(IRI, IRI)] = {
        // Get the predicates that describe the entity's default permissions.
        val defaultPermissionPredicates: Map[IRI, PredicateInfoV1] = entityInfo.predicates.filterKeys {
            key => OntologyConstants.KnoraBase.defaultPermissionProperties(key)
        }

        // Convert them into a list of predicate-object pairs.
        val defaultPermissionAssertions: Seq[(IRI, IRI)] = defaultPermissionPredicates.toVector.flatMap {
            case (predicateIri, predicateInfo) => predicateInfo.objects.map(obj => predicateIri -> obj)
        }

        // Convert the IRIs representing default permissions to the corresponding IRIs representing permissions.
        defaultPermissionAssertions.map {
            case (defaultPermission, grantedToGroup) => defaultPermissions2Permissions(defaultPermission) -> grantedToGroup
        }
    }

    /**
      * Determines the permissions that a user has on a subject, and returns a permissions code in Knora API v1 format.
      * @param subjectIri the IRI of the subject.
      * @param assertions a [[Seq]] containing all the permission-relevant predicates and objects
      *                   pertaining to the subject. The predicates must include
      *                   [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToUser]], and should include
      *                   [[org.knora.webapi.OntologyConstants.KnoraBase.AttachedToProject]]
      *                   and any subproperties of `knora-admin:hasPermission`.
      *                   Other predicates may be included, but they will be ignored, so there is no need to filter
      *                   them before passing them to this function.
      * @param userProfile the profile of the current user.
      * @return a code representing the user's permission level for the subject.
      */
    def getUserPermissionV1(subjectIri: IRI,
                            assertions: Seq[(IRI, IRI)],
                            userProfile: UserProfileV1): Option[Int] = {
        // Get the subject's owner, project, and permissions.
        val (subjectOwnerOption: Option[IRI], subjectProjectOption: Option[IRI], subjectPermissions: Seq[(IRI, IRI)]) =
            assertions.foldLeft((None: Option[IRI], None: Option[IRI], Vector.empty[(IRI, IRI)])) {
                case (acc@(accSubjectOwner, accSubjectProject, accSubjectPermissions), (p, o)) =>
                    if (p == OntologyConstants.KnoraBase.AttachedToUser) {
                        (Some(o), accSubjectProject, accSubjectPermissions)
                    } else if (p == OntologyConstants.KnoraBase.AttachedToProject) {
                        (accSubjectOwner, Some(o), accSubjectPermissions)
                    } else if (permissionProperties.contains(p)) {
                        (accSubjectOwner, accSubjectProject, (p, o) +: accSubjectPermissions)
                    } else {
                        acc
                    }
            }

        // Anything with permissions must have an owner and a project.
        val subjectOwner = subjectOwnerOption.getOrElse(throw InconsistentTriplestoreDataException(s"Subject $subjectIri has no owner"))
        val subjectProject = subjectProjectOption.getOrElse(throw InconsistentTriplestoreDataException(s"Subject $subjectIri has no project"))

        /*
        println()
        println(s"User: ${userProfile.userData.user_id}")
        println(s"Subject: $subjectIri")
        println(s"Subject owner: $subjectOwner")
        println(s"Subject project: $subjectProjectOption")
        println(s"Subject permissions: ${ScalaPrettyPrinter.prettyPrint(subjectPermissions)}")
        */

        /**
          * Calculates the highest permission a user can be granted on a subject.
          * @param subjectPermissions tuples of permissions on a subject and the groups they are granted to.
          * @param userGroups the groups that the user belongs to.
          * @return the code of the highest permission the user has on the subject, or `None` if the user has no permissions
          *         on the subject.
          */
        def calculateHighestGrantedPermission(subjectPermissions: Seq[(IRI, IRI)], userGroups: Seq[IRI]): Option[Int] = {
            // Make a list of the codes for all the permissions the user can obtain for this subject.
            val permissionCodes = subjectPermissions.foldLeft(Vector.empty[Int]) {
                case (acc, (permission, grantedToGroup)) =>
                    if (userGroups.contains(grantedToGroup)) {
                        permissionIrisToV1PermissionCodes(permission) +: acc
                    } else {
                        acc
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
        println(s"User permission: ${permissionCodeOption.map(code => v1PermissionCodesToPermissionIris(code.toInt))}")
        println(s"User permission code: $permissionCodeOption")
        */

        permissionCodeOption
    }

    /**
      * Given the IRI of a subject's owner, the IRI of a subject's project, and a string containing a semicolon-separated
      * list of permission assertions (each of which consists of a permission predicate, a comma, and a permission object),
      * creates a list of tuples containing all necessary assertions for `getUserPermissionV1`.
      * @param assertionsString string containing the assertions (to be parsed)
      * @param owner the owner of the requested resource
      * @param project the project the requested resource belongs to
      * @return a list of permission-relevant assertions (the subject's owner and project plus its permissions) as a list
      *         of tuples.
      */
    def parsePermissions(assertionsString: String, owner: IRI, project: IRI): Seq[(IRI, IRI)] = {
        val resourcePermissions: Seq[(IRI, IRI)] = if (assertionsString.length > 0) {
            assertionsString.split(';').map {
                row =>
                    val pair = row.split(',')
                    (pair(0), pair(1))
            }
        } else {
            Vector.empty[(IRI, IRI)]
        }

        // in the resourcePermissions, the information about the user (owner) and the project are still missing
        (OntologyConstants.KnoraBase.AttachedToProject, project) +:(OntologyConstants.KnoraBase.AttachedToUser, owner) +: resourcePermissions
    }
}
