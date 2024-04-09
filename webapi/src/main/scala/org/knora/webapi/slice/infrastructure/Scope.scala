package org.knora.webapi.slice.infrastructure

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.infrastructure.ScopeValue.Admin

final case class Scope(values: Set[ScopeValue]) {
  self =>
  def toScopeString: String = values.map(_.toScopeString).mkString(" ")
  def +(add: ScopeValue): Scope =
    if (values.contains(Admin) || add == Admin) {
      Scope(Set(Admin))
    } else {
      values.find(_.merge(add).size == 1) match {
        case Some(value) => Scope(values - value ++ value.merge(add))
        case None        => Scope(values + add)
      }
    }
}

object Scope {
  val empty: Scope = Scope(Set.empty)
  val admin: Scope = Scope(Set(ScopeValue.Admin))

  def from(scopeValue: ScopeValue)       = Scope(Set(scopeValue))
  def from(scopeValues: Seq[ScopeValue]) = scopeValues.foldLeft(Scope.empty)(_ + _)
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
      case (Admin, _)                         => Set(Admin)
      case (_, Admin)                         => Set(Admin)
      case (Write(p1), Write(p2)) if p1 == p2 => Set(Write(p1))
      case (Read(p1), Write(p2)) if p1 == p2  => Set(Write(p1))
      case (Write(p1), Read(p2)) if p1 == p2  => Set(Write(p1))
      case (Read(p1), Read(p2)) if p1 == p2   => Set(Read(p1))
      case (a, b)                             => Set(a, b)
    }
}
