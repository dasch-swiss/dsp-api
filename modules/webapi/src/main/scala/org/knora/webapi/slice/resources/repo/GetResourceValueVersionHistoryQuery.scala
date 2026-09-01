/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import java.time.Instant

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.OntologyConstants.KnoraBase.KnoraBasePrefixExpansion
import org.knora.webapi.messages.OntologyConstants.Rdfs.SubPropertyOf
import org.knora.webapi.slice.common.ResourceIri

object GetResourceValueVersionHistoryQuery {

  private def kb(name: String): Iri = Iri.unsafeFrom(KnoraBasePrefixExpansion + name)

  private val attachedToUser    = kb("attachedToUser")
  private val deleteDate        = kb("deleteDate")
  private val deletedBy         = kb("deletedBy")
  private val hasValue          = kb("hasValue")
  private val isDeleted         = kb("isDeleted")
  private val previousValue     = kb("previousValue")
  private val valueCreationDate = kb("valueCreationDate")
  private val subPropertyOf     = Iri.unsafeFrom(SubPropertyOf)

  private val author       = Variable("author")
  private val currentValue = Variable("currentValue")
  private val property     = Variable("property")
  private val valueObject  = Variable("valueObject")
  private val versionDate  = Variable("versionDate")

  /** Indentation of the patterns inside the WHERE block, shared by the template and the joins. */
  private val indent = Fragment.raw("\n  ")

  def build(
    resourceIri: ResourceIri,
    withDeletedResource: Boolean = false,
    maybeStartDate: Option[Instant] = None,
    maybeEndDate: Option[Instant] = None,
  ): String = {
    val resource = Iri.unsafeFrom(resourceIri.value)

    // A deleted resource's own deletion counts as a history entry only when deleted resources are
    // requested; otherwise the resource itself must not be deleted.
    val notDeleted      = Option.unless(withDeletedResource)(sparql"$resource $isDeleted false .")
    val resourceDeleted = Option.when(withDeletedResource)(
      join(
        sparql"$resource $deleteDate $versionDate .",
        sparql"$resource $attachedToUser $author .",
      ),
    )

    val versionBranches = Seq(
      join(
        sparql"$valueObject $valueCreationDate $versionDate .",
        sparql"$valueObject $attachedToUser $author .",
      ),
      join(
        sparql"$valueObject $deleteDate $versionDate .",
        sparql"$valueObject $deletedBy $author .",
      ),
    ) ++ resourceDeleted

    val where = Fragment.join(
      Seq(sparql"$resource $property $currentValue .") ++
        notDeleted ++
        Seq(
          sparql"$property $subPropertyOf* $hasValue .",
          sparql"$currentValue $previousValue* $valueObject .",
          Fragments.union(versionBranches*),
        ) ++
        maybeStartDate.map(d => Fragments.filter(sparql"$versionDate >= ${Literal.dateTime(d)}")) ++
        maybeEndDate.map(d => Fragments.filter(sparql"$versionDate < ${Literal.dateTime(d)}")),
      indent,
    )

    sparql"""SELECT DISTINCT $versionDate $author
WHERE {
  $where
}
ORDER BY DESC($versionDate)""".render
  }

  private def join(patterns: Fragment*): Fragment = Fragment.join(patterns, indent)
}
