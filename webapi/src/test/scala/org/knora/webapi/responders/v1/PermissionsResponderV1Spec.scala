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

package org.knora.webapi.responders.v1

import akka.actor.Props
import akka.actor.Status.Failure
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.permissionmessages._
import org.knora.webapi.messages.v1.store.triplestoremessages.{ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.PermissionsResponderV1SpecTestData._
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.KnoraIdUtil

import scala.collection.Map
import scala.concurrent.Await
import scala.concurrent.duration._


object PermissionsResponderV1Spec {

    val config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}


/**
  * This spec is used to test the [[PermissionsResponderV1]] actor.
  */
class PermissionsResponderV1Spec extends CoreSpec(PermissionsResponderV1Spec.config) with ImplicitSender {

    implicit val executionContext = system.dispatcher
    private val timeout = 5.seconds

    val knoraIdUtil = new KnoraIdUtil

    val rootUserProfileV1 = SharedTestData.rootUserProfileV1
    val multiuserUserProfileV1 = SharedTestData.multiuserUserProfileV1

    val actorUnderTest = TestActorRef[PermissionsResponderV1]
    val underlyingActorUnderTest = actorUnderTest.underlyingActor
    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List()

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }


    "The PermissionsResponderV1 " when {

        "queried about the permission profile" should {

            "return the permissions profile" in {
                actorUnderTest ! PermissionProfileGetV1(
                    projectIris = SharedTestData.rootUserProfileV1.projects,
                    groupIris = SharedTestData.rootUserProfileV1.groups,
                    isInProjectAdminGroups = Seq.empty[IRI],
                    isInSystemAdminGroup = true
                )
                expectMsg(SharedTestData.rootUserProfileV1.permissionProfile)

                actorUnderTest ! PermissionProfileGetV1(
                    projectIris = SharedTestData.multiuserUserProfileV1.projects,
                    groupIris = SharedTestData.multiuserUserProfileV1.groups,
                    isInProjectAdminGroups = List("http://data.knora.org/projects/77275339>", "http://data.knora.org/projects/images", "http://data.knora.org/projects/666"),
                    isInSystemAdminGroup = false
                )
                expectMsg(SharedTestData.multiuserUserProfileV1.permissionProfile)
            }
        }

        "queried about administrative permissions " should {

            "return all AdministrativePermissions for project " in {
                actorUnderTest ! AdministrativePermissionsForProjectGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    SharedTestData.rootUserProfileV1
                )
                expectMsg(AdministrativePermissionsForProjectGetResponseV1(
                    Seq(perm001.p, perm003.p)
                ))
            }

            "return AdministrativePermission for project and group" in {
                actorUnderTest ! AdministrativePermissionForProjectGroupGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    groupIri = OntologyConstants.KnoraBase.ProjectMember,
                    SharedTestData.rootUserProfileV1
                )
                expectMsg(AdministrativePermissionForProjectGroupGetResponseV1(perm001.p))
            }

            "return AdministrativePermission for IRI " in {
                actorUnderTest ! AdministrativePermissionForIriGetRequestV1(
                    administrativePermissionIri = perm001.iri,
                    SharedTestData.rootUserProfileV1
                )
                expectMsg(AdministrativePermissionForIriGetResponseV1(perm001.p))
            }

        }

        "queried about default object access permissions " should {

            "return all DefaultObjectAccessPermissions for project " in {
                actorUnderTest ! DefaultObjectAccessPermissionsForProjectGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    SharedTestData.rootUserProfileV1
                )
                expectMsg(DefaultObjectAccessPermissionsForProjectGetResponseV1(
                    defaultObjectAccessPermissions = Seq(perm002.p)
                ))
            }

            "return DefaultObjectAccessPermission for project and group" in {
                actorUnderTest ! DefaultObjectAccessPermissionForProjectGroupGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    groupIri = OntologyConstants.KnoraBase.ProjectMember,
                    userProfileV1 = SharedTestData.rootUserProfileV1
                )
                expectMsg(DefaultObjectAccessPermissionForProjectGroupGetResponseV1(
                    defaultObjectAccessPermission = perm002.p
                ))
            }

            "return DefaultObjectAccessPermission for IRI" in {
                actorUnderTest ! DefaultObjectAccessPermissionForIriGetRequestV1(
                    defaultObjectAccessPermissionIri = perm002.iri,
                    SharedTestData.rootUserProfileV1
                )
                expectMsg(DefaultObjectAccessPermissionForIriGetResponseV1(
                    defaultObjectAccessPermission = perm002.p
                ))
            }

        }

        "asked to create an administrative permission" should {

            "fail and return a 'BadRequestException' when project does not exist" ignore {
                fail
            }

            "fail and return a  'BadRequestException' when group does not exist" ignore {
                fail
            }

            "fail and return a 'NotAuthorizedException' whe the user's permission are not high enough (e.g., not member of ProjectAdmin group" ignore {
                fail
            }

            "fail and return a 'DuplicateValueException' when permission for project and group combination already exists" in {
                val iri = knoraIdUtil.makeRandomPermissionIri
                actorUnderTest ! AdministrativePermissionCreateRequestV1(
                    newAdministrativePermissionV1 = NewAdministrativePermissionV1(
                        iri = iri,
                        forProject = IMAGES_PROJECT_IRI,
                        forGroup = OntologyConstants.KnoraBase.ProjectMember,
                        hasPermissions = Seq(PermissionV1.ProjectResourceCreateAllPermission)
                    ),
                    userProfileV1 = rootUserProfileV1
                )
                expectMsg(Failure(DuplicateValueException(s"Permission for project: '$IMAGES_PROJECT_IRI' and group: '${OntologyConstants.KnoraBase.ProjectMember}' combination already exists.")))
            }

            "create and return an administrative permission " ignore {
                fail
            }

        }

        "asked to create a default object access permission" should {

            "fail and return a 'BadRequestException' when project does not exist" ignore {
                fail
            }

            "fail and return a  'BadRequestException' when resource class does not exist" ignore {
                fail
            }

            "fail and return a  'BadRequestException' when property does not exist" ignore {
                fail
            }

            "fail and return a 'NotAuthorizedException' whe the user's permission are not high enough (e.g., not member of ProjectAdmin group" ignore {

                /* defining project level default object access permissions, so I need to be at least a member of the 'ProjectAdmin' group */

                /* defining system level default object access permissions, so I need to be at least a member of the 'SystemAdmin' group */

                fail
            }

            "fail and return a 'DuplicateValueException' when permission for project / group / resource class / property  combination already exists" ignore {
                fail
            }
        }
        "asked to delete a permission object " should {

            "delete an administrative permission " ignore {
                fail
            }

            "delete a default object access permission " ignore {
                fail
            }
        }
        /*
        "asked to create permissions from a template " should {
            "create and return all permissions defined inside the template " ignore {
                /* the default behaviour is to delete all permissions inside a project prior to applying a template */
                actorUnderTest ! TemplatePermissionsCreateRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    permissionsTemplate = PermissionsTemplate.OPEN,
                    rootUserProfileV1
                )
                expectMsg(TemplatePermissionsCreateResponseV1(
                    success = true,
                    msg = "ok",
                    administrativePermissions = List(perm001.p, perm003.p),
                    defaultObjectAccessPermissions = List(perm002.p)
                ))
            }
        }
        */
    }

    "The PermissensResponderV1 helper methods" when {

        "called" should {

            "return user's administrative permissions " in {
                val result: Map[IRI, Set[PermissionV1]] = Await.result(underlyingActorUnderTest.getUserAdministrativePermissionsRequestV1(multiuserUserProfileV1.permissionProfile.groupsPerProject).mapTo[Map[IRI, Set[PermissionV1]]], 1.seconds)
                result should equal(multiuserUserProfileV1.permissionProfile.administrativePermissionsPerProject)
            }

            "return user's default object access permissions " in {
                val result: Map[IRI, Set[PermissionV1]] = Await.result(underlyingActorUnderTest.getUserDefaultObjectAccessPermissionsRequestV1(multiuserUserProfileV1.permissionProfile.groupsPerProject), 1.seconds)
                result should equal(multiuserUserProfileV1.permissionProfile.defaultObjectAccessPermissionsPerProject)
            }

            "build a permission object" in {
                underlyingActorUnderTest.buildPermissionObject(
                    name = OntologyConstants.KnoraBase.ProjectResourceCreateRestrictedPermission,
                    iris = Some(Set("1", "2", "3"))
                ) should equal(
                    PermissionV1.ProjectResourceCreateRestrictedPermission(Set("1", "2", "3"))
                )
            }

        }
    }
}
