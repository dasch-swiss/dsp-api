package org.knora.webapi.slice.common

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import zio.test.*
import zio.*

object KnoraIrisSpec extends ZIOSpecDefault {

  private val converter = ZIO.serviceWithZIO[IriConverter]

  private val propertyIriSuite = suite("PropertyIri.from")(
    test("should return a PropertyIri") {
      val validIris = Seq(
        // internal ontology
        "http://www.knora.org/ontology/0001/anything#hasListItem",
        // external api v2 complex
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem",
      )
      check(Gen.fromIterable(validIris)) { iri =>
        for {
          actual <- converter(_.asPropertyIri(iri))
        } yield assertTrue(actual.smartIri.toString == iri)
      }
    },
    test("should fail for invalid PropertyIri") {
      val invalidIris = Seq(
        "http://example.com/ontology#Foo",
      )
      check(Gen.fromIterable(invalidIris)) { iri =>
        for {
          actual <- converter(_.asPropertyIri(iri)).exit
        } yield assertTrue(actual.isFailure)
      }
    },
  )

  private val resourceClassIriSuite = suite("ResourceClassIri.from")(
    test("should return a ResourceClassIri") {
      val validIris = Seq(
        // internal ontology
        "http://www.knora.org/ontology/0001/anything#Thing",
        // external api v2 complex
        "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
      )
      check(Gen.fromIterable(validIris)) { iri =>
        for {
          actual <- converter(_.asResourceClassIri(iri))
        } yield assertTrue(actual.smartIri.toString == iri)
      }
    },
    test("should fail for invalid ResourceClassIri") {
      val invalidIris = Seq(
        "http://example.com/ontology#Foo",
        "http://0.0.0.0/ontology/0001/anything/v2#Thing",
        "http://rdfh.ch/0001/5zCt1EMJKezFUOW_RCB0Gw/values/tdWAtnWK2qUC6tr4uQLAHA",
      )
      check(Gen.fromIterable(invalidIris)) { iri =>
        for {
          actual <- converter(_.asResourceClassIri(iri)).exit
        } yield assertTrue(actual.isFailure)
      }
    },
  )

  val spec =
    suite("KnoraIris")(resourceClassIriSuite, propertyIriSuite).provide(IriConverter.layer, StringFormatter.test)
}
