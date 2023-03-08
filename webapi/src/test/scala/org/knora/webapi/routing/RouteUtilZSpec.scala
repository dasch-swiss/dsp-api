/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import zio.test.Assertion._
import zio.test.ZIOSpecDefault
import zio.test._
import zio.test.assertTrue

import dsp.errors.BadRequestException

object RouteUtilZSpec extends ZIOSpecDefault {
  val spec: Spec[Any, Throwable] = suite("routeUtilZSpec")(
    suite("function `urlDecode` should")(
      test("given a valid encoding, return the decoded value") {
        for {
          actual <- RouteUtilZ.urlDecode("http%3A%2F%2Frdfh.ch%2Fprojects%2FLw3FC39BSzCwvmdOaTyLqQ")
        } yield assertTrue(actual == "http://rdfh.ch/projects/Lw3FC39BSzCwvmdOaTyLqQ")
      },
      test("given an empty value should return BadRequestException") {
        for {
          error <- RouteUtilZ.urlDecode("%-5", "Failed to url decode IRI.").exit
        } yield assert(error)(
          fails(equalTo(BadRequestException("Failed to url decode IRI.")))
        )
      },
      test("given an empty value, return BadRequestException with default error message") {
        for {
          error <- RouteUtilZ.urlDecode("%-5").exit
        } yield assert(error)(
          fails(equalTo(BadRequestException("Not an url encoded utf-8 String '%-5'")))
        )
      }
    )
  )
}
