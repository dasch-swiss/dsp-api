package org.knora.webapi.slice.ontology.domain.model

final case class Cardinality private (min: Int, max: Option[Int]) {
  def isStricter(other: Cardinality): Boolean =
    if (this == other) {
      false
    } else if (this.min > other.min) {
      true
    } else if (this.max.nonEmpty) {
      other match {
        case Cardinality(_, Some(otherMax)) => this.max.get < otherMax
        case _                              => true
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

  def make(min: Int, max: Int): Option[Cardinality] = make(min, Some(max))

  def make(min: Int, maxMaybe: Option[Int] = None): Option[Cardinality] = {
    if (min < 0) {
      return None
    } else if (maxMaybe.nonEmpty) {
      val max = maxMaybe.get
      if (max < 0 || min > max) {
        return None
      }
    }
    Some(Cardinality(min, maxMaybe))
  }

  val AtLeastOne: Cardinality = Cardinality.make(1).get
  val ExactlyOne: Cardinality = Cardinality.make(1, 1).get
  val Unbounded: Cardinality  = Cardinality.make(0).get
  val ZeroOrOne: Cardinality  = Cardinality.make(0, 1).get
}
