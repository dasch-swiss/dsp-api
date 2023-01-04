/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.domain

import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible

import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter

@accessible
trait IriConverter {
  def asInternalSmartIri(iri: String): Task[SmartIri]
  def asSmartIri(iri: IRI): Task[SmartIri]

  def asInternalIri(iri: IRI): Task[InternalIri]           = asSmartIri(iri).mapAttempt(_.toInternalIri)
  def asInternalSmartIri(iri: InternalIri): Task[SmartIri] = asInternalSmartIri(iri.value)

  def getOntologyIriFromClassIri(iri: InternalIri): Task[SmartIri] =
    asInternalSmartIri(iri.value).mapAttempt(_.getOntologyFromEntity)
}

final case class IriConverterLive(sf: StringFormatter) extends IriConverter {
  override def asInternalSmartIri(iri: IRI): Task[SmartIri] = ZIO.attempt(sf.toInternalSmartIri(iri))
  override def asSmartIri(iri: IRI): Task[SmartIri]         = ZIO.attempt(sf.toSmartIri(iri))
}

object IriConverter {
  val layer: ZLayer[StringFormatter, Nothing, IriConverterLive] = ZLayer.fromFunction(IriConverterLive(_))
}
