/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import dsp.errors.BadRequestException
import dsp.valueobjects.List.Comments
import dsp.valueobjects.List.Labels
import dsp.valueobjects.List.ListName
import dsp.valueobjects.List.Position

/**
 * This spec is used to test the [[List]] value objects creation.
 */
object ListSpec extends ZIOSpecDefault {
  private val validName       = "Valid list name"
  private val invalidName     = "Invalid list name\r"
  private val validPosition   = 0
  private val invalidPosition = -2
  private val validLabel      = Seq(V2.StringLiteralV2(value = "Valid list label", language = Some("en")))
  private val invalidLabel    = Seq(V2.StringLiteralV2(value = "Invalid list label \r", language = Some("en")))
  private val validComment    = Seq(V2.StringLiteralV2(value = "Valid list comment", language = Some("en")))
  private val invalidComment  = Seq(V2.StringLiteralV2(value = "Invalid list comment \r", language = Some("en")))

  def spec: Spec[Any, Any] = listNameTest + positionTest + labelsTest + commentsTest

  private val listNameTest = suite("ListSpec - ListName")(
    test("pass an empty value and return an error") {
      assertTrue(
        ListName.make("") == Validation.fail(BadRequestException(ListErrorMessages.ListNameMissing)),
        ListName.make(Some("")) == Validation.fail(BadRequestException(ListErrorMessages.ListNameMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        ListName.make(invalidName) == Validation.fail(BadRequestException(ListErrorMessages.ListNameInvalid)),
        ListName.make(Some(invalidName)) == Validation.fail(BadRequestException(ListErrorMessages.ListNameInvalid))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(
        ListName.make(validName).toOption.get.value == validName,
        ListName.make(Option(validName)).getOrElse(null).get.value == validName
      )
    },
    test("successfully validate passing None") {
      assertTrue(
        ListName.make(None) == Validation.succeed(None)
      )
    }
  )

  private val positionTest = suite("ListSpec - Position")(
    test("pass an invalid value and return an error") {
      assertTrue(
        Position.make(invalidPosition) == Validation.fail(BadRequestException(ListErrorMessages.InvalidPosition)),
        Position.make(Some(invalidPosition)) == Validation.fail(BadRequestException(ListErrorMessages.InvalidPosition))
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(
        Position.make(validPosition).toOption.get.value == validPosition,
        Position.make(Option(validPosition)).getOrElse(null).get.value == validPosition
      )
    },
    test("successfully validate passing None") {
      assertTrue(
        Position.make(None) == Validation.succeed(None)
      )
    }
  )

  private val labelsTest = suite("ListSpec - Labels")(
    test("pass an empty object and return an error") {
      assertTrue(
        Labels.make(Seq.empty) == Validation.fail(BadRequestException(ListErrorMessages.LabelsMissing)),
        Labels.make(Some(Seq.empty)) == Validation.fail(BadRequestException(ListErrorMessages.LabelsMissing))
      )
    },
    test("pass an invalid object and return an error") {
      assertTrue(
        Labels.make(invalidLabel) == Validation.fail(BadRequestException(ListErrorMessages.LabelsInvalid)),
        Labels.make(Some(invalidLabel)) == Validation.fail(BadRequestException(ListErrorMessages.LabelsInvalid))
      )
    },
    test("pass a valid object and successfully create value object") {
      assertTrue(
        Labels.make(validLabel).toOption.get.value == validLabel,
        Labels.make(Option(validLabel)).getOrElse(null).get.value == validLabel
      )
    },
    test("successfully validate passing None") {
      assertTrue(
        Labels.make(None) == Validation.succeed(None)
      )
    }
  )

  private val commentsTest = suite("ListSpec - Comments")(
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
