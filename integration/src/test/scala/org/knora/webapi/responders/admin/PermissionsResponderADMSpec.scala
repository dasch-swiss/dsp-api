/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import org.apache.pekko.actor.Status.Failure
import org.apache.pekko.testkit.ImplicitSender
import zio.NonEmptyChunk

import java.util.UUID
import scala.collection.Map

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.webapi.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraBase.EntityPermissionAbbreviations
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsMessagesUtilADM.PermissionTypeAndCodes
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.KnoraSystemInstances
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
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.util.ZioScalaTestUtil.assertFailsWithA

/**
 * This spec is used to test the [[PermissionsResponderADM]] actor.
 */
class PermissionsResponderADMSpec extends CoreSpec with ImplicitSender {

  private val rootUser      = SharedTestDataADM.rootUser
  private val multiuserUser = SharedTestDataADM.multiuserUser
  private val knownUser     = OntologyConstants.KnoraAdmin.KnownUser
  private val unknownUser   = OntologyConstants.KnoraAdmin.UnknownUser
  private val projectAdmin  = OntologyConstants.KnoraAdmin.ProjectAdmin
  private val projectMember = OntologyConstants.KnoraAdmin.ProjectMember
  private val creator       = OntologyConstants.KnoraAdmin.Creator

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path =
        "test_data/generated_test_data/responders.admin.PermissionsResponderV1Spec/additional_permissions-data.ttl",
      name = "http://www.knora.org/data/permissions"
    ),
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula"
    ),
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  "The PermissionsResponderADM" when {

    "ask about the permission profile" should {

      "return the permissions profile (root user)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataADM2.rootUser.projects_info.keys.toSeq,
          groupIris = SharedTestDataADM2.rootUser.groups,
          isInProjectAdminGroups = Seq.empty[IRI],
          isInSystemAdminGroup = true,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataADM2.rootUser.permissionData)
      }

      "return the permissions profile (multi group user)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataADM2.multiuserUser.projects_info.keys.toSeq,
          groupIris = SharedTestDataADM2.multiuserUser.groups,
          isInProjectAdminGroups = Seq(SharedTestDataADM.incunabulaProjectIri, imagesProjectIri),
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataADM2.multiuserUser.permissionData)
      }

      "return the permissions profile (incunabula project admin user)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataADM2.incunabulaProjectAdminUser.projects_info.keys.toSeq,
          groupIris = SharedTestDataADM2.incunabulaProjectAdminUser.groups,
          isInProjectAdminGroups = Seq(SharedTestDataADM.incunabulaProjectIri),
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataADM2.incunabulaProjectAdminUser.permissionData)
      }

      "return the permissions profile (incunabula creator user)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataADM2.incunabulaProjectAdminUser.projects_info.keys.toSeq,
          groupIris = SharedTestDataADM2.incunabulaCreatorUser.groups,
          isInProjectAdminGroups = Seq.empty[IRI],
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataADM2.incunabulaCreatorUser.permissionData)
      }

      "return the permissions profile (incunabula normal project member user)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataADM2.incunabulaProjectAdminUser.projects_info.keys.toSeq,
          groupIris = SharedTestDataADM2.incunabulaMemberUser.groups,
          isInProjectAdminGroups = Seq.empty[IRI],
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataADM2.incunabulaMemberUser.permissionData)
      }

      "return the permissions profile (images user 01)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataADM2.imagesUser01.projects_info.keys.toSeq,
          groupIris = SharedTestDataADM2.imagesUser01.groups,
          isInProjectAdminGroups = Seq(imagesProjectIri),
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataADM2.imagesUser01.permissionData)
      }

      "return the permissions profile (images-reviewer-user)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataADM2.imagesReviewerUser.projects_info.keys.toSeq,
          groupIris = SharedTestDataADM2.imagesReviewerUser.groups,
          isInProjectAdminGroups = Seq.empty[IRI],
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataADM2.imagesReviewerUser.permissionData)
      }

      "return the permissions profile (anything user 01)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataADM2.anythingUser1.projects_info.keys.toSeq,
          groupIris = SharedTestDataADM2.anythingUser1.groups,
          isInProjectAdminGroups = Seq.empty[IRI],
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataADM2.anythingUser1.permissionData)
      }
    }
    "ask for userAdministrativePermissionsGetADM" should {
      "return user's administrative permissions (helper method used in queries before)" in {
        val result: Map[IRI, Set[PermissionADM]] = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.userAdministrativePermissionsGetADM(
            multiuserUser.permissions.groupsPerProject
          )
        )
        result should equal(multiuserUser.permissions.administrativePermissionsPerProject)
      }
    }

    "ask about administrative permissions " should {

      "return all AdministrativePermissions for project" in {
        val result = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.getPermissionsApByProjectIri(imagesProjectIri)
        )
        result shouldEqual AdministrativePermissionsForProjectGetResponseADM(
          Seq(perm002_a1.p, perm002_a3.p, perm002_a2.p)
        )
      }

      "return AdministrativePermission for project and group" in {
        val result = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.getPermissionsApByProjectAndGroupIri(
            imagesProjectIri,
            OntologyConstants.KnoraAdmin.ProjectMember
          )
        )
        result shouldEqual AdministrativePermissionGetResponseADM(perm002_a1.p)
      }

      "return AdministrativePermission for IRI" in {
        appActor ! AdministrativePermissionForIriGetRequestADM(
          administrativePermissionIri = perm002_a1.iri,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(AdministrativePermissionGetResponseADM(perm002_a1.p))
      }
      "throw ForbiddenException for AdministrativePermissionForIriGetRequestADM if requesting user is not system or project admin" in {
        val permissionIri = perm002_a1.iri
        appActor ! AdministrativePermissionForIriGetRequestADM(
          administrativePermissionIri = permissionIri,
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          Failure(
            ForbiddenException(
              s"Permission $permissionIri can only be queried/updated/deleted by system or project admin."
            )
          )
        )
      }
    }

    "asked to create an administrative permission" should {
      "fail and return a 'DuplicateValueException' when permission for project and group combination already exists" in {
        val exit = UnsafeZioRun.run(
          PermissionsResponderADM.createAdministrativePermission(
            CreateAdministrativePermissionAPIRequestADM(
              forProject = imagesProjectIri,
              forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
              hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
            ),
            rootUser,
            UUID.randomUUID()
          )
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"An administrative permission for project: '$imagesProjectIri' and group: '${OntologyConstants.KnoraAdmin.ProjectMember}' combination already exists. " +
            s"This permission currently has the scope '${PermissionUtilADM
                .formatPermissionADMs(perm002_a1.p.hasPermissions, PermissionType.AP)}'. " +
            s"Use its IRI ${perm002_a1.iri} to modify it, if necessary."
        )
      }

      "create and return an administrative permission with a custom IRI" in {
        val customIri = "http://rdfh.ch/permissions/0001/24RD7QcoTKqEJKrDBE885Q"
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.createAdministrativePermission(
            CreateAdministrativePermissionAPIRequestADM(
              id = Some(customIri),
              forProject = SharedTestDataADM.anythingProjectIri,
              forGroup = SharedTestDataADM.thingSearcherGroup.id,
              hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
            ),
            rootUser,
            UUID.randomUUID()
          )
        )
        assert(actual.administrativePermission.iri == customIri)
        assert(actual.administrativePermission.forProject == SharedTestDataADM.anythingProjectIri)
        assert(actual.administrativePermission.forGroup == SharedTestDataADM.thingSearcherGroup.id)
      }

      "create and return an administrative permission even if irrelevant values were given for name and code of its permission" in {
        val customIri = "http://rdfh.ch/permissions/0001/0pd-VUDeShWNJ2Nq3fGGGQ"
        val hasPermissions = Set(
          PermissionADM(
            name = OntologyConstants.KnoraAdmin.ProjectResourceCreateAllPermission,
            additionalInformation = Some("blabla"),
            permissionCode = Some(8)
          )
        )
        val expectedHasPermissions = Set(
          PermissionADM(
            name = OntologyConstants.KnoraAdmin.ProjectResourceCreateAllPermission,
            additionalInformation = None,
            permissionCode = None
          )
        )
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.createAdministrativePermission(
            CreateAdministrativePermissionAPIRequestADM(
              id = Some(customIri),
              forProject = SharedTestDataADM.anythingProjectIri,
              forGroup = OntologyConstants.KnoraAdmin.KnownUser,
              hasPermissions = hasPermissions
            ),
            rootUser,
            UUID.randomUUID()
          )
        )
        assert(actual.administrativePermission.iri == customIri)
        assert(actual.administrativePermission.forGroup == knownUser)
        assert(actual.administrativePermission.forProject == SharedTestDataADM.anythingProjectIri)
        assert(actual.administrativePermission.hasPermissions.equals(expectedHasPermissions))
      }
    }

    "ask to query about object access permissions " should {
      "return object access permissions for a resource" in {
        appActor ! ObjectAccessPermissionsForResourceGetADM(
          resourceIri = perm003_o1.iri,
          requestingUser = rootUser
        )
        expectMsg(Some(perm003_o1.p))
      }

      "return 'ForbiddenException' if the user requesting ObjectAccessPermissionsForResourceGetADM is not ProjectAdmin" in {

        appActor ! ObjectAccessPermissionsForResourceGetADM(
          resourceIri = perm003_o1.iri,
          requestingUser = SharedTestDataADM.incunabulaMemberUser
        )
        expectMsg(
          Failure(ForbiddenException("Object access permissions can only be queried by system and project admin."))
        )
      }

      "return object access permissions for a value" in {
        appActor ! ObjectAccessPermissionsForValueGetADM(
          valueIri = perm003_o2.iri,
          requestingUser = rootUser
        )
        expectMsg(Some(perm003_o2.p))
      }

      "return 'ForbiddenException' if the user requesting ObjectAccessPermissionsForValueGetADM is not ProjectAdmin" in {

        appActor ! ObjectAccessPermissionsForValueGetADM(
          valueIri = perm003_o2.iri,
          requestingUser = SharedTestDataADM.incunabulaMemberUser
        )
        expectMsg(
          Failure(ForbiddenException("Object access permissions can only be queried by system and project admin."))
        )
      }
    }

    "ask to query about default object access permissions " should {

      "return all DefaultObjectAccessPermissions for project" in {
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.getPermissionsDaopByProjectIri(
            ProjectIri.unsafeFrom(imagesProjectIri)
          )
        )
        actual shouldEqual DefaultObjectAccessPermissionsForProjectGetResponseADM(
          Seq(perm002_d2.p, perm0003_a4.p, perm002_d1.p)
        )
      }

      "return DefaultObjectAccessPermission for IRI" in {
        appActor ! DefaultObjectAccessPermissionForIriGetRequestADM(
          defaultObjectAccessPermissionIri = perm002_d1.iri,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          DefaultObjectAccessPermissionGetResponseADM(
            defaultObjectAccessPermission = perm002_d1.p
          )
        )
      }

      "return 'ForbiddenException' if the user requesting DefaultObjectAccessPermissionForIriGetRequestADM is not System or project Admin" in {
        val permissionIri = perm002_d1.iri
        appActor ! DefaultObjectAccessPermissionForIriGetRequestADM(
          defaultObjectAccessPermissionIri = permissionIri,
          requestingUser = SharedTestDataADM.imagesUser02,
          apiRequestID = UUID.randomUUID()
        )

        expectMsg(
          Failure(
            ForbiddenException(
              s"Permission $permissionIri can only be queried/updated/deleted by system or project admin."
            )
          )
        )
      }

      "return DefaultObjectAccessPermission for project and group" in {
        appActor ! DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.incunabulaProjectIri,
          groupIri = Some(OntologyConstants.KnoraAdmin.ProjectMember),
          resourceClassIri = None,
          propertyIri = None,
          requestingUser = rootUser
        )
        expectMsg(
          DefaultObjectAccessPermissionGetResponseADM(
            defaultObjectAccessPermission = perm003_d1.p
          )
        )
      }

      "return DefaultObjectAccessPermission for project and resource class ('incunabula:Page')" in {
        appActor ! DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.incunabulaProjectIri,
          groupIri = None,
          resourceClassIri = Some(SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS),
          propertyIri = None,
          requestingUser = rootUser
        )
        expectMsg(
          DefaultObjectAccessPermissionGetResponseADM(
            defaultObjectAccessPermission = perm003_d2.p
          )
        )
      }

      "return DefaultObjectAccessPermission for project and property ('knora-base:hasStillImageFileValue') (system property)" in {
        appActor ! DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.incunabulaProjectIri,
          groupIri = None,
          resourceClassIri = None,
          propertyIri = Some(OntologyConstants.KnoraBase.HasStillImageFileValue),
          requestingUser = rootUser
        )
        expectMsg(
          DefaultObjectAccessPermissionGetResponseADM(
            defaultObjectAccessPermission = perm001_d3.p
          )
        )
      }
    }

    "ask to create a default object access permission" should {

      "create a DefaultObjectAccessPermission for project and group" in {
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.createDefaultObjectAccessPermission(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = SharedTestDataADM.anythingProjectIri,
              forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
              hasPermissions = Set(PermissionADM.restrictedViewPermission(SharedTestDataADM.thingSearcherGroup.id))
            ),
            rootUser,
            UUID.randomUUID()
          )
        )

        assert(actual.defaultObjectAccessPermission.forProject == SharedTestDataADM.anythingProjectIri)
        assert(actual.defaultObjectAccessPermission.forGroup.contains(SharedTestDataADM.thingSearcherGroup.id))
        assert(
          actual.defaultObjectAccessPermission.hasPermissions.contains(
            PermissionADM.restrictedViewPermission(SharedTestDataADM.thingSearcherGroup.id)
          )
        )
      }

      "create a DefaultObjectAccessPermission for project and group with custom IRI" in {
        val customIri = "http://rdfh.ch/permissions/0001/4PnSvolsTEa86KJ2EG76SQ"
        val received = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.createDefaultObjectAccessPermission(
            createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
              id = Some(customIri),
              forProject = SharedTestDataADM.anythingProjectIri,
              forGroup = Some(OntologyConstants.KnoraAdmin.UnknownUser),
              hasPermissions = Set(PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser))
            ),
            rootUser,
            UUID.randomUUID()
          )
        )
        assert(received.defaultObjectAccessPermission.iri == customIri)
        assert(received.defaultObjectAccessPermission.forGroup.contains(unknownUser))
        assert(received.defaultObjectAccessPermission.forProject == SharedTestDataADM.anythingProjectIri)
        assert(
          received.defaultObjectAccessPermission.hasPermissions
            .contains(PermissionADM.restrictedViewPermission(unknownUser))
        )
      }

      "create a DefaultObjectAccessPermission for project and resource class" in {
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.createDefaultObjectAccessPermission(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = imagesProjectIri,
              forResourceClass = Some(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
              hasPermissions = Set(PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.KnownUser))
            ),
            rootUser,
            UUID.randomUUID()
          )
        )
        assert(actual.defaultObjectAccessPermission.forProject == imagesProjectIri)
        assert(
          actual.defaultObjectAccessPermission.forResourceClass
            .contains(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS)
        )
        assert(
          actual.defaultObjectAccessPermission.hasPermissions
            .contains(PermissionADM.modifyPermission(knownUser))
        )
      }

      "create a DefaultObjectAccessPermission for project and property" in {
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.createDefaultObjectAccessPermission(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = imagesProjectIri,
              forProperty = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
              hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator))
            ),
            rootUser,
            UUID.randomUUID()
          )
        )
        assert(actual.defaultObjectAccessPermission.forProject == imagesProjectIri)
        assert(
          actual.defaultObjectAccessPermission.forProperty
            .contains(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY)
        )
        assert(
          actual.defaultObjectAccessPermission.hasPermissions
            .contains(PermissionADM.changeRightsPermission(creator))
        )
      }

      "fail and return a 'DuplicateValueException' when a doap permission for project and group combination already exists" in {
        val exit = UnsafeZioRun.run(
          PermissionsResponderADM.createDefaultObjectAccessPermission(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = SharedTestDataADM2.incunabulaProjectIri,
              forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
              hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember))
            ),
            rootUser,
            UUID.randomUUID()
          )
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"A default object access permission for project: '${SharedTestDataADM2.incunabulaProjectIri}' and group: '${OntologyConstants.KnoraAdmin.ProjectMember}' " +
            "combination already exists. " +
            s"This permission currently has the scope '${PermissionUtilADM
                .formatPermissionADMs(perm003_d1.p.hasPermissions, PermissionType.OAP)}'. " +
            s"Use its IRI ${perm003_d1.iri} to modify it, if necessary."
        )
      }

      "fail and return a 'DuplicateValueException' when a doap permission for project and resourceClass combination already exists" in {
        val exit = UnsafeZioRun.run(
          PermissionsResponderADM.createDefaultObjectAccessPermission(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = SharedTestDataADM2.incunabulaProjectIri,
              forResourceClass = Some(SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS),
              hasPermissions = Set(
                PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
                PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
              )
            ),
            rootUser,
            UUID.randomUUID()
          )
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"A default object access permission for project: '${SharedTestDataADM2.incunabulaProjectIri}' and resourceClass: '${SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS}' " +
            "combination already exists. " +
            s"This permission currently has the scope '${PermissionUtilADM
                .formatPermissionADMs(perm003_d2.p.hasPermissions, PermissionType.OAP)}'. " +
            s"Use its IRI ${perm003_d2.iri} to modify it, if necessary."
        )
      }

      "fail and return a 'DuplicateValueException' when a doap permission for project and property combination already exists" in {
        val exit = UnsafeZioRun.run(
          PermissionsResponderADM.createDefaultObjectAccessPermission(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = SharedTestDataADM2.incunabulaProjectIri,
              forProperty = Some(SharedOntologyTestDataADM.INCUNABULA_PartOf_Property),
              hasPermissions = Set(
                PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.KnownUser)
              )
            ),
            rootUser,
            UUID.randomUUID()
          )
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"A default object access permission for project: '${SharedTestDataADM2.incunabulaProjectIri}' and property: '${SharedOntologyTestDataADM.INCUNABULA_PartOf_Property}' " +
            "combination already exists. " +
            s"This permission currently has the scope '${PermissionUtilADM
                .formatPermissionADMs(perm003_d4.p.hasPermissions, PermissionType.OAP)}'. " +
            s"Use its IRI ${perm003_d4.iri} to modify it, if necessary."
        )
      }

      "fail and return a 'DuplicateValueException' when a doap permission for project, resource class, and property combination already exists" in {
        val exit = UnsafeZioRun.run(
          PermissionsResponderADM.createDefaultObjectAccessPermission(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = SharedTestDataADM2.incunabulaProjectIri,
              forResourceClass = Some(SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS),
              forProperty = Some(SharedOntologyTestDataADM.INCUNABULA_PartOf_Property),
              hasPermissions = Set(
                PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
                PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
              )
            ),
            rootUser,
            UUID.randomUUID()
          )
        )
        assertFailsWithA[DuplicateValueException](
          exit,
          s"A default object access permission for project: '${SharedTestDataADM2.incunabulaProjectIri}' and resourceClass: '${SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS}' " +
            s"and property: '${SharedOntologyTestDataADM.INCUNABULA_PartOf_Property}' " +
            "combination already exists. " +
            s"This permission currently has the scope '${PermissionUtilADM
                .formatPermissionADMs(perm003_d5.p.hasPermissions, PermissionType.OAP)}'. " +
            s"Use its IRI ${perm003_d5.iri} to modify it, if necessary."
        )
      }

      "create a DefaultObjectAccessPermission for project and property even if name of a permission was missing" in {
        val hasPermissions = Set(
          PermissionADM(
            name = "",
            additionalInformation = Some(OntologyConstants.KnoraAdmin.UnknownUser),
            permissionCode = Some(1)
          )
        )
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.createDefaultObjectAccessPermission(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = imagesProjectIri,
              forGroup = Some(OntologyConstants.KnoraAdmin.UnknownUser),
              hasPermissions = hasPermissions
            ),
            rootUser,
            UUID.randomUUID()
          )
        )
        assert(actual.defaultObjectAccessPermission.forProject == imagesProjectIri)
        assert(actual.defaultObjectAccessPermission.forGroup.contains(unknownUser))
        assert(
          actual.defaultObjectAccessPermission.hasPermissions.contains(
            PermissionADM.restrictedViewPermission(unknownUser)
          )
        )
      }

      "create a DefaultObjectAccessPermission for project and property even if permissionCode of a permission was missing" in {
        val hasPermissions = Set(
          PermissionADM(
            name = OntologyConstants.KnoraBase.DeletePermission,
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = None
          )
        )
        val expectedPermissions = Set(
          PermissionADM(
            name = OntologyConstants.KnoraBase.DeletePermission,
            additionalInformation = Some(projectAdmin),
            permissionCode = Some(7)
          )
        )
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.createDefaultObjectAccessPermission(
            CreateDefaultObjectAccessPermissionAPIRequestADM(
              forProject = imagesProjectIri,
              forGroup = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
              hasPermissions = hasPermissions
            ),
            rootUser,
            UUID.randomUUID()
          )
        )
        assert(actual.defaultObjectAccessPermission.forProject == imagesProjectIri)
        assert(actual.defaultObjectAccessPermission.forGroup.contains(projectAdmin))
        assert(actual.defaultObjectAccessPermission.hasPermissions.equals(expectedPermissions))
      }
    }

    "ask to get all permissions" should {

      "return all permissions for 'image' project" in {
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.getPermissionsByProjectIri(ProjectIri.unsafeFrom(imagesProjectIri))
        )
        actual.allPermissions.size should be(10)
      }

      "return all permissions for 'incunabula' project" in {
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.getPermissionsByProjectIri(
            ProjectIri.unsafeFrom(SharedTestDataADM.incunabulaProjectIri)
          )
        )
        actual shouldEqual
          PermissionsForProjectGetResponseADM(allPermissions =
            Set(
              PermissionInfoADM(
                perm003_a1.iri,
                OntologyConstants.KnoraAdmin.AdministrativePermission
              ),
              PermissionInfoADM(
                perm003_a2.iri,
                OntologyConstants.KnoraAdmin.AdministrativePermission
              ),
              PermissionInfoADM(
                perm003_d1.iri,
                OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission
              ),
              PermissionInfoADM(
                perm003_d2.iri,
                OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission
              ),
              PermissionInfoADM(
                perm003_d3.iri,
                OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission
              ),
              PermissionInfoADM(
                perm003_d4.iri,
                OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission
              ),
              PermissionInfoADM(
                perm003_d5.iri,
                OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission
              )
            )
          )
      }
    }

    "ask for default object access permissions 'string'" should {

      "return the default object access permissions 'string' for the 'knora-base:LinkObj' resource class (system resource class)" in {
        appActor ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri = SharedTestDataADM.incunabulaProjectIri,
          resourceClassIri = OntologyConstants.KnoraBase.LinkObj,
          targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(
          DefaultObjectAccessPermissionsStringResponseADM(
            "M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser"
          )
        )
      }

      "return the default object access permissions 'string' for the 'knora-base:hasStillImageFileValue' property (system property)" in {
        appActor ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = SharedTestDataADM.incunabulaProjectIri,
          resourceClassIri = OntologyConstants.KnoraBase.StillImageRepresentation,
          propertyIri = OntologyConstants.KnoraBase.HasStillImageFileValue,
          targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(
          DefaultObjectAccessPermissionsStringResponseADM(
            "M knora-admin:Creator,knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser"
          )
        )
      }

      "return the default object access permissions 'string' for the 'incunabula:book' resource class (project resource class)" in {
        appActor ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri = SharedTestDataADM.incunabulaProjectIri,
          resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS,
          targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(
          DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"
          )
        )
      }

      "return the default object access permissions 'string' for the 'incunabula:page' resource class (project resource class)" in {
        appActor ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri = SharedTestDataADM.incunabulaProjectIri,
          resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS,
          targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(
          DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"
          )
        )
      }

      "return the default object access permissions 'string' for the 'anything:hasInterval' property" in {
        appActor ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = SharedTestDataADM.anythingProjectIri,
          resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
          propertyIri = "http://www.knora.org/ontology/0001/anything#hasInterval",
          targetUser = SharedTestDataADM.anythingUser2,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(
          DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"
          )
        )
      }

      "return the default object access permissions 'string' for the 'anything:Thing' class" in {
        appActor ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri = SharedTestDataADM.anythingProjectIri,
          resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
          targetUser = SharedTestDataADM.anythingUser2,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(
          DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"
          )
        )
      }

      "return the default object access permissions 'string' for the 'anything:Thing' class and 'anything:hasText' property" in {
        appActor ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = SharedTestDataADM.anythingProjectIri,
          resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
          propertyIri = "http://www.knora.org/ontology/0001/anything#hasText",
          targetUser = SharedTestDataADM.anythingUser1,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator"))
      }

      "return the default object access permissions 'string' for the 'images:Bild' class and 'anything:hasText' property" in {
        appActor ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
          projectIri = SharedTestDataADM.anythingProjectIri,
          resourceClassIri = s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#bild",
          propertyIri = "http://www.knora.org/ontology/0001/anything#hasText",
          targetUser = SharedTestDataADM.anythingUser2,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(
          DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"
          )
        )
      }

      "return the default object access permissions 'string' for the 'anything:Thing' resource class for the root user (system admin and not member of project)" in {
        appActor ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
          projectIri = SharedTestDataADM.anythingProjectIri,
          resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
          targetUser = SharedTestDataADM.rootUser,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(
          DefaultObjectAccessPermissionsStringResponseADM(
            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"
          )
        )
      }

    }

    "ask to get the permission by IRI" should {
      "not return the permission if requesting user does not have permission to see it" in {
        val permissionIri = perm002_a1.iri
        appActor ! PermissionByIriGetRequestADM(
          permissionIri = perm002_a1.iri,
          requestingUser = SharedTestDataADM.imagesUser02
        )
        expectMsg(
          Failure(
            ForbiddenException(
              s"Permission $permissionIri can only be queried/updated/deleted by system or project admin."
            )
          )
        )
      }

      "return an administrative permission" in {
        appActor ! PermissionByIriGetRequestADM(
          permissionIri = perm002_a1.iri,
          requestingUser = rootUser
        )
        expectMsg(AdministrativePermissionGetResponseADM(perm002_a1.p))
      }

      "return a default object access permission" in {
        appActor ! PermissionByIriGetRequestADM(
          permissionIri = perm002_d1.iri,
          requestingUser = rootUser
        )
        expectMsg(DefaultObjectAccessPermissionGetResponseADM(perm002_d1.p))
      }
    }

    "ask to update group of a permission" should {
      "update group of an administrative permission" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM
            .updatePermissionsGroup(permissionIri, newGroupIri, rootUser, UUID.randomUUID())
        )
        val ap = actual.asInstanceOf[AdministrativePermissionGetResponseADM].administrativePermission
        assert(ap.iri == permissionIri.value)
        assert(ap.forGroup == newGroupIri.value)
      }

      "throw ForbiddenException for PermissionChangeGroupRequestADM if requesting user is not system or project Admin" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        val exit = UnsafeZioRun.run(
          PermissionsResponderADM
            .updatePermissionsGroup(permissionIri, newGroupIri, imagesUser02, UUID.randomUUID())
        )
        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission ${permissionIri.value} can only be queried/updated/deleted by system or project admin."
        )
      }

      "update group of a default object access permission" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM
            .updatePermissionsGroup(permissionIri, newGroupIri, rootUser, UUID.randomUUID())
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri.value)
        assert(doap.forGroup.contains(newGroupIri.value))
      }

      "update group of a default object access permission, resource class must be deleted" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA")
        val newGroupIri   = GroupIri.unsafeFrom(projectMember)
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM
            .updatePermissionsGroup(permissionIri, newGroupIri, rootUser, UUID.randomUUID())
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri.value)
        assert(doap.forGroup.contains(projectMember))
        assert(doap.forResourceClass.isEmpty)
      }

      "update group of a default object access permission, property must be deleted" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/0000/KMjKHCNQQmC4uHPQwlEexw")
        val newGroupIri   = GroupIri.unsafeFrom(projectMember)
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM
            .updatePermissionsGroup(permissionIri, newGroupIri, rootUser, UUID.randomUUID())
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri.value)
        assert(doap.forGroup.contains(projectMember))
        assert(doap.forProperty.isEmpty)
      }
    }

    "ask to update hasPermissions of a permission" should {
      "throw ForbiddenException for PermissionChangeHasPermissionsRequestADM if requesting user is not system or project Admin" in {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ"
        val hasPermissions = NonEmptyChunk(PermissionADM.ProjectResourceCreateAllPermission)

        val exit = UnsafeZioRun.run(
          PermissionsResponderADM
            .updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              imagesUser02,
              UUID.randomUUID()
            )
        )

        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission $permissionIri can only be queried/updated/deleted by system or project admin."
        )
      }

      "update hasPermissions of an administrative permission" in {
        val permissionIri  = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ"
        val hasPermissions = NonEmptyChunk(PermissionADM.ProjectResourceCreateAllPermission)
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM
            .updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID()
            )
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
            name = OntologyConstants.KnoraAdmin.ProjectAdminAllPermission,
            additionalInformation = Some("aIRI"),
            permissionCode = Some(1)
          )
        )
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM
            .updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID()
            )
        )
        val ap = actual.asInstanceOf[AdministrativePermissionGetResponseADM].administrativePermission
        assert(ap.iri == permissionIri)
        ap.hasPermissions.size should be(1)
        val expectedSetOfPermissions = Set(PermissionADM.ProjectAdminAllPermission)
        assert(ap.hasPermissions.equals(expectedSetOfPermissions))
      }

      "update hasPermissions of a default object access permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM.changeRightsPermission(creator),
          PermissionADM.modifyPermission(projectMember)
        )

        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM
            .updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID()
            )
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
            additionalInformation = Some(OntologyConstants.KnoraAdmin.Creator),
            permissionCode = Some(8)
          )
        )

        val expectedHasPermissions = Set(
          PermissionADM(
            name = OntologyConstants.KnoraBase.ChangeRightsPermission,
            additionalInformation = Some(creator),
            permissionCode = Some(8)
          )
        )

        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM
            .updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID()
            )
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.hasPermissions.equals(expectedHasPermissions))
      }

      "add missing permissionCode of the permission, if name of permission was given in hasPermissions of a default object access permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = OntologyConstants.KnoraBase.DeletePermission,
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = None
          )
        )

        val expectedHasPermissions = Set(
          PermissionADM(
            name = OntologyConstants.KnoraBase.DeletePermission,
            additionalInformation = Some(projectAdmin),
            permissionCode = Some(7)
          )
        )
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM
            .updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID()
            )
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.hasPermissions.equals(expectedHasPermissions))
      }

      "not update hasPermissions of a default object access permission, if both name and project code of a permission were missing" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val code          = 1
        val name          = OntologyConstants.KnoraBase.DeletePermission
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = name,
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = Some(code)
          )
        )
        val exit = UnsafeZioRun.run(
          PermissionsResponderADM
            .updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID()
            )
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"Given permission code $code and permission name $name are not consistent."
        )
      }

      "not update hasPermissions of a default object access permission, if an invalid name was given for a permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val name          = "invalidName"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = name,
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = None
          )
        )

        val exit = UnsafeZioRun.run(
          PermissionsResponderADM
            .updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID()
            )
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"Invalid value for name parameter of hasPermissions: $name, it should be one of " +
            s"${EntityPermissionAbbreviations.toString}"
        )
      }

      "not update hasPermissions of a default object access permission, if an invalid code was given for a permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val code          = 10
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = OntologyConstants.KnoraBase.DeletePermission,
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = Some(code)
          )
        )

        val exit = UnsafeZioRun.run(
          PermissionsResponderADM
            .updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID()
            )
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"Invalid value for permissionCode parameter of hasPermissions: $code, it should be one of " +
            s"${PermissionTypeAndCodes.values.toString}"
        )
      }

      "not update hasPermissions of a default object access permission, if given name and project code are not consistent" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val hasPermissions = NonEmptyChunk(
          PermissionADM(
            name = "",
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = None
          )
        )

        val exit = UnsafeZioRun.run(
          PermissionsResponderADM
            .updatePermissionHasPermissions(
              PermissionIri.unsafeFrom(permissionIri),
              hasPermissions,
              rootUser,
              UUID.randomUUID()
            )
        )
        assertFailsWithA[BadRequestException](
          exit,
          s"One of permission code or permission name must be provided for a default object access permission."
        )
      }
    }
    "ask to update resource class of a permission" should {
      "throw ForbiddenException for PermissionChangeResourceClassRequestADM if requesting user is not system or project Admin" in {
        val permissionIri    = "http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS

        val exit = UnsafeZioRun.run(
          PermissionsResponderADM
            .updatePermissionResourceClass(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionResourceClassApiRequestADM(resourceClassIri),
              incunabulaMemberUser,
              UUID.randomUUID()
            )
        )

        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission $permissionIri can only be queried/updated/deleted by system or project admin."
        )
      }
      "update resource class of a default object access permission" in {
        val permissionIri    = "http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS

        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM
            .updatePermissionResourceClass(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionResourceClassApiRequestADM(resourceClassIri),
              rootUser,
              UUID.randomUUID()
            )
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.forResourceClass.contains(resourceClassIri))
      }

      "update resource class of a default object access permission, and delete group" in {
        val permissionIri    = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM
            .updatePermissionResourceClass(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionResourceClassApiRequestADM(resourceClassIri),
              rootUser,
              UUID.randomUUID()
            )
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
          PermissionsResponderADM
            .updatePermissionResourceClass(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionResourceClassApiRequestADM(resourceClassIri),
              rootUser,
              UUID.randomUUID()
            )
        )
        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission $permissionIri is of type administrative permission. " +
            s"Only a default object access permission defined for a resource class can be updated."
        )
      }
    }
    "ask to update property of a permission" should {
      "not update property of an administrative permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/OySsjGn8QSqIpXUiSYnSSQ"
        val propertyIri   = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY

        val exit = UnsafeZioRun.run(
          PermissionsResponderADM
            .updatePermissionProperty(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionPropertyApiRequestADM(propertyIri),
              rootUser,
              UUID.randomUUID()
            )
        )
        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission $permissionIri is of type administrative permission. " +
            s"Only a default object access permission defined for a property can be updated."
        )
      }
      "throw ForbiddenException for PermissionChangePropertyRequestADM if requesting user is not system or project Admin" in {
        val permissionIri = "http://rdfh.ch/permissions/0000/KMjKHCNQQmC4uHPQwlEexw"
        val propertyIri   = OntologyConstants.KnoraBase.TextFileValue

        val exit = UnsafeZioRun.run(
          PermissionsResponderADM
            .updatePermissionProperty(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionPropertyApiRequestADM(propertyIri),
              normalUser,
              UUID.randomUUID()
            )
        )
        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission $permissionIri can only be queried/updated/deleted by system or project admin."
        )
      }
      "update property of a default object access permission" in {
        val permissionIri = "http://rdfh.ch/permissions/0000/KMjKHCNQQmC4uHPQwlEexw"
        val propertyIri   = OntologyConstants.KnoraBase.TextFileValue

        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM
            .updatePermissionProperty(
              PermissionIri.unsafeFrom(permissionIri),
              ChangePermissionPropertyApiRequestADM(propertyIri),
              rootUser,
              UUID.randomUUID()
            )
        )
        val doap = actual.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM].defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.forProperty.contains(propertyIri))
      }

      "update property of a default object access permission, delete group" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA"
        val propertyIri   = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY

        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.updatePermissionProperty(
            PermissionIri.unsafeFrom(permissionIri),
            ChangePermissionPropertyApiRequestADM(propertyIri),
            rootUser,
            UUID.randomUUID()
          )
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
          PermissionsResponderADM.deletePermission(permissionIri, rootUser, UUID.randomUUID())
        )
        assertFailsWithA[NotFoundException](
          exit,
          s"Permission with given IRI: ${permissionIri.value} not found."
        )
      }

      "throw ForbiddenException if user requesting PermissionDeleteResponseADM is not a system or project admin" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA")
        val exit = UnsafeZioRun.run(
          PermissionsResponderADM.deletePermission(permissionIri, SharedTestDataADM.imagesUser02, UUID.randomUUID())
        )
        assertFailsWithA[ForbiddenException](
          exit,
          s"Permission ${permissionIri.value} can only be queried/updated/deleted by system or project admin."
        )
      }

      "erase a permission with given IRI" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA")
        val actual = UnsafeZioRun.runOrThrow(
          PermissionsResponderADM.deletePermission(permissionIri, rootUser, UUID.randomUUID())
        )
        assert(actual.deleted)
      }
    }
  }
}
