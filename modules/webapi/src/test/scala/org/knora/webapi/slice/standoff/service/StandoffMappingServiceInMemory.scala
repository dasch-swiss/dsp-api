/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.standoff.service

import zio.*

import dsp.errors.NotFoundException
import org.knora.webapi.IRI
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.GetMappingResponseV2
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.slice.common.StandoffMappingIri

/**
 * Deterministic [[StandoffMappingService]] test double: serves the mappings it was given from memory and dies on the
 * XSL and entity-resolution operations, which no standoff-conversion test exercises.
 */
final class StandoffMappingServiceInMemory(responses: Map[StandoffMappingIri, GetMappingResponseV2])
    extends StandoffMappingService {

  override def getMappingV2(mappingIri: StandoffMappingIri): Task[GetMappingResponseV2] =
    ZIO.fromOption(responses.get(mappingIri)).orElseFail(NotFoundException(s"No in-memory mapping for $mappingIri"))

  override def getXSLTransformation(xslTransformationIri: IRI): Task[String] =
    ZIO.dieMessage("getXSLTransformation is not stubbed in StandoffMappingServiceInMemory")

  override def getStandoffEntitiesFromMappingV2(
    mappingXMLtoStandoff: MappingXMLtoStandoff,
  ): Task[StandoffEntityInfoGetResponseV2] =
    ZIO.dieMessage("getStandoffEntitiesFromMappingV2 is not stubbed in StandoffMappingServiceInMemory")
}

object StandoffMappingServiceInMemory {

  /** A layer serving the given mappings, each keyed by its [[StandoffMappingIri]]. */
  def layer(mappings: (StandoffMappingIri, GetMappingResponseV2)*): ULayer[StandoffMappingService] =
    ZLayer.succeed(new StandoffMappingServiceInMemory(mappings.toMap))
}
