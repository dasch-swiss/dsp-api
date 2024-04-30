/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.NonEmptyChunk
import zio.Task

import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Keyword
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Longname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.common.repo.service.Repository

trait KnoraProjectRepo extends Repository[KnoraProject, ProjectIri] {
  def save(project: KnoraProject): Task[KnoraProject]
  def findByShortcode(shortcode: Shortcode): Task[Option[KnoraProject]]
  def findByShortname(shortname: Shortname): Task[Option[KnoraProject]]

  final def existsByShortcode(shortcode: Shortcode): Task[Boolean] =
    findByShortcode(shortcode).map(_.isDefined)
  final def existsByShortname(shortname: Shortname): Task[Boolean] =
    findByShortname(shortname).map(_.isDefined)
}

object KnoraProjectRepo {
  object builtIn {
    private def makeBuiltIn(
      name: String,
      shortcode: String,
      longname: String,
      description: String,
    ) = KnoraProject(
      ProjectIri.unsafeFrom("http://www.knora.org/ontology/knora-admin#" + name),
      Shortname.unsafeFrom(name),
      Shortcode.unsafeFrom(shortcode),
      Some(Longname.unsafeFrom(longname)),
      NonEmptyChunk(KnoraProject.Description.unsafeFrom(description, Some("en"))),
      List.empty[Keyword],
      None,
      KnoraProject.Status.Active,
      KnoraProject.SelfJoin.CannotJoin,
      RestrictedView.default,
    )

    val SystemProject: KnoraProject = makeBuiltIn(
      "SystemProject",
      "FFFF",
      "Knora System Project",
      "Knora System Project",
    )

    val DefaultSharedOntologiesProject: KnoraProject = makeBuiltIn(
      "DefaultSharedOntologiesProject",
      "0000",
      "Knora Default Shared Ontologies Project",
      "Knora Shared Ontologies Project",
    )

    val all: Chunk[KnoraProject] = Chunk(SystemProject, DefaultSharedOntologiesProject)

    def findOneBy(p: KnoraProject => Boolean): Option[KnoraProject] = all.find(p)

    def findAllBy(p: KnoraProject => Boolean): Chunk[KnoraProject] = all.filter(p)
  }
}
