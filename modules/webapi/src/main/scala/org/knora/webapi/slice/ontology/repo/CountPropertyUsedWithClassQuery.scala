/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/**
 * Query to count how many times a property is used with instances of a class or its subclasses.
 * Returns all instances of the class with the count of how often each uses the property.
 */
object CountPropertyUsedWithClassQuery {

  /**
   * Build a SELECT query that returns all instances of a class with the count of  how many times they use a specific property.
   * The query excludes deleted resources and values.
   *
   * @param propertyIri the IRI of the property to check
   * @param classIri    the IRI of the class to check instances of
   * @return a Select query
   */
  def build(propertyIri: PropertyIri, classIri: ResourceClassIri): Select = {
    val property = Iri.unsafeFrom(propertyIri.toInternalSchema.toIri)
    val clazz    = Iri.unsafeFrom(classIri.toInternalSchema.toIri)

    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT ?subject (COUNT(?object) AS ?count)
               |WHERE {
               |  ?subject a $clazz .
               |  MINUS { ?subject knora-base:isDeleted true . }
               |  OPTIONAL {
               |    ?subject $property ?object .
               |    MINUS { ?object knora-base:isDeleted true . }
               |  }
               |}
               |GROUP BY ?subject""".render,
    )
  }
}
