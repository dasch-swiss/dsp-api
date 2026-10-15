/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ProjectOntologyIndex
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ValueKind
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.PermissionCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType

/**
 * Tests for [[ValueCountFold]]. Pure: no triplestore and no ontology cache.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ValueCountFoldSpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")

  private val anything = "http://www.knora.org/ontology/0001/anything#"
  private val plain    = s"${anything}hasText"
  private val file     = s"${anything}hasImage"
  private val link     = s"${anything}hasOtherThingValue"
  private val isPartOf = "http://www.knora.org/ontology/knora-base#isPartOf"
  private val thing    = s"${anything}Thing"
  private val member   = "M knora-admin:ProjectMember"
  private val open     = "V knora-admin:UnknownUser"

  private val index = ProjectOntologyIndex(
    resourceClasses = Set(thing),
    valueProperties = Map(plain -> ValueKind.Plain, file -> ValueKind.File, link -> ValueKind.Link),
    knownProperties = Set(plain, file, link, isPartOf),
  )

  private def row(
    property: String,
    permissions: String = member,
    count: Int = 1,
    commented: Int = 0,
    deleted: Boolean = false,
  ) = ValueCountRow(thing, property, permissions, deleted, count, commented)

  private def counts(rows: Seq[ValueCountRow], itemType: ValueItemType): Seq[PermissionCountRow] =
    ValueCountFold.permissionCounts(rows, index, itemType)._1

  private val permissionCountsSuite = suite("permissionCounts")(
    test("deleted rows are dropped") {
      assertTrue(counts(Seq(row(plain, deleted = true)), ValueItemType.All).isEmpty)
    },
    test("a property missing from the index is dropped and reported as unknown") {
      val (rows, unknown) = ValueCountFold.permissionCounts(Seq(row("urn:x:unknown")), index, ValueItemType.All)
      assertTrue(rows.isEmpty, unknown == Set("urn:x:unknown"))
    },
    test("a link target row is dropped and not reported as unknown, because its property is in the ontology") {
      val (rows, unknown) = ValueCountFold.permissionCounts(Seq(row(isPartOf)), index, ValueItemType.All)
      assertTrue(rows.isEmpty, unknown.isEmpty)
    },
    test("All counts every kind and sums per permission literal") {
      val result = counts(Seq(row(plain, count = 2), row(file), row(link, open)), ValueItemType.All)
      assertTrue(
        result.toSet == Set(
          PermissionCountRow(member, 3),
          PermissionCountRow(open, 1),
        ),
      )
    },
    test("File counts only file values") {
      val result = counts(Seq(row(plain), row(file, count = 2), row(link)), ValueItemType.File)
      assertTrue(result == Seq(PermissionCountRow(member, 2)))
    },
    test("Value counts plain and link values, not file values") {
      val result = counts(Seq(row(plain), row(file, count = 5), row(link, count = 2)), ValueItemType.Value)
      assertTrue(result == Seq(PermissionCountRow(member, 3)))
    },
    test("Comment counts commented values of every kind") {
      val result = counts(Seq(row(plain, commented = 1), row(file, commented = 2), row(link)), ValueItemType.Comment)
      assertTrue(result == Seq(PermissionCountRow(member, 3)))
    },
    test("a link value with a comment counts under Comment") {
      val result = counts(Seq(row(link, commented = 1)), ValueItemType.Comment)
      assertTrue(result == Seq(PermissionCountRow(member, 1)))
    },
    test("a commented value counts once under All") {
      val result = counts(Seq(row(plain, count = 1, commented = 1)), ValueItemType.All)
      assertTrue(result == Seq(PermissionCountRow(member, 1)))
    },
    test("literals whose summed count is zero are omitted") {
      val result = counts(Seq(row(plain, commented = 0), row(plain, open, commented = 1)), ValueItemType.Comment)
      assertTrue(result == Seq(PermissionCountRow(open, 1)))
    },
  )

  private val foldSuite = suite("fold")(
    test("classifies the grouped counts per audience and totals them") {
      for {
        (counts, total) <-
          ValueCountFold.fold(Seq(row(plain, count = 2), row(plain, open)), index, ValueItemType.All, projectIri)
      } yield assertTrue(total == 3, counts.anonymous.hidden == 2, counts.projectMember.hidden == 0)
    },
    test("logs exactly one warning naming the unknown property IRIs, sorted") {
      for {
        before <- warningMessages
        _      <-
          ValueCountFold.fold(Seq(row("urn:x:b"), row("urn:x:a"), row("urn:x:b")), index, ValueItemType.All, projectIri)
        after <- warningMessages
      } yield assertTrue(
        after.drop(before.size) == List(
          "View restrictions: ignored values of properties not in the ontology cache: urn:x:a, urn:x:b",
        ),
      )
    },
    test("logs no warning when every property is known") {
      for {
        before <- warningMessages
        _      <- ValueCountFold.fold(Seq(row(plain), row(file)), index, ValueItemType.All, projectIri)
        after  <- warningMessages
      } yield assertTrue(after.size == before.size)
    },
    test("logs no warning for a known property that is not counted") {
      for {
        before <- warningMessages
        _      <- ValueCountFold.fold(Seq(row(isPartOf)), index, ValueItemType.All, projectIri)
        after  <- warningMessages
      } yield assertTrue(after.size == before.size)
    },
    test("does not report a deleted row of an unknown property") {
      for {
        before <- warningMessages
        _      <- ValueCountFold.fold(Seq(row("urn:x:unknown", deleted = true)), index, ValueItemType.All, projectIri)
        after  <- warningMessages
      } yield assertTrue(after.size == before.size)
    },
  ) @@ TestAspect.sequential

  private val warningMessages: UIO[List[String]] =
    ZTestLogger.logOutput.map(_.filter(_.logLevel == LogLevel.Warning).map(_.message()).toList)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ValueCountFold")(permissionCountsSuite, foldSuite)
}
