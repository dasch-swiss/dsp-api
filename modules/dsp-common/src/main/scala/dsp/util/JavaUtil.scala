/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.util

import java.util.function.BiFunction
import java.util.function.Function

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