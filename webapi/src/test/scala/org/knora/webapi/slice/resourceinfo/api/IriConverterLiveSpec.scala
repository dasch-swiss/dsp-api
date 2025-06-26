/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resourceinfo.api

import zio.ZIO
import zio.test.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.service.IriConverter
object IriConverterLiveSpec extends ZIOSpecDefault {

  private val iriConverter    = ZIO.serviceWithZIO[IriConverter]
  private val someInternalIri = "http://www.knora.org/ontology/0001/anything#Thing"
  private val someExternalIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing"

  def spec: Spec[Any, Throwable] =
    suite("iriConverter(_")(
      suite("asInternalIri(IRI)")(
        test("should not convert an already internal iri") {
          for {
            actual <- iriConverter(_.asInternalIri(someInternalIri))
          } yield assertTrue(actual == InternalIri(someInternalIri))
        },
        test("should convert an external resourceClassIri to the internal representation") {
          for {
            actual <- iriConverter(_.asInternalIri(someExternalIri))
          } yield assertTrue(actual == InternalIri(someInternalIri))
        },
        test("should fail if String is no IRI") {
          for {
            actual <- iriConverter(_.asInternalIri("notAnIRI")).exit
          } yield assertTrue(actual.isFailure)
        },
      ),
      suite("asSmartIri(IRI)")(
        test("when provided an internal Iri should return correct SmartIri") {
          for {
            actual <- iriConverter(_.asSmartIri(someInternalIri))
          } yield assertTrue(actual.toIri == someInternalIri)
        },
        test("when provided an external Iri should return correct SmartIri") {
          for {
            actual <- iriConverter(_.asSmartIri(someExternalIri))
          } yield assertTrue(actual.toIri == someExternalIri)
        },
      ),
      suite("asInternalSmartIri(InternalIri)")(
        test("should return correct SmartIri") {
          for {
            actual <- iriConverter(_.asInternalSmartIri(someInternalIri))
          } yield assertTrue(actual.toIri == someInternalIri)
        },
        test("when provided an external Iri should return converted iri") {
          for {
            actual <- iriConverter(_.asInternalSmartIri(someExternalIri))
          } yield assertTrue(actual.toIri == someInternalIri)
        },
      ),
    ).provide(IriConverter.layer, StringFormatter.test)
}
