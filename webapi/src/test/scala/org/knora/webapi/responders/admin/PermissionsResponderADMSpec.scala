/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import akka.actor.Status.Failure
import akka.testkit.ImplicitSender
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.PrivateMethodTester

import java.util.UUID
import scala.collection.Map
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

import dsp.errors.BadRequestException
import dsp.errors.DuplicateValueException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.webapi._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.OntologyConstants.KnoraBase.EntityPermissionAbbreviations
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsMessagesUtilADM.PermissionTypeAndCodes
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedPermissionsTestData._
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV1
import org.knora.webapi.util.cache.CacheUtil

/**
 * This spec is used to test the [[PermissionsResponderADM]] actor.
 */
class PermissionsResponderADMSpec extends CoreSpec with ImplicitSender with PrivateMethodTester {

  private val rootUser      = SharedTestDataADM.rootUser
  private val multiuserUser = SharedTestDataADM.multiuserUser

  /* define private method access */
  private val userAdministrativePermissionsGetADM =
    PrivateMethod[Future[Map[IRI, Set[PermissionADM]]]](Symbol("userAdministrativePermissionsGetADM"))
  PrivateMethod[Future[Set[PermissionADM]]](Symbol("defaultObjectAccessPermissionsForGroupsGetADM"))

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/responders.admin.PermissionsResponderV1Spec/additional_permissions-data.ttl",
      name = "http://www.knora.org/data/permissions"
    ),
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  "The PermissionsResponderADM" when {

    "ask about the permission profile" should {

      "return the permissions profile (root user)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataV1.rootUser.projects_info.keys.toSeq,
          groupIris = SharedTestDataV1.rootUser.groups,
          isInProjectAdminGroups = Seq.empty[IRI],
          isInSystemAdminGroup = true,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataV1.rootUser.permissionData)
      }

      "return the permissions profile (multi group user)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataV1.multiuserUser.projects_info.keys.toSeq,
          groupIris = SharedTestDataV1.multiuserUser.groups,
          isInProjectAdminGroups = Seq(SharedTestDataADM.INCUNABULA_PROJECT_IRI, SharedTestDataADM.IMAGES_PROJECT_IRI),
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataV1.multiuserUser.permissionData)
      }

      "return the permissions profile (incunabula project admin user)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataV1.incunabulaProjectAdminUser.projects_info.keys.toSeq,
          groupIris = SharedTestDataV1.incunabulaProjectAdminUser.groups,
          isInProjectAdminGroups = Seq(SharedTestDataADM.INCUNABULA_PROJECT_IRI),
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataV1.incunabulaProjectAdminUser.permissionData)
      }

      "return the permissions profile (incunabula creator user)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataV1.incunabulaProjectAdminUser.projects_info.keys.toSeq,
          groupIris = SharedTestDataV1.incunabulaCreatorUser.groups,
          isInProjectAdminGroups = Seq.empty[IRI],
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataV1.incunabulaCreatorUser.permissionData)
      }

      "return the permissions profile (incunabula normal project member user)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataV1.incunabulaProjectAdminUser.projects_info.keys.toSeq,
          groupIris = SharedTestDataV1.incunabulaMemberUser.groups,
          isInProjectAdminGroups = Seq.empty[IRI],
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataV1.incunabulaMemberUser.permissionData)
      }

      "return the permissions profile (images user 01)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataV1.imagesUser01.projects_info.keys.toSeq,
          groupIris = SharedTestDataV1.imagesUser01.groups,
          isInProjectAdminGroups = Seq(SharedTestDataADM.IMAGES_PROJECT_IRI),
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataV1.imagesUser01.permissionData)
      }

      "return the permissions profile (images-reviewer-user)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataV1.imagesReviewerUser.projects_info.keys.toSeq,
          groupIris = SharedTestDataV1.imagesReviewerUser.groups,
          isInProjectAdminGroups = Seq.empty[IRI],
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataV1.imagesReviewerUser.permissionData)
      }

      "return the permissions profile (anything user 01)" in {
        appActor ! PermissionDataGetADM(
          projectIris = SharedTestDataV1.anythingUser1.projects_info.keys.toSeq,
          groupIris = SharedTestDataV1.anythingUser1.groups,
          isInProjectAdminGroups = Seq.empty[IRI],
          isInSystemAdminGroup = false,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )
        expectMsg(SharedTestDataV1.anythingUser1.permissionData)
      }
    }
    "ask for userAdministrativePermissionsGetADM" should {
      "return user's administrative permissions (helper method used in queries before)" in {
        val f: Future[Map[IRI, Set[PermissionADM]]] =
          responderUnderTest invokePrivate userAdministrativePermissionsGetADM(
            multiuserUser.permissions.groupsPerProject
          )
        val result: Map[IRI, Set[PermissionADM]] = Await.result(f, 1.seconds)
        result should equal(multiuserUser.permissions.administrativePermissionsPerProject)
      }
    }

    "ask about administrative permissions " should {

      "return all AdministrativePermissions for project " in {
        appActor ! AdministrativePermissionsForProjectGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          AdministrativePermissionsForProjectGetResponseADM(
            Seq(perm002_a2.p, perm002_a3.p, perm002_a1.p)
          )
        )
      }

      "return AdministrativePermission for project and group" in {
        appActor ! AdministrativePermissionForProjectGroupGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          groupIri = OntologyConstants.KnoraAdmin.ProjectMember,
          requestingUser = rootUser
        )
        expectMsg(AdministrativePermissionGetResponseADM(perm002_a1.p))
      }

      "return AdministrativePermission for IRI " in {
        appActor ! AdministrativePermissionForIriGetRequestADM(
          administrativePermissionIri = perm002_a1.iri,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(AdministrativePermissionGetResponseADM(perm002_a1.p))
      }
      "throw ForbiddenException for AdministrativePermissionForIriGetRequestADM if requesting user is not system or project admin " in {
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
        appActor ! AdministrativePermissionCreateRequestADM(
          createRequest = CreateAdministrativePermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
            hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          Failure(
            DuplicateValueException(
              s"An administrative permission for project: '${SharedTestDataADM.IMAGES_PROJECT_IRI}' and group: '${OntologyConstants.KnoraAdmin.ProjectMember}' combination already exists. " +
                s"This permission currently has the scope '${PermissionUtilADM
                    .formatPermissionADMs(perm002_a1.p.hasPermissions, PermissionType.AP)}'. " +
                s"Use its IRI ${perm002_a1.iri} to modify it, if necessary."
            )
          )
        )
      }

      "create and return an administrative permission with a custom IRI" in {
        val customIri = "http://rdfh.ch/permissions/0001/24RD7QcoTKqEJKrDBE885Q"
        appActor ! AdministrativePermissionCreateRequestADM(
          createRequest = CreateAdministrativePermissionAPIRequestADM(
            id = Some(customIri),
            forProject = SharedTestDataADM.ANYTHING_PROJECT_IRI,
            forGroup = SharedTestDataADM.thingSearcherGroup.id,
            hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: AdministrativePermissionCreateResponseADM =
          expectMsgType[AdministrativePermissionCreateResponseADM]
        assert(received.administrativePermission.iri == customIri)
        assert(received.administrativePermission.forProject == SharedTestDataADM.ANYTHING_PROJECT_IRI)
        assert(received.administrativePermission.forGroup == SharedTestDataADM.thingSearcherGroup.id)
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
        appActor ! AdministrativePermissionCreateRequestADM(
          createRequest = CreateAdministrativePermissionAPIRequestADM(
            id = Some(customIri),
            forProject = SharedTestDataADM.ANYTHING_PROJECT_IRI,
            forGroup = OntologyConstants.KnoraAdmin.KnownUser,
            hasPermissions = hasPermissions
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: AdministrativePermissionCreateResponseADM =
          expectMsgType[AdministrativePermissionCreateResponseADM]
        assert(received.administrativePermission.iri == customIri)
        assert(received.administrativePermission.forProject == SharedTestDataADM.ANYTHING_PROJECT_IRI)
        assert(received.administrativePermission.forGroup == OntologyConstants.KnoraAdmin.KnownUser)
        assert(received.administrativePermission.hasPermissions.equals(expectedHasPermissions))
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
        appActor ! DefaultObjectAccessPermissionsForProjectGetRequestADM(
          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )

        expectMsg(
          DefaultObjectAccessPermissionsForProjectGetResponseADM(
            defaultObjectAccessPermissions = Seq(perm002_d2.p, perm002_d1.p, perm0003_a4.p)
          )
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
          projectIri = SharedTestDataADM.INCUNABULA_PROJECT_IRI,
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
          projectIri = SharedTestDataADM.INCUNABULA_PROJECT_IRI,
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
          projectIri = SharedTestDataADM.INCUNABULA_PROJECT_IRI,
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

      "cache DefaultObjectAccessPermission" in {
        appActor ! DefaultObjectAccessPermissionGetRequestADM(
          projectIri = SharedTestDataADM.INCUNABULA_PROJECT_IRI,
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

        val key = perm001_d3.p.cacheKey
        val maybePermission =
          CacheUtil.get[DefaultObjectAccessPermissionADM](PermissionsMessagesUtilADM.PermissionsCacheName, key)
        maybePermission should be(Some(perm001_d3.p))
      }
    }

    "ask to create a default object access permission" should {

      "create a DefaultObjectAccessPermission for project and group" in {
        appActor ! DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.ANYTHING_PROJECT_IRI,
            forGroup = Some(SharedTestDataADM.thingSearcherGroup.id),
            hasPermissions = Set(PermissionADM.restrictedViewPermission(SharedTestDataADM.thingSearcherGroup.id))
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionCreateResponseADM =
          expectMsgType[DefaultObjectAccessPermissionCreateResponseADM]
        assert(received.defaultObjectAccessPermission.forProject == SharedTestDataADM.ANYTHING_PROJECT_IRI)
        assert(received.defaultObjectAccessPermission.forGroup.contains(SharedTestDataADM.thingSearcherGroup.id))
        assert(
          received.defaultObjectAccessPermission.hasPermissions
            .contains(PermissionADM.restrictedViewPermission(SharedTestDataADM.thingSearcherGroup.id))
        )
      }

      "create a DefaultObjectAccessPermission for project and group with custom IRI" in {
        val customIri = "http://rdfh.ch/permissions/0001/4PnSvolsTEa86KJ2EG76SQ"
        appActor ! DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            id = Some(customIri),
            forProject = SharedTestDataADM.ANYTHING_PROJECT_IRI,
            forGroup = Some(OntologyConstants.KnoraAdmin.UnknownUser),
            hasPermissions = Set(PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser))
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionCreateResponseADM =
          expectMsgType[DefaultObjectAccessPermissionCreateResponseADM]
        assert(received.defaultObjectAccessPermission.iri == customIri)
        assert(received.defaultObjectAccessPermission.forProject == SharedTestDataADM.ANYTHING_PROJECT_IRI)
        assert(received.defaultObjectAccessPermission.forGroup.contains(OntologyConstants.KnoraAdmin.UnknownUser))
        assert(
          received.defaultObjectAccessPermission.hasPermissions
            .contains(PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser))
        )
      }

      "create a DefaultObjectAccessPermission for project and resource class" in {
        appActor ! DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forResourceClass = Some(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS),
            hasPermissions = Set(PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.KnownUser))
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionCreateResponseADM =
          expectMsgType[DefaultObjectAccessPermissionCreateResponseADM]
        assert(received.defaultObjectAccessPermission.forProject == SharedTestDataADM.IMAGES_PROJECT_IRI)
        assert(
          received.defaultObjectAccessPermission.forResourceClass
            .contains(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS)
        )
        assert(
          received.defaultObjectAccessPermission.hasPermissions
            .contains(PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.KnownUser))
        )

      }

      "create a DefaultObjectAccessPermission for project and property" in {
        appActor ! DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forProperty = Some(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator))
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionCreateResponseADM =
          expectMsgType[DefaultObjectAccessPermissionCreateResponseADM]
        assert(received.defaultObjectAccessPermission.forProject == SharedTestDataADM.IMAGES_PROJECT_IRI)
        assert(
          received.defaultObjectAccessPermission.forProperty.contains(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY)
        )
        assert(
          received.defaultObjectAccessPermission.hasPermissions
            .contains(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator))
        )
      }

      "fail and return a 'DuplicateValueException' when a doap permission for project and group combination already exists" in {
        appActor ! DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
            forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
            hasPermissions = Set(PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectMember))
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          Failure(
            DuplicateValueException(
              s"A default object access permission for project: '${SharedTestDataV1.INCUNABULA_PROJECT_IRI}' and group: '${OntologyConstants.KnoraAdmin.ProjectMember}' " +
                "combination already exists. " +
                s"This permission currently has the scope '${PermissionUtilADM
                    .formatPermissionADMs(perm003_d1.p.hasPermissions, PermissionType.OAP)}'. " +
                s"Use its IRI ${perm003_d1.iri} to modify it, if necessary."
            )
          )
        )
      }

      "fail and return a 'DuplicateValueException' when a doap permission for project and resourceClass combination already exists" in {
        appActor ! DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
            forResourceClass = Some(SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS),
            hasPermissions = Set(
              PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
              PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
            )
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          Failure(
            DuplicateValueException(
              s"A default object access permission for project: '${SharedTestDataV1.INCUNABULA_PROJECT_IRI}' and resourceClass: '${SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS}' " +
                "combination already exists. " +
                s"This permission currently has the scope '${PermissionUtilADM
                    .formatPermissionADMs(perm003_d2.p.hasPermissions, PermissionType.OAP)}'. " +
                s"Use its IRI ${perm003_d2.iri} to modify it, if necessary."
            )
          )
        )
      }

      "fail and return a 'DuplicateValueException' when a doap permission for project and property combination already exists" in {
        appActor ! DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
            forProperty = Some(SharedOntologyTestDataADM.INCUNABULA_PartOf_Property),
            hasPermissions = Set(
              PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.KnownUser)
            )
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          Failure(
            DuplicateValueException(
              s"A default object access permission for project: '${SharedTestDataV1.INCUNABULA_PROJECT_IRI}' and property: '${SharedOntologyTestDataADM.INCUNABULA_PartOf_Property}' " +
                "combination already exists. " +
                s"This permission currently has the scope '${PermissionUtilADM
                    .formatPermissionADMs(perm003_d4.p.hasPermissions, PermissionType.OAP)}'. " +
                s"Use its IRI ${perm003_d4.iri} to modify it, if necessary."
            )
          )
        )
      }

      "fail and return a 'DuplicateValueException' when a doap permission for project, resource class, and property combination already exists" in {
        appActor ! DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataV1.INCUNABULA_PROJECT_IRI,
            forResourceClass = Some(SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS),
            forProperty = Some(SharedOntologyTestDataADM.INCUNABULA_PartOf_Property),
            hasPermissions = Set(
              PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
              PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
            )
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          Failure(
            DuplicateValueException(
              s"A default object access permission for project: '${SharedTestDataV1.INCUNABULA_PROJECT_IRI}' and resourceClass: '${SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS}' " +
                s"and property: '${SharedOntologyTestDataADM.INCUNABULA_PartOf_Property}' " +
                "combination already exists. " +
                s"This permission currently has the scope '${PermissionUtilADM
                    .formatPermissionADMs(perm003_d5.p.hasPermissions, PermissionType.OAP)}'. " +
                s"Use its IRI ${perm003_d5.iri} to modify it, if necessary."
            )
          )
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
        appActor ! DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forGroup = Some(OntologyConstants.KnoraAdmin.UnknownUser),
            hasPermissions = hasPermissions
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionCreateResponseADM =
          expectMsgType[DefaultObjectAccessPermissionCreateResponseADM]
        assert(received.defaultObjectAccessPermission.forProject == SharedTestDataADM.IMAGES_PROJECT_IRI)
        assert(received.defaultObjectAccessPermission.forGroup == Some(OntologyConstants.KnoraAdmin.UnknownUser))
        assert(
          received.defaultObjectAccessPermission.hasPermissions
            .contains(PermissionADM.restrictedViewPermission(OntologyConstants.KnoraAdmin.UnknownUser))
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
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = Some(7)
          )
        )
        appActor ! DefaultObjectAccessPermissionCreateRequestADM(
          createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
            forProject = SharedTestDataADM.IMAGES_PROJECT_IRI,
            forGroup = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            hasPermissions = hasPermissions
          ).prepareHasPermissions,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionCreateResponseADM =
          expectMsgType[DefaultObjectAccessPermissionCreateResponseADM]
        assert(received.defaultObjectAccessPermission.forProject == SharedTestDataADM.IMAGES_PROJECT_IRI)
        assert(received.defaultObjectAccessPermission.forGroup == Some(OntologyConstants.KnoraAdmin.ProjectAdmin))
        assert(received.defaultObjectAccessPermission.hasPermissions.equals(expectedPermissions))
      }
    }
