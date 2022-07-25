/*
 * Copyright © 2021 - 2022 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package dsp.role.domain

import dsp.valueobjects.Id
import dsp.valueobjects.Role._
import dsp.valueobjects.User
import dsp.valueobjects.Permission

object RoleTestData {
  val roleId1          = Id.RoleId.make()
  val roleName1        = LangString.make("Name", "en")
  val roleDescription1 = LangString.make("Description", "en")
  val roleUsers1       = List(RoleUser(Id.UserId.make().fold(e => throw e.head, v => v)))
  val rolePermission1  = Permission(0)

  val role1 = for {
    id          <- roleId1
    name        <- roleName1
    description <- roleDescription1

    role <- Role.make(
              id,
              name,
              description,
              users = roleUsers1,
              permission = rolePermission1
            )
  } yield role

  val roleId2          = Id.RoleId.make()
  val roleName2        = LangString.make("Name 2", "en")
  val roleDescription2 = LangString.make("Description 2", "en")
  val roleUsers2       = List(RoleUser(Id.UserId.make().fold(e => throw e.head, v => v)))
  val rolePermission2  = Permission(4)

  val role2 = for {
    id          <- roleId2
    name        <- roleName2
    description <- roleDescription2

    role <- Role.make(
              id,
              name,
              description,
              users = roleUsers2,
              permission = rolePermission2
            )
  } yield role
}
