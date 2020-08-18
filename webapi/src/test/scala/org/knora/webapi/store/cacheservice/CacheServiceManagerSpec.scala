/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.cacheservice

import com.typesafe.config.ConfigFactory
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.store.cacheservicemessages.{CacheServiceGetProjectADM, CacheServiceGetUserADM, CacheServicePutProjectADM, CacheServicePutUserADM}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.sharedtestdata.SharedTestDataADM

object CacheServiceManagerSpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * This spec is used to test [[org.knora.webapi.store.cacheservice.CacheSerialization]].
  */
class CacheServiceManagerSpec extends CoreSpec(CacheServiceManagerSpec.config) {

    implicit protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val user = SharedTestDataADM.imagesUser01
    val project = SharedTestDataADM.imagesProject

    "The RedisManager" should {

        "successfully store a user" in {
            storeManager ! CacheServicePutUserADM(user)
            expectMsg(true)
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
            expectMsg(true)
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
