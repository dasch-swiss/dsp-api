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
import org.knora.webapi.slice.admin.domain.model.LicenseIri.AI_GENERATED
import org.knora.webapi.slice.admin.domain.model.LicenseIri.BORIS
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_0_1_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_BY_4_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_BY_NC_4_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_BY_NC_ND_4_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_BY_NC_SA_4_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_BY_ND_4_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_BY_SA_4_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.CC_PDM_1_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.OPEN_LICENCE_2_0
import org.knora.webapi.slice.admin.domain.model.LicenseIri.PUBLIC_DOMAIN
import org.knora.webapi.slice.admin.domain.model.LicenseIri.UNKNOWN
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.StringValueCompanion.*
import org.knora.webapi.slice.common.StringValueCompanion.maxLength
import org.knora.webapi.slice.common.Value.StringValue

final case class LegalInfo(
  copyrightHolder: Option[CopyrightHolder],
  authorship: Option[List[Authorship]],
  licenseIri: Option[LicenseIri],
) {
  def from(copyrightHolder: CopyrightHolder, authorship: List[Authorship], licenseIri: LicenseIri): LegalInfo =
    LegalInfo(Some(copyrightHolder), Some(authorship).filter(_.nonEmpty), Some(licenseIri))
}

final case class CopyrightHolder private (override val value: String) extends StringValue
object CopyrightHolder extends StringValueCompanion[CopyrightHolder] {
  given JsonCodec[CopyrightHolder] = ZioJsonCodec.stringCodec(CopyrightHolder.from)
  given Schema[CopyrightHolder]    = Schema.string
  given Ordering[CopyrightHolder]  = Ordering.by(_.value)

  val default: Set[CopyrightHolder] =
    Set("AI-Generated Content - Not Protected by Copyright", "Public Domain - Not Protected by Copyright").map(
      CopyrightHolder.unsafeFrom,
    )

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
  def from(str: String): Either[String, Authorship] =
    fromValidations("Authorship", Authorship.apply, List(nonEmpty, noLineBreaks, maxLength(1_000)))(str)
}

final case class LicenseIri private (override val value: String) extends StringValue
object LicenseIri extends StringValueCompanion[LicenseIri] {
  given JsonCodec[LicenseIri] = ZioJsonCodec.stringCodec(LicenseIri.from)
  given Schema[LicenseIri]    = Schema.string

  /**
   * Explanation of the IRI regex:
   * * `^` asserts the start of the string.
   * * `http://rdfh\.ch/licenses/` matches the specified prefix.
   * * `[A-Za-z0-9_-]{22}` matches any base64 encoded UUID (22 characters long).
   */
  private lazy val licenseIriRegEx = """^http://rdfh\.ch/licenses/[A-Za-z0-9_-]{22}$""".r

  val CC_BY_4_0: LicenseIri        = LicenseIri("http://rdfh.ch/licenses/cc-by-4.0")
  val CC_BY_SA_4_0: LicenseIri     = LicenseIri("http://rdfh.ch/licenses/cc-by-sa-4.0")
  val CC_BY_NC_4_0: LicenseIri     = LicenseIri("http://rdfh.ch/licenses/cc-by-nc-4.0")
  val CC_BY_NC_SA_4_0: LicenseIri  = LicenseIri("http://rdfh.ch/licenses/cc-by-nc-sa-4.0")
  val CC_BY_ND_4_0: LicenseIri     = LicenseIri("http://rdfh.ch/licenses/cc-by-nd-4.0")
  val CC_BY_NC_ND_4_0: LicenseIri  = LicenseIri("http://rdfh.ch/licenses/cc-by-nc-nd-4.0")
  val AI_GENERATED: LicenseIri     = LicenseIri("http://rdfh.ch/licenses/ai-generated")
  val UNKNOWN: LicenseIri          = LicenseIri("http://rdfh.ch/licenses/unknown")
  val PUBLIC_DOMAIN: LicenseIri    = LicenseIri("http://rdfh.ch/licenses/public-domain")
  val CC_0_1_0: LicenseIri         = LicenseIri("http://rdfh.ch/licenses/cc-0-1.0")
  val CC_PDM_1_0: LicenseIri       = LicenseIri("http://rdfh.ch/licenses/cc-pdm-1.0")
  val BORIS: LicenseIri            = LicenseIri("http://rdfh.ch/licenses/boris")
  val OPEN_LICENCE_2_0: LicenseIri = LicenseIri("http://rdfh.ch/licenses/open-licence-2.0")

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
      CC_0_1_0,
      CC_PDM_1_0,
      BORIS,
      OPEN_LICENCE_2_0,
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

final case class License private (id: LicenseIri, uri: URI, labelEn: String, isRecommended: IsDaschRecommended)
enum IsDaschRecommended {
  case Yes extends IsDaschRecommended
  case No  extends IsDaschRecommended
  def toBoolean: Boolean = this == Yes
}
object License {

