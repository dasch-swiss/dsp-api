/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora

package object webapi {

  /**
   * The version of `knora-base` and of the other built-in ontologies that this version of Knora requires.
   * Must be the same as the object of `knora-base:ontologyVersion` in the `knora-base` ontology being used.
   */
  val KnoraBaseVersion: String = "knora-base v13"

  /**
   * `IRI` is a synonym for `String`, used to improve code readability.
   */
  type IRI = String

}
