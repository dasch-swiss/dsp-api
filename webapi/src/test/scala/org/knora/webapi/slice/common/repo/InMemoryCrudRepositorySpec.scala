/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.repo

import zio.Ref
import zio.ZIO
import zio.ZLayer
import zio.test.Assertion.hasSameElements
import zio.test._

final case class SomeEntity(id: Int, name: String)
final case class InMemoryRepository(entities: Ref[List[SomeEntity]])
    extends AbstractInMemoryCrudRepository[SomeEntity, Int](entities, _.id)
object InMemoryRepository {
  val layer: ZLayer[Any, Nothing, InMemoryRepository] =
    ZLayer.fromZIO(Ref.make(List.empty[SomeEntity]).map(InMemoryRepository(_)))
}

object InMemoryCrudRepositorySpec extends ZIOSpecDefault {

  private def repo        = ZIO.serviceWithZIO[InMemoryRepository]
  private val someEntity  = SomeEntity(1, "name")
  private val otherEntity = SomeEntity(2, "other")
  private val twoEntities = List(someEntity, otherEntity)

  val spec: Spec[InMemoryRepository, Throwable]#ZSpec[Any, Throwable, TestSuccess] =
    suite("InMemoryCrudRepository")(
      test("should be empty initially")(repo(_.findAll()).map(actual => assertTrue(actual.isEmpty))),
      test("should save and find entity")(for {
        saved  <- repo(_.save(someEntity))
        actual <- repo(_.findById(someEntity.id))
        count  <- repo(_.count())
        exists <- repo(_.existsById(someEntity.id))
      } yield assertTrue(actual.contains(saved), count == 1, exists)),
      test("should save all and find all by id")(for {
        _      <- repo(_.saveAll(twoEntities))
        actual <- repo(_.findAllById(twoEntities.map(_.id)))
      } yield assert(actual)(hasSameElements(List(otherEntity, someEntity)))),
      test("should save all and delete one")(for {
        _      <- repo(_.saveAll(twoEntities))
        _      <- repo(_.delete(someEntity))
        actual <- repo(_.findAll())
        count  <- repo(_.count())
        exists <- repo(_.existsById(someEntity.id))
      } yield assertTrue(!actual.contains(someEntity), count == 1, !exists)),
      test("should save all and delete all")(for {
        _      <- repo(_.saveAll(twoEntities))
        _      <- repo(_.deleteAll(twoEntities))
        actual <- repo(_.findAll())
        count  <- repo(_.count())
      } yield assertTrue(actual.isEmpty, count == 0)),
      test("should save all and delete all by id")(for {
        _      <- repo(_.saveAll(twoEntities))
        _      <- repo(_.deleteAllById(twoEntities.map(_.id)))
        actual <- repo(_.findAll())
        count  <- repo(_.count())
      } yield assertTrue(actual.isEmpty, count == 0))
    ).provide(InMemoryRepository.layer)
}
