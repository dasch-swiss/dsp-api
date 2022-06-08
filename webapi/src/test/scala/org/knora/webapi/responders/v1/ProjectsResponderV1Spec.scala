/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * To be able to test UsersResponder, we need to be able to start UsersResponder isolated. Now the UsersResponder
 * extend ResponderV1 which messes up testing, as we cannot inject the TestActor system.
 */
package org.knora.webapi.responders.v1

import akka.actor.Status.Failure
import akka.testkit.ImplicitSender
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import dsp.errors.NotFoundException
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.sharedtestdata.SharedTestDataV1

import scala.concurrent.duration._

object ProjectsResponderV1Spec {

  val config: Config = ConfigFactory.parseString("""
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

        responderManager ! ProjectsGetRequestV1(
          featureFactoryConfig = defaultFeatureFactoryConfig,
          userProfile = Some(rootUserProfileV1)
        )
        val received = expectMsgType[ProjectsResponseV1](timeout)

        assert(received.projects.contains(SharedTestDataV1.imagesProjectInfo))
        assert(received.projects.contains(SharedTestDataV1.incunabulaProjectInfo))
      }

      "return information about a project identified by IRI" in {

        /* Incunabula project */
        responderManager ! ProjectInfoByIRIGetRequestV1(
          iri = SharedTestDataV1.incunabulaProjectInfo.id,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          userProfileV1 = Some(SharedTestDataV1.rootUser)
        )
        expectMsg(ProjectInfoResponseV1(SharedTestDataV1.incunabulaProjectInfo))

        /* Images project */
        responderManager ! ProjectInfoByIRIGetRequestV1(
          iri = SharedTestDataV1.imagesProjectInfo.id,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          userProfileV1 = Some(SharedTestDataV1.rootUser)
        )
        expectMsg(ProjectInfoResponseV1(SharedTestDataV1.imagesProjectInfo))

        /* 'SystemProject' */
        responderManager ! ProjectInfoByIRIGetRequestV1(
          iri = SharedTestDataV1.systemProjectInfo.id,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          userProfileV1 = Some(SharedTestDataV1.rootUser)
        )
        expectMsg(ProjectInfoResponseV1(SharedTestDataV1.systemProjectInfo))

      }

      "return information about a project identified by shortname" in {
        responderManager ! ProjectInfoByShortnameGetRequestV1(
          SharedTestDataV1.incunabulaProjectInfo.shortname,
          featureFactoryConfig = defaultFeatureFactoryConfig,
          Some(rootUserProfileV1)
        )
        expectMsg(ProjectInfoResponseV1(SharedTestDataV1.incunabulaProjectInfo))
      }

      "return 'NotFoundException' when the project IRI is unknown" in {

        responderManager ! ProjectInfoByIRIGetRequestV1(
          iri = "http://rdfh.ch/projects/notexisting",
          featureFactoryConfig = defaultFeatureFactoryConfig,
          userProfileV1 = Some(rootUserProfileV1)
        )
        expectMsg(Failure(NotFoundException(s"Project 'http://rdfh.ch/projects/notexisting' not found")))

      }

      "return 'NotFoundException' when the project shortname unknown " in {
        responderManager ! ProjectInfoByShortnameGetRequestV1(
          shortname = "projectwrong",
          featureFactoryConfig = defaultFeatureFactoryConfig,
          userProfileV1 = Some(rootUserProfileV1)
        )
        expectMsg(Failure(NotFoundException(s"Project 'projectwrong' not found")))
      }
    }
  }

}
