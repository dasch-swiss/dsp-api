/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.standoff

import com.google.gwt.safehtml.shared.UriUtils.encodeAllowEscapes
import org.apache.commons.validator.routines.UrlValidator

import scala.util.Try

import dsp.valueobjects.Iri.IRI

/**
 * Validates the target URI of a standoff link (`knora-base:valueHasUri`).
 *
 * Unlike `Iri.validateAndEscapeIri`, which validates every SmartIri and so rejects `//` in the path, this accepts it,
 * as in archived URLs such as `https://web.archive.org/web/<timestamp>/https://example.org/`.
 */
object StandoffLinkUri {

  private val validator =
    new UrlValidator(Array("http", "https"), UrlValidator.ALLOW_LOCAL_URLS + UrlValidator.ALLOW_2_SLASHES)

  /**
   * @return the URI escaped as `Iri.validateAndEscapeIri` escapes it, or `None` if it is not a valid http or https URL.
   */
  def validate(s: String): Option[IRI] =
    Try(encodeAllowEscapes(s)).toOption.filter(validator.isValid)
}
