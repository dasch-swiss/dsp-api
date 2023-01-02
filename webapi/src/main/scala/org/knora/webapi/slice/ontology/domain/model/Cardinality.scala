package org.knora.webapi.slice.ontology.domain.model

sealed trait Cardinality {
  def min: Int
  def max: Option[Int]

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

  case object AtLeastOne extends Cardinality {
    val min: Int         = 1
    val max: Option[Int] = None
  }

  case object ExactlyOne extends Cardinality {
    val min: Int         = 1
    val max: Option[Int] = Some(1)
  }

  case object Unbounded extends Cardinality {
    val min: Int         = 0
    val max: Option[Int] = None
  }

  case object ZeroOrOne extends Cardinality {
    val min: Int         = 0
    val max: Option[Int] = Some(1)
  }
}
