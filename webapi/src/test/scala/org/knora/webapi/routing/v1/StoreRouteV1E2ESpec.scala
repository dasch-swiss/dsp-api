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

package org.knora.webapi.routing.v1

import org.knora.webapi._
import spray.httpx.RequestBuilding



/**
  * End-to-End (E2E) test specification for testing the 'v1/store' route.
  */
class StoreRouteV1E2ESpec extends R2RSpec with RequestBuilding {


    /* Set the startup flags and start the Knora Server */
    StartupFlags.loadDemoData send true
    KnoraService.start

    val rdfDataObjectsJsonList =
        """
            [
                {"path": "../knora-ontologies/knora-base.ttl", "name": "http://www.knora.org/ontology/knora-base"},
                {"path": "../knora-ontologies/knora-dc.ttl", "name": "http://www.knora.org/ontology/dc"},
                {"path": "../knora-ontologies/salsah-gui.ttl", "name": "http://www.knora.org/ontology/salsah-gui"},
                {"path": "_test_data/ontologies/incunabula-onto.ttl", "name": "http://www.knora.org/ontology/incunabula"},
                {"path": "_test_data/all_data/incunabula-data.ttl", "name": "http://www.knora.org/data/incunabula"},
                {"path": "_test_data/all_data/admin-data.ttl", "name": "http://www.knora.org/data/admin"}
            ]
        """

    // TDOD: Write test only using HTTP

    "The ResetTriplestoreContent Route ('v1/store/ResetTriplestoreContent')" should {
        "succeed with resetting if startup flag is set" in {
            /**
              * This test corresponds to the following curl call:
              * curl -H "Content-Type: application/json" -X POST -d '[{"path":"../knora-ontologies/knora-base.ttl","name":"http://www.knora.org/ontology/knora-base"}]' http://localhost:3333/v1/store/ResetTriplestoreContent
              */
        }
        "fail with resetting if startup flag is not set" in {

        }
    }
}
