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

package org.knora.webapi.responders.v1


import akka.actor.Props
import akka.testkit._
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v1.responder.listmessages._
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.responders._
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}

import scala.concurrent.duration._


/**
  * Static data for testing [[ListsResponderV1]].
  */
object ListsResponderV1Spec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)

}

/**
  * Tests [[ListsResponderV1]].
  */
class ListsResponderV1Spec extends CoreSpec(ListsResponderV1Spec.config) with ImplicitSender {

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[ListsResponderV1]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/responders.v1.HierarchicalListsResponderV1Spec/dokubib-data.ttl", name = "http://www.knora.org/data/dokubib")
    )

    // A test UserProfileV1.
    private val userProfile = SharedAdminTestData.incunabulaProjectAdminUser

    // A test UserDataV1.
    private val userData = userProfile.userData

    private val hKeywords = HListGetResponseV1(
        hlist = Vector(
            HierarchicalListV1(
                position = 0,
                level = 0,
                children = Vector(
                    HierarchicalListV1(
                        position = 0,
                        level = 1,
                        children = Nil,
                        label = Some("BIBLIOTHEKEN ST. MORITZ"),
                        name = Some("1"),
                        id = "http://data.knora.org/lists/412821d3a6"
                    ),
                    HierarchicalListV1(
                        position = 1,
                        level = 1,
                        children = Nil,
                        label = Some("SAMMELB\u00C4NDE, FOTOALBEN"),
                        name = Some("6"),
                        id = "http://data.knora.org/lists/da5b740ca7"
                    )
                ),
                label = Some("ALLGEMEINES"),
                name = Some("1ALL"),
                id = "http://data.knora.org/lists/a8f4cd99a6"
            ),
            HierarchicalListV1(
                position = 1,
                level = 0,
                children = Vector(
                    HierarchicalListV1(
                        position = 0,
                        level = 1,
                        children = Vector(HierarchicalListV1(
                            position = 0,
                            level = 2,
                            children = Nil,
                            label = Some("Personen"),
                            name = Some("1"),
                            id = "http://data.knora.org/lists/a5f66db8a7"
                        )),
                        label = Some("SCHWEIZ"),
                        name = Some("1"),
                        id = "http://data.knora.org/lists/0cc31a7fa7"
                    ),
                    HierarchicalListV1(
                        position = 1,
                        level = 1,
                        children = Vector(HierarchicalListV1(
                            position = 0,
                            level = 2,
                            children = Nil,
                            label = Some("Personen"),
                            name = Some("1"),
                            id = "http://data.knora.org/lists/d75d142ba8"
                        )),
                        label = Some("GRAUB\u00DCNDEN"),
                        name = Some("2"),
                        id = "http://data.knora.org/lists/3e2ac1f1a7"
                    ),
                    HierarchicalListV1(
                        position = 2,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Flugaufnahmen"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/09c5ba9da8"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Landschaft Sommer ohne Ortschaften"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/a2f80dd7a8"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Landschaft Sommer mit Ortschaften"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/3b2c6110a9"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Landschaft Winter ohne Ortschaften"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/d45fb449a9"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Landschaft Winter mit Ortschaften"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/6d930783a9"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("Landschaft Seen"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/06c75abca9"
                            ),
                            HierarchicalListV1(
                                position = 6,
                                level = 2,
                                children = Nil,
                                label = Some("Landschaft Berge"),
                                name = Some("7"),
                                id = "http://data.knora.org/lists/9ffaadf5a9"
                            ),
                            HierarchicalListV1(
                                position = 7,
                                level = 2,
                                children = Vector(
                                    HierarchicalListV1(
                                        position = 0,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Maloja"),
                                        name = Some("1"),
                                        id = "http://data.knora.org/lists/d1615468aa"
                                    ),
                                    HierarchicalListV1(
                                        position = 1,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Sils"),
                                        name = Some("2"),
                                        id = "http://data.knora.org/lists/6a95a7a1aa"
                                    ),
                                    HierarchicalListV1(
                                        position = 2,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Silvaplana"),
                                        name = Some("3"),
                                        id = "http://data.knora.org/lists/03c9fadaaa"
                                    ),
                                    HierarchicalListV1(
                                        position = 3,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Surlej"),
                                        name = Some("4"),
                                        id = "http://data.knora.org/lists/9cfc4d14ab"
                                    ),
                                    HierarchicalListV1(
                                        position = 4,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Champf\u00E8r"),
                                        name = Some("5"),
                                        id = "http://data.knora.org/lists/3530a14dab"
                                    ),
                                    HierarchicalListV1(
                                        position = 5,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Pontresina"),
                                        name = Some("6"),
                                        id = "http://data.knora.org/lists/ce63f486ab"
                                    ),
                                    HierarchicalListV1(
                                        position = 6,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Celerina"),
                                        name = Some("7"),
                                        id = "http://data.knora.org/lists/679747c0ab"
                                    ),
                                    HierarchicalListV1(
                                        position = 7,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Samedan"),
                                        name = Some("8"),
                                        id = "http://data.knora.org/lists/00cb9af9ab"
                                    ),
                                    HierarchicalListV1(
                                        position = 8,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Bever"),
                                        name = Some("9"),
                                        id = "http://data.knora.org/lists/99feed32ac"
                                    ),
                                    HierarchicalListV1(
                                        position = 9,
                                        level = 3,
                                        children = Nil,
                                        label = Some("La Punt"),
                                        name = Some("10"),
                                        id = "http://data.knora.org/lists/3232416cac"
                                    ),
                                    HierarchicalListV1(
                                        position = 10,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Chamues-ch"),
                                        name = Some("11"),
                                        id = "http://data.knora.org/lists/cb6594a5ac"
                                    ),
                                    HierarchicalListV1(
                                        position = 11,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Madulain"),
                                        name = Some("12"),
                                        id = "http://data.knora.org/lists/6499e7deac"
                                    ),
                                    HierarchicalListV1(
                                        position = 12,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Zuoz"),
                                        name = Some("13"),
                                        id = "http://data.knora.org/lists/fdcc3a18ad"
                                    ),
                                    HierarchicalListV1(
                                        position = 13,
                                        level = 3,
                                        children = Nil,
                                        label = Some("S-chanf"),
                                        name = Some("14"),
                                        id = "http://data.knora.org/lists/96008e51ad"
                                    ),
                                    HierarchicalListV1(
                                        position = 14,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Cinous-chel"),
                                        name = Some("15"),
                                        id = "http://data.knora.org/lists/2f34e18aad"
                                    ),
                                    HierarchicalListV1(
                                        position = 15,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Fex"),
                                        name = Some("16"),
                                        id = "http://data.knora.org/lists/c86734c4ad"
                                    ),
                                    HierarchicalListV1(
                                        position = 16,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Unterengadin"),
                                        name = Some("17"),
                                        id = "http://data.knora.org/lists/619b87fdad"
                                    ),
                                    HierarchicalListV1(
                                        position = 17,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen"),
                                        name = Some("18"),
                                        id = "http://data.knora.org/lists/faceda36ae"
                                    )
                                ),
                                label = Some("Ortschaften Sommer"),
                                name = Some("8"),
                                id = "http://data.knora.org/lists/382e012faa"
                            ),
                            HierarchicalListV1(
                                position = 8,
                                level = 2,
                                children = Vector(
                                    HierarchicalListV1(
                                        position = 0,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Maloja"),
                                        name = Some("1"),
                                        id = "http://data.knora.org/lists/2c3681a9ae"
                                    ),
                                    HierarchicalListV1(
                                        position = 1,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Sils"),
                                        name = Some("2"),
                                        id = "http://data.knora.org/lists/c569d4e2ae"
                                    ),
                                    HierarchicalListV1(
                                        position = 2,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Silvaplana"),
                                        name = Some("3"),
                                        id = "http://data.knora.org/lists/5e9d271caf"
                                    ),
                                    HierarchicalListV1(
                                        position = 3,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Surlej"),
                                        name = Some("4"),
                                        id = "http://data.knora.org/lists/f7d07a55af"
                                    ),
                                    HierarchicalListV1(
                                        position = 4,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Champf\u00E8r"),
                                        name = Some("5"),
                                        id = "http://data.knora.org/lists/9004ce8eaf"
                                    ),
                                    HierarchicalListV1(
                                        position = 5,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Pontresina"),
                                        name = Some("6"),
                                        id = "http://data.knora.org/lists/293821c8af"
                                    ),
                                    HierarchicalListV1(
                                        position = 6,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Celerina"),
                                        name = Some("7"),
                                        id = "http://data.knora.org/lists/c26b7401b0"
                                    ),
                                    HierarchicalListV1(
                                        position = 7,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Samedan"),
                                        name = Some("8"),
                                        id = "http://data.knora.org/lists/5b9fc73ab0"
                                    ),
                                    HierarchicalListV1(
                                        position = 8,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Bever"),
                                        name = Some("9"),
                                        id = "http://data.knora.org/lists/f4d21a74b0"
                                    ),
                                    HierarchicalListV1(
                                        position = 9,
                                        level = 3,
                                        children = Nil,
                                        label = Some("La Punt"),
                                        name = Some("10"),
                                        id = "http://data.knora.org/lists/8d066eadb0"
                                    ),
                                    HierarchicalListV1(
                                        position = 10,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Chamues-ch"),
                                        name = Some("11"),
                                        id = "http://data.knora.org/lists/263ac1e6b0"
                                    ),
                                    HierarchicalListV1(
                                        position = 11,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Madulain"),
                                        name = Some("12"),
                                        id = "http://data.knora.org/lists/bf6d1420b1"
                                    ),
                                    HierarchicalListV1(
                                        position = 12,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Zuoz"),
                                        name = Some("13"),
                                        id = "http://data.knora.org/lists/58a16759b1"
                                    ),
                                    HierarchicalListV1(
                                        position = 13,
                                        level = 3,
                                        children = Nil,
                                        label = Some("S-chanf"),
                                        name = Some("14"),
                                        id = "http://data.knora.org/lists/f1d4ba92b1"
                                    ),
                                    HierarchicalListV1(
                                        position = 14,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Cinous-chel"),
                                        name = Some("15"),
                                        id = "http://data.knora.org/lists/8a080eccb1"
                                    ),
                                    HierarchicalListV1(
                                        position = 15,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Fex"),
                                        name = Some("16"),
                                        id = "http://data.knora.org/lists/233c6105b2"
                                    ),
                                    HierarchicalListV1(
                                        position = 16,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Unterengadin"),
                                        name = Some("17"),
                                        id = "http://data.knora.org/lists/bc6fb43eb2"
                                    ),
                                    HierarchicalListV1(
                                        position = 17,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen"),
                                        name = Some("18"),
                                        id = "http://data.knora.org/lists/55a30778b2"
                                    )
                                ),
                                label = Some("Ortschaften Winter"),
                                name = Some("9"),
                                id = "http://data.knora.org/lists/93022e70ae"
                            )
                        ),
                        label = Some("ENGADIN"),
                        name = Some("3"),
                        id = "http://data.knora.org/lists/70916764a8"
                    ),
                    HierarchicalListV1(
                        position = 3,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("St. Moritz Dorf und Bad Winter"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/870aaeeab2"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("St. Moritz Dorf Sommer"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/203e0124b3"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("St. Moritz Bad Sommer"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/b971545db3"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("St. Moritz Denkm\u00E4ler"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/52a5a796b3"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("St. Moritz Landschaft Sommer"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/ebd8facfb3"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("St. Moritz Landschaft Winter"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/840c4e09b4"
                            ),
                            HierarchicalListV1(
                                position = 6,
                                level = 2,
                                children = Nil,
                                label = Some("St. Moritz Schulh\u00E4user"),
                                name = Some("7"),
                                id = "http://data.knora.org/lists/1d40a142b4"
                            )
                        ),
                        label = Some("ST. MORITZ"),
                        name = Some("4"),
                        id = "http://data.knora.org/lists/eed65ab1b2"
                    ),
                    HierarchicalListV1(
                        position = 4,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Ortschaften"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/4fa747b5b4"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Landschaften"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/e8da9aeeb4"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Personen"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/45cfa1df0401"
                            )
                        ),
                        label = Some("SUEDTAELER"),
                        name = Some("5"),
                        id = "http://data.knora.org/lists/b673f47bb4"
                    ),
                    HierarchicalListV1(
                        position = 5,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Landkarten"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/1a424161b5"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Panoramen"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/b375949ab5"
                            )
                        ),
                        label = Some("LANDKARTEN UND PANORAMEN"),
                        name = Some("6"),
                        id = "http://data.knora.org/lists/810eee27b5"
                    )
                ),
                label = Some("GEOGRAPHIE"),
                name = Some("2GEO"),
                id = "http://data.knora.org/lists/738fc745a7"
            ),
            HierarchicalListV1(
                position = 2,
                level = 0,
                children = Vector(
                    HierarchicalListV1(
                        position = 0,
                        level = 1,
                        children = Nil,
                        label = Some("SCHWEIZ"),
                        name = Some("1"),
                        id = "http://data.knora.org/lists/de02f5180501"
                    ),
                    HierarchicalListV1(
                        position = 1,
                        level = 1,
                        children = Nil,
                        label = Some("GRAUB\u00DCNDEN"),
                        name = Some("2"),
                        id = "http://data.knora.org/lists/773648520501"
                    ),
                    HierarchicalListV1(
                        position = 2,
                        level = 1,
                        children = Nil,
                        label = Some("ENGADIN"),
                        name = Some("3"),
                        id = "http://data.knora.org/lists/106a9b8b0501"
                    ),
                    HierarchicalListV1(
                        position = 3,
                        level = 1,
                        children = Nil,
                        label = Some("ST. MORITZ"),
                        name = Some("4"),
                        id = "http://data.knora.org/lists/a99deec40501"
                    ),
                    HierarchicalListV1(
                        position = 4,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Vector(
                                    HierarchicalListV1(
                                        position = 0,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen A"),
                                        name = Some("1"),
                                        id = "http://data.knora.org/lists/1744e17fb6"
                                    ),
                                    HierarchicalListV1(
                                        position = 1,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen B"),
                                        name = Some("2"),
                                        id = "http://data.knora.org/lists/b07734b9b6"
                                    ),
                                    HierarchicalListV1(
                                        position = 2,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen C"),
                                        name = Some("3"),
                                        id = "http://data.knora.org/lists/49ab87f2b6"
                                    ),
                                    HierarchicalListV1(
                                        position = 3,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen D"),
                                        name = Some("4"),
                                        id = "http://data.knora.org/lists/e2deda2bb7"
                                    ),
                                    HierarchicalListV1(
                                        position = 4,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen E"),
                                        name = Some("5"),
                                        id = "http://data.knora.org/lists/7b122e65b7"
                                    ),
                                    HierarchicalListV1(
                                        position = 5,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen F"),
                                        name = Some("6"),
                                        id = "http://data.knora.org/lists/1446819eb7"
                                    ),
                                    HierarchicalListV1(
                                        position = 6,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen G"),
                                        name = Some("7"),
                                        id = "http://data.knora.org/lists/ad79d4d7b7"
                                    ),
                                    HierarchicalListV1(
                                        position = 7,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen H"),
                                        name = Some("8"),
                                        id = "http://data.knora.org/lists/46ad2711b8"
                                    ),
                                    HierarchicalListV1(
                                        position = 8,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen I"),
                                        name = Some("9"),
                                        id = "http://data.knora.org/lists/dfe07a4ab8"
                                    ),
                                    HierarchicalListV1(
                                        position = 9,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen J"),
                                        name = Some("10"),
                                        id = "http://data.knora.org/lists/7814ce83b8"
                                    ),
                                    HierarchicalListV1(
                                        position = 10,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen K"),
                                        name = Some("11"),
                                        id = "http://data.knora.org/lists/114821bdb8"
                                    ),
                                    HierarchicalListV1(
                                        position = 11,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen L"),
                                        name = Some("12"),
                                        id = "http://data.knora.org/lists/aa7b74f6b8"
                                    ),
                                    HierarchicalListV1(
                                        position = 12,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen M"),
                                        name = Some("13"),
                                        id = "http://data.knora.org/lists/43afc72fb9"
                                    ),
                                    HierarchicalListV1(
                                        position = 13,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen N"),
                                        name = Some("14"),
                                        id = "http://data.knora.org/lists/dce21a69b9"
                                    ),
                                    HierarchicalListV1(
                                        position = 14,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen O"),
                                        name = Some("15"),
                                        id = "http://data.knora.org/lists/75166ea2b9"
                                    ),
                                    HierarchicalListV1(
                                        position = 15,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen P"),
                                        name = Some("16"),
                                        id = "http://data.knora.org/lists/0e4ac1dbb9"
                                    ),
                                    HierarchicalListV1(
                                        position = 16,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen Q"),
                                        name = Some("17"),
                                        id = "http://data.knora.org/lists/a77d1415ba"
                                    ),
                                    HierarchicalListV1(
                                        position = 17,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen R"),
                                        name = Some("18"),
                                        id = "http://data.knora.org/lists/40b1674eba"
                                    ),
                                    HierarchicalListV1(
                                        position = 18,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen S"),
                                        name = Some("19"),
                                        id = "http://data.knora.org/lists/d9e4ba87ba"
                                    ),
                                    HierarchicalListV1(
                                        position = 19,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen T"),
                                        name = Some("20"),
                                        id = "http://data.knora.org/lists/72180ec1ba"
                                    ),
                                    HierarchicalListV1(
                                        position = 20,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen U"),
                                        name = Some("21"),
                                        id = "http://data.knora.org/lists/0b4c61faba"
                                    ),
                                    HierarchicalListV1(
                                        position = 21,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen V"),
                                        name = Some("22"),
                                        id = "http://data.knora.org/lists/a47fb433bb"
                                    ),
                                    HierarchicalListV1(
                                        position = 22,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen W"),
                                        name = Some("23"),
                                        id = "http://data.knora.org/lists/3db3076dbb"
                                    ),
                                    HierarchicalListV1(
                                        position = 23,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen X"),
                                        name = Some("24"),
                                        id = "http://data.knora.org/lists/d6e65aa6bb"
                                    ),
                                    HierarchicalListV1(
                                        position = 24,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen Y"),
                                        name = Some("25"),
                                        id = "http://data.knora.org/lists/6f1aaedfbb"
                                    ),
                                    HierarchicalListV1(
                                        position = 25,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen Z"),
                                        name = Some("26"),
                                        id = "http://data.knora.org/lists/084e0119bc"
                                    )
                                ),
                                label = Some("Personen A-Z"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/7e108e46b6"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Personen unbekannt"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/a1815452bc"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Gruppen Einheimische"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/3ab5a78bbc"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Kinder Winter"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/d3e8fac4bc"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Kinder Sommer"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/6c1c4efebc"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("Sonnenbadende"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/0550a137bd"
                            ),
                            HierarchicalListV1(
                                position = 6,
                                level = 2,
                                children = Nil,
                                label = Some("Zuschauer"),
                                name = Some("7"),
                                id = "http://data.knora.org/lists/9e83f470bd"
                            )
                        ),
                        label = Some("BIOGRAPHIEN"),
                        name = Some("5"),
                        id = "http://data.knora.org/lists/e5dc3a0db6"
                    ),
                    HierarchicalListV1(
                        position = 5,
                        level = 1,
                        children = Nil,
                        label = Some("WAPPEN UND FAHNEN"),
                        name = Some("7"),
                        id = "http://data.knora.org/lists/37b747aabd"
                    ),
                    HierarchicalListV1(
                        position = 6,
                        level = 1,
                        children = Nil,
                        label = Some("KRIEGE UND MILIT\u00C4R"),
                        name = Some("9"),
                        id = "http://data.knora.org/lists/d0ea9ae3bd"
                    )
                ),
                label = Some("GESCHICHTE"),
                name = Some("3GES"),
                id = "http://data.knora.org/lists/4ca9e7d3b5"
            ),
            HierarchicalListV1(
                position = 3,
                level = 0,
                children = Vector(
                    HierarchicalListV1(
                        position = 0,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Ausstellungen"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/9b85948fbe"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Gem\u00E4lde"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/34b9e7c8be"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Karrikaturen und Kritik"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/cdec3a02bf"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Segantini und Museum"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/66208e3bbf"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Sgrafitti"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/ff53e174bf"
                            )
                        ),
                        label = Some("MALEREI"),
                        name = Some("5"),
                        id = "http://data.knora.org/lists/02524156be"
                    ),
                    HierarchicalListV1(
                        position = 1,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Kurorchester"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/31bb87e7bf"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Musik"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/caeeda20c0"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Zirkus"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/63222e5ac0"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Theater"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/fc558193c0"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Tanz"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/9589d4ccc0"
                            )
                        ),
                        label = Some("MUSIK, THEATER UND RADIO"),
                        name = Some("6"),
                        id = "http://data.knora.org/lists/988734aebf"
                    ),
                    HierarchicalListV1(
                        position = 2,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Heidi Film"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/c7f07a3fc1"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Foto"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/6024ce78c1"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Film"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/f95721b2c1"
                            )
                        ),
                        label = Some("FILM UND FOTO"),
                        name = Some("7"),
                        id = "http://data.knora.org/lists/2ebd2706c1"
                    ),
                    HierarchicalListV1(
                        position = 3,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Modelle"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/2bbfc724c2"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Schneeskulpturen"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/c4f21a5ec2"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Plastiken"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/5d266e97c2"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Stiche"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/f659c1d0c2"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Bildhauerei"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/8f8d140ac3"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("Kunstgewerbe"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/28c16743c3"
                            )
                        ),
                        label = Some("BILDHAUEREI UND KUNSTGEWERBE"),
                        name = Some("8"),
                        id = "http://data.knora.org/lists/928b74ebc1"
                    ),
                    HierarchicalListV1(
                        position = 4,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Grafiken"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/5a280eb6c3"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Holzschnitte"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/f35b61efc3"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Plakate"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/8c8fb428c4"
                            )
                        ),
                        label = Some("ST. MORITZ GRAFIKEN UND PLAKATE"),
                        name = Some("9"),
                        id = "http://data.knora.org/lists/c1f4ba7cc3"
                    ),
                    HierarchicalListV1(
                        position = 5,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Architektur / Inneneinrichtungen"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/bef65a9bc4"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Pl\u00E4ne"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/572aaed4c4"
                            )
                        ),
                        label = Some("ARCHITEKTUR"),
                        name = Some("10"),
                        id = "http://data.knora.org/lists/25c30762c4"
                    )
                ),
                label = Some("KUNST"),
                name = Some("4KUN"),
                id = "http://data.knora.org/lists/691eee1cbe"
            ),
            HierarchicalListV1(
                position = 4,
                level = 0,
                children = Vector(
                    HierarchicalListV1(
                        position = 0,
                        level = 1,
                        children = Nil,
                        label = Some("MEDIZIN UND NATURHEILKUNDE"),
                        name = Some("1"),
                        id = "http://data.knora.org/lists/89915447c5"
                    ),
                    HierarchicalListV1(
                        position = 1,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Heilbad aussen"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/bbf8fab9c5"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Heilbad innen"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/542c4ef3c5"
                            )
                        ),
                        label = Some("HEILBAD UND QUELLEN"),
                        name = Some("3"),
                        id = "http://data.knora.org/lists/22c5a780c5"
                    ),
                    HierarchicalListV1(
                        position = 2,
                        level = 1,
                        children = Nil,
                        label = Some("SPITAL UND KLINIKEN / KINDERHEIME"),
                        name = Some("4"),
                        id = "http://data.knora.org/lists/ed5fa12cc6"
                    )
                ),
                label = Some("MEDIZIN"),
                name = Some("5MED"),
                id = "http://data.knora.org/lists/f05d010ec5"
            ),
            HierarchicalListV1(
                position = 5,
                level = 0,
                children = Vector(
                    HierarchicalListV1(
                        position = 0,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Fischen"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/b8fa9ad8c6"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Jagen"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/512eee11c7"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Tiere"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/ea61414bc7"
                            )
                        ),
                        label = Some("FAUNA"),
                        name = Some("2"),
                        id = "http://data.knora.org/lists/1fc7479fc6"
                    ),
                    HierarchicalListV1(
                        position = 1,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Blumen"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/1cc9e7bdc7"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("B\u00E4ume"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/b5fc3af7c7"
                            )
                        ),
                        label = Some("FLORA"),
                        name = Some("3"),
                        id = "http://data.knora.org/lists/83959484c7"
                    ),
                    HierarchicalListV1(
                        position = 2,
                        level = 1,
                        children = Nil,
                        label = Some("GEOLOGIE UND MINERALOGIE"),
                        name = Some("4"),
                        id = "http://data.knora.org/lists/4e308e30c8"
                    ),
                    HierarchicalListV1(
                        position = 3,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Gew\u00E4sser und \u00DCberschwemmungen"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/809734a3c8"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Gletscher"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/19cb87dcc8"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Lawinen"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/b2feda15c9"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Schnee, Raureif, Eisblumen"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/4b322e4fc9"
                            )
                        ),
                        label = Some("KLIMATOLOGIE UND METEOROLOGIE"),
                        name = Some("5"),
                        id = "http://data.knora.org/lists/e763e169c8"
                    ),
                    HierarchicalListV1(
                        position = 4,
                        level = 1,
                        children = Nil,
                        label = Some("UMWELT"),
                        name = Some("6"),
                        id = "http://data.knora.org/lists/e4658188c9"
                    )
                ),
                label = Some("NATURKUNDE"),
                name = Some("6NAT"),
                id = "http://data.knora.org/lists/8693f465c6"
            ),
            HierarchicalListV1(
                position = 6,
                level = 0,
                children = Vector(HierarchicalListV1(
                    position = 0,
                    level = 1,
                    children = Vector(HierarchicalListV1(
                        position = 0,
                        level = 2,
                        children = Nil,
                        label = Some("St. Moritz Kirchen"),
                        name = Some("1"),
                        id = "http://data.knora.org/lists/af007b34ca"
                    )),
                    label = Some("RELIGION UND KIRCHEN"),
                    name = Some("1"),
                    id = "http://data.knora.org/lists/16cd27fbc9"
                )),
                label = Some("RELIGION"),
                name = Some("7REL"),
                id = "http://data.knora.org/lists/7d99d4c1c9"
            ),
            HierarchicalListV1(
                position = 7,
                level = 0,
                children = Vector(
                    HierarchicalListV1(
                        position = 0,
                        level = 1,
                        children = Nil,
                        label = Some("VERFASSUNGEN UND GESETZE"),
                        name = Some("1"),
                        id = "http://data.knora.org/lists/e16721a7ca"
                    ),
                    HierarchicalListV1(
                        position = 1,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Wasserwirtschaft"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/13cfc719cb"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Feuer und Feuerwehr"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/ac021b53cb"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Polizei und Beh\u00F6rde"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/45366e8ccb"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Abfallbewirtschaftung"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/de69c1c5cb"
                            )
                        ),
                        label = Some("GEMEINDEWESEN"),
                        name = Some("2"),
                        id = "http://data.knora.org/lists/7a9b74e0ca"
                    ),
                    HierarchicalListV1(
                        position = 2,
                        level = 1,
                        children = Nil,
                        label = Some("SCHULWESEN"),
                        name = Some("3"),
                        id = "http://data.knora.org/lists/779d14ffcb"
                    ),
                    HierarchicalListV1(
                        position = 3,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("B\u00E4lle und Verkleidungen"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/a904bb71cc"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Chalandamarz"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/42380eabcc"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Engadiner Museum"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/db6b61e4cc"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Feste und Umz\u00FCge"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/749fb41dcd"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Schlitteda"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/0dd30757cd"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("Trachten"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/a6065b90cd"
                            )
                        ),
                        label = Some("VOLKSKUNDE"),
                        name = Some("4"),
                        id = "http://data.knora.org/lists/10d16738cc"
                    ),
                    HierarchicalListV1(
                        position = 4,
                        level = 1,
                        children = Nil,
                        label = Some("PARTEIEN UND GRUPPIERUNGEN"),
                        name = Some("6"),
                        id = "http://data.knora.org/lists/3f3aaec9cd"
                    ),
                    HierarchicalListV1(
                        position = 5,
                        level = 1,
                        children = Nil,
                        label = Some("SCHWESTERNST\u00C4TDE"),
                        name = Some("7"),
                        id = "http://data.knora.org/lists/d86d0103ce"
                    )
                ),
                label = Some("SOZIALES"),
                name = Some("8SOZ"),
                id = "http://data.knora.org/lists/4834ce6dca"
            ),
            HierarchicalListV1(
                position = 8,
                level = 0,
                children = Vector(
                    HierarchicalListV1(
                        position = 0,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Bridge"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/a308fbaece"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Boxen"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/3c3c4ee8ce"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Camping"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/d56fa121cf"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Fechten"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/6ea3f45acf"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Fitness"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/07d74794cf"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("H\u00F6hentraining"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/a00a9bcdcf"
                            ),
                            HierarchicalListV1(
                                position = 6,
                                level = 2,
                                children = Nil,
                                label = Some("Krafttraining"),
                                name = Some("7"),
                                id = "http://data.knora.org/lists/393eee06d0"
                            ),
                            HierarchicalListV1(
                                position = 7,
                                level = 2,
                                children = Nil,
                                label = Some("Leichtathletik"),
                                name = Some("8"),
                                id = "http://data.knora.org/lists/d2714140d0"
                            ),
                            HierarchicalListV1(
                                position = 8,
                                level = 2,
                                children = Nil,
                                label = Some("Pokale, Preise, Medallien"),
                                name = Some("9"),
                                id = "http://data.knora.org/lists/6ba59479d0"
                            ),
                            HierarchicalListV1(
                                position = 9,
                                level = 2,
                                children = Nil,
                                label = Some("Schiessen"),
                                name = Some("10"),
                                id = "http://data.knora.org/lists/04d9e7b2d0"
                            ),
                            HierarchicalListV1(
                                position = 10,
                                level = 2,
                                children = Nil,
                                label = Some("Turnen"),
                                name = Some("11"),
                                id = "http://data.knora.org/lists/9d0c3becd0"
                            ),
                            HierarchicalListV1(
                                position = 11,
                                level = 2,
                                children = Nil,
                                label = Some("Zeitmessung"),
                                name = Some("12"),
                                id = "http://data.knora.org/lists/36408e25d1"
                            ),
                            HierarchicalListV1(
                                position = 12,
                                level = 2,
                                children = Nil,
                                label = Some("Hornussen"),
                                name = Some("13"),
                                id = "http://data.knora.org/lists/cf73e15ed1"
                            ),
                            HierarchicalListV1(
                                position = 13,
                                level = 2,
                                children = Nil,
                                label = Some("Schwingen"),
                                name = Some("14"),
                                id = "http://data.knora.org/lists/68a73498d1"
                            )
                        ),
                        label = Some("SPORT"),
                        name = Some("0"),
                        id = "http://data.knora.org/lists/0ad5a775ce"
                    ),
                    HierarchicalListV1(
                        position = 1,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Cricket"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/9a0edb0ad2"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Schlitteln"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/33422e44d2"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Schneeschuhlaufen"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/cc75817dd2"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Tailing"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/65a9d4b6d2"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Wind-, Schlittenhundrennen"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/fedc27f0d2"
                            )
                        ),
                        label = Some("WINTERSPORT"),
                        name = Some("1"),
                        id = "http://data.knora.org/lists/01db87d1d1"
                    ),
                    HierarchicalListV1(
                        position = 2,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Verschiedenes"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/3044ce62d3"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Skiakrobatik"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/c977219cd3"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Ski Corvatsch"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/62ab74d5d3"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Skifahren"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/fbdec70ed4"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Ski Kilometer-Lanc\u00E9"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/94121b48d4"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("Ski SOS"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/2d466e81d4"
                            ),
                            HierarchicalListV1(
                                position = 6,
                                level = 2,
                                children = Nil,
                                label = Some("Skitouren"),
                                name = Some("7"),
                                id = "http://data.knora.org/lists/c679c1bad4"
                            )
                        ),
                        label = Some("SKI"),
                        name = Some("2"),
                        id = "http://data.knora.org/lists/97107b29d3"
                    ),
                    HierarchicalListV1(
                        position = 3,
                        level = 1,
                        children = Nil,
                        label = Some("SKISCHULE"),
                        name = Some("2-2"),
                        id = "http://data.knora.org/lists/5fad14f4d4"
                    ),
                    HierarchicalListV1(
                        position = 4,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Skirennen"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/9114bb66d5"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Ski Rennpisten"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/2a480ea0d5"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Personen"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/c37b61d9d5"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Guardia Grischa"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/5cafb412d6"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Ski Vorweltmeisterschaft 1973"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/f5e2074cd6"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("Ski Weltmeisterschaft 1974"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/8e165b85d6"
                            ),
                            HierarchicalListV1(
                                position = 6,
                                level = 2,
                                children = Nil,
                                label = Some("Ski Weltmeisterschaft 2003"),
                                name = Some("7"),
                                id = "http://data.knora.org/lists/274aaebed6"
                            ),
                            HierarchicalListV1(
                                position = 7,
                                level = 2,
                                children = Nil,
                                label = Some("Skispringen"),
                                name = Some("8"),
                                id = "http://data.knora.org/lists/c07d01f8d6"
                            )
                        ),
                        label = Some("SKIRENNEN UND SKISPRINGEN"),
                        name = Some("2-3"),
                        id = "http://data.knora.org/lists/f8e0672dd5"
                    ),
                    HierarchicalListV1(
                        position = 5,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Skilanglauf"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/f2e4a76ad7"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Engadin Skimarathon"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/8b18fba3d7"
                            )
                        ),
                        label = Some("SKILANGLAUF UND ENGADIN SKIMARATHON"),
                        name = Some("2-4"),
                        id = "http://data.knora.org/lists/59b15431d7"
                    ),
                    HierarchicalListV1(
                        position = 6,
                        level = 1,
                        children = Nil,
                        label = Some("SNOWBOARD UND SNOWBOARDSCHULE"),
                        name = Some("2-5"),
                        id = "http://data.knora.org/lists/244c4eddd7"
                    ),
                    HierarchicalListV1(
                        position = 7,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Olympiade 1928"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/56b3f44fd8"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Olympiade 1948"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/efe64789d8"
                            )
                        ),
                        label = Some("OLYMPIADEN"),
                        name = Some("3"),
                        id = "http://data.knora.org/lists/bd7fa116d8"
                    ),
                    HierarchicalListV1(
                        position = 8,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Eishockey und Bandy"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/214eeefbd8"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Vector(
                                    HierarchicalListV1(
                                        position = 0,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Gefrorene Seen"),
                                        name = Some("1"),
                                        id = "http://data.knora.org/lists/53b5946ed9"
                                    ),
                                    HierarchicalListV1(
                                        position = 1,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Gymkhana"),
                                        name = Some("2"),
                                        id = "http://data.knora.org/lists/ece8e7a7d9"
                                    ),
                                    HierarchicalListV1(
                                        position = 2,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Eisrevue"),
                                        name = Some("3"),
                                        id = "http://data.knora.org/lists/851c3be1d9"
                                    ),
                                    HierarchicalListV1(
                                        position = 3,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Paarlauf"),
                                        name = Some("4"),
                                        id = "http://data.knora.org/lists/1e508e1ada"
                                    ),
                                    HierarchicalListV1(
                                        position = 4,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Schnellauf"),
                                        name = Some("5"),
                                        id = "http://data.knora.org/lists/b783e153da"
                                    ),
                                    HierarchicalListV1(
                                        position = 5,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Kellner auf Eis"),
                                        name = Some("6"),
                                        id = "http://data.knora.org/lists/50b7348dda"
                                    ),
                                    HierarchicalListV1(
                                        position = 6,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen"),
                                        name = Some("7"),
                                        id = "http://data.knora.org/lists/e9ea87c6da"
                                    )
                                ),
                                label = Some("Eislaufen"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/ba814135d9"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Eissegeln, -Surfen"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/821edbffda"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Eisstadion"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/1b522e39db"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Vector(HierarchicalListV1(
                                    position = 0,
                                    level = 3,
                                    children = Nil,
                                    label = Some("Personen"),
                                    name = Some("1"),
                                    id = "http://data.knora.org/lists/4db9d4abdb"
                                )),
                                label = Some("Curling"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/b4858172db"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("Eisstockschiessen"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/e6ec27e5db"
                            ),
                            HierarchicalListV1(
                                position = 6,
                                level = 2,
                                children = Nil,
                                label = Some("Kunsteisbahn Ludains"),
                                name = Some("7"),
                                id = "http://data.knora.org/lists/7f207b1edc"
                            )
                        ),
                        label = Some("EISSPORT"),
                        name = Some("4"),
                        id = "http://data.knora.org/lists/881a9bc2d8"
                    ),
                    HierarchicalListV1(
                        position = 9,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Vector(
                                    HierarchicalListV1(
                                        position = 0,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen"),
                                        name = Some("1"),
                                        id = "http://data.knora.org/lists/4abb74cadc"
                                    ),
                                    HierarchicalListV1(
                                        position = 1,
                                        level = 3,
                                        children = Nil,
                                        label = Some("St\u00FCrze"),
                                        name = Some("2"),
                                        id = "http://data.knora.org/lists/e3eec703dd"
                                    ),
                                    HierarchicalListV1(
                                        position = 2,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Bau"),
                                        name = Some("3"),
                                        id = "http://data.knora.org/lists/7c221b3ddd"
                                    )
                                ),
                                label = Some("Bob Run"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/b1872191dc"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Vector(
                                    HierarchicalListV1(
                                        position = 0,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen"),
                                        name = Some("1"),
                                        id = "http://data.knora.org/lists/ae89c1afdd"
                                    ),
                                    HierarchicalListV1(
                                        position = 1,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Bau"),
                                        name = Some("2"),
                                        id = "http://data.knora.org/lists/47bd14e9dd"
                                    )
                                ),
                                label = Some("Cresta Run"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/15566e76dd"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Rodeln"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/42d141fe0501"
                            )
                        ),
                        label = Some("CRESTA RUN UND BOB"),
                        name = Some("5"),
                        id = "http://data.knora.org/lists/1854ce57dc"
                    ),
                    HierarchicalListV1(
                        position = 10,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Concours Hippique"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/7924bb5bde"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Pferderennen"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/12580e95de"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Polo"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/ab8b61cede"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Reiten"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/44bfb407df"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Reithalle"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/ddf20741df"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("Skikj\u00F6ring"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/76265b7adf"
                            ),
                            HierarchicalListV1(
                                position = 6,
                                level = 2,
                                children = Nil,
                                label = Some("Fahrturnier"),
                                name = Some("7"),
                                id = "http://data.knora.org/lists/0f5aaeb3df"
                            ),
                            HierarchicalListV1(
                                position = 7,
                                level = 2,
                                children = Nil,
                                label = Some("Zuschauer"),
                                name = Some("8"),
                                id = "http://data.knora.org/lists/a88d01eddf"
                            )
                        ),
                        label = Some("PFERDESPORT"),
                        name = Some("6"),
                        id = "http://data.knora.org/lists/e0f06722de"
                    ),
                    HierarchicalListV1(
                        position = 11,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Billiard"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/daf4a75fe0"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Fussball"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/7328fb98e0"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Kegeln"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/0c5c4ed2e0"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Vector(
                                    HierarchicalListV1(
                                        position = 0,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Minigolf"),
                                        name = Some("1"),
                                        id = "http://data.knora.org/lists/3ec3f444e1"
                                    ),
                                    HierarchicalListV1(
                                        position = 1,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Sommergolf"),
                                        name = Some("2"),
                                        id = "http://data.knora.org/lists/d7f6477ee1"
                                    ),
                                    HierarchicalListV1(
                                        position = 2,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Wintergolf"),
                                        name = Some("3"),
                                        id = "http://data.knora.org/lists/702a9bb7e1"
                                    )
                                ),
                                label = Some("Golf"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/a58fa10be1"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Tennis"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/095eeef0e1"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("Volleyball"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/a291412ae2"
                            )
                        ),
                        label = Some("BALLSPORT"),
                        name = Some("7"),
                        id = "http://data.knora.org/lists/41c15426e0"
                    ),
                    HierarchicalListV1(
                        position = 12,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Alpinismus"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/d4f8e79ce2"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Bergh\u00FCtten und Restaurants"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/6d2c3bd6e2"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Trecking mit Tieren"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/06608e0fe3"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Wandern"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/9f93e148e3"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Spazieren"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/38c73482e3"
                            )
                        ),
                        label = Some("ALPINISMUS"),
                        name = Some("8"),
                        id = "http://data.knora.org/lists/3bc59463e2"
                    ),
                    HierarchicalListV1(
                        position = 13,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Ballon"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/6a2edbf4e3"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Delta"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/03622e2ee4"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Flugzeuge"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/9c958167e4"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Helikopter"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/35c9d4a0e4"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Segelflieger"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/cefc27dae4"
                            )
                        ),
                        label = Some("FLIEGEN"),
                        name = Some("9"),
                        id = "http://data.knora.org/lists/d1fa87bbe3"
                    ),
                    HierarchicalListV1(
                        position = 14,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Vector(
                                    HierarchicalListV1(
                                        position = 0,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Malojarennen"),
                                        name = Some("1"),
                                        id = "http://data.knora.org/lists/99972186e5"
                                    ),
                                    HierarchicalListV1(
                                        position = 1,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Berninarennen"),
                                        name = Some("2"),
                                        id = "http://data.knora.org/lists/32cb74bfe5"
                                    ),
                                    HierarchicalListV1(
                                        position = 2,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Shellstrasse"),
                                        name = Some("3"),
                                        id = "http://data.knora.org/lists/cbfec7f8e5"
                                    ),
                                    HierarchicalListV1(
                                        position = 3,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Personen"),
                                        name = Some("4"),
                                        id = "http://data.knora.org/lists/64321b32e6"
                                    ),
                                    HierarchicalListV1(
                                        position = 4,
                                        level = 3,
                                        children = Nil,
                                        label = Some("Verschiedenes"),
                                        name = Some("5"),
                                        id = "http://data.knora.org/lists/fd656e6be6"
                                    )
                                ),
                                label = Some("Autorennen"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/0064ce4ce5"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Geschicklichkeitsfahren"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/9699c1a4e6"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Sch\u00F6nheitskonkurrenz"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/2fcd14dee6"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Inline Skating"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/c8006817e7"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Montainbiking"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/6134bb50e7"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("Radfahren"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/fa670e8ae7"
                            ),
                            HierarchicalListV1(
                                position = 6,
                                level = 2,
                                children = Nil,
                                label = Some("Motorradfahren"),
                                name = Some("7"),
                                id = "http://data.knora.org/lists/939b61c3e7"
                            )
                        ),
                        label = Some("RADSPORT"),
                        name = Some("10"),
                        id = "http://data.knora.org/lists/67307b13e5"
                    ),
                    HierarchicalListV1(
                        position = 15,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Schwimmen Hallenb\u00E4der"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/c5020836e8"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Schwimmen Seen"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/5e365b6fe8"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Rudern"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/f769aea8e8"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Segeln"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/909d01e2e8"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Windsurfen"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/29d1541be9"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("Tauchen"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/c204a854e9"
                            ),
                            HierarchicalListV1(
                                position = 6,
                                level = 2,
                                children = Nil,
                                label = Some("Rafting"),
                                name = Some("7"),
                                id = "http://data.knora.org/lists/5b38fb8de9"
                            ),
                            HierarchicalListV1(
                                position = 7,
                                level = 2,
                                children = Nil,
                                label = Some("Kitesurfen"),
                                name = Some("8"),
                                id = "http://data.knora.org/lists/f46b4ec7e9"
                            )
                        ),
                        label = Some("WASSERSPORT"),
                        name = Some("11"),
                        id = "http://data.knora.org/lists/2ccfb4fce7"
                    )
                ),
                label = Some("SPORT"),
                name = Some("9SPO"),
                id = "http://data.knora.org/lists/71a1543cce"
            ),
            HierarchicalListV1(
                position = 9,
                level = 0,
                children = Vector(
                    HierarchicalListV1(
                        position = 0,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Autos, Busse und Postautos"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/bf064873ea"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Boote"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/583a9bacea"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Flugplatz Samedan"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/f16deee5ea"
                            ),
                            HierarchicalListV1(
                                position = 3,
                                level = 2,
                                children = Nil,
                                label = Some("Kommunikation"),
                                name = Some("4"),
                                id = "http://data.knora.org/lists/8aa1411feb"
                            ),
                            HierarchicalListV1(
                                position = 4,
                                level = 2,
                                children = Nil,
                                label = Some("Kutschen und Pferdetransporte"),
                                name = Some("5"),
                                id = "http://data.knora.org/lists/23d59458eb"
                            ),
                            HierarchicalListV1(
                                position = 5,
                                level = 2,
                                children = Nil,
                                label = Some("Luftseilbahnen und Stationen"),
                                name = Some("6"),
                                id = "http://data.knora.org/lists/bc08e891eb"
                            ),
                            HierarchicalListV1(
                                position = 6,
                                level = 2,
                                children = Nil,
                                label = Some("Schneer\u00E4umungs- und Pistenfahrzeuge"),
                                name = Some("7"),
                                id = "http://data.knora.org/lists/553c3bcbeb"
                            ),
                            HierarchicalListV1(
                                position = 7,
                                level = 2,
                                children = Nil,
                                label = Some("Schneekanonen"),
                                name = Some("8"),
                                id = "http://data.knora.org/lists/ee6f8e04ec"
                            ),
                            HierarchicalListV1(
                                position = 8,
                                level = 2,
                                children = Nil,
                                label = Some("Skilifte"),
                                name = Some("9"),
                                id = "http://data.knora.org/lists/87a3e13dec"
                            ),
                            HierarchicalListV1(
                                position = 9,
                                level = 2,
                                children = Nil,
                                label = Some("Standseilbahnen und Stationen"),
                                name = Some("10"),
                                id = "http://data.knora.org/lists/20d73477ec"
                            ),
                            HierarchicalListV1(
                                position = 10,
                                level = 2,
                                children = Nil,
                                label = Some("Strassen und P\u00E4sse"),
                                name = Some("11"),
                                id = "http://data.knora.org/lists/b90a88b0ec"
                            ),
                            HierarchicalListV1(
                                position = 11,
                                level = 2,
                                children = Nil,
                                label = Some("Tram"),
                                name = Some("12"),
                                id = "http://data.knora.org/lists/523edbe9ec"
                            ),
                            HierarchicalListV1(
                                position = 12,
                                level = 2,
                                children = Nil,
                                label = Some("Wegweiser"),
                                name = Some("13"),
                                id = "http://data.knora.org/lists/eb712e23ed"
                            )
                        ),
                        label = Some("VERKEHR"),
                        name = Some("1"),
                        id = "http://data.knora.org/lists/26d3f439ea"
                    ),
                    HierarchicalListV1(
                        position = 1,
                        level = 1,
                        children = Vector(HierarchicalListV1(
                            position = 0,
                            level = 2,
                            children = Nil,
                            label = Some("Eisenbahnen und Bahnh\u00F6fe"),
                            name = Some("1"),
                            id = "http://data.knora.org/lists/1dd9d495ed"
                        )),
                        label = Some("EISENBAHNEN"),
                        name = Some("1-1"),
                        id = "http://data.knora.org/lists/84a5815ced"
                    ),
                    HierarchicalListV1(
                        position = 2,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Casino"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/4f407b08ee"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("G\u00E4ste"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/e873ce41ee"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Mode"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/81a7217bee"
                            )
                        ),
                        label = Some("FREMDENVERKEHR"),
                        name = Some("2"),
                        id = "http://data.knora.org/lists/b60c28cfed"
                    ),
                    HierarchicalListV1(
                        position = 3,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Vector(
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/97744976b801",
                                        Some("hotel_a"),
                                        Some("Hotel A"),
                                        Nil,
                                        3,
                                        0),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/30a89cafb801",
                                        Some("hotel_b"),
                                        Some("Hotel B"),
                                        Nil,
                                        3,
                                        1),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/c9dbefe8b801",
                                        Some("hotel_c"),
                                        Some("Hotel C"),
                                        Nil,
                                        3,
                                        2),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/620f4322b901",
                                        Some("hotel_d"),
                                        Some("Hotel D"),
                                        Nil,
                                        3,
                                        3),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/fb42965bb901",
                                        Some("hotel_e"),
                                        Some("Hotel E"),
                                        Nil,
                                        3,
                                        4),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/9476e994b901",
                                        Some("hotel_f"),
                                        Some("Hotel F"),
                                        Nil,
                                        3,
                                        5),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/2daa3cceb901",
                                        Some("hotel_g"),
                                        Some("Hotel G"),
                                        Nil,
                                        3,
                                        6),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/c6dd8f07ba01",
                                        Some("hotel_h"),
                                        Some("Hotel H"),
                                        Nil,
                                        3,
                                        7),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/5f11e340ba01",
                                        Some("hotel_i"),
                                        Some("Hotel I"),
                                        Nil,
                                        3,
                                        8),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/f844367aba01",
                                        Some("hotel_j"),
                                        Some("Hotel J"),
                                        Nil,
                                        3,
                                        9),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/917889b3ba01",
                                        Some("hotel_k"),
                                        Some("Hotel K"),
                                        Nil,
                                        3,
                                        10),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/2aacdcecba01",
                                        Some("hotel_l"),
                                        Some("Hotel L"),
                                        Nil,
                                        3,
                                        11),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/c3df2f26bb01",
                                        Some("hotel_m"),
                                        Some("Hotel M"),
                                        Nil,
                                        3,
                                        12),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/5c13835fbb01",
                                        Some("hotel_n"),
                                        Some("Hotel N"),
                                        Nil,
                                        3,
                                        13),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/f546d698bb01",
                                        Some("hotel_o"),
                                        Some("Hotel O"),
                                        Nil,
                                        3,
                                        14),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/8e7a29d2bb01",
                                        Some("hotel_p"),
                                        Some("Hotel P"),
                                        Nil,
                                        3,
                                        15),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/27ae7c0bbc01",
                                        Some("hotel_q"),
                                        Some("Hotel Q"),
                                        Nil,
                                        3,
                                        16),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/c0e1cf44bc01",
                                        Some("hotel_r"),
                                        Some("Hotel R"),
                                        Nil,
                                        3,
                                        17),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/5915237ebc01",
                                        Some("hotel_s"),
                                        Some("Hotel S"),
                                        Nil,
                                        3,
                                        18),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/f24876b7bc01",
                                        Some("hotel_t"),
                                        Some("Hotel T"),
                                        Nil,
                                        3,
                                        19),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/8b7cc9f0bc01",
                                        Some("hotel_u"),
                                        Some("Hotel U"),
                                        Nil,
                                        3,
                                        20),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/24b01c2abd01",
                                        Some("hotel_v"),
                                        Some("Hotel V"),
                                        Nil,
                                        3,
                                        21),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/9f29173c3b02",
                                        Some("hotel_w"),
                                        Some("Hotel W"),
                                        Nil,
                                        3,
                                        22),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/bde36f63bd01",
                                        Some("hotel_x"),
                                        Some("Hotel X"),
                                        Nil,
                                        3,
                                        23),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/5617c39cbd01",
                                        Some("hotel_y"),
                                        Some("Hotel Y"),
                                        Nil,
                                        3,
                                        24),
                                    HierarchicalListV1(
                                        "http://data.knora.org/lists/ef4a16d6bd01",
                                        Some("hotel_z"),
                                        Some("Hotel Z"),
                                        Nil,
                                        3,
                                        25)),
                                label = Some("Hotels und Restaurants A-Z"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/b30ec8edee"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Essen"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/4c421b27ef"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Men\u00FCkarten"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/e5756e60ef"
                            )
                        ),
                        label = Some("HOTELLERIE"),
                        name = Some("3"),
                        id = "http://data.knora.org/lists/1adb74b4ee"
                    ),
                    HierarchicalListV1(
                        position = 4,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Personal und B\u00FCro"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/17dd14d3ef"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Anl\u00E4sse und Reisen"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/b010680cf0"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Markenzeichen St. Moritz"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/4944bb45f0"
                            )
                        ),
                        label = Some("KURVEREIN"),
                        name = Some("4"),
                        id = "http://data.knora.org/lists/7ea9c199ef"
                    ),
                    HierarchicalListV1(
                        position = 5,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Arbeitswelt"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/7bab61b8f0"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Reklame"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/14dfb4f1f0"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Bauwesen"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/ad12082bf1"
                            )
                        ),
                        label = Some("GEWERBE"),
                        name = Some("6"),
                        id = "http://data.knora.org/lists/e2770e7ff0"
                    ),
                    HierarchicalListV1(
                        position = 6,
                        level = 1,
                        children = Vector(
                            HierarchicalListV1(
                                position = 0,
                                level = 2,
                                children = Nil,
                                label = Some("Elektrizit\u00E4t"),
                                name = Some("1"),
                                id = "http://data.knora.org/lists/df79ae9df1"
                            ),
                            HierarchicalListV1(
                                position = 1,
                                level = 2,
                                children = Nil,
                                label = Some("Wasserkraft"),
                                name = Some("2"),
                                id = "http://data.knora.org/lists/78ad01d7f1"
                            ),
                            HierarchicalListV1(
                                position = 2,
                                level = 2,
                                children = Nil,
                                label = Some("Solarenergie"),
                                name = Some("3"),
                                id = "http://data.knora.org/lists/11e15410f2"
                            )
                        ),
                        label = Some("ENERGIEWIRTSCHAFT"),
                        name = Some("7"),
                        id = "http://data.knora.org/lists/46465b64f1"
                    ),
                    HierarchicalListV1(
                        position = 7,
                        level = 1,
                        children = Nil,
                        label = Some("AGRARWIRTSCHAFT"),
                        name = Some("8"),
                        id = "http://data.knora.org/lists/aa14a849f2"
                    ),
                    HierarchicalListV1(
                        position = 8,
                        level = 1,
                        children = Nil,
                        label = Some("WALDWIRTSCHAFT"),
                        name = Some("9"),
                        id = "http://data.knora.org/lists/4348fb82f2"
                    )
                ),
                label = Some("WIRTSCHAFT"),
                name = Some("10WIR"),
                id = "http://data.knora.org/lists/8d9fa100ea"
            )
        )
    )

    private val imageCategory = SelectionGetResponseV1(
        selection = Vector(
            HierarchicalListV1(
                position = 0,
                level = 0,
                children = Nil,
                label = Some("Laserkopie"),
                name = Some("laserkopie"),
                id = "http://data.knora.org/lists/88d6cc5f05"
            ),
            HierarchicalListV1(
                position = 1,
                level = 0,
                children = Nil,
                label = Some("Fotografie s/w"),
                name = Some("foto_sw"),
                id = "http://data.knora.org/lists/230a209905"
            ),
            HierarchicalListV1(
                position = 2,
                level = 0,
                children = Nil,
                label = Some("Fotografie chamois"),
                name = Some("foto_chamois"),
                id = "http://data.knora.org/lists/be3d73d205"
            ),
            HierarchicalListV1(
                position = 3,
                level = 0,
                children = Nil,
                label = Some("Fotografie farb"),
                name = Some("foto_farb"),
                id = "http://data.knora.org/lists/5971c60b06"
            ),
            HierarchicalListV1(
                position = 4,
                level = 0,
                children = Nil,
                label = Some("Fotografie kol"),
                name = Some("foto_kol"),
                id = "http://data.knora.org/lists/f4a4194506"
            ),
            HierarchicalListV1(
                position = 5,
                level = 0,
                children = Nil,
                label = Some("Fotografie digital"),
                name = Some("foto_digital"),
                id = "http://data.knora.org/lists/8fd86c7e06"
            ),
            HierarchicalListV1(
                position = 6,
                level = 0,
                children = Nil,
                label = Some("Postkarte s/w"),
                name = Some("postkarte_sw"),
                id = "http://data.knora.org/lists/2a0cc0b706"
            ),
            HierarchicalListV1(
                position = 7,
                level = 0,
                children = Nil,
                label = Some("Postkarte farb"),
                name = Some("postkarte_farb"),
                id = "http://data.knora.org/lists/c53f13f106"
            ),
            HierarchicalListV1(
                position = 8,
                level = 0,
                children = Nil,
                label = Some("Postkarte kol"),
                name = Some("postkarte_kol"),
                id = "http://data.knora.org/lists/6073662a07"
            ),
            HierarchicalListV1(
                position = 9,
                level = 0,
                children = Nil,
                label = Some("Dia farbig"),
                name = Some("dia_farb"),
                id = "http://data.knora.org/lists/fba6b96307"
            ),
            HierarchicalListV1(
                position = 10,
                level = 0,
                children = Nil,
                label = Some("Dia s/w"),
                name = Some("dia_sw"),
                id = "http://data.knora.org/lists/96da0c9d07"
            ),
            HierarchicalListV1(
                position = 11,
                level = 0,
                children = Nil,
                label = Some("Negativ s/w"),
                name = Some("negativ_sw"),
                id = "http://data.knora.org/lists/310e60d607"
            ),
            HierarchicalListV1(
                position = 12,
                level = 0,
                children = Nil,
                label = Some("Negativ farb."),
                name = Some("negativ_farb"),
                id = "http://data.knora.org/lists/cc41b30f08"
            ),
            HierarchicalListV1(
                position = 13,
                level = 0,
                children = Nil,
                label = Some("Gem\u00E4lde"),
                name = Some("gemaelde"),
                id = "http://data.knora.org/lists/6775064908"
            ),
            HierarchicalListV1(
                position = 14,
                level = 0,
                children = Nil,
                label = Some("Stich"),
                name = Some("stich"),
                id = "http://data.knora.org/lists/02a9598208"
            ),
            HierarchicalListV1(
                position = 15,
                level = 0,
                children = Nil,
                label = Some("Zeichnung"),
                name = Some("zeichnung"),
                id = "http://data.knora.org/lists/9ddcacbb08"
            ),
            HierarchicalListV1(
                position = 16,
                level = 0,
                children = Nil,
                label = Some("Druck"),
                name = Some("druck"),
                id = "http://data.knora.org/lists/381000f508"
            ),
            HierarchicalListV1(
                position = 17,
                level = 0,
                children = Nil,
                label = Some("Glasplatte (positiv)"),
                name = Some("glasplatte_p"),
                id = "http://data.knora.org/lists/d343532e09"
            ),
            HierarchicalListV1(
                position = 18,
                level = 0,
                children = Nil,
                label = Some("Glasplatte (negativ)"),
                name = Some("glasplatte_n"),
                id = "http://data.knora.org/lists/9f85063e11"
            ),
            HierarchicalListV1(
                position = 19,
                level = 0,
                children = Nil,
                label = Some("Plakate"),
                name = Some("plakat"),
                id = "http://data.knora.org/lists/6e77a66709"
            ),
            HierarchicalListV1(
                position = 20,
                level = 0,
                children = Nil,
                label = Some("Unbekannt"),
                name = Some("unknown"),
                id = "http://data.knora.org/lists/3ab9597711"
            )
        )
    )

    private val season = HListGetResponseV1(
        hlist = Vector(
            HierarchicalListV1(
                position = 0,
                level = 0,
                children = Nil,
                label = Some("Winter"),
                name = Some("winter"),
                id = "http://data.knora.org/lists/eda2792605"
            ),
            HierarchicalListV1(
                position = 1,
                level = 0,
                children = Nil,
                label = Some("Sommer"),
                name = Some("sommer"),
                id = "http://data.knora.org/lists/526f26ed04"
            )
        )
    )

    private val nodePath = NodePathGetResponseV1(
        nodelist = Vector(
            NodePathElementV1(
                label = Some("KUNST"),
                name = Some("4KUN"),
                id = "http://data.knora.org/lists/691eee1cbe"
            ),
            NodePathElementV1(
                label = Some("FILM UND FOTO"),
                name = Some("7"),
                id = "http://data.knora.org/lists/2ebd2706c1"
            ),
            NodePathElementV1(
                label = Some("Heidi Film"),
                name = Some("1"),
                id = "http://data.knora.org/lists/c7f07a3fc1"
            )
        )
    )

    "Load test data " in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(userProfile)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The hlist responder" should {
        "return all the toplevel and child nodes of \"Hierarchisches Stichwortverzeichnis / Signatur der Bilder\" when we do a query for the hlist http://data.knora.org/lists/73d0ec0302 (root node) in the dokubib test data" in {
            // http://localhost:3333/v1/hlists/http%3A%2F%2Fdata.knora.org%2Flists%2F73d0ec0302
            actorUnderTest ! HListGetRequestV1(
                userProfile = userProfile,
                iri = "http://data.knora.org/lists/73d0ec0302"
            )

            expectMsg(timeout, hKeywords)
        }

        "return all nodes of the flat (one level only) list (selection) \"Art des Bildes oder Photographie\"" in {
            // http://localhost:3333/v1/selections/http%3A%2F%2Fdata.knora.org%2Flists%2F6cce4ce5
            actorUnderTest ! SelectionGetRequestV1(
                userProfile = userProfile,
                iri = "http://data.knora.org/lists/6cce4ce5"
            )

            expectMsg(timeout, imageCategory)
        }

        "return the two seasons winter and summer (flat season list consisting of two items)" in {
            // http://localhost:3333/v1/hlists/http%3A%2F%2Fdata.knora.org%2Flists%2Fd19af9ab
            actorUnderTest ! HListGetRequestV1(
                userProfile = userProfile,
                iri = "http://data.knora.org/lists/d19af9ab"
            )

            expectMsg(timeout, season)
        }

        "return the path to the node 'Heidi Film'" in {
            // http://localhost:3333/v1/hlists/http%3A%2F%2Fdata.knora.org%2Flists%2Fc7f07a3fc1?reqtype=node
            actorUnderTest ! NodePathGetRequestV1(
                userProfile = userProfile,
                iri = "http://data.knora.org/lists/c7f07a3fc1"
            )

            expectMsg(timeout, nodePath)
        }
    }
}
