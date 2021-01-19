/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
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
