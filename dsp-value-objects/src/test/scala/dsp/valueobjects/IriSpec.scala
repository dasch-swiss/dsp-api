/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import dsp.valueobjects.Iri._
import zio.prelude.Validation
import zio.test._

/**
 * This spec is used to test the [[Iri]] value objects creation.
 */
object IriSpec extends ZIOSpecDefault {

  // make it GroupIri instead of GroupIRI + for all iris
  val invalidIri                 = "Invalid IRI"
  val validGroupIri              = "http://rdfh.ch/groups/0803/qBCJAdzZSCqC_2snW5Q7Nw"
  val groupIriWithUUIDVersion3   = "http://rdfh.ch/groups/0803/rKAU0FNjPUKWqOT8MEW_UQ"
  val validListIri               = "http://rdfh.ch/lists/0803/qBCJAdzZSCqC_2snW5Q7Nw"
  val listIriWithUUIDVersion3    = "http://rdfh.ch/lists/0803/6_xROK_UN1S2ZVNSzLlSXQ"
  val validProjectIri            = "http://rdfh.ch/projects/0001"
  val projectIriWithUUIDVersion3 = "http://rdfh.ch/projects/tZjZhGSZMeCLA5VeUmwAmg"
  val validUserIri               = "http://rdfh.ch/users/jDEEitJESRi3pDaDjjQ1WQ"
  val userIriWithUUIDVersion3    = "http://rdfh.ch/users/cCmdcpn2MO211YYOplR1hQ"

  def spec = (groupIriTest + listIriTest + projectIriTest)

  private val groupIriTest = suite("IriSpec - GroupIRI")(
    test("pass an empty value and throw an error") {
      assertTrue(GroupIRI.make("") == Validation.fail(V2.BadRequestException(IriErrorMessages.GroupIriMissing)))
      assertTrue(
        GroupIRI.make(Some("")) == Validation.fail(V2.BadRequestException(IriErrorMessages.GroupIriMissing))
      )
    } +
      test("pass an invalid value and throw an error") {
        assertTrue(
          GroupIRI.make(invalidIri) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.GroupIriInvalid)
          )
        )
        assertTrue(
          GroupIRI.make(Some(invalidIri)) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.GroupIriInvalid)
          )
        )
      } +
      test("pass an invalid IRI containing unsupported UUID version and throw an error") {
        assertTrue(
          GroupIRI.make(groupIriWithUUIDVersion3) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.UuidInvalid)
          )
        )
        assertTrue(
          GroupIRI.make(Some(groupIriWithUUIDVersion3)) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.UuidInvalid)
          )
        )
      } +
      test("pass a valid value and successfully create value object") {
        assertTrue(GroupIRI.make(validGroupIri).toOption.get.value == validGroupIri)
        assertTrue(GroupIRI.make(Option(validGroupIri)).getOrElse(null).get.value == validGroupIri)
      } +
      test("pass None") {
        assertTrue(
          GroupIRI.make(None) == Validation.succeed(None)
        )
      }
  )

  private val listIriTest = suite("IriSpec - ListIRI")(
    test("pass an empty value and throw an error") {
      assertTrue(ListIRI.make("") == Validation.fail(V2.BadRequestException(IriErrorMessages.ListIriMissing)))
      assertTrue(
        ListIRI.make(Some("")) == Validation.fail(V2.BadRequestException(IriErrorMessages.ListIriMissing))
      )
    } +
      test("pass an invalid value and throw an error") {
        assertTrue(
          ListIRI.make(invalidIri) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.ListIriInvalid)
          )
        )
        assertTrue(
          ListIRI.make(Some(invalidIri)) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.ListIriInvalid)
          )
        )
      } +
      test("pass an invalid IRI containing unsupported UUID version and throw an error") {
        assertTrue(
          ListIRI.make(listIriWithUUIDVersion3) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.UuidInvalid)
          )
        )
        assertTrue(
          ListIRI.make(Some(listIriWithUUIDVersion3)) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.UuidInvalid)
          )
        )
      } +
      test("pass a valid value and successfully create value object") {
        assertTrue(ListIRI.make(validListIri).toOption.get.value == validListIri)
        assertTrue(ListIRI.make(Option(validListIri)).getOrElse(null).get.value == validListIri)
      } +
      test("pass None") {
        assertTrue(
          ListIRI.make(None) == Validation.succeed(None)
        )
      }
  )

  private val projectIriTest = suite("IriSpec - ProjectIRI")(
    test("pass an empty value and throw an error") {
      assertTrue(ProjectIRI.make("") == Validation.fail(V2.BadRequestException(IriErrorMessages.ProjectIriMissing)))
      assertTrue(
        ProjectIRI.make(Some("")) == Validation.fail(V2.BadRequestException(IriErrorMessages.ProjectIriMissing))
      )
    } +
      test("pass an invalid value and throw an error") {
        assertTrue(
          ProjectIRI.make(invalidIri) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.ProjectIriInvalid)
          )
        )
        assertTrue(
          ProjectIRI.make(Some(invalidIri)) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.ProjectIriInvalid)
          )
        )
      } +
      test("pass an invalid IRI containing unsupported UUID version and throw an error") {
        assertTrue(
          ProjectIRI.make(projectIriWithUUIDVersion3) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.UuidInvalid)
          )
        )
        assertTrue(
          ProjectIRI.make(Some(projectIriWithUUIDVersion3)) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.UuidInvalid)
          )
        )
      } +
      test("pass a valid value and successfully create value object") {
        assertTrue(ProjectIRI.make(validProjectIri).toOption.get.value == validProjectIri)
        assertTrue(ProjectIRI.make(Option(validProjectIri)).getOrElse(null).get.value == validProjectIri)
      } +
      test("pass None") {
        assertTrue(
          ProjectIRI.make(None) == Validation.succeed(None)
        )
      }
  )

  private val UserIriTest = suite("IriSpec - ProjectIRI")(
    test("pass an empty value and throw an error") {
      assertTrue(UserIRI.make("") == Validation.fail(V2.BadRequestException(IriErrorMessages.UserIriMissing)))
      assertTrue(
        UserIRI.make(Some("")) == Validation.fail(V2.BadRequestException(IriErrorMessages.UserIriMissing))
      )
    } +
      test("pass an invalid value and throw an error") {
        assertTrue(
          UserIRI.make(invalidIri) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.UserIriInvalid)
          )
        )
        assertTrue(
          UserIRI.make(Some(invalidIri)) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.UserIriInvalid)
          )
        )
      } +
      test("pass an invalid IRI containing unsupported UUID version and throw an error") {
        assertTrue(
          UserIRI.make(userIriWithUUIDVersion3) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.UuidInvalid)
          )
        )
        assertTrue(
          UserIRI.make(Some(userIriWithUUIDVersion3)) == Validation.fail(
            V2.BadRequestException(IriErrorMessages.UuidInvalid)
          )
        )
      } +
      test("pass a valid value and successfully create value object") {
        assertTrue(UserIRI.make(validUserIri).toOption.get.value == validUserIri)
        assertTrue(UserIRI.make(Option(validUserIri)).getOrElse(null).get.value == validUserIri)
      } +
      test("pass None") {
        assertTrue(
          UserIRI.make(None) == Validation.succeed(None)
        )
      }
  )
}
