/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.api

import zio.*
import zio.nio.file.Files
import zio.test.*

import org.knora.webapi.slice.shacl.domain.ShaclValidator

object ShaclApiServiceSpec extends ZIOSpecDefault {

  private val shaclApiService = ZIO.serviceWithZIO[ShaclApiService]

  val spec: Spec[TestEnvironment & Scope, Any] = suite("ShaclApiService")(
    test("validate") {
      for {
        data    <- Files.createTempFile("data.ttl", None, Seq.empty)
        shacl   <- Files.createTempFile("shacl.ttl", None, Seq.empty)
        formData = ValidationFormData(data.toFile, shacl.toFile, None, None, None)
        result  <- shaclApiService(_.validate(formData))
      } yield assertTrue(result.contains("sh:conforms  true"))
    },
  ).provide(ShaclApiService.layer, ShaclValidator.layer)
}
