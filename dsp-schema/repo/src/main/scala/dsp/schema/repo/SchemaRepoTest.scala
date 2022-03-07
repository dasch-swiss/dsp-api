package dsp.schema.repo

import zio._
import dsp.schema.domain.SchemaDomain.{UserID, UserProfile}

case class SchemaRepoTest() extends SchemaRepo {
  private var map: Map[UserID, UserProfile] = Map()

  def setTestData(map0: Map[UserID, UserProfile]): Task[Unit] =
    Task { map = map0 }

  def getTestData: Task[Map[UserID, UserProfile]] =
    Task(map)

  def lookup(id: UserID): Task[UserProfile] =
    Task(map(id))

  def update(id: UserID, profile: UserProfile): Task[Unit] =
    Task.attempt { map = map + (id -> profile) }
}

object SchemaRepoTest extends (() => SchemaRepo) {
  val layer: URLayer[Any, SchemaRepo] = (SchemaRepoTest.apply _).toLayer
}
