/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object IsNodeUsedQuery {

  def build(nodeIri: ListIri): Ask = {
    val node = Iri.unsafeFrom(nodeIri.value)
    // The gui attribute references the node as a string of the form `hlist=<nodeIri>`.
    val guiAttributeValue = Literal.string(s"hlist=<${nodeIri.value}>")
    Ask(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |PREFIX salsah-gui: <http://www.knora.org/ontology/salsah-gui#>
               |
               |ASK
               |WHERE {
               |  {
               |    ?s salsah-gui:guiAttribute $guiAttributeValue .
               |  } UNION {
               |    ?s knora-base:valueHasListNode $node .
               |  }
               |}""".render,
    )
  }
}
