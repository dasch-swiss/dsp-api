/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import zio.json.JsonCodec

import java.net.URI

import dsp.valueobjects.Iri
import org.knora.webapi.slice.api.admin.Codecs.TapirCodec
import org.knora.webapi.slice.api.admin.Codecs.ZioJsonCodec
import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue

final case class OntologyMappingExternalIri private (value: String) extends StringValue

object OntologyMappingExternalIri extends StringValueCompanion[OntologyMappingExternalIri] {

  given JsonCodec[OntologyMappingExternalIri]                            = ZioJsonCodec.stringCodec(from)
  given Codec[String, OntologyMappingExternalIri, CodecFormat.TextPlain] = TapirCodec.stringCodec(from)

  private val forbiddenHosts = List("knora.org", "dasch.swiss")

  private def checkHost(value: String): Either[String, OntologyMappingExternalIri] = {
    val host = URI.create(value).getHost
    forbiddenHosts.find(h => host != null && host.contains(h)) match {
      case Some(h) => Left(s"OntologyMappingExternalIri must not contain host '$h': $value")
      case None    => Right(OntologyMappingExternalIri(value))
    }
  }

  def from(value: String): Either[String, OntologyMappingExternalIri] =
    if (!Iri.isIri(value)) Left(s"OntologyMappingExternalIri is not a valid IRI: $value")
    else checkHost(value)

  def from(iriDto: IriDto): Either[String, OntologyMappingExternalIri] =
    checkHost(iriDto.value)
}
