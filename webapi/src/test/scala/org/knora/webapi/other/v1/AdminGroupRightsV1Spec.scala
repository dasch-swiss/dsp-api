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

package org.knora.webapi.other.v1.AdminGroupRightsV1Spec.scala

import java.util.UUID

import akka.actor.Props
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.permissionmessages.{DefaultObjectAccessPermissionsStringForPropertyGetV1, DefaultObjectAccessPermissionsStringForResourceClassGetV1, DefaultObjectAccessPermissionsStringResponseV1}
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.usermessages.{UserProfileByIRIGetV1, UserProfileType, UserProfileV1}
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK, TriplestoreJsonProtocol}
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.{MutableTestIri, MutableUserProfileV1}
import org.knora.webapi.{CoreSpec, LiveActorMaker, OntologyConstants, SharedAdminTestData}

import scala.concurrent.duration._

object AdminGroupRightsV1Spec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * Test specification for testing a difference of rights between a project's admin and member group.
  */
class AdminGroupRightsV1Spec extends CoreSpec(AdminGroupRightsV1Spec.config) with TriplestoreJsonProtocol {

    implicit val executionContext = system.dispatcher
    private val timeout = 5.seconds
    implicit val log = akka.event.Logging(system, this.getClass())

    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/other.v1.AdminGroupRightsV1Spec/admin.ttl", name = "http://www.knora.org/data/admin"),
        RdfDataObject(path = "_test_data/other.v1.AdminGroupRightsV1Spec/permissions.ttl", name = "http://www.knora.org/data/permissions"),
        RdfDataObject(path = "_test_data/other.v1.AdminGroupRightsV1Spec/onto.ttl", name = "http://www.knora.org/ontology/admin-group-rights")
    )

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedAdminTestData.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    /**
      *
      */
    "issue: admin" should {

        val projectIri = "http://data.knora.org/projects/admin-group-rights"
        val adminUserIri = "http://rdfh.ch/users/admin-group-rights-test-admin"
        val adminUser = new MutableUserProfileV1
        val memberUserIri = "http://rdfh.ch/users/admin-group-rights-test-member"
        val memberUser = new MutableUserProfileV1
        val testPass = "test"
        val thingIri = new MutableTestIri
        val firstValueIri = new MutableTestIri
        val secondValueIri = new MutableTestIri
        val conceptResourceClass = "http://www.knora.org/ontology/admin-group-rights#Concept"

        "retrieve the admin-group-rights users' profiles" in {
            responderManager ! UserProfileByIRIGetV1(adminUserIri, UserProfileType.FULL)
            val response1 = expectMsgType[Option[UserProfileV1]](timeout)
            adminUser.set(response1.get)

            responderManager ! UserProfileByIRIGetV1(memberUserIri, UserProfileType.FULL)
            val response2 = expectMsgType[Option[UserProfileV1]](timeout)
            memberUser.set(response2.get)
        }

        "return correct admin-group-rights:Concept resource permissions string for admin user" in {
            responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetV1(projectIri, conceptResourceClass, adminUser.get.permissionData)
            expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR knora-base:ProjectAdmin|D knora-base:ProjectMember"))
        }

        "return correct admin-group-rights:Concept resource class permissions string for member user" in {
            responderManager ! DefaultObjectAccessPermissionsStringForResourceClassGetV1(projectIri, conceptResourceClass, memberUser.get.permissionData)
            expectMsg(DefaultObjectAccessPermissionsStringResponseV1("CR knora-base:ProjectAdmin|D knora-base:ProjectMember"))
        }

    }
}
