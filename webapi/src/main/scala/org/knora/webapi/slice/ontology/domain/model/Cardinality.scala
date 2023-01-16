/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model
import dsp.schema.domain.Cardinality.MayHaveMany
import dsp.schema.domain.Cardinality.MayHaveOne
import dsp.schema.domain.Cardinality.MustHaveOne
import dsp.schema.domain.Cardinality.MustHaveSome
import dsp.schema.domain.{Cardinality => OldCardinality}
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo

/**
 * Represents a cardinality value object.
 */
sealed trait Cardinality {
  def min: Int
  def max: Option[Int]

  val oldCardinality: OldCardinality

  /**
   * Checks whether a cardinality is stricter than another one.
   *
   * @param other     The cardinality to be compared against.
   * @return          `true` if this cardinality is stricter than the `other`, `false` otherwise.
   */
  def isStricterThan(other: Cardinality): Boolean =
    (other.min, this.min, other.max, this.max) match {
      case (otherMin, thisMin, _, _) if otherMin < thisMin => true
      case (_, _, otherMax, Some(thisMax))                 => otherMax.forall(_ > thisMax)
      case _                                               => false
    }

  /**
   * The [[String]] representation of a [[Cardinality]].
   *
   * @example `1-n` in case no `max` is given
   *
   *          `1`   in case `max` and `min` are the same
   *
   *          `0-1` in case `max` and `min` are different
   *
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
    override val min: Int                       = 1
    override val max: Option[Int]               = None
    override val oldCardinality: OldCardinality = MustHaveSome
  }

  case object ExactlyOne extends Cardinality {
    override val min: Int                       = 1
    override val max: Option[Int]               = Some(1)
    override val oldCardinality: OldCardinality = MustHaveOne
  }

  case object Unbounded extends Cardinality {
    override val min: Int                       = 0
    override val max: Option[Int]               = None
    override val oldCardinality: OldCardinality = MayHaveMany
  }

  case object ZeroOrOne extends Cardinality {
    override val min: Int                       = 0
    override val max: Option[Int]               = Some(1)
    override val oldCardinality: OldCardinality = MayHaveOne
  }

  val allCardinalities: Array[Cardinality] = Array(AtLeastOne, ExactlyOne, Unbounded, ZeroOrOne)

  def get(cardinalityInfo: KnoraCardinalityInfo): Cardinality = get(cardinalityInfo.cardinality)
  def get(cardinality: OldCardinality): Cardinality =
    allCardinalities.find(_.oldCardinality == cardinality).getOrElse(throw new IllegalStateException)

  def fromString(str: String): Option[Cardinality] = allCardinalities.find(_.toString == str)
}
