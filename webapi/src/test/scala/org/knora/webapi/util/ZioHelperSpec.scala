/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import zio.*
import zio.test.*

object ZioHelperSpec extends ZIOSpecDefault {

  val spec: Spec[Any, Throwable] = suite("ZioHelper")(
    suite(".sequence(Map)")(
      test("should sequence a Map of ZIOs") {
        val map = Map(1 -> ZIO.succeed("one"), 2 -> ZIO.succeed("two"))
        for {
          actual <- ZioHelper.sequence(map)
        } yield assertTrue(actual == Map(1 -> "one", 2 -> "two"))
      },
      test("should sequence an empty Map") {
        val list: Map[Int, Task[String]] = Map.empty
        for {
          actual <- ZioHelper.sequence(list)
        } yield assertTrue(actual == Map.empty[Int, String])
      },
    ),
    suite(".sequence(List)")(
      test("should sequence a List of ZIOs") {
        val list = List(ZIO.succeed("1"), ZIO.succeed("2"))
        for {
          actual <- ZioHelper.sequence(list)
        } yield assertTrue(actual == List("1", "2"))
      },
      test("should sequence an empty List") {
        val list: List[Task[String]] = List.empty
        for {
          actual <- ZioHelper.sequence(list)
        } yield assertTrue(actual == List.empty[String])
      },
    ),
  )
}
