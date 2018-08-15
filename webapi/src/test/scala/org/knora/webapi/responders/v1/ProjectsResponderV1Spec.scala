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

    private val actorUnderTest = TestActorRef[ProjectsResponderV1]

    "The ProjectsResponderV1 " when {

        "used to query for project information" should {

            "return information for every project" in {

                actorUnderTest ! ProjectsGetRequestV1(Some(rootUserProfileV1))
                val received = expectMsgType[ProjectsResponseV1](timeout)

                assert(received.projects.contains(SharedTestDataV1.imagesProjectInfo))
                assert(received.projects.contains(SharedTestDataV1.incunabulaProjectInfo))
            }

            "return information about a project identified by IRI" in {

                /* Incunabula project */
                actorUnderTest ! ProjectInfoByIRIGetRequestV1(
                    SharedTestDataV1.incunabulaProjectInfo.id,
                    Some(SharedTestDataV1.rootUser)
                )
                expectMsg(ProjectInfoResponseV1(SharedTestDataV1.incunabulaProjectInfo))

                /* Images project */
                actorUnderTest ! ProjectInfoByIRIGetRequestV1(
                    SharedTestDataV1.imagesProjectInfo.id,
                    Some(SharedTestDataV1.rootUser)
                )
                expectMsg(ProjectInfoResponseV1(SharedTestDataV1.imagesProjectInfo))

                /* 'SystemProject' */
                actorUnderTest ! ProjectInfoByIRIGetRequestV1(
                    SharedTestDataV1.systemProjectInfo.id,
                    Some(SharedTestDataV1.rootUser)
                )
                expectMsg(ProjectInfoResponseV1(SharedTestDataV1.systemProjectInfo))

            }

            "return information about a project identified by shortname" in {
                actorUnderTest ! ProjectInfoByShortnameGetRequestV1(SharedTestDataV1.incunabulaProjectInfo.shortname, Some(rootUserProfileV1))
                expectMsg(ProjectInfoResponseV1(SharedTestDataV1.incunabulaProjectInfo))
            }

            "return 'NotFoundException' when the project IRI is unknown" in {

                actorUnderTest ! ProjectInfoByIRIGetRequestV1("http://rdfh.ch/projects/notexisting", Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found")))

            }

            "return 'NotFoundException' when the project shortname unknown " in {
                actorUnderTest ! ProjectInfoByShortnameGetRequestV1("projectwrong", Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"Project 'projectwrong' not found")))
            }

        }

        "used to query members" should {

            "return all members of a project identified by IRI" in {
                actorUnderTest ! ProjectMembersByIRIGetRequestV1(SharedTestDataV1.imagesProjectInfo.id, SharedTestDataV1.rootUser)
                val received: ProjectMembersGetResponseV1 = expectMsgType[ProjectMembersGetResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedTestDataV1.imagesUser01.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedTestDataV1.imagesUser02.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedTestDataV1.multiuserUser.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedTestDataV1.imagesReviewerUser.ofType(UserProfileTypeV1.SHORT).userData
                )
                received.userDataV1 should equal (SharedTestDataV1.rootUser.ofType(UserProfileTypeV1.SHORT).userData)
            }

            "return all members of a project identified by shortname" in {
                actorUnderTest ! ProjectMembersByShortnameGetRequestV1(SharedTestDataV1.imagesProjectInfo.shortname, SharedTestDataV1.rootUser)
                val received: ProjectMembersGetResponseV1 = expectMsgType[ProjectMembersGetResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedTestDataV1.imagesUser01.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedTestDataV1.imagesUser02.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedTestDataV1.multiuserUser.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedTestDataV1.imagesReviewerUser.ofType(UserProfileTypeV1.SHORT).userData
                )
                received.userDataV1 should equal (SharedTestDataV1.rootUser.ofType(UserProfileTypeV1.SHORT).userData)
            }

            "return 'NotFound' when the project IRI is unknown (project membership)" in {
                actorUnderTest ! ProjectMembersByIRIGetRequestV1("http://rdfh.ch/projects/notexisting", SharedTestDataV1.rootUser)
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found.")))
            }

            "return 'NotFound' when the project shortname is unknown (project membership)" in {
                actorUnderTest ! ProjectMembersByShortnameGetRequestV1("projectwrong", SharedTestDataV1.rootUser)
                expectMsg(Failure(NotFoundException(s"Project 'projectwrong' not found.")))
            }

            "return all project admin members of a project identified by IRI" in {
                actorUnderTest ! ProjectAdminMembersByIRIGetRequestV1(SharedTestDataV1.IMAGES_PROJECT_IRI, SharedTestDataV1.rootUser)
                val received: ProjectAdminMembersGetResponseV1 = expectMsgType[ProjectAdminMembersGetResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedTestDataV1.imagesUser01.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedTestDataV1.multiuserUser.ofType(UserProfileTypeV1.SHORT).userData
                )
                received.userDataV1 should equal (SharedTestDataV1.rootUser.ofType(UserProfileTypeV1.SHORT).userData)
            }

            "return all project admin members of a project identified by shortname" in {
                actorUnderTest ! ProjectAdminMembersByShortnameGetRequestV1(SharedTestDataV1.imagesProjectInfo.shortname, SharedTestDataV1.rootUser)
                val received: ProjectAdminMembersGetResponseV1 = expectMsgType[ProjectAdminMembersGetResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedTestDataV1.imagesUser01.ofType(UserProfileTypeV1.SHORT).userData,
                    SharedTestDataV1.multiuserUser.ofType(UserProfileTypeV1.SHORT).userData
                )
                received.userDataV1 should equal (SharedTestDataV1.rootUser.ofType(UserProfileTypeV1.SHORT).userData)
            }

            "return 'NotFound' when the project IRI is unknown (project admin membership)" in {
                actorUnderTest ! ProjectAdminMembersByIRIGetRequestV1("http://rdfh.ch/projects/notexisting", SharedTestDataV1.rootUser)
                expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found.")))
            }

            "return 'NotFound' when the project shortname is unknown (project admin membership)" in {
                actorUnderTest ! ProjectAdminMembersByShortnameGetRequestV1("projectwrong", SharedTestDataV1.rootUser)
                expectMsg(Failure(NotFoundException(s"Project 'projectwrong' not found.")))
            }
        }
    }

}
