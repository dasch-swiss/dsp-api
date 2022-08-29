/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.valueobjects

import zio.prelude.Validation
import zio.test._

import dsp.errors.BadRequestException
import dsp.errors.ValidationException
import dsp.valueobjects.Iri._

/**
 * This spec is used to test the [[Iri]] value objects creation.
 */
object IriSpec extends ZIOSpecDefault {
  val invalidIri                 = "Invalid IRI"
  val validGroupIri              = "http://rdfh.ch/groups/0803/qBCJAdzZSCqC_2snW5Q7Nw"
  val groupIriWithUUIDVersion3   = "http://rdfh.ch/groups/0803/rKAU0FNjPUKWqOT8MEW_UQ"
  val validListIri               = "http://rdfh.ch/lists/0803/qBCJAdzZSCqC_2snW5Q7Nw"
  val listIriWithUUIDVersion3    = "http://rdfh.ch/lists/0803/6_xROK_UN1S2ZVNSzLlSXQ"
  val validProjectIri            = "http://rdfh.ch/projects/0001"
  val projectIriWithUUIDVersion3 = "http://rdfh.ch/projects/tZjZhGSZMeCLA5VeUmwAmg"
  val validRoleIri               = "http://rdfh.ch/roles/ZPKPVh8yQs6F7Oyukb8WIQ"
  val roleIriWithUUIDVersion3    = "http://rdfh.ch/roles/Ul3IYhDMOQ2fyoVY0ePz0w"
  val validUserIri               = "http://rdfh.ch/users/jDEEitJESRi3pDaDjjQ1WQ"
  val userIriWithUUIDVersion3    = "http://rdfh.ch/users/cCmdcpn2MO211YYOplR1hQ"

  def spec = (groupIriTest + listIriTest + projectIriTest + RoleIriTest + UserIriTest)

