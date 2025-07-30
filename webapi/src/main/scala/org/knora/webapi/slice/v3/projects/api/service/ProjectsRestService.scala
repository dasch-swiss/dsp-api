/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.v3.projects.api.service

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.v3.projects.api.model.ProjectsDto.ProjectResponseDto
import org.knora.webapi.slice.v3.projects.api.model.ProjectsDto.ProjectShortcodeParam
import org.knora.webapi.slice.v3.projects.api.model.ProjectsDto.ResourceCountsResponseDto
import org.knora.webapi.slice.v3.projects.api.model.V3InvalidProjectIdException
import org.knora.webapi.slice.v3.projects.api.model.V3ProjectNotFoundException
import org.knora.webapi.slice.v3.projects.domain.service.ProjectsService

final case class ProjectsRestService(
  projectsService: ProjectsService,
) {

  def findProjectByShortcode(shortcode: ProjectShortcodeParam): Task[ProjectResponseDto] =
    for {
      projectIri <- findProjectIri(shortcode)
      project <-
        projectsService.findProjectInfoByIri(projectIri).someOrFail(V3ProjectNotFoundException(shortcode.value))
      _ <- projectsService.warmResourceCountsCache(projectIri)
      _ <- ZIO.logDebug(s"Cache Reload Triggered for project ${projectIri.value}")
    } yield ProjectResponseDto.from(project)

  def findResourceCountsByShortcode(shortcode: ProjectShortcodeParam): Task[ResourceCountsResponseDto] =
    for {
      projectIri <- findProjectIri(shortcode)
      counts <-
        projectsService.findResourceCountsById(projectIri).someOrFail(V3ProjectNotFoundException(shortcode.value))
    } yield ResourceCountsResponseDto.from(counts)

  private def findProjectIri(shortcode: ProjectShortcodeParam): Task[ProjectIri] =
    for {
      knoraShortcode <-
        ZIO
          .fromEither(shortcode.toDomain.flatMap(domainShortcode => Shortcode.from(domainShortcode.value)))
          .mapError(_ => V3InvalidProjectIdException(shortcode.value, "Must be a 4-character hexadecimal shortcode"))
      projectIri <- projectsService.findProjectIriByShortcode(knoraShortcode)
    } yield projectIri
}

object ProjectsRestService {
  val layer = ZLayer.derive[ProjectsRestService]
}
