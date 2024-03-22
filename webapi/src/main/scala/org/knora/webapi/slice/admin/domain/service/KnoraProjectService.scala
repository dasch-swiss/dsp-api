/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.NonEmptyChunk
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.DuplicateValueException
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.RestrictedView

final case class KnoraProjectService(knoraProjectRepo: KnoraProjectRepo) {
  def findById(id: ProjectIri): Task[Option[KnoraProject]]           = knoraProjectRepo.findById(id)
  def findById(id: ProjectIdentifierADM): Task[Option[KnoraProject]] = knoraProjectRepo.findById(id)
  def findByShortcode(code: Shortcode): Task[Option[KnoraProject]]   = knoraProjectRepo.findByShortcode(code)
  def findAll(): Task[List[KnoraProject]]                            = knoraProjectRepo.findAll()
  def setProjectRestrictedView(project: KnoraProject, settings: RestrictedView): Task[RestrictedView] = {
    val newSettings = settings match {
      case RestrictedView.Watermark(false) => RestrictedView.default
      case s                               => s
    }
    knoraProjectRepo.save(project.copy(restrictedView = newSettings)).as(newSettings)
  }

  def createProject(req: ProjectCreateRequest): Task[KnoraProject] = for {
    _ <- ensureShortcodeIsUnique(req.shortcode)
    _ <- ensureShortnameIsUnique(req.shortname)
    _ <- ZIO
           .fail(new IllegalArgumentException("ProjectCreateRequest is not valid"))
           .when(req.description.isEmpty)
    projectIri = ProjectIri.makeNew
    project = KnoraProject(
                projectIri,
                req.shortname,
                req.shortcode,
                req.longname,
                NonEmptyChunk.fromIterable(req.description.head, req.description.tail),
                req.keywords,
                req.logo,
                req.status,
                req.selfjoin,
                RestrictedView.default,
              )
    project <- knoraProjectRepo.save(project)
  } yield project

  private def ensureShortcodeIsUnique(shortcode: Shortcode) =
    knoraProjectRepo
      .existsByShortcode(shortcode)
      .negate
      .filterOrFail(identity)(
        DuplicateValueException(s"Project with the shortcode: '${shortcode.value}' already exists"),
      )
  private def ensureShortnameIsUnique(shortname: Shortname) =
    knoraProjectRepo
      .existsByShortname(shortname)
      .negate
      .filterOrFail(identity)(
        DuplicateValueException(s"Project with the shortname: '${shortname.value}' already exists"),
      )
}

object KnoraProjectService {
  val layer = ZLayer.derive[KnoraProjectService]
}
