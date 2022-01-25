/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice.inmem

import org.knora.webapi.UnitSpec
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM}
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * This spec is used to test [[org.knora.webapi.store.cacheservice.inmem.CacheServiceInMemImpl]].
 */
class CacheServiceInMemImplSpec extends UnitSpec() {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  private val user: UserADM = SharedTestDataADM.imagesUser01
  private val project: ProjectADM = SharedTestDataADM.imagesProject

  private val inMemCache: CacheServiceInMemImpl.type = CacheServiceInMemImpl

  "The CacheServiceInMemImpl" should {

    "successfully store a user" in {
      val resFuture = inMemCache.putUserADM(user)
      resFuture map { res => res should equal(true) }
    }

    "successfully retrieve a user by IRI" in {
      val resFuture = inMemCache.getUserADM(UserIdentifierADM(maybeIri = Some(user.id)))
      resFuture map { res => res should equal(Some(user)) }
    }

    "successfully retrieve a user by USERNAME" in {
      val resFuture = inMemCache.getUserADM(UserIdentifierADM(maybeUsername = Some(user.username)))
      resFuture map { res => res should equal(Some(user)) }
    }

    "successfully retrieve a user by EMAIL" in {
      val resFuture = inMemCache.getUserADM(UserIdentifierADM(maybeEmail = Some(user.email)))
      resFuture map { res => res should equal(Some(user)) }
    }

    "successfully store a project" in {
      val resFuture = inMemCache.putProjectADM(project)
      resFuture map { res => res should equal(true) }
    }

    "successfully retrieve a project by IRI" in {
      val resFuture = inMemCache.getProjectADM(ProjectIdentifierADM(maybeIri = Some(project.id)))
      resFuture map { res => res should equal(Some(project)) }
    }

    "successfully retrieve a project by SHORTNAME" in {
      val resFuture = inMemCache.getProjectADM(ProjectIdentifierADM(maybeShortname = Some(project.shortname)))
      resFuture map { res => res should equal(Some(project)) }
    }

    "successfully retrieve a project by SHORTCODE" in {
      val resFuture = inMemCache.getProjectADM(ProjectIdentifierADM(maybeShortcode = Some(project.shortcode)))
      resFuture map { res => res should equal(Some(project)) }
    }
  }
}
