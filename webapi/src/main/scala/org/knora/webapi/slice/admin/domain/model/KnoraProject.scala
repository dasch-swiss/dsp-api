/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import sttp.tapir.Schema
import zio.NonEmptyChunk
import zio.json.*
import zio.prelude.Validation

import scala.util.matching.Regex

import dsp.errors.ValidationException
import dsp.valueobjects.Iri
import dsp.valueobjects.Iri.isProjectIri
import dsp.valueobjects.Iri.validateAndEscapeProjectIri
import dsp.valueobjects.IriErrorMessages
import dsp.valueobjects.UuidUtil
import dsp.valueobjects.V2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.common.StringBasedValueCompanionWithCodecs
import org.knora.webapi.slice.common.Value
import org.knora.webapi.slice.common.Value.BooleanValue
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

case class KnoraProject(
  id: ProjectIri,
  shortname: Shortname,
  shortcode: Shortcode,
  longname: Option[Longname],
  description: NonEmptyChunk[Description],
  keywords: List[Keyword],
  logo: Option[Logo],
  status: Status,
  selfjoin: SelfJoin,
  ontologies: List[InternalIri]
)

object KnoraProject {
  final case class ProjectIri private (override val value: String) extends AnyVal with StringValue

  object ProjectIri {

    implicit val codec: JsonCodec[ProjectIri] =
      JsonCodec[String].transformOrFail(ProjectIri.from(_).toEitherWith(e => e.head.getMessage), _.value)

    implicit val tapirCodec: Codec[String, ProjectIri, CodecFormat.TextPlain] =
      Codec.string.mapEither(str => ProjectIri.from(str).toEitherWith(e => e.head.getMessage))(_.value)

    def unsafeFrom(str: String): ProjectIri = from(str).fold(e => throw e.head, identity)

    def from(str: String): Validation[ValidationException, ProjectIri] = str match {
      case str if str.isEmpty =>
        Validation.fail(ValidationException(IriErrorMessages.ProjectIriMissing))
      case str if !isProjectIri(str) =>
        Validation.fail(ValidationException(IriErrorMessages.ProjectIriInvalid))
      case str if UuidUtil.hasValidLength(str.split("/").last) && !UuidUtil.hasSupportedVersion(str) =>
        Validation.fail(ValidationException(IriErrorMessages.UuidVersionInvalid))
      case _ =>
        Validation
          .fromOption(validateAndEscapeProjectIri(str))
          .mapError(_ => ValidationException(IriErrorMessages.ProjectIriInvalid))
          .map(ProjectIri(_))
    }
  }

  final case class Shortcode private (override val value: String) extends AnyVal with StringValue

  object Shortcode extends StringBasedValueCompanionWithCodecs[Shortcode] {

    private val shortcodeRegex: Regex = "^\\p{XDigit}{4}$".r

    def from(value: String): Either[String, Shortcode] =
      if (shortcodeRegex.matches(value.toUpperCase)) Right(Shortcode(value.toUpperCase))
      else if (value.isEmpty) Left("Shortcode cannot be empty.")
      else Left(s"Shortcode is invalid: $value")
  }

  final case class Shortname private (override val value: String) extends AnyVal with StringValue

  object Shortname {

    private val shortnameRegex: Regex = "^[a-zA-Z][a-zA-Z0-9_-]{2,19}$".r

    def unsafeFrom(str: String): Shortname = from(str).fold(e => throw e.head, identity)

    def from(value: String): Validation[ValidationException, Shortname] =
      if (value.isEmpty) Validation.fail(ValidationException("Shortname cannot be empty."))
      else {
        val maybeShortname = value match {
          case "DefaultSharedOntologiesProject" => Some(value)
          case _                                => shortnameRegex.findFirstIn(value)
        }
        Validation
          .fromOption(maybeShortname.flatMap(Iri.toSparqlEncodedString))
          .mapError(_ => ValidationException(s"Shortname is invalid: $value"))
          .map(Shortname(_))
      }

