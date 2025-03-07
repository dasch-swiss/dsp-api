/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import zio.*
import zio.test.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.common.KnoraIrisSpec.test
import org.knora.webapi.slice.ontology.domain.service.IriConverter
object KnoraIrisSpec extends ZIOSpecDefault {

  private val converter = ZIO.serviceWithZIO[IriConverter]

  // Some common test values
  private val internalPropertyIri     = "http://www.knora.org/ontology/0001/anything#hasListItem"
  private val apiV2ComplexPropertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem"

  private val internalResourceClassIri     = "http://www.knora.org/ontology/0001/anything#Thing"
  private val apiV2ComplexResourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing"

  private val valueIri    = "http://rdfh.ch/0001/thing-with-history/values/xZisRC3jPkcplt1hQQdb-A"
  private val resourceIri = "http://rdfh.ch/080C/Ef9heHjPWDS7dMR_gGax2Q"

  private val propertyIriSuite = suite("PropertyIri")(
    suite("from")(
      test("should return a PropertyIri") {
        val validIris = Seq(internalPropertyIri, apiV2ComplexPropertyIri, "http://example.com/foo#hasBar")
        check(Gen.fromIterable(validIris)) { iri =>
          for {
            sIri  <- converter(_.asSmartIri(iri))
            actual = PropertyIri.from(sIri)
          } yield assertTrue(actual.map(_.smartIri) == Right(sIri))
        }
      },
    ),
    suite("fromApiV2Complex")(
      test("should return a PropertyIri") {
        val validIris = Seq(apiV2ComplexPropertyIri)
        check(Gen.fromIterable(validIris)) { iri =>
          for {
            sIri  <- converter(_.asSmartIri(iri))
            actual = PropertyIri.fromApiV2Complex(sIri)
          } yield assertTrue(actual.map(_.smartIri) == Right(sIri))
        }
      },
      test("should fail for an non api V2 complex IRI") {
        val validIris = Seq(internalPropertyIri, "http://example.com/foo#hasBar")
        check(Gen.fromIterable(validIris)) { iri =>
          for {
            sIri  <- converter(_.asSmartIri(iri))
            actual = PropertyIri.fromApiV2Complex(sIri)
          } yield assertTrue(actual == Left(s"Not an API v2 complex IRI $iri"))
        }
      },
    ),
  )

  private val resourceClassIriSuite = suite("ResourceClassIri")(
    suite("from")(
      test("should return a ResourceClassIri") {
        val validIris = Seq(
          // internal ontology
          "http://www.knora.org/ontology/0001/anything#Thing",
          // external api v2 complex
          "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",

          /// other IRIs
          "http://example.com/ontology#Foo",
          "http://0.0.0.0/ontology/0001/anything/v2#Thing",
          "http://rdfh.ch/0001/5zCt1EMJKezFUOW_RCB0Gw/values/tdWAtnWK2qUC6tr4uQLAHA",
        )
        check(Gen.fromIterable(validIris)) { iri =>
          for {
            sIri  <- converter(_.asSmartIri(iri))
            actual = ResourceClassIri.from(sIri)
          } yield assertTrue(actual.map(_.smartIri) == Right(sIri))
        }
      },
    ),
    suite("fromApiV2Complex")(
      test("should fail for an internal IRI") {
        val iri = "http://www.knora.org/ontology/0001/anything#Thing"
        for {
          sIri  <- converter(_.asSmartIri(iri))
          actual = ResourceClassIri.fromApiV2Complex(sIri)
        } yield assertTrue(actual == Left(s"Not an API v2 complex IRI $iri"))
      },
    ),
  )

  private val valueIriSuite = suite("ValueIri")(
    suite("from")(
      test("should return a ValueIri") {
        val validIris = Seq(valueIri)
        check(Gen.fromIterable(validIris)) { iri =>
          for {
            sIri  <- converter(_.asSmartIri(iri))
            actual = ValueIri.from(sIri)
          } yield assertTrue(actual.map(_.smartIri) == Right(sIri))
        }
      },
      test("should fail for an invalid ValueIri") {
        val invalidIris = Seq(
          "http://example.com/ontology#Foo",
          internalResourceClassIri,
          apiV2ComplexResourceClassIri,
          internalPropertyIri,
          apiV2ComplexPropertyIri,
          resourceIri,
        )
        check(Gen.fromIterable(invalidIris)) { iri =>
          for {
            sIri  <- converter(_.asSmartIri(iri))
            actual = ValueIri.from(sIri)
          } yield assertTrue(actual == Left(s"<${sIri.toIri}> is not a Knora value IRI"))
        }
      },
    ),
  )

  private val resourceIriSuite = suite("ResourceIri")(
    suite("from")(
      test("should return a ResourceIri") {
        val validIris = Seq(resourceIri)
        check(Gen.fromIterable(validIris)) { iri =>
          for {
            sIri  <- converter(_.asSmartIri(iri))
            actual = ResourceIri.from(sIri)
          } yield assertTrue(actual.map(_.smartIri) == Right(sIri))
        }
      },
      test("should fail for an invalid ResourceIri") {
        val invalidIris = Seq(
          "http://example.com/ontology#Foo",
          internalResourceClassIri,
          apiV2ComplexResourceClassIri,
          internalPropertyIri,
          apiV2ComplexPropertyIri,
          valueIri,
        )
        check(Gen.fromIterable(invalidIris)) { iri =>
          for {
            sIri  <- converter(_.asSmartIri(iri))
            actual = ResourceIri.from(sIri)
          } yield assertTrue(actual == Left(s"<${sIri.toIri}> is not a Knora resource IRI"))
        }
      },
    ),
  )

  val spec = suite("KnoraIris")(
    resourceClassIriSuite,
    resourceIriSuite,
    propertyIriSuite,
    valueIriSuite,
  ).provide(IriConverter.layer, StringFormatter.test)
}
