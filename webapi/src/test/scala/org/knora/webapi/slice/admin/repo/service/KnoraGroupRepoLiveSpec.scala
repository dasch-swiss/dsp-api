/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import zio.Ref
import zio.ZIO
import zio.ZLayer
import zio.test.Gen
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

import org.knora.webapi.TestDataFactory.UserGroup._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupName
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.common.repo.AbstractInMemoryCrudRepository
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

final case class KnoraGroupRepoInMemory(groups: Ref[List[KnoraGroup]])
    extends AbstractInMemoryCrudRepository[KnoraGroup, GroupIri](groups, _.id)
    with KnoraGroupRepo {}

object KnoraGroupRepoInMemory {
  val layer = ZLayer.fromZIO(Ref.make(List.empty[KnoraGroup])) >>>
    ZLayer.derive[KnoraGroupRepoInMemory]
}

object KnoraGroupRepoLiveSpec extends ZIOSpecDefault {

  private val KnoraGroupRepo = ZIO.serviceWithZIO[KnoraGroupRepo]
  private val builtInGroups  = org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo.builtIn.all

  private val findById = suite("findById")(
    test("given a non existing user should return None") {
      for {
        userGroup <- KnoraGroupRepo(_.findById(GroupIri.unsafeFrom("http://rdfh.ch/groups/0001/1234")))
      } yield assertTrue(userGroup.isEmpty)
    },
    test("given an existing user should return that user") {
      for {
        _         <- KnoraGroupRepo(_.save(testUserGroup))
        userGroup <- KnoraGroupRepo(_.findById(testUserGroup.id))
      } yield assertTrue(userGroup.contains(testUserGroup))
    },
    test("should find all builtIn users") {
      check(Gen.fromIterable(builtInGroups)) { group =>
        for {
          actual <- KnoraGroupRepo(_.findById(group.id))
        } yield assertTrue(actual.contains(group))
      }
    },
  )

  private val findAll = suite("findAll")(
    test("should return built in users") {
      for {
        userGroup <- KnoraGroupRepo(_.findAll())
      } yield assertTrue(userGroup.sortBy(_.id.value) == builtInGroups.toList.sortBy(_.id.value))
    },
  )

  private val save: Spec[KnoraGroupRepo, Throwable] = suite("save")(
    test("should update fields") {
      for {
        _ <- KnoraGroupRepo(_.save(testUserGroup))

        testUserGroupModified =
          testUserGroup.copy(
            groupName = GroupName.unsafeFrom("another"),
            belongsToProject = None,
          )
        _         <- KnoraGroupRepo(_.save(testUserGroupModified))
        userGroup <- KnoraGroupRepo(_.findById(testUserGroup.id))
      } yield assertTrue(userGroup.contains(testUserGroupModified))
    },
    test("should die for built in groups") {
      check(Gen.fromIterable(builtInGroups)) { group =>
        for {
          exit <- KnoraGroupRepo(_.save(group)).exit
        } yield assertTrue(exit.isFailure)
      }
    },
  )
  val spec: Spec[Any, Any] = suite("KnoraUserGroupRepoLive")(findById, findAll, save)
    .provide(
      KnoraGroupRepoLive.layer,
      TriplestoreServiceInMemory.emptyLayer,
      StringFormatter.test,
    )
}
