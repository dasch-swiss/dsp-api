/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.shacl

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.topbraid.shacl.validation.ValidationEngineConfiguration
import org.topbraid.shacl.validation.ValidationUtil
import zio.*

import java.io.StringWriter
import java.nio.file.Path

enum ShaclValidationError {
  case LoadingError(cause: Throwable)
  case ValidationError(report: Resource)

  def message: String = this match {
    case LoadingError(cause)     => s"Error loading data for SHACL validation: ${cause.getMessage}"
    case ValidationError(report) =>
      val sw = new StringWriter()
      RDFDataMgr.write(sw, report.getModel, Lang.TURTLE)
      sw.toString
  }
}

object ShaclValidator {

  def validate(
    ontologies: NonEmptyChunk[Path],
    data: NonEmptyChunk[Path],
    shapes: NonEmptyChunk[Path],
  ): IO[ShaclValidationError, Unit] =
    ZIO.attempt {
      // Load ontologies as the schema model
      val schemaModel = ModelFactory.createOntologyModel()
      ontologies.foreach(path => RDFDataMgr.read(schemaModel, path.toUri.toString))

      // Load data into a separate model
      val baseDataModel = ModelFactory.createDefaultModel()
      data.foreach(path => RDFDataMgr.read(baseDataModel, path.toUri.toString))

      // Create an RDFS inference model: schema provides the TBox, data provides the ABox
      val dataModel = ModelFactory.createRDFSModel(schemaModel, baseDataModel)

      // Load shapes
      val shapesModel = ModelFactory.createDefaultModel()
      shapes.foreach(path => RDFDataMgr.read(shapesModel, path.toUri.toString))

      val config = new ValidationEngineConfiguration().setValidateShapes(false)
      val report = ValidationUtil.validateModel(dataModel, shapesModel, config)

      val conforms =
        report.getRequiredProperty(report.getModel.createProperty("http://www.w3.org/ns/shacl#conforms")).getBoolean
      (conforms, report)
    }
      .mapError(ShaclValidationError.LoadingError(_))
      .flatMap { case (conforms, report) =>
        if (conforms) ZIO.unit
        else ZIO.fail(ShaclValidationError.ValidationError(report))
      }
}
