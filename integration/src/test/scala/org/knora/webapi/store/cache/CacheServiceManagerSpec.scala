/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache

import dsp.errors.BadRequestException
import org.knora.webapi.*
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.messages.store.cacheservicemessages.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * This spec is used to test [[org.knora.webapi.store.cache.serialization.CacheSerialization]].
 */
class CacheServiceManagerSpec extends CoreSpec {

  val project = SharedTestDataADM.imagesProject

  "The CacheManager" should {

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
