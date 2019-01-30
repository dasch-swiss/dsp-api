/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{PermissionADM, PermissionType}
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.util.PermissionUtilADM._

import scala.collection.Map

object PermissionUtilADMSpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

class PermissionUtilADMSpec extends CoreSpec("PermissionUtilSpec") with ImplicitSender with Authenticator {

    val permissionLiteral = "RV knora-base:UnknownUser|V knora-base:KnownUser|M knora-base:ProjectMember|CR knora-base:Creator"

    val parsedPermissionLiteral: Map[EntityPermission, Set[IRI]] = Map(
        RestrictedViewPermission -> Set(OntologyConstants.KnoraBase.UnknownUser),
        ViewPermission -> Set(OntologyConstants.KnoraBase.KnownUser),
        ModifyPermission -> Set(OntologyConstants.KnoraBase.ProjectMember),
        ChangeRightsPermission -> Set(OntologyConstants.KnoraBase.Creator)
    )

    "PermissionUtil" should {

        "return user's max permission for a specific resource (incunabula normal project member user)" in {
            PermissionUtilADM.getUserPermissionV1(
                entityIri = "http://rdfh.ch/00014b43f902",
                entityCreator = "http://rdfh.ch/users/91e19f1e01",
                entityProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                entityPermissionLiteral = permissionLiteral,
                userProfile = SharedTestDataV1.incunabulaMemberUser
            ) should equal(Some(6)) // modify permission

            PermissionUtilADM.getUserPermissionADM(
                entityIri = "http://rdfh.ch/00014b43f902",
                entityCreator = "http://rdfh.ch/users/91e19f1e01",
                entityProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                entityPermissionLiteral = permissionLiteral,
                requestingUser = SharedTestDataADM.incunabulaMemberUser
            ) should equal(Some(ModifyPermission)) // modify permission
        }

        "return user's max permission for a specific resource (incunabula project admin user)" in {
            PermissionUtilADM.getUserPermissionV1(
                entityIri = "http://rdfh.ch/00014b43f902",
                entityCreator = "http://rdfh.ch/users/91e19f1e01",
                entityProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                entityPermissionLiteral = permissionLiteral,
                userProfile = SharedTestDataV1.incunabulaProjectAdminUser
            ) should equal(Some(8)) // change rights permission

            PermissionUtilADM.getUserPermissionADM(
                entityIri = "http://rdfh.ch/00014b43f902",
                entityCreator = "http://rdfh.ch/users/91e19f1e01",
                entityProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                entityPermissionLiteral = permissionLiteral,
                requestingUser = SharedTestDataADM.incunabulaProjectAdminUser
            ) should equal(Some(ChangeRightsPermission)) // change rights permission
        }

        "return user's max permission for a specific resource (incunabula creator user)" in {
            PermissionUtilADM.getUserPermissionV1(
                entityIri = "http://rdfh.ch/00014b43f902",
                entityCreator = "http://rdfh.ch/users/91e19f1e01",
                entityProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                entityPermissionLiteral = permissionLiteral,
                userProfile = SharedTestDataV1.incunabulaCreatorUser
            ) should equal(Some(8)) // change rights permission

            PermissionUtilADM.getUserPermissionADM(
                entityIri = "http://rdfh.ch/00014b43f902",
                entityCreator = "http://rdfh.ch/users/91e19f1e01",
                entityProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                entityPermissionLiteral = permissionLiteral,
                requestingUser = SharedTestDataADM.incunabulaCreatorUser
            ) should equal(Some(ChangeRightsPermission)) // change rights permission
        }

        "return user's max permission for a specific resource (root user)" in {
            PermissionUtilADM.getUserPermissionV1(
                entityIri = "http://rdfh.ch/00014b43f902",
                entityCreator = "http://rdfh.ch/users/91e19f1e01",
                entityProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                entityPermissionLiteral = permissionLiteral,
                userProfile = SharedTestDataV1.rootUser
            ) should equal(Some(8)) // change rights permission

            PermissionUtilADM.getUserPermissionADM(
                entityIri = "http://rdfh.ch/00014b43f902",
                entityCreator = "http://rdfh.ch/users/91e19f1e01",
                entityProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                entityPermissionLiteral = permissionLiteral,
                requestingUser = SharedTestDataADM.rootUser
            ) should equal(Some(ChangeRightsPermission)) // change rights permission
        }

        "return user's max permission for a specific resource (normal user)" in {
            PermissionUtilADM.getUserPermissionV1(
                entityIri = "http://rdfh.ch/00014b43f902",
                entityCreator = "http://rdfh.ch/users/91e19f1e01",
                entityProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                entityPermissionLiteral = permissionLiteral,
                userProfile = SharedTestDataV1.normalUser
            ) should equal(Some(2)) // view permission

            PermissionUtilADM.getUserPermissionADM(
                entityIri = "http://rdfh.ch/00014b43f902",
                entityCreator = "http://rdfh.ch/users/91e19f1e01",
                entityProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                entityPermissionLiteral = permissionLiteral,
                requestingUser = SharedTestDataADM.normalUser
            ) should equal(Some(ViewPermission)) // view permission
        }

        "return user's max permission for a specific resource (anonymous user)" in {
            PermissionUtilADM.getUserPermissionV1(
                entityIri = "http://rdfh.ch/00014b43f902",
                entityCreator = "http://rdfh.ch/users/91e19f1e01",
                entityProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                entityPermissionLiteral = permissionLiteral,
                userProfile = SharedTestDataV1.anonymousUser
            ) should equal(Some(1)) // restricted view permission

            PermissionUtilADM.getUserPermissionADM(
                entityIri = "http://rdfh.ch/00014b43f902",
                entityCreator = "http://rdfh.ch/users/91e19f1e01",
                entityProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
                entityPermissionLiteral = permissionLiteral,
                requestingUser = SharedTestDataADM.anonymousUser
            ) should equal(Some(RestrictedViewPermission)) // restricted view permission
        }

        "return user's max permission from assertions for a specific resource" in {
            val assertions: Seq[(IRI, String)] = Seq(
                (OntologyConstants.KnoraBase.AttachedToUser, "http://rdfh.ch/users/91e19f1e01"),
                (OntologyConstants.KnoraBase.AttachedToProject, SharedTestDataV1.INCUNABULA_PROJECT_IRI),
                (OntologyConstants.KnoraBase.HasPermissions, permissionLiteral)
            )

            PermissionUtilADM.getUserPermissionFromAssertionsV1(
                entityIri = "http://rdfh.ch/00014b43f902",
                assertions = assertions,
                userProfile = SharedTestDataV1.incunabulaMemberUser
            ) should equal(Some(6)) // modify permissions

            PermissionUtilADM.getUserPermissionFromAssertionsADM(
                entityIri = "http://rdfh.ch/00014b43f902",
                assertions = assertions,
                requestingUser = SharedTestDataADM.incunabulaMemberUser
            ) should equal(Some(ModifyPermission)) // modify permissions
        }


        "return user's max permission on link value" ignore {
            // TODO
        }

        "return parsed permissions string as 'Map[IRI, Set[String]]" in {
            PermissionUtilADM.parsePermissions(permissionLiteral) should equal(parsedPermissionLiteral)
        }


        "return parsed permissions string as 'Set[PermissionV1]' (object access permissions)" in {
            val hasPermissionsString = "M knora-base:Creator,knora-base:ProjectMember|V knora-base:KnownUser,http://rdfh.ch/groups/customgroup|RV knora-base:UnknownUser"

            val permissionsSet = Set(
                PermissionADM.modifyPermission(OntologyConstants.KnoraBase.Creator),
                PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser),
                PermissionADM.viewPermission("http://rdfh.ch/groups/customgroup"),
                PermissionADM.restrictedViewPermission(OntologyConstants.KnoraBase.UnknownUser)
            )

            PermissionUtilADM.parsePermissionsWithType(Some(hasPermissionsString), PermissionType.OAP) should contain allElementsOf permissionsSet
        }

