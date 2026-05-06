/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.standoff.service

import zio.*

import org.knora.webapi.IRI
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.slice.common.StandoffMappingIri

/**
 * No-op `StandoffMappingService` stub for specs that wire the layer graph but
 * never exercise the mapping/XSLT code paths at runtime.
 */
final class StandoffMappingServiceFake extends StandoffMappingService {
  override def getMappingV2(mappingIri: StandoffMappingIri): Task[GetMappingResponseV2] =
    ZIO.die(new UnsupportedOperationException("StandoffMappingServiceFake.getMappingV2"))

  override def getXSLTransformation(xslTransformationIri: IRI): Task[String] =
    ZIO.die(new UnsupportedOperationException("StandoffMappingServiceFake.getXSLTransformation"))

  override def getStandoffEntitiesFromMappingV2(
    mappingXMLtoStandoff: MappingXMLtoStandoff,
  ): Task[StandoffEntityInfoGetResponseV2] =
    ZIO.die(new UnsupportedOperationException("StandoffMappingServiceFake.getStandoffEntitiesFromMappingV2"))
}

object StandoffMappingServiceFake {
  val layer: ULayer[StandoffMappingService] = ZLayer.succeed(new StandoffMappingServiceFake)
}
