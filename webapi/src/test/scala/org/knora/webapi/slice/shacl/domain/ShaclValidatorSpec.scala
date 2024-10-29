/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.shacl.domain

import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
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
      |@prefix sh:   <http://www.w3.org/ns/shacl#> .
      |@prefix owl:  <http://www.w3.org/2002/07/owl#> .
      |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      |@prefix dash: <http://datashapes.org/dash#> .
      |@prefix onto: <http://0.0.0.0:3333/ontology/9990/onto/v2#> .
      |@prefix data: <http://0.0.0.0:3333/ontology/9990/data/> .
      |
      |# ONTO
      |onto:Resource0 a owl:Class .
      |onto:Resource1
      |  a               owl:Class ;
      |  rdfs:subClassOf onto:Resource0 .
      |onto:hasText0 a owl:DatatypeProperty .
      |onto:hasText1
      |  a                  owl:DatatypeProperty ;
      |  rdfs:subPropertyOf onto:hasText0 .
      |
      |# SHAPES
      |onto:Resource0_Shape
      |  a                  sh:NodeShape ;
      |  sh:targetClass     onto:Resource0 ;
      |  dash:closedByTypes true ;
      |  sh:property        [
      |                       a           sh:PropertyShape ;
      |                       sh:path     onto:hasText0 ;
      |                       sh:minCount 1 ;
      |                       sh:maxCount 1
      |                     ] .
      |
      |onto:Resource1_Shape
      |  a                  sh:NodeShape ;
      |  dash:closedByTypes true ;
      |  sh:targetClass     onto:Resource1 ;
      |  sh:property        [
      |                       a           sh:PropertyShape ;
      |                       sh:path     onto:hasText1 ;
      |                       sh:minCount 1 ;
      |                       sh:maxCount 1
      |                     ] .
      |""".stripMargin

  private val invalidData =
    """
      |@prefix owl:  <http://www.w3.org/2002/07/owl#> .
      |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      |@prefix onto: <http://0.0.0.0:3333/ontology/9990/onto/v2#> .
      |@prefix data: <http://0.0.0.0:3333/ontology/9990/data/> .
      |
      |# ONTO
      |onto:Resource0 a owl:Class .
      |onto:Resource1
      |  a               owl:Class ;
      |  rdfs:subClassOf onto:Resource0 .
      |onto:hasText0 a owl:DatatypeProperty .
      |onto:hasText1
      |  a                  owl:DatatypeProperty ;
      |  rdfs:subPropertyOf onto:hasText0 .
      |
      |# DATA
      |data:Resource0DataInvalid
      |  a             onto:Resource0 ;
      |  onto:hasText0 "Text0" , "Text0.1".
      |""".stripMargin

  private val validData =
    """
      |@prefix owl:  <http://www.w3.org/2002/07/owl#> .
      |@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      |@prefix onto: <http://0.0.0.0:3333/ontology/9990/onto/v2#> .
      |@prefix data: <http://0.0.0.0:3333/ontology/9990/data/> .
      |
      |# ONTO
      |onto:Resource0 a owl:Class .
      |onto:Resource1
      |  a               owl:Class ;
      |  rdfs:subClassOf onto:Resource0 .
      |onto:hasText0 a owl:DatatypeProperty .
      |onto:hasText1
      |  a                  owl:DatatypeProperty ;
      |  rdfs:subPropertyOf onto:hasText0 .
      |
      |# DATA
      |data:Resource0DataValid
      |  a             onto:Resource0 ;
      |  onto:hasText0 "Text0".
      |""".stripMargin

  def spec: Spec[Any, Throwable] =
    suite("ShaclValidator")(
      test("validate with invalid data") {
        for {
          reportResource <- shaclValidator(_.validate(invalidData, shapes, ValidationOptions.default))
          _               = RDFDataMgr.write(java.lang.System.out, reportResource.getModel, RDFFormat.TURTLE)
        } yield assertTrue(reportResource.hasProperty(SH.conforms, JenaDatatypes.FALSE))
      },
      test("validate with valid data") {
        for {
          reportResource <- shaclValidator(_.validate(validData, shapes, ValidationOptions.default))
          _               = RDFDataMgr.write(java.lang.System.out, reportResource.getModel, RDFFormat.TURTLE)
        } yield assertTrue(reportResource.hasProperty(SH.conforms, JenaDatatypes.TRUE))
      },
    ).provide(ShaclValidator.layer)
}
