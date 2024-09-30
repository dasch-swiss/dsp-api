/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.domain

import org.apache.jena.rdf.model.Resource
import org.topbraid.jenax.util.JenaDatatypes
import org.topbraid.shacl.vocabulary.SH
import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object ShaclValidatorSpec extends ZIOSpecDefault {

  val shaclValidator = ZIO.serviceWithZIO[ShaclValidator]

  private val shapes =
    """
      |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      |@prefix schema: <http://schema.org/> .
      |@prefix sh: <http://www.w3.org/ns/shacl#> .
      |@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
      |
      |schema:PersonShape
      |    a sh:NodeShape ;
      |    sh:targetClass schema:Person ;
      |    sh:property [
      |        sh:path schema:givenName ;
      |        sh:datatype xsd:string ;
      |    ] .
      |""".stripMargin

  private val invalidData =
    """
      |@prefix ex: <http://example.org/ns#> .
      |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      |@prefix schema: <http://schema.org/> .
      |
      |ex:Bob
      |    a schema:Person ;
      |    schema:givenName 0 .
      |""".stripMargin

  private val validData =
    """
      |@prefix ex: <http://example.org/ns#> .
      |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      |@prefix schema: <http://schema.org/> .
      |
      |ex:Bob
      |    a schema:Person ;
      |    schema:givenName "valid name" .
      |""".stripMargin

  def spec: Spec[Any, Throwable] =
    suite("ShaclValidator")(
      test("validate with invalid data") {
        for {
          reportResource <- shaclValidator(_.validate(invalidData, shapes, ValidationOptions.default))
        } yield assertTrue(reportResource.hasProperty(SH.conforms, JenaDatatypes.FALSE))
      },
      test("validate with valid data") {
        for {
          reportResource <- shaclValidator(_.validate(validData, shapes, ValidationOptions.default))
        } yield assertTrue(reportResource.hasProperty(SH.conforms, JenaDatatypes.TRUE))
      },
    ).provide(ShaclValidator.layer)
}
