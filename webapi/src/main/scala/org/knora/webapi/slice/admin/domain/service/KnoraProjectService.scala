/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.Chunk
import zio.NonEmptyChunk
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.DuplicateValueException
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Description
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.RestrictedView

final case class KnoraProjectService(knoraProjectRepo: KnoraProjectRepo) {
  def findById(id: ProjectIri): Task[Option[KnoraProject]]         = knoraProjectRepo.findById(id)
  def existsById(id: ProjectIri): Task[Boolean]                    = knoraProjectRepo.existsById(id)
  def findByShortcode(code: Shortcode): Task[Option[KnoraProject]] = knoraProjectRepo.findByShortcode(code)
  def findByShortname(code: Shortname): Task[Option[KnoraProject]] = knoraProjectRepo.findByShortname(code)
  def findAll(): Task[Chunk[KnoraProject]]                         = knoraProjectRepo.findAll()
  def setProjectRestrictedView(project: KnoraProject, settings: RestrictedView): Task[RestrictedView] = {
    val newSettings = settings match {
      case RestrictedView.Watermark(false) => RestrictedView.default
      case s                               => s
    }
    knoraProjectRepo.save(project.copy(restrictedView = newSettings)).as(newSettings)
  }

  def createProject(req: ProjectCreateRequest): Task[KnoraProject] = for {
    _            <- ensureShortcodeIsUnique(req.shortcode)
    _            <- ensureShortnameIsUnique(req.shortname)
    descriptions <- toNonEmptyChunk(req.description)
    project = KnoraProject(
                req.id.getOrElse(ProjectIri.makeNew),
                req.shortname,
                req.shortcode,
                req.longname,
                descriptions,
                req.keywords,
                req.logo,
                req.status,
                req.selfjoin,
                RestrictedView.default,
              )
    project <- knoraProjectRepo.save(project)
  } yield project

  private def toNonEmptyChunk(descriptions: List[Description]) =
    ZIO
      .fail(new IllegalArgumentException("Descriptions may not be empty"))
      .when(descriptions.isEmpty)
      .as(NonEmptyChunk.fromIterable(descriptions.head, descriptions.tail))

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

  def updateProject(project: KnoraProject, updateReq: ProjectUpdateRequest): Task[KnoraProject] =
    for {
      desc <- updateReq.description match {
                case Some(value) => toNonEmptyChunk(value).map(Some(_))
                case None        => ZIO.none
              }
      _ <- updateReq.shortname match {
             case Some(value) => ensureShortnameIsUnique(value)
             case None        => ZIO.unit
           }
      updated <- knoraProjectRepo.save(
                   project.copy(
                     longname = updateReq.longname.orElse(project.longname),
                     description = desc.getOrElse(project.description),
                     keywords = updateReq.keywords.getOrElse(project.keywords),
                     logo = updateReq.logo.orElse(project.logo),
                     status = updateReq.status.getOrElse(project.status),
                     selfjoin = updateReq.selfjoin.getOrElse(project.selfjoin),
                   ),
                 )
    } yield updated
}

object KnoraProjectService {
  val layer = ZLayer.derive[KnoraProjectService]
}
