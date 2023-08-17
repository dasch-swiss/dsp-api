package org.knora.webapi.responders.v2

import org.scalatest.{Assertion, Assertions}
import org.scalatest.matchers.should.Matchers.{a, convertToAnyShouldWrapper}
import zio.Exit

import scala.reflect.ClassTag

object ScalaTestZioAssertions {
  def assertFailsWithA[T <: Throwable: ClassTag](actual: Exit[Throwable, _]): Assertion = actual match {
    case Exit.Failure(err) => err.squash shouldBe a[T]
    case _                 => Assertions.fail(s"Expected Exit.Failure with specific T.")
  }
}
