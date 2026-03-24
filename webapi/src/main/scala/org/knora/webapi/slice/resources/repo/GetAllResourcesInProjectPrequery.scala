/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import org.knora.webapi.IRI
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object GetAllResourcesInProjectPrequery extends QueryBuilderHelper {

  def build(projectIri: IRI): SelectQuery = {
    val resource     = variable("resource")
    val resourceType = variable("resourceType")
    val creationDate = variable("creationDate")

    val wherePattern = resource
      .has(KnoraBase.attachedToProject, Rdf.iri(projectIri))
      .and(resourceType.has(zeroOrMore(RDFS.SUBCLASSOF), KnoraBase.Resource))
      .and(resource.has(Rdf.iri(RDF.TYPE.stringValue()), resourceType))
      .and(resource.has(KnoraBase.creationDate, creationDate))

    Queries
      .SELECT(resource)
      .distinct()
      .prefix(RDF.NS, RDFS.NS, KnoraBase.NS)
      .where(wherePattern)
      .orderBy(creationDate.desc())
  }
}
