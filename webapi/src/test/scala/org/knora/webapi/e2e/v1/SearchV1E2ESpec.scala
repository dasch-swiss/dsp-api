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

package org.knora.webapi.e2e.v1

import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.store.triplestoremessages.RdfDataObject
import org.knora.webapi.{E2ESpec, StartupFlags}
import spray.client.pipelining._
import spray.http.{HttpResponse, StatusCodes}
import spray.httpx.SprayJsonSupport._

import scala.concurrent.Await
import scala.concurrent.duration._

object SearchV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing the 'v1/store' route.
  *
  * This spec tests the 'v1/store' route.
  */
class SearchV1E2ESpec extends E2ESpec(SearchV1E2ESpec.config) {

    import org.knora.webapi.messages.v1.store.triplestoremessages.TriplestoreJsonProtocol._

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/all_data/admin-data.ttl", name = "http://www.knora.org/data/admin"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images")
    )

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        Await.result(pipe(Post(s"${baseApiUrl}v1/store/ResetTriplestoreContent", rdfDataObjects)), 300 seconds)
    }

    "The Search Route ('v1/search')" should {
        "allow simple search" in {
            /* http://localhost:3333/v1/search/Zeitglöcklein?searchtype=fulltext */
            val response: HttpResponse = Await.result(pipe(Get(s"${baseApiUrl}v1/search/Zeitgl%C3%B6cklein?searchtype=fulltext")), 5 seconds)
            log.debug(s"==>> ${response.toString}")
            log.debug(s"==>> ${response.entity.data.asString}")
            assert(response.status === StatusCodes.OK)

        }
        "allow (1) extended search" in {
            /* http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http://www.knora.org/ontology/incunabula#book&property_id=http://www.knora.org/ontology/incunabula#title&compop=MATCH&searchval=Zeitglöcklein */
            val response: HttpResponse = Await.result(pipe(Get(s"${baseApiUrl}v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23title&compop=MATCH&searchval=Zeitgl%C3%B6cklein%20")), 5 seconds)
            log.debug("==>> " + response.toString)
            assert(response.status === StatusCodes.OK)
        }
        "allow (2) extended search" in {
            /* http://localhost:3333/v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23partOf&compop=EQ&searchval=http%3A%2F%2Fdata.knora.org%2Fc5058f3a */
            val response: HttpResponse = Await.result(pipe(Get(s"${baseApiUrl}v1/search/?searchtype=extended&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23partOf&compop=EQ&searchval=http%3A%2F%2Fdata.knora.org%2Fc5058f3a")), 5 seconds)
            log.debug("==>> " + response.toString)
            assert(response.status === StatusCodes.OK)
        }


    }
}
