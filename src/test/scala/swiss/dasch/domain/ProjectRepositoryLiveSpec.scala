/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.util.TestUtils
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger

object ProjectRepositoryLiveSpec extends ZIOSpecDefault {

  private val repo = ZIO.serviceWithZIO[ProjectRepository]

  private val counter      = new AtomicInteger()
  private def newShortcode = ProjectShortcode.unsafeFrom(numberFormatter(counter.getAndIncrement()))
  private def numberFormatter(value: Int): String = {
    val hexString = value.toHexString
    "0" * (4 - hexString.length) + hexString
  }

  val spec = suite("ProjectRepositoryLive")(
    test("given an empty database should find nothing") {
      for {
        actual <- repo(_.findByShortcode(newShortcode))
      } yield assertTrue(actual.isEmpty)
    },
    test("findByShortcode") {
      for {
        prj    <- repo(_.addProject(newShortcode))
        actual <- repo(_.findByShortcode(prj.shortcode))
      } yield assertTrue(actual.contains(prj))
    },
    test("is advancing the id") {
      for {
        prj1 <- repo(_.addProject(newShortcode))
        prj2 <- repo(_.addProject(newShortcode))
      } yield assertTrue(prj1.id.value < prj2.id.value)
    },
    test("findById") {
      for {
        prj    <- repo(_.addProject(newShortcode))
        actual <- repo(_.findById(prj.id))
      } yield assertTrue(actual.contains(prj))
    },
    test("deleteProjectById") {
      for {
        prj    <- repo(_.addProject(newShortcode))
        _      <- repo(_.deleteById(prj.id))
        actual <- repo(_.findByShortcode(prj.shortcode))
      } yield assertTrue(actual.isEmpty)
    },
    test("deleteProjectByShortcode") {
      for {
        prj    <- repo(_.addProject(newShortcode))
        _      <- repo(_.deleteByShortcode(prj.shortcode))
        actual <- repo(_.findByShortcode(prj.shortcode))
      } yield assertTrue(actual.isEmpty)
    },
    test("is deleting multiple projects") {
      for {
        projects  <- ZIO.foreachPar(Chunk.fill(10)(newShortcode))(shortcode => repo(_.addProject(shortcode)))
        projectIds = projects.map(_.id)
        _         <- repo(_.deleteByIds(projectIds))
        notfound  <- repo(_.findByIds(projectIds))
      } yield assertTrue(notfound.isEmpty)
    },
    test("refuse to create a project twice") {
      val shortcode = newShortcode
      for {
        prj  <- repo(_.addProject(shortcode))
        exit <- repo(_.addProject(shortcode)).exit
      } yield assert(exit)(
        failsWithA[SQLException] &&
          fails(hasMessage(containsString("A UNIQUE constraint failed"))),
      )
    },
  ).provide(TestUtils.testDbLayerWithEmptyDb, ProjectRepositoryLive.layer)
}
