/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import org.apache.pekko.testkit.ImplicitSender
import zio.NonEmptyChunk
import zio.ZIO

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.ConvertibleIri
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedPermissionsTestData.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.imagesProjectIri
import org.knora.webapi.sharedtestdata.SharedTestDataADM.imagesUser02
import org.knora.webapi.sharedtestdata.SharedTestDataADM.incunabulaMemberUser
import org.knora.webapi.sharedtestdata.SharedTestDataADM.normalUser
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.api.service.PermissionRestService
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * This spec is used to test the [[PermissionsResponder]] actor.
 */
class PermissionsResponderSpec extends CoreSpec with ImplicitSender {
  private val rootUser = SharedTestDataADM.rootUser

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path =
        "test_data/generated_test_data/responders.admin.PermissionsResponderADMSpec/additional_permissions-data.ttl",
      name = "http://www.knora.org/data/permissions",
    ),
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
  )

  private val permissionRestService = ZIO.serviceWithZIO[PermissionRestService]
  private val permissionsResponder  = ZIO.serviceWithZIO[PermissionsResponder]

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "The PermissionsResponderADM" when {

    "ask about administrative permissions " should {

      "return all Permission.Administrative for project" in {
        val result = UnsafeZioRun.runOrThrow(
          permissionsResponder(_.getPermissionsApByProjectIri(imagesProjectIri)),
        )
        result shouldEqual AdministrativePermissionsForProjectGetResponseADM(
          Seq(perm002_a1.p, perm002_a3.p, perm002_a2.p),
        )
      }

      "return Permission.Administrative for project and group" in {
        val result = UnsafeZioRun.runOrThrow(
          permissionRestService(
            _.getPermissionsApByProjectAndGroupIri(
              ProjectIri.unsafeFrom(imagesProjectIri),
              KnoraGroupRepo.builtIn.ProjectMember.id,
              rootUser,
            ),
          ),
        )
        result shouldEqual AdministrativePermissionGetResponseADM(perm002_a1.p)
      }
    }

    "asked to create an administrative permission" should {
      "fail and return a 'DuplicateValueException' when permission for project and group combination already exists" in {
        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.createAdministrativePermission(
              CreateAdministrativePermissionAPIRequestADM(
                forProject = imagesProjectIri,
                forGroup = KnoraGroupRepo.builtIn.ProjectMember.id.value,
                hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)),
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"An administrative permission for project: '$imagesProjectIri' and group: '${KnoraGroupRepo.builtIn.ProjectMember.id.value}' combination already exists. " +
            s"This permission currently has the scope '${PermissionUtilADM
                .formatPermissionADMs(perm002_a1.p.hasPermissions, PermissionType.AP)}'. " +
            s"Use its IRI ${perm002_a1.iri} to modify it, if necessary.",
        )
      }

      "create and return an administrative permission with a custom IRI" in {
        val customIri = "http://rdfh.ch/permissions/0001/24RD7QcoTKqEJKrDBE885Q"
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.createAdministrativePermission(
              CreateAdministrativePermissionAPIRequestADM(
                id = Some(customIri),
                forProject = SharedTestDataADM.anythingProjectIri,
                forGroup = SharedTestDataADM.thingSearcherGroup.id,
                hasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll)),
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assert(actual.administrativePermission.iri == customIri)
        assert(actual.administrativePermission.forProject == SharedTestDataADM.anythingProjectIri)
        assert(actual.administrativePermission.forGroup == SharedTestDataADM.thingSearcherGroup.id)
      }

      "create and return an administrative permission even if irrelevant values were given for name and code of its permission" in {
        val customIri = "http://rdfh.ch/permissions/0001/0pd-VUDeShWNJ2Nq3fGGGQ"
        val hasPermissions = Set(
          PermissionADM(
            name = Permission.Administrative.ProjectResourceCreateAll.token,
            additionalInformation = Some("blabla"),
            permissionCode = Some(8),
          ),
        )
        val expectedHasPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll))
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.createAdministrativePermission(
              CreateAdministrativePermissionAPIRequestADM(
                id = Some(customIri),
                forProject = SharedTestDataADM.anythingProjectIri,
                forGroup = KnoraGroupRepo.builtIn.KnownUser.id.value,
                hasPermissions = hasPermissions,
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assert(actual.administrativePermission.iri == customIri)
        assert(actual.administrativePermission.forGroup == KnoraGroupRepo.builtIn.KnownUser.id.value)
        assert(actual.administrativePermission.forProject == SharedTestDataADM.anythingProjectIri)
        assert(actual.administrativePermission.hasPermissions.equals(expectedHasPermissions))
      }
    }

    "ask to query about default object access permissions " should {

      "return all DefaultObjectAccessPermissions for project" in {
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.getPermissionsDaopByProjectIri(
              ProjectIri.unsafeFrom(imagesProjectIri),
            ),
          ),
        )
        actual shouldEqual DefaultObjectAccessPermissionsForProjectGetResponseADM(
          Seq(perm002_d2.p, perm0003_a4.p, perm002_d1.p),
        )
      }
    }

    "ask to create a default object access permission" should {

      "create a DefaultObjectAccessPermission for project and group" in {
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = SharedTestDataADM.anythingProjectIri,
                forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
                hasPermissions = Set(
                  PermissionADM.from(Permission.ObjectAccess.RestrictedView, SharedTestDataADM.thingSearcherGroup.id),
                ),
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )

        assert(actual.defaultObjectAccessPermission.forProject == SharedTestDataADM.anythingProjectIri)
        assert(actual.defaultObjectAccessPermission.forGroup.contains(SharedTestDataADM.thingSearcherGroup.id))
        assert(
          actual.defaultObjectAccessPermission.hasPermissions.contains(
            PermissionADM.from(Permission.ObjectAccess.RestrictedView, SharedTestDataADM.thingSearcherGroup.id),
          ),
        )
      }

      "create a DefaultObjectAccessPermission for project and group with custom IRI" in {
        val customIri = "http://rdfh.ch/permissions/0001/4PnSvolsTEa86KJ2EG76SQ"
        val received = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.createDefaultObjectAccessPermission(
              createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
                id = Some(customIri),
                forProject = SharedTestDataADM.anythingProjectIri,
                forGroup = Some(KnoraGroupRepo.builtIn.UnknownUser.id.value),
                hasPermissions = Set(
                  PermissionADM
                    .from(Permission.ObjectAccess.RestrictedView, KnoraGroupRepo.builtIn.UnknownUser.id.value),
                ),
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assert(received.defaultObjectAccessPermission.iri == customIri)
        assert(received.defaultObjectAccessPermission.forGroup.contains(KnoraGroupRepo.builtIn.UnknownUser.id.value))
        assert(received.defaultObjectAccessPermission.forProject == SharedTestDataADM.anythingProjectIri)
        assert(
          received.defaultObjectAccessPermission.hasPermissions
            .contains(
              PermissionADM.from(Permission.ObjectAccess.RestrictedView, KnoraGroupRepo.builtIn.UnknownUser.id.value),
            ),
        )
      }

      "create a DefaultObjectAccessPermission for project and resource class" in {
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = imagesProjectIri,
                forResourceClass = Some(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
                hasPermissions =
                  Set(PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.KnownUser.id.value)),
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assert(actual.defaultObjectAccessPermission.forProject == imagesProjectIri)
        assert(
          actual.defaultObjectAccessPermission.forResourceClass
            .contains(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
        )
        assert(
          actual.defaultObjectAccessPermission.hasPermissions
            .contains(PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.KnownUser.id.value)),
        )
      }

      "create a DefaultObjectAccessPermission for project and property" in {
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = imagesProjectIri,
                forProperty = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
                hasPermissions = Set(
                  PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
                ),
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assert(actual.defaultObjectAccessPermission.forProject == imagesProjectIri)
        assert(
          actual.defaultObjectAccessPermission.forProperty
            .contains(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
        )
        assert(
          actual.defaultObjectAccessPermission.hasPermissions
            .contains(PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value)),
        )
      }

      "fail and return a 'DuplicateValueException' when a doap permission for project and group combination already exists" in {
        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = SharedTestDataADM2.incunabulaProjectIri,
                forGroup = Some(KnoraGroupRepo.builtIn.ProjectMember.id.value),
                hasPermissions = Set(
                  PermissionADM
                    .from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.ProjectMember.id.value),
                ),
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"A default object access permission for project: '${SharedTestDataADM2.incunabulaProjectIri}' and group: '${KnoraGroupRepo.builtIn.ProjectMember.id.value}' " +
            "combination already exists. " +
            s"This permission currently has the scope '${PermissionUtilADM
                .formatPermissionADMs(perm003_d1.p.hasPermissions, PermissionType.OAP)}'. " +
            s"Use its IRI ${perm003_d1.iri} to modify it, if necessary.",
        )
      }

      "fail and return a 'DuplicateValueException' when a doap permission for project and resourceClass combination already exists" in {
        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = SharedTestDataADM2.incunabulaProjectIri,
                forResourceClass = Some(SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS),
                hasPermissions = Set(
                  PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
                  PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
                ),
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"A default object access permission for project: '${SharedTestDataADM2.incunabulaProjectIri}' and resourceClass: '${SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS}' " +
            "combination already exists. " +
            s"This permission currently has the scope '${PermissionUtilADM
                .formatPermissionADMs(perm003_d2.p.hasPermissions, PermissionType.OAP)}'. " +
            s"Use its IRI ${perm003_d2.iri} to modify it, if necessary.",
        )
      }

      "fail and return a 'DuplicateValueException' when a doap permission for project and property combination already exists" in {
        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = SharedTestDataADM2.incunabulaProjectIri,
                forProperty = Some(SharedOntologyTestDataADM.INCUNABULA_PartOf_Property),
                hasPermissions = Set(
                  PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.KnownUser.id.value),
                ),
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"A default object access permission for project: '${SharedTestDataADM2.incunabulaProjectIri}' and property: '${SharedOntologyTestDataADM.INCUNABULA_PartOf_Property}' " +
            "combination already exists. " +
            s"This permission currently has the scope '${PermissionUtilADM
                .formatPermissionADMs(perm003_d4.p.hasPermissions, PermissionType.OAP)}'. " +
            s"Use its IRI ${perm003_d4.iri} to modify it, if necessary.",
        )
      }

      "fail and return a 'DuplicateValueException' when a doap permission for project, resource class, and property combination already exists" in {
        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = SharedTestDataADM2.incunabulaProjectIri,
                forResourceClass = Some(SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS),
                forProperty = Some(SharedOntologyTestDataADM.INCUNABULA_PartOf_Property),
                hasPermissions = Set(
                  PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
                  PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
                ),
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"A default object access permission for project: '${SharedTestDataADM2.incunabulaProjectIri}' and resourceClass: '${SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS}' " +
            s"and property: '${SharedOntologyTestDataADM.INCUNABULA_PartOf_Property}' " +
            "combination already exists. " +
            s"This permission currently has the scope '${PermissionUtilADM
                .formatPermissionADMs(perm003_d5.p.hasPermissions, PermissionType.OAP)}'. " +
            s"Use its IRI ${perm003_d5.iri} to modify it, if necessary.",
        )
      }

      "create a DefaultObjectAccessPermission for project and property even if name of a permission was missing" in {
        val hasPermissions = Set(
          PermissionADM(
            name = "",
            additionalInformation = Some(KnoraGroupRepo.builtIn.UnknownUser.id.value),
            permissionCode = Some(1),
          ),
        )
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = imagesProjectIri,
                forGroup = Some(KnoraGroupRepo.builtIn.UnknownUser.id.value),
                hasPermissions = hasPermissions,
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assert(actual.defaultObjectAccessPermission.forProject == imagesProjectIri)
        assert(actual.defaultObjectAccessPermission.forGroup.contains(KnoraGroupRepo.builtIn.UnknownUser.id.value))
        assert(
          actual.defaultObjectAccessPermission.hasPermissions.contains(
            PermissionADM.from(Permission.ObjectAccess.RestrictedView, KnoraGroupRepo.builtIn.UnknownUser.id.value),
          ),
        )
      }

      "create a DefaultObjectAccessPermission for project and property even if permissionCode of a permission was missing" in {
        val hasPermissions = Set(
          PermissionADM(
            name = Permission.ObjectAccess.Delete.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = None,
          ),
        )
        val expectedPermissions = Set(
          PermissionADM(
            name = Permission.ObjectAccess.Delete.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = Some(7),
          ),
        )
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                forProject = imagesProjectIri,
                forGroup = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
                hasPermissions = hasPermissions,
              ),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assert(actual.defaultObjectAccessPermission.forProject == imagesProjectIri)
        assert(actual.defaultObjectAccessPermission.forGroup.contains(KnoraGroupRepo.builtIn.ProjectAdmin.id.value))
        assert(actual.defaultObjectAccessPermission.hasPermissions.equals(expectedPermissions))
      }
    }

    "ask to get all permissions" should {

      "return all permissions for 'image' project" in {
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.getPermissionsByProjectIri(ProjectIri.unsafeFrom(imagesProjectIri)),
          ),
        )
        actual.permissions.size should be(10)
      }

      "return all permissions for 'incunabula' project" in {
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.getPermissionsByProjectIri(
              ProjectIri.unsafeFrom(SharedTestDataADM.incunabulaProjectIri),
            ),
          ),
        )
        actual shouldEqual
          PermissionsForProjectGetResponseADM(permissions =
            Set(
              PermissionInfoADM(
                perm003_a1.iri,
                OntologyConstants.KnoraAdmin.AdministrativePermission,
              ),
              PermissionInfoADM(
                perm003_a2.iri,
                OntologyConstants.KnoraAdmin.AdministrativePermission,
              ),
              PermissionInfoADM(
                perm003_d1.iri,
                OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission,
              ),
              PermissionInfoADM(
                perm003_d2.iri,
                OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission,
              ),
              PermissionInfoADM(
                perm003_d3.iri,
                OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission,
              ),
              PermissionInfoADM(
                perm003_d4.iri,
                OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission,
              ),
              PermissionInfoADM(
                perm003_d5.iri,
                OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission,
              ),
            ),
          )
      }
    }

    "ask for default object access permissions 'string'" should {

      "return the default object access permissions 'string' for the 'knora-base:LinkObj' resource class (system resource class)" in {
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.getDefaultResourcePermissions(
              ProjectIri.unsafeFrom(SharedTestDataADM.incunabulaProjectIri),
              OntologyConstants.KnoraBase.LinkObj.toSmartIri,
              SharedTestDataADM.incunabulaProjectAdminUser,
            ),
          ),
        )
        assert(
          actual == DefaultObjectAccessPermissionsStringResponseADM(
            "M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          ),
        )
      }

      "return the default object access permissions 'string' for the 'knora-base:hasStillImageFileValue' property (system property)" in {
        UnsafeZioRun
          .runOrThrow(
            permissionsResponder(
              _.getPropertyDefaultPermissions(
                projectIri = SharedTestDataADM.incunabulaProjectIri,
                resourceClassIri = stringFormatter.toSmartIri(OntologyConstants.KnoraBase.StillImageRepresentation),
                propertyIri = stringFormatter.toSmartIri(OntologyConstants.KnoraBase.HasStillImageFileValue),
                targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
              ),
            ),
          )
          .shouldEqual(
            "M knora-admin:Creator,knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser",
          )
      }

      "return the default object access permissions 'string' for the 'incunabula:book' resource class (project resource class)" in {
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.getDefaultResourcePermissions(
              ProjectIri.unsafeFrom(SharedTestDataADM.incunabulaProjectIri),
              SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS.toSmartIri,
              SharedTestDataADM.incunabulaProjectAdminUser,
            ),
          ),
        )
        assert(
          actual == DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          ),
        )
      }

      "return the default object access permissions 'string' for the 'incunabula:page' resource class (project resource class)" in {
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.getDefaultResourcePermissions(
              ProjectIri.unsafeFrom(SharedTestDataADM.incunabulaProjectIri),
              SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS.toSmartIri,
              SharedTestDataADM.incunabulaProjectAdminUser,
            ),
          ),
        )
        assert(
          actual == DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          ),
        )
      }

      "return the default object access permissions 'string' for the 'anything:hasInterval' property" in {
        UnsafeZioRun
          .runOrThrow(
            permissionsResponder(
              _.getPropertyDefaultPermissions(
                projectIri = SharedTestDataADM.anythingProjectIri,
                resourceClassIri = stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/anything#Thing"),
                propertyIri = stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/anything#hasInterval"),
                targetUser = SharedTestDataADM.anythingUser2,
              ),
            ),
          )
          .shouldEqual(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          )
      }

      "return the default object access permissions 'string' for the 'anything:Thing' class" in {
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.getDefaultResourcePermissions(
              ProjectIri.unsafeFrom(SharedTestDataADM.anythingProjectIri),
              "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
              SharedTestDataADM.anythingUser2,
            ),
          ),
        )
        assert(
          actual == DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          ),
        )
      }

      "return the default object access permissions 'string' for the 'anything:Thing' class and 'anything:hasText' property" in {
        UnsafeZioRun
          .runOrThrow(
            permissionsResponder(
              _.getPropertyDefaultPermissions(
                projectIri = SharedTestDataADM.anythingProjectIri,
                resourceClassIri = stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/anything#Thing"),
                propertyIri = stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/anything#hasText"),
                targetUser = SharedTestDataADM.anythingUser1,
              ),
            ),
          )
          .shouldEqual("CR knora-admin:Creator")
      }

      "return the default object access permissions 'string' for the 'images:Bild' class and 'anything:hasText' property" in {
        UnsafeZioRun
          .runOrThrow(
            permissionsResponder(
              _.getPropertyDefaultPermissions(
                projectIri = SharedTestDataADM.anythingProjectIri,
                resourceClassIri = stringFormatter.toSmartIri(s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#bild"),
                propertyIri = stringFormatter.toSmartIri("http://www.knora.org/ontology/0001/anything#hasText"),
                targetUser = SharedTestDataADM.anythingUser2,
              ),
            ),
          )
          .shouldEqual(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          )
      }

      "return 'BadRequest' if the supplied project IRI DefaultObjectAccessPermissionsStringForPropertyGetADM is not valid" in {
        val projectIri = ""
        UnsafeZioRun
          .runOrThrow(
            permissionsResponder(
              _.getPropertyDefaultPermissions(
                projectIri = projectIri,
                resourceClassIri = stringFormatter.toSmartIri(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
                propertyIri = stringFormatter.toSmartIri(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
                targetUser = SharedTestDataADM.imagesUser02,
              ),
            ).flip.map(_.getMessage),
          )
          .shouldEqual(s"Invalid project IRI $projectIri")
      }

      "return 'BadRequest' if the supplied resourceClass IRI for DefaultObjectAccessPermissionsStringForPropertyGetADM is not valid" in {
        UnsafeZioRun
          .runOrThrow(
            permissionsResponder(
              _.getPropertyDefaultPermissions(
                projectIri = SharedTestDataADM.imagesProjectIri,
                resourceClassIri = stringFormatter.toSmartIri(SharedTestDataADM.customResourceIRI),
                propertyIri = stringFormatter.toSmartIri(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
                targetUser = SharedTestDataADM.imagesReviewerUser,
              ),
            ).flip.map(_.getMessage),
          )
          .shouldEqual(s"Invalid resource class IRI: ${SharedTestDataADM.customResourceIRI}")
      }

      "return 'BadRequest' if the supplied property IRI for DefaultObjectAccessPermissionsStringForPropertyGetADM is not valid" in {
        UnsafeZioRun
          .runOrThrow(
            permissionsResponder(
              _.getPropertyDefaultPermissions(
                projectIri = SharedTestDataADM.imagesProjectIri,
                resourceClassIri = stringFormatter.toSmartIri(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
                propertyIri = stringFormatter.toSmartIri(SharedTestDataADM.customValueIRI),
                targetUser = SharedTestDataADM.imagesReviewerUser,
              ),
            ).flip.map(_.getMessage),
          )
          .shouldEqual(s"Invalid property IRI: ${SharedTestDataADM.customValueIRI}")
      }

      "return 'BadRequest' if the target user of DefaultObjectAccessPermissionsStringForPropertyGetADM is an Anonymous user" in {
        UnsafeZioRun
          .runOrThrow(
            permissionsResponder(
              _.getPropertyDefaultPermissions(
                projectIri = SharedTestDataADM.imagesProjectIri,
                resourceClassIri = stringFormatter.toSmartIri(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
                propertyIri = stringFormatter.toSmartIri(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
                targetUser = SharedTestDataADM.anonymousUser,
              ),
            ).flip.map(_.getMessage),
          )
          .shouldEqual("Anonymous Users are not allowed.")
      }

      "return the default object access permissions 'string' for the 'anything:Thing' resource class for the root user (system admin and not member of project)" in {
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.getDefaultResourcePermissions(
              ProjectIri.unsafeFrom(SharedTestDataADM.anythingProjectIri),
              "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
              SharedTestDataADM.rootUser,
            ),
          ),
        )
        assert(
          actual == DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser",
          ),
        )
      }
    }

    "ask to update group of a permission" should {
      "update group of an administrative permission" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionsGroup(permissionIri, newGroupIri, rootUser, UUID.randomUUID()),
          ),
        )
        val ap = actual.asInstanceOf[AdministrativePermissionGetResponseADM].administrativePermission
        assert(ap.iri == permissionIri.value)
        assert(ap.forGroup == newGroupIri.value)
      }

      "throw ForbiddenException for PermissionChangeGroupRequestADM if requesting user is not system or project Admin" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.updatePermissionsGroup(permissionIri, newGroupIri, imagesUser02, UUID.randomUUID()),
          ),
        )
        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission ${permissionIri.value} can only be queried/updated/deleted by system or project admin.",
        )
      }

      "update group of a default object access permission" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionsGroup(permissionIri, newGroupIri, rootUser, UUID.randomUUID()),
          ),
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri.value)
        assert(doap.forGroup.contains(newGroupIri.value))
      }

      "update group of a default object access permission, resource class must be deleted" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA")
        val newGroupIri   = GroupIri.unsafeFrom(KnoraGroupRepo.builtIn.ProjectMember.id.value)
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionsGroup(permissionIri, newGroupIri, rootUser, UUID.randomUUID()),
          ),
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri.value)
        assert(doap.forGroup.contains(KnoraGroupRepo.builtIn.ProjectMember.id.value))
        assert(doap.forResourceClass.isEmpty)
      }

      "update group of a default object access permission, property must be deleted" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/0000/KMjKHCNQQmC4uHPQwlEexw")
        val newGroupIri   = GroupIri.unsafeFrom(KnoraGroupRepo.builtIn.ProjectMember.id.value)
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionsGroup(permissionIri, newGroupIri, rootUser, UUID.randomUUID()),
          ),
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri.value)
        assert(doap.forGroup.contains(KnoraGroupRepo.builtIn.ProjectMember.id.value))
        assert(doap.forProperty.isEmpty)
      }
    }

    "ask to update hasPermissions of a permission" should {
      "throw ForbiddenException for PermissionChangeHasPermissionsRequestADM if requesting user is not system or project Admin" in {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ"
        val hasPermissions = NonEmptyChunk(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll))

        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              imagesUser02,
              UUID.randomUUID(),
            ),
          ),
        )

        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission $permissionIri can only be queried/updated/deleted by system or project admin.",
        )
      }

      "update hasPermissions of an administrative permission" in {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ"
        val hasPermissions = NonEmptyChunk(PermissionADM.from(Permission.Administrative.ProjectResourceCreateAll))
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )

        val ap = actual.asInstanceOf[AdministrativePermissionGetResponseADM].administrativePermission
        assert(ap.iri == permissionIri)
        ap.hasPermissions.size should be(1)
        assert(ap.hasPermissions.equals(hasPermissions.toSet))
      }

      "ignore irrelevant parameters given in ChangePermissionHasPermissionsApiRequestADM for an administrative permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = Permission.Administrative.ProjectAdminAll.token,
            additionalInformation = Some("aIRI"),
            permissionCode = Some(1),
          ),
        )
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        val ap = actual.asInstanceOf[AdministrativePermissionGetResponseADM].administrativePermission
        assert(ap.iri == permissionIri)
        ap.hasPermissions.size should be(1)
        val expectedSetOfPermissions = Set(PermissionADM.from(Permission.Administrative.ProjectAdminAll))
        assert(ap.hasPermissions.equals(expectedSetOfPermissions))
      }

      "update hasPermissions of a default object access permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM.from(Permission.ObjectAccess.ChangeRights, KnoraGroupRepo.builtIn.Creator.id.value),
          PermissionADM.from(Permission.ObjectAccess.Modify, KnoraGroupRepo.builtIn.ProjectMember.id.value),
        )

        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )

        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        doap.hasPermissions.size should be(2)
        assert(doap.hasPermissions.equals(hasPermissions.toSet))
      }

      "add missing name of the permission, if permissionCode of permission was given in hasPermissions of a default object access permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = "",
            additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
            permissionCode = Some(8),
          ),
        )

        val expectedHasPermissions = Set(
          PermissionADM(
            name = Permission.ObjectAccess.ChangeRights.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.Creator.id.value),
            permissionCode = Some(8),
          ),
        )

        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.hasPermissions.equals(expectedHasPermissions))
      }

      "add missing permissionCode of the permission, if name of permission was given in hasPermissions of a default object access permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = Permission.ObjectAccess.Delete.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = None,
          ),
        )

        val expectedHasPermissions = Set(
          PermissionADM(
            name = Permission.ObjectAccess.Delete.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = Some(7),
          ),
        )
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.hasPermissions.equals(expectedHasPermissions))
      }

      "not update hasPermissions of a default object access permission, if both name and project code of a permission were missing" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val code          = 1
        val name          = Permission.ObjectAccess.Delete.token
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = name,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = Some(code),
          ),
        )
        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"Given permission code $code and permission name $name are not consistent.",
        )
      }

      "not update hasPermissions of a default object access permission, if an invalid name was given for a permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val name          = "invalidName"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = name,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = None,
          ),
        )

        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"Invalid value for name parameter of hasPermissions: $name, it should be one of " +
            s"${Permission.ObjectAccess.allTokens.mkString(", ")}",
        )
      }

      "not update hasPermissions of a default object access permission, if an invalid code was given for a permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val code          = 10
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = Permission.ObjectAccess.Delete.token,
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = Some(code),
          ),
        )

        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"Invalid value for permissionCode parameter of hasPermissions: $code, it should be one of " +
            s"${Permission.ObjectAccess.allCodes.mkString(", ")}",
        )
      }

      "not update hasPermissions of a default object access permission, if given name and project code are not consistent" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = "",
            additionalInformation = Some(KnoraGroupRepo.builtIn.ProjectAdmin.id.value),
            permissionCode = None,
          ),
        )

        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"One of permission code or permission name must be provided for a default object access permission.",
        )
      }
    }
    "ask to update resource class of a permission" should {
      "throw ForbiddenException for PermissionChangeResourceClassRequestADM if requesting user is not system or project Admin" in {
        val permissionIri    = "http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS

        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.updatePermissionResourceClass(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionResourceClassApiRequestADM(resourceClassIri),
              incunabulaMemberUser,
              UUID.randomUUID(),
            ),
          ),
        )

        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission $permissionIri can only be queried/updated/deleted by system or project admin.",
        )
      }
      "update resource class of a default object access permission" in {
        val permissionIri    = "http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS

        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionResourceClass(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionResourceClassApiRequestADM(resourceClassIri),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.forResourceClass.contains(resourceClassIri))
      }

      "update resource class of a default object access permission, and delete group" in {
        val permissionIri    = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionResourceClass(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionResourceClassApiRequestADM(resourceClassIri),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.forResourceClass.contains(resourceClassIri))
        assert(doap.forGroup.isEmpty)
      }

      "not update resource class of an administrative permission" in {
        val permissionIri    = "http://rdfh.ch/permissions/00FF/OySsjGn8QSqIpXUiSYnSSQ"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS
        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.updatePermissionResourceClass(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionResourceClassApiRequestADM(resourceClassIri),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission $permissionIri is of type administrative permission. " +
            s"Only a default object access permission defined for a resource class can be updated.",
        )
      }
    }
    "ask to update property of a permission" should {
      "not update property of an administrative permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/OySsjGn8QSqIpXUiSYnSSQ"
        val propertyIri   = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY

        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.updatePermissionProperty(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionPropertyApiRequestADM(propertyIri),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission $permissionIri is of type administrative permission. " +
            s"Only a default object access permission defined for a property can be updated.",
        )
      }
      "throw ForbiddenException for PermissionChangePropertyRequestADM if requesting user is not system or project Admin" in {
        val permissionIri = "http://rdfh.ch/permissions/0000/KMjKHCNQQmC4uHPQwlEexw"
        val propertyIri   = OntologyConstants.KnoraBase.TextFileValue

        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.updatePermissionProperty(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionPropertyApiRequestADM(propertyIri),
              normalUser,
              UUID.randomUUID(),
            ),
          ),
        )
        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission $permissionIri can only be queried/updated/deleted by system or project admin.",
        )
      }
      "update property of a default object access permission" in {
        val permissionIri = "http://rdfh.ch/permissions/0000/KMjKHCNQQmC4uHPQwlEexw"
        val propertyIri   = OntologyConstants.KnoraBase.TextFileValue

        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionProperty(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionPropertyApiRequestADM(propertyIri),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.forProperty.contains(propertyIri))
      }

      "update property of a default object access permission, delete group" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA"
        val propertyIri   = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY

        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(
            _.updatePermissionProperty(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionPropertyApiRequestADM(propertyIri),
              rootUser,
              UUID.randomUUID(),
            ),
          ),
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.forProperty.contains(propertyIri))
        assert(doap.forGroup.isEmpty)
      }
    }

    "ask to delete a permission" should {
      "throw BadRequestException if given IRI is not a permission IRI" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/RkVssk8XRVO9hZ3VR5IpLA")
        val exit = UnsafeZioRun.run(
          permissionsResponder(_.deletePermission(permissionIri, rootUser, UUID.randomUUID())),
        )
        assertFailsWithA[NotFoundException](
          exit,
          s"Permission with given IRI: ${permissionIri.value} not found.",
        )
      }

      "throw ForbiddenException if user requesting PermissionDeleteResponseADM is not a system or project admin" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA")
        val exit = UnsafeZioRun.run(
          permissionsResponder(
            _.deletePermission(permissionIri, SharedTestDataADM.imagesUser02, UUID.randomUUID()),
          ),
        )
        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission ${permissionIri.value} can only be queried/updated/deleted by system or project admin.",
        )
      }

      "erase a permission with given IRI" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA")
        val actual = UnsafeZioRun.runOrThrow(
          permissionsResponder(_.deletePermission(permissionIri, rootUser, UUID.randomUUID())),
        )
        assert(actual.deleted)
      }
    }
  }
}
