/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.repo

import zio.Ref
import zio.Task
import zio.ULayer
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortnameIdentifier
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.repo.AbstractInMemoryCrudRepository

final case class KnoraProjectRepoInMemory(projects: Ref[List[KnoraProject]])
    extends AbstractInMemoryCrudRepository[KnoraProject, ProjectIri](projects, _.id)
    with KnoraProjectRepo {

  override def findById(id: ProjectIdentifierADM): Task[Option[KnoraProject]] = projects.get.map(
    _.find(id match {
      case ShortcodeIdentifier(shortcode) => _.shortcode == shortcode
      case ShortnameIdentifier(shortname) => _.shortname == shortname
      case IriIdentifier(iri)             => _.id.value == iri.value
    }),
  )

  override def setProjectRestrictedView(project: KnoraProject, settings: RestrictedView): Task[Unit] =
    ZIO.die(new UnsupportedOperationException("Not implemented yet"))
}

object KnoraProjectRepoInMemory {
  val layer: ULayer[KnoraProjectRepoInMemory] =
    ZLayer.fromZIO(Ref.make(List.empty[KnoraProject]).map(KnoraProjectRepoInMemory(_)))
}
