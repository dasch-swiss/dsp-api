/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources

import zio.*

import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v3.resources.BatchChangeStatus.*
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.resources.domain.BatchEdit
import org.knora.webapi.slice.resources.domain.BatchEditFailure
import org.knora.webapi.slice.resources.domain.BatchEditOutcome
import org.knora.webapi.slice.resources.service.BatchEditService

/**
 * Parses the changes of a batch request and reports a result per change. A change that does not parse is `invalid`
 * and is not applied; the others go to [[BatchEditService]] in their order.
 */
final class BatchChangesRestService(service: BatchEditService, iriConverter: IriConverter) {

  def applyBatch(user: User)(request: BatchChangesRequest): UIO[BatchChangesResponse] =
    for {
      parsed   <- ZIO.foreach(request.changes)(parse(_).either)
      outcomes <- service(user, parsed.collect { case Right(edit) => edit })
    } yield BatchChangesResponse(results(parsed, outcomes))

  /** Results in request order: a parse error where a change did not parse, otherwise the next outcome. */
  private def results(parsed: List[Either[String, BatchEdit]], outcomes: List[BatchEditOutcome]) =
    parsed.zipWithIndex
      .foldLeft((List.empty[BatchChangeResult], outcomes)) {
        case ((acc, rest), (Left(message), index)) =>
          (BatchChangeResult(index, invalid, message = Some(message)) :: acc, rest)
        case ((acc, outcome :: rest), (Right(_), index)) => (toResult(index, outcome) :: acc, rest)
        case ((acc, Nil), (Right(_), index))             =>
          (BatchChangeResult(index, error, message = Some("No outcome for this change.")) :: acc, Nil)
      }
      ._1
      .reverse

  private def toResult(index: Int, outcome: BatchEditOutcome): BatchChangeResult = outcome match {
    case BatchEditOutcome.Applied(valueIri)        => BatchChangeResult(index, ok, valueIri.map(_.value))
    case BatchEditOutcome.Failed(failure, message) => BatchChangeResult(index, status(failure), message = Some(message))
  }

  private def status(failure: BatchEditFailure): BatchChangeStatus = failure match {
    case BatchEditFailure.Conflict  => conflict
    case BatchEditFailure.Forbidden => forbidden
    case BatchEditFailure.NotFound  => not_found
    case BatchEditFailure.Invalid   => invalid
    case BatchEditFailure.Error     => error
  }

  private def parse(change: BatchChange): IO[String, BatchEdit] =
    for {
      resource <- ZIO.fromEither(ResourceIri.from(change.resource))
      edit     <- (change.label, change.property, change.newValue) match {
                case (Some(label), None, None)
                    if change.value.isEmpty && change.expected.isEmpty && change.expectEmpty.isEmpty =>
                  ZIO.succeed(BatchEdit.SetLabel(resource, label.expected, label.newLabel))
                case (None, Some(property), Some(newValue))
                    if !(change.expectEmpty.contains(true) && (change.value.isDefined || change.expected.isDefined)) =>
                  for {
                    propertyIri <- iriConverter.asPropertyIriApiV2Complex(property)
                    valueIri    <- ZIO.foreach(change.value)(v => ZIO.fromEither(ValueIri.from(v)))
                  } yield BatchEdit.SetValue(
                    resource,
                    propertyIri,
                    valueIri,
                    change.expected,
                    change.expectEmpty.contains(true),
                    newValue,
                  )
                case _ =>
                  ZIO.fail(
                    "A change sets either `label`, or `property` and `new`; `expectEmpty` only goes with a create.",
                  )
              }
    } yield edit
}

object BatchChangesRestService {
  val layer = ZLayer.derive[BatchChangesRestService]
}
