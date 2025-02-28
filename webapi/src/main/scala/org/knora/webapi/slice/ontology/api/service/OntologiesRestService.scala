/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.api.service
import zio.*

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.responders.v2.OntologyResponderV2
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.ontology.api.LastModificationDate
import org.knora.webapi.slice.ontology.domain.service.IriConverter
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.resources.api.model.IriDto

final case class OntologiesRestService(
  private val auth: AuthorizationRestService,
  private val iriConverter: IriConverter,
  private val ontologiesRepo: OntologyRepo,
  private val ontologyResponder: OntologyResponderV2,
  private val renderer: KnoraResponseRenderer,
) {
  def deleteOntology(
    user: User,
  )(ontologyIri: IriDto, formatOptions: FormatOptions, lastModificationDate: LastModificationDate) = for {
    onto     <- ensureOntologyExists(ontologyIri)
    _        <- ensureProjectAdmin(onto, user)
    uuid     <- Random.nextUUID()
    result   <- ontologyResponder.deleteOntology(onto.ontologyIri, lastModificationDate.value, uuid)
    response <- renderer.render(result, formatOptions)
  } yield response

  private def ensureOntologyExists(ontologyIri: IriDto) = for {
    ontologyIri <- iriConverter.asOntologyIri(ontologyIri.value).mapError(BadRequestException.apply)
    onto        <- ontologiesRepo.findById(ontologyIri)
    onto        <- ZIO.fromOption(onto).orElseFail(NotFoundException.notfound(ontologyIri))
  } yield onto

  private def ensureProjectAdmin(onto: ReadOntologyV2, user: User) = for {
    projectIri <- ZIO.fromOption(onto.projectIri).orElseFail(NotFoundException.notfound(onto.ontologyIri))
    _          <- auth.ensureSystemAdminOrProjectAdminById(user, projectIri)
  } yield ()
}

object OntologiesRestService {
  val layer = ZLayer.derive[OntologiesRestService]
}
