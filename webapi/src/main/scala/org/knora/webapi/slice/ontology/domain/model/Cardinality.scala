package org.knora.webapi.slice.ontology.domain.model
import dsp.schema.domain
import dsp.schema.domain.Cardinality.MayHaveMany
import dsp.schema.domain.Cardinality.MayHaveOne
import dsp.schema.domain.Cardinality.MustHaveOne
import dsp.schema.domain.Cardinality.MustHaveSome

import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo

sealed trait Cardinality {
  def min: Int
  def max: Option[Int]

  val oldCardinality: dsp.schema.domain.Cardinality

  def isStricter(other: KnoraCardinalityInfo): Boolean =
    Cardinality.get(other).isStricter(this)

  def isStricter(other: Cardinality): Boolean =
    if (this == other) {
      false
    } else if (this.min > other.min) {
      true
    } else if (this.max.nonEmpty) {
      other.max match {
        case Some(otherMax) => this.max.get < otherMax
        case _              => true
      }
    } else {
      false
    }

  override def toString: String = (min, max) match {
    case (min, None)      => s"$min-n"
    case (min, Some(max)) => if (min == max) s"$min" else s"$min-$max"
  }
}

object Cardinality {
  def toOwlPropertyAndValue(c: Cardinality): String =
    (c.min, c.max) match {
      case (x, None)              => s"""owl:minCardinality "$x"^^xsd:nonNegativeInteger"""
      case (0, Some(y))           => s"""owl:maxCardinality "$y"^^xsd:nonNegativeInteger"""
      case (x, Some(y)) if x == y => s"""owl:cardinality "$x"^^xsd:nonNegativeInteger"""
      case (x, Some(y)) =>
        s"""owl:minCardinality "$x"^^xsd:nonNegativeInteger
           |owl:maxCardinality "$y"^^xsd:nonNegativeInteger""".stripMargin
    }

  case object AtLeastOne extends Cardinality {
    override val min: Int                           = 1
    override val max: Option[Int]                   = None
    override val oldCardinality: domain.Cardinality = MustHaveSome
  }

  case object ExactlyOne extends Cardinality {
    override val min: Int                           = 1
    override val max: Option[Int]                   = Some(1)
    override val oldCardinality: domain.Cardinality = MustHaveOne
  }

  case object Unbounded extends Cardinality {
    override val min: Int                           = 0
    override val max: Option[Int]                   = None
    override val oldCardinality: domain.Cardinality = MayHaveMany
  }

  case object ZeroOrOne extends Cardinality {
    override val min: Int                           = 0
    override val max: Option[Int]                   = Some(1)
    override val oldCardinality: domain.Cardinality = MayHaveOne
  }

  val allCardinalities: Array[Cardinality] = Array(AtLeastOne, ExactlyOne, Unbounded, ZeroOrOne)
  def get(cardinalityInfo: KnoraCardinalityInfo) =
    allCardinalities.find(_.oldCardinality == cardinalityInfo.cardinality).getOrElse(throw new IllegalStateException)
}
