/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import org.knora.webapi.slice.admin.domain.model.CopyrightHolder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Description
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.LicenseIri
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

final case class KnoraProjectService(knoraProjectRepo: KnoraProjectRepo, ontologyRepo: OntologyRepo) {
  def findById(id: ProjectIri): Task[Option[KnoraProject]] = knoraProjectRepo.findById(id)
  def existsById(id: ProjectIri): Task[Boolean]            = knoraProjectRepo.existsById(id)
  def enableLicense(license: LicenseIri, project: KnoraProject): Task[KnoraProject] =
    withProjectFromDb(project.id) { project =>
      knoraProjectRepo.save(project.copy(enabledLicenses = project.enabledLicenses.incl(license)))
    }
  def disableLicense(license: LicenseIri, project: KnoraProject): Task[KnoraProject] =
    withProjectFromDb(project.id) { project =>
      knoraProjectRepo.save(project.copy(enabledLicenses = project.enabledLicenses.excl(license)))
    }
  def findByShortcode(code: Shortcode): Task[Option[KnoraProject]] = knoraProjectRepo.findByShortcode(code)
  def findByShortname(code: Shortname): Task[Option[KnoraProject]] = knoraProjectRepo.findByShortname(code)
  def findAll(): Task[Chunk[KnoraProject]]                         = knoraProjectRepo.findAll()
  def setProjectRestrictedView(project: KnoraProject, settings: RestrictedView): Task[RestrictedView] =
    withProjectFromDb(project.id) { project =>
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
                Set.empty,
                LicenseIri.BUILT_IN,
              )
    project <- knoraProjectRepo.save(project)
  } yield project

  private def withProjectFromDb[A](id: ProjectIri)(task: KnoraProject => Task[A]): Task[A] =
    knoraProjectRepo
      .findById(id)
      .someOrFail(new IllegalArgumentException(s"Project with id: $id not found"))
      .flatMap(task)

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

  def erase(project: KnoraProject): Task[Unit] = knoraProjectRepo.delete(project)

  def updateProject(project: KnoraProject, updateReq: ProjectUpdateRequest): Task[KnoraProject] =
    withProjectFromDb(project.id) { project =>
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

  def getNamedGraphsForProject(project: KnoraProject): Task[List[InternalIri]] =
    getOntologyGraphsForProject(project).map(_ :+ getDataGraphForProject(project))

  def getDataGraphForProject(project: KnoraProject): InternalIri =
    ProjectService.projectDataNamedGraphV2(project)

  def getOntologyGraphsForProject(project: KnoraProject): Task[List[InternalIri]] =
    ontologyRepo.findByProject(project.id).map(_.map(_.ontologyMetadata.ontologyIri.toInternalIri))

  def addCopyrightHolders(project: ProjectIri, addThese: Set[CopyrightHolder]): Task[KnoraProject] =
    withProjectFromDb(project) { project =>
      if (addThese.isEmpty) ZIO.succeed(project)
      else {
        val newAuthors = project.allowedCopyrightHolders ++ addThese
        knoraProjectRepo.save(project.copy(allowedCopyrightHolders = newAuthors))
      }
    }

  def replaceCopyrightHolder(
    projectIri: ProjectIri,
    oldValue: CopyrightHolder,
    newValue: CopyrightHolder,
  ): Task[KnoraProject] =
    withProjectFromDb(projectIri) { project =>
      if (!project.allowedCopyrightHolders.contains(oldValue)) ZIO.succeed(project)
      else {
        val newAuthors = project.allowedCopyrightHolders - oldValue + newValue
        knoraProjectRepo.save(project.copy(allowedCopyrightHolders = newAuthors))
      }
    }

  /**
   * Removes all copyright holders from the project.
   * This is not a use case for normal users and currently only used in tests
   * @param iri the project iri
   */
  def removeAllCopyrightHolder(iri: ProjectIri): Task[KnoraProject] =
    withProjectFromDb(iri) { project =>
      knoraProjectRepo.save(project.copy(allowedCopyrightHolders = Set.empty))
    }
}

object KnoraProjectService {
  val layer = ZLayer.derive[KnoraProjectService]
}
