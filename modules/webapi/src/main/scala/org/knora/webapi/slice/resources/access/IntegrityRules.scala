/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.ontology.domain.model.Cardinality

/** Integrity rules over the unfiltered resource, passed to [[Grant.verify]]; only their verdict leaves the package. */
object IntegrityRules {

  /**
   * A value of `property` may be removed unless it is the resource's only one and `cardinality` requires one. Every
   * loaded value of the property counts, hidden and deleted ones included. Erasing only a value's history removes none.
   */
  def valueRemovable(
    resource: ResourceIri,
    property: PropertyIri,
    cardinality: Cardinality,
    onlyHistory: Boolean,
  ): Unfiltered => Option[IntegrityViolation] = { unfiltered =>
    val loaded = unfiltered.resource.fold(0)(_.values.get(property.toInternalSchema).fold(0)(_.size))
    Option.when(!onlyHistory && cardinality.min == 1 && loaded == 1)(
      IntegrityViolation.CardinalityViolated(resource, property),
    )
  }

  /**
   * A value of `property` may be added unless the resource already has as many as `cardinality` allows. Every loaded
   * value of the property counts, hidden ones included.
   */
  def valueAddable(
    resource: ResourceIri,
    property: PropertyIri,
    cardinality: Cardinality,
  ): Unfiltered => Option[IntegrityViolation] = { unfiltered =>
    val loaded = unfiltered.resource.fold(0)(_.values.get(property.toInternalSchema).fold(0)(_.size))
    Option.when(cardinality.max.exists(_ <= loaded))(IntegrityViolation.CardinalityViolated(resource, property))
  }

  /**
   * A resource links to a target at most once per link property: the link is one triple, described by one
   * `LinkValue`. Links hidden from the caller count.
   */
  def noDuplicateLink(
    resource: ResourceIri,
    linkValueProperty: PropertyIri,
    target: ResourceIri,
  ): Unfiltered => Option[IntegrityViolation] = { unfiltered =>
    unfiltered.resource
      .flatMap(LinkValueWrites.currentLinkValueOf(_, linkValueProperty.toInternalSchema, target))
      .map(_ => IntegrityViolation.DuplicateLink(resource, linkValueProperty, target))
  }
}
