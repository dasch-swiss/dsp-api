/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import sttp.tapir.Schema
import zio.Chunk
import zio.NonEmptyChunk
import zio.json.JsonCodec
import zio.prelude.Validation

import java.net.URI
import dsp.valueobjects.UuidUtil
import org.knora.webapi.slice.admin.api.Codecs.ZioJsonCodec
import org.knora.webapi.slice.admin.api.model.PagedResponse
import org.knora.webapi.slice.admin.domain.model.LicenseIri.AI_GENERATED
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_BY_4_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_BY_NC_4_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_BY_NC_ND_4_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_BY_NC_SA_4_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_BY_ND_4_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_BY_SA_4_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.PUBLIC_DOMAIN
import org.knora.webapi.slice.admin.domain.model.LicenseIri.UNKNOWN
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.StringValueCompanion.*
import org.knora.webapi.slice.common.StringValueCompanion.maxLength
import org.knora.webapi.slice.common.Value.StringValue

final case class CopyrightHolder private (override val value: String) extends StringValue
object CopyrightHolder extends StringValueCompanion[CopyrightHolder] {
  given JsonCodec[CopyrightHolder] = ZioJsonCodec.stringCodec(CopyrightHolder.from)
  given Schema[CopyrightHolder]    = Schema.string
  given Schema[PagedResponse[CopyrightHolder]] =
    Schema.derived[PagedResponse[CopyrightHolder]].modify(_.data)(_.copy(isOptional = false))

  given Ordering[CopyrightHolder] = Ordering.by(_.value)
  def from(str: String): Either[String, CopyrightHolder] =
    fromValidations(
      "Copyright Holder",
      CopyrightHolder.apply,
      List(nonEmpty, noLineBreaks, maxLength(1_000)),
    )(str)
}

final case class Authorship private (override val value: String) extends StringValue
object Authorship extends StringValueCompanion[Authorship] {
  given JsonCodec[Authorship] = ZioJsonCodec.stringCodec(Authorship.from)
  given Schema[Authorship]    = Schema.string
  given Schema[PagedResponse[Authorship]] =
    Schema.derived[PagedResponse[Authorship]].modify(_.data)(_.copy(isOptional = false))
  def from(str: String): Either[String, Authorship] =
    fromValidations("Authorship", Authorship.apply, List(nonEmpty, noLineBreaks, maxLength(1_000)))(str)
}

final case class LicenseIri private (override val value: String) extends StringValue
object LicenseIri extends StringValueCompanion[LicenseIri] {
  given JsonCodec[LicenseIri] = ZioJsonCodec.stringCodec(LicenseIri.from)
  given Schema[PagedResponse[LicenseIri]] =
    Schema.derived[PagedResponse[LicenseIri]].modify(_.data)(_.copy(isOptional = false))
  given Schema[LicenseIri] = Schema.string

  /**
   * Explanation of the IRI regex:
   * * `^` asserts the start of the string.
   * * `http://rdfh\.ch/licenses/` matches the specified prefix.
   * * `[A-Za-z0-9_-]{22}` matches any base64 encoded UUID (22 characters long).
   */
  private lazy val licenseIriRegEx = """^http://rdfh\.ch/licenses/[A-Za-z0-9_-]{22}$""".r

  val CC_BY_4_0: LicenseIri       = LicenseIri("http://rdfh.ch/licenses/cc-by-4.0")
  val CC_BY_SA_4_0: LicenseIri    = LicenseIri("http://rdfh.ch/licenses/cc-by-sa-4.0")
  val CC_BY_NC_4_0: LicenseIri    = LicenseIri("http://rdfh.ch/licenses/cc-by-nc-4.0")
  val CC_BY_NC_SA_4_0: LicenseIri = LicenseIri("http://rdfh.ch/licenses/cc-by-nc-sa-4.0")
  val CC_BY_ND_4_0: LicenseIri    = LicenseIri("http://rdfh.ch/licenses/cc-by-nd-4.0")
  val CC_BY_NC_ND_4_0: LicenseIri = LicenseIri("http://rdfh.ch/licenses/cc-by-nc-nd-4.0")
  val AI_GENERATED: LicenseIri    = LicenseIri("http://rdfh.ch/licenses/ai-generated")
  val UNKNOWN: LicenseIri         = LicenseIri("http://rdfh.ch/licenses/unknown")
  val PUBLIC_DOMAIN: LicenseIri   = LicenseIri("http://rdfh.ch/licenses/public-domain")

