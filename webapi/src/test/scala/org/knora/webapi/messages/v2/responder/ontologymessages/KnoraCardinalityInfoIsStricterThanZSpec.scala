package org.knora.webapi.messages.v2.responder.ontologymessages

import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import dsp.schema.domain.Cardinality.MayHaveMany
import dsp.schema.domain.Cardinality.MayHaveOne
import dsp.schema.domain.Cardinality.MustHaveOne
import dsp.schema.domain.Cardinality.MustHaveSome

/**
 * Tests the comparison forstrictness of cardinalities with [[isStricterThan()]].
 */
object KnoraCardinalityInfoIsStricterThanZSpec extends ZIOSpecDefault {

  def spec = (mustHaveOneTest + mustHaveSomeTest + mayHaveOneTest + mayHaveManyTest)

  val mustHaveOne  = MustHaveOne  // "1"
  val mustHaveSome = MustHaveSome // "1-n"
  val mayHaveOne   = MayHaveOne   // "0-1"
  val mayHaveMany  = MayHaveMany  // "0-n"

  val mustHaveOneTest = suite("MustHaveOneTest")(
    test("cardinality of '1' is NOT stricter than cardinality of '1'") {
      assertTrue(!mustHaveOne.isStricterThan(mustHaveOne))
    },
    test("cardinality of '1' is stricter than cardinality of '1-n'") {
      assertTrue(mustHaveOne.isStricterThan(mustHaveSome))
    },
    test("cardinality of '1' is stricter than cardinality of '0-1'") {
      assertTrue(mustHaveOne.isStricterThan(mayHaveOne))
    },
    test("cardinality of '1' is stricter than cardinality of '0-n'") {
      assertTrue(mustHaveOne.isStricterThan(mayHaveMany))
    }
  )

  val mustHaveSomeTest = suite("MustHaveSomeTest")(
    test("cardinality of '1-n' is NOT stricter than cardinality of '1'") {
      assertTrue(!mustHaveSome.isStricterThan(mustHaveOne))
    },
    test("cardinality of '1-n' is NOT stricter than cardinality of '1-n'") {
      assertTrue(!mustHaveSome.isStricterThan(mustHaveSome))
    },
    test("cardinality of '1-n' is NOT stricter than cardinality of '0-1'") {
      assertTrue(!mustHaveSome.isStricterThan(mayHaveOne))
    },
    test("cardinality of '1-n' is stricter than cardinality of '0-n'") {
      assertTrue(mustHaveSome.isStricterThan(mayHaveMany))
    }
  )

  val mayHaveOneTest = suite("MayHaveOneTest")(
    test("cardinality of '0-1' is NOT stricter than cardinality of '1'") {
      assertTrue(!mayHaveOne.isStricterThan(mustHaveOne))
    },
    test("cardinality of '0-1' is NOT stricter than cardinality of '1-n'") {
      assertTrue(!mayHaveOne.isStricterThan(mustHaveSome))
    },
    test("cardinality of '0-1' is NOT stricter than cardinality of '0-1'") {
      assertTrue(!mayHaveOne.isStricterThan(mayHaveOne))
    },
    test("cardinality of '0-1' is stricter than cardinality of '0-n'") {
      assertTrue(mayHaveOne.isStricterThan(mayHaveMany))
    }
  )

  val mayHaveManyTest = suite("MayHaveManyTest")(
    test("cardinality of '0-n' is NOT stricter than cardinality of '1'") {
      assertTrue(!mayHaveMany.isStricterThan(mustHaveOne))
    },
    test("cardinality of '0-n' is NOT stricter than cardinality of '1-n'") {
      assertTrue(!mayHaveMany.isStricterThan(mustHaveSome))
    },
    test("cardinality of '0-n' is NOT stricter than cardinality of '0-1'") {
      assertTrue(!mayHaveMany.isStricterThan(mayHaveOne))
    },
    test("cardinality of '0-n' is NOT stricter than cardinality of '0-n'") {
      assertTrue(!mayHaveMany.isStricterThan(mayHaveMany))
    }
  )
}
