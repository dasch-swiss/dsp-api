/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

/*
package org.knora.rapier.store.triplestore

import java.io.FileInputStream

import akka.actor.Props
import akka.testkit.ImplicitSender
import org.apache.jena.query.{DatasetAccessorFactory, DatasetAccessor}
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.sparql.function.library.leviathan.log
import org.knora.rapier.SettingsConstants._
import org.knora.rapier.messages.v1respondermessages.triplestoremessages._
import org.knora.rapier.{TriplestoreException, TriplestoreUnsupportedFeatureException, LiveActorMaker, CoreSpec}
import org.knora.rapier.store._

import scala.concurrent.duration._

/**
  * TODO: document this.
  */
class GraphStoreHTTPProtocolSpec extends CoreSpec() with ImplicitSender {

    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), STORE_MANAGER_ACTOR_NAME)

    private val timeout = 30.seconds
    private val tsType = settings.triplestoreType

    // println(system.settings.config.getConfig("app").root().render())
    // println(system.settings.config.getConfig("app.triplestore").root().render())

    val rdfDataObjects = List (
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/dokubib-onto.ttl", name = "http://www.knora.org/ontology/dokubib")
    )


    s"The Triplestore ($tsType) " when {
        if (tsType != EMBEDDED_JENA_TDB_TS_TYPE) {
            "accessed over the Graph Store HTTP Protocol " must {
                "allow writing to named graph " in {

                    //storeManager ! DropAllTriplestoreContent()
                    //expectMsg(3.seconds, DropAllTriplestoreContentACK())

                    val urlString: String = tsType match {
                        case HTTP_FUSEKI_TS_TYPE if !settings.fusekiTomcat => "http://" + settings.triplestoreHost + ":" + settings.triplestorePort + "/" + settings.triplestoreDatabaseName + "/data"
                        case HTTP_FUSEKI_TS_TYPE if settings.fusekiTomcat => "http://" + settings.triplestoreHost + ":" + settings.triplestorePort + "/" + settings.fusekiTomcatContext + "/" + settings.triplestoreDatabaseName + "/data"
                        case HTTP_GRAPH_DB_TS_TYPE => "http://" + settings.triplestoreHost + ":" + settings.triplestorePort + "/openrdf-sesame/repositories/" + settings.triplestoreDatabaseName + "/rdf-graphs/service"
                        //case HTTP_GRAPH_DB_TS_TYPE => "http://" + settings.triplestoreHost + ":4711/openrdf-sesame/repositories/" + settings.triplestoreDatabaseName + "/rdf-graphs/service"
                        case _ => throw TriplestoreUnsupportedFeatureException(s"insertDataIntoTriplestore: the triplestore of type ${settings.triplestoreType} is currently not supported")
                    }
                    try {
                        for (elem <- rdfDataObjects) {
                            val is = new FileInputStream(elem.path)

                            val model = ModelFactory.createDefaultModel()
                            model.read(is, null, "TURTLE")

                            val accessor: DatasetAccessor = DatasetAccessorFactory.createHTTP(urlString)
                            accessor.putModel(elem.name, model)

                            /*
                            if (tsType == HTTP_GRAPH_DB_TS_TYPE) {
                                /* need to update the lucene index */
                                val emptyModel = ModelFactory.createDefaultModel()
                                model.add()
                            }
                            */
                        }
                    } catch {
                        case e: Exception => throw e
                    }
                }
            }
        }
    }
}
*/