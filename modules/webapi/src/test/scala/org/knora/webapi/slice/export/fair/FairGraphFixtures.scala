/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import zio.json.ast.Json

import java.time.Instant

/** Graphs shared by the projection specs. */
object FairGraphFixtures {

  val orcid = "https://orcid.org/0000-0002-1825-0097"

  val ccBy   = LicenseFact("https://creativecommons.org/licenses/by/4.0/", "CC BY 4.0", Some("CC-BY-4.0"))
  val ccZero = LicenseFact("https://creativecommons.org/publicdomain/zero/1.0/", "CC0 1.0", Some("CC0-1.0"))

  val openWithFile: ResourceFairGraph = ResourceFairGraph(
    ark = "https://ark.dasch.swiss/ark:/72163/1/0868/abc123",
    pageUrl = "https://app.dasch.swiss/resource/0868/abc123",
    title = "Table 1",
    creators = Seq(
      Creator("Jane Doe", Some(CreatorKind.Person), Some(orcid)),
      Creator("John Roe", None, None),
    ),
    dateCreated = Instant.parse("2024-03-01T10:15:30Z"),
    dateModified = Some(Instant.parse("2025-01-02T03:04:05Z")),
    license = Some(ccBy),
    copyrightHolder = Some("University of Basel"),
    generalType = "Dataset",
    accessLevel = AccessLevel.FullOpen,
    file = Some(
      FileFacts(
        "https://ingest.dasch.swiss/projects/0868/assets/xyz/original",
        Some("table1.csv"),
        Some("text/csv"),
        Some(1234L),
        Some(ccZero),
      ),
    ),
    projectArk = "https://ark.dasch.swiss/ark:/72163/1/0868",
    projectName = "Example project",
    resourceClassIri = "http://api.dasch.swiss/ontology/0868/example/v2#Table",
  )

  val restricted: ResourceFairGraph = openWithFile.copy(accessLevel = AccessLevel.Restricted, file = None)

  val versioned: ResourceFairGraph = openWithFile.copy(
    ark = "https://ark.dasch.swiss/ark:/72163/1/0868/abc123.20240301T101530Z",
    file = None,
  )

  val bare: ResourceFairGraph = openWithFile.copy(
    creators = Seq.empty,
    license = None,
    copyrightHolder = None,
    dateModified = None,
    file = None,
  )

  def containsNull(j: Json): Boolean = j match {
    case Json.Null    => true
    case Json.Obj(fs) => fs.exists(f => containsNull(f._2))
    case Json.Arr(es) => es.exists(containsNull)
    case _            => false
  }
}
