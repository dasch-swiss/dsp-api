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

package org.knora.webapi.responders.v2

import java.util.{Base64, UUID}

import akka.testkit._
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.persistentmapmessages._

import scala.concurrent.duration._

/**
  * Static data for testing [[PersistentMapResponderV2]].
  */
object PersistentMapResponderV2Spec {
    private val base64Encoder = Base64.getUrlEncoder.withoutPadding
    private val userProfile = SharedTestDataADM.incunabulaProjectAdminUser
    private val userEmailBytes = userProfile.email.getBytes("UTF-8")
    private val userEmailBase64 = base64Encoder.encodeToString(userEmailBytes)
    private val testMap1Path = s"user/$userEmailBase64/testmap1"

    private val testMap1Data: Map[String, String] = Map(
        "key1" ->
            """Nel mezzo del cammin di nostra vita
              |mi ritrovai per una selva oscura,
              |ché la diritta via era smarrita.""".stripMargin,
        "key2" ->
            """Ahi quanto a dir qual era è cosa dura
              |esta selva selvaggia e aspra e forte
              |che nel pensier rinova la paura!""".stripMargin,
        "key3" ->
            """Tant' è amara che poco è più morte;
              |ma per trattar del ben ch'i' vi trovai,
              |dirò de l'altre cose ch'i' v'ho scorte.""".stripMargin,
        "key4" ->
            """Io non so ben ridir com' i' v'intrai,
              |tant' era pien di sonno a quel punto
              |che la verace via abbandonai.""".stripMargin
    )
}

/**
  * Tests [[PersistentMapResponderV2]].
  */
class PersistentMapResponderV2Spec extends CoreSpec() with ImplicitSender {

    import PersistentMapResponderV2Spec._

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    "The persistent map responder" should {
        "store a persistent map entry, creating the map, then read the entry" in {
            val mapEntryKey = "key1"

            responderManager ! PersistentMapEntryPutRequestV2(
                mapPath = testMap1Path,
                mapEntryKey = mapEntryKey,
                mapEntryValue = testMap1Data(mapEntryKey),
                apiRequestID = UUID.randomUUID
            )

            expectMsg(timeout, PersistentMapEntryPutResponseV2())

            responderManager ! PersistentMapEntryGetRequestV2(
                mapPath = testMap1Path,
                mapEntryKey = mapEntryKey
            )

            expectMsgPF(timeout) {
                case persistentMapEntry: PersistentMapEntryV2 =>
                    (persistentMapEntry.key == mapEntryKey || persistentMapEntry.value == testMap1Data(mapEntryKey)) should ===(true)
            }
        }

        "store more persistent map entries, then read the whole map" in {
            for (index <- 2 to 4) {
                val mapEntryKey = s"key$index"

                responderManager ! PersistentMapEntryPutRequestV2(
                    mapPath = testMap1Path,
                    mapEntryKey = mapEntryKey,
                    mapEntryValue = testMap1Data(mapEntryKey),
                    apiRequestID = UUID.randomUUID
                )

                expectMsg(timeout, PersistentMapEntryPutResponseV2())
            }

            responderManager ! PersistentMapGetRequestV2(mapPath = testMap1Path)

            expectMsgPF(timeout) {
                case persistentMap: PersistentMapV2 =>
                    persistentMap.path should ===(testMap1Path)

                    val entriesAsMap: Map[String, String] = persistentMap.entries.map {
                        entry => (entry.key, entry.value)
                    }.toMap

                    entriesAsMap should ===(testMap1Data)
            }
        }

        "delete a persistent map" in {
            responderManager ! PersistentMapDeleteRequestV2(
                mapPath = testMap1Path,
                apiRequestID = UUID.randomUUID
            )

            expectMsg(timeout, PersistentMapDeleteResponseV2())

            responderManager ! PersistentMapGetRequestV2(mapPath = testMap1Path)

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }
    }
}

