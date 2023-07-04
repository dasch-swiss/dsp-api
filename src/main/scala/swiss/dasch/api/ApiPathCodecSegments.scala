/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import zio.http.codec.HttpCodec.string
import zio.http.codec.PathCodec

object ApiPathCodecSegments {
  val projects: PathCodec[Unit]           = "projects"
  val shortcodePathVar: PathCodec[String] = string("shortcode")
}
