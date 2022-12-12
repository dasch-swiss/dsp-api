package org.knora.webapi.routing

import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.Assertion._
import zio.test._
import dsp.errors.BadRequestException

object RouteUtilZSpec extends ZIOSpecDefault {
  val spec = suite("routeUtilZSpec")(
    test("given a valid encoding should return the decoded value") {
      for {
        actual <- RouteUtilZ.decodeUrl("http%3A%2F%2Frdfh.ch%2Fprojects%2FLw3FC39BSzCwvmdOaTyLqQ")
      } yield assertTrue(actual == "http://rdfh.ch/projects/Lw3FC39BSzCwvmdOaTyLqQ")
    },
    test("given an empty value should return BadRequestException") {
      for {
        error <- RouteUtilZ.decodeUrl("%-5").exit
      } yield assert(error)(
        fails(equalTo(BadRequestException("Failed to decode IRI %-5")))
      )
    }
  )
}
