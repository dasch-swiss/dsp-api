/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.domain

/**
 * Triplestore status
 * - ServiceUnavailable: Triplestore is not responding to HTTP requests.
 * - NotInitialized: Triplestore is responding to HTTP requests but the repository defined in 'application.conf' is missing.
 * - Available: Everything is OK.
 */
trait TriplestoreStatus {
  val msg: String
}
object TriplestoreStatus {
  final case class Unavailable(msg: String)    extends TriplestoreStatus
  final case class NotInitialized(msg: String) extends TriplestoreStatus
  final case class Available(msg: String)      extends TriplestoreStatus
}
