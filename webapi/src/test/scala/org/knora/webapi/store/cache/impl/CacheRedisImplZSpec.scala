/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache.impl

import zio._
import zio.test.Assertion._
import zio.test._

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.cache.config.RedisTestConfig
import org.knora.webapi.testcontainers.RedisTestContainer
import dsp.errors.BadRequestException

/**
 * This spec is used to test [[org.knora.webapi.store.cache.impl.CacheServiceRedisImpl]].
 */
object CacheRedisImplZSpec extends ZIOSpecDefault {

  StringFormatter.initForTest()
  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val user: UserADM       = SharedTestDataADM.imagesUser01
  val project: ProjectADM = SharedTestDataADM.imagesProject

  /**
   * Defines a layer which encompases all dependencies that are needed for
   * for running the tests.
   */
  val testLayers = ZLayer.make[CacheService & zio.test.Annotations](
    CacheServiceRedisImpl.layer,
    RedisTestConfig.redisTestContainer,
    zio.test.Annotations.live,
    RedisTestContainer.layer
  )

  def spec = (userTests + projectTests).provideLayerShared(testLayers) @@ TestAspect.sequential

  val userTests = suite("CacheRedisImplSpec - user")(
    test("successfully store a user and retrieve by IRI") {
      for {
        _             <- CacheService.putUserADM(user)
        retrievedUser <- CacheService.getUserADM(UserIdentifierADM(maybeIri = Some(user.id)))
      } yield assert(retrievedUser)(equalTo(Some(user)))
    } @@ TestAspect.ignore +
      test("successfully store a user and retrieve by USERNAME")(
        for {
          _             <- CacheService.putUserADM(user)
          retrievedUser <- CacheService.getUserADM(UserIdentifierADM(maybeUsername = Some(user.username)))
        } yield assert(retrievedUser)(equalTo(Some(user)))
      ) @@ TestAspect.ignore +
      test("successfully store a user and retrieve by EMAIL")(
        for {
          _             <- CacheService.putUserADM(user)
          retrievedUser <- CacheService.getUserADM(UserIdentifierADM(maybeEmail = Some(user.email)))
        } yield assert(retrievedUser)(equalTo(Some(user)))
      ) @@ TestAspect.ignore
  )

  val projectTests = suite("CacheRedisImplSpec - project")(
    test("successfully store a project and retrieve by IRI")(
      for {
        _ <- CacheService.putProjectADM(project)
        retrievedProject <- CacheService.getProjectADM(
                              IriIdentifier
                                .fromString(project.id)
                                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
                            )
      } yield assert(retrievedProject)(equalTo(Some(project)))
    ) +
      test("successfully store a project and retrieve by SHORTCODE")(
        for {
          _ <- CacheService.putProjectADM(project)
          retrievedProject <-
            CacheService.getProjectADM(
              ShortcodeIdentifier
                .fromString(project.shortcode)
                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
            )
        } yield assert(retrievedProject)(equalTo(Some(project)))
      ) +
      test("successfully store a project and retrieve by SHORTNAME")(
        for {
          _ <- CacheService.putProjectADM(project)
          retrievedProject <-
            CacheService.getProjectADM(
              ShortnameIdentifier
                .fromString(project.shortname)
                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
            )
        } yield assert(retrievedProject)(equalTo(Some(project)))
      )
  )
}
