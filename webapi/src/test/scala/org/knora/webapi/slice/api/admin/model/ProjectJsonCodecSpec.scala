/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.model

import zio.json.*
import zio.test.*

import org.knora.webapi.TestDataFactory
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Lifecycle

object ProjectJsonCodecSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("Project JSON codec")(
    test("encodes lifecycle as a string field") {
      val draftJson     = TestDataFactory.someProjectADM.copy(lifecycle = Lifecycle.Draft).toJson
      val publishedJson = TestDataFactory.someProjectADM.copy(lifecycle = Lifecycle.Published).toJson
      assertTrue(
        draftJson.contains("\"lifecycle\":\"draft\""),
        publishedJson.contains("\"lifecycle\":\"published\""),
      )
    },
    test("round-trips through JSON") {
      val original = TestDataFactory.someProjectADM.copy(lifecycle = Lifecycle.Published)
      assertTrue(original.toJson.fromJson[Project] == Right(original))
    },
  )
}
