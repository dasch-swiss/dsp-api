/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.standoff

import zio.prelude.Validation

import scala.util.matching.Regex

import dsp.errors.ValidationException
import dsp.valueobjects.Iri
import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants.KnoraBase as KB
import org.knora.webapi.messages.XmlPatterns
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagIriAttributeV2
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffTagV2

object StandoffStringUtil {

  // In XML import data, a standoff link tag that refers to a resource described in the import must have the
  // form defined by this regex.
  private val standoffLinkReferenceToClientIdForResourceRegex: Regex = ("^ref:(" + XmlPatterns.nCNamePattern + ")$").r

  /**
   * Checks that a string represents a valid resource identifier in a standoff link.
   *
   * @param s               the string to be checked.
   * @param acceptClientIDs if `true`, the function accepts either an IRI or an XML NCName prefixed by `ref:`.
   *                        The latter is used to refer to a client's ID for a resource that is described in an XML bulk import.
   *                        If `false`, only an IRI is accepted.
   * @param errorFun        a function that throws an exception. It will be called if the form of the string is invalid.
   * @return the same string.
   */
  def validateStandoffLinkResourceReference(s: String, acceptClientIDs: Boolean, errorFun: => Nothing): IRI =
    validateStandoffLinkResourceReference(s, acceptClientIDs).getOrElse(errorFun)

  private def validateStandoffLinkResourceReference(
    s: String,
    acceptClientIDs: Boolean,
  ): Validation[ValidationException, IRI] =
    if (acceptClientIDs && isStandoffLinkReferenceToClientIDForResource(s)) Validation.succeed(s)
    else Iri.validateAndEscapeIri(s)

  /**
   * Checks whether a string is a reference to a client's ID for a resource described in an XML bulk import.
   *
   * @param s the string to be checked.
   * @return `true` if the string is an XML NCName prefixed by `ref:`.
   */
  private def isStandoffLinkReferenceToClientIDForResource(s: String): Boolean = s match {
    case standoffLinkReferenceToClientIdForResourceRegex(_) => true
    case _                                                  => false
  }

  /**
   * Map over all standoff tags to collect IRIs that are referred to by linking standoff tags.
   *
   * @param standoffTags The list of [[StandoffTagV2]].
   * @return a set of Iris referred to in the [[StandoffTagV2]].
   */
  def getResourceIrisFromStandoffLinkTags(standoffTags: Seq[StandoffTagV2]): Seq[IRI] =
    standoffTags
      .filter(_.dataType.contains(StandoffDataTypeClasses.StandoffLinkTag))
      .flatMap(_.attributes)
      .collect { case attr: StandoffTagIriAttributeV2 => attr }
      .filter(_.standoffPropertyIri.toInternalIri.value == KB.StandoffTagHasLink)
      .map(_.value)

  /**
   * Creates a new standoff tag IRI based on a UUID.
   *
   * @param valueIri   the IRI of the text value containing the standoff tag.
   * @param startIndex the standoff tag's start index.
   * @return a standoff tag IRI.
   */
  def makeRandomStandoffTagIri(valueIri: IRI, startIndex: Int): IRI = s"$valueIri/standoff/$startIndex"
}
