/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.PermissionCountRow
import org.knora.webapi.slice.admin.repo.ViewRestrictionsRepo.ValueCountRow
import org.knora.webapi.slice.api.admin.ViewRestrictionsEndpoints.*

/**
 * Pure folds from counted rows to per-audience restriction counts.
 *
 * Rows are counted per permission literal by the triplestore; the literals are classified per audience by
 * [[ViewRestrictionsAudience]], which also explains why that is sound.
 */
object ValueCountFold {

  /**
   * Counts value rows per permission literal, for one item type: `File` counts the file rows, `Value` the other
   * rows, `Comment` the commented values of every row, and `All` every value. Literals with a zero count are
   * omitted.
   */
  def permissionCounts(rows: Seq[ValueCountRow], itemType: ValueItemType): Seq[PermissionCountRow] =
    rows
      .groupMapReduce(_.permissions)(contribution(_, itemType))(_ + _)
      .collect { case (literal, n) if n > 0 => PermissionCountRow(literal, n) }
      .toSeq

  private def contribution(row: ValueCountRow, itemType: ValueItemType): Int =
    itemType match {
      case ValueItemType.All     => row.count
      case ValueItemType.File    => if row.isFile then row.count else 0
      case ValueItemType.Value   => if row.isFile then 0 else row.count
      case ValueItemType.Comment => row.commented
    }

  /** Folds value-count rows into per-audience counts and the total number of counted values. */
  def fold(
    rows: Seq[ValueCountRow],
    itemType: ValueItemType,
    projectIri: ProjectIri,
  ): (AudienceRestrictionCounts, Int) = {
    val counted = permissionCounts(rows, itemType)
    foldRows(counted, ViewRestrictionsAudience.classify(counted.map(_.permissions).toSet, projectIri))
  }

  /**
   * Folds permission-grouped rows into per-audience counts, and the total object count across every
   * literal.
   *
   * Because the grouped query applies no permission filter, summing a group's counts over all its literals,
   * the fully visible ones included, gives that group's entire population.
   */
  def foldRows(
    rows: Seq[PermissionCountRow],
    lookup: Map[(String, Audience), Visibility],
  ): (AudienceRestrictionCounts, Int) =
    rows.foldLeft((AudienceRestrictionCounts.zero, 0)) { case ((counts, total), row) =>
      val next = Audience.ordered.foldLeft(counts) { (acc, audience) =>
        lookup.get((row.permissions, audience)) match {
          case Some(Visibility.Hidden) =>
            AudienceRestrictionCounts.add(acc, audience, RestrictionCounts(row.count, 0))
          case Some(Visibility.RestrictedView) =>
            AudienceRestrictionCounts.add(acc, audience, RestrictionCounts(0, row.count))
          // Fully visible is not a restriction, so it contributes to the population only.
          case _ => acc
        }
      }
      (next, total + row.count)
    }
}
