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

package org.knora.webapi.store.triplestore

import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.CoreSpec

object PerformanceFakeTriplestoreSpec {
    val config = ConfigFactory.parseString(
        """
         app {
            triplestore {
                dbtype = "embedded-jena-tdb"

                embedded-jena-tdb {
                    persisted = true // "true" -> disk, "false" -> in-memory
                    storage-path = "_TMP" // ignored if "in-memory"
                }

                reload-on-start = true // ignored if "memory" as data will need to be reloaded.

                rdf-data = [
                        {
                            path = "../knora-ontologies/knora-base.ttl"
                            name = "http://www.knora.org/ontology/knora-base"
                        }
                        {
                            path = "../knora-ontologies/salsah-gui.ttl"
                            name = "http://www.knora.org/ontology/salsah-gui"
                        }
                        {
                            path = "_test_data/ontologies/incunabula-onto.ttl"
                            name = "http://www.knora.org/ontology/incunabula"
                        }
                        {
                            path = "_test_data/ontologies/dokubib-onto.ttl"
                            name = "http://www.knora.org/ontology/dokubib"
                        }
                    ]
            }
         }
        """.stripMargin)
}

/**
  * A test using the fake triplestore to measure the performance of a set of queries
  */
class PerformanceFakeTriplestoreSpec extends CoreSpec("PerformanceFakeTriplestoreSpec", PerformanceFakeTriplestoreSpec.config) with ImplicitSender {
    /**
      * This test should have the following steps
      *
      * 1. Put Embedded-Fakeable triplestore in fake mode
      * 2. Run a predefined set of queries to prep the fake triplestore
      * 3. Run the benchmark queries and create a performance report
      */
}