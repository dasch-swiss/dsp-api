/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import zio.Chunk
import zio.Ref
import zio.Task
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.common.repo.service.Repository

final case class LicenseRepo(ref: Ref[Chunk[License]]) extends Repository[License, LicenseIri] {

  // The placeholder license is part of `License.BUILT_IN` but is only surfaced when the
  // `allow-placeholder` deployment policy is enabled. The flag is read at call time (like
  // `LegalInfoService` does) rather than when the layer is built, so `LicenseRepo.layer`
  // carries no implicit runtime-config-provider dependency and can be composed as a sibling
  // of `AppConfig.layer`.
  private def visible(licenses: Chunk[License]): Task[Chunk[License]] =
    AppConfig.features(_.allowPlaceholder).map { allow =>
      if allow then licenses else licenses.filterNot(_.id == LicenseIri.PLACEHOLDER)
    }

  override def findById(id: LicenseIri): Task[Option[License]] = ref.get.flatMap(visible).map(_.find(_.id == id))
  override def findAll(): Task[Chunk[License]]                 = ref.get.flatMap(visible)
  def findRecommendedLicenses(): Task[Chunk[License]] = ref.get.flatMap(visible).map(_.filter(_.isRecommended.toBoolean))
}

object LicenseRepo {
  val layer: ZLayer[Any, Nothing, LicenseRepo] = ZLayer.fromZIO(Ref.make(License.BUILT_IN).map(LicenseRepo.apply))
}
