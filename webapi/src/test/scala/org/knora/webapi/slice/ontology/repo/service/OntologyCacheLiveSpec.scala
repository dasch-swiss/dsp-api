/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo.service

import zio.*
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

/**
 * This spec is used to test [[org.knora.webapi.slice.ontology.repo.service.OntologyCache]].
 */
object OntologyCacheLiveSpec extends ZIOSpecDefault {

  private val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_ontologies/books-onto.ttl",
      name = "http://www.knora.org/ontology/0001/books",
    ),
  )

  override val spec = suite("OntologyCache")(
    test("should return an error if the cache is not available") {
      for {
        _     <- ZIO.serviceWithZIO[TriplestoreService](_.resetTripleStoreContent(rdfDataObjects))
        _     <- ZIO.serviceWithZIO[OntologyCache](_.refreshCache())
        cache <- ZIO.serviceWithZIO[OntologyCache](_.getCacheData.map(_.ontologies))
      } yield assertTrue(cache.size == 13)
    },
  ).provide(OntologyCacheLive.layer, TriplestoreServiceInMemory.emptyLayer, StringFormatter.test)
}
