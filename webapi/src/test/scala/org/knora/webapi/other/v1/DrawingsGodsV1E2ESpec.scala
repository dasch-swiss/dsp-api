/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.other.v1

import java.net.URLEncoder

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.util.{MutableTestIri, ResourceResponseExtractorMethods, ValuesResponseExtractorMethods}
import spray.json._

import scala.concurrent.duration._

object DrawingsGodsV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for additional testing of permissions.
  */
class DrawingsGodsV1E2ESpec extends E2ESpec(DrawingsGodsV1E2ESpec.config) with TriplestoreJsonProtocol {

    implicit override val log = akka.event.Logging(system, this.getClass())

    private val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1E2ESpec/rvp-admin-data.ttl", name = "http://www.knora.org/data/admin"),
        RdfDataObject(path = "_test_data/other.v1.DrawingsGodsV1E2ESpec/rvp-permissions-data.ttl", name = "http://www.knora.org/data/permissions")
    )

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    /**
      *  1a. parole-religieuse user creates a resource
      *  1b. parole-religieuse user create a value
      *  2a. drawings-gods user changes existing value
      *  2b1. drawings-gods user creates a new value (inside parole-religieuse project)
      *  2b2. drawings-gods user creates a new value (inside drawings-gods project)
      */
    "issue: https://github.com/dhlab-basel/Knora/issues/408" should {

        val drawingsOfGodsUserEmail = "drawings-gods-test-user@example.com"
        val paroleReligieuseUserEmail = "parole@unil.ch"
        val testPass = "test"
        val thingIri = new MutableTestIri
        val firstValueIri = new MutableTestIri
        val secondValueIri = new MutableTestIri

        "1a. parole-religieuse user creates a resource" in {

            val params =
                s"""
                   |{
                   |    "restype_id": "http://www.knora.org/ontology/anything#Thing",
                   |    "label": "A thing",
                   |    "project_id": "http://data.knora.org/projects/parole-religieuse",
                   |    "properties": {}
                   |}
                """.stripMargin

            val request = Post(baseApiUrl + s"/v1/resources", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(paroleReligieuseUserEmail, testPass))
            val response: HttpResponse = singleAwaitingRequest(request)

            assert(response.status === StatusCodes.OK)
            val resId = ResourceResponseExtractorMethods.getResIriFromJsonResponse(response)

            thingIri.set(resId)
            log.debug(s"1a. thingIri: ${thingIri.get}")
        }


        "1b. parole-religieuse user adds an integer value to a resource" in {
            val params =
                s"""
                  |{
                  |    "project_id": "http://data.knora.org/projects/parole-religieuse",
                  |    "res_id": "${thingIri.get}",
                  |    "prop": "http://www.knora.org/ontology/anything#hasInteger",
                  |    "int_value": 1234
                  |}
                """.stripMargin

            val request = Post(baseApiUrl + s"/v1/values", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(paroleReligieuseUserEmail, testPass))
            val response: HttpResponse = singleAwaitingRequest(request)

            assert(response.status === StatusCodes.OK)
            val valId = ValuesResponseExtractorMethods.getNewValueIriFromJsonResponse(response)

            firstValueIri.set(valId)
            log.debug(s"1b. firstValueIri: ${firstValueIri.get}")
        }

        "2a. drawings-gods user changes existing value" in {
            val params =
                s"""
                  |{
                  |    "project_id": "http://data.knora.org/projects/parole-religieuse",
                  |    "res_id": "${thingIri.get}",
                  |    "prop": "http://www.knora.org/ontology/anything#hasInteger",
                  |    "int_value": 1111
                  |}
                """.stripMargin


            val request = Put(baseApiUrl + s"/v1/values/${URLEncoder.encode(firstValueIri.get, "UTF-8")}", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(drawingsOfGodsUserEmail, testPass))
            val response: HttpResponse = singleAwaitingRequest(request)

            assert(response.status === StatusCodes.OK)
            val valId = ValuesResponseExtractorMethods.getNewValueIriFromJsonResponse(response)

            firstValueIri.set(valId)
            log.debug(s"2a. firstValueIri: ${firstValueIri.get}")
        }

        "2b1. drawings-gods user creates a new value inside the parole-religieuse project" in {
            val params =
                s"""
                   |{
                   |    "project_id": "http://data.knora.org/projects/parole-religieuse",
                   |    "res_id": "${thingIri.get}",
                   |    "prop": "http://www.knora.org/ontology/anything#hasInteger",
                   |    "int_value": 2222
                   |}
                """.stripMargin


            val request = Post(baseApiUrl + s"/v1/values", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(drawingsOfGodsUserEmail, testPass))
            val response: HttpResponse = singleAwaitingRequest(request)

            assert(response.status === StatusCodes.OK)
            val valId = ValuesResponseExtractorMethods.getNewValueIriFromJsonResponse(response)

            secondValueIri.set(valId)
            log.debug(s"2b1. secondValueIri: ${secondValueIri.get}")
        }

        "2b2. drawings-gods user is not allowed to create a new value inside the drawings-gods project (no permissions for this project are defined which would lead to no permissions attached to the value)" in {
            val params =
                s"""
                   |{
                   |    "project_id": "http://data.knora.org/projects/drawings-gods",
                   |    "res_id": "${thingIri.get}",
                   |    "prop": "http://www.knora.org/ontology/anything#hasInteger",
                   |    "int_value": 3333
                   |}
                """.stripMargin


            val request = Post(baseApiUrl + s"/v1/values", HttpEntity(ContentTypes.`application/json`, params)) ~> addCredentials(BasicHttpCredentials(drawingsOfGodsUserEmail, testPass))
            val response: HttpResponse = singleAwaitingRequest(request)

            assert(response.status === StatusCodes.BadRequest)
        }
    }
}
