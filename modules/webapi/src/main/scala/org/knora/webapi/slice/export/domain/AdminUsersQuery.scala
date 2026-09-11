/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

object AdminUsersQuery {

  /**
   * CONSTRUCT projecting only the `?user a knora-admin:User` typing triples from the admin data graph. The SHACL
   * data shapes check that every `attachedToUser` reference points to a `knora-admin:User` instance; the projection
   * keeps the validation model's size independent of the instance's full admin data (user profiles, groups,
   * projects, permissions).
   */
  def build: Construct = {
    val adminGraph = Iri.unsafeFrom(adminDataNamedGraph.value)
    Construct(
      sparql"""|PREFIX knora-admin: <http://www.knora.org/ontology/knora-admin#>
               |
               |CONSTRUCT { ?user a knora-admin:User . }
               |WHERE {
               |  GRAPH $adminGraph { ?user a knora-admin:User . }
               |}""".render,
    )
  }
}
