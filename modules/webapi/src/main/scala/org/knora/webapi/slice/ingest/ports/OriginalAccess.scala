/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.ports

import zio.Task

import org.knora.webapi.slice.ingest.domain.ProjectShortcode

trait OriginalAccess {

  /**
   * Whether dsp-api grants the caller the Original of the asset whose derivative is `derivativeFilename`. An absent
   * token is the anonymous user; a token that does not authenticate fails.
   */
  def isOriginalGranted(token: Option[String], shortcode: ProjectShortcode, derivativeFilename: String): Task[Boolean]
}
