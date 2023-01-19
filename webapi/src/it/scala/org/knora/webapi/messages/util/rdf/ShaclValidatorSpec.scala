/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.rdf

import java.nio.file.Paths

import dsp.errors.AssertionException
import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.rdf._

/**
 * Tests implementations of [[ShaclValidator]].
 */
class ShaclValidatorSpec() extends CoreSpec {

  private val rdfFormatUtil: RdfFormatUtil   = RdfFeatureFactory.getRdfFormatUtil()
  private val nodeFactory: RdfNodeFactory    = RdfFeatureFactory.getRdfNodeFactory()
  private val shaclValidator: ShaclValidator = RdfFeatureFactory.getShaclValidator()

  private val conformsIri: IriNode =
    nodeFactory
      .makeIriNode(OntologyConstants.Shacl.Conforms)

  private val resultIri: IriNode =
    nodeFactory
      .makeIriNode(OntologyConstants.Shacl.Result)

  private val sourceConstraintComponentIri: IriNode =
    nodeFactory
      .makeIriNode(OntologyConstants.Shacl.SourceConstraintComponent)

  private val datatypeConstraintComponentIri: IriNode =
    nodeFactory
      .makeIriNode(OntologyConstants.Shacl.DatatypeConstraintComponent)

  private val maxCountConstraintComponentIri: IriNode =
    nodeFactory
      .makeIriNode(OntologyConstants.Shacl.MaxCountConstraintComponent)

  "ShaclValidator" should {
    "accept valid RDF" in {
      val validRdfStr =
        """
          |@prefix ex: <http://example.com/ns#> .
          |@prefix foaf: <http://xmlns.com/foaf/0.1/> .
          |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
          |
          |ex:sally a foaf:Person ;
          |  foaf:age "30"^^xsd:int .
          |""".stripMargin

      val validRdfModel: RdfModel = rdfFormatUtil.parseToRdfModel(
        rdfStr = validRdfStr,
        rdfFormat = Turtle
      )

      val shaclPath = Paths.get("test/person.ttl")

      val validationResult: ShaclValidationResult = shaclValidator.validate(
        rdfModel = validRdfModel,
        shaclPath = shaclPath
      )

      validationResult match {
        case ShaclValidationSuccess    => ()
        case _: ShaclValidationFailure => throw AssertionException("Validation should have succeeded")
      }
    }

    "reject invalid RDF" in {
      val invalidRdfStr =
        """
          |@prefix ex: <http://example.com/ns#> .
          |@prefix foaf: <http://xmlns.com/foaf/0.1/> .
          |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
          |
          |ex:sally a foaf:Person ;
          |  foaf:age 20, "30"^^xsd:int .
          |""".stripMargin

      val invalidRdfModel: RdfModel = rdfFormatUtil.parseToRdfModel(
        rdfStr = invalidRdfStr,
        rdfFormat = Turtle
      )

      val shaclPath = Paths.get("test/person.ttl")

      val validationResult: ShaclValidationResult = shaclValidator.validate(
        rdfModel = invalidRdfModel,
        shaclPath = shaclPath
      )

      validationResult match {
        case ShaclValidationSuccess => throw AssertionException("Validation should have failed")

        case ShaclValidationFailure(reportModel) =>
          // The validation report should say that the data doesn't conform.
          val conformsStatement: Statement = reportModel
            .find(
              subj = None,
              pred = Some(conformsIri),
              obj = None
            )
            .toSet
            .head

          assert(!conformsStatement.getBooleanObject)

          // There should be two sh:result entities.
          val results: Seq[Statement] = reportModel
            .find(
              subj = None,
              pred = Some(resultIri),
              obj = None
            )
            .toSeq

          assert(results.size == 2)

          // Get the IRI of the sh:sourceConstraintComponent mentioned in each sh:result.
          val constraintComponentIris: Set[IriNode] = results.map { resultStatement: Statement =>
            val resultBlankNode: BlankNode = resultStatement.getBlankNodeObject

            val sourceConstraintComponentStatement: Statement = reportModel
              .find(
                subj = Some(resultBlankNode),
                pred = Some(sourceConstraintComponentIri),
                obj = None
              )
              .toSet
              .head

            sourceConstraintComponentStatement.getIriObject
          }.toSet

          // There should be one DatatypeConstraintComponent and one MaxCountConstraintComponent.
          assert(constraintComponentIris == Set(datatypeConstraintComponentIri, maxCountConstraintComponentIri))
      }
    }
  }
}
