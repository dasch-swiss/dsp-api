/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache

import dsp.errors.BadRequestException
import org.knora.webapi.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.messages.store.cacheservicemessages.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.*

/**
 * This spec is used to test [[org.knora.webapi.store.cache.serialization.CacheSerialization]].
 */
class CacheServiceManagerSpec extends CoreSpec {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val user    = SharedTestDataADM.imagesUser01
  val project = SharedTestDataADM.imagesProject

  "The CacheManager" should {

    "successfully store a user" in {
      appActor ! CacheServicePutUserADM(user)
      expectMsg(())
    }

    "successfully retrieve a user by IRI" in {
      appActor ! CacheServiceGetUserByIriADM(UserIri.unsafeFrom(user.id))
      expectMsg(Some(user))
    }

    "successfully retrieve a user by USERNAME" in {
      appActor ! CacheServiceGetUserByUsernameADM(Username.unsafeFrom(user.username))
      expectMsg(Some(user))
    }

    "successfully retrieve a user by EMAIL" in {
      appActor ! CacheServiceGetUserByEmailADM(Email.unsafeFrom(user.email))
      expectMsg(Some(user))
    }

    "successfully store a project" in {
      appActor ! CacheServicePutProjectADM(project)
      expectMsg(())
    }

    "successfully retrieve a project by IRI" in {
      appActor ! CacheServiceGetProjectADM(
        IriIdentifier
          .fromString(project.id)
          .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
      )
      expectMsg(Some(project))

    }

    "successfully retrieve a project by SHORTNAME" in {
      appActor ! CacheServiceGetProjectADM(
        ShortnameIdentifier
          .fromString(project.shortname)
          .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
      )
      expectMsg(Some(project))

    }

    "successfully retrieve a project by SHORTCODE" in {
      appActor ! CacheServiceGetProjectADM(
        ShortcodeIdentifier
          .fromString(project.shortcode)
          .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
      )
      expectMsg(Some(project))
    }
  }
}
