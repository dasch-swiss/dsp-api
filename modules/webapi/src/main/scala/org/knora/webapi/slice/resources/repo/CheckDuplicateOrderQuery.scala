/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object CheckDuplicateOrderQuery {

  def build(resourceIri: InternalIri, propertyIri: SmartIri, order: Int): Ask = {
    val resource   = Iri.unsafeFrom(resourceIri.value)
    val property   = Iri.unsafeFrom(propertyIri.toInternalSchema.toIri)
    val orderValue = Literal.int(order)
    Ask(
      // OPTIONAL so values lacking knora-base:isDeleted (e.g. legacy data) are treated as non-deleted.
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |ASK
               |WHERE {
               |  $resource $property ?existingValue .
               |  ?existingValue knora-base:valueHasOrder $orderValue .
               |  OPTIONAL { ?existingValue knora-base:isDeleted ?isDeleted . }
               |  FILTER ( !bound(?isDeleted) || ?isDeleted = false )
               |}""".render,
    )
  }
}
