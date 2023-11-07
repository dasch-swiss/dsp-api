/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import dsp.valueobjects.Iri._
import dsp.valueobjects.Project._
import dsp.valueobjects.V2
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

  def projectShortcode(shortcode: String): Shortcode =
    Shortcode
      .make(shortcode)
      .getOrElse(throw new IllegalArgumentException(s"Invalid Shortcode $shortcode."))

  def projectShortname(shortname: String): Shortname =
    Shortname
      .make(shortname)
      .getOrElse(throw new IllegalArgumentException(s"Invalid Shortname $shortname."))

  def projectName(name: String): Longname =
    Longname
      .make(name)
      .getOrElse(throw new IllegalArgumentException(s"Invalid Name $name."))

  def projectDescription(description: Seq[V2.StringLiteralV2]): Description =
    Description
      .make(description)
      .getOrElse(throw new IllegalArgumentException(s"Invalid ProjectDescription $description."))

  def projectKeywords(keywords: Seq[String]): Keywords =
    Keywords
      .make(keywords)
      .getOrElse(throw new IllegalArgumentException(s"Invalid Keywords $keywords."))

  def projectLogo(logo: String): Logo =
    Logo
      .make(logo)
      .getOrElse(throw new IllegalArgumentException(s"Invalid Logo $logo."))

  def projectStatus(status: Boolean): ProjectStatus =
    ProjectStatus
      .make(status)
      .getOrElse(throw new IllegalArgumentException(s"Invalid ProjectStatus $status."))

  def projectSelfJoin(selfJoin: Boolean): ProjectSelfJoin =
    ProjectSelfJoin
      .make(selfJoin)
      .getOrElse(throw new IllegalArgumentException(s"Invalid ProjectSelfJoin $selfJoin."))

  def projectIri(iri: String): ProjectIri =
    ProjectIri
      .make(iri)
      .getOrElse(throw new IllegalArgumentException(s"Invalid ProjectIri $iri."))
}
