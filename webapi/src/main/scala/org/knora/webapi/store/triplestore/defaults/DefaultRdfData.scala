/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.defaults

import zio.NonEmptyChunk

import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

object DefaultRdfData {

  /**
   * This data is automatically loaded during resetting of the triple store content initiated
   * through the `ResetTriplestoreContent` message. The main usage is in unit testing, where
   * we want a known state of the triple store data. If additional triples need to be loaded,
   * then a list of `RdfDataObject` instances containing the path and the name of the named graph
   * can be supplied to the `ResetTriplestoreContent` message.
   */
  val data = NonEmptyChunk(
    RdfDataObject(
      path = "knora-ontologies/knora-admin.ttl",
      name = "http://www.knora.org/ontology/knora-admin"
    ),
    RdfDataObject(
      path = "knora-ontologies/knora-base.ttl",
      name = "http://www.knora.org/ontology/knora-base"
    ),
    RdfDataObject(
      path = "knora-ontologies/standoff-onto.ttl",
      name = "http://www.knora.org/ontology/standoff"
    ),
    RdfDataObject(
      path = "knora-ontologies/standoff-data.ttl",
      name = "http://www.knora.org/data/standoff"
    ),
    RdfDataObject(
      path = "knora-ontologies/salsah-gui.ttl",
      name = "http://www.knora.org/ontology/salsah-gui"
    ),
    RdfDataObject(
      path = "test_data/all_data/admin-data.ttl",
      name = "http://www.knora.org/data/admin"
    ),
    RdfDataObject(
      path = "test_data/all_data/permissions-data.ttl",
      name = "http://www.knora.org/data/permissions"
    ),
    RdfDataObject(
      path = "test_data/all_data/system-data.ttl",
      name = "http://www.knora.org/data/0000/SystemProject"
    ),
    RdfDataObject(
      path = "test_data/ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything"
    ),
    RdfDataObject(
      path = "test_data/ontologies/something-onto.ttl",
      name = "http://www.knora.org/ontology/0001/something"
    ),
    RdfDataObject(
      path = "test_data/ontologies/images-onto.ttl",
      name = "http://www.knora.org/ontology/00FF/images"
    ),
    RdfDataObject(
      path = "test_data/ontologies/beol-onto.ttl",
      name = "http://www.knora.org/ontology/0801/beol"
    ),
    RdfDataObject(
      path = "test_data/ontologies/biblio-onto.ttl",
      name = "http://www.knora.org/ontology/0801/biblio"
    ),
    RdfDataObject(
      path = "test_data/ontologies/incunabula-onto.ttl",
      name = "http://www.knora.org/ontology/0803/incunabula"
    ),
    RdfDataObject(
      path = "test_data/ontologies/dokubib-onto.ttl",
      name = "http://www.knora.org/ontology/0804/dokubib"
    ),
    RdfDataObject(
      path = "test_data/ontologies/webern-onto.ttl",
      name = "http://www.knora.org/ontology/0806/webern"
    )
  )
}
