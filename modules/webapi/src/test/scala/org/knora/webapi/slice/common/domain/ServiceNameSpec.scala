/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.domain

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class ServiceNameSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Any] = suite("ServiceName")(
    test("accepts lower-case letters, digits and hyphens") {
      assertTrue(ServiceName.from("dsp-repository-2").map(_.value) == Right("dsp-repository-2"))
    },
    test("rejects an empty, upper-case, blank or punctuated name") {
      val invalid = List("", " ", "Dsp-Repository", "dsp_repository", "dsp.repository", "dsp repository", "dsp\n")
      assertTrue(invalid.forall(ServiceName.from(_).isLeft))
    },
  )
}
