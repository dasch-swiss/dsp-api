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

/**
  * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
  * extend ResponderV1 which messes up testing, as we cannot inject the TestActor system.
  */
package org.knora.webapi.responders.v1


import akka.actor.Status.Failure
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages._

import scala.concurrent.duration._


object UsersResponderV1Spec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the messages received by the [[UsersResponderV1]] actor.
  */
class UsersResponderV1Spec extends CoreSpec(UsersResponderV1Spec.config) with ImplicitSender {

    private val timeout = 5.seconds

    private val rootUser = SharedTestDataV1.rootUser
    private val rootUserIri = rootUser.userData.user_id.get
    private val rootUserEmail = rootUser.userData.email.get

    private val normalUser = SharedTestDataV1.normalUser
    private val normalUserIri = normalUser.userData.user_id.get

    private val incunabulaUser = SharedTestDataV1.incunabulaProjectAdminUser
    private val incunabulaUserIri = incunabulaUser.userData.user_id.get
    private val incunabulaUserEmail = incunabulaUser.userData.email.get

    private val imagesProjectIri = SharedTestDataV1.imagesProjectInfo.id

    "The UsersResponder " when {

        "asked about all users" should {
            "return a list" in {
                responderManager ! UsersGetRequestV1(rootUser)
                val response = expectMsgType[UsersGetResponseV1](timeout)
                // println(response.users)
                response.users.nonEmpty should be (true)
                response.users.size should be (20)
            }
        }

        "asked about an user identified by 'iri' " should {

            "return a profile if the user (root user) is known" in {
                responderManager ! UserProfileByIRIGetV1(rootUserIri, UserProfileTypeV1.FULL)
                val response = expectMsgType[Option[UserProfileV1]](timeout)
                // println(response)
                response should equal(Some(rootUser.ofType(UserProfileTypeV1.FULL)))
            }

            "return a profile if the user (incunabula user) is known" in {
                responderManager ! UserProfileByIRIGetV1(incunabulaUserIri, UserProfileTypeV1.FULL)
                expectMsg(Some(incunabulaUser.ofType(UserProfileTypeV1.FULL)))
            }

            "return 'NotFoundException' when the user is unknown " in {
                responderManager ! UserProfileByIRIGetRequestV1("http://rdfh.ch/users/notexisting", UserProfileTypeV1.RESTRICTED, rootUser)
                expectMsg(Failure(NotFoundException(s"User 'http://rdfh.ch/users/notexisting' not found")))
            }

            "return 'None' when the user is unknown " in {
                responderManager ! UserProfileByIRIGetV1("http://rdfh.ch/users/notexisting", UserProfileTypeV1.RESTRICTED)
                expectMsg(None)
            }
        }

        "asked about an user identified by 'email'" should {

            "return a profile if the user (root user) is known" in {
                responderManager ! UserProfileByEmailGetV1(rootUserEmail, UserProfileTypeV1.RESTRICTED)
                expectMsg(Some(rootUser.ofType(UserProfileTypeV1.RESTRICTED)))
            }

            "return a profile if the user (incunabula user) is known" in {
                responderManager ! UserProfileByEmailGetV1(incunabulaUserEmail, UserProfileTypeV1.RESTRICTED)
                expectMsg(Some(incunabulaUser.ofType(UserProfileTypeV1.RESTRICTED)))
            }

            "return 'NotFoundException' when the user is unknown" in {
                responderManager ! UserProfileByEmailGetRequestV1("userwrong@example.com", UserProfileTypeV1.RESTRICTED, rootUser)
                expectMsg(Failure(NotFoundException(s"User 'userwrong@example.com' not found")))
            }

            "return 'None' when the user is unknown" in {
                responderManager ! UserProfileByEmailGetV1("userwrong@example.com", UserProfileTypeV1.RESTRICTED)
                expectMsg(None)
            }
        }
    }
}
