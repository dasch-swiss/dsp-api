/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

final case class Scope(values: Set[ScopeValue]) { self =>
  def toScopeString: String         = self.values.map(_.toScopeString).mkString(" ")
  def +(addThis: ScopeValue): Scope = Scope(values.foldLeft(Set(addThis)) { case (s, old) => s.flatMap(_.merge(old)) })
}

object Scope {
  val empty: Scope = Scope(Set.empty)
  val admin: Scope = Scope(Set(ScopeValue.Admin))

  def from(scopeValues: Seq[ScopeValue]): Scope = scopeValues.foldLeft(Scope.empty)(_ + _)
}

sealed trait ScopeValue {
  def toScopeString: String
  final def merge(other: ScopeValue): Set[ScopeValue] = ScopeValue.merge(this, other)
}

object ScopeValue {
  final case class Read(project: Shortcode) extends ScopeValue {
    override def toScopeString: String = s"read:project:${project.value}"
  }

  final case class Write(project: Shortcode) extends ScopeValue {
    override def toScopeString: String = s"write:project:${project.value}"
  }

  final case object Admin extends ScopeValue {
    override def toScopeString: String = "admin"
  }

  def merge(one: ScopeValue, two: ScopeValue): Set[ScopeValue] =
    (one, two) match {
      case (Admin, _) | (_, Admin)            => Set(Admin)
      case (Write(p1), Write(p2)) if p1 == p2 => Set(Write(p1))
      case (Write(p1), Read(p2)) if p1 == p2  => Set(Write(p1))
      case (Read(p1), Write(p2)) if p1 == p2  => Set(Write(p1))
      case (Read(p1), Read(p2)) if p1 == p2   => Set(Read(p1))
      case (a, b)                             => Set(a, b)
    }
}
