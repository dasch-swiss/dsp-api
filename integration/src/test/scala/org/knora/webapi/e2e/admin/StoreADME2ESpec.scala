/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin

import org.apache.pekko
import spray.json._
import zio.Duration

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol

import pekko.http.scaladsl.model.ContentTypes
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.StatusCodes

/**
 * End-to-End (E2E) test specification for testing the 'v1/store' route.
 *
 * This spec tests the 'v1/store' route.
 */
class StoreADME2ESpec extends E2ESpec with TriplestoreJsonProtocol {

  "The ResetTriplestoreContent Route ('admin/store/ResetTriplestoreContent')" should {

    "succeed with resetting" in {

      /**
       * This test corresponds to the following curl call:
       * curl -H "Content-Type: application/json" -X POST -d '[{"path":"../knora-ontologies/knora-base.ttl","name":"http://www.knora.org/ontology/knora-base"}]' http://localhost:3333/admin/store/ResetTriplestoreContent
       */
      val request = Post(
        baseApiUrl + "/admin/store/ResetTriplestoreContent",
        HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint)
      )
      val response = singleAwaitingRequest(request, Duration.fromSeconds(480))
      assert(response.status === StatusCodes.OK)
    }
  }
}
