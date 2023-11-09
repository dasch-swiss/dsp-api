/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import sttp.tapir.Schema
import zio.NonEmptyChunk
import zio.json._
import zio.prelude.Validation

import scala.util.matching.Regex

import dsp.errors.ValidationException
import dsp.valueobjects.Iri
import dsp.valueobjects.V2
import dsp.valueobjects.V2.StringLiteralV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject._
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

case class KnoraProject(
  id: InternalIri,
  shortname: Shortname,
  shortcode: Shortcode,
  longname: Option[Longname],
  description: NonEmptyChunk[StringLiteralV2],
  keywords: List[String],
  logo: Option[Logo],
  status: ProjectStatus,
  selfjoin: ProjectSelfJoin,
  ontologies: List[InternalIri]
)

object KnoraProject {

  object ErrorMessages {
    val ProjectDescriptionMissing = "Description cannot be empty."
    val ProjectDescriptionInvalid = "Description must be 3 to 40960 characters long."
    val KeywordsMissing           = "Keywords cannot be empty."
    val KeywordsInvalid           = "Keywords must be 3 to 64 characters long."
  }

  final case class Shortcode private (value: String) extends AnyVal

  object Shortcode {

    private val shortcodeRegex: Regex = ("^\\p{XDigit}{4,4}$").r

    implicit val codec: JsonCodec[Shortcode] =
      JsonCodec[String].transformOrFail(value => Shortcode.make(value).toEitherWith(e => e.head.getMessage), _.value)

    def unsafeFrom(str: String): Shortcode = make(str).fold(e => throw e.head, identity)

    def make(value: String): Validation[ValidationException, Shortcode] =
      if (value.isEmpty) Validation.fail(ValidationException("Shortcode cannot be empty."))
      else if (shortcodeRegex.matches(value.toUpperCase)) { Validation.succeed(Shortcode(value.toUpperCase)) }
      else { Validation.fail(ValidationException(s"Shortcode is invalid: $value")) }
  }

  final case class Shortname private (value: String) extends AnyVal

  object Shortname {

    private val shortnameRegex: Regex = "^[a-zA-Z][a-zA-Z0-9_-]{2,19}$".r

    implicit val codec: JsonCodec[Shortname] =
      JsonCodec[String].transformOrFail(value => Shortname.make(value).toEitherWith(e => e.head.getMessage), _.value)

    def unsafeFrom(str: String): Shortname = make(str).fold(e => throw e.head, identity)

    def make(value: String): Validation[ValidationException, Shortname] =
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
  }

  final case class Longname private (value: String) extends AnyVal

  object Longname {

    implicit val codec: JsonCodec[Longname] =
      JsonCodec[String].transformOrFail(Longname.make(_).toEitherWith(e => e.head.getMessage), _.value)

    private val longnameRegex: Regex = "^.{3,256}$".r

    def unsafeFrom(str: String): Longname = make(str).fold(e => throw e.head, identity)

    def make(value: String): Validation[ValidationException, Longname] =
      if (longnameRegex.matches(value)) Validation.succeed(Longname(value))
      else Validation.fail(ValidationException("Longname must be 3 to 256 characters long."))
  }

  /**
   * Description value object.
   */
  sealed abstract case class Description private (value: Seq[V2.StringLiteralV2])
  object Description { self =>
    implicit val decoder: JsonDecoder[Description] = JsonDecoder[Seq[V2.StringLiteralV2]].mapOrFail { value =>
      Description.make(value).toEitherWith(e => e.head.getMessage)
    }
    implicit val encoder: JsonEncoder[Description] =
      JsonEncoder[Seq[V2.StringLiteralV2]].contramap((description: Description) => description.value)

    private def isLengthCorrect(descriptionToCheck: Seq[V2.StringLiteralV2]): Boolean = {
      val checked = descriptionToCheck.filter(d => d.value.length > 2 && d.value.length < 40961)
      descriptionToCheck == checked
    }

    def make(value: Seq[V2.StringLiteralV2]): Validation[ValidationException, Description] =
      if (value.isEmpty) Validation.fail(ValidationException(ErrorMessages.ProjectDescriptionMissing))
      else if (!isLengthCorrect(value)) Validation.fail(ValidationException(ErrorMessages.ProjectDescriptionInvalid))
      else Validation.succeed(new Description(value) {})

    def make(value: Option[Seq[V2.StringLiteralV2]]): Validation[ValidationException, Option[Description]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  /**
   * Project Keywords value object.
   */
  sealed abstract case class Keywords private (value: Seq[String])
  object Keywords { self =>
    implicit val decoder: JsonDecoder[Keywords] = JsonDecoder[Seq[String]].mapOrFail { value =>
      Keywords.make(value).toEitherWith(e => e.head.getMessage)
    }
    implicit val encoder: JsonEncoder[Keywords] =
      JsonEncoder[Seq[String]].contramap((keywords: Keywords) => keywords.value)

    private def isLengthCorrect(keywordsToCheck: Seq[String]): Boolean = {
      val checked = keywordsToCheck.filter(k => k.length > 2 && k.length < 65)
      keywordsToCheck == checked
    }

    def make(value: Seq[String]): Validation[ValidationException, Keywords] =
      if (value.isEmpty) Validation.fail(ValidationException(ErrorMessages.KeywordsMissing))
      else if (!isLengthCorrect(value)) Validation.fail(ValidationException(ErrorMessages.KeywordsInvalid))
      else Validation.succeed(new Keywords(value) {})

    def make(value: Option[Seq[String]]): Validation[ValidationException, Option[Keywords]] =
      value match {
        case Some(v) => self.make(v).map(Some(_))
        case None    => Validation.succeed(None)
      }
  }

  final case class Logo private (value: String) extends AnyVal

  object Logo { self =>

    implicit val codec: JsonCodec[Logo] =
      JsonCodec[String].transformOrFail(value => Logo.make(value).toEitherWith(e => e.head.getMessage), _.value)

    def unsafeFrom(str: String): Logo = make(str).fold(e => throw e.head, identity)

    def make(value: String): Validation[ValidationException, Logo] =
      if (value.isEmpty) Validation.fail(ValidationException("Logo cannot be empty."))
      else Validation.succeed(Logo(value))
  }

  trait ProjectStatus { def value: Boolean }

  object ProjectStatus {
    case object Active   extends ProjectStatus { val value = true  }
    case object Inactive extends ProjectStatus { val value = false }

    implicit val codec: JsonCodec[ProjectStatus] =
      JsonCodec[Boolean].transformOrFail(value => Right(ProjectStatus.from(value)), _.value)

    implicit val schema: Schema[ProjectStatus] = Schema.schemaForBoolean.map(b => Some(ProjectStatus.from(b)))(_.value)

    def from(value: Boolean): ProjectStatus = if (value) Active else Inactive
  }

  trait ProjectSelfJoin { def value: Boolean }

  object ProjectSelfJoin {
    case object CanJoin    extends ProjectSelfJoin { val value = true  }
    case object CannotJoin extends ProjectSelfJoin { val value = false }

    implicit val codec: JsonCodec[ProjectSelfJoin] =
      JsonCodec[Boolean].transformOrFail(value => Right(ProjectSelfJoin.from(value)), _.value)

    implicit val schema: Schema[ProjectSelfJoin] =
      Schema.schemaForBoolean.map(b => Some(ProjectSelfJoin.from(b)))(_.value)

    def from(value: Boolean): ProjectSelfJoin = if (value) CanJoin else CannotJoin
  }
}
