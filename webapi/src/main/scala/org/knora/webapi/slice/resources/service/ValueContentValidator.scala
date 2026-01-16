/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import zio.*

import org.knora.webapi.IRI
import org.knora.webapi.messages.util.standoff.StandoffStringUtil
import org.knora.webapi.messages.v2.responder.resourcemessages.CreateResourceRequestV2
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.service.LegalInfoService
import org.knora.webapi.slice.common.service.IriConverter

/**
 * A service that validates values in requests that create resources with values or create/update values.
 *
 * The validations performed here include:
 * - Ensuring that link or standoff link values do not create cross-project links.
 * - Ensuring that file values have valid legal information
 */
final case class ValueContentValidator(
  private val iriConverter: IriConverter,
  private val legalInfoService: LegalInfoService,
) {

  def validate(cv: CreateValueV2): IO[String, Unit] = validateValueContent(cv.valueContent, cv.resourceIri)

  def validate(uv: UpdateValueV2): IO[String, Unit] = uv match
    case uvc: UpdateValueContentV2   => validateValueContent(uvc.valueContent, uvc.resourceIri)
    case _: UpdateValuePermissionsV2 => ZIO.unit

  def validate(req: CreateResourceRequestV2): IO[String, Unit] =
    val shortcode   = req.createResource.projectADM.shortcode
    val vcsToCreate = req.createResource.values.values.flatten.map(_.valueContent)
    ZIO.foreachDiscard(vcsToCreate)(validateValueContent(_, shortcode))

  private def validateValueContent(vc: ValueContentV2, resourceIri: IRI): IO[String, Unit] =
    extractShortcode(resourceIri).flatMap(validateValueContent(vc, _))

  private def extractShortcode(resourceIri: IRI): IO[String, Shortcode] =
    iriConverter.asResourceIri(resourceIri).map(_.shortcode)

  private def validateValueContent(vc: ValueContentV2, inProject: Shortcode): IO[String, Unit] =
    ensureNoCrossProjectLink(vc, inProject) *> ensureValidLegalInfo(vc, inProject)

  private def ensureNoCrossProjectLink(vc: ValueContentV2, inProject: Shortcode): IO[String, Unit] = vc match
    case lvc: LinkValueContentV2 => ensureNoCrossProjectLink(lvc, inProject)
    case tvc: TextValueContentV2 => ensureNoCrossProjectLink(tvc, inProject)
    case _                       => ZIO.unit

  private def ensureNoCrossProjectLink(lvc: LinkValueContentV2, inProject: Shortcode) =
    extractShortcode(lvc.referredResourceIri).flatMap { refShortcode =>
      ZIO
        .fail(s"Cannot create a link between resources cross projects $inProject and $refShortcode")
        .unless(inProject == refShortcode)
        .unit
    }

  private def ensureNoCrossProjectLink(tvc: TextValueContentV2, inProject: Shortcode): IO[String, Unit] =
    ZIO.foreachDiscard(StandoffStringUtil.getResourceIrisFromStandoffLinkTags(tvc.standoff))(resourceIri =>
      extractShortcode(resourceIri).option.flatMap {
        case Some(refShortcode) =>
          ZIO
            .fail(s"Cannot create a standoff IRI link between resources cross projects $inProject and $refShortcode")
            .unless(inProject == refShortcode)
            .unit
        case None => ZIO.unit
      },
    )

  private def ensureValidLegalInfo(vc: ValueContentV2, inProject: Shortcode): IO[String, Unit] = vc match
    case fvc: FileValueContentV2 => legalInfoService.validateLegalInfo(fvc.fileValue, inProject).unit
    case _                       => ZIO.unit
}

object ValueContentValidator {
  val layer = ZLayer.derive[ValueContentValidator]
}
