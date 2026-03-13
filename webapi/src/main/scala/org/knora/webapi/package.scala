/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora

package object webapi {

  private val versionPrefix                                    = "knora-base v"
  def knoraBaseVersionFrom(versionString: String): Option[Int] = versionString.replace(versionPrefix, "").toIntOption

  /**
   * The version of `knora-base` and of the other built-in ontologies that this version of Knora requires.
   * Must be the same as the object of `knora-base:ontologyVersion` in the `knora-base` ontology being used.
   *
   * NOTE: A v51 entry was introduced on feature/add-ontology-mapping-endpoints
   * but was never merged to main and was never deployed to production. It has been removed here.
   * Safe to deploy only to environments at v50 or below.
   * If deployed to an environment at v51, RepositoryUpdater will call ZIO.die (crash on startup).
   */
  val KnoraBaseVersion: Int          = 50
  val KnoraBaseVersionString: String = s"$versionPrefix$KnoraBaseVersion"

  /**
   * `IRI` is a synonym for `String`, used to improve code readability.
   */
  type IRI = String

}