  val BUILT_IN: Chunk[License] = Chunk(
    License(CC_BY_4_0, URI.create("https://creativecommons.org/licenses/by/4.0/"), "CC BY 4.0", IsDaschRecommended.Yes),
    License(
      CC_BY_SA_4_0,
      URI.create("https://creativecommons.org/licenses/by-sa/4.0/"),
      "CC BY-SA 4.0",
      IsDaschRecommended.Yes,
    ),
    License(
      CC_BY_NC_4_0,
      URI.create("https://creativecommons.org/licenses/by-nc/4.0/"),
      "CC BY-NC 4.0",
      IsDaschRecommended.Yes,
    ),
    License(
      CC_BY_NC_SA_4_0,
      URI.create("https://creativecommons.org/licenses/by-nc-sa/4.0/"),
      "CC BY-NC-SA 4.0",
      IsDaschRecommended.Yes,
    ),
    License(
      CC_BY_ND_4_0,
      URI.create("https://creativecommons.org/licenses/by-nd/4.0/"),
      "CC BY-ND 4.0",
      IsDaschRecommended.Yes,
    ),
    License(
      CC_BY_NC_ND_4_0,
      URI.create("https://creativecommons.org/licenses/by-nc-nd/4.0/"),
      "CC BY-NC-ND 4.0",
      IsDaschRecommended.Yes,
    ),
    License(
      AI_GENERATED,
      URI.create(AI_GENERATED.value),
      "AI-Generated Content - Not Protected by Copyright",
      IsDaschRecommended.Yes,
    ),
    License(
      UNKNOWN,
      URI.create(UNKNOWN.value),
      "Unknown License - Ask Copyright Holder for Permission",
      IsDaschRecommended.Yes,
    ),
    License(
      PUBLIC_DOMAIN,
      URI.create(PUBLIC_DOMAIN.value),
      "Public Domain - Not Protected by Copyright",
      IsDaschRecommended.Yes,
    ),
    License(
      CC_0_1_0,
      URI.create("https://creativecommons.org/publicdomain/zero/1.0/"),
      "CC0 1.0",
      IsDaschRecommended.No,
    ),
    License(
      CC_PDM_1_0,
      URI.create("https://creativecommons.org/publicdomain/mark/1.0/"),
      "CC PDM 1.0",
      IsDaschRecommended.No,
    ),
    License(
      BORIS,
      URI.create("https://www.ub.unibe.ch/services/open_science/boris_publications/index_eng.html#collapse_pane631832"),
      "BORIS Standard License",
      IsDaschRecommended.No,
    ),
    License(
      OPEN_LICENCE_2_0,
      URI.create("https://www.etalab.gouv.fr/wp-content/uploads/2018/11/open-licence.pdf"),
      "LICENCE OUVERTE 2.0",
      IsDaschRecommended.No,
    ),
  )

  def from(id: LicenseIri, uri: URI, labelEn: String, isRecommended: IsDaschRecommended): Either[String, License] = {
    val labelValidation = Validation
      .validate(nonEmpty(labelEn), maxLength(255)(labelEn), noLineBreaks(labelEn))
      .mapErrorAll(errs => NonEmptyChunk(s"Label en ${errs.mkString("; ")}"))
    Validation
      .validate(absoluteUri(uri), labelValidation)
      .as(License(id, uri, labelEn, isRecommended))
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

  def unsafeFrom(id: LicenseIri, uri: URI, labelEn: String, isRecommended: IsDaschRecommended): License =
    from(id, uri, labelEn, isRecommended).fold(e => throw new IllegalArgumentException(e), identity)
}
