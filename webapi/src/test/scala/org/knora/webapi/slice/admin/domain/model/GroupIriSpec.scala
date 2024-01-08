/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import dsp.valueobjects.IriSpec.groupIriWithUUIDVersion3
import dsp.valueobjects.IriSpec.invalidIri

object GroupIriSpec extends ZIOSpecDefault {

  private val validGroupIri = "http://rdfh.ch/groups/0803/qBCJAdzZSCqC_2snW5Q7Nw"

  override val spec: Spec[Any, Nothing] = suite("GroupIri from should")(
    test("pass an empty value and return an error") {
      assertTrue(GroupIri.from("") == Left("Group IRI cannot be empty."))
    },
    test("pass an invalid value and return an error") {
      assertTrue(GroupIri.from(invalidIri) == Left("Group IRI is invalid."))
    },
    test("pass an invalid IRI containing unsupported UUID version and return an error") {
      assertTrue(
        GroupIri.from(groupIriWithUUIDVersion3) ==
          Left("Invalid UUID used to create IRI. Only versions 4 and 5 are supported.")
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(GroupIri.from(validGroupIri).map(_.value) == Right(validGroupIri))
    }
  )
}
