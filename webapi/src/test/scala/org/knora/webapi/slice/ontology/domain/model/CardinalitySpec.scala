/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model

import zio.Random
import zio.Scope
import zio.test._

import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality
import org.knora.webapi.slice.ontology.domain.model.Cardinality._

object CardinalitySpec extends ZIOSpecDefault {
  val cardinalityGen: Gen[Any, Cardinality] = {
    val list = Array(AtLeastOne, ExactlyOne, ZeroOrOne, Unbounded)
    Gen.fromZIO(for {
      random <- Random.nextIntBetween(0, list.length)
    } yield list(random))
  }

  val spec: Spec[TestEnvironment with Scope, Nothing] = suite("CardinalitySpec")(
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
    suite("Cardinality isStricterThan")(
      test("Same cardinality is never stricter") {
        check(cardinalityGen)(c => assertTrue(!c.isStricterThan(c)))
      },
      suite(s"Unbounded $Unbounded")(
        test(s"'$AtLeastOne' is stricter than $Unbounded") {
          assertTrue(AtLeastOne.isStricterThan(Unbounded))
        },
        test(s"'$ExactlyOne is stricter than $Unbounded") {
          assertTrue(ExactlyOne.isStricterThan(Unbounded))
        },
        test(s"'$ZeroOrOne' is stricter than $Unbounded") {
          assertTrue(ZeroOrOne.isStricterThan(Unbounded))
        },
        test(s"'$Unbounded' is NOT stricter than any other") {
          check(cardinalityGen) { other =>
            assertTrue(!Unbounded.isStricterThan(other))
          }
        }
      ),
      suite(s"AtLeastOne $AtLeastOne")(
        test(s"'$AtLeastOne' is NOT stricter than $ExactlyOne") {
          assertTrue(!AtLeastOne.isStricterThan(ExactlyOne))
        },
        test(s"'$AtLeastOne' is stricter than $ZeroOrOne") {
          assertTrue(AtLeastOne.isStricterThan(ZeroOrOne))
        }
      ),
      suite(s"ExactlyOne $ExactlyOne")(
        test(s"'$ExactlyOne' is stricter than $AtLeastOne") {
          assertTrue(ExactlyOne.isStricterThan(AtLeastOne))
        },
        test(s"'$ExactlyOne' is stricter than $ZeroOrOne") {
          assertTrue(ExactlyOne.isStricterThan(ZeroOrOne))
        }
      ),
      suite(s"ZeroOrOne $ZeroOrOne")(
        test(s"'$ZeroOrOne' is stricter than $AtLeastOne") {
          assertTrue(ZeroOrOne.isStricterThan(AtLeastOne))
        },
        test(s"'$ZeroOrOne' is NOT stricter than $ExactlyOne") {
          assertTrue(!ZeroOrOne.isStricterThan(ExactlyOne))
        }
      )
    ),
    suite("toOwl")(
      test(s"AtLeastOne $AtLeastOne") {
        assertTrue(
          Cardinality
            .toOwl(AtLeastOne) == OwlCardinality.OwlCardinalityInfo("http://www.w3.org/2002/07/owl#minCardinality", 1)
        )
      },
      test(s"ExactlyOne $ExactlyOne") {
        assertTrue(
          Cardinality
            .toOwl(ExactlyOne) == OwlCardinality.OwlCardinalityInfo("http://www.w3.org/2002/07/owl#cardinality", 1)
        )
      },
      test(s"ZeroOrOne $ZeroOrOne") {
        assertTrue(
          Cardinality
            .toOwl(ZeroOrOne) == OwlCardinality.OwlCardinalityInfo("http://www.w3.org/2002/07/owl#maxCardinality", 1)
        )
      },
      test(s"Unbounded $Unbounded") {
        assertTrue(
          Cardinality
            .toOwl(Unbounded) == OwlCardinality.OwlCardinalityInfo("http://www.w3.org/2002/07/owl#minCardinality", 0)
        )
      }
    ),
    suite("getIntersection")(
      test(s"$AtLeastOne <> $ZeroOrOne => $ExactlyOne") {
        assertTrue(AtLeastOne.getIntersection(ZeroOrOne).contains(ExactlyOne))
      },
      test(s"$Unbounded <> $AtLeastOne => $AtLeastOne") {
        assertTrue(Unbounded.getIntersection(AtLeastOne).contains(AtLeastOne))
      }
    )
  )
}
