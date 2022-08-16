/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.schema.domain

/**
 * Represents a cardinality value object.
 */
sealed trait Cardinality { self =>

  /**
   * The string representation of the cardinality
   */
  val value: String

  /**
   * Checks whether a cardinality is stricter than another one.
   *
   * @param that      the cardinality to be compared against
   * @return          `true` if the present cardinality is stricter than `that`, `false` otherwise
   */
  def isStricterThan(that: Cardinality): Boolean =
    if (self == that) {
      false
    } else {
      self match {
        case MustHaveOne  => true
        case MustHaveSome => that == MayHaveMany
        case MayHaveOne   => that == MayHaveMany
        case MayHaveMany  => false
      }
    }
}
object MustHaveOne extends Cardinality {
  override val value: String = "1"
}
object MustHaveSome extends Cardinality {
  override val value: String = "1-n"
}
object MayHaveOne extends Cardinality {
  override val value: String = "0-1"
}
object MayHaveMany extends Cardinality {
  override val value: String = "0-n"
}
