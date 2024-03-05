/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.defaults

import zio.Chunk
import zio.NonEmptyChunk

import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdatePlan

object DefaultRdfData {

  /**
   * This data is automatically loaded during resetting of the triple store content initiated
   * through the `ResetTriplestoreContent` message. The main usage is in unit testing, where
   * we want a known state of the triple store data. If additional triples need to be loaded,
   * then a list of `RdfDataObject` instances containing the path and the name of the named graph
   * can be supplied to the `ResetTriplestoreContent` message.
   */
  val data: NonEmptyChunk[RdfDataObject] = NonEmptyChunk(
    RdfDataObject(
      path = "test_data/project_data/admin-data.ttl",
      name = "http://www.knora.org/data/admin",
    ),
    RdfDataObject(
      path = "test_data/project_data/permissions-data.ttl",
      name = "http://www.knora.org/data/permissions",
    ),
    RdfDataObject(
      path = "test_data/project_data/system-data.ttl",
      name = "http://www.knora.org/data/0000/SystemProject",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/something-onto.ttl",
      name = "http://www.knora.org/ontology/0001/something",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/images-onto.ttl",
      name = "http://www.knora.org/ontology/00FF/images",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/beol-onto.ttl",
      name = "http://www.knora.org/ontology/0801/beol",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/biblio-onto.ttl",
      name = "http://www.knora.org/ontology/0801/biblio",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/incunabula-onto.ttl",
      name = "http://www.knora.org/ontology/0803/incunabula",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/dokubib-onto.ttl",
      name = "http://www.knora.org/ontology/0804/dokubib",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/webern-onto.ttl",
      name = "http://www.knora.org/ontology/0806/webern",
    ),
  ) ++ Chunk(RepositoryUpdatePlan.builtInNamedGraphs.toSeq: _*)
}
