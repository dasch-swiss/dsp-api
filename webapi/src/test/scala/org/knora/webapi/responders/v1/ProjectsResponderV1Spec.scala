/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

/**
  * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
  * extend ResponderV1 which messes up testing, as we cannot inject the TestActor system.
  */
package org.knora.webapi.responders.v1

import java.util.UUID

import akka.actor.Props
import akka.actor.Status.Failure
import akka.testkit.{ImplicitSender, TestActorRef}
import arq.iri
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileType}
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.MutableTestIri

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

    private implicit val executionContext = system.dispatcher
    private val timeout = 5.seconds

    private val rootUserProfileV1 = SharedAdminTestData.rootUser

    private val actorUnderTest = TestActorRef[ProjectsResponderV1]
    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List()

    "Load test data" in {

        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedAdminTestData.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The ProjectsResponderV1 " when {

        "used to query for project information" should {

            "return information for every project" in {

                actorUnderTest ! ProjectsGetRequestV1(Some(rootUserProfileV1))
                val received = expectMsgType[ProjectsResponseV1](timeout)

                assert(received.projects.contains(SharedAdminTestData.imagesProjectInfo))
                assert(received.projects.contains(SharedAdminTestData.incunabulaProjectInfo))
            }

            "return information about a project identified by IRI" in {

                /* Incunabula project */
                actorUnderTest ! ProjectInfoByIRIGetRequestV1(
                    SharedAdminTestData.incunabulaProjectInfo.id,
                    Some(SharedAdminTestData.rootUser)
                )
                expectMsg(ProjectInfoResponseV1(SharedAdminTestData.incunabulaProjectInfo))

                /* Images project */
                actorUnderTest ! ProjectInfoByIRIGetRequestV1(
                    SharedAdminTestData.imagesProjectInfo.id,
                    Some(SharedAdminTestData.rootUser)
                )
                expectMsg(ProjectInfoResponseV1(SharedAdminTestData.imagesProjectInfo))

                /* 'SystemProject' */
                actorUnderTest ! ProjectInfoByIRIGetRequestV1(
                    SharedAdminTestData.systemProjectInfo.id,
                    Some(SharedAdminTestData.rootUser)
                )
                expectMsg(ProjectInfoResponseV1(SharedAdminTestData.systemProjectInfo))

            }

            "return information about a project identified by shortname" in {
                actorUnderTest ! ProjectInfoByShortnameGetRequestV1(SharedAdminTestData.incunabulaProjectInfo.shortname, Some(rootUserProfileV1))
                expectMsg(ProjectInfoResponseV1(SharedAdminTestData.incunabulaProjectInfo))
            }

            "return 'NotFoundException' when the project IRI is unknown" in {

                actorUnderTest ! ProjectInfoByIRIGetRequestV1("http://data.knora.org/projects/notexisting", Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"Project 'http://data.knora.org/projects/notexisting' not found")))

            }

            "return 'NotFoundException' when the project shortname unknown " in {
                actorUnderTest ! ProjectInfoByShortnameGetRequestV1("projectwrong", Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"Project 'projectwrong' not found")))
            }

        }

        "used to modify project information" should {

            val newProjectIri = new MutableTestIri

            "create the project with using a permissions template, and return the 'full' project info if the supplied shortname is unique" in {
                actorUnderTest ! ProjectCreateRequestV1(
                    CreateProjectApiRequestV1(
                        shortname = "newproject",
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                val received: ProjectOperationResponseV1 = expectMsgType[ProjectOperationResponseV1](timeout)
                assert(received.project_info.shortname.equals("newproject"))
                assert(received.project_info.longname.contains("project longname"))
                assert(received.project_info.description.contains("project description"))
                assert(received.project_info.ontologyNamedGraph.equals("http://www.knora.org/ontology/newproject"))
                assert(received.project_info.dataNamedGraph.equals("http://www.knora.org/data/newproject"))

                newProjectIri.set(received.project_info.id)
            }

            "return a 'DuplicateValueException' if the supplied project shortname during creation is not unique" in {
                actorUnderTest ! ProjectCreateRequestV1(
                    CreateProjectApiRequestV1(
                        shortname = "newproject",
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(DuplicateValueException(s"Project with the shortname: 'newproject' already exists")))
            }

            "return 'BadRequestException' if project 'shortname' during creation is missing" in {

                actorUnderTest ! ProjectCreateRequestV1(
                    CreateProjectApiRequestV1(
                        shortname = "",
                        longname = Some("project longname"),
                        description = Some("project description"),
                        keywords = Some("keywords"),
                        logo = Some("/fu/bar/baz.jpg"),
                        status = true,
                        selfjoin = false
                    ),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                expectMsg(Failure(BadRequestException("'Shortname' cannot be empty")))
            }

            "update a project" in {
                actorUnderTest ! ProjectChangeRequestV1(
                    projectIri = newProjectIri.get,
                    changeProjectRequest = ChangeProjectApiRequestV1(
                        shortname = None,
                        longname = Some("updated project longname"),
                        description = Some("updated project description"),
                        keywords = Some("updated keywords"),
                        logo = Some("/fu/bar/baz-updated.jpg"),
                        status = Some(false),
                        selfjoin = Some(true)
                    ),
                    SharedAdminTestData.rootUser,
                    UUID.randomUUID()
                )
                val received: ProjectOperationResponseV1 = expectMsgType[ProjectOperationResponseV1](timeout)
                received.project_info.longname should be (Some("project longname"))
                received.project_info.description should be (Some("project description"))
                received.project_info.ontologyNamedGraph should be ("http://www.knora.org/ontology/newproject")
                received.project_info.dataNamedGraph should be ("http://www.knora.org/data/newproject")
                received.project_info.status should be (false)
                received.project_info.selfjoin should be (true)
            }

        }

        "used to query members" should {

            "return all members of a project identified by IRI" in {
                actorUnderTest ! ProjectMembersByIRIGetRequestV1(SharedAdminTestData.imagesProjectInfo.id, SharedAdminTestData.rootUser)
                val received: ProjectMembersGetResponseV1 = expectMsgType[ProjectMembersGetResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedAdminTestData.imagesUser01.ofType(UserProfileType.SHORT).userData,
                    SharedAdminTestData.imagesUser02.ofType(UserProfileType.SHORT).userData,
                    SharedAdminTestData.multiuserUser.ofType(UserProfileType.SHORT).userData,
                    SharedAdminTestData.imagesReviewerUser.ofType(UserProfileType.SHORT).userData
                )
                received.userDataV1 should equal (SharedAdminTestData.rootUser.ofType(UserProfileType.SHORT).userData)
            }

            "return all members of a project identified by shortname" in {
                actorUnderTest ! ProjectMembersByShortnameGetRequestV1(SharedAdminTestData.imagesProjectInfo.shortname, SharedAdminTestData.rootUser)
                val received: ProjectMembersGetResponseV1 = expectMsgType[ProjectMembersGetResponseV1](timeout)
                received.members should contain allElementsOf Seq(
                    SharedAdminTestData.imagesUser01.ofType(UserProfileType.SHORT).userData,
                    SharedAdminTestData.imagesUser02.ofType(UserProfileType.SHORT).userData,
                    SharedAdminTestData.multiuserUser.ofType(UserProfileType.SHORT).userData,
                    SharedAdminTestData.imagesReviewerUser.ofType(UserProfileType.SHORT).userData
                )
                received.userDataV1 should equal (SharedAdminTestData.rootUser.ofType(UserProfileType.SHORT).userData)
            }

            "return 'NotFound' when the project IRI is unknown" in {
                actorUnderTest ! ProjectMembersByIRIGetRequestV1("http://data.knora.org/projects/notexisting", SharedAdminTestData.rootUser)
                expectMsg(Failure(NotFoundException(s"Project 'http://data.knora.org/projects/notexisting' either not found or has no members")))
            }

            "return 'NotFound' when the project shortname is unknown" in {
                actorUnderTest ! ProjectMembersByShortnameGetRequestV1("projectwrong", SharedAdminTestData.rootUser)
                expectMsg(Failure(NotFoundException(s"Project 'projectwrong' either not found or has no members")))
            }
        }




        /*
        "asked to update a project " should {
            "update the project " in {

                /* User information is updated by the user */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.normaluserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.Foaf.GivenName,
                    newValue = "Donald",
                    userProfile = SharedTestData.normaluserUserProfileV1,
                    UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(updatedUserProfile, requestingUserData) => {
                        // check if information was changed
                        assert(updatedUserProfile.userData.firstname.contains("Donald"))

                        // check if correct and updated userdata is returned
                        assert(requestingUserData.firstname.contains("Donald"))
                    }
                }

                /* User information is updated by a system admin */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.normaluserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.Foaf.FamilyName,
                    newValue = "Duck",
                    userProfile = SharedTestData.superuserUserProfileV1,
                    UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(updatedUserProfile, requestingUserData) => {
                        // check if information was changed
                        assert(updatedUserProfile.userData.lastname.contains("Duck"))

                        // check if the correct userdata is returned
                        assert(requestingUserData.user_id.contains(SharedTestData.superuserUserProfileV1.userData.user_id.get))
                    }
                }

            }

            "return a 'ForbiddenException' if the user requesting the update does not have the necessary permission" in {

                /* User information is updated by other normal user */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.superuserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.Foaf.GivenName,
                    newValue = "Donald",
                    userProfile = SharedTestData.normaluserUserProfileV1,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User information can only be changed by the user itself or a system administrator")))

                /* User information is updated by anonymous */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.superuserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.Foaf.GivenName,
                    newValue = ("Donald"),
                    userProfile = SharedTestData.anonymousUserProfileV1,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("User information can only be changed by the user itself or a system administrator")))

            }

            "update the project, (deleting) making it inactive " in {
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.normaluserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.KnoraBase.IsActiveUser,
                    newValue = false,
                    userProfile = SharedTestData.superuserUserProfileV1,
                    UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(updatedUserProfile, requestingUserData) => {
                        // check if information was changed
                        assert(updatedUserProfile.userData.isActiveUser.contains(false))
                    }
                }

            }
        }
        */
    }

}
