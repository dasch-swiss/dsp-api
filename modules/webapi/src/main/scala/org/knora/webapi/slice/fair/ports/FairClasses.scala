/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.ports

import zio.Task

/**
 * What the landing page needs to know about a resource class.
 *
 * @param labels       rdfs:label keyed by language tag; empty when none
 * @param requiresFile true when the class has a cardinality with a minimum of at least one on a file-value property
 */
final case class FairClass(labels: Map[String, String], requiresFile: Boolean)

trait FairClasses {

  /** The class's description; an unknown class or unparseable IRI yields no labels and `requiresFile = false`. */
  def describe(classIri: String): Task[FairClass]
}
