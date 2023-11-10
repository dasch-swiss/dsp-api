/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import zio.NonEmptyChunk

import dsp.valueobjects.Iri.*
import dsp.valueobjects.V2
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

/**
 * Helps in creating value objects for tests.
 */
object TestDataFactory {

  val someProject = KnoraProject(
    InternalIri("http://rdfh.ch/projects/0001"),
    Shortname.unsafeFrom("shortname"),
    Shortcode.unsafeFrom("0001"),
    None,
    NonEmptyChunk(Description.unsafeFrom(V2.StringLiteralV2("Some description", None))),
    List.empty,
    None,
    Status.Active,
    SelfJoin.CannotJoin,
    List.empty
  )

  def projectShortcodeIdentifier(shortcode: String): ShortcodeIdentifier =
    ShortcodeIdentifier
      .fromString(shortcode)
      .getOrElse(throw new IllegalArgumentException(s"Invalid ShortcodeIdentifier $shortcode."))

  def projectShortnameIdentifier(shortname: String): ShortnameIdentifier =
    ShortnameIdentifier
      .fromString(shortname)
      .getOrElse(throw new IllegalArgumentException(s"Invalid ShortnameIdentifier $shortname."))

  def projectIriIdentifier(iri: String): IriIdentifier =
    IriIdentifier
      .fromString(iri)
      .getOrElse(throw new IllegalArgumentException(s"Invalid IriIdentifier $iri."))

  def projectIri(iri: String): ProjectIri =
    ProjectIri
      .make(iri)
      .getOrElse(throw new IllegalArgumentException(s"Invalid ProjectIri $iri."))
}
