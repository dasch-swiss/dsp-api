/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.ports

import zio.UIO

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

trait FairAssets {

  /** The asset's sidecar facts; None when the sidecar is missing or unreadable. Never fails. */
  def findSidecar(shortcode: Shortcode, assetId: String): UIO[Option[FairAsset]]
}

final case class FairAsset(mimeType: Option[String], fileName: Option[String], size: Option[Long])
