/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo.builtIn.ProjectMember
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestAdminApiClient

object PermissionEndpointsE2ESpec extends E2EZSpec {

  val imageProjectIri: ProjectIri  = ProjectIri.unsafeFrom(SharedTestDataADM2.imagesProjectInfo.id)
  val customDOAPIri: PermissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/zTOK3HlWTLGgTO8ZWVnotg")

  override val e2eSpec = suite("The Permissions Route ('admin/permissions')")(
    suite("getting permissions")(
      test("return a group's administrative permission") {
        for {
          response <- TestAdminApiClient
                        .getAdministrativePermissions(imageProjectIri, ProjectMember.id, rootUser)
                        .flatMap(_.assert200)
        } yield assertTrue(
          response.administrativePermission.iri == "http://rdfh.ch/permissions/00FF/QYdrY7O6QD2VR30oaAt3Yg",
        )
      },
      test("return a project's administrative permissions") {
        for {
          response <- TestAdminApiClient
                        .getAdministrativePermissions(imageProjectIri, rootUser)
                        .flatMap(_.assert200)
        } yield assertTrue(response.administrativePermissions.size == 3)
      },
      test("return a project's default object access permissions") {
        for {
          response <- TestAdminApiClient
                        .getDefaultObjectAccessPermissions(imageProjectIri, rootUser)
                        .flatMap(_.assert200)
        } yield assertTrue(response.defaultObjectAccessPermissions.size == 3)
      },
      test("return a project's all permissions") {
        for {
          response <- TestAdminApiClient
                        .getAdminPermissions(imageProjectIri, rootUser)
                        .flatMap(_.assert200)
        } yield assertTrue(response.permissions.size == 6)
      },
    ),
    suite("creating permissions")(
      test("create administrative permission") {
        for {
          response <- TestAdminApiClient
                        .createAdministrativePermission(
                          CreateAdministrativePermissionAPIRequestADM(
                            forProject = SharedTestDataADM.anythingProjectIri,
                            forGroup = SharedTestDataADM.thingSearcherGroup.groupIri,
                            hasPermissions = Set(PermissionADM(name = "ProjectAdminGroupAllPermission")),
                          ),
                          rootUser,
                        )
                        .flatMap(_.assert200)
          actual = response.administrativePermission
        } yield assertTrue(
          actual.forGroup == "http://rdfh.ch/groups/0001/thing-searcher",
          actual.forProject == "http://rdfh.ch/projects/0001",
          actual.hasPermissions.map(_.name).contains("ProjectAdminGroupAllPermission"),
        )
      },
      test("create a default object access permission") {
        for {
          response <-
            TestAdminApiClient
              .createDefaultObjectAccessPermission(
                CreateDefaultObjectAccessPermissionAPIRequestADM(
                  forProject = anythingProjectIri.value,
                  forGroup = Some(thingSearcherGroup.id),
                  hasPermissions = Set(
                    PermissionADM(
                      name = "D",
                      additionalInformation = Some("http://www.knora.org/ontology/knora-admin#ProjectMember"),
                      permissionCode = Some(7),
                    ),
                  ),
                ),
                rootUser,
              )
              .flatMap(_.assert200)
          actual = response.defaultObjectAccessPermission
        } yield assertTrue(
          actual.forGroup.contains("http://rdfh.ch/groups/0001/thing-searcher"),
          actual.forProject == "http://rdfh.ch/projects/0001",
          actual.hasPermissions
            .flatMap(_.additionalInformation)
            .contains("http://www.knora.org/ontology/knora-admin#ProjectMember"),
        )
      },
      test("create a default object access permission with a custom IRI") {
        for {
          response <-
            TestAdminApiClient
              .createDefaultObjectAccessPermission(
                CreateDefaultObjectAccessPermissionAPIRequestADM(
                  id = Some(customDOAPIri.value),
                  forProject = imagesProjectIri.value,
                  forResourceClass = Some(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
                  hasPermissions = Set(
                    PermissionADM(
                      name = "D",
                      additionalInformation = Some("http://www.knora.org/ontology/knora-admin#ProjectMember"),
                      permissionCode = Some(7),
                    ),
                  ),
                ),
                rootUser,
              )
              .flatMap(_.assert200)
          actual = response.defaultObjectAccessPermission
        } yield assertTrue(
          actual.iri == customDOAPIri.value,
          actual.forResourceClass.contains(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
          actual.forProject == "http://rdfh.ch/projects/00FF",
          actual.hasPermissions
            .flatMap(_.additionalInformation)
            .contains("http://www.knora.org/ontology/knora-admin#ProjectMember"),
        )
      },
    ),
    suite("updating permissions")(
      test("change the group of an administrative permission") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        for {
          response <-
            TestAdminApiClient
              .updatePermissionGroup(permissionIri, newGroupIri, rootUser)
              .flatMap(_.assert200)
        } yield assertTrue(
          response
            .asInstanceOf[AdministrativePermissionGetResponseADM]
            .administrativePermission
            .forGroup == newGroupIri.value,
        )
      },
      test("change the group of a default object access permission") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        for {
          response <- TestAdminApiClient
                        .updatePermissionGroup(permissionIri, newGroupIri, rootUser)
                        .flatMap(_.assert200)
          actual = response.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM]
        } yield assertTrue(actual.defaultObjectAccessPermission.forGroup.contains(newGroupIri.value))
      },
      test("change the set of hasPermissions of an administrative permission") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ")
        for {
          response <- TestAdminApiClient
                        .updatePermissionHasPermission(
                          permissionIri,
                          Set(PermissionADM(name = "ProjectAdminGroupAllPermission")),
                          rootUser,
                        )
                        .flatMap(_.assert200)
          actual = response.asInstanceOf[AdministrativePermissionGetResponseADM].administrativePermission.hasPermissions
        } yield assertTrue(actual == Set(PermissionADM(name = "ProjectAdminGroupAllPermission")))
      },
      test("change the set of hasPermissions of a default object access permission") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ")
        for {
          response <- TestAdminApiClient
                        .updatePermissionHasPermission(
                          permissionIri,
                          Set(
                            PermissionADM(
                              name = "D",
                              permissionCode = Some(7),
                              additionalInformation = Some("http://www.knora.org/ontology/knora-admin#ProjectMember"),
                            ),
                          ),
                          rootUser,
                        )
                        .flatMap(_.assert200)
          actual = response
                     .asInstanceOf[DefaultObjectAccessPermissionGetResponseADM]
                     .defaultObjectAccessPermission
                     .hasPermissions
        } yield assertTrue(
          actual == Set(
            PermissionADM(
              name = "D",
              permissionCode = Some(7),
              additionalInformation = Some("http://www.knora.org/ontology/knora-admin#ProjectMember"),
            ),
          ),
        )
      },
      test("change the resource class of a default object access permission") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ")
        for {
          response <- TestAdminApiClient
                        .updateDefaultObjectAccessPermissionsResourceClass(
                          permissionIri,
                          SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
                          rootUser,
                        )
                        .flatMap(_.assert200)
        } yield assertTrue(
          response.defaultObjectAccessPermission.forResourceClass.contains(
            SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS,
          ),
        )
      },
      test("change the property of a default object access permission") {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA")
        for {
          response <-
            TestAdminApiClient
              .updateDefaultObjectAccessPermissionsProperty(
                permissionIri,
                SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY,
                rootUser,
              )
              .flatMap(_.assert200)
        } yield assertTrue(
          response.defaultObjectAccessPermission.forProperty.contains(
            SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY,
          ),
        )
      },
    ),
    suite("delete request")(
      test("erase a defaultObjectAccess permission") {
        for {
          response <- TestAdminApiClient.deletePermission(customDOAPIri, rootUser).flatMap(_.assert200)
        } yield assertTrue(response.deleted)
      },
      test("erase an administrative permission") {
        val iri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ")
        for {
          response <- TestAdminApiClient.deletePermission(iri, rootUser).flatMap(_.assert200)
        } yield assertTrue(response.deleted)
      },
    ),
  )
}
