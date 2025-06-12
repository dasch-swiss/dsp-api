/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import spray.json.*

import java.net.URLEncoder

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.admin.responder.IntegrationTestAdminJsonProtocol.*
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateAdministrativePermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateDefaultObjectAccessPermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo.builtIn.ProjectMember
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestAdminApiClient
import org.knora.webapi.util.AkkaHttpUtils

/**
 * End-to-End (E2E) test specification for testing the 'v1/permissions' route.
 *
 * This spec tests the 'v1/store' route.
 */
class PermissionsADME2ESpec extends E2ESpec with SprayJsonSupport {

  private val imageProjectIri: ProjectIri = ProjectIri.unsafeFrom(SharedTestDataADM2.imagesProjectInfo.id)
  private val customDOAPIri: String       = "http://rdfh.ch/permissions/00FF/zTOK3HlWTLGgTO8ZWVnotg"

  "The Permissions Route ('admin/permissions')" when {
    "getting permissions" should {
      "return a group's administrative permission" in {
        val response: AdministrativePermissionGetResponseADM = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getAdministrativePermissions(imageProjectIri, ProjectMember.id, rootUser)
            .flatMap(_.assert200),
        )
        assert(response.administrativePermission.iri == "http://rdfh.ch/permissions/00FF/QYdrY7O6QD2VR30oaAt3Yg")
      }

      "return a project's administrative permissions" in {
        val response: AdministrativePermissionsForProjectGetResponseADM = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getAdministrativePermissions(imageProjectIri, rootUser)
            .flatMap(_.assert200),
        )
        response.administrativePermissions.size should be(3)
      }

