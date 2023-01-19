/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf.jenaimpl

import org.apache.jena
import org.apache.jena.query.DatasetFactory

import java.nio.file.Path

import org.knora.webapi.messages.util.rdf._

/**
 * Performs SHACL validation using Jena.
 *
 * @param baseDir       the base directory that SHACL graphs are loaded from.
 * @param rdfFormatUtil an [[JenaFormatUtil]].
 * @param nodeFactory   a [[JenaNodeFactory]].
 */
class JenaShaclValidator(baseDir: Path, rdfFormatUtil: JenaFormatUtil, private val nodeFactory: JenaNodeFactory)
    extends AbstractShaclValidator[jena.shacl.Shapes](baseDir, rdfFormatUtil) {

  import JenaConversions._

  private val shaclValidator: jena.shacl.ShaclValidator = jena.shacl.ShaclValidator.get

  override def validateWithShaclGraph(rdfModel: RdfModel, shaclGraph: jena.shacl.Shapes): ShaclValidationResult = {
    // Get the jena.graph.Graph representing the model's default graph.
    val graphToValidate: jena.graph.Graph = rdfModel.asJenaDataset.asDatasetGraph.getDefaultGraph

    // Validate the data and get Jena's validation report.
    val validationReport: jena.shacl.ValidationReport = shaclValidator.validate(shaclGraph, graphToValidate)

    // Did the data pass validation?
    if (validationReport.conforms) {
      // Yes.
      ShaclValidationSuccess
    } else {
      // No. Convert the validation report to an RdfModel, and return it.
      ShaclValidationFailure(
        new JenaModel(
          dataset = DatasetFactory.wrap(validationReport.getModel),
          nodeFactory = nodeFactory
        )
      )
    }
  }

  override def rdfModelToShaclGraph(rdfModel: RdfModel): jena.shacl.Shapes =
    jena.shacl.Shapes.parse(rdfModel.asJenaDataset.asDatasetGraph.getDefaultGraph)
}
