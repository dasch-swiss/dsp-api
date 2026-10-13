/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain

import java.time.Instant

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.common.ResourceIri

object ResourceFactsFixture {

  val facts: ResourceFacts = ResourceFacts(
    iri = ResourceIri.unsafeFrom("http://rdfh.ch/0803/0b03b0a6e6be"),
    ark = "https://ark.dasch.swiss/ark:/72163/1/0803/lklK7rVuVOmpBZYWrF8o=gh",
    pageUrl = "https://app.example.org/resource/0803/0b03b0a6e6be",
    title = "A resource",
    description = None,
    creators = Creators(List("Project"), CreatorSource.ProjectOrganisation),
    licence = None,
    dateCreated = Instant.parse("2024-01-02T03:04:05Z"),
    dateModified = None,
    typeOfData = None,
    classIri = "http://api.example.org/ontology/0803/x/v2#Thing",
    classLabel = "Thing",
    file = None,
    accessLevel = AccessLevel.FullOpenAccess,
    projectArk = "https://ark.dasch.swiss/ark:/72163/1/0803",
    shortcode = Shortcode.unsafeFrom("0803"),
  )
}
