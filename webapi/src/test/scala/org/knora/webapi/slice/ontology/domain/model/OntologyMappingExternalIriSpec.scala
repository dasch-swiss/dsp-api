/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.model

import zio.test.*

import org.knora.webapi.slice.api.v2.IriDto

object OntologyMappingExternalIriSpec extends ZIOSpecDefault {
  val spec = suite("OntologyMappingExternalIri")(
    suite("from(String)")(
      test("should accept valid external IRIs") {
        val validIris = List(
          "http://schema.org/name",
          "http://www.w3.org/2002/07/owl#Class",
          "http://xmlns.com/foaf/0.1/Person",
          "https://www.wikidata.org/wiki/Q5",
          "http://purl.org/dc/terms/title",
          "http://www.cidoc-crm.org/cidoc-crm/E21_Person",
        )
        check(Gen.fromIterable(validIris)) { iri =>
          assertTrue(OntologyMappingExternalIri.from(iri).map(_.value) == Right(iri))
        }
      },
      test("should reject non-IRI strings") {
        val invalidIris = List("not-an-iri", "", "just some text", "ftp://")
        check(Gen.fromIterable(invalidIris)) { iri =>
          assertTrue(OntologyMappingExternalIri.from(iri).isLeft)
        }
      },
      test("should reject IRIs with knora.org host") {
        val knoraIris = List(
          "http://www.knora.org/ontology/knora-base",
          "http://www.knora.org/ontology/knora-admin",
          "http://api.knora.org/ontology/shared/v2",
          "http://knora.org/something",
        )
        check(Gen.fromIterable(knoraIris)) { iri =>
          assertTrue(
            OntologyMappingExternalIri.from(iri) == Left(
              s"OntologyMappingExternalIri must not contain host 'knora.org': $iri",
            ),
          )
        }
      },
      test("should reject IRIs with dasch.swiss host") {
        val daschIris = List(
          "http://api.dasch.swiss/ontology/0001/anything/v2",
          "https://app.dasch.swiss/project/001",
          "http://dasch.swiss/something",
        )
        check(Gen.fromIterable(daschIris)) { iri =>
          assertTrue(
            OntologyMappingExternalIri.from(iri) == Left(
              s"OntologyMappingExternalIri must not contain host 'dasch.swiss': $iri",
            ),
          )
        }
      },
    ),
    suite("from(IriDto)")(
      test("should accept valid external IRIs") {
        val validIris = List(
          "http://schema.org/name",
          "http://www.w3.org/2002/07/owl#Class",
          "https://www.wikidata.org/wiki/Q5",
        )
        check(Gen.fromIterable(validIris)) { iri =>
          val dto = IriDto.unsafeFrom(iri)
          assertTrue(OntologyMappingExternalIri.from(dto).map(_.value) == Right(iri))
        }
      },
      test("should reject IRIs with knora.org host") {
        val dto = IriDto.unsafeFrom("http://www.knora.org/ontology/knora-base")
        assertTrue(OntologyMappingExternalIri.from(dto).isLeft)
      },
      test("should reject IRIs with dasch.swiss host") {
        val dto = IriDto.unsafeFrom("http://api.dasch.swiss/ontology/0001/anything/v2")
        assertTrue(OntologyMappingExternalIri.from(dto).isLeft)
      },
    ),
  )
}
