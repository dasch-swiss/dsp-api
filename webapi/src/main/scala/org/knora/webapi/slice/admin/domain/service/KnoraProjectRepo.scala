/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Task

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.common.repo.service.Repository

trait KnoraProjectRepo extends Repository[KnoraProject, ProjectIri] {
  def findById(id: ProjectIdentifierADM): Task[Option[KnoraProject]] =
    id match {
      case ProjectIdentifierADM.IriIdentifier(value)       => findById(value)
      case ProjectIdentifierADM.ShortcodeIdentifier(value) => findByShortcode(value)
      case ProjectIdentifierADM.ShortnameIdentifier(value) => findByShortname(value)
    }
  def findByShortcode(shortcode: Shortcode): Task[Option[KnoraProject]]
  def findByShortname(shortname: Shortname): Task[Option[KnoraProject]]
  def existsByShortcode(shortcode: Shortcode): Task[Boolean] = findByShortcode(shortcode).map(_.isDefined)
  def existsByShortname(shortname: Shortname): Task[Boolean] = findByShortname(shortname).map(_.isDefined)
  def save(project: KnoraProject): Task[KnoraProject]
}
