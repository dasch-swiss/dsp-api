/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.util.rdf.jenaimpl

import java.io.File

import org.apache.jena
import org.apache.jena.query.DatasetFactory
import org.knora.webapi.messages.util.rdf._

/**
 * Performs SHACL validation using Jena.
 *
 * @param baseDir       the base directory that SHACL graphs are loaded from.
 * @param rdfFormatUtil an [[JenaFormatUtil]].
 * @param nodeFactory   a [[JenaNodeFactory]].
 */
class JenaShaclValidator(baseDir: File,
                         rdfFormatUtil: JenaFormatUtil,
                         private val nodeFactory: JenaNodeFactory)
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

    override def rdfModelToShaclGraph(rdfModel: RdfModel): jena.shacl.Shapes = {
        jena.shacl.Shapes.parse(rdfModel.asJenaDataset.asDatasetGraph.getDefaultGraph)
    }
}
