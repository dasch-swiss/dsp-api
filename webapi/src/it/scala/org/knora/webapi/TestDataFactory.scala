package org.knora.webapi

import dsp.valueobjects.Iri._
import dsp.valueobjects.Project._
import dsp.valueobjects.V2
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._

/**
 * Helps in creating value objects for tests.
 */
object TestDataFactory {
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

  def projectShortCode(shortCode: String): ShortCode =
    ShortCode
      .make(shortCode)
      .getOrElse(throw new IllegalArgumentException(s"Invalid ShortCode $shortCode."))

  def projectShortName(shortName: String): ShortName =
    ShortName
      .make(shortName)
      .getOrElse(throw new IllegalArgumentException(s"Invalid ShortName $shortName."))

  def projectName(name: String): Name =
    Name
      .make(name)
      .getOrElse(throw new IllegalArgumentException(s"Invalid Name $name."))

  def projectDescription(description: Seq[V2.StringLiteralV2]): ProjectDescription =
    ProjectDescription
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
