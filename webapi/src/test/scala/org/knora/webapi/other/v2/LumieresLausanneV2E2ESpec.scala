/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.other.v2

import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol

object LumieresLausanneV2E2ESpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for additional testing of permissions.
 */
class LumieresLausanneV2E2ESpec extends E2ESpec(LumieresLausanneV2E2ESpec.config) with TriplestoreJsonProtocol {

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/other.v2.LumieresLausanneV2E2ESpec/lumieres-lausanne_admin.ttl",
      name = "http://www.knora.org/data/admin"
    ),
    RdfDataObject(
      path = "test_data/other.v2.LumieresLausanneV2E2ESpec/lumieres-lausanne_permissions.ttl",
      name = "http://www.knora.org/data/permissions"
    ),
    RdfDataObject(
      path = "test_data/other.v2.LumieresLausanneV2E2ESpec/lumieres-lausanne-onto.ttl",
      name = "http://www.knora.org/ontology/0113/lumieres-lausanne"
    ),
    RdfDataObject(
      path = "test_data/other.v2.LumieresLausanneV2E2ESpec/lumieres-lausanne-data-lists.ttl",
      name = "http://www.knora.org/data/0113/lumieres-lausanne"
    )
  )

  "For project Lumieres Lausanne" should {

    val gfUserEmail = "gilles.faucherand@unil.ch"
    val testPass    = "test"

    "allow user 'gfaucherand' to create a resource using V2 API" in {

      val params =
        s"""
           |{
           |    "@type": "onto:User",
           |    "rdfs:label": "Test",
           |    "knora-api:attachedToProject": {
           |        "@id": "http://rdfh.ch/projects/0113"
           |    },
           |    "onto:isKnoraUser": {
           |        "@type": "knora-api:UriValue",
           |        "knora-api:uriValueAsUri": {
           |            "@type": "xsd:anyURI",
           |            "@value": "http://rdfh.ch/users/lumieres-lausanne-gfaucherand"
           |        }
           |    },
           |    "@context": {
           |        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "xsd": "http://www.w3.org/2001/XMLSchema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
           |        "onto": "http://0.0.0.0:3333/ontology/0113/lumieres-lausanne/v2#"
           |    }
           |}
                """.stripMargin

      val request =
        Post(baseApiUrl + s"/v2/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(
          BasicHttpCredentials(gfUserEmail, testPass)
        )
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)
    }
  }
}
