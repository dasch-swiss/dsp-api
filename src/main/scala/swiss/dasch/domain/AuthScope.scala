/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import cats.implicits.*
import swiss.dasch.domain.AuthScope.ScopeValue.*

final case class AuthScope(values: Set[AuthScope.ScopeValue] = Set()) extends AnyVal {
  def hasAdmin: Boolean =
    values.contains(Admin)

  def projectWritable(projectShortcode: ProjectShortcode): Boolean =
    hasAdmin || values.contains(Write(projectShortcode))

  def projectReadable(projectShortcode: ProjectShortcode): Boolean =
    projectWritable(projectShortcode) || values.contains(Read(projectShortcode))
}

object AuthScope {
  enum ScopeValue {
    case Admin
    case Write(projectShortcode: ProjectShortcode)
    case Read(projectShortcode: ProjectShortcode)
  }

  def from(sv: ScopeValue*): AuthScope = AuthScope(Set(sv: _*))

  val Empty = AuthScope()

  val WriteFormat = "^write:project:([^ ]+)$".r
  val ReadFormat  = "^read:project:([^ ]+)$".r

  def parseScopeValue(value: String): Either[String, Option[ScopeValue]] =
    value match {
      case "admin"                => Right(Some(ScopeValue.Admin))
      case WriteFormat(shortcode) => ProjectShortcode.from(shortcode).map(ScopeValue.Write(_).some)
      case ReadFormat(shortcode)  => ProjectShortcode.from(shortcode).map(ScopeValue.Read(_).some)
      case _                      => Right(None)
    }

  def parse(value: String): Either[String, AuthScope] =
    value.split(' ').toList.traverse(parseScopeValue).map(v => AuthScope(Set.from(v.flatten)))
}
