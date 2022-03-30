/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cacheservice.impl

import org.knora.webapi.UnitSpec
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM}
import org.knora.webapi.sharedtestdata.SharedTestDataADM

import zio.test._
import zio.test.Assertion._
import zio.test.TestAspect.{ignore, timeout}
import org.knora.webapi.store.cacheservice.api.CacheService

/**
 * This spec is used to test [[org.knora.webapi.store.cacheservice.impl.CacheServiceInMemImpl]].
 */
object CacheInMemImplSpec extends DefaultRunnableSpec {

  StringFormatter.initForTest()
  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val user: UserADM       = SharedTestDataADM.imagesUser01
  private val project: ProjectADM = SharedTestDataADM.imagesProject

  def spec = suite("CacheInMemImplSpec")(
    test("successfully store a user and retrieve by IRI") {
      for {
        _             <- CacheService(_.putUserADM(user))
        retrievedUser <- CacheService(_.getUserADM(UserIdentifierADM(maybeIri = Some(user.id))))
      } yield assert(retrievedUser)(equalTo(Some(user)))
    } +
      test("successfully store a user and retrieve by USERNAME")(
        for {
          _             <- CacheService(_.putUserADM(user))
          retrievedUser <- CacheService(_.getUserADM(UserIdentifierADM(maybeUsername = Some(user.username))))
        } yield assert(retrievedUser)(equalTo(Some(user)))
      ) +
      test("successfully store a user and retrieve by EMAIL")(
        for {
          _             <- CacheService(_.putUserADM(user))
          retrievedUser <- CacheService(_.getUserADM(UserIdentifierADM(maybeEmail = Some(user.email))))
        } yield assert(retrievedUser)(equalTo(Some(user)))
      ) +
      test("successfully store a project and retrieve by IRI")(
        for {
          _                <- CacheService(_.putProjectADM(project))
          retrievedProject <- CacheService(_.getProjectADM(ProjectIdentifierADM(maybeIri = Some(project.id))))
        } yield assert(retrievedProject)(equalTo(Some(project)))
      ) +
      test("successfully store a project and retrieve by SHORTCODE")(
        for {
          _ <- CacheService(_.putProjectADM(project))
          retrievedProject <-
            CacheService(_.getProjectADM(ProjectIdentifierADM(maybeShortcode = Some(project.shortcode))))
        } yield assert(retrievedProject)(equalTo(Some(project)))
      ) +
      test("successfully store a project and retrieve by SHORTNAME")(
        for {
          _ <- CacheService(_.putProjectADM(project))
          retrievedProject <-
            CacheService(_.getProjectADM(ProjectIdentifierADM(maybeShortname = Some(project.shortname))))
        } yield assert(retrievedProject)(equalTo(Some(project)))
      )
  ).provide(CacheServiceInMemImpl.layer)

  // "The CacheInMemImpl" should {

  //   "successfully store a user" in {
  //     val resFuture = inMemCache.putUserADM(user)
  //     resFuture map { res => res should equal(true) }
  //   }

  //   "successfully retrieve a user by IRI" in {
  //     val resFuture = inMemCache.getUserADM(UserIdentifierADM(maybeIri = Some(user.id)))
  //     resFuture map { res => res should equal(Some(user)) }
  //   }

  //   "successfully retrieve a user by USERNAME" in {
  //     val resFuture = inMemCache.getUserADM(UserIdentifierADM(maybeUsername = Some(user.username)))
  //     resFuture map { res => res should equal(Some(user)) }
  //   }

  //   "successfully retrieve a user by EMAIL" in {
  //     val resFuture = inMemCache.getUserADM(UserIdentifierADM(maybeEmail = Some(user.email)))
  //     resFuture map { res => res should equal(Some(user)) }
  //   }

  //   "successfully store a project" in {
  //     val resFuture = inMemCache.putProjectADM(project)
  //     resFuture map { res => res should equal(true) }
  //   }

  //   "successfully retrieve a project by IRI" in {
  //     val resFuture = inMemCache.getProjectADM(ProjectIdentifierADM(maybeIri = Some(project.id)))
  //     resFuture map { res => res should equal(Some(project)) }
  //   }

  //   "successfully retrieve a project by SHORTNAME" in {
  //     val resFuture = inMemCache.getProjectADM(ProjectIdentifierADM(maybeShortname = Some(project.shortname)))
  //     resFuture map { res => res should equal(Some(project)) }
  //   }

  //   "successfully retrieve a project by SHORTCODE" in {
  //     val resFuture = inMemCache.getProjectADM(ProjectIdentifierADM(maybeShortcode = Some(project.shortcode)))
  //     resFuture map { res => res should equal(Some(project)) }
  //   }
  // }
}
