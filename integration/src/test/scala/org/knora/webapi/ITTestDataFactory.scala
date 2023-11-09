/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import dsp.valueobjects.Iri._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._

/**
 * Helps in creating value objects for tests.
 */
object ITTestDataFactory {
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
