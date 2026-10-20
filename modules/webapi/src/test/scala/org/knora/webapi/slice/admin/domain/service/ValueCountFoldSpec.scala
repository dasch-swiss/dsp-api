/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.PermissionCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.ValueItemType

/**
 * Tests for [[ValueCountFold]]. Pure: no triplestore.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ValueCountFoldSpec extends ZIOSpecDefault {

  private val projectIri = ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001")

  private val anything = "http://www.knora.org/ontology/0001/anything#"
  private val plain    = s"${anything}hasText"
  private val file     = s"${anything}hasImage"
  private val link     = s"${anything}hasOtherThingValue"
  private val thing    = s"${anything}Thing"
  private val member   = "M knora-admin:ProjectMember"
  private val open     = "V knora-admin:UnknownUser"

  private def row(
    property: String,
    permissions: String = member,
    count: Int = 1,
    commented: Int = 0,
    isFile: Boolean = false,
  ) = ValueCountRow(thing, property, permissions, isFile, count, commented)

  private def counts(rows: Seq[ValueCountRow], itemType: ValueItemType): Seq[PermissionCountRow] =
    ValueCountFold.permissionCounts(rows, itemType)

  private val permissionCountsSuite = suite("permissionCounts")(
    test("All counts every row and sums per permission literal") {
      val result = counts(Seq(row(plain, count = 2), row(file, isFile = true), row(link, open)), ValueItemType.All)
      assertTrue(
        result.toSet == Set(
          PermissionCountRow(member, 3),
          PermissionCountRow(open, 1),
        ),
      )
    },
    test("File counts only file values") {
      val result = counts(Seq(row(plain), row(file, count = 2, isFile = true), row(link)), ValueItemType.File)
      assertTrue(result == Seq(PermissionCountRow(member, 2)))
    },
    test("Value counts plain and link values, not file values") {
      val result =
        counts(Seq(row(plain), row(file, count = 5, isFile = true), row(link, count = 2)), ValueItemType.Value)
      assertTrue(result == Seq(PermissionCountRow(member, 3)))
    },
    test("Comment counts commented values of every kind") {
      val result = counts(
        Seq(row(plain, commented = 1), row(file, commented = 2, isFile = true), row(link)),
        ValueItemType.Comment,
      )
      assertTrue(result == Seq(PermissionCountRow(member, 3)))
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
      val (counts, total) =
        ValueCountFold.fold(Seq(row(plain, count = 2), row(plain, open)), ValueItemType.All, projectIri)
      assertTrue(total == 3, counts.anonymous.hidden == 2, counts.projectMember.hidden == 0)
    },
  )

  override def spec: Spec[TestEnvironment, Any] =
    suite("ValueCountFold")(permissionCountsSuite, foldSuite)
}
