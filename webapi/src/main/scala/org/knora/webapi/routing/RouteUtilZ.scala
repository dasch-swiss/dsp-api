/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import zio.*

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.ontology.domain.service.IriConverter

object RouteUtilZ { self =>

  def toSmartIri(s: String): ZIO[IriConverter, Throwable, SmartIri] =
    ZIO.serviceWithZIO[IriConverter](_.asSmartIri(s))

  def toSmartIri(s: String, errorMsg: String): ZIO[IriConverter, BadRequestException, SmartIri] =
    toSmartIri(s).orElseFail(BadRequestException(errorMsg))

  def randomUuid(): UIO[UUID] = Random.nextUUID

  def toSparqlEncodedString(s: String, errorMsg: String): IO[BadRequestException, String] =
    ZIO.fromOption(Iri.toSparqlEncodedString(s)).orElseFail(BadRequestException(errorMsg))

  def externalOntologyIri(str: String): ZIO[IriConverter, BadRequestException, OntologyIri] = self
    .ontologyIri(str)
    .filterOrFail(_.isExternal)(())
    .orElseFail(BadRequestException(s"Invalid external ontology IRI: $str"))

  def ontologyIri(str: String): ZIO[IriConverter, BadRequestException, OntologyIri] = self
    .toSmartIri(str)
    .flatMap(s => ZIO.fromEither(OntologyIri.from(s)))
    .orElseFail(BadRequestException(s"Invalid ontology IRI: $str"))
}
