/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import zio.test.*

import java.util.UUID

import dsp.valueobjects.UuidUtil

object GetStandoffTagByUUIDQuerySpec extends ZIOSpecDefault {

  private val testUuid = UUID.fromString("12345678-1234-1234-1234-123456789abc")

  override def spec: Spec[TestEnvironment, Any] = suite("GetStandoffTagByUUIDQuery")(
    test("should produce the expected SPARQL query") {
      val actual   = GetStandoffTagByUUIDQuery.build(testUuid).getQueryString
      val expected = UuidUtil.base64Encode(testUuid)
      assertTrue(
        actual ==
          s"""PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
             |SELECT DISTINCT ?standoffTag
             |WHERE { ?standoffTag knora-base:standoffTagHasUUID "$expected" . }
             |""".stripMargin,
      )
    },
  )
}
