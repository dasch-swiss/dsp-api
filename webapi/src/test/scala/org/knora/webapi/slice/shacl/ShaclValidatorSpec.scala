package org.knora.webapi.slice.shacl
import org.apache.jena.rdf.model.Resource
import org.topbraid.jenax.util.JenaDatatypes
import org.topbraid.shacl.vocabulary.SH
import zio.ZIO
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.io.ByteArrayInputStream

object ShaclValidatorSpec extends ZIOSpecDefault {

  val shaclValidator = ZIO.serviceWithZIO[ShaclValidator]

  private val shapes =
    new ByteArrayInputStream("""
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
                               |        sh:name "given name" ;
                               |    ] .
                               |""".stripMargin.getBytes)

  private val invalidData =
    new ByteArrayInputStream("""
                               |@prefix ex: <http://example.org/ns#> .
                               |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                               |@prefix schema: <http://schema.org/> .
                               |
                               |ex:Bob
                               |    a schema:Person ;
                               |    schema:givenName 0 .
                               |""".stripMargin.getBytes)

  private val validData =
    new ByteArrayInputStream("""
                               |@prefix ex: <http://example.org/ns#> .
                               |@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                               |@prefix schema: <http://schema.org/> .
                               |
                               |ex:Bob
                               |    a schema:Person ;
                               |    schema:givenName "valid name" .
                               |""".stripMargin.getBytes)

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
