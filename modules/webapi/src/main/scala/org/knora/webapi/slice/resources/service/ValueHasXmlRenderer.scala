/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import zio.*

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.IRI
import org.knora.webapi.InternalSchema
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueType
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.standoff.service.StandoffMappingService

/** Loads mappings and renders the canonical XML of stored text values for the `valueHasXml` backfill. */
trait ValueHasXmlRenderer {

  def loadMapping(mappingIri: IRI): Task[MappingXMLtoStandoff]

  /**
   * Renders the XML of the value, or `None` if the value has no standoff or is unformatted text.
   * The other writer of `valueHasXml`, the value write path, renders through the same
   * `TextValueContentV2.computedValueHasXml`.
   */
  def render(value: StoredTextValue, mapping: MappingXMLtoStandoff): Task[Option[String]]
}

final case class ValueHasXmlRendererLive(
  mappingService: StandoffMappingService,
  standoffTagUtil: StandoffTagUtilV2,
) extends ValueHasXmlRenderer {

  override def loadMapping(mappingIri: IRI): Task[MappingXMLtoStandoff] =
    for {
      iri      <- toMappingIri(mappingIri)
      response <- mappingService.getMappingV2(iri)
    } yield response.mapping

  /** The other writer of `valueHasXml` is the value write path; both render via `TextValueContentV2.computedValueHasXml`. */
  override def render(value: StoredTextValue, mapping: MappingXMLtoStandoff): Task[Option[String]] =
    for {
      iri  <- toMappingIri(value.mappingIri)
      tags <- standoffTagUtil.createStandoffTagsV2FromSelectResults(
                value.standoffNodes,
                KnoraSystemInstances.Users.SystemUser,
              )
      content = TextValueContentV2(
                  ontologySchema = InternalSchema,
                  maybeValueHasString = Some(value.valueHasString),
                  textValueType = textValueType(value),
                  standoff = tags,
                  mappingIri = Some(iri),
                  mapping = Some(mapping),
                )
      xml <- ZIO.attempt(content.computedValueHasXml)
    } yield xml

  private def toMappingIri(mappingIri: IRI): Task[StandoffMappingIri] =
    ZIO
      .fromEither(StandoffMappingIri.from(mappingIri))
      .mapError(msg => InconsistentRepositoryDataException(s"Invalid mapping IRI $mappingIri: $msg"))

  private def textValueType(value: StoredTextValue): TextValueType =
    value.textValueType match {
      case Some(KnoraBase.UnformattedText)     => TextValueType.UnformattedText
      case Some(KnoraBase.CustomFormattedText) => TextValueType.CustomFormattedText(InternalIri(value.mappingIri))
      case _                                   => TextValueType.FormattedText
    }
}

object ValueHasXmlRendererLive {
  val layer = ZLayer.derive[ValueHasXmlRendererLive]
}
