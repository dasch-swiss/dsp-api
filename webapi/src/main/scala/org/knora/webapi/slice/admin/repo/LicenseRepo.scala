/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import zio.Chunk
import zio.Ref
import zio.Task
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.common.repo.service.Repository

final case class LicenseRepo(ref: Ref[Chunk[License]]) extends Repository[License, LicenseIri] {
  override def findById(id: LicenseIri): Task[Option[License]] = ref.get.map(_.find(_.id == id))
  override def findAll(): Task[Chunk[License]]                 = ref.get
  def findRecommendedLicenses(): Task[Chunk[License]]          = ref.get.map(_.filter(_.isRecommended.toBoolean))
}

object LicenseRepo {
  def layer = ZLayer.fromZIO(Ref.make(License.BUILT_IN).map(LicenseRepo.apply))
}
