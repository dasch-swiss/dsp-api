/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import org.eclipse.rdf4j.model.vocabulary.RDFS

import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask

object isPropertyUsedInResourcesQuery extends QueryBuilderHelper {

  def build(propertyIri: PropertyIri, classIri: ResourceClassIri): Ask =
    val property      = toRdfIri(propertyIri)
    val resourceClass = toRdfIri(classIri)
    val (s, o)        = (variable("s"), variable("o"))

    val allResourcesOfClassWithProperty = s.has(property, o).andIsA(resourceClass)

    val classVar                     = variable("class")
    val subClassOfPattern            = classVar.has(zeroOrMore(RDFS.SUBCLASSOF), resourceClass)
    val allItemsBelongingToASubclass = s.has(property, o).andIsA(classVar).and(subClassOfPattern)

    val union = allResourcesOfClassWithProperty.union(allItemsBelongingToASubclass)
    Ask(s"ASK WHERE { ${union.getQueryString} }")
}
