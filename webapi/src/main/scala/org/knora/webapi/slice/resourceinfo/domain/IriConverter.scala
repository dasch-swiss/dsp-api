/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.domain

import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.macros.accessible
import scala.util.Try

import org.knora.webapi.IRI
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter

@accessible
trait IriConverter {
  def asInternalIri(iri: IRI): Task[InternalIri]
  def asSmartIri(iri: InternalIri): Task[SmartIri]
  def getOntologyIriFromClassIri(iri: InternalIri): Task[SmartIri]
}

final case class IriConverterLive(stringFormatter: StringFormatter) extends IriConverter {
  def asInternalIri(iri: IRI): Task[InternalIri] =
    ZIO.attempt {
      stringFormatter.toSmartIri(iri, requireInternal = true).internalIri
    }.map(InternalIri(_))

  override def asSmartIri(iri: InternalIri): Task[SmartIri] =
    ZIO.attempt(stringFormatter.toSmartIri(iri.value, requireInternal = true))

  override def getOntologyIriFromClassIri(iri: InternalIri): Task[SmartIri] =
    ZIO.fromTry {
      Try(stringFormatter.toSmartIri(iri.value, requireInternal = true))
        .flatMap(smartIri => Try(smartIri.getOntologyFromEntity))
    }
}

object IriConverter {
  val layer: ZLayer[StringFormatter, Nothing, IriConverterLive] = ZLayer.fromFunction(IriConverterLive(_))
}
