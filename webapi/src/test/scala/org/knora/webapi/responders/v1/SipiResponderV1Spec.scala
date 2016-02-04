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

package org.knora.webapi.responders.v1


import akka.actor.Props
import akka.testkit._
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.sipimessages._
import org.knora.webapi.messages.v1respondermessages.triplestoremessages._
import org.knora.webapi.messages.v1respondermessages.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}

import scala.concurrent.duration._

object SipiResponderV1Spec {

    // A test UserDataV1.
    private val userData = UserDataV1(
        email = Some("test@test.ch"),
        lastname = Some("Test"),
        firstname = Some("User"),
        username = Some("testuser"),
        token = None,
        user_id = Some("http://data.knora.org/users/b83acc5f05"),
        lang = "de"
    )

    // A test UserProfileV1.
    private val userProfile = UserProfileV1(
        projects = Vector("http://data.knora.org/projects/77275339"),
        groups = Nil,
        userData = userData
    )

    /*
        This file value has not project Iri attached, it has to be retrieved from the resource.
     */
    private val fileValueResponseFull = SipiFileInfoGetResponseV1(
        path = Some("http://localhost:1024/incunabula_0000000002.jp2"),
        permissionCode = Some(6)
    )

    /*
        This file value is attached to the test project 666
     */
    private val fileValueResponsePreview = SipiFileInfoGetResponseV1(
        path = Some("http://localhost:1024/incunabula_0000000002.jpg"),
        permissionCode = Some(2) // the user is not member of the file value's project.
    )
}

/**
  * Tests [[SipiResponderV1]].
  */
class SipiResponderV1Spec extends CoreSpec() with ImplicitSender {
    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[SipiResponderV1]
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/responders.v1.SipiResponderV1Spec/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 20.seconds


    "Load test data " in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }

    "The Sipi responder" should {
        "return details of a full quality file value (project IRI has to be retrieved from resource because it is not given for file value)" in {
            // http://localhost:3333/v1/files/http%3A%2F%2Fdata.knora.org%2F8a0b1e75%2Freps%2F7e4ba672
            actorUnderTest ! SipiFileInfoGetRequestV1(
                userProfile = SipiResponderV1Spec.userProfile,
                fileValueIri = "http://data.knora.org/8a0b1e75/reps/7e4ba672"
            )

            expectMsg(timeout, SipiResponderV1Spec.fileValueResponseFull)
        }
    }

    "The representations responder" should {
        "return details of a preview file value (a test project Iri is directly attached to file value: the current user is member of the resource's project, not of the one of the file value)" in {
            // http://localhost:3333/v1/files/http%3A%2F%2Fdata.knora.org%2F8a0b1e75%2Freps%2Fbf255339
            actorUnderTest ! SipiFileInfoGetRequestV1(
                userProfile = SipiResponderV1Spec.userProfile,
                fileValueIri = "http://data.knora.org/8a0b1e75/reps/bf255339"
            )

            expectMsg(timeout, SipiResponderV1Spec.fileValueResponsePreview)
        }
    }


}
