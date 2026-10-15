/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ProjectOntologyIndex
import org.knora.webapi.slice.admin.domain.service.ViewRestrictionsClassification.ValueKind
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
   * Counts value rows per permission literal, for one item type.
   *
   * Rows of deleted values and rows of properties that are not counted (link targets, metadata, standoff
   * links, unknown properties) are dropped. Returns the counted rows, grouped by literal with zero-count
   * literals omitted, and the property IRIs that are not in the ontologies at all.
   */
  def permissionCounts(
    rows: Seq[ValueCountRow],
    index: ProjectOntologyIndex,
    itemType: ValueItemType,
  ): (Seq[PermissionCountRow], Set[String]) = {
    val live    = rows.filterNot(_.deleted)
    val counted = live.flatMap { r =>
      index.valueProperties.get(r.propertyIri).map(kind => r.permissions -> contribution(kind, r, itemType))
    }
    val perLiteral = counted.groupMapReduce(_._1)(_._2)(_ + _)
    val result     = perLiteral.collect { case (literal, n) if n > 0 => PermissionCountRow(None, literal, n) }.toSeq
    val unknown    = live.map(_.propertyIri).filterNot(index.knownProperties.contains).toSet
    (result, unknown)
  }

  private def contribution(kind: ValueKind, row: ValueCountRow, itemType: ValueItemType): Int =
    itemType match {
      case ValueItemType.All     => row.count
      case ValueItemType.File    => if kind == ValueKind.File then row.count else 0
      case ValueItemType.Value   => if kind != ValueKind.File then row.count else 0
      case ValueItemType.Comment => row.commented
    }

  /**
   * Folds value-count rows into per-audience counts and the total number of counted values.
   *
   * Logs one warning, naming the properties, when rows mention properties the index does not know.
   */
  def fold(
    rows: Seq[ValueCountRow],
    index: ProjectOntologyIndex,
    itemType: ValueItemType,
    projectIri: ProjectIri,
  ): UIO[(AudienceRestrictionCounts, Int)] = {
    val (counted, unknown) = permissionCounts(rows, index, itemType)
    val lookup             = ViewRestrictionsAudience.classify(counted.map(_.permissions).toSet, projectIri)
    ZIO
      .logWarning(
        s"View restrictions: ignored values of properties not in the ontology cache: ${unknown.toSeq.sorted.mkString(", ")}",
      )
      .when(unknown.nonEmpty)
      .as(foldRows(counted, lookup))
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
