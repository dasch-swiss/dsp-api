/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import org.junit.runner.RunWith
import zio.test.Gen
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.infrastructure.ScopeValue.Admin

@RunWith(classOf[DspZTestJUnitRunner])
class ScopeSpec extends ZIOSpecDefault {
  private val prj1             = Shortcode.unsafeFrom("0001")
  private val readScopeValue1  = ScopeValue.Read(prj1)
  private val writeScopeValue1 = ScopeValue.Write(prj1)

  private val prj2             = Shortcode.unsafeFrom("0002")
  private val readScopeValue2  = ScopeValue.Read(prj2)
  private val writeScopeValue2 = ScopeValue.Write(prj2)

  private val scopeValueSuite = suite("ScopeValue")(
    test("merging any ScopeValue with Admin should return Admin") {
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

  private val scopeSuite = suite("Scope")(
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
    test("rendering a read scope to string is successful") {
      val scope = Scope(Set(readScopeValue1))
      assertTrue(scope.toScopeString == s"read:project:0001")
    },
    test("rendering a write scope to string is successful") {
      val scope = Scope(Set(writeScopeValue2))
      assertTrue(scope.toScopeString == s"write:project:0002")
    },
    test("rendering an admin scope to string is successful") {
      val scope = Scope.admin
      assertTrue(scope.toScopeString == s"admin")
    },
    test("rendering a combined scope to string is successful") {
      val scope = Scope(Set(readScopeValue1, writeScopeValue2))
      assertTrue(scope.toScopeString == s"read:project:0001 write:project:0002")
    },
  )

  private val parseSuite = suite("Scope.parse")(
    test("should parse the admin scope and ignore unknown values") {
      assertTrue(
        Scope.parse("admin profile") == Right(Scope.admin),
        Scope.parse("admin admin admin") == Right(Scope.admin),
      )
    },
    test("should parse the project write scope") {
      assertTrue(
        Scope.parse("write:project:0001") == Right(Scope(Set(writeScopeValue1))),
        Scope.parse("write:project:0001 write:project:0002") == Right(Scope(Set(writeScopeValue1, writeScopeValue2))),
      )
    },
    test("should parse the project read scope") {
      assertTrue(
        Scope.parse("read:project:0001") == Right(Scope(Set(readScopeValue1))),
        Scope.parse("read:project:0001 write:project:0002") == Right(Scope(Set(readScopeValue1, writeScopeValue2))),
      )
    },
    test("should invert toScopeString") {
      val scope = Scope(Set(readScopeValue1, writeScopeValue2))
      assertTrue(Scope.parse(scope.toScopeString) == Right(scope))
    },
    test("should ignore unknown keys and report bad project shortcodes") {
      assertTrue(
        Scope.parse("write:project:1234x") == Left("Shortcode is invalid: 1234x"),
        Scope.parse("read:project:12") == Left("Shortcode is invalid: 12"),
        Scope.parse("write project:1234") == Right(Scope.empty),
        Scope.parse("write:project1234") == Right(Scope.empty),
        Scope.parse("") == Right(Scope.empty),
      )
    },
  )

  private val checksSuite = suite("Scope checks")(
    test("only admin has admin") {
      assertTrue(
        Scope.admin.hasAdmin,
        !Scope.empty.hasAdmin,
        !Scope(Set(readScopeValue1)).hasAdmin,
        !Scope(Set(writeScopeValue1)).hasAdmin,
      )
    },
    test("a project is readable with read, write or admin for that project") {
      assertTrue(
        !Scope.empty.projectReadable(prj1),
        !Scope(Set(readScopeValue1)).projectReadable(prj2),
        !Scope(Set(writeScopeValue1)).projectReadable(prj2),
        Scope(Set(readScopeValue1)).projectReadable(prj1),
        Scope(Set(writeScopeValue1)).projectReadable(prj1),
        Scope.admin.projectReadable(prj1),
      )
    },
    test("a project is writable with write or admin for that project") {
      assertTrue(
        !Scope.empty.projectWritable(prj1),
        !Scope(Set(readScopeValue1)).projectWritable(prj1),
        !Scope(Set(writeScopeValue1)).projectWritable(prj2),
        Scope(Set(writeScopeValue1)).projectWritable(prj1),
        Scope.admin.projectWritable(prj1),
      )
    },
  )

  val spec = suite("ScopeSpec")(scopeValueSuite, scopeSuite, parseSuite, checksSuite)
}
