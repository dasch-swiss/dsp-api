/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache

import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceGetProjectADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceGetUserADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServicePutProjectADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServicePutUserADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM

object CacheServiceManagerSpec {
  val config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * This spec is used to test [[org.knora.webapi.store.cacheservice.serialization.CacheSerialization]].
 */
class CacheServiceManagerSpec extends CoreSpec(CacheServiceManagerSpec.config) {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val user    = SharedTestDataADM.imagesUser01
  val project = SharedTestDataADM.imagesProject

  "The CacheManager" should {

    "successfully store a user" in {
      storeManager ! CacheServicePutUserADM(user)
      expectMsg(())
    }

    "successfully retrieve a user by IRI" in {
      storeManager ! CacheServiceGetUserADM(UserIdentifierADM(maybeIri = Some(user.id)))
      expectMsg(Some(user))
    }

    "successfully retrieve a user by USERNAME" in {
      storeManager ! CacheServiceGetUserADM(UserIdentifierADM(maybeUsername = Some(user.username)))
      expectMsg(Some(user))
    }

    "successfully retrieve a user by EMAIL" in {
      storeManager ! CacheServiceGetUserADM(UserIdentifierADM(maybeEmail = Some(user.email)))
      expectMsg(Some(user))
    }

    "successfully store a project" in {
      storeManager ! CacheServicePutProjectADM(project)
      expectMsg(())
    }

    "successfully retrieve a project by IRI" in {
      storeManager ! CacheServiceGetProjectADM(ProjectIdentifierADM(maybeIri = Some(project.id)))
      expectMsg(Some(project))
    }

    "successfully retrieve a project by SHORTNAME" in {
      storeManager ! CacheServiceGetProjectADM(ProjectIdentifierADM(maybeShortname = Some(project.shortname)))
      expectMsg(Some(project))
    }

    "successfully retrieve a project by SHORTCODE" in {
      storeManager ! CacheServiceGetProjectADM(ProjectIdentifierADM(maybeShortcode = Some(project.shortcode)))
      expectMsg(Some(project))
    }
  }
}
