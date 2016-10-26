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

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.util.Timeout
import org.knora.webapi.e2e.E2ESpec
import org.knora.webapi.messages.v1.store.triplestoremessages.RdfDataObject
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1.StoreRouteV1
import org.knora.webapi.store._
import org.knora.webapi.{LiveActorMaker, StartupFlags}

import scala.concurrent.duration._

object StoreRouteV1E2ESpec {
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
class StoreRouteV1E2ESpec extends E2ESpec(StoreRouteV1E2ESpec.config) {

    import org.knora.webapi.messages.v1.store.triplestoremessages.TriplestoreJsonProtocol._

    /* Start a live ResponderManager */
    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

	/**
      * The marshaling to Json is done automatically by spray, hence the import of the 'TriplestoreJsonProtocol'.
      * The Json which spray generates looks like this:
      *
      *  [
      *     {"path": "_test_data/ontologies/incunabula-onto.ttl", "name": "http://www.knora.org/ontology/incunabula"},
      *     {"path": "_test_data/all_data/incunabula-data.ttl", "name": "http://www.knora.org/data/incunabula"},
      *     {"path": "_test_data/ontologies/images-demo-onto.ttl", "name": "http://www.knora.org/ontology/images"},
      *     {"path": "_test_data/demo_data/images-demo-data.ttl", "name": "http://www.knora.org/data/images"}
      *
      *  ]
      *
      * and could have been supplied to the post request instead of the scala object.
      */
    private val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images")
    )

    "The ResetTriplestoreContent Route ('v1/store/ResetTriplestoreContent')" should {

        "succeed with resetting if startup flag is set" ignore {
            /**
              * This test corresponds to the following curl call:
              * curl -H "Content-Type: application/json" -X POST -d '[{"path":"../knora-ontologies/knora-base.ttl","name":"http://www.knora.org/ontology/knora-base"}]' http://localhost:3333/v1/store/ResetTriplestoreContent
              */

			StartupFlags.allowResetTriplestoreContentOperationOverHTTP send true
			log.debug(s"StartupFlags.allowResetTriplestoreContentOperationOverHTTP = ${StartupFlags.allowResetTriplestoreContentOperationOverHTTP.get}")

            val response: HttpResponse = Await.result(pipe(Post(s"${baseApiUrl}v1/store/ResetTriplestoreContent", rdfDataObjects)), 300 seconds)
            log.debug("==>> " + response.toString)
            assert(response.status === StatusCodes.OK)
        }


        "fail with resetting if startup flag is not set" ignore {
            StartupFlags.allowResetTriplestoreContentOperationOverHTTP send false
            //log.debug("==>> before")
            val response: HttpResponse = Await.result(pipe(Post(s"${baseApiUrl}v1/store/ResetTriplestoreContent", rdfDataObjects)), 300 seconds)
            //log.debug("==>> " + response.toString)
            assert(response.status === StatusCodes.Forbidden)
        }
    }
}
