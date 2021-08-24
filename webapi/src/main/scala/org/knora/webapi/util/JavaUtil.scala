/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import java.util.function.{BiFunction, Function}
import scala.language.implicitConversions

/**
 * Utility functions for working with Java libraries.
 */
object JavaUtil {

  /**
   * Converts a 1-argument Scala function into a Java [[Function]].
   *
   * @param f the Scala function.
   * @return a [[Function]] that calls the Scala function.
   */
  def function[A, B](f: A => B): Function[A, B] =
    (a: A) => f(a)

  /**
   * Converts a 2-argument Scala function into a Java [[BiFunction]].
   *
   * @param f the Scala function.
   * @return a [[BiFunction]] that calls the Scala function.
   */
  def biFunction[A, B, C](f: (A, B) => C): BiFunction[A, B, C] =
    (a: A, b: B) => f(a, b)

  /**
   * Wraps a Java `Optional` and converts it to a Scala [[Option]].
   */
  implicit class ConvertibleJavaOptional[T](val self: java.util.Optional[T]) extends AnyVal {
    def toOption: Option[T] = if (self.isPresent) Some(self.get) else None
  }
}
