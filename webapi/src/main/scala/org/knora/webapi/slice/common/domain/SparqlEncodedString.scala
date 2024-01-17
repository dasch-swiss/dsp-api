package org.knora.webapi.slice.common.domain

import dsp.valueobjects.Iri
import org.knora.webapi.slice.common.WithFrom.WithFromString
import sttp.tapir.{Codec, CodecFormat}
import zio.json.JsonCodec

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
