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
   * Checks whether a cardinality is included in another one.
   *
   * A [[Cardinality]] includes another cardinality if and only if the other cardinality
   * is included in its entire range of possible values.
   *
   * @param other The cardinality to be compared against.
   * @return `true` if this cardinality is included in the `other`, `false` otherwise.
   */
  def isIncludedIn(other: Cardinality): Boolean = {
    val lowerBoundIsIncluded = this.min >= other.min && other.max.forall(this.min <= _)
    lazy val upperBoundIsIncluded = (this.max, other.max) match {
      case (_, None)                       => true
      case (None, Some(_))                 => false
      case (Some(thisMax), Some(otherMax)) => thisMax >= other.min && thisMax <= otherMax
    }
    lowerBoundIsIncluded && upperBoundIsIncluded
  }

  /**
   * Negated check for [[Cardinality.isIncludedIn(org.knora.webapi.slice.ontology.domain.model.Cardinality)]]
   *
   * @param other The cardinality to be compared against.
   * @return `true` if this cardinality is not include in the `other`, `false` otherwise.
   */
  def isNotIncludedIn(other: Cardinality): Boolean = !isIncludedIn(other)

  /**
   * Checks whether a `count` is in included in the range of the [[Cardinality]].
   *
   * @param count  The quantity to check.
   * @return `true` if the count is a valid value for the given [[Cardinality]].
   */
  def isCountIncluded(count: Int): Boolean = min <= count && max.forall(_ >= count)

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
