/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import java.nio.file.Path

/**
 * A trait for the results of SHACL validation operations.
 */
sealed trait ShaclValidationResult

/**
 * Indicates that data passed SHACL validation.
 */
case object ShaclValidationSuccess extends ShaclValidationResult

/**
 * Indicates that data did not pass SHACL validation.
 *
 * @param reportModel an [[RdfModel]] describing the failure.
 */
case class ShaclValidationFailure(reportModel: RdfModel) extends ShaclValidationResult

/**
 * A trait for classes that validate RDF models using SHACL shapes.
 */
trait ShaclValidator {

  /**
   * Validates the default graph of an [[RdfModel]] using SHACL shapes.
   *
   * @param rdfModel  the model to be validated.
   * @param shaclPath a path identifying the graph of SHACL shapes to be used.
   * @return a [[ShaclValidationResult]].
   */
  def validate(rdfModel: RdfModel, shaclPath: Path): ShaclValidationResult
}
