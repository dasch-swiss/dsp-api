/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers.BasicHttpCredentials
import spray.json.*
import zio.durationInt

import java.net.URLEncoder

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.admin.responder.IntegrationTestAdminJsonProtocol.*
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionGetResponseADM
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.util.AkkaHttpUtils

/**
 * End-to-End (E2E) test specification for testing the 'v1/permissions' route.
 *
 * This spec tests the 'v1/store' route.
 */
class PermissionsADME2ESpec extends E2ESpec with SprayJsonSupport {

  private val customDOAPIri = "http://rdfh.ch/permissions/00FF/zTOK3HlWTLGgTO8ZWVnotg"
  "The Permissions Route ('admin/permissions')" when {
    "getting permissions" should {
      "return a group's administrative permission" in {
        val projectIri =
          URLEncoder.encode(ProjectIri.unsafeFrom(SharedTestDataADM2.imagesProjectInfo.id).value, "utf-8")
        val groupIri = URLEncoder.encode(KnoraGroupRepo.builtIn.ProjectMember.id.value, "utf-8")
        val request = Get(baseApiUrl + s"/admin/permissions/ap/$projectIri/$groupIri") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass),
        )

        val response = singleAwaitingRequest(request, 1.seconds)
        assert(response.status === StatusCodes.OK, responseToString(response))
        val result = AkkaHttpUtils.httpResponseToJson(response).fields("administrative_permission").asJsObject.fields

        val iri = result
          .getOrElse("iri", throw DeserializationException("The expected field 'iri' is missing."))
          .convertTo[String]

        assert(iri == "http://rdfh.ch/permissions/00FF/QYdrY7O6QD2VR30oaAt3Yg")
      }

