package org.knora.webapi.messages.admin.responder.valueObjects

import org.knora.webapi.exceptions.{AssertionException, BadRequestException}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import zio.prelude.Validation

/** Project value objects */

/**
 * Project Shortcode value object.
 */
sealed abstract case class Shortcode private (value: String)
object Shortcode {
  val stringFormatter = StringFormatter.getGeneralInstance

  def make(value: String): Validation[Throwable, Shortcode] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException("Missing shortcode"))
    } else {
      val validatedValue: Validation[Throwable, String] = Validation(
        stringFormatter.validateProjectShortcode(value, throw AssertionException("not valid"))
      )
      validatedValue.map(new Shortcode(_) {})
    }
}

/**
 * Project Shortname value object.
 */
sealed abstract case class Shortname private (value: String)
object Shortname {
  val stringFormatter = StringFormatter.getGeneralInstance

  def make(value: String): Validation[Throwable, Shortname] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException("Missing shortname"))
    } else {
      val validatedValue = Validation(
        stringFormatter.validateAndEscapeProjectShortname(value, throw AssertionException("not valid"))
      )
      validatedValue.map(new Shortname(_) {})
    }
}

/**
 * Project Longname value object.
 */
sealed abstract case class Longname private (value: String)
object Longname { self =>
  def make(value: String): Validation[Throwable, Longname] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException("Missing long name"))
    } else {
      Validation.succeed(new Longname(value) {})
    }
  def make(value: Option[String]): Validation[Throwable, Option[Longname]] =
    value match {
      case None    => Validation.succeed(None)
      case Some(v) => self.make(v).map(Some(_))
    }
}

/**
 * Project Keywords value object.
 */
sealed abstract case class Keywords private (value: Seq[String])
object Keywords {
  def make(value: Seq[String]): Validation[Throwable, Keywords] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException("Missing keywords"))
    } else {
      Validation.succeed(new Keywords(value) {})
    }
}

/**
 * Project Logo value object.
 */
sealed abstract case class Logo private (value: String)
object Logo { self =>
  def make(value: String): Validation[Throwable, Logo] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException("Missing logo"))
    } else {
      Validation.succeed(new Logo(value) {})
    }
  def make(value: Option[String]): Validation[Throwable, Option[Logo]] =
    value match {
      case None    => Validation.succeed(None)
      case Some(v) => self.make(v).map(Some(_))
    }
}

/** Shared value objects */

/**
 * Selfjoin value object.
 */
sealed abstract case class Selfjoin private (value: Boolean)
object Selfjoin {
  def make(value: Boolean): Validation[Throwable, Selfjoin] =
    Validation.succeed(new Selfjoin(value) {})
}

/**
 * Status value object.
 */
sealed abstract case class Status private (value: Boolean)
object Status {
  def make(value: Boolean): Validation[Throwable, Status] =
    Validation.succeed(new Status(value) {})
}

/**
 * Description value object.
 */
sealed abstract case class Description private (value: Seq[StringLiteralV2])
object Description {
  def make(value: Seq[StringLiteralV2]): Validation[Throwable, Description] =
    if (value.isEmpty) {
      Validation.fail(BadRequestException("Missing description"))
    } else {
      Validation.succeed(new Description(value) {})
    }
}

/**
 * Name value object.
 */
sealed abstract case class Name private (value: String)
object Name {
  def create(value: String): Either[Throwable, Name] =
    if (value.isEmpty) {
      Left(BadRequestException("Missing Name"))
    } else {
      Right(new Name(value) {})
    }
}
