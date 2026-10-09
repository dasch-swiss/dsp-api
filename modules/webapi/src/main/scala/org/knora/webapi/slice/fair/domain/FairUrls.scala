/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain

object FairUrls {
  val JsonLd: String   = "application/ld+json"
  val DataCite: String = "application/vnd.datacite.datacite+json"

  /** The machine-readable representations of a resource, built from its canonical IRI only. */
  def representations(apiBaseUrl: String, facts: ResourceFacts): List[Candidate] = {
    val base =
      s"${apiBaseUrl.stripSuffix("/")}/fair/resources/${facts.iri.shortcode.value}/${facts.iri.resourceId.value}"
    List(Candidate(JsonLd, s"$base/metadata.jsonld"), Candidate(DataCite, s"$base/metadata.datacite.json"))
  }
}
