package org.knora.webapi.routing

import org.knora.webapi.routing.ScopeValue.Admin
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import zio.test.Gen
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

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
    override def toScopeString: String = s"read:${project.value}"
  }

  final case class Write(project: Shortcode) extends ScopeValue {
    override def toScopeString: String = s"write:${project.value}"
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

object ScopeSpec extends ZIOSpecDefault {
  private val prj1             = Shortcode.unsafeFrom("0001")
  private val readScopeValue1  = ScopeValue.Read(prj1)
  private val writeScopeValue1 = ScopeValue.Write(prj1)

  private val prj2             = Shortcode.unsafeFrom("0002")
  private val readScopeValue2  = ScopeValue.Read(prj2)
  private val writeScopeValue2 = ScopeValue.Write(prj2)

  private val scopeValueSuite = suite("scope values")(
    test("merging any scope value with Admin should return Admin") {
      val adminScopeValue           = Admin
      val expected: Set[ScopeValue] = Set(Admin)
      check(Gen.fromIterable(Seq(Admin, ScopeValue.Read(prj1), ScopeValue.Write(prj2)))) { (other: ScopeValue) =>
        assertTrue(
          other.merge(adminScopeValue) == expected,
          adminScopeValue.merge(other) == expected,
        )
      }
    },
    test("merging Read with Write for the same project should return Write") {
      val expected: Set[ScopeValue] = Set(writeScopeValue1)
      assertTrue(
        readScopeValue1.merge(writeScopeValue1) == expected,
        writeScopeValue1.merge(readScopeValue1) == expected,
      )
    },
    test("merging Read with Write for different projects should return both values") {
      val expected: Set[ScopeValue] = Set(readScopeValue1, writeScopeValue2)
      assertTrue(
        readScopeValue1.merge(writeScopeValue2) == expected,
        writeScopeValue2.merge(readScopeValue1) == expected,
      )
    },
    test("merging two Read values for the same project should return one Read value") {
      val expected: Set[ScopeValue] = Set(readScopeValue1)
      assertTrue(
        readScopeValue1.merge(readScopeValue1) == expected,
        readScopeValue1.merge(readScopeValue1) == expected,
      )
    },
    test("merging two Write values for the same project should return one Write value") {
      val expected: Set[ScopeValue] = Set(writeScopeValue1)
      assertTrue(
        writeScopeValue1.merge(writeScopeValue1) == expected,
        writeScopeValue1.merge(writeScopeValue1) == expected,
      )
    },
    test("merging two different Read values should return both values") {
      val expected: Set[ScopeValue] = Set(readScopeValue1, readScopeValue2)
      assertTrue(
        readScopeValue1.merge(readScopeValue2) == expected,
        readScopeValue2.merge(readScopeValue1) == expected,
      )
    },
    test("merging two different Write values should return both values") {
      val expected: Set[ScopeValue] = Set(writeScopeValue1, writeScopeValue2)
      assertTrue(
        writeScopeValue1.merge(writeScopeValue2) == expected,
        writeScopeValue2.merge(writeScopeValue1) == expected,
      )
    },
  )

  private val scopeSuite = suite("ScopeSpec")(
    test("adding a value to an empty scope") {
      val scope = Scope.empty
      check(Gen.fromIterable(Seq(Admin, readScopeValue1, writeScopeValue1, readScopeValue2, writeScopeValue2))) {
        (value: ScopeValue) => assertTrue(scope + value == Scope(Set(value)))
      }
    },
    test("adding admin any scope results in admin") {
      check(
        Gen.fromIterable(
          Seq(Scope(Set(readScopeValue1, writeScopeValue1)), Scope(Set(readScopeValue1, writeScopeValue2))),
        ),
      )((scope: Scope) => assertTrue(scope + Admin == Scope.admin))
    },
    test("adding an already present scope does nothing") {
      val scope = Scope(Set(readScopeValue1))
      assertTrue(scope + readScopeValue1 == scope)
    },
    test("adding a write scope to a read scope merges") {
      val scope = Scope(Set(readScopeValue1, readScopeValue2))
      assertTrue(scope + writeScopeValue1 == Scope(Set(writeScopeValue1, readScopeValue2)))
    },
    test("adding a read scope to a write scope merges") {
      val scope = Scope(Set(writeScopeValue1, readScopeValue2))
      assertTrue(scope + readScopeValue1 == Scope(Set(writeScopeValue1, readScopeValue2)))
    },
  )

  val spec = suite("ScopeSpec")(scopeValueSuite, scopeSuite)
}
