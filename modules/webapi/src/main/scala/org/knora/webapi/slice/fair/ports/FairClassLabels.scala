/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.ports

import zio.Task

trait FairClassLabels {

  /** rdfs:label of the class, keyed by language tag; empty when none. */
  def labels(classIri: String): Task[Map[String, String]]
}
