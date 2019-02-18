/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileTypeV1

import scala.concurrent.duration._


object ProjectsResponderV1Spec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the messages received by the [[ProjectsResponderV1]] actor.
  */
class ProjectsResponderV1Spec extends CoreSpec(ProjectsResponderV1Spec.config) with ImplicitSender {

    private val timeout = 5.seconds

    private val rootUserProfileV1 = SharedTestDataV1.rootUser

    "The ProjectsResponderV1 " when {

        "used to query for project information" should {

            "return information for every project" in {

                responderManager ! ProjectsGetRequestV1(Some(rootUserProfileV1))
                val received = expectMsgType[ProjectsResponseV1](timeout)

                assert(received.projects.contains(SharedTestDataV1.imagesProjectInfo))
                assert(received.projects.contains(SharedTestDataV1.incunabulaProjectInfo))
            }

            "return information about a project identified by IRI" in {

                /* Incunabula project */
                responderManager ! ProjectInfoByIRIGetRequestV1(
                    SharedTestDataV1.incunabulaProjectInfo.id,
                    Some(SharedTestDataV1.rootUser)
                )
                expectMsg(ProjectInfoResponseV1(SharedTestDataV1.incunabulaProjectInfo))

                /* Images project */
                responderManager ! ProjectInfoByIRIGetRequestV1(
                    SharedTestDataV1.imagesProjectInfo.id,
                    Some(SharedTestDataV1.rootUser)
                )
                expectMsg(ProjectInfoResponseV1(SharedTestDataV1.imagesProjectInfo))

                /* 'SystemProject' */
                responderManager ! ProjectInfoByIRIGetRequestV1(
                    SharedTestDataV1.systemProjectInfo.id,
                    Some(SharedTestDataV1.rootUser)
                )
                expectMsg(ProjectInfoResponseV1(SharedTestDataV1.systemProjectInfo))

            }

            "return information about a project identified by shortname" in {
                responderManager ! ProjectInfoByShortnameGetRequestV1(SharedTestDataV1.incunabulaProjectInfo.shortname, Some(rootUserProfileV1))
                expectMsg(ProjectInfoResponseV1(SharedTestDataV1.incunabulaProjectInfo))
            }

            "return 'NotFoundException' when the project IRI is unknown" in {

                responderManager ! ProjectInfoByIRIGetRequestV1("http://rdfh.ch/projects/notexisting", Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found")))

            }

            "return 'NotFoundException' when the project shortname unknown " in {
                responderManager ! ProjectInfoByShortnameGetRequestV1("projectwrong", Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"Project 'projectwrong' not found")))
            }
        }
    }

}
