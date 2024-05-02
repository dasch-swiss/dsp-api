/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.repo

import zio.Chunk
import zio.Ref
import zio.Task
import zio.ULayer
import zio.ZLayer

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.repo.AbstractInMemoryCrudRepository

final case class KnoraProjectRepoInMemory(projects: Ref[Chunk[KnoraProject]])
    extends AbstractInMemoryCrudRepository[KnoraProject, ProjectIri](projects, _.id)
    with KnoraProjectRepo {

  override def findByShortcode(shortcode: Shortcode): Task[Option[KnoraProject]] =
    projects.get.map(_.find(_.shortcode == shortcode))

  override def findByShortname(shortname: Shortname): Task[Option[KnoraProject]] =
    projects.get.map(_.find(_.shortname == shortname))
}

object KnoraProjectRepoInMemory {
  val layer: ULayer[KnoraProjectRepoInMemory] =
    ZLayer.fromZIO(Ref.make(Chunk.empty[KnoraProject]).map(KnoraProjectRepoInMemory(_)))
}
