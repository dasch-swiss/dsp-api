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

/**
  * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
  * extend ResponderV1 which messes up testing, as we cannot inject the TestActor system.
  */
package org.knora.webapi.responders.v1

import akka.actor.Props
import akka.actor.Status.Failure
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.store.triplestoremessages._
import org.knora.webapi.responders.RESPONDER_MANAGER_ACTOR_NAME
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}

import scala.concurrent.duration._


object ProjectsResponderV1Spec {

    val config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test the messages received by the [[ProjectsResponderV1]] actor.
  */
class ProjectsResponderV1Spec extends CoreSpec(ProjectsResponderV1Spec.config) with ImplicitSender {

    implicit val executionContext = system.dispatcher
    private val timeout = 5.seconds

    val rootUserProfileV1 = SharedAdminTestData.rootUserProfileV1

    val actorUnderTest = TestActorRef[ProjectsResponderV1]
    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List()

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }

    "The ProjectsResponderV1 " when {
        "asked about all projects " should {
            "return 'full' project info for every project" in {
                actorUnderTest ! ProjectsGetRequestV1(ProjectInfoType.FULL, Some(rootUserProfileV1))
                expectMsgPF(timeout) {
                    case ProjectsResponseV1(projects, userdata) => {
                        //println(projects)
                        assert(projects.contains(SharedAdminTestData.imagesProjectInfoV1))
                        //Todo: make it work with icunabula
                        //assert(projects.contains(incunabulaPI))
                        assert(projects.contains(SharedAdminTestData.sixerProjectProjectInfoV1))
                    }
                }
            }
        }
        "asked about a project identified by 'iri' " should {
            "return full project info if the project is known " in {

                /* Incunabula project */
                actorUnderTest ! ProjectInfoByIRIGetRequestV1(
                    SharedAdminTestData.incunabulaProjectInfoV1.id,
                    ProjectInfoType.FULL,
                    Some(SharedAdminTestData.rootUserProfileV1)
                )
                expectMsg(ProjectInfoResponseV1(SharedAdminTestData.incunabulaProjectInfoV1, Some(rootUserProfileV1.userData)))

                /* Images project */
                actorUnderTest ! ProjectInfoByIRIGetRequestV1(
                    SharedAdminTestData.imagesProjectInfoV1.id,
                    ProjectInfoType.FULL,
                    Some(SharedAdminTestData.rootUserProfileV1)
                )
                expectMsg(ProjectInfoResponseV1(SharedAdminTestData.imagesProjectInfoV1, Some(rootUserProfileV1.userData)))

                /* 'SystemProject' */
                actorUnderTest ! ProjectInfoByIRIGetRequestV1(
                    SharedAdminTestData.systemProjectInfoV1.id,
                    ProjectInfoType.FULL,
                    Some(SharedAdminTestData.rootUserProfileV1)
                )
                expectMsg(ProjectInfoResponseV1(SharedAdminTestData.systemProjectInfoV1, Some(rootUserProfileV1.userData)))

            }
            "return 'NotFoundException' when the project is unknown " in {
                actorUnderTest ! ProjectInfoByIRIGetRequestV1("http://data.knora.org/projects/notexisting", ProjectInfoType.FULL, Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"Project 'http://data.knora.org/projects/notexisting' not found")))
            }
        }
        "asked about a project identified by 'shortname' " should {
            "return 'full' project info if the project is known " in {
                actorUnderTest ! ProjectInfoByShortnameGetRequestV1(SharedAdminTestData.incunabulaProjectInfoV1.shortname, ProjectInfoType.FULL, Some(rootUserProfileV1))
                expectMsg(ProjectInfoResponseV1(SharedAdminTestData.incunabulaProjectInfoV1, Some(rootUserProfileV1.userData)))
            }

            "return 'NotFoundException' when the project is unknown " in {
                actorUnderTest ! ProjectInfoByShortnameGetRequestV1("projectwrong", ProjectInfoType.FULL, Some(rootUserProfileV1))
                expectMsg(Failure(NotFoundException(s"Project 'projectwrong' not found")))
            }
        }
        "asked to create a new project " should {
            "create the project with using a permissions template, and return the 'full' project info if the supplied shortname is unique " in {
                actorUnderTest ! ProjectCreateRequestV1(
                    NewProjectDataV1(
                        shortname = "newproject",
                        longname = "project longname",
                        description = "project description",
                        keywords = "keywords",
                        logo = "",
                        basepath = "/fu/bar",
                        isActiveProject = true,
                        hasSelfJoinEnabled = false
                    ),
                    SharedAdminTestData.rootUserProfileV1
                )
                expectMsgPF(timeout) {
                    case ProjectOperationResponseV1(newProjectInfo, requestingUserData) => {
                        //println(newProjectInfo)
                        assert(newProjectInfo.shortname.equals("newproject"))
                        assert(newProjectInfo.longname.get.equals("project longname"))
                        assert(newProjectInfo.projectOntologyGraph.equals("http://www.knora.org/ontology/newproject"))
                        assert(newProjectInfo.projectDataGraph.equals("http://www.knora.org/data/newproject"))
                    }
                }
            }
            "return a 'DuplicateValueException' if the supplied project shortname is not unique " in {
                actorUnderTest ! ProjectCreateRequestV1(
                    NewProjectDataV1(
                        shortname = "newproject",
                        longname = "project longname",
                        description = "project description",
                        keywords = "keywords",
                        logo = "",
                        basepath = "",
                        isActiveProject = true,
                        hasSelfJoinEnabled = false
                    ),
                    SharedAdminTestData.rootUserProfileV1
                )
                expectMsg(Failure(DuplicateValueException(s"Project with the shortname: 'newproject' already exists")))
            }
            "return 'BadRequestException' if 'shortname' is missing" in {

                actorUnderTest ! ProjectCreateRequestV1(
                    NewProjectDataV1(
                        shortname = "",
                        longname = "project longname",
                        description = "project description",
                        keywords = "keywords",
                        logo = "",
                        basepath = "",
                        isActiveProject = true,
                        hasSelfJoinEnabled = false
                    ),
                    SharedAdminTestData.rootUserProfileV1
                )
                expectMsg(Failure(BadRequestException("'Shortname' cannot be empty")))
            }
        }
        /*
        "asked to update a user " should {
            "update the user " in {

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
            "return a 'ForbiddenException' if the user requesting update is not the user itself or system admin " in {

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
            "return a 'ForbiddenException' if the update gives SA rights but the user requesting the update is not SA " in {
                /* User information is updated by the user */
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.normaluserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.KnoraBase.IsSystemAdmin,
                    newValue = true,
                    userProfile = SharedTestData.normaluserUserProfileV1,
                    UUID.randomUUID
                )
                expectMsg(Failure(ForbiddenException("Giving an user system admin rights can only be performed by another system admin")))
            }
            "update the user, giving him SA rights " in {
                actorUnderTest ! UserUpdateRequestV1(
                    userIri = SharedTestData.normaluserUserProfileV1.userData.user_id.get,
                    propertyIri = OntologyConstants.KnoraBase.IsSystemAdmin,
                    newValue = true,
                    userProfile = SharedTestData.superuserUserProfileV1,
                    UUID.randomUUID
                )
                expectMsgPF(timeout) {
                    case UserOperationResponseV1(updatedUserProfile, requestingUserData) => {
                        // check if information was changed
                        assert(updatedUserProfile.userData.isSystemAdmin.contains(true))
                    }
                }
            }
            "update the user, (deleting) making him inactive " in {
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
