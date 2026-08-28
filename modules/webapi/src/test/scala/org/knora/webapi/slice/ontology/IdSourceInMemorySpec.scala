/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import java.util.UUID

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.ResourceIri

@RunWith(classOf[DspZTestJUnitRunner])
class IdSourceInMemorySpec extends ZIOSpecDefault {

  private val resource = ResourceIri.makeNew(Shortcode.unsafeFrom("9999"))
  private val idSource = ZIO.serviceWithZIO[IdSource]

  override def spec = suite("IdSourceInMemory")(
    test("mints sequential link-value IRIs whose value id becomes valueHasUUID") {
      for {
        a <- idSource(_.freshLinkValueIri(resource))
        b <- idSource(_.freshLinkValueIri(resource))
      } yield assertTrue(
        a.value == s"${resource.value}/values/1",
        b.value == s"${resource.value}/values/2",
        a.valueId.value == "1",
      )
    },
    test("mints sequential tag UUIDs on a counter independent of the link counter") {
      for {
        _  <- idSource(_.freshLinkValueIri(resource))
        u1 <- idSource(_.freshStandoffTagUuid)
        u2 <- idSource(_.freshStandoffTagUuid)
      } yield assertTrue(u1 == new UUID(0L, 1L), u2 == new UUID(0L, 2L))
    },
  ).provide(IdSourceInMemory.layer)
}