      "return a project's default object access permissions" in {
        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .getDefaultObjectAccessPermissions(imageProjectIri, rootUser)
            .flatMap(_.assert200),
        )
        response.defaultObjectAccessPermissions.size should be(3)
      }

      "return a project's all permissions" in {
        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient.getAdminPermissions(imageProjectIri, rootUser).flatMap(_.assert200),
        )
        response.permissions.size should be(6)
      }
    }

    "creating permissions" should {
      "create administrative permission" in {
        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .createAdministrativePermission(
              CreateAdministrativePermissionAPIRequestADM(
                forProject = SharedTestDataADM.anythingProjectIri.value,
                forGroup = SharedTestDataADM.thingSearcherGroup.id,
                hasPermissions = Set(PermissionADM(name = "ProjectAdminGroupAllPermission")),
              ),
              rootUser,
            )
            .flatMap(_.assert200),
        )
        val actual = response.administrativePermission
        assert(actual.forGroup == "http://rdfh.ch/groups/0001/thing-searcher")
        assert(actual.forProject == "http://rdfh.ch/projects/0001")
        assert(actual.hasPermissions.map(_.name).contains("ProjectAdminGroupAllPermission"))
      }

      "create a default object access permission" in {
        val response = UnsafeZioRun.runOrThrow(
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
            .flatMap(_.assert200),
        )

        val actual = response.defaultObjectAccessPermission
        assert(actual.forGroup.contains("http://rdfh.ch/groups/0001/thing-searcher"))
        assert(actual.forProject == "http://rdfh.ch/projects/0001")
        assert(
          actual.hasPermissions
            .flatMap(_.additionalInformation)
            .contains("http://www.knora.org/ontology/knora-admin#ProjectMember"),
        )
      }

      "create a default object access permission with a custom IRI" in {
        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .createDefaultObjectAccessPermission(
              CreateDefaultObjectAccessPermissionAPIRequestADM(
                id = Some(customDOAPIri),
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
            .flatMap(_.assert200),
        )

        val actual = response.defaultObjectAccessPermission
        assert(actual.iri == customDOAPIri)
        assert(actual.forResourceClass.contains(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS))
        assert(actual.forProject == "http://rdfh.ch/projects/00FF")
        assert(
          actual.hasPermissions
            .flatMap(_.additionalInformation)
            .contains("http://www.knora.org/ontology/knora-admin#ProjectMember"),
        )
      }
    }

    "updating permissions" should {
      "change the group of an administrative permission" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .updateAdministrativePermissionGroup(permissionIri, newGroupIri, rootUser)
            .flatMap(_.assert200),
        )
        assert(
          response
            .asInstanceOf[AdministrativePermissionGetResponseADM]
            .administrativePermission
            .forGroup == newGroupIri.value,
        )
      }

      "change the group of a default object access permission" in {
        val permissionIri = PermissionIri.unsafeFrom("http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA")
        val newGroupIri   = GroupIri.unsafeFrom("http://rdfh.ch/groups/00FF/images-reviewer")
        val response = UnsafeZioRun.runOrThrow(
          TestAdminApiClient
            .updateAdministrativePermissionGroup(permissionIri, newGroupIri, rootUser)
            .flatMap(_.assert200),
        )
        val actual = response.asInstanceOf[DefaultObjectAccessPermissionGetResponseADM]
        assert(actual.defaultObjectAccessPermission.forGroup.contains(newGroupIri.value))
      }

      "change the set of hasPermissions of an administrative permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ"
        val encodedPermissionIri = URLEncoder.encode(permissionIri, "utf-8")
        val updateHasPermissions =
          s"""{
             |   "hasPermissions":[{"additionalInformation":null,"name":"ProjectAdminGroupAllPermission","permissionCode":null}]
             |}""".stripMargin
        val request = Put(
          baseApiUrl + s"/admin/permissions/" + encodedPermissionIri + "/hasPermissions",
          HttpEntity(ContentTypes.`application/json`, updateHasPermissions),
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val result = AkkaHttpUtils.httpResponseToJson(response).fields("administrative_permission").asJsObject.fields
        val permissions = result
          .getOrElse(
            "hasPermissions",
            throw DeserializationException("The expected field 'hasPermissions' is missing."),
          )
          .asInstanceOf[JsArray]
          .elements
        permissions.size should be(1)
        assert(permissions.head.asJsObject.fields("name").toString.contains("ProjectAdminGroupAllPermission"))
      }

      "change the set of hasPermissions of a default object access permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val encodedPermissionIri = URLEncoder.encode(permissionIri, "utf-8")
        val updateHasPermissions =
          s"""{
             |   "hasPermissions":[{"additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember","name":"D","permissionCode":7}]
             |}""".stripMargin
        val request = Put(
          baseApiUrl + s"/admin/permissions/" + encodedPermissionIri + "/hasPermissions",
          HttpEntity(ContentTypes.`application/json`, updateHasPermissions),
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val result =
          AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permission").asJsObject.fields
        val permissions = result
          .getOrElse(
            "hasPermissions",
            throw DeserializationException("The expected field 'hasPermissions' is missing."),
          )
          .asInstanceOf[JsArray]
          .elements
        permissions.size should be(1)
        assert(
          permissions.head.asJsObject
            .fields("additionalInformation")
            .toString
            .contains("http://www.knora.org/ontology/knora-admin#ProjectMember"),
        )
      }

      "change the resource class of a default object access permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/00FF/Q3OMWyFqStGYK8EXmC7KhQ"
        val encodedPermissionIri = URLEncoder.encode(permissionIri, "utf-8")
        val resourceClassIri     = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS
        val updateResourceClass =
          s"""{
             |   "forResourceClass":"$resourceClassIri"
             |}""".stripMargin
        val request = Put(
          baseApiUrl + s"/admin/permissions/" + encodedPermissionIri + "/resourceClass",
          HttpEntity(ContentTypes.`application/json`, updateResourceClass),
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val result =
          AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permission").asJsObject.fields
        val forResourceClassIRI = result
          .getOrElse(
            "forResourceClass",
            throw DeserializationException("The expected field 'forResourceClass' is missing."),
          )
          .convertTo[String]
        assert(forResourceClassIRI == resourceClassIri)
      }

      "change the property of a default object access permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/00FF/Mck2xJDjQ_Oimi_9z4aFaA"
        val encodedPermissionIri = URLEncoder.encode(permissionIri, "utf-8")
        val propertyClassIri     = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY
        val updateResourceClass =
          s"""{
             |   "forProperty":"$propertyClassIri"
             |}""".stripMargin
        val request = Put(
          baseApiUrl + s"/admin/permissions/" + encodedPermissionIri + "/property",
          HttpEntity(ContentTypes.`application/json`, updateResourceClass),
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val result =
          AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permission").asJsObject.fields
        val forProperty = result
          .getOrElse("forProperty", throw DeserializationException("The expected field 'forProperty' is missing."))
          .convertTo[String]
        assert(forProperty == propertyClassIri)
      }
    }

    "delete request" should {
      "erase a defaultObjectAccess permission" in {
        val permissionIri        = customDOAPIri
        val encodedPermissionIri = URLEncoder.encode(permissionIri, "utf-8")
        val request = Delete(baseApiUrl + s"/admin/permissions/" + encodedPermissionIri) ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val deletedStatus = AkkaHttpUtils.httpResponseToJson(response).fields("deleted")
        deletedStatus.convertTo[Boolean] should be(true)
      }
      "erase an administrative permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ"
        val encodedPermissionIri = URLEncoder.encode(permissionIri, "utf-8")
        val request = Delete(baseApiUrl + s"/admin/permissions/" + encodedPermissionIri) ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass),
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val deletedStatus = AkkaHttpUtils.httpResponseToJson(response).fields("deleted")
        deletedStatus.convertTo[Boolean] should be(true)
      }
    }
  }
}
