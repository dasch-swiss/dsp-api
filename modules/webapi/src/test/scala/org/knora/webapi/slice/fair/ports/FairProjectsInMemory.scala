/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.ports

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

final case class FairProjectsInMemory(
  projects: Map[Shortcode, FairProject] = Map.empty,
  licences: Map[String, FairLicence] = Map.empty,
) extends FairProjects {
  override def findByShortcode(shortcode: Shortcode): Task[Option[FairProject]] = ZIO.succeed(projects.get(shortcode))
  override def findLicence(licenceIri: String): Task[Option[FairLicence]]       = ZIO.succeed(licences.get(licenceIri))
}
