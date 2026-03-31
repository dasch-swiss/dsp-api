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

  private val forbiddenNamespaces = List(
    "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
    "http://www.w3.org/2000/01/rdf-schema#",
    "http://www.w3.org/2002/07/owl#",
    "http://www.w3.org/2001/XMLSchema#",
    "https://www.w3.org/ns/shacl#",
    "http://datashapes.org/dash#",
  )

  private def validate(value: String): Either[String, OntologyMappingExternalIri] =
    for {
      _ <- checkHost(value)
      _ <- checkNamespace(value)
    } yield OntologyMappingExternalIri(value)

  private def checkHost(value: String): Either[String, Unit] = {
    val host = URI.create(value).getHost
    forbiddenHosts.find(h => host != null && host.contains(h)) match {
      case Some(h) => Left(s"OntologyMappingExternalIri must not contain host '$h': $value")
      case None    => Right(())
    }
  }

  private def checkNamespace(value: String): Either[String, Unit] =
    forbiddenNamespaces.find(ns => value.startsWith(ns)) match {
      case Some(ns) => Left(s"OntologyMappingExternalIri must not start with namespace '$ns': $value")
      case None     => Right(())
    }

  def from(value: String): Either[String, OntologyMappingExternalIri] =
    if (!Iri.isIri(value)) Left(s"OntologyMappingExternalIri is not a valid IRI: $value")
    else validate(value)

  def from(iriDto: IriDto): Either[String, OntologyMappingExternalIri] =
    validate(iriDto.value)
}
