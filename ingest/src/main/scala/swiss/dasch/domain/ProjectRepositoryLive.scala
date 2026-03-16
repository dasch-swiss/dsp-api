/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.ProjectId.toProjectIdUnsafe
import swiss.dasch.domain.ProjectShortcode.toShortcodeUnsafe
import zio.Chunk
import zio.Clock
import zio.IO
import zio.ZIO
import zio.ZLayer

import java.sql.ResultSet
import java.sql.SQLException
import java.time.Instant
import javax.sql.DataSource

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

final case class ProjectRepositoryLive(private val ds: DataSource) extends ProjectRepository {

  private def toProject(rs: ResultSet): Project =
    Project(
      rs.getInt("id").toProjectIdUnsafe,
      rs.getString("shortcode").toShortcodeUnsafe,
      Instant.ofEpochMilli(rs.getLong("created_at")),
    )

  private def queryOpt(sql: String)(bind: java.sql.PreparedStatement => Unit): DbTask[Option[Project]] =
    ZIO.blocking(ZIO.attempt {
      val conn = ds.getConnection
      try {
        val ps = conn.prepareStatement(sql)
        try {
          bind(ps)
          val rs = ps.executeQuery()
          try if (rs.next()) Some(toProject(rs)) else None
          finally rs.close()
        } finally ps.close()
      } finally conn.close()
    }).refineToOrDie[SQLException]

  private def update(sql: String)(bind: java.sql.PreparedStatement => Unit): DbTask[Unit] =
    ZIO.blocking(ZIO.attempt {
      val conn = ds.getConnection
      try {
        val ps = conn.prepareStatement(sql)
        try { bind(ps); ps.executeUpdate(); () }
        finally ps.close()
      } finally conn.close()
    }).refineToOrDie[SQLException]

  override def findById(id: ProjectId): DbTask[Option[Project]] =
    queryOpt("SELECT id, shortcode, created_at FROM project WHERE id = ? LIMIT 1")(_.setInt(1, id.value))

  override def findByShortcode(shortcode: ProjectShortcode): DbTask[Option[Project]] =
    queryOpt("SELECT id, shortcode, created_at FROM project WHERE shortcode = ? LIMIT 1")(
      _.setString(1, shortcode.value),
    )

  override def addProject(shortcode: ProjectShortcode): DbTask[Project] = for {
    now <- Clock.instant
    id  <- ZIO.blocking(ZIO.attempt {
             val conn = ds.getConnection
             try {
               val ps =
                 conn.prepareStatement(
                   "INSERT INTO project (shortcode, created_at) VALUES (?, ?)",
                   java.sql.Statement.RETURN_GENERATED_KEYS,
                 )
               try {
                 ps.setString(1, shortcode.value)
                 ps.setLong(2, now.toEpochMilli)
                 ps.executeUpdate()
                 val keys = ps.getGeneratedKeys
                 try { keys.next(); keys.getInt(1) }
                 finally keys.close()
               } finally ps.close()
             } finally conn.close()
           }).refineToOrDie[SQLException]
  } yield Project(id.toProjectIdUnsafe, shortcode, now)

  override def deleteById(id: ProjectId): DbTask[Unit] =
    update("DELETE FROM project WHERE id = ?")(_.setInt(1, id.value))

  override def deleteByShortcode(shortcode: ProjectShortcode): DbTask[Unit] =
    update("DELETE FROM project WHERE shortcode = ?")(_.setString(1, shortcode.value))
}

object ProjectRepositoryLive {
  val layer = ZLayer.derive[ProjectRepositoryLive]
}
