/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.domain

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

object OaiShortcodes {

  /** Parses a comma-separated shortcode list; blank and invalid entries are dropped. */
  def parse(raw: String): Set[Shortcode] =
    raw.split(',').iterator.map(_.trim).filter(_.nonEmpty).flatMap(Shortcode.from(_).toOption).toSet
}
