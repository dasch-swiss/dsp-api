/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model

import zio.Random
import zio.Scope
import zio.test._

import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality
import org.knora.webapi.slice.ontology.domain.model.Cardinality._
import org.knora.webapi.slice.ontology.domain.model.CardinalitySpec.Generator.cardinalitiesGen

object CardinalitySpec extends ZIOSpecDefault {
  object Generator {
    def cardinalitiesGen(cardinalities: Cardinality*): Gen[Any, Cardinality] = Gen.fromZIO {
      val candidates: Array[Cardinality] = if (cardinalities != Nil) {
        cardinalities.toArray
      } else {
        allCardinalities
      }
      val length = candidates.length
      if (length == 0) {
        throw new IllegalArgumentException("The parameter cardinalities may not be an empty Array")
      } else {
        Random.nextIntBetween(0, length).map(i => candidates(i))
      }
    }
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
    suite("Cardinality isIncludedIn")(
      test("Same cardinality is always included in itself") {
        check(cardinalitiesGen())(c => assertTrue(c.isIncludedIn(c)))
      },
      test(s"All cardinalities are included in Unbounded '$Unbounded'") {
        check(cardinalitiesGen()) { other =>
          assertTrue(other.isIncludedIn(Unbounded))
        }
      },
      test(s"AtLeastOne '$AtLeastOne' is NOT included in: ZeroOrOne '$ZeroOrOne', ExactlyOne '$ExactlyOne'") {
        check(cardinalitiesGen(ZeroOrOne, ExactlyOne)) { other =>
          assertTrue(AtLeastOne.isNotIncludedIn(other))
        }
      },
      test(s"ZeroOrOne '$ZeroOrOne' is NOT included in: AtLeastOne '$AtLeastOne', ExactlyOne '$ExactlyOne''") {
        check(cardinalitiesGen(AtLeastOne, ExactlyOne)) { other =>
          assertTrue(ZeroOrOne.isNotIncludedIn(other))
        }
      },
      test(s"ExactlyOne '$ExactlyOne' is included in all other Cardinalities") {
        check(cardinalitiesGen()) { other =>
          assertTrue(ExactlyOne.isIncludedIn(other))
        }
      }
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
    ),
    suite("fromString")(
      test("`0-n` => Unbounded") {
        assertTrue(Cardinality.fromString("0-n").contains(Unbounded))
      },
      test("`0-1` => ZeroOrOne") {
        assertTrue(Cardinality.fromString("0-1").contains(ZeroOrOne))
      },
      test("`1-n` => AtLeastOne") {
        assertTrue(Cardinality.fromString("1-n").contains(AtLeastOne))
      },
      test("`1` => ExactlyOne") {
        assertTrue(Cardinality.fromString("1").contains(ExactlyOne))
      }
    )
  )
}
