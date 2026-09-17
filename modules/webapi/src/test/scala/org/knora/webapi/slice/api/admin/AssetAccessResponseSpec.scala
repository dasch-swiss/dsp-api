/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import org.junit.runner.RunWith
import zio.json.EncoderOps
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.AssetAccess
import org.knora.webapi.slice.admin.domain.model.MediaKind
import org.knora.webapi.slice.admin.domain.model.Permission
import org.knora.webapi.slice.admin.domain.model.RestrictedView

/**
 * Pins the wire vocabulary. The literals are the VRE's, deliberately not Sipi's `allow` / `restrict` / `deny`,
 * and `size` and `watermark` appear only beside `clamped` - exactly one of them.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class AssetAccessResponseSpec extends ZIOSpecDefault {

  private val restricted = Some(Permission.ObjectAccess.RestrictedView)

  private def wire(
    perm: Option[Permission.ObjectAccess],
    media: MediaKind,
    stored: Option[RestrictedView] = None,
  ) = AssetAccessResponse.from(AssetAccess.from(perm, media, stored)).toJson

  override def spec: Spec[Any, Any] = suite("AssetAccessResponse")(
    test("encode a full decision") {
      assertTrue(
        wire(Some(Permission.ObjectAccess.View), MediaKind.Audio) == """{"derivative":"full","original":"grant"}""",
      )
    },
    test("encode a denied decision") {
      assertTrue(wire(None, MediaKind.Audio) == """{"derivative":"denied","original":"withhold"}""")
    },
    test("encode a stream decision") {
      assertTrue(wire(restricted, MediaKind.Audio) == """{"derivative":"stream","original":"withhold"}""")
    },
    test("encode a clamped decision carrying an IIIF size string") {
      assertTrue(
        wire(restricted, MediaKind.RasterStillImage, Some(RestrictedView.Size.unsafeFrom("pct:30"))) ==
          """{"derivative":"clamped","original":"withhold","size":"pct:30"}""",
      )
    },
    test("encode a clamped decision carrying a watermark boolean, never a path") {
      assertTrue(
        wire(restricted, MediaKind.RasterStillImage, Some(RestrictedView.Watermark.On)) ==
          """{"derivative":"clamped","original":"withhold","watermark":true}""",
      )
    },
    test("deny an archive under restricted view") {
      assertTrue(wire(restricted, MediaKind.Archive) == """{"derivative":"denied","original":"withhold"}""")
    },
  )
}
