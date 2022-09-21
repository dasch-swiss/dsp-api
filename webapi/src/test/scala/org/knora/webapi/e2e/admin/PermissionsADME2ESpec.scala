/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import spray.json._
import zio._

import org.knora.webapi.E2ESpec
import org.knora.webapi.e2e.ClientTestDataCollector
import org.knora.webapi.e2e.TestDataFileContent
import org.knora.webapi.e2e.TestDataFilePath
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV1
import org.knora.webapi.util.AkkaHttpUtils

/**
 * End-to-End (E2E) test specification for testing the 'v1/permissions' route.
 *
 * This spec tests the 'v1/store' route.
 */
class PermissionsADME2ESpec extends E2ESpec with TriplestoreJsonProtocol {
  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("admin", "permissions")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(appConfig)
  private val customDOAPIri           = "http://rdfh.ch/permissions/00FF/zTOK3HlWTLGgTO8ZWVnotg"
  "The Permissions Route ('admin/permissions')" when {
    "getting permissions" should {
      "return a group's administrative permission" in {

        val projectIri = java.net.URLEncoder.encode(SharedTestDataV1.imagesProjectInfo.id, "utf-8")
        val groupIri   = java.net.URLEncoder.encode(OntologyConstants.KnoraAdmin.ProjectMember, "utf-8")
        val request = Get(baseApiUrl + s"/admin/permissions/ap/$projectIri/$groupIri") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)
        )

        val response = singleAwaitingRequest(request, 1.seconds)
        logger.debug("==>> " + response.toString)
        assert(response.status === StatusCodes.OK)
        val result = AkkaHttpUtils.httpResponseToJson(response).fields("administrative_permission").asJsObject.fields

        val iri = result
          .getOrElse("iri", throw DeserializationException("The expected field 'iri' is missing."))
          .convertTo[String]

        assert(iri == "http://rdfh.ch/permissions/00FF/a1")
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-administrative-permission-for-project-group-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return a project's administrative permissions" in {
        val projectIri = java.net.URLEncoder.encode(SharedTestDataV1.imagesProjectInfo.id, "utf-8")

        val request = Get(baseApiUrl + s"/admin/permissions/ap/$projectIri") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)
        )

        val response = singleAwaitingRequest(request, 1.seconds)
        logger.debug("==>> " + response.toString)
        assert(response.status === StatusCodes.OK)
        val result = AkkaHttpUtils.httpResponseToJson(response).fields("administrative_permissions")
        result.asInstanceOf[JsArray].elements.size should be(3)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-administrative-permissions-for-project-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return a project's default object access permissions" in {
        val projectIri = java.net.URLEncoder.encode(SharedTestDataV1.imagesProjectInfo.id, "utf-8")

        val request = Get(baseApiUrl + s"/admin/permissions/doap/$projectIri") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)
        )

        val response = singleAwaitingRequest(request, 1.seconds)
        logger.debug("==>> " + response.toString)
        assert(response.status === StatusCodes.OK)
        val result = AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permissions")
        result.asInstanceOf[JsArray].elements.size should be(3)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-defaultObjectAccess-permissions-for-project-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "return a project's all permissions" in {
        val projectIri = java.net.URLEncoder.encode(SharedTestDataV1.imagesProjectInfo.id, "utf-8")

        val request = Get(baseApiUrl + s"/admin/permissions/$projectIri") ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)
        )

        val response = singleAwaitingRequest(request, 1.seconds)
        logger.debug("==>> " + response.toString)
        assert(response.status === StatusCodes.OK)
        val result = AkkaHttpUtils.httpResponseToJson(response).fields("permissions")
        result.asInstanceOf[JsArray].elements.size should be(6)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "get-permissions-for-project-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }
    }

    "creating permissions" should {
      "create an administrative access permission" in {
        val createAdministrativePermissionRequest: String =
          s"""{
             |    "forGroup":"${SharedTestDataADM.thingSearcherGroup.id}",
             |    "forProject":"${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
             |	"hasPermissions":[{"additionalInformation":null,"name":"ProjectAdminGroupAllPermission","permissionCode":null}]
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-administrative-permission-request",
              fileExtension = "json"
            ),
            text = createAdministrativePermissionRequest
          )
        )

        val request = Post(
          baseApiUrl + s"/admin/permissions/ap",
          HttpEntity(ContentTypes.`application/json`, createAdministrativePermissionRequest)
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
            throw DeserializationException("The expected field 'hasPermissions' is missing.")
          )
          .toString()

        assert(permissions.contains("ProjectAdminGroupAllPermission"))

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-administrative-permission-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
        val customAPIri = "http://rdfh.ch/permissions/0001/u0PRnDl3kgcbrehZnRlEfA"
        val createAdministrativePermissionWithCustomIriRequest: String =
          s"""{
             |    "id": "$customAPIri",
             |    "forGroup":"${SharedTestDataADM.thingSearcherGroup.id}",
             |    "forProject":"${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
             |	"hasPermissions":[{"additionalInformation":null,"name":"ProjectAdminGroupAllPermission","permissionCode":null}]
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-administrative-permission-withCustomIRI-request",
              fileExtension = "json"
            ),
            text = createAdministrativePermissionWithCustomIriRequest
          )
        )

        val createAdministrativePermissionWithCustomIriResponse: String =
          s"""{
             |    "administrative_permission": {
             |        "forGroup": "http://rdfh.ch/groups/0001/thing-searcher",
             |        "forProject": "http://rdfh.ch/projects/0001",
             |        "hasPermissions": [
             |            {
             |                "additionalInformation": null,
             |                "name": "ProjectAdminGroupAllPermission",
             |                "permissionCode": null
             |            }
             |        ],
             |        "iri": "$customAPIri"
             |    }
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-administrative-permission-withCustomIRI-response",
              fileExtension = "json"
            ),
            text = createAdministrativePermissionWithCustomIriResponse
          )
        )
      }

      "create a new administrative permission for a new project" in {
        val projectIri = "http://rdfh.ch/projects/3333"
        val projectPayload =
          s"""
             |{
             |	"projectIri": "$projectIri",
             |    "shortname": "newprojectWithIri",
             |    "shortcode": "3333",
             |    "longname": "new project with a custom IRI",
             |    "description": [{"value": "a project created with a custom IRI", "language": "en"}],
             |    "keywords": ["projectIRI"],
             |    "logo": "/fu/bar/baz.jpg",
             |    "status": true,
             |    "selfjoin": false
             |
             |}
             |""".stripMargin

        val request = Post(
          baseApiUrl + s"/admin/projects",
          HttpEntity(ContentTypes.`application/json`, projectPayload)
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
          HttpEntity(ContentTypes.`application/json`, permissionPayload)
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))

        val permissionResponse: HttpResponse = singleAwaitingRequest(permissionRequest)
        assert(permissionResponse.status === StatusCodes.OK)

      }

      "create a default object access permission" in {
        val createDefaultObjectAccessPermissionRequest: String =
          s"""{
             |    "forGroup":"${SharedTestDataADM.thingSearcherGroup.id}",
             |    "forProject":"${SharedTestDataADM.ANYTHING_PROJECT_IRI}",
             |    "forProperty":null,
             |    "forResourceClass":null,
             |    "hasPermissions":[{"additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember","name":"D","permissionCode":7}]
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-defaultObjectAccess-permission-request",
              fileExtension = "json"
            ),
            text = createDefaultObjectAccessPermissionRequest
          )
        )

        val request = Post(
          baseApiUrl + s"/admin/permissions/doap",
          HttpEntity(ContentTypes.`application/json`, createDefaultObjectAccessPermissionRequest)
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
            throw DeserializationException("The expected field 'hasPermissions' is missing.")
          )
          .toString()

        assert(permissions.contains("http://www.knora.org/ontology/knora-admin#ProjectMember"))

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-defaultObjectAccess-permission-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "create a default object access permission with a custom IRI" in {
        val createDefaultObjectAccessPermissionWithCustomIriRequest: String =
          s"""{

             |    "id": "$customDOAPIri",
             |    "forGroup":null,
             |    "forProject":"${SharedTestDataADM.IMAGES_PROJECT_IRI}",
             |    "forProperty":null,
             |    "forResourceClass":"${SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS}",
             |    "hasPermissions":[{"additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember","name":"D","permissionCode":7}]
             |}""".stripMargin

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-defaultObjectAccess-permission-withCustomIRI-request",
              fileExtension = "json"
            ),
            text = createDefaultObjectAccessPermissionWithCustomIriRequest
          )
        )

        val request = Post(
          baseApiUrl + s"/admin/permissions/doap",
          HttpEntity(ContentTypes.`application/json`, createDefaultObjectAccessPermissionWithCustomIriRequest)
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
            throw DeserializationException("The expected field 'forResourceClass' is missing.")
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
            throw DeserializationException("The expected field 'hasPermissions' is missing.")
          )
          .toString()

        assert(permissions.contains("http://www.knora.org/ontology/knora-admin#ProjectMember"))

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "create-defaultObjectAccess-permission-withCustomIRI-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }
    }

    "updating permissions" should {
      "change the group of an administrative permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/00FF/a2"
        val encodedPermissionIri = java.net.URLEncoder.encode(permissionIri, "utf-8")
        val newGroupIri          = "http://rdfh.ch/groups/00FF/images-reviewer"
        val updatePermissionGroup =
          s"""{
             |    "forGroup": "$newGroupIri"
             |}""".stripMargin
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-administrative-permission-forGroup-request",
              fileExtension = "json"
            ),
            text = updatePermissionGroup
          )
        )
        val request = Put(
          baseApiUrl + s"/admin/permissions/" + encodedPermissionIri + "/group",
          HttpEntity(ContentTypes.`application/json`, updatePermissionGroup)
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val result = AkkaHttpUtils.httpResponseToJson(response).fields("administrative_permission").asJsObject.fields
        val groupIri = result
          .getOrElse("forGroup", throw DeserializationException("The expected field 'forGroup' is missing."))
          .convertTo[String]
        assert(groupIri == newGroupIri)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-administrative-permission-forGroup-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "change the group of a default object access permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/0803/003-d2"
        val encodedPermissionIri = java.net.URLEncoder.encode(permissionIri, "utf-8")
        val newGroupIri          = "http://rdfh.ch/groups/00FF/images-reviewer"
        val updatePermissionGroup =
          s"""{
             |    "forGroup": "$newGroupIri"
             |}""".stripMargin
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-defaultObjectAccess-permission-forGroup-request",
              fileExtension = "json"
            ),
            text = updatePermissionGroup
          )
        )
        val request = Put(
          baseApiUrl + s"/admin/permissions/" + encodedPermissionIri + "/group",
          HttpEntity(ContentTypes.`application/json`, updatePermissionGroup)
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val result =
          AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permission").asJsObject.fields
        val groupIri = result
          .getOrElse("forGroup", throw DeserializationException("The expected field 'forGroup' is missing."))
          .convertTo[String]
        assert(groupIri == newGroupIri)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-defaultObjectAccess-permission-forGroup-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "change the set of hasPermissions of an administrative permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/00FF/a2"
        val encodedPermissionIri = java.net.URLEncoder.encode(permissionIri, "utf-8")
        val updateHasPermissions =
          s"""{
             |   "hasPermissions":[{"additionalInformation":null,"name":"ProjectAdminGroupAllPermission","permissionCode":null}]
             |}""".stripMargin
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-administrative-permission-hasPermissions-request",
              fileExtension = "json"
            ),
            text = updateHasPermissions
          )
        )
        val request = Put(
          baseApiUrl + s"/admin/permissions/" + encodedPermissionIri + "/hasPermissions",
          HttpEntity(ContentTypes.`application/json`, updateHasPermissions)
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val result = AkkaHttpUtils.httpResponseToJson(response).fields("administrative_permission").asJsObject.fields
        val permissions = result
          .getOrElse(
            "hasPermissions",
            throw DeserializationException("The expected field 'hasPermissions' is missing.")
          )
          .asInstanceOf[JsArray]
          .elements
        permissions.size should be(1)
        assert(permissions.head.asJsObject.fields("name").toString.contains("ProjectAdminGroupAllPermission"))
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-administrative-permission-hasPermissions-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "change the set of hasPermissions of a default object access permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/0803/003-d1"
        val encodedPermissionIri = java.net.URLEncoder.encode(permissionIri, "utf-8")
        val updateHasPermissions =
          s"""{
             |   "hasPermissions":[{"additionalInformation":"http://www.knora.org/ontology/knora-admin#ProjectMember","name":"D","permissionCode":7}]
             |}""".stripMargin
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-defaultObjectAccess-permission-hasPermissions-request",
              fileExtension = "json"
            ),
            text = updateHasPermissions
          )
        )
        val request = Put(
          baseApiUrl + s"/admin/permissions/" + encodedPermissionIri + "/hasPermissions",
          HttpEntity(ContentTypes.`application/json`, updateHasPermissions)
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val result =
          AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permission").asJsObject.fields
        val permissions = result
          .getOrElse(
            "hasPermissions",
            throw DeserializationException("The expected field 'hasPermissions' is missing.")
          )
          .asInstanceOf[JsArray]
          .elements
        permissions.size should be(1)
        assert(
          permissions.head.asJsObject
            .fields("additionalInformation")
            .toString
            .contains("http://www.knora.org/ontology/knora-admin#ProjectMember")
        )
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-defaultObjectAccess-permission-hasPermissions-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "change the resource class of a default object access permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/0803/003-d1"
        val encodedPermissionIri = java.net.URLEncoder.encode(permissionIri, "utf-8")
        val resourceClassIri     = SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS
        val updateResourceClass =
          s"""{
             |   "forResourceClass":"$resourceClassIri"
             |}""".stripMargin
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-defaultObjectAccess-permission-forResourceClass-request",
              fileExtension = "json"
            ),
            text = updateResourceClass
          )
        )
        val request = Put(
          baseApiUrl + s"/admin/permissions/" + encodedPermissionIri + "/resourceClass",
          HttpEntity(ContentTypes.`application/json`, updateResourceClass)
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val result =
          AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permission").asJsObject.fields
        val forResourceClassIRI = result
          .getOrElse(
            "forResourceClass",
            throw DeserializationException("The expected field 'forResourceClass' is missing.")
          )
          .convertTo[String]
        assert(forResourceClassIRI == resourceClassIri)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-defaultObjectAccess-permission-forResourceClass-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "change the property of a default object access permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/00FF/d1"
        val encodedPermissionIri = java.net.URLEncoder.encode(permissionIri, "utf-8")
        val propertyClassIri     = SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY
        val updateResourceClass =
          s"""{
             |   "forProperty":"$propertyClassIri"
             |}""".stripMargin
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-defaultObjectAccess-permission-forProperty-request",
              fileExtension = "json"
            ),
            text = updateResourceClass
          )
        )
        val request = Put(
          baseApiUrl + s"/admin/permissions/" + encodedPermissionIri + "/property",
          HttpEntity(ContentTypes.`application/json`, updateResourceClass)
        ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass))
        val response: HttpResponse = singleAwaitingRequest(request)
        assert(response.status === StatusCodes.OK)
        val result =
          AkkaHttpUtils.httpResponseToJson(response).fields("default_object_access_permission").asJsObject.fields
        val forProperty = result
          .getOrElse("forProperty", throw DeserializationException("The expected field 'forProperty' is missing."))
          .convertTo[String]
        assert(forProperty == propertyClassIri)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "update-defaultObjectAccess-permission-forProperty-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }
    }

    "delete request" should {
      "erase a defaultObjectAccess permission" in {
        val permissionIri        = customDOAPIri
        val encodedPermissionIri = java.net.URLEncoder.encode(permissionIri, "utf-8")
        val request = Delete(baseApiUrl + s"/admin/permissions/" + encodedPermissionIri) ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val deletedStatus = AkkaHttpUtils.httpResponseToJson(response).fields("deleted")
        deletedStatus.convertTo[Boolean] should be(true)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "delete-defaultObjectAccess-permission-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }
      "erase an administrative permission" in {
        val permissionIri        = "http://rdfh.ch/permissions/00FF/a2"
        val encodedPermissionIri = java.net.URLEncoder.encode(permissionIri, "utf-8")
        val request = Delete(baseApiUrl + s"/admin/permissions/" + encodedPermissionIri) ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val deletedStatus = AkkaHttpUtils.httpResponseToJson(response).fields("deleted")
        deletedStatus.convertTo[Boolean] should be(true)
        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "delete-administrative-permission-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }
    }
  }
}
