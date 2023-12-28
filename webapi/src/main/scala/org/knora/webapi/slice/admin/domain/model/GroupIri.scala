package org.knora.webapi.slice.admin.domain.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat

import dsp.valueobjects.Iri
import dsp.valueobjects.Iri.validateAndEscapeIri
import dsp.valueobjects.UuidUtil

final case class GroupIri private (value: String) extends AnyVal

object GroupIri {

  implicit val tapirCodec: Codec[String, GroupIri, CodecFormat.TextPlain] =
    Codec.string.mapEither(GroupIri.from)(_.value)

  def unsafeFrom(value: String): GroupIri = from(value).fold(e => throw new IllegalArgumentException(e), identity)

  def from(value: String): Either[String, GroupIri] =
    if (value.isEmpty) Left("Group IRI cannot be empty.")
    else if (!(Iri.isIri(value) && value.startsWith("http://rdfh.ch/groups/")))
      Left("Group IRI is invalid.")
    else if (UuidUtil.hasValidLength(value.split("/").last) && !UuidUtil.hasSupportedVersion(value))
      Left("Invalid UUID used to create IRI. Only versions 4 and 5 are supported.")
    else
      validateAndEscapeIri(value).toEither
        .map(GroupIri.apply)
        .left
        .map(_ => "Group IRI is invalid.")
}
