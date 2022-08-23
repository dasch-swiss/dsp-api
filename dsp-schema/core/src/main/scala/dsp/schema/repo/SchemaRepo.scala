package dsp.schema.repo

import zio._
import dsp.schema.domain.SchemaDomain.{UserID, UserProfile}

trait SchemaRepo {
  def lookup(id: UserID): Task[UserProfile]
  def update(id: UserID, profile: UserProfile): Task[Unit]
}

object SchemaRepo {
  def lookup(id: UserID): ZIO[SchemaRepo, Throwable, UserProfile] =
    ZIO.serviceWithZIO[SchemaRepo](_.lookup(id))

  def update(id: UserID, profile: UserProfile): RIO[SchemaRepo, Unit] =
    ZIO.serviceWithZIO[SchemaRepo](_.update(id, profile))
}