    implicit val codec: JsonCodec[Shortname] =
      JsonCodec[String].transformOrFail(Shortname.from(_).toEitherWith(_.head.getMessage), _.value)
  }

  final case class Longname private (override val value: String) extends AnyVal with StringValue

  object Longname {

    private val longnameRegex: Regex = "^.{3,256}$".r

    def unsafeFrom(str: String): Longname = from(str).fold(e => throw e.head, identity)

    def from(value: String): Validation[ValidationException, Longname] =
      if (longnameRegex.matches(value)) Validation.succeed(Longname(value))
      else Validation.fail(ValidationException("Longname must be 3 to 256 characters long."))

    implicit val codec: JsonCodec[Longname] =
      JsonCodec[String].transformOrFail(Longname.from(_).toEitherWith(_.head.getMessage), _.value)
  }

  final case class Description private (override val value: V2.StringLiteralV2)
      extends AnyVal
      with Value[V2.StringLiteralV2]

  object Description {

    def unsafeFrom(str: V2.StringLiteralV2): Description = from(str).fold(e => throw e.head, identity)

    def from(literal: V2.StringLiteralV2): Validation[ValidationException, Description] =
      if (literal.value.length >= 3 && literal.value.length <= 40960) Validation.succeed(Description(literal))
      else Validation.fail(ValidationException("Description must be 3 to 40960 characters long."))

    implicit val codec: JsonCodec[Description] =
      JsonCodec[V2.StringLiteralV2].transformOrFail(Description.from(_).toEitherWith(_.head.getMessage), _.value)
  }

  final case class Keyword private (override val value: String) extends AnyVal with StringValue

  object Keyword {

    private val keywordRegex: Regex = "^.{3,64}$".r

    def unsafeFrom(str: String): Keyword = from(str).fold(e => throw e.head, identity)

    def from(value: String): Validation[ValidationException, Keyword] =
      if (keywordRegex.matches(value)) Validation.succeed(Keyword(value))
      else Validation.fail(ValidationException("Keyword must be 3 to 64 characters long."))

    implicit val codec: JsonCodec[Keyword] =
      JsonCodec[String].transformOrFail(Keyword.from(_).toEitherWith(_.head.getMessage), _.value)
  }

  final case class Logo private (override val value: String) extends AnyVal with StringValue

  object Logo {

    def unsafeFrom(str: String): Logo = from(str).fold(e => throw e.head, identity)

    def from(value: String): Validation[ValidationException, Logo] =
      if (value.isEmpty) Validation.fail(ValidationException("Logo cannot be empty."))
      else Validation.succeed(Logo(value))

    implicit val codec: JsonCodec[Logo] =
      JsonCodec[String].transformOrFail(Logo.from(_).toEitherWith(_.head.getMessage), _.value)
  }

  sealed trait Status extends BooleanValue

  object Status {

    case object Active   extends Status { val value = true  }
    case object Inactive extends Status { val value = false }

    def from(value: Boolean): Status = if (value) Active else Inactive

    implicit val codec: JsonCodec[Status] = JsonCodec[Boolean].transformOrFail(b => Right(Status.from(b)), _.value)

    implicit val schema: Schema[Status] = Schema.schemaForBoolean.map(b => Some(Status.from(b)))(_.value)
  }

  sealed trait SelfJoin extends BooleanValue

  object SelfJoin {

    case object CanJoin    extends SelfJoin { val value = true  }
    case object CannotJoin extends SelfJoin { val value = false }

    def from(value: Boolean): SelfJoin = if (value) CanJoin else CannotJoin

    implicit val codec: JsonCodec[SelfJoin] = JsonCodec[Boolean].transformOrFail(b => Right(SelfJoin.from(b)), _.value)

    implicit val schema: Schema[SelfJoin] = Schema.schemaForBoolean.map(b => Some(SelfJoin.from(b)))(_.value)
  }
}
