/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi

import com.typesafe.config.ConfigFactory

object SettingsSpec {
    val config = ConfigFactory.parseString(
        """
        akka {
            loggers = ["akka.event.Logging$DefaultLogger"]
            loglevel = "ERROR"
            stdout-loglevel = "ERROR"
            log-config-on-start = off
        }
        app {
            triplestore {
                dbtype = "embedded-jena-tdb"
                host = "localhost"

                virtuoso {
                    port = 8890 // this is for virtuoso
                    username = "dba" // for virtuoso
                    password = "dba" // for virtuoso
                }

                embedded-jena-tdb {
                     persisted = true
                     storage-path = "_TMP" // ignored if "memory
                }

                reload-on-start = false // ignored if "memory" as it will always reload
                 rdf-data = [
                     {
                         path = "_test_data/1_export/knora-base.ttl"
                         name = "http://www.knora.org/ontology/knora-base"
                     }
                     {
                         path = "_test_data/1_export/knora-admin.ttl"
                         name = "http://www.knora.org/ontology/knora-admin"
                     }
                     {
                         path = "_test_data/1_export/knora-dc.ttl"
                         name = "http://www.knora.org/ontology/dc"
                     }
                     {
                         path = "_test_data/1_export/salsah-gui.ttl"
                         name = "http://www.knora.org/ontology/salsah-gui"
                     }
                     {
                         path = "_test_data/1_export/incunabula-onto.ttl"
                         name = "http://www.knora.org/ontology/incunabula"
                     }
                     {
                         path = "_test_data/1_export/incunabula-data.ttl"
                         name = "http://www.knora.org/data/incunabula"
                     }
                     {
                         path = "_test_data/1_export/dokubib-onto.ttl"
                         name = "http://www.knora.org/ontology/dokubib"
                     }
                     {
                         path = "_test_data/1_export/dokubib-data.ttl"
                         name = "http://www.knora.org/data/dokubib"
                     }
                 ]

                // Set to "use" to use a fake triplestore for performance testing. Set to "prepare" to generate a fake triplestore
                // (org.knora.rapier.store.FakeTriplestore) by getting data from the real triplestore. Set to "off" to just use
                // the real triplestore.
                fake-triplestore = "off"
                fake-triplestore-template = "src/main/scala/org/knora/rapier/store/FakeTriplestore.scala.tmpl"
                fake-triplestore-source = "src/main/scala/org/knora/rapier/store/FakeTriplestore.scala"
            }

            project-named-graphs =
            [
                {
                    project = "http://data.knora.org/projects/77275339"
                    ontology = "http://www.knora.org/ontology/incunabula"
                    data = "http://www.knora.org/data/incunabula"
                }
                {
                    project = "http://data.knora.org/projects/b83b99ca01"
                    ontology = "http://www.knora.org/ontology/dokubib"
                    data = "http://www.knora.org/data/dokubib"
                }
            ]

        }
        """.stripMargin)
}

class SettingsSpec extends CoreSpec("SettingsActorTestSystem", SettingsSpec.config) {

    "The RapierSettings Object" should {
        "provide access to all config values" in {
            settings.triplestoreType should ===("embedded-jena-tdb")

        }
        "provide access to the project to named graph mapping" in {
            val mapping = settings.projectNamedGraphs
            assert(mapping("http://data.knora.org/projects/77275339").ontology === "http://www.knora.org/ontology/incunabula")
            assert(mapping("http://data.knora.org/projects/77275339").data === "http://www.knora.org/data/incunabula")
            assert(mapping("http://data.knora.org/projects/b83b99ca01").ontology === "http://www.knora.org/ontology/dokubib")
            assert(mapping("http://data.knora.org/projects/b83b99ca01").data === "http://www.knora.org/data/dokubib")
        }

    }
}
