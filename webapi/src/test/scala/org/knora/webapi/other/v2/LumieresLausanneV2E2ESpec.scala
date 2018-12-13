/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.other.v2

import akka.event.LoggingAdapter
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}

object LumieresLausanneV2E2ESpec {
    val config: Config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for additional testing of permissions.
  */
class LumieresLausanneV2E2ESpec extends E2ESpec(LumieresLausanneV2E2ESpec.config) with TriplestoreJsonProtocol {

    implicit override lazy val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

    override lazy val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/other.v2.LumieresLausanneV2E2ESpec/lumieres-lausanne_admin.ttl", name = "http://www.knora.org/data/admin"),
        RdfDataObject(path = "_test_data/other.v2.LumieresLausanneV2E2ESpec/lumieres-lausanne_permissions.ttl", name = "http://www.knora.org/data/permissions"),
        RdfDataObject(path = "_test_data/other.v2.LumieresLausanneV2E2ESpec/lumieres-lausanne-onto.ttl", name = "http://www.knora.org/ontology/0113/lumieres-lausanne"),
        RdfDataObject(path = "_test_data/other.v2.LumieresLausanneV2E2ESpec/lumieres-lausanne-data-lists.ttl", name = "http://www.knora.org/data/0113/lumieres-lausanne")
    )

    "For project Lumieres Lausanne" should {

        val gfUser = "gfaucherand"
        val testPass = "test"

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

            val request = Post(baseApiUrl + s"/v2/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(gfUser, testPass))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status === StatusCodes.OK)
        }
    }
}
