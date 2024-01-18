/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.domain

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import zio.json.JsonCodec

import dsp.valueobjects.Iri
import org.knora.webapi.slice.common.WithFrom.WithFromString

final case class SparqlEncodedString private (value: String) extends AnyVal

object SparqlEncodedString extends WithFromString[SparqlEncodedString] {

  implicit val codec: JsonCodec[SparqlEncodedString] =
    JsonCodec[String].transformOrFail(SparqlEncodedString.from, _.value)

  implicit val tapirCodec: Codec[String, SparqlEncodedString, CodecFormat.TextPlain] =
    Codec.string.mapEither(SparqlEncodedString.from)(_.value)

  def from(str: String): Either[String, SparqlEncodedString] =
    Iri
      .toSparqlEncodedString(str)
      .map(SparqlEncodedString.apply)
      .toRight(s"May not be empty or contain a line break: '$str'")
}
