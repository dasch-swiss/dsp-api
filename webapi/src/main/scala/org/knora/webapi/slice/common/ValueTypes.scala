/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import zio.prelude.Validation
import zio.prelude.ZValidation

import java.net.URI
import scala.util.Try

import org.knora.webapi.slice.common.Value.IntValue
import org.knora.webapi.slice.common.Value.StringValue

trait Value[A] extends Any {
  def value: A
  override def toString: String = value.toString
}

object Value {
  type BooleanValue = Value[Boolean]
  type StringValue  = Value[String]
  type IntValue     = Value[Int]
}

trait WithFrom[-I, +A] {

  def from(in: I): Either[String, A]

  final def unsafeFrom(in: I): A =
    from(in).fold(e => throw new IllegalArgumentException(e), identity)
}

trait StringValueCompanion[A <: StringValue] extends WithFrom[String, A]
object StringValueCompanion {

  def nonEmpty: String => Validation[String, String] =
    value => Validation.fromPredicateWith(s"cannot be empty")(value)((str: String) => str.nonEmpty)

  def noLineBreaks: String => Validation[String, String] =
    value => Validation.fromPredicateWith(s"must not contain line breaks")(value)((str: String) => !str.contains("\n"))

  def maxLength(max: Int): String => Validation[String, String] =
    value =>
      Validation.fromPredicateWith(s"must be maximum $max characters long")(value)((str: String) => str.length <= max)

  def isUri: String => Validation[String, String] =
    value => Validation.fromTry(Try(URI.create(value))).as(value).mapError(_ => s"is not a valid URI")

  def absoluteUri: URI => Validation[String, URI] =
    uri => Validation.fromPredicateWith(s"URI must be absolute")(uri)(_.isAbsolute)

  def fromValidations[A](
    typ: String,
    make: String => A,
    validations: List[String => Validation[String, String]],
  ): String => Either[String, A] = value =>
    ZValidation
      .validateAll(validations.map(_(value)))
      .as(make(value))
      .toEither
      .left
      .map(_.mkString(s"$typ ", ", ", "."))
}

trait IntValueCompanion[A <: IntValue] extends WithFrom[Int, A]
