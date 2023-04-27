/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.projectsmessages

import zio.json._
import zio.prelude.Validation

import dsp.errors.ValidationException
import dsp.valueobjects.Iri.ProjectIri
import dsp.valueobjects.Project._

/**
 * Project creation payload
 */
final case class ProjectCreatePayloadADM(
  id: Option[ProjectIri] = None,
  shortname: ShortName,
  shortcode: ShortCode,
  longname: Option[Name] = None,
  description: ProjectDescription,
  keywords: Keywords,
  logo: Option[Logo] = None,
  status: ProjectStatus,
  selfjoin: ProjectSelfJoin
)

object ProjectCreatePayloadADM {

  implicit val codec: JsonCodec[ProjectCreatePayloadADM] = DeriveJsonCodec.gen[ProjectCreatePayloadADM]

  def make(apiRequest: CreateProjectApiRequestADM): Validation[Throwable, ProjectCreatePayloadADM] = {
    val id: Validation[Throwable, Option[ProjectIri]]          = ProjectIri.make(apiRequest.id)
    val shortname: Validation[Throwable, ShortName]            = ShortName.make(apiRequest.shortname)
    val shortcode: Validation[Throwable, ShortCode]            = ShortCode.make(apiRequest.shortcode)
    val longname: Validation[Throwable, Option[Name]]          = Name.make(apiRequest.longname)
    val description: Validation[Throwable, ProjectDescription] = ProjectDescription.make(apiRequest.description)
    val keywords: Validation[Throwable, Keywords]              = Keywords.make(apiRequest.keywords)
    val logo: Validation[Throwable, Option[Logo]]              = Logo.make(apiRequest.logo)
    val status: Validation[Throwable, ProjectStatus]           = ProjectStatus.make(apiRequest.status)
    val selfjoin: Validation[Throwable, ProjectSelfJoin]       = ProjectSelfJoin.make(apiRequest.selfjoin)
    Validation.validateWith(id, shortname, shortcode, longname, description, keywords, logo, status, selfjoin)(
      ProjectCreatePayloadADM.apply
    )
  }
}

/**
 * Project update payload
 */
final case class ProjectUpdatePayloadADM(
  shortname: Option[ShortName] = None,
  longname: Option[Name] = None,
  description: Option[ProjectDescription] = None,
  keywords: Option[Keywords] = None,
  logo: Option[Logo] = None,
  status: Option[ProjectStatus] = None,
  selfjoin: Option[ProjectSelfJoin] = None
)

object ProjectUpdatePayloadADM {

  implicit val codec: JsonCodec[ProjectUpdatePayloadADM] = DeriveJsonCodec.gen[ProjectUpdatePayloadADM]

  def make(apiRequest: ChangeProjectApiRequestADM): Validation[Throwable, ProjectUpdatePayloadADM] = {
    val shortname: Validation[ValidationException, Option[ShortName]] = ShortName.make(apiRequest.shortname)
    val longname: Validation[Throwable, Option[Name]]                 = Name.make(apiRequest.longname)
    val description: Validation[Throwable, Option[ProjectDescription]] =
      ProjectDescription.make(apiRequest.description)
    val keywords: Validation[Throwable, Option[Keywords]]        = Keywords.make(apiRequest.keywords)
    val logo: Validation[Throwable, Option[Logo]]                = Logo.make(apiRequest.logo)
    val status: Validation[Throwable, Option[ProjectStatus]]     = ProjectStatus.make(apiRequest.status)
    val selfjoin: Validation[Throwable, Option[ProjectSelfJoin]] = ProjectSelfJoin.make(apiRequest.selfjoin)

    Validation.validateWith(shortname, longname, description, keywords, logo, status, selfjoin)(
      ProjectUpdatePayloadADM.apply
    )
  }
}
