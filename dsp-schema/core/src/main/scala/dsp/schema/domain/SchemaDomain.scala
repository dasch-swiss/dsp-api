/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.schema.domain

import dsp.valueobjects.Schema._
import zio.prelude.Validation

/**
 * Represents the property domain object.
 *
 * @param id          the ID of the property
 */
sealed abstract case class Property private (
  id: PropertyId
) extends Ordered[Property] { self =>

  /**
   * Allows to sort collections of [[Property]]s. Sorting is done by the IRI.
   */
  def compare(that: Property): Int = self.id.iri.toString().compareTo(that.id.iri.toString())

  /**
   * Update the label of a Property
   *
   *  @param newValue  the new label
   *  @return the updated [[Property]]
   */
  def updateLabel(newValue: PropertyLabel): Property =
    new Property(
      self.id
    ) {}
}

object Property {
  def make(
    label: PropertyLabel
  ): Property = {
    val id = PropertyId.make()
    new Property(id) {}
  }

}
