/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import dsp.errors.BadRequestException
import org.knora.sparqlbuilder.*
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

/**
 * The `knora-base:valueHasOrder` slots of a value reorder. The caller's visible values are permuted among their own
 * slots; values hidden from the caller keep theirs and are not written.
 */
object ReorderSlots {

  /** Every value of `property` without `knora-base:isDeleted true` (a read's `deletionInfo`), and its order. */
  private[access] def query(resource: ResourceIri, property: PropertyIri): Select = {
    val subject   = Iri.unsafeFrom(resource.value)
    val predicate = Iri.unsafeFrom(property.toInternalSchema.toIri)
    Select(
      sparql"""|PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
               |
               |SELECT ?value ?order
               |WHERE {
               |  $subject $predicate ?value .
               |  OPTIONAL { ?value knora-base:isDeleted ?isDeleted . }
               |  FILTER ( !bound(?isDeleted) || ?isDeleted = false )
               |  OPTIONAL { ?value knora-base:valueHasOrder ?order . }
               |}""".render,
    )
  }

  /**
   * Refuses a request that does not list each of the caller's visible values once. The message names only visible
   * values and request IRIs, so a hidden and a nonexistent value read the same.
   */
  private[access] def mismatch(target: ValuesToReorder, requested: Seq[ValueIri]): Option[BadRequestException] =
    if (requested.distinct.size != requested.size) Some(BadRequestException("Duplicate value IRIs in request."))
    else {
      val visible = target.visibleValues.map(_.valueIri)
      val missing = visible.filterNot(requested.contains)
      val extra   = requested.filterNot(visible.contains)
      Option.when(missing.nonEmpty || extra.nonEmpty)(
        BadRequestException(
          s"The provided value IRIs do not match the values of property <${target.property}> on resource " +
            s"<${target.resource.resourceIri}> that you can see. Expected ${visible.size} value(s), got ${requested.size}. " +
            s"Missing: ${missing.mkString(", ")}. Extra: ${extra.mkString(", ")}.",
        ),
      )
    }

  /**
   * Assigns the old slots of `requested`, sorted with duplicates and gaps kept, in the requested order. A value
   * without a slot adds one after the highest order in `orders` (all non-deleted values; a missing order reads as 0).
   */
  private[access] def assign(requested: Seq[ValueIri], orders: Map[String, Option[Int]]): List[(ValueIri, Int)] = {
    val old     = requested.flatMap(v => orders.get(v.value).flatten).sorted
    val highest = orders.values.map(_.getOrElse(0)).maxOption.getOrElse(-1)
    val fresh   = (1 to requested.size - old.size).map(highest + _)
    requested.zip(old ++ fresh).toList
  }
}