//
//    "ask to get all permissions" should {
//
//      "return all permissions for 'image' project " in {
//        appActor ! PermissionsForProjectGetRequestADM(
//          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
//          requestingUser = rootUser,
//          apiRequestID = UUID.randomUUID()
//        )
//        val received: PermissionsForProjectGetResponseADM = expectMsgType[PermissionsForProjectGetResponseADM]
//        received.allPermissions.size should be(8)
//      }
//
//      "return all permissions for 'incunabula' project " in {
//        appActor ! PermissionsForProjectGetRequestADM(
//          projectIri = SharedTestDataADM.INCUNABULA_PROJECT_IRI,
//          requestingUser = rootUser,
//          apiRequestID = UUID.randomUUID()
//        )
//        expectMsg(
//          PermissionsForProjectGetResponseADM(allPermissions = Set(
//            PermissionInfoADM(perm003_a1.iri, OntologyConstants.KnoraAdmin.AdministrativePermission),
//            PermissionInfoADM(perm003_a2.iri, OntologyConstants.KnoraAdmin.AdministrativePermission),
//            PermissionInfoADM(perm003_d1.iri, OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission),
//            PermissionInfoADM(perm003_d2.iri, OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission),
//            PermissionInfoADM(perm003_d3.iri, OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission),
//            PermissionInfoADM(perm003_d4.iri, OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission),
//            PermissionInfoADM(perm003_d5.iri, OntologyConstants.KnoraAdmin.DefaultObjectAccessPermission)
//          )))
//      }
//    }
//
//    "ask for default object access permissions 'string'" should {
//
//      "return the default object access permissions 'string' for the 'knora-base:LinkObj' resource class (system resource class)" in {
//        appActor ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
//          projectIri = SharedTestDataADM.INCUNABULA_PROJECT_IRI,
//          resourceClassIri = OntologyConstants.KnoraBase.LinkObj,
//          targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
//          requestingUser = KnoraSystemInstances.Users.SystemUser
//        )
//        expectMsg(
//          DefaultObjectAccessPermissionsStringResponseADM(
//            "M knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser"))
//      }
//
//      "return the default object access permissions 'string' for the 'knora-base:hasStillImageFileValue' property (system property)" in {
//        appActor ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
//          projectIri = SharedTestDataADM.INCUNABULA_PROJECT_IRI,
//          resourceClassIri = OntologyConstants.KnoraBase.StillImageRepresentation,
//          propertyIri = OntologyConstants.KnoraBase.HasStillImageFileValue,
//          targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
//          requestingUser = KnoraSystemInstances.Users.SystemUser
//        )
//        expectMsg(
//          DefaultObjectAccessPermissionsStringResponseADM(
//            "M knora-admin:Creator,knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser"))
//      }
//
//      "return the default object access permissions 'string' for the 'incunabula:book' resource class (project resource class)" in {
//        appActor ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
//          projectIri = SharedTestDataADM.INCUNABULA_PROJECT_IRI,
//          resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS,
//          targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
//          requestingUser = KnoraSystemInstances.Users.SystemUser
//        )
//        expectMsg(
//          DefaultObjectAccessPermissionsStringResponseADM(
//            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
//      }
//
//      "return the default object access permissions 'string' for the 'incunabula:page' resource class (project resource class)" in {
//        appActor ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
//          projectIri = SharedTestDataADM.INCUNABULA_PROJECT_IRI,
//          resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS,
//          targetUser = SharedTestDataADM.incunabulaProjectAdminUser,
//          requestingUser = KnoraSystemInstances.Users.SystemUser
//        )
//        expectMsg(
//          DefaultObjectAccessPermissionsStringResponseADM(
//            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
//      }
//
//      "return the default object access permissions 'string' for the 'images:jahreszeit' property" in {
//        appActor ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
//          projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
//          resourceClassIri = s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#bild",
//          propertyIri = s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#jahreszeit",
//          targetUser = SharedTestDataADM.imagesUser01,
//          requestingUser = KnoraSystemInstances.Users.SystemUser
//        )
//        expectMsg(
//          DefaultObjectAccessPermissionsStringResponseADM(
//            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser"))
//      }
//
//      "return the default object access permissions 'string' for the 'anything:hasInterval' property" in {
//        appActor ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
//          projectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI,
//          resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
//          propertyIri = "http://www.knora.org/ontology/0001/anything#hasInterval",
//          targetUser = SharedTestDataADM.anythingUser2,
//          requestingUser = KnoraSystemInstances.Users.SystemUser
//        )
//        expectMsg(
//          DefaultObjectAccessPermissionsStringResponseADM(
//            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
//      }
//
//      "return the default object access permissions 'string' for the 'anything:Thing' class" in {
//        appActor ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
//          projectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI,
//          resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
//          targetUser = SharedTestDataADM.anythingUser2,
//          requestingUser = KnoraSystemInstances.Users.SystemUser
//        )
//        expectMsg(
//          DefaultObjectAccessPermissionsStringResponseADM(
//            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
//      }
//
//      "return the default object access permissions 'string' for the 'anything:Thing' class and 'anything:hasText' property" in {
//        appActor ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
//          projectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI,
//          resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
//          propertyIri = "http://www.knora.org/ontology/0001/anything#hasText",
//          targetUser = SharedTestDataADM.anythingUser1,
//          requestingUser = KnoraSystemInstances.Users.SystemUser
//        )
//        expectMsg(DefaultObjectAccessPermissionsStringResponseADM("CR knora-admin:Creator"))
//      }
//
//      "return the default object access permissions 'string' for the 'images:Bild' class and 'anything:hasText' property" in {
//        appActor ! DefaultObjectAccessPermissionsStringForPropertyGetADM(
//          projectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI,
//          resourceClassIri = s"${SharedOntologyTestDataADM.IMAGES_ONTOLOGY_IRI}#bild",
//          propertyIri = "http://www.knora.org/ontology/0001/anything#hasText",
//          targetUser = SharedTestDataADM.anythingUser2,
//          requestingUser = KnoraSystemInstances.Users.SystemUser
//        )
//        expectMsg(
//          DefaultObjectAccessPermissionsStringResponseADM(
//            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
//      }
//
//      "return the default object access permissions 'string' for the 'anything:Thing' resource class for the root user (system admin and not member of project)" in {
//        appActor ! DefaultObjectAccessPermissionsStringForResourceClassGetADM(
//          projectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI,
//          resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
//          targetUser = SharedTestDataADM.rootUser,
//          requestingUser = KnoraSystemInstances.Users.SystemUser
//        )
//        expectMsg(
//          DefaultObjectAccessPermissionsStringResponseADM(
//            "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"))
//      }
//
//      "return a combined and max set of permissions (default object access permissions) defined on the supplied groups (helper method used in queries before)" in {
//        val groups = List("http://rdfh.ch/groups/images-reviewer",
//                          s"${OntologyConstants.KnoraAdmin.ProjectMember}",
//                          s"${OntologyConstants.KnoraAdmin.ProjectAdmin}")
//        val expected = Set(
//          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
//          PermissionADM.viewPermission(OntologyConstants.KnoraAdmin.KnownUser),
//          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
//        )
//        val f
//          : Future[Set[PermissionADM]] = responderUnderTest invokePrivate defaultObjectAccessPermissionsForGroupsGetADM(
//          SharedTestDataADM.IMAGES_PROJECT_IRI,
//          groups)
//        val result: Set[PermissionADM] = Await.result(f, 1.seconds)
//        result should equal(expected)
//      }
//    }
//
//    "ask to get the permission by IRI" should {
//      "not return the permission if requesting user does not have permission to see it" in {
//        val permissionIri = perm002_a1.iri
//        appActor ! PermissionByIriGetRequestADM(
//          permissionIri = perm002_a1.iri,
//          requestingUser = SharedTestDataADM.imagesUser02
//        )
//        expectMsg(
//          Failure(ForbiddenException(
//            s"Permission $permissionIri can only be queried/updated/deleted by system or project admin.")))
//      }
//
//      "return an administrative permission" in {
//        appActor ! PermissionByIriGetRequestADM(
//          permissionIri = perm002_a1.iri,
//          requestingUser = rootUser
//        )
//        expectMsg(AdministrativePermissionGetResponseADM(perm002_a1.p))
//      }
//
//      "return a default object access permission" in {
//        appActor ! PermissionByIriGetRequestADM(
//          permissionIri = perm002_d1.iri,
//          requestingUser = rootUser
//        )
//        expectMsg(DefaultObjectAccessPermissionGetResponseADM(perm002_d1.p))
//      }
//    }
//
//    "ask to update group of a permission" should {
//      "update group of an administrative permission" in {
//        val permissionIri = "http://rdfh.ch/permissions/00FF/a2"
//        val newGroupIri = "http://rdfh.ch/groups/00FF/images-reviewer"
//        appActor ! PermissionChangeGroupRequestADM(
//          permissionIri = permissionIri,
//          changePermissionGroupRequest = ChangePermissionGroupApiRequestADM(
//            forGroup = newGroupIri
//          ),
//          requestingUser = rootUser,
//          apiRequestID = UUID.randomUUID()
//        )
//        val received: AdministrativePermissionGetResponseADM = expectMsgType[AdministrativePermissionGetResponseADM]
//        val ap = received.administrativePermission
//        assert(ap.iri == permissionIri)
//        assert(ap.forGroup == newGroupIri)
//      }
//
//      "throw ForbiddenException for PermissionChangeGroupRequestADM if requesting user is not system or project Admin" in {
//        val permissionIri = "http://rdfh.ch/permissions/00FF/a2"
//        val newGroupIri = "http://rdfh.ch/groups/00FF/images-reviewer"
//        appActor ! PermissionChangeGroupRequestADM(
//          permissionIri = permissionIri,
//          changePermissionGroupRequest = ChangePermissionGroupApiRequestADM(
//            forGroup = newGroupIri
//          ),
//          requestingUser = SharedTestDataADM.imagesUser02,
//          apiRequestID = UUID.randomUUID()
//        )
//        expectMsg(
//          Failure(ForbiddenException(
//            s"Permission $permissionIri can only be queried/updated/deleted by system or project admin.")))
//      }
//
//      "update group of a default object access permission" in {
//        val permissionIri = "http://rdfh.ch/permissions/00FF/d1"
//        val newGroupIri = "http://rdfh.ch/groups/00FF/images-reviewer"
//        appActor ! PermissionChangeGroupRequestADM(
//          permissionIri = permissionIri,
//          changePermissionGroupRequest = ChangePermissionGroupApiRequestADM(
//            forGroup = newGroupIri
//          ),
//          requestingUser = rootUser,
//          apiRequestID = UUID.randomUUID()
//        )
//        val received: DefaultObjectAccessPermissionGetResponseADM =
//          expectMsgType[DefaultObjectAccessPermissionGetResponseADM]
//        val doap = received.defaultObjectAccessPermission
//        assert(doap.iri == permissionIri)
//        assert(doap.forGroup.get == newGroupIri)
//      }
//
//      "update group of a default object access permission, resource class must be deleted" in {
//        val permissionIri = "http://rdfh.ch/permissions/0803/003-d2"
//        val newGroupIri = "http://www.knora.org/ontology/knora-admin#ProjectMember"
//        appActor ! PermissionChangeGroupRequestADM(
//          permissionIri = permissionIri,
//          changePermissionGroupRequest = ChangePermissionGroupApiRequestADM(
//            forGroup = newGroupIri
//          ),
//          requestingUser = rootUser,
//          apiRequestID = UUID.randomUUID()
//        )
//        val received: DefaultObjectAccessPermissionGetResponseADM =
//          expectMsgType[DefaultObjectAccessPermissionGetResponseADM]
//        val doap = received.defaultObjectAccessPermission
//        assert(doap.iri == permissionIri)
//        assert(doap.forGroup.get == newGroupIri)
//        assert(doap.forResourceClass.isEmpty)
//      }
//
//      "update group of a default object access permission, property must be deleted" in {
//        val permissionIri = "http://rdfh.ch/permissions/0000/001-d3"
//        val newGroupIri = "http://www.knora.org/ontology/knora-admin#ProjectMember"
//        appActor ! PermissionChangeGroupRequestADM(
//          permissionIri = permissionIri,
//          changePermissionGroupRequest = ChangePermissionGroupApiRequestADM(
//            forGroup = newGroupIri
//          ),
//          requestingUser = rootUser,
//          apiRequestID = UUID.randomUUID()
//        )
//        val received: DefaultObjectAccessPermissionGetResponseADM =
//          expectMsgType[DefaultObjectAccessPermissionGetResponseADM]
//        val doap = received.defaultObjectAccessPermission
//        assert(doap.iri == permissionIri)
//        assert(doap.forGroup.get == newGroupIri)
//        assert(doap.forProperty.isEmpty)
//      }
//    }

    "ask to update hasPermissions of a permission" should {
//      "throw ForbiddenException for PermissionChangeHasPermissionsRequestADM if requesting user is not system or project Admin" in {
//        val permissionIri = "http://rdfh.ch/permissions/00FF/a2"
//        val hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
//
//        appActor ! PermissionChangeHasPermissionsRequestADM(
//          permissionIri = permissionIri,
//          changePermissionHasPermissionsRequest = ChangePermissionHasPermissionsApiRequestADM(
//            hasPermissions = hasPermissions
//          ),
//          requestingUser = SharedTestDataADM.imagesUser02,
//          apiRequestID = UUID.randomUUID()
//        )
//        expectMsg(
//          Failure(ForbiddenException(
//            s"Permission $permissionIri can only be queried/updated/deleted by system or project admin.")))
//      }
//
//      "update hasPermissions of an administrative permission" in {
//        val permissionIri = "http://rdfh.ch/permissions/00FF/a2"
//        val hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
//
//        appActor ! PermissionChangeHasPermissionsRequestADM(
//          permissionIri = permissionIri,
//          changePermissionHasPermissionsRequest = ChangePermissionHasPermissionsApiRequestADM(
//            hasPermissions = hasPermissions
//          ),
//          requestingUser = rootUser,
//          apiRequestID = UUID.randomUUID()
//        )
//        val received: AdministrativePermissionGetResponseADM = expectMsgType[AdministrativePermissionGetResponseADM]
//        val ap = received.administrativePermission
//        assert(ap.iri == permissionIri)
//        ap.hasPermissions.size should be(1)
//        assert(ap.hasPermissions.equals(hasPermissions))
//      }

      "ignore irrelevant parameters given in ChangePermissionHasPermissionsApiRequestADM for an administrative permission" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/a2"
        val hasPermissions = Set(
          PermissionADM(
            name = OntologyConstants.KnoraAdmin.ProjectAdminAllPermission,
            additionalInformation = Some("aIRI"),
            permissionCode = Some(1)
          )
        )
        appActor ! PermissionChangeHasPermissionsRequestADM(
          permissionIri = permissionIri,
          changePermissionHasPermissionsRequest = ChangePermissionHasPermissionsApiRequestADM(
            hasPermissions = hasPermissions
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: AdministrativePermissionGetResponseADM = expectMsgType[AdministrativePermissionGetResponseADM]
        val ap                                               = received.administrativePermission
        assert(ap.iri == permissionIri)
        ap.hasPermissions.size should be(1)
        val expectedSetOfPermissions = Set(PermissionADM.ProjectAdminAllPermission)
        assert(ap.hasPermissions.equals(expectedSetOfPermissions))
      }

      "update hasPermissions of a default object access permission" in {
        val permissionIri = "http://rdfh.ch/permissions/0803/003-d1"
        val hasPermissions = Set(
          PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.Creator),
          PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
        )

        appActor ! PermissionChangeHasPermissionsRequestADM(
          permissionIri = permissionIri,
          changePermissionHasPermissionsRequest = ChangePermissionHasPermissionsApiRequestADM(
            hasPermissions = hasPermissions
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionGetResponseADM =
          expectMsgType[DefaultObjectAccessPermissionGetResponseADM]
        val doap = received.defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        doap.hasPermissions.size should be(2)
        assert(doap.hasPermissions.equals(hasPermissions))
      }

      "add missing name of the permission, if permissionCode of permission was given in hasPermissions of a default object access permission" in {
        val permissionIri = "http://rdfh.ch/permissions/0803/003-d1"
        val hasPermissions = Set(
          PermissionADM(
            name = "",
            additionalInformation = Some(OntologyConstants.KnoraAdmin.Creator),
            permissionCode = Some(8)
          )
        )

        val expectedHasPermissions = Set(
          PermissionADM(
            name = OntologyConstants.KnoraBase.ChangeRightsPermission,
            additionalInformation = Some(OntologyConstants.KnoraAdmin.Creator),
            permissionCode = Some(8)
          )
        )

        appActor ! PermissionChangeHasPermissionsRequestADM(
          permissionIri = permissionIri,
          changePermissionHasPermissionsRequest = ChangePermissionHasPermissionsApiRequestADM(
            hasPermissions = hasPermissions
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionGetResponseADM =
          expectMsgType[DefaultObjectAccessPermissionGetResponseADM]
        val doap = received.defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.hasPermissions.equals(expectedHasPermissions))
      }

      "add missing permissionCode of the permission, if name of permission was given in hasPermissions of a default object access permission" in {
        val permissionIri = "http://rdfh.ch/permissions/0803/003-d1"
        val hasPermissions = Set(
          PermissionADM(
            name = OntologyConstants.KnoraBase.DeletePermission,
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = None
          )
        )

        val expectedHasPermissions = Set(
          PermissionADM(
            name = OntologyConstants.KnoraBase.DeletePermission,
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = Some(7)
          )
        )

        appActor ! PermissionChangeHasPermissionsRequestADM(
          permissionIri = permissionIri,
          changePermissionHasPermissionsRequest = ChangePermissionHasPermissionsApiRequestADM(
            hasPermissions = hasPermissions
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionGetResponseADM =
          expectMsgType[DefaultObjectAccessPermissionGetResponseADM]
        val doap = received.defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.hasPermissions.equals(expectedHasPermissions))
      }

      "not update hasPermissions of a default object access permission, if both name and project code of a permission were missing" in {
        val permissionIri = "http://rdfh.ch/permissions/0803/003-d1"
        val code          = 1
        val name          = OntologyConstants.KnoraBase.DeletePermission
        val hasPermissions = Set(
          PermissionADM(
            name = name,
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = Some(code)
          )
        )

        appActor ! PermissionChangeHasPermissionsRequestADM(
          permissionIri = permissionIri,
          changePermissionHasPermissionsRequest = ChangePermissionHasPermissionsApiRequestADM(
            hasPermissions = hasPermissions
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          Failure(BadRequestException(s"Given permission code $code and permission name $name are not consistent."))
        )

      }

      "not update hasPermissions of a default object access permission, if an invalid name was given for a permission" in {
        val permissionIri = "http://rdfh.ch/permissions/0803/003-d1"
        val name          = "invalidName"
        val hasPermissions = Set(
          PermissionADM(
            name = name,
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = None
          )
        )

        appActor ! PermissionChangeHasPermissionsRequestADM(
          permissionIri = permissionIri,
          changePermissionHasPermissionsRequest = ChangePermissionHasPermissionsApiRequestADM(
            hasPermissions = hasPermissions
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          Failure(
            BadRequestException(
              s"Invalid value for name parameter of hasPermissions: $name, it should be one of " +
                s"${EntityPermissionAbbreviations.toString}"
            )
          )
        )

      }

      "not update hasPermissions of a default object access permission, if an invalid code was given for a permission" in {
        val permissionIri = "http://rdfh.ch/permissions/0803/003-d1"
        val code          = 10
        val hasPermissions = Set(
          PermissionADM(
            name = OntologyConstants.KnoraBase.DeletePermission,
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = Some(code)
          )
        )

        appActor ! PermissionChangeHasPermissionsRequestADM(
          permissionIri = permissionIri,
          changePermissionHasPermissionsRequest = ChangePermissionHasPermissionsApiRequestADM(
            hasPermissions = hasPermissions
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          Failure(
            BadRequestException(
              s"Invalid value for permissionCode parameter of hasPermissions: $code, it should be one of " +
                s"${PermissionTypeAndCodes.values.toString}"
            )
          )
        )

      }

      "not update hasPermissions of a default object access permission, if given name and project code are not consistent" in {
        val permissionIri = "http://rdfh.ch/permissions/0803/003-d1"
        val hasPermissions = Set(
          PermissionADM(
            name = "",
            additionalInformation = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
            permissionCode = None
          )
        )

        appActor ! PermissionChangeHasPermissionsRequestADM(
          permissionIri = permissionIri,
          changePermissionHasPermissionsRequest = ChangePermissionHasPermissionsApiRequestADM(
            hasPermissions = hasPermissions
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          Failure(
            BadRequestException(
              s"One of permission code or permission name must be provided for a default object access permission."
            )
          )
        )

      }
    }
    "ask to update resource class of a permission" should {
      "throw ForbiddenException for PermissionChangeResourceClassRequestADM if requesting user is not system or project Admin" in {
        val permissionIri    = "http://rdfh.ch/permissions/0803/003-d2"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS

        appActor ! PermissionChangeResourceClassRequestADM(
          permissionIri = permissionIri,
          changePermissionResourceClassRequest = ChangePermissionResourceClassApiRequestADM(
            forResourceClass = resourceClassIri
          ),
          requestingUser = SharedTestDataADM.incunabulaMemberUser,
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
      "update resource class of a default object access permission" in {
        val permissionIri    = "http://rdfh.ch/permissions/0803/003-d2"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS

        appActor ! PermissionChangeResourceClassRequestADM(
          permissionIri = permissionIri,
          changePermissionResourceClassRequest = ChangePermissionResourceClassApiRequestADM(
            forResourceClass = resourceClassIri
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionGetResponseADM =
          expectMsgType[DefaultObjectAccessPermissionGetResponseADM]
        val doap = received.defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.forResourceClass.get == resourceClassIri)
      }

      "update resource class of a default object access permission, and delete group" in {
        val permissionIri    = "http://rdfh.ch/permissions/0803/003-d1"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS

        appActor ! PermissionChangeResourceClassRequestADM(
          permissionIri = permissionIri,
          changePermissionResourceClassRequest = ChangePermissionResourceClassApiRequestADM(
            forResourceClass = resourceClassIri
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionGetResponseADM =
          expectMsgType[DefaultObjectAccessPermissionGetResponseADM]
        val doap = received.defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.forResourceClass.get == resourceClassIri)
        assert(doap.forGroup.isEmpty)
      }

      "not update resource class of an administrative permission" in {
        val permissionIri    = "http://rdfh.ch/permissions/0803/003-a2"
        val resourceClassIri = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS

        appActor ! PermissionChangeResourceClassRequestADM(
          permissionIri = permissionIri,
          changePermissionResourceClassRequest = ChangePermissionResourceClassApiRequestADM(
            forResourceClass = resourceClassIri
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          Failure(
            BadRequestException(
              s"Permission $permissionIri is of type administrative permission. " +
                s"Only a default object access permission defined for a resource class can be updated."
            )
          )
        )
      }
    }
    "ask to update property of a permission" should {
      "not update property of an administrative permission" in {
        val permissionIri = "http://rdfh.ch/permissions/0803/003-a2"
        val propertyIri   = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY

        appActor ! PermissionChangePropertyRequestADM(
          permissionIri = permissionIri,
          changePermissionPropertyRequest = ChangePermissionPropertyApiRequestADM(
            forProperty = propertyIri
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(
          Failure(
            BadRequestException(
              s"Permission $permissionIri is of type administrative permission. " +
                s"Only a default object access permission defined for a property can be updated."
            )
          )
        )
      }
      "throw ForbiddenException for PermissionChangePropertyRequestADM if requesting user is not system or project Admin" in {
        val permissionIri = "http://rdfh.ch/permissions/0000/001-d3"
        val propertyIri   = OntologyConstants.KnoraBase.TextFileValue

        appActor ! PermissionChangePropertyRequestADM(
          permissionIri = permissionIri,
          changePermissionPropertyRequest = ChangePermissionPropertyApiRequestADM(
            forProperty = propertyIri
          ),
          requestingUser = SharedTestDataADM.normalUser,
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
      "update property of a default object access permission" in {
        val permissionIri = "http://rdfh.ch/permissions/0000/001-d3"
        val propertyIri   = OntologyConstants.KnoraBase.TextFileValue

        appActor ! PermissionChangePropertyRequestADM(
          permissionIri = permissionIri,
          changePermissionPropertyRequest = ChangePermissionPropertyApiRequestADM(
            forProperty = propertyIri
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionGetResponseADM =
          expectMsgType[DefaultObjectAccessPermissionGetResponseADM]
        val doap = received.defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.forProperty.get == propertyIri)
      }

      "update property of a default object access permission, delete group" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/d1"
        val propertyIri   = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY

        appActor ! PermissionChangePropertyRequestADM(
          permissionIri = permissionIri,
          changePermissionPropertyRequest = ChangePermissionPropertyApiRequestADM(
            forProperty = propertyIri
          ),
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: DefaultObjectAccessPermissionGetResponseADM =
          expectMsgType[DefaultObjectAccessPermissionGetResponseADM]
        val doap = received.defaultObjectAccessPermission
        assert(doap.iri == permissionIri)
        assert(doap.forProperty.get == propertyIri)
        assert(doap.forGroup.isEmpty)
      }
    }

    "ask to delete a permission" should {
      "throw BadRequestException if given IRI is not a permission IRI" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/d1kjhfkj"
        appActor ! PermissionDeleteRequestADM(
          permissionIri = permissionIri,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        expectMsg(Failure(NotFoundException(s"Permission with given IRI: $permissionIri not found.")))
      }

      "throw ForbiddenException if user requesting PermissionDeleteResponseADM is not a system or project admin" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/d1"
        appActor ! PermissionDeleteRequestADM(
          permissionIri = permissionIri,
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

      "erase a permission with given IRI" in {
        val permissionIri = "http://rdfh.ch/permissions/00FF/d1"
        appActor ! PermissionDeleteRequestADM(
          permissionIri = permissionIri,
          requestingUser = rootUser,
          apiRequestID = UUID.randomUUID()
        )
        val received: PermissionDeleteResponseADM = expectMsgType[PermissionDeleteResponseADM]
        assert(received.deleted == true)
      }
    }
  }
}
