/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import sttp.tapir.{Codec, CodecFormat}
import zio.json.JsonCodec

trait Value[A] {
  def value: A
}

trait WithFrom[-I, +A] {

  def from(in: I): Either[String, A]

  final def unsafeFrom(in: I): A =
    from(in).fold(e => throw new IllegalArgumentException(e), identity)
}

trait WithJsonCodec[StringValue <: Value[String]] { self: WithFrom[String, StringValue] =>
  implicit val zioJsonCodec: JsonCodec[StringValue] =
    JsonCodec.string.transformOrFail(self.from, _.value)
}

trait WithTapirCodec[StringValue <: Value[String]] { self: WithFrom[String, StringValue] =>
  implicit val tapirCodec: Codec[String, StringValue, CodecFormat.TextPlain] =
    Codec.string.mapEither(self.from)(_.value)
}
