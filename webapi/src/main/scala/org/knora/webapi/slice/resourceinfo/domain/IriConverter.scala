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
import org.knora.webapi.messages.StringFormatter

@accessible
trait IriConverter {
  def asInternalIri(iri: IRI): Task[InternalIri]
}

final case class IriConverterLive(stringFormatter: StringFormatter) extends IriConverter {
  def asInternalIri(iri: IRI): Task[InternalIri] =
    ZIO.attempt {
      stringFormatter.toSmartIri(iri).internalIri
    }.map(InternalIri(_))
}

object IriConverter {
  val layer: ZLayer[StringFormatter, Nothing, IriConverterLive] = ZLayer.fromFunction(IriConverterLive(_))
}