  private val groupIriTest = suite("IriSpec - GroupIri")(
    test("pass an empty value and return an error") {
      assertTrue(GroupIri.make("") == Validation.fail(BadRequestException(IriErrorMessages.GroupIriMissing))) &&
      assertTrue(
        GroupIri.make(Some("")) == Validation.fail(BadRequestException(IriErrorMessages.GroupIriMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        GroupIri.make(invalidIri) == Validation.fail(
          BadRequestException(IriErrorMessages.GroupIriInvalid)
        )
      ) &&
      assertTrue(
        GroupIri.make(Some(invalidIri)) == Validation.fail(
          BadRequestException(IriErrorMessages.GroupIriInvalid)
        )
      )
    },
    test("pass an invalid IRI containing unsupported UUID version and return an error") {
      assertTrue(
        GroupIri.make(groupIriWithUUIDVersion3) == Validation.fail(
          BadRequestException(IriErrorMessages.UuidVersionInvalid)
        )
      ) &&
      assertTrue(
        GroupIri.make(Some(groupIriWithUUIDVersion3)) == Validation.fail(
          BadRequestException(IriErrorMessages.UuidVersionInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(GroupIri.make(validGroupIri).toOption.get.value == validGroupIri) &&
      assertTrue(GroupIri.make(Option(validGroupIri)).getOrElse(null).get.value == validGroupIri)
    },
    test("successfully validate passing None") {
      assertTrue(
        GroupIri.make(None) == Validation.succeed(None)
      )
    }
  )

  private val listIriTest = suite("IriSpec - ListIri")(
    test("pass an empty value and return an error") {
      assertTrue(ListIri.make("") == Validation.fail(BadRequestException(IriErrorMessages.ListIriMissing))) &&
      assertTrue(
        ListIri.make(Some("")) == Validation.fail(BadRequestException(IriErrorMessages.ListIriMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        ListIri.make(invalidIri) == Validation.fail(
          BadRequestException(IriErrorMessages.ListIriInvalid)
        )
      ) &&
      assertTrue(
        ListIri.make(Some(invalidIri)) == Validation.fail(
          BadRequestException(IriErrorMessages.ListIriInvalid)
        )
      )
    },
    test("pass an invalid IRI containing unsupported UUID version and return an error") {
      assertTrue(
        ListIri.make(listIriWithUUIDVersion3) == Validation.fail(
          BadRequestException(IriErrorMessages.UuidVersionInvalid)
        )
      ) &&
      assertTrue(
        ListIri.make(Some(listIriWithUUIDVersion3)) == Validation.fail(
          BadRequestException(IriErrorMessages.UuidVersionInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(ListIri.make(validListIri).toOption.get.value == validListIri) &&
      assertTrue(ListIri.make(Option(validListIri)).getOrElse(null).get.value == validListIri)
    },
    test("successfully validate passing None") {
      assertTrue(
        ListIri.make(None) == Validation.succeed(None)
      )
    }
  )

  private val projectIriTest = suite("IriSpec - ProjectIri")(
    test("pass an empty value and return an error") {
      assertTrue(ProjectIri.make("") == Validation.fail(ValidationException(IriErrorMessages.ProjectIriMissing))) &&
      assertTrue(
        ProjectIri.make(Some("")) == Validation.fail(ValidationException(IriErrorMessages.ProjectIriMissing))
      )
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        ProjectIri.make(invalidIri) == Validation.fail(
          ValidationException(IriErrorMessages.ProjectIriInvalid)
        )
      ) &&
      assertTrue(
        ProjectIri.make(Some(invalidIri)) == Validation.fail(
          ValidationException(IriErrorMessages.ProjectIriInvalid)
        )
      )
    },
    test("pass an invalid IRI containing unsupported UUID version and return an error") {
      assertTrue(
        ProjectIri.make(projectIriWithUUIDVersion3) == Validation.fail(
          ValidationException(IriErrorMessages.UuidVersionInvalid)
        )
      ) &&
      assertTrue(
        ProjectIri.make(Some(projectIriWithUUIDVersion3)) == Validation.fail(
          ValidationException(IriErrorMessages.UuidVersionInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(ProjectIri.make(validProjectIri).toOption.get.value == validProjectIri) &&
      assertTrue(ProjectIri.make(Option(validProjectIri)).getOrElse(null).get.value == validProjectIri)
    },
    test("successfully validate passing None") {
      assertTrue(
        ProjectIri.make(None) == Validation.succeed(None)
      )
    }
  )

  private val RoleIriTest = suite("IriSpec - roleIri")(
    test("pass an empty value and return an error") {
      assertTrue(RoleIri.make("") == Validation.fail(BadRequestException(IriErrorMessages.RoleIriMissing)))
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        RoleIri.make(invalidIri) == Validation.fail(
          BadRequestException(IriErrorMessages.RoleIriInvalid(invalidIri))
        )
      )
    },
    test("pass an invalid IRI containing unsupported UUID version and return an error") {
      assertTrue(
        RoleIri.make(roleIriWithUUIDVersion3) == Validation.fail(
          BadRequestException(IriErrorMessages.UuidVersionInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(RoleIri.make(validRoleIri).toOption.get.value == validRoleIri)
    }
  )

  private val UserIriTest = suite("IriSpec - UserIri")(
    test("pass an empty value and return an error") {
      assertTrue(UserIri.make("") == Validation.fail(BadRequestException(IriErrorMessages.UserIriMissing)))
    },
    test("pass an invalid value and return an error") {
      assertTrue(
        UserIri.make(invalidIri) == Validation.fail(
          BadRequestException(IriErrorMessages.UserIriInvalid(invalidIri))
        )
      )
    },
    test("pass an invalid IRI containing unsupported UUID version and return an error") {
      assertTrue(
        UserIri.make(userIriWithUUIDVersion3) == Validation.fail(
          BadRequestException(IriErrorMessages.UuidVersionInvalid)
        )
      )
    },
    test("pass a valid value and successfully create value object") {
      assertTrue(UserIri.make(validUserIri).toOption.get.value == validUserIri)
    }
  )
}
