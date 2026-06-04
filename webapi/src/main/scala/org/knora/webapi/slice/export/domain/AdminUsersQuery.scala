/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraAdmin as KA

object AdminUsersQuery extends QueryBuilderHelper {

  /**
   * CONSTRUCT projecting only the `?user a knora-admin:User` typing triples from the admin data graph. The SHACL
   * data shapes check that every `attachedToUser` reference points to a `knora-admin:User` instance; the projection
   * keeps the validation model's size independent of the instance's full admin data (user profiles, groups,
   * projects, permissions).
   */
  def build: ConstructQuery = {
    val user = variable("user")
    Queries
      .CONSTRUCT(user.isA(KA.User))
      .where(user.isA(KA.User).from(Rdf.iri(adminDataNamedGraph.value)))
  }
}
