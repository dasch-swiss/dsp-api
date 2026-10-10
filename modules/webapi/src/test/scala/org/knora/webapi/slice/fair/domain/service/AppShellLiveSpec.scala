/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain.service

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class AppShellLiveSpec extends ZIOSpecDefault {

  private val html = "<html><head></head><body></body></html>"

  private def counting(counter: Ref[Int], result: Option[String]): UIO[Option[String]] =
    counter.update(_ + 1).as(result)

  override val spec: Spec[Any, Any] = suite("AppShellLive")(
    suite("cached")(
      test("serves a success from the cache within its TTL and re-fetches after it") {
        for {
          calls <- Ref.make(0)
          shell <- AppShellLive.cached(counting(calls, Some(html)))
          a     <- shell.get
          _     <- TestClock.adjust(9.seconds)
          b     <- shell.get
          n1    <- calls.get
          _     <- TestClock.adjust(2.seconds)
          _     <- shell.get
          n2    <- calls.get
        } yield assertTrue(a == Some(html), b == Some(html), n1 == 1, n2 == 2)
      },
      test("caches a failure for 2 seconds and then retries") {
        for {
          calls <- Ref.make(0)
          shell <- AppShellLive.cached(counting(calls, None))
          a     <- shell.get
          _     <- TestClock.adjust(1.second)
          _     <- shell.get
          n1    <- calls.get
          _     <- TestClock.adjust(2.seconds)
          _     <- shell.get
          n2    <- calls.get
        } yield assertTrue(a.isEmpty, n1 == 1, n2 == 2)
      },
    ),
    suite("accept")(
      test("200 with </head> is accepted") {
        assertTrue(AppShellLive.accept(200, html) == Some(html))
      },
      test("200 without </head> is rejected") {
        assertTrue(AppShellLive.accept(200, "<html></html>").isEmpty)
      },
      test("404 is rejected") {
        assertTrue(AppShellLive.accept(404, html).isEmpty)
      },
    ),
  )
}
