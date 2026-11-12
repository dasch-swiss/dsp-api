/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import org.knora.sparqlbuilder.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.FilterNotExistsPattern
import org.knora.webapi.messages.util.search.GroupPattern
import org.knora.webapi.messages.util.search.IriRef
import org.knora.webapi.messages.util.search.QueryPattern
import org.knora.webapi.messages.util.search.QueryVariable
import org.knora.webapi.messages.util.search.StatementPattern

/**
 * The triple that binds the standoff node of a text value in the standoff branch of a resource query.
 *
 * For [[StandoffSelection.OnlyWithoutStoredXml]] the triple is wrapped in its own group together with
 * `FILTER NOT EXISTS { ?value knora-base:valueHasXml [] }`. The group must contain exactly this triple and
 * nothing else of the branch: with the filter anywhere in the branch group, the optimizer applies it after the
 * join with all standoff node properties instead of to the link scan alone. The group binds the value variable
 * itself, so the filter never sees it unbound, and the XML literal is never loaded.
 *
 * [[fragment]] and [[patterns]] are the two representations of this one rule; the query builders that use
 * `sparql""` fragments and those that use the `QueryPattern` ADT each call the one that fits.
 */
object StandoffLink {

  /** The unprojected variable standing in for the blank node, which the ADT cannot express. */
  private val StoredXml = QueryVariable("storedXml")

  def fragment(standoff: StandoffSelection, value: Variable, node: Variable): Fragment = {
    val link = sparql"$value knora-base:valueHasStandoff $node ."
    if (standoff == StandoffSelection.OnlyWithoutStoredXml)
      sparql"""|{
               |  $link
               |  FILTER NOT EXISTS { $value knora-base:valueHasXml [] }
               |}"""
    else link
  }

  def patterns(standoff: StandoffSelection, value: QueryVariable, node: QueryVariable)(using
    StringFormatter,
  ): Seq[QueryPattern] = {
    val link = StatementPattern(
      subj = value,
      pred = IriRef(OntologyConstants.KnoraBase.ValueHasStandoff.toSmartIri),
      obj = node,
    )
    if (standoff == StandoffSelection.OnlyWithoutStoredXml)
      Seq(
        GroupPattern(
          Seq(
            link,
            FilterNotExistsPattern(
              Seq(
                StatementPattern(
                  subj = value,
                  pred = IriRef(OntologyConstants.KnoraBase.ValueHasXml.toSmartIri),
                  obj = StoredXml,
                ),
              ),
            ),
          ),
        ),
      )
    else Seq(link)
  }
}
