/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import zio.json.JsonCodec

import org.knora.webapi.slice.common.Value.StringValue

trait Value[A] extends Any {
  def value: A
}

object Value {
  type StringValue  = Value[String]
  type BooleanValue = Value[Boolean]
}

trait WithFrom[-I, +A] {

  def from(in: I): Either[String, A]

  final def unsafeFrom(in: I): A =
    from(in).fold(e => throw new IllegalArgumentException(e), identity)
}

trait WithJsonCodec[S <: StringValue] { self: WithFrom[String, S] =>
  implicit val zioJsonCodec: JsonCodec[S] =
    JsonCodec.string.transformOrFail(self.from, _.value)
}

trait WithTapirCodec[S <: StringValue] { self: WithFrom[String, S] =>
  implicit val tapirCodec: Codec[String, S, CodecFormat.TextPlain] =
    Codec.string.mapEither(self.from)(_.value)
}

trait StringBasedValueCompanionWithCodecs[A <: StringValue]
    extends WithFrom[String, A]
    with WithJsonCodec[A]
    with WithTapirCodec[A]