        "return parsed permissions string as 'Set[PermissionV1]' (administrative permissions)" in {
            val hasPermissionsString = "ProjectResourceCreateAllPermission|ProjectAdminAllPermission|ProjectResourceCreateRestrictedPermission <http://www.knora.org/ontology/00FF/images#bild>,<http://www.knora.org/ontology/00FF/images#bildformat>"

            val permissionsSet = Set(
                PermissionADM.ProjectResourceCreateAllPermission,
                PermissionADM.ProjectAdminAllPermission,
                PermissionADM.projectResourceCreateRestrictedPermission("http://www.knora.org/ontology/00FF/images#bild"),
                PermissionADM.projectResourceCreateRestrictedPermission("http://www.knora.org/ontology/00FF/images#bildformat")
            )

            PermissionUtilADM.parsePermissionsWithType(Some(hasPermissionsString), PermissionType.AP) should contain allElementsOf permissionsSet
        }

        "build a 'PermissionV1' object" in {
            PermissionUtilADM.buildPermissionObject(
                name = OntologyConstants.KnoraBase.ProjectResourceCreateRestrictedPermission,
                iris = Set("1", "2", "3")
            ) should equal(
                Set(
                    PermissionADM.projectResourceCreateRestrictedPermission("1"),
                    PermissionADM.projectResourceCreateRestrictedPermission("2"),
                    PermissionADM.projectResourceCreateRestrictedPermission("3")
                )
            )
        }

