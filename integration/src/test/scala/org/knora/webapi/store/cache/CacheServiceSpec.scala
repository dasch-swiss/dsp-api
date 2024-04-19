/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.cache

import zio.ZIO
import zio.test._

import org.knora.webapi.messages.admin.responder.projectsmessages.Project
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.sharedtestdata.SharedTestDataADM

object CacheServiceSpec extends ZIOSpecDefault {

  val project: Project = SharedTestDataADM.imagesProject

  private val cacheService = ZIO.serviceWithZIO[CacheService]

  private val projectTests = suite("ProjectADM")(
    test("successfully store a project and retrieve by IRI")(
      for {
        _                <- cacheService(_.putProjectADM(project))
        retrievedProject <- cacheService(_.getProjectADM(IriIdentifier.from(project.projectIri)))
      } yield assertTrue(retrievedProject.contains(project)),
    ),
    test("successfully store a project and retrieve by SHORTCODE")(
      for {
        _                <- cacheService(_.putProjectADM(project))
        retrievedProject <- cacheService(_.getProjectADM(ShortcodeIdentifier.from(project.getShortcode)))
      } yield assertTrue(retrievedProject.contains(project)),
    ),
    test("successfully store a project and retrieve by SHORTNAME")(
      for {
        _                <- cacheService(_.putProjectADM(project))
        retrievedProject <- cacheService(_.getProjectADM(ShortnameIdentifier.from(project.getShortname)))
      } yield assertTrue(retrievedProject.contains(project)),
    ),
  )

  private val clearCacheSuite = suite("clearCache")(
    test("successfully clears the cache")(
      for {
        _                  <- cacheService(_.putProjectADM(project))
        _                  <- cacheService(_.clearCache())
        projectByIri       <- cacheService(_.getProjectADM(IriIdentifier.from(project.projectIri)))
        projectByShortcode <- cacheService(_.getProjectADM(ShortcodeIdentifier.from(project.getShortcode)))
        projectByShortname <- cacheService(_.getProjectADM(ShortnameIdentifier.from(project.getShortname)))
      } yield assertTrue(
        projectByIri.isEmpty,
        projectByShortcode.isEmpty,
        projectByShortname.isEmpty,
      ),
    ),
  )

  def spec: Spec[Any, Throwable] =
    suite("CacheServiceLive")(projectTests, clearCacheSuite).provide(CacheService.layer)
}
