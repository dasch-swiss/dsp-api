/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service

import zio.ZIO

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.UnsafeZioRun

/**
 * This spec is used to test [[org.knora.webapi.slice.ontology.repo.service.OntologyCache]].
 */
class OntologyCacheSpec extends CoreSpec {

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_ontologies/books-onto.ttl",
      name = "http://www.knora.org/ontology/0001/books",
    ),
    RdfDataObject(
      path = "test_data/project_data/books-data.ttl",
      name = "http://www.knora.org/data/0001/books",
    ),
  )

  val CACHE_NOT_AVAILABLE_ERROR = "Cache not available"

  "The cache" should {
    "successfully load the cache data" in {
      UnsafeZioRun.runOrThrow(ZIO.serviceWithZIO[OntologyCache](_.getCacheData.map(_.ontologies))).size should equal(13)
    }
  }
}
