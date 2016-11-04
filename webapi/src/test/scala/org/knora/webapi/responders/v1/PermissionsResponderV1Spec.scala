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
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.responder.permissionmessages._
import org.knora.webapi.messages.v1.store.triplestoremessages.{ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.{CoreSpec, LiveActorMaker, OntologyConstants, SharedTestData}
import PermissionsResponderV1SpecTestData._

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

    val rootUserProfileV1 = SharedTestData.rootUserProfileV1
    val multiuserUserProfileV1 = SharedTestData.multiuserUserProfileV1

    val actorUnderTest = TestActorRef[PermissionsResponderV1]
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List()

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }


    "The PermissionsResponderV1 " when {
        "queried about permissions " should {
            "return AdministrativePermission IRIs for project " in {
                actorUnderTest ! AdministrativePermissionIrisForProjectGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    SharedTestData.rootUserProfileV1
                )
                expectMsg(List(perm001.iri, perm003.iri))
            }
            "return AdministrativePermission object for IRI " in {
                actorUnderTest ! AdministrativePermissionGetRequestV1(
                    administrativePermissionIri = perm001.iri,
                    SharedTestData.rootUserProfileV1
                )
                expectMsg(Some(perm001.p))
            }
            "return DefaultObjectAccessPermission IRIs for project " in {
                actorUnderTest ! DefaultObjectAccessPermissionIrisForProjectGetRequestV1(
                    projectIri = IMAGES_PROJECT_IRI,
                    SharedTestData.rootUserProfileV1
                )
                expectMsg(List(perm002.iri))
            }
            "return DefaultObjectAccessPermission for IRI " in {
                actorUnderTest ! DefaultObjectAccessPermissionGetRequestV1(
                    defaultObjectAccessPermissionIri = perm002.iri,
                    SharedTestData.rootUserProfileV1
                )
                expectMsg(perm002.p)
            }
            "return user's administrative permissions " in {
                actorUnderTest ! GetUserAdministrativePermissionsRequestV1(multiuserUserProfileV1.projectGroups)
                expectMsg(multiuserUserProfileV1.projectAdministrativePermissions)
            }
            "return user's default object access permissions " in {
                actorUnderTest ! GetUserDefaultObjectAccessPermissionsRequestV1(multiuserUserProfileV1.projectGroups)
            }
        }
        "asked to create a permission object " should {
            "create and return an administrative permission " ignore {
                actorUnderTest ! AdministrativePermissionCreateRequestV1(
                    newAdministrativePermissionV1 = NewAdministrativePermissionV1(
                        iri = "http://data.knora.org/permissions/998",
                        forProject = IMAGES_PROJECT_IRI,
                        forGroup = OntologyConstants.KnoraBase.ProjectMember
                    ),
                    userProfileV1 = rootUserProfileV1
                )
            }
            "create and return a default object access permission " ignore {

            }
        }
        "asked to delete a permission object " should {
            "delete an administrative permission " ignore {

            }
            "delete a default object access permission " ignore {

            }
        }
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
    }
}
