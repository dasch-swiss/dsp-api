/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.resources

import sttp.tapir.Schema
import sttp.tapir.Schema.annotations.encodedName
import sttp.tapir.Validator
import zio.json.*

/**
 * One planned write of a bulk edit. Exactly one of two shapes:
 *
 *  - a value change: `property` and `new` are set. Without `value` a new value is created; with `value` that value is
 *    updated, and `expected` is the content the client saw when it planned the change. A create with `expectEmpty`
 *    is only written while the property holds no value other than those created earlier in the same batch.
 *  - a label change: only `label` is set.
 *
 * For a text value `new` and `expected` are the plain text; for a list value they are the list node IRI.
 */
final case class BatchChange(
  resource: String,
  property: Option[String] = None,
  value: Option[String] = None,
  expected: Option[String] = None,
  @jsonField("new") @encodedName("new") newValue: Option[String] = None,
  label: Option[BatchLabelChange] = None,
  expectEmpty: Option[Boolean] = None,
)
object BatchChange {
  given JsonCodec[BatchChange] = DeriveJsonCodec.gen[BatchChange]
  given Schema[BatchChange]    = Schema.derived[BatchChange]
}

final case class BatchLabelChange(
  expected: Option[String] = None,
  @jsonField("new") @encodedName("new") newLabel: String,
)
object BatchLabelChange {
  given JsonCodec[BatchLabelChange] = DeriveJsonCodec.gen[BatchLabelChange]
  given Schema[BatchLabelChange]    = Schema.derived[BatchLabelChange]
}

final case class BatchChangesRequest(changes: List[BatchChange])
object BatchChangesRequest {
  // Sized to finish well within the ingress idle timeout (60 s on prod), like max-batch-size in application.conf.
  val MaxChanges: Int = 100

  given JsonCodec[BatchChangesRequest] = DeriveJsonCodec.gen[BatchChangesRequest]
  given Schema[BatchChangesRequest]    =
    Schema
      .derived[BatchChangesRequest]
      .modify(_.changes)(_.validate(Validator.minSize(1)).validate(Validator.maxSize(MaxChanges)))
}

enum BatchChangeStatus {
  case ok, conflict, forbidden, not_found, invalid, error
}
object BatchChangeStatus {
  given JsonCodec[BatchChangeStatus] = JsonCodec.string.transformOrFail(
    s => BatchChangeStatus.values.find(_.toString == s).toRight(s"Unknown status: $s"),
    _.toString,
  )
  given Schema[BatchChangeStatus] = Schema.derivedEnumeration[BatchChangeStatus].defaultStringBased
}

/**
 * The outcome of the change at `index` in the request. `valueIri` is the IRI of the created or new value version;
 * `message` explains a failure.
 */
final case class BatchChangeResult(
  index: Int,
  status: BatchChangeStatus,
  valueIri: Option[String] = None,
  message: Option[String] = None,
)
object BatchChangeResult {
  given JsonCodec[BatchChangeResult] = DeriveJsonCodec.gen[BatchChangeResult]
  given Schema[BatchChangeResult]    = Schema.derived[BatchChangeResult]
}

final case class BatchChangesResponse(results: List[BatchChangeResult])
object BatchChangesResponse {
  given JsonCodec[BatchChangesResponse] = DeriveJsonCodec.gen[BatchChangesResponse]
  given Schema[BatchChangesResponse]    = Schema.derived[BatchChangesResponse]
}
