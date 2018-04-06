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

package org.knora.webapi.other.v1

import java.util.UUID

import akka.actor.Props
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{DefaultObjectAccessPermissionsStringForPropertyGetADM, DefaultObjectAccessPermissionsStringForResourceClassGetADM, DefaultObjectAccessPermissionsStringResponseADM}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.resourcemessages.{ResourceCreateRequestV1, ResourceCreateResponseV1, _}
import org.knora.webapi.messages.v1.responder.usermessages.{UserProfileByIRIGetV1, UserProfileTypeV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.valuemessages.{CreateValueV1WithComment, TextValueSimpleV1, _}
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.{MutableTestIri, MutableUserProfileV1}

import scala.concurrent.duration._

object NieIneV1Spec {
  val config = ConfigFactory.parseString(
    """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * Test specification for testing a complex permissions structure of the drawings-gods-project.
  */
class NieIneV1Spec extends CoreSpec( NieIneV1Spec.config ) with TriplestoreJsonProtocol {

  implicit val executionContext = system.dispatcher
  private val timeout = 5.seconds
  implicit val log = akka.event.Logging(system, this.getClass())

  val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
  val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

  private val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/admin-data.ttl", name = "http://www.knora.org/data/admin"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/permissions-data.ttl", name = "http://www.knora.org/data/permissions"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/drcs-ontology-knora.ttl", name = "http://www.knora.org/ontology/drcs"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/text-ontology-knora.ttl", name = "http://www.knora.org/ontology/text"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/human-ontology-knora.ttl", name = "http://www.knora.org/ontology/human"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/image-ontology-knora.ttl", name = "http://www.knora.org/ontology/image"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/concept-ontology-knora.ttl", name = "http://www.knora.org/ontology/concept"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/scholasticism-ontology-knora.ttl", name = "http://www.knora.org/ontology/scholasticism"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/kuno-raeber-ontology-knora.ttl", name = "http://www.knora.org/ontology/kuno-raeber"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/event-ontology-knora.ttl", name = "http://www.knora.org/ontology/event"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/kuno-raeber-gui-ontology-knora.ttl", name = "http://www.knora.org/ontology/kuno-raeber-gui"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/publishing-ontology-knora.ttl", name = "http://www.knora.org/ontology/kuno-raeber-gui"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/agent-ontology-knora.ttl", name = "http://www.knora.org/ontology/agent"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/information-carrier-ontology-knora.ttl", name = "http://www.knora.org/ontology/information-carrier"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/language-ontology-knora.ttl", name = "http://www.knora.org/ontology/language"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/physical-resource-ontology-knora.ttl", name = "http://www.knora.org/ontology/physical-resource"),
    //RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/organisation-ontology-knora.ttl", name = "http://www.knora.org/ontology/organisation"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/literature-ontology-knora.ttl", name = "http://www.knora.org/ontology/literature"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/teaching-ontology-knora.ttl", name = "http://www.knora.org/ontology/teaching"),
    RdfDataObject(path = "_test_data/other.v1.NieIneV1Spec/ontologies/political-geography-ontology-knora.ttl", name = "http://www.knora.org/ontology/political-geography")
  )

  "Load test data" in {
    storeManager ! ResetTriplestoreContent(rdfDataObjects)
    expectMsg(300.seconds, ResetTriplestoreContentACK())

    responderManager ! LoadOntologiesRequest(SharedTestDataV1.rootUser)
    expectMsg(10.seconds, LoadOntologiesResponse())
  }

}
