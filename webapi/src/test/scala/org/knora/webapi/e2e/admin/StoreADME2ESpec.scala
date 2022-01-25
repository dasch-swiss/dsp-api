/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.app.appmessages.SetAllowReloadOverHTTPState
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import spray.json._

import scala.concurrent.duration._

object StoreADME2ESpec {
  val config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for testing the 'v1/store' route.
 *
 * This spec tests the 'v1/store' route.
 */
class StoreADME2ESpec extends E2ESpec(StoreADME2ESpec.config) with TriplestoreJsonProtocol {

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(120.seconds)

  /**
   * The marshaling to Json is done automatically by spray, hence the import of the 'TriplestoreJsonProtocol'.
   * The Json which spray generates looks like this:
   *
   *  [
   *     {"path": "test_data/all_data/incunabula-data.ttl", "name": "http://www.knora.org/data/0803/incunabula"},
   *     {"path": "test_data/demo_data/images-demo-data.ttl", "name": "http://www.knora.org/data/00FF/images"}
   *  ]
   *
   * and could have been supplied to the post request instead of the scala object.
   */
  /*
    override lazy val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images")
    )
   */

  "The ResetTriplestoreContent Route ('admin/store/ResetTriplestoreContent')" should {

    "succeed with resetting if startup flag is set" in {

      /**
       * This test corresponds to the following curl call:
       * curl -H "Content-Type: application/json" -X POST -d '[{"path":"../knora-ontologies/knora-base.ttl","name":"http://www.knora.org/ontology/knora-base"}]' http://localhost:3333/admin/store/ResetTriplestoreContent
       */
      logger.debug("==>>")
      appActor ! SetAllowReloadOverHTTPState(true)
      logger.debug("==>>")
      val request = Post(
        baseApiUrl + "/admin/store/ResetTriplestoreContent",
        HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint)
      )
      val response = singleAwaitingRequest(request, 300.seconds)
      // log.debug("==>> " + response.toString)
      assert(response.status === StatusCodes.OK)
    }

    "fail with resetting if startup flag is not set" in {
      appActor ! SetAllowReloadOverHTTPState(false)
      val request = Post(
        baseApiUrl + "/admin/store/ResetTriplestoreContent",
        HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint)
      )
      val response = singleAwaitingRequest(request, 300.seconds)
      // log.debug("==>> " + response.toString)
      assert(response.status === StatusCodes.Forbidden)
    }
  }
}