        "remove duplicate permissions" in {

            val duplicatedPermissions = Seq(
                PermissionADM.restrictedViewPermission("1"),
                PermissionADM.restrictedViewPermission("1"),
                PermissionADM.restrictedViewPermission("2"),
                PermissionADM.changeRightsPermission("2"),
                PermissionADM.changeRightsPermission("3"),
                PermissionADM.changeRightsPermission("3")
            )

            val deduplicatedPermissions = Set(
                PermissionADM.restrictedViewPermission("1"),
                PermissionADM.restrictedViewPermission("2"),
                PermissionADM.changeRightsPermission("2"),
                PermissionADM.changeRightsPermission("3")
            )

            val result = PermissionUtilADM.removeDuplicatePermissions(duplicatedPermissions)
            result.size should equal(deduplicatedPermissions.size)
            result should contain allElementsOf deduplicatedPermissions

        }

        "remove lesser permissions" in {
            val withLesserPermissions = Set(
                PermissionADM.restrictedViewPermission("1"),
                PermissionADM.viewPermission("1"),
                PermissionADM.modifyPermission("2"),
                PermissionADM.changeRightsPermission("1"),
                PermissionADM.deletePermission("2")
            )

            val withoutLesserPermissions = Set(
                PermissionADM.changeRightsPermission("1"),
                PermissionADM.deletePermission("2")
            )

            val result = PermissionUtilADM.removeLesserPermissions(withLesserPermissions, PermissionType.OAP)
            result.size should equal(withoutLesserPermissions.size)
            result should contain allElementsOf withoutLesserPermissions
        }

        "create permissions string" in {
            val permissions = Set(
                PermissionADM.changeRightsPermission("1"),
                PermissionADM.deletePermission("2"),
                PermissionADM.changeRightsPermission(OntologyConstants.KnoraBase.Creator),
                PermissionADM.modifyPermission(OntologyConstants.KnoraBase.ProjectMember),
                PermissionADM.viewPermission(OntologyConstants.KnoraBase.KnownUser)
            )

            val permissionsString = "CR knora-base:Creator,1|D 2|M knora-base:ProjectMember|V knora-base:KnownUser"

            val result = PermissionUtilADM.formatPermissionADMs(permissions, PermissionType.OAP)
            result should equal(permissionsString)

        }
    }
}
