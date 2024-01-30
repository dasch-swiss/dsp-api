/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.model

import zio.prelude.Validation
import zio.test.Gen
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

import dsp.errors.BadRequestException
import dsp.valueobjects.V2
import org.knora.webapi.slice.admin.domain.model.ListProperties.*

/**
 * This spec is used to test the [[List]] value objects creation.
 */
object ListPropertiesSpec extends ZIOSpecDefault {
  private val validComment   = Seq(V2.StringLiteralV2(value = "Valid list comment", language = Some("en")))
  private val invalidComment = Seq(V2.StringLiteralV2(value = "Invalid list comment \r", language = Some("en")))

  def spec: Spec[Any, Any] = suite("ListProperties")(listNameSuite, positionSuite, labelsTest, commentsTest)

  private val listNameSuite = suite("ListName")(
    test("pass an empty value and return an error") {
      assertTrue(ListName.from("") == Left("List name cannot be empty."))
    },
    test("pass an invalid value and return an error") {
      assertTrue(ListName.from("Invalid list name\r") == Left("List name is invalid."))
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(ListName.from("Valid list name").map(_.value) == Right("Valid list name"))
    }
  )

  private val positionSuite = suite("Position")(
    test("should be greater than or equal -1") {
      check(Gen.int(-10, 10)) { i =>
        val actual = Position.from(i)
        i match {
          case i if i >= -1 => assertTrue(actual.map(_.value) == Right(i))
          case _ =>
            assertTrue(
              actual == Left("Invalid position value is given. Position should be either a positive value, 0 or -1.")
            )
        }
      }
    }
  )

  private val labelsTest = suite("Labels")(
    test("pass an empty object and return an error") {
      assertTrue(Labels.from(Seq.empty) == Left("At least one label needs to be supplied."))
    },
    test("pass an invalid object and return an error") {
      val invalid = Seq(V2.StringLiteralV2(value = "Invalid list label \r", language = Some("en")))
      assertTrue(Labels.from(invalid) == Left("Invalid label."))
    },
    test("pass a valid object and successfully create value object") {
      val valid = Seq(V2.StringLiteralV2(value = "Valid list label", language = Some("en")))
      assertTrue(Labels.from(valid).map(_.value) == Right(valid))
    }
  )

  private val commentsTest = suite("Comments")(
    test("pass an empty object and return an error") {
      assertTrue(
        Comments.make(Seq.empty) == Validation.fail(BadRequestException(ListErrorMessages.CommentsMissing)),
        Comments.make(Some(Seq.empty)) == Validation.fail(BadRequestException(ListErrorMessages.CommentsMissing))
      )
    },
    test("pass an invalid object and return an error") {
      assertTrue(
        Comments.make(invalidComment) == Validation.fail(BadRequestException(ListErrorMessages.CommentsInvalid)),
        Comments.make(Some(invalidComment)) == Validation.fail(BadRequestException(ListErrorMessages.CommentsInvalid))
      )
    },
    test("pass a valid object and successfully create value object") {
      assertTrue(
        Comments.make(validComment).toOption.get.value == validComment,
        Comments.make(Option(validComment)).getOrElse(null).get.value == validComment
      )
    },
    test("successfully validate passing None") {
      assertTrue(
        Comments.make(None) == Validation.succeed(None)
      )
    }
  )
}
