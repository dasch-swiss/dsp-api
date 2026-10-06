/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.fair

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

@RunWith(classOf[DspZTestJUnitRunner])
class ResourceLandingRefSpec extends ZIOSpecDefault {

  private val id = "lklK7rVuVOmpBZYWrF8o-g"

  val spec: Spec[Any, Nothing] = suite("ResourceLandingRef")(
    test("a valid ref without version yields the resource IRI") {
      val ref = ResourceLandingRef.from("0803", id, None)
      assertTrue(
        ref.map(_.resourceIri.value) == Right(s"http://rdfh.ch/0803/$id"),
        ref.map(_.version) == Right(None),
      )
    },
    test("a malformed resource id is rejected") {
      assertTrue(
        ResourceLandingRef.from("0803", "a/b", None).isLeft,
        ResourceLandingRef.from("0803", "", None).isLeft,
      )
    },
    test("a bad shortcode is rejected") {
      assertTrue(
        ResourceLandingRef.from("08", id, None).isLeft,
        ResourceLandingRef.from("zzzz", id, None).isLeft,
        ResourceLandingRef.from("", id, None).isLeft,
      )
    },
    test("a check-digit id is rejected") {
      assertTrue(ResourceLandingRef.from("0803", "lklK7rVuVOmpBZYWrF8o=gh", None).isLeft)
    },
    test("an ARK-form version is accepted") {
      val ref = ResourceLandingRef.from("0803", id, Some("20180604T085622513Z"))
      assertTrue(ref.map(_.version.map(_.value.toString)) == Right(Some("2018-06-04T08:56:22.513Z")))
    },
    test("an xsd:dateTimeStamp version is accepted") {
      val ref = ResourceLandingRef.from("0803", id, Some("2018-06-04T08:56:22.513Z"))
      assertTrue(ref.map(_.version.map(_.value.toString)) == Right(Some("2018-06-04T08:56:22.513Z")))
    },
    test("a bad version is rejected") {
      assertTrue(ResourceLandingRef.from("0803", id, Some("yesterday")).isLeft)
    },
    test("an empty version is rejected") {
      assertTrue(ResourceLandingRef.from("0803", id, Some("")).isLeft)
    },
  )
}
