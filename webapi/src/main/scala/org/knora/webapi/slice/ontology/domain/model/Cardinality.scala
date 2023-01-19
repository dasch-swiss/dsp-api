/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model

import org.knora.webapi.messages.OntologyConstants.Owl
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.OwlCardinalityInfo
import org.knora.webapi.slice.ontology.domain.model.Cardinality.allCardinalities

/**
 * Represents a cardinality value object.
 */
sealed trait Cardinality {
  def min: Int

  def max: Option[Int]

  /**
   * Checks whether a cardinality is stricter than another one.
   *
   * @param other The cardinality to be compared against.
   * @return `true` if this cardinality is stricter than the `other`, `false` otherwise.
   */
  def isStricterThan(other: Cardinality): Boolean =
    (other.min, this.min, other.max, this.max) match {
      case (otherMin, thisMin, _, _) if otherMin < thisMin => true
      case (_, _, otherMax, Some(thisMax))                 => otherMax.forall(_ > thisMax)
      case _                                               => false
    }

  def getIntersection(other: Cardinality): Option[Cardinality] = {
    val lower = math.max(this.min, other.min)
    val upper = (this.max ++ other.max).minOption
    allCardinalities.filter(_.min == lower).find(_.max == upper)
  }

  /**
   * The [[String]] representation of a [[Cardinality]].
   *
   * @example `1-n` in case no `max` is given
   *
   *          `1`   in case `max` and `min` are the same
   *
   *          `0-1` in case `max` and `min` are different
   * @return the string, suitable for display
   */
  override def toString: String = (min, max) match {
    case (min, None)                    => s"$min-n"
    case (min, Some(max)) if min == max => s"$min"
    case (min, Some(max))               => s"$min-$max"
  }
}

object Cardinality {
  case object AtLeastOne extends Cardinality {
    override val min: Int         = 1
    override val max: Option[Int] = None
  }

  case object ExactlyOne extends Cardinality {
    override val min: Int         = 1
    override val max: Option[Int] = Some(1)
  }

  case object Unbounded extends Cardinality {
    override val min: Int         = 0
    override val max: Option[Int] = None
  }

  case object ZeroOrOne extends Cardinality {
    override val min: Int         = 0
    override val max: Option[Int] = Some(1)
  }

  def toOwl(cardinality: Cardinality): OwlCardinalityInfo =
    cardinality match {
      case ZeroOrOne  => OwlCardinalityInfo(owlCardinalityIri = Owl.MaxCardinality, owlCardinalityValue = 1)
      case Unbounded  => OwlCardinalityInfo(owlCardinalityIri = Owl.MinCardinality, owlCardinalityValue = 0)
      case ExactlyOne => OwlCardinalityInfo(owlCardinalityIri = Owl.Cardinality, owlCardinalityValue = 1)
      case AtLeastOne => OwlCardinalityInfo(owlCardinalityIri = Owl.MinCardinality, owlCardinalityValue = 1)
    }

  val allCardinalities: Array[Cardinality] = Array(AtLeastOne, ExactlyOne, Unbounded, ZeroOrOne)

  def fromString(str: String): Option[Cardinality] = allCardinalities.find(_.toString == str)
}
