/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v1

import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}

/**
 * End-to-End (E2E) test specification for testing Knora-Sipi integration. Sipi must be running with the config file
 * `sipi.knora-docker-it-config.lua`.
 */
class KnoraSipiPermissionsV1ITSpec extends ITKnoraLiveSpec with TriplestoreJsonProtocol {

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  "Requesting Image" should {

    "returned as a restricted image in a smaller size" ignore {
      // TODO: https://github.com/dhlab-basel/Knora/issues/894
    }

    "denied with '401 Unauthorized' if the user does not have permission to see the image" ignore {
      // TODO: https://github.com/dhlab-basel/Knora/issues/894

    }
  }
}