      "return a project's administrative permissions" in {
        val projectIri = URLEncoder.encode(SharedTestDataADM2.imagesProjectInfo.id, "utf-8")

        val request = Get(baseApiUrl + s"/admin/permissions/ap/$projectIri") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass),
        )

        val response = singleAwaitingRequest(request, 1.seconds)
        assert(response.status === StatusCodes.OK, responseToString(response))
        val result = AkkaHttpUtils.httpResponseToJson(response).fields("administrative_permissions")
        result.asInstanceOf[JsArray].elements.size should be(3)
      }

      "return a project's default object access permissions" in {
        val projectIri = URLEncoder.encode(SharedTestDataADM2.imagesProjectInfo.id, "utf-8")

        val request = Get(baseApiUrl + s"/admin/permissions/doap/$projectIri") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass),
        )

        val response = singleAwaitingRequest(request, 1.seconds)
        assert(response.status === StatusCodes.OK, responseToString(response))
        val result = AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permissions")
        result.asInstanceOf[JsArray].elements.size should be(3)
      }

      "return a project's all permissions" in {
        val projectIri = URLEncoder.encode(SharedTestDataADM2.imagesProjectInfo.id, "utf-8")

        val request = Get(baseApiUrl + s"/admin/permissions/$projectIri") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass),
        )

        val response = singleAwaitingRequest(request, 1.seconds)
        assert(response.status === StatusCodes.OK, responseToString(response))
        val result = AkkaHttpUtils.httpResponseToJson(response).fields("permissions")
        result.asInstanceOf[JsArray].elements.size should be(6)
      }
    }

    "creating permissions" should {
      "create an administrative access permission" in {
        val createAdministrativePermissionRequest: String =
          s"""{
             |    "forGroup":"${SharedTestDataADM.thingSearcherGroup.id}",
             |    "forProject":"${SharedTestDataADM.anythingProjectIri}",
             |	"hasPermissions":[{"additionalInformation":null,"name":"ProjectAdminGroupAllPermission","permissionCode":null}]
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/permissions/ap",
          HttpEntity(ContentTypes.`application/json`, createAdministrativePermissionRequest),
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))

        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val result = AkkaHttpUtils.httpResponseToJson(response).fields("administrative_permission").asJsObject.fields

        val groupIri = result
          .getOrElse("forGroup", throw DeserializationException("The expected field 'forGroup' is missing."))
          .convertTo[String]
        assert(groupIri == "http://rdfh.ch/groups/0001/thing-searcher")
        val projectIri = result
          .getOrElse("forProject", throw DeserializationException("The expected field 'forProject' is missing."))
          .convertTo[String]
        assert(projectIri == "http://rdfh.ch/projects/0001")
        val permissions = result
          .getOrElse(
            "hasPermissions",
            throw DeserializationException("The expected field 'hasPermissions' is missing."),
          )
          .compactPrint

        assert(permissions.contains("ProjectAdminGroupAllPermission"))
      }

      "create a new administrative permission for a new project" in {
        val projectIri = "http://rdfh.ch/projects/Fti-cwr3QICVH1DjE_cvCQ"
        val projectPayload =
          s"""
             |{
             |	  "id": "$projectIri",
             |    "shortname": "newprojectWithIri",
             |    "shortcode": "3333",
             |    "longname": "new project with a custom IRI",
             |    "description": [{"value": "a project created with a custom IRI", "language": "en"}],
             |    "keywords": ["projectIRI"],
             |    "logo": "/fu/bar/baz.jpg",
             |    "status": true,
             |    "selfjoin": false
             |}
             |""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/projects",
          HttpEntity(ContentTypes.`application/json`, projectPayload),
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val permissionPayload =
          s"""{
             |    "forGroup":"http://www.knora.org/ontology/knora-admin#KnownUser",
             |    "forProject":"$projectIri",
             |	   "hasPermissions":[{"additionalInformation":null,"name":"ProjectResourceCreateAllPermission","permissionCode":null}]
             |}""".stripMargin

        val permissionRequest = Post(
          baseApiUrl + s"/admin/permissions/ap",
          HttpEntity(ContentTypes.`application/json`, permissionPayload),
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))

        val permissionResponse: HttpResponse = singleAwaitingRequest(permissionRequest)
        assert(permissionResponse.status === StatusCodes.OK)
      }

      "create a default object access permission" in {
        val createDefaultObjectAccessPermissionRequest: String =
          s"""{
             |    "forGroup":"${SharedTestDataADM.thingSearcherGroup.id}",
             |    "forProject":"${SharedTestDataADM.anythingProjectIri}",
             |    "forProperty":null,
             |    "forResourceClass":null,
             |    "hasPermissions":[{"additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember","name":"D","permissionCode":7}]
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/permissions/doap",
          HttpEntity(ContentTypes.`application/json`, createDefaultObjectAccessPermissionRequest),
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val result =
          AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permission").asJsObject.fields
        val groupIri = result
          .getOrElse("forGroup", throw DeserializationException("The expected field 'forGroup' is missing."))
          .convertTo[String]
        assert(groupIri == "http://rdfh.ch/groups/0001/thing-searcher")
        val projectIri = result
          .getOrElse("forProject", throw DeserializationException("The expected field 'forProject' is missing."))
          .convertTo[String]
        assert(projectIri == "http://rdfh.ch/projects/0001")
        val permissions = result
          .getOrElse(
            "hasPermissions",
            throw DeserializationException("The expected field 'hasPermissions' is missing."),
          )
          .compactPrint

        assert(permissions.contains("http://www.knora.org/ontology/knora-admin#ProjectMember"))
      }

      "create a default object access permission with a custom IRI" in {
        val createDefaultObjectAccessPermissionWithCustomIriRequest: String =
          s"""{

             |    "id": "$customDOAPIri",
             |    "forGroup":null,
             |    "forProject":"${SharedTestDataADM.imagesProjectIri}",
             |    "forProperty":null,
             |    "forResourceClass":"${SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS}",
             |    "hasPermissions":[{"additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember","name":"D","permissionCode":7}]
             |}""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/permissions/doap",
          HttpEntity(ContentTypes.`application/json`, createDefaultObjectAccessPermissionWithCustomIriRequest),
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)

        val result =
          AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permission").asJsObject.fields
        val permissionIri = result
          .getOrElse("iri", throw DeserializationException("The expected field 'iri' is missing."))
          .convertTo[String]
        assert(permissionIri == customDOAPIri)
        val forResourceClassIRI = result
          .getOrElse(
            "forResourceClass",
            throw DeserializationException("The expected field 'forResourceClass' is missing."),
          )
          .convertTo[String]
        assert(forResourceClassIRI == SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS)
        val projectIri = result
          .getOrElse("forProject", throw DeserializationException("The expected field 'forProject' is missing."))
          .convertTo[String]
        assert(projectIri == "http://rdfh.ch/projects/00FF")
        val permissions = result
          .getOrElse(
            "hasPermissions",
            throw DeserializationException("The expected field 'hasPermissions' is missing."),
          )
          .compactPrint

        assert(permissions.contains("http://www.knora.org/ontology/knora-admin#ProjectMember"))
      }
    }

    "updating permissions" should {
      "change the group of an administrative permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/00FF/buxHAlz8SHuu0FuiLN_tKQ"
        val encodedPermissionIri = URLEncoder.encode(permissionIri, "utf-8")
        val newGroupIri          = "http://rdfh.ch/groups/00FF/images-reviewer"
        val updatePermissionGroup =
          s"""{
             |    "forGroup": "$newGroupIri"
             |}""".stripMargin
        val request = Put(
          baseApiUrl + s"/admin/permissions/" + encodedPermissionIri + "/group",
          HttpEntity(ContentTypes.`application/json`, updatePermissionGroup),
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val result   = AkkaHttpUtils.httpResponseToJson(response).convertTo[AdministrativePermissionGetResponseADM]
        val groupIri = result.administrativePermission.forGroup
        assert(groupIri == newGroupIri)
      }

      "change the group of a default object access permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/00FF/sdHG20U6RoiwSu8MeAT1vA"
        val encodedPermissionIri = URLEncoder.encode(permissionIri, "utf-8")
        val newGroupIri          = "http://rdfh.ch/groups/00FF/images-reviewer"
        val updatePermissionGroup =
          s"""{
             |    "forGroup": "$newGroupIri"
             |}""".stripMargin
        val request = Put(
          baseApiUrl + s"/admin/permissions/" + encodedPermissionIri + "/group",
          HttpEntity(ContentTypes.`application/json`, updatePermissionGroup),
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val result =
          AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permission").asJsObject.fields
        val groupIri = result
          .getOrElse("forGroup", throw DeserializationException("The expected field 'forGroup' is missing."))
          .convertTo[String]
        assert(groupIri == newGroupIri)
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