  val BUILT_IN: Set[LicenseIri] =
    Set(
      CC_BY_4_0,
      CC_BY_SA_4_0,
      CC_BY_NC_4_0,
      CC_BY_NC_SA_4_0,
      CC_BY_ND_4_0,
      CC_BY_NC_ND_4_0,
      AI_GENERATED,
      UNKNOWN,
      PUBLIC_DOMAIN,
    )

  private def isLicenseIri(iri: String) = licenseIriRegEx.matches(iri) || BUILT_IN.map(_.value).contains(iri)

  def from(str: String): Either[String, LicenseIri] = str match {
    case str if !isLicenseIri(str) => Left("Invalid license IRI")
    case _                         => Right(LicenseIri(str))
  }

  def makeNew: LicenseIri = {
    val uuid = UuidUtil.makeRandomBase64EncodedUuid
    unsafeFrom(s"http://rdfh.ch/licenses/$uuid")
  }
}

final case class License private (id: LicenseIri, uri: URI, labelEn: String)
object License {

  val BUILT_IN: Chunk[License] = Chunk(
    License(CC_BY_4_0, URI.create("https://creativecommons.org/licenses/by/4.0/"), "CC BY 4.0"),
    License(CC_BY_SA_4_0, URI.create("https://creativecommons.org/licenses/by-sa/4.0/"), "CC BY-SA 4.0"),
    License(CC_BY_NC_4_0, URI.create("https://creativecommons.org/licenses/by-nc/4.0/"), "CC BY-NC 4.0"),
    License(CC_BY_NC_SA_4_0, URI.create("https://creativecommons.org/licenses/by-nc-sa/4.0/"), "CC BY-NC-SA 4.0"),
    License(CC_BY_ND_4_0, URI.create("https://creativecommons.org/licenses/by-nd/4.0/"), "CC BY-ND 4.0"),
    License(CC_BY_NC_ND_4_0, URI.create("https://creativecommons.org/licenses/by-nc-nd/4.0/"), "CC BY-NC-ND 4.0"),
    License(AI_GENERATED, URI.create(AI_GENERATED.value), "AI-Generated Content - Not Protected by Copyright"),
    License(UNKNOWN, URI.create(UNKNOWN.value), "Unknown License - Ask Copyright Holder for Permission"),
    License(PUBLIC_DOMAIN, URI.create(PUBLIC_DOMAIN.value), "Public Domain - Not Protected by Copyright"),
  )

  def from(id: LicenseIri, uri: URI, labelEn: String): Either[String, License] = {
    val labelValidation = Validation
      .validate(nonEmpty(labelEn), maxLength(255)(labelEn), noLineBreaks(labelEn))
      .mapErrorAll(errs => NonEmptyChunk(s"Label en ${errs.mkString("; ")}"))
    Validation
      .validate(absoluteUri(uri), labelValidation)
      .as(License(id, uri, labelEn))
      .flatMap(checkAgainstBuiltIn)
      .toEither
      .left
      .map(errs => s"License: ${errs.mkString(", ")}")
  }

  private def checkAgainstBuiltIn(license: License): Validation[String, License] = {
    val byId    = BUILT_IN.find(_.id == license.id)
    val byUri   = BUILT_IN.find(_.uri == license.uri)
    val byLabel = BUILT_IN.find(_.labelEn == license.labelEn)
    (license, Set(byId, byUri, byLabel).flatten) match {
      case (l, found) if found.isEmpty => Validation.succeed(l)
      case (l, found) if !found.contains(l) =>
        Validation.fail(s"Found predefined license expected one of '${found.toList.mkString(", ")}'")
      case (l, _) => Validation.succeed(l)
    }
  }

  def unsafeFrom(id: LicenseIri, uri: URI, labelEn: String): License =
    from(id, uri, labelEn).fold(e => throw new IllegalArgumentException(e), identity)
}
