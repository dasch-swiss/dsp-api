/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import zio.Ref
import zio.ZIO
import zio.ZLayer
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.TestDataFactory.UserGroup.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.KnoraUserGroup
import org.knora.webapi.slice.admin.domain.service.KnoraUserGroupRepo
import org.knora.webapi.slice.common.repo.AbstractInMemoryCrudRepository
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

final case class KnoraUserGroupRepoInMemory(groups: Ref[List[KnoraUserGroup]])
    extends AbstractInMemoryCrudRepository[KnoraUserGroup, GroupIri](groups, _.id)
    with KnoraUserGroupRepo {}

object KnoraUserGroupRepoInMemory {
  val layer = ZLayer.fromZIO(Ref.make(List.empty[KnoraUserGroup])) >>>
    ZLayer.derive[KnoraUserGroupRepoInMemory]
}

object KnoraUserGroupRepoLiveSpec extends ZIOSpecDefault {
  val spec: Spec[Any, Any] = suite("KnoraUserGroupRepoLive")(
    suite("findById")(
      test("findById given a non existing user should return None") {
        ZIO.serviceWithZIO[KnoraUserGroupRepo](userGroupRepo =>
          for {
            userGroup <- userGroupRepo.findById(GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/1234"))
          } yield assertTrue(userGroup.isEmpty),
        )
      },
      test("findById given an existing user should return that user") {
        ZIO.serviceWithZIO[KnoraUserGroupRepo](userGroupRepo =>
          for {
            _         <- userGroupRepo.save(testUserGroup)
            userGroup <- userGroupRepo.findById(testUserGroup.id)
          } yield assertTrue(userGroup.contains(testUserGroup)),
        )
      },
      test("save should update fields") {
        ZIO.serviceWithZIO[KnoraUserGroupRepo](userGroupRepo =>
          for {
            _ <- userGroupRepo.save(testUserGroup)
            testUserGroupModified =
              testUserGroup.copy(
                groupName = GroupName.unsafeFrom("another"),
                belongsToProject = None,
              )
            _         <- userGroupRepo.save(testUserGroupModified)
            userGroup <- userGroupRepo.findById(testUserGroup.id)
          } yield assertTrue(userGroup.contains(testUserGroupModified)),
        )
      },
    ),
  ).provide(
    KnoraUserGroupRepoLive.layer,
    TriplestoreServiceInMemory.emptyLayer,
    StringFormatter.test,
  )
}
