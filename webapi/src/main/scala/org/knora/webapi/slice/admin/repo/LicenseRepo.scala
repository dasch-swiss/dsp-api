/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo

import zio.Chunk
import zio.Ref
import zio.Task
import zio.ZLayer

import java.net.URI

import org.knora.webapi.slice.admin.domain.model.License
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.common.repo.service.Repository

final case class LicenseRepo(ref: Ref[Chunk[License]]) extends Repository[License, LicenseIri] {
  override def findById(id: LicenseIri): Task[Option[License]] = ref.get.map(_.find(_.id == id))
  override def findAll(): Task[Chunk[License]]                 = ref.get
}

object LicenseRepo {
  def layer = ZLayer.fromZIO(Ref.make(AllowedLicenses.all).map(LicenseRepo.apply))
}

object AllowedLicenses {
  val all: Chunk[License] = Chunk(
    License.unsafeFrom(
      LicenseIri.unsafeFrom("http://rdfh.ch/licenses/heUgoYutSxWm2Rc7Gc1J5g"),
      URI.create("https://creativecommons.org/licenses/by/4.0/"),
      "CC BY 4.0",
    ),
    License.unsafeFrom(
      LicenseIri.unsafeFrom("http://rdfh.ch/licenses/iAMRMWz7QCihyjKTToiOxQ"),
      URI.create("https://creativecommons.org/licenses/by-sa/4.0/"),
      "CC BY-SA 4.0",
    ),
    License.unsafeFrom(
      LicenseIri.unsafeFrom("http://rdfh.ch/licenses/jfTnk7gSTLCfbaG1jw7TxQ"),
      URI.create("https://creativecommons.org/licenses/by-nc/4.0/"),
      "CC BY-NC 4.0",
    ),
    License.unsafeFrom(
      LicenseIri.unsafeFrom("http://rdfh.ch/licenses/ImEbZ_1RQXuZ1vBP6h9Y1g"),
      URI.create("https://creativecommons.org/licenses/by-nc-sa/4.0/"),
      "CC BY-NC-SA 4.0",
    ),
    License.unsafeFrom(
      LicenseIri.unsafeFrom("http://rdfh.ch/licenses/G4aqIIoZQ_a185x73y8Neg"),
      URI.create("https://creativecommons.org/licenses/by-nd/4.0/"),
      "CC BY-ND 4.0",
    ),
    License.unsafeFrom(
      LicenseIri.unsafeFrom("http://rdfh.ch/licenses/wxaLnc8mT4-HqLv06zMDrA"),
      URI.create("https://creativecommons.org/licenses/by-nc-nd/4.0/"),
      "CC BY-NC-ND 4.0",
    ),
    License.unsafeFrom(
      LicenseIri.unsafeFrom("http://rdfh.ch/licenses/u27qfxzgSSWxNA_1o9_VQg"),
      URI.create("http://example.com/"),
      "Produced by an AI - Not Protected by Copyright",
    ),
    License.unsafeFrom(
      LicenseIri.unsafeFrom("http://rdfh.ch/licenses/B1pkLhbcSCioh_2NothNNQ"),
      URI.create("http://example.com/"),
      "Unknown License - Ask Copyright Holder for Permission",
    ),
    License.unsafeFrom(
      LicenseIri.unsafeFrom("http://rdfh.ch/licenses/noWBM260TbS3laZ5yxKZSQ"),
      URI.create("http://example.com/"),
      "Public Domain - Not Protected by Copyright",
    ),
  )
}
