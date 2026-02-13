/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.bagit.domain

final case class Bag(
  version: String,
  encoding: String,
  manifests: List[Manifest],
  tagManifests: List[Manifest],
  bagInfo: Option[BagInfo],
  payloadFiles: List[PayloadPath],
)
