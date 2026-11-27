/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

/**
 * The file content cannot be ingested; always answered as 422 Unprocessable Content.
 *
 * `message` reaches the client: it names the upload filename, never a server path.
 */
final case class IngestRejected(errorCode: String, message: String) extends Exception(message)
