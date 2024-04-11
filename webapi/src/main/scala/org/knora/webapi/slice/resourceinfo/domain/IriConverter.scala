/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.domain

import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter

final case class IriConverter(sf: StringFormatter) {
  def asSmartIri(iri: String): Task[SmartIri]              = ZIO.attempt(sf.toSmartIri(iri, requireInternal = false))
  def asSmartIris(iris: Set[String]): Task[Set[SmartIri]]  = ZIO.foreach(iris)(asSmartIri)
  def asInternalIri(iri: String): Task[InternalIri]        = asSmartIri(iri).mapAttempt(_.toInternalIri)
  def asInternalSmartIri(iri: String): Task[SmartIri]      = asSmartIri(iri).mapAttempt(_.toOntologySchema(InternalSchema))
  def asInternalSmartIri(iri: InternalIri): Task[SmartIri] = asInternalSmartIri(iri.value)
  def asInternalSmartIri(iri: SmartIri): Task[SmartIri]    = ZIO.attempt(iri.toOntologySchema(InternalSchema))
  def asExternalIri(iri: InternalIri): Task[String] =
    asInternalSmartIri(iri.value).mapAttempt(_.toOntologySchema(ApiV2Complex)).map(_.toIri)
  def getOntologyIriFromClassIri(iri: InternalIri): Task[InternalIri] =
    getOntologySmartIriFromClassIri(iri).mapAttempt(_.toInternalIri)
  def getOntologySmartIriFromClassIri(iri: InternalIri): Task[SmartIri] =
    asInternalSmartIri(iri.value).mapAttempt(_.getOntologyFromEntity)
}

object IriConverter {
  val layer: ZLayer[StringFormatter, Nothing, IriConverter] = ZLayer.derive[IriConverter]
}
