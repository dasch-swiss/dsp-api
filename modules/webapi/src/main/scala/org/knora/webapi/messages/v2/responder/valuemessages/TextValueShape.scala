/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.valuemessages

import dsp.errors.InconsistentRepositoryDataException
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.common.domain.InternalIri

/**
 * The text type and mapping of a text value read from the triplestore.
 */
final case class TextValueShape(textValueType: TextValueType, mappingIri: Option[StandoffMappingIri])

object TextValueShape {

  /**
   * Derives the [[TextValueShape]] of a text value. A value with stored XML is formatted text and must have a mapping.
   * Otherwise it is formatted text with a mapping if it has standoff, and unformatted text without a mapping if not.
   *
   * @param valueIri            the value IRI, used in error messages.
   * @param hasStoredXml        whether the value is read with its stored XML.
   * @param hasStandoff         whether the value has standoff links in the query result.
   * @param mappingIri          the value's `knora-base:valueHasMapping`, if any.
   * @param storedTextValueType the value's `knora-base:hasTextValueType`, if any.
   */
  def derive(
    valueIri: IRI,
    hasStoredXml: Boolean,
    hasStandoff: Boolean,
    mappingIri: Option[IRI],
    storedTextValueType: Option[IRI],
  ): Either[InconsistentRepositoryDataException, TextValueShape] =
    if (!hasStoredXml && !hasStandoff) Right(TextValueShape(TextValueType.UnformattedText, None))
    else if (hasStoredXml && mappingIri.isEmpty)
      Left(InconsistentRepositoryDataException(s"Text value $valueIri has stored XML but no mapping"))
    else
      for {
        mapping <- mappingIri.fold(Right(None))(iri =>
                     StandoffMappingIri
                       .from(iri)
                       .left
                       .map(_ => InconsistentRepositoryDataException(s"Invalid mapping IRI $iri"))
                       .map(Some(_)),
                   )
      } yield TextValueShape(textValueType(storedTextValueType, mapping), mapping)

  private def textValueType(stored: Option[IRI], mapping: Option[StandoffMappingIri]): TextValueType = {
    val inferred = mapping.map(_.value) match
      case None                                              => TextValueType.UnformattedText
      case Some(OntologyConstants.KnoraBase.StandardMapping) => TextValueType.FormattedText
      case Some(iri)                                         => TextValueType.CustomFormattedText(InternalIri(iri))
    stored.flatMap {
      case OntologyConstants.KnoraBase.UnformattedText     => Some(TextValueType.UnformattedText)
      case OntologyConstants.KnoraBase.FormattedText       => Some(TextValueType.FormattedText)
      case OntologyConstants.KnoraBase.CustomFormattedText =>
        mapping.map(m => TextValueType.CustomFormattedText(InternalIri(m.value)))
      case _ => None
    }.getOrElse(inferred)
  }
}
