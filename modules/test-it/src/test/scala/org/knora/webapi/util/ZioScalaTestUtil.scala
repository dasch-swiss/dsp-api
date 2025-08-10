/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import org.scalatest.Assertions
import org.scalatest.matchers.should.Matchers.*
import zio.Exit

import scala.reflect.ClassTag

object ZioScalaTestUtil {

  def assertFailsWithA[T <: Throwable: ClassTag](actual: Exit[Throwable, ?]) = actual match {
    case Exit.Failure(err) => err.squash shouldBe a[T]
    case _                 => Assertions.fail(s"Expected Exit.Failure with specific T.")
  }

  def assertFailsWithA[T <: Throwable: ClassTag](actual: Exit[Throwable, ?], expectedError: String) = actual match {
    case Exit.Failure(err) => {
      err.squash shouldBe a[T]
      err.squash.getMessage shouldEqual expectedError
    }
    case _ => Assertions.fail(s"Expected Exit.Failure with specific T.")
  }

  def assertFailsWithA[T <: Throwable: ClassTag](actual: Exit[Throwable, ?], errorPredicate: Throwable => Boolean) =
    actual match {
      case Exit.Failure(err) => {
        val errSquashed = err.squash
        errSquashed shouldBe a[T]
        errorPredicate(errSquashed) shouldBe true
      }
      case _ => Assertions.fail(s"Expected Exit.Failure with specific T.")
    }
}
