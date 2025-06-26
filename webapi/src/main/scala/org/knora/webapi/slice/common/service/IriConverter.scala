/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.service

import zio.IO
import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.domain.InternalIri

final case class IriConverter(sf: StringFormatter) {
  def asSmartIri(iri: String): Task[SmartIri]              = ZIO.attempt(sf.toSmartIri(iri, requireInternal = false))
  def asSmartIris(iris: Set[String]): Task[Set[SmartIri]]  = ZIO.foreach(iris)(asSmartIri)
  def asInternalIri(iri: String): Task[InternalIri]        = asSmartIri(iri).mapAttempt(_.toInternalIri)
  def asInternalSmartIri(iri: String): Task[SmartIri]      = asSmartIri(iri).mapAttempt(_.toOntologySchema(InternalSchema))
  def asInternalSmartIri(iri: InternalIri): Task[SmartIri] = asInternalSmartIri(iri.value)
  def asInternalSmartIri(iri: SmartIri): Task[SmartIri]    = ZIO.attempt(iri.toOntologySchema(InternalSchema))
  def asExternalIri(iri: InternalIri): Task[String] =
    asInternalSmartIri(iri.value).mapAttempt(_.toComplexSchema).map(_.toIri)
  def asExternalIri(iri: String): Task[String] = asSmartIri(iri).mapAttempt(_.toComplexSchema).map(_.toIri)
  def getOntologyIriFromClassIri(iri: InternalIri): Task[InternalIri] =
    getOntologySmartIriFromClassIri(iri).mapAttempt(_.toInternalIri)
  def getOntologySmartIriFromClassIri(iri: InternalIri): Task[SmartIri] =
    asInternalSmartIri(iri.value).mapAttempt(_.getOntologyFromEntity)
  def isKnoraDataIri(iri: String): Task[Boolean] = asSmartIri(iri).map(_.isKnoraDataIri)

  def asResourceClassIriApiV2Complex(iri: String): IO[String, ResourceClassIri] =
    asSmartIri(iri).mapError(_.getMessage).flatMap(sIri => ZIO.fromEither(ResourceClassIri.fromApiV2Complex(sIri)))
  def asResourceClassIri(iri: String): IO[String, ResourceClassIri] =
    asSmartIri(iri).mapError(_.getMessage).flatMap(sIri => ZIO.fromEither(ResourceClassIri.from(sIri)))

  def asPropertyIriApiV2Complex(iri: String): IO[String, PropertyIri] =
    asSmartIri(iri).mapError(_.getMessage).flatMap(sIri => ZIO.fromEither(PropertyIri.fromApiV2Complex(sIri)))
  def asPropertyIri(iri: String): IO[String, PropertyIri] =
    asSmartIri(iri).mapError(_.getMessage).flatMap(sIri => ZIO.fromEither(PropertyIri.from(sIri)))

  def asResourceIri(iri: String): IO[String, ResourceIri] =
    asSmartIri(iri).mapError(_.getMessage).flatMap(sIri => ZIO.fromEither(ResourceIri.from(sIri)))

  def asOntologyIriApiV2Complex(iri: String): IO[String, OntologyIri] =
    asSmartIri(iri).mapError(_.getMessage).flatMap(sIri => ZIO.fromEither(OntologyIri.fromApiV2Complex(sIri)))
  def asOntologyIri(iri: String): IO[String, OntologyIri] =
    asSmartIri(iri).mapError(_.getMessage).flatMap(sIri => ZIO.fromEither(OntologyIri.from(sIri)))
}

object IriConverter {
  val layer = ZLayer.derive[IriConverter]
}
