/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

/** Display names of the classes and properties of the view restrictions reports, derived from their IRIs. */
object ViewRestrictionsIriNames {

  /** The part of the IRI after the last `#` or `/`. */
  def localName(iri: String): String = iri.split(Array('#', '/')).lastOption.getOrElse(iri)

  /** The last path segment of the IRI before its `#`. */
  def ontologyName(iri: String): String = {
    val beforeHash = iri.split('#').headOption.getOrElse(iri)
    beforeHash.split('/').lastOption.getOrElse(beforeHash)
  }
}
