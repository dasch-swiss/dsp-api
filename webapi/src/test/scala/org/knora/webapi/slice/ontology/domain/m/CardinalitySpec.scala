/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.m

import zio.Random
import zio.Scope
import zio.test._

import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.model.Cardinality._

object CardinalitySpec extends ZIOSpecDefault {
  val cardinalityGen: Gen[Any, Cardinality] = {
    val list = Array(AtLeastOne, ExactlyOne, ZeroOrOne, Unbounded)
    Gen.fromZIO(for {
      random <- Random.nextIntBetween(0, list.length)
    } yield list(random))
  }

  val spec: Spec[TestEnvironment with Scope, Nothing] = suite("CardinalitySpec")(
    suite("Cardinality make")(
      test("with min only => return Some with min only") {
        assertTrue(Cardinality.make(0).contains(Cardinality(0, None)))
      },
      test("with min and max => return Some with min and max") {
        assertTrue(Cardinality.make(1, 2).contains(Cardinality(1, Some(2))))
      },
      test("with min greater than max => return None") {
        check(Gen.int(0, Int.MaxValue)) { i =>
          assertTrue(Cardinality.make(i + 1, i).isEmpty)
        }
      }
    ),
    suite("Cardinality to String")(
      test("lower bound only") {
        assertTrue(AtLeastOne.toString == "1-n")
      },
      test("different lower and upper bound ") {
        assertTrue(ZeroOrOne.toString == "0-1")
      },
      test("same upper and lower bound") {
        assertTrue(ExactlyOne.toString == "1")
      }
    ),
    suite("Cardinality isStricter")(
      test("Same cardinality is never stricter") {
        check(cardinalityGen)(c => assertTrue(!c.isStricter(c)))
      },
      suite(s"Unbounded $Unbounded")(
        test(s"'$AtLeastOne' is stricter than $Unbounded") {
          assertTrue(AtLeastOne.isStricter(Unbounded))
        },
        test(s"'$ExactlyOne is stricter than $Unbounded") {
          assertTrue(ExactlyOne.isStricter(Unbounded))
        },
        test(s"'$ZeroOrOne' is stricter than $Unbounded") {
          assertTrue(ZeroOrOne.isStricter(Unbounded))
        },
        test(s"'$Unbounded' is NOT stricter than any other") {
          check(cardinalityGen) { other =>
            assertTrue(!Unbounded.isStricter(other))
          }
        }
      ),
      suite(s"AtLeastOne $AtLeastOne")(
        test(s"'$AtLeastOne' is NOT stricter than $ExactlyOne") {
          assertTrue(!AtLeastOne.isStricter(ExactlyOne))
        },
        test(s"'$AtLeastOne' is stricter than $ZeroOrOne") {
          assertTrue(AtLeastOne.isStricter(ZeroOrOne))
        }
      ),
      suite(s"ExactlyOne $ExactlyOne")(
        test(s"'$ExactlyOne' is stricter than $AtLeastOne") {
          assertTrue(ExactlyOne.isStricter(AtLeastOne))
        },
        test(s"'$ExactlyOne' is stricter than $ZeroOrOne") {
          assertTrue(ExactlyOne.isStricter(ZeroOrOne))
        }
      ),
      suite(s"ExactlyOne $ZeroOrOne")(
        test(s"'$ZeroOrOne' is stricter than $AtLeastOne") {
          assertTrue(ZeroOrOne.isStricter(AtLeastOne))
        },
        test(s"'$ZeroOrOne' is NOT stricter than $ExactlyOne") {
          assertTrue(!ZeroOrOne.isStricter(ExactlyOne))
        }
      )
    )
  )
}
