/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model

import java.net.URI

import dsp.valueobjects.Iri
import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue

final case class ExternalIri private (value: String) extends StringValue

object ExternalIri extends StringValueCompanion[ExternalIri] {

  private val forbiddenHosts = List("knora.org", "dasch.swiss")

  private def checkHost(value: String): Either[String, ExternalIri] = {
    val host = URI.create(value).getHost
    forbiddenHosts.find(h => host != null && host.contains(h)) match {
      case Some(h) => Left(s"ExternalIri must not contain host '$h': $value")
      case None    => Right(ExternalIri(value))
    }
  }

  def from(value: String): Either[String, ExternalIri] =
    if (!Iri.isIri(value)) Left(s"ExternalIri is not a valid IRI: $value")
    else checkHost(value)

  def from(iriDto: IriDto): Either[String, ExternalIri] =
    checkHost(iriDto.value)
}
