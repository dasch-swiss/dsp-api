/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import io.getquill.*
import io.getquill.jdbczio.*
import swiss.dasch.domain.ProjectId.toProjectIdUnsafe
import swiss.dasch.domain.ProjectShortcode.toShortcodeUnsafe
import zio.{Chunk, Clock, IO, ZIO, ZLayer}

import java.sql.SQLException
import java.time.Instant

trait Repository[Entity, Id] { self =>

  type DbTask[A] = IO[SQLException, A]

  def findById(id: ProjectId): DbTask[Option[Entity]]
  final def findByIds(ids: Chunk[ProjectId]): DbTask[Chunk[Entity]] = ZIO.foreach(ids)(self.findById).map(_.flatten)

  def deleteById(id: ProjectId): DbTask[Unit]
  final def deleteByIds(ids: Chunk[ProjectId]): DbTask[Unit] = ZIO.foreachDiscard(ids)(self.deleteById)
}

trait ProjectRepository extends Repository[Project, ProjectId] {
  def addProject(shortcode: ProjectShortcode): DbTask[Project]
  def findByShortcode(shortcode: ProjectShortcode): DbTask[Option[Project]]
  def deleteByShortcode(shortcode: ProjectShortcode): DbTask[Unit]
}

private final case class ProjectRow(id: Int, shortcode: String, createdAt: Instant)

final case class ProjectRepositoryLive(private val quill: Quill.Sqlite[SnakeCase]) extends ProjectRepository {
  import quill.*

  private inline def queryProject =
    quote(querySchema[ProjectRow](entity = "project"))
  private inline def queryProjectById(id: ProjectId) =
    quote(queryProject.filter(prj => prj.id == lift(id.value)))
  private inline def queryProjectByShortcode(shortcode: ProjectShortcode) =
    quote(queryProject.filter(prj => prj.shortcode == lift(shortcode.value)))

  private def toProject(row: ProjectRow): Project =
    Project(row.id.toProjectIdUnsafe, row.shortcode.toShortcodeUnsafe, row.createdAt)

  override def findById(id: ProjectId): DbTask[Option[Project]] =
    run(queryProjectById(id).take(1)).map(_.headOption.map(toProject))

  override def findByShortcode(shortcode: ProjectShortcode): DbTask[Option[Project]] =
    run(queryProjectByShortcode(shortcode).take(1)).map(_.headOption.map(toProject))

  override def addProject(shortcode: ProjectShortcode): DbTask[Project] = for {
    now   <- Clock.instant
    row    = ProjectRow(0, shortcode.value, now)
    newId <- run(queryProject.insertValue(lift(row)).returningGenerated(_.id))
  } yield Project(newId.toProjectIdUnsafe, shortcode, now)

  override def deleteById(id: ProjectId): DbTask[Unit] =
    run(queryProjectById(id).delete).unit

  override def deleteByShortcode(shortcode: ProjectShortcode): DbTask[Unit] =
    run(queryProjectByShortcode(shortcode).delete).unit
}

object ProjectRepositoryLive {
  val layer = ZLayer.derive[ProjectRepositoryLive]
}
