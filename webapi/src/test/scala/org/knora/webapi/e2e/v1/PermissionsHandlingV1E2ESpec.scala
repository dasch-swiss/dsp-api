/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v1

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials

import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataV1

/**
 * End-to-end test specification for testing the handling of permissions.
 */
class PermissionsHandlingV1E2ESpec extends E2ESpec with TriplestoreJsonProtocol {

  private val rootUser      = SharedTestDataV1.rootUser
  private val rootUserEmail = rootUser.userData.email.get

  private val imagesUser      = SharedTestDataV1.imagesUser01
  private val imagesUserEmail = imagesUser.userData.email.get

  private val incunabulaUser      = SharedTestDataV1.incunabulaProjectAdminUser
  private val incunabulaUserEmail = incunabulaUser.userData.email.get

  private val password = SharedTestDataADM.testPass

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest"
    ),
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  "The Permissions Handling" should {

    "allow a project member to create a resource" in {

      val params =
        """
          |{
          |    "restype_id": "http://www.knora.org/ontology/00FF/images#person",
          |    "label": "Testperson",
          |    "project_id": "http://rdfh.ch/projects/00FF",
          |    "properties": {
          |        "http://www.knora.org/ontology/00FF/images#lastname": [{"richtext_value":{"utf8str":"Testname"}}],
          |        "http://www.knora.org/ontology/00FF/images#firstname": [{"richtext_value":{"utf8str":"Name"}}]
          |    }
          |}
                """.stripMargin

      val request =
        Post(baseApiUrl + s"/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
          BasicHttpCredentials(imagesUserEmail, password)
        )
      val response: HttpResponse = singleAwaitingRequest(request)

      assert(response.status === StatusCodes.OK)

    }

    "allow a system admin user not in the project to create a resource" in {

      val params =
        """
          |{
          |    "restype_id": "http://www.knora.org/ontology/00FF/images#person",
          |    "label": "Testperson",
          |    "project_id": "http://rdfh.ch/projects/00FF",
          |    "properties": {
          |        "http://www.knora.org/ontology/00FF/images#lastname": [{"richtext_value":{"utf8str":"Testname"}}],
          |        "http://www.knora.org/ontology/00FF/images#firstname": [{"richtext_value":{"utf8str":"Name"}}]
          |    }
          |}
                """.stripMargin

      val request =
        Post(baseApiUrl + s"/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
          BasicHttpCredentials(rootUserEmail, password)
        )
      singleAwaitingRequest(request)
    }

    "not allow a user from another project to create a resource" in {

      val params =
        """
          |{
          |    "restype_id": "http://www.knora.org/ontology/00FF/images#person",
          |    "label": "Testperson",
          |    "project_id": "http://rdfh.ch/projects/00FF",
          |    "properties": {
          |        "http://www.knora.org/ontology/00FF/images#lastname": [{"richtext_value":{"utf8str":"Testname"}}],
          |        "http://www.knora.org/ontology/00FF/images#firstname": [{"richtext_value":{"utf8str":"Name"}}]
          |    }
          |}
                """.stripMargin

      val request =
        Post(baseApiUrl + s"/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
          BasicHttpCredentials(incunabulaUserEmail, password)
        )
      singleAwaitingRequest(request)
    }
  }

}
