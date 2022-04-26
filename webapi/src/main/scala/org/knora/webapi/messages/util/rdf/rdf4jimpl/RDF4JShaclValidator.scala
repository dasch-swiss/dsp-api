/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf.rdf4jimpl

import java.nio.file.Path

import org.eclipse.rdf4j
import org.knora.webapi.messages.util.rdf._

import scala.util.{Failure, Success, Try}

/**
 * Performs SHACL validation using RDF4J.
 *
 * @param baseDir       the base directory that SHACL graphs are loaded from.
 * @param rdfFormatUtil an [[RdfFormatUtil]].
 * @param nodeFactory   an [[RDF4JNodeFactory]].
 */
class RDF4JShaclValidator(baseDir: Path, rdfFormatUtil: RDF4JFormatUtil, private val nodeFactory: RDF4JNodeFactory)
    extends AbstractShaclValidator[rdf4j.model.Model](baseDir, rdfFormatUtil) {

  import RDF4JConversions._

  override def validateWithShaclGraph(rdfModel: RdfModel, shaclGraph: rdf4j.model.Model): ShaclValidationResult = {
    // Make a SailRepository repository that supports SHACL validation.
    val shaclSail  = new rdf4j.sail.shacl.ShaclSail(new rdf4j.sail.memory.MemoryStore())
    val repository = new rdf4j.repository.sail.SailRepository(shaclSail)

    // Open a connection to the repository and begin a transaction.
    val connection: rdf4j.repository.sail.SailRepositoryConnection = repository.getConnection
    connection.begin()

    // Add the graph of SHACL shapes.
    connection.add(shaclGraph, rdf4j.model.vocabulary.RDF4J.SHACL_SHAPE_GRAPH)

    // Add the default graph of the input model to be validated.
    connection.add(rdfModel.asRDF4JModel.filter(null, null, null, null))

    // Commit the transaction to validate the data.
    val validationTry: Try[ShaclValidationResult] = Try {
      connection.commit()
      ShaclValidationSuccess
    }

    // Was an exception thrown?
    val validationResultTry: Try[ShaclValidationResult] = validationTry.recoverWith {
      // Yes. Was it because the data didn't pass validation?
      case repositoryException: rdf4j.repository.RepositoryException =>
        Option(repositoryException.getCause) match {
          case Some(cause: Throwable) =>
            cause match {
              case shaclValidationException: rdf4j.sail.shacl.ShaclSailValidationException =>
                // Yes. Convert the validation report to an RdfModel and return it.
                Success(
                  ShaclValidationFailure(
                    new RDF4JModel(
                      model = shaclValidationException.validationReportAsModel,
                      nodeFactory = nodeFactory
                    )
                  )
                )

              case _ =>
                // No, it was for some other reason.
                Failure(repositoryException)
            }

          case None =>
            // No, it was for some other reason.
            Failure(repositoryException)
        }

      case other: Throwable =>
        // No, it was for some other reason.
        Failure(other)

    }

    connection.close()
    repository.shutDown()
    validationResultTry.get
  }

  override protected def rdfModelToShaclGraph(rdfModel: RdfModel): rdf4j.model.Model =
    rdfModel.asRDF4JModel
}
