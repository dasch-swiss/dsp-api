/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.ontology

import sttp.client4.*
import sttp.model.StatusCode
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.api.v3.AddClassMappingsRequest
import org.knora.webapi.slice.api.v3.AddPropertyMappingsRequest
import org.knora.webapi.slice.api.v3.ClassMappingResponse
import org.knora.webapi.slice.api.v3.PropertyMappingResponse
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestApiClient

/**
 * E2E tests for the four ontology-mapping endpoints:
 *   F1  PUT    /v3/ontologies/{ontologyIri}/classes/{classIri}/mapping
 *   F2  DELETE /v3/ontologies/{ontologyIri}/classes/{classIri}/mapping?mapping=...
 *   F3  PUT    /v3/ontologies/{ontologyIri}/properties/{propertyIri}/mapping
 *   F4  DELETE /v3/ontologies/{ontologyIri}/properties/{propertyIri}/mapping?mapping=...
 *
 * sttp's uri interpolator URL-encodes each interpolated value as a path segment or query value,
 * so IRI strings (which contain '/', '#' etc.) are transmitted correctly.
 */
object OntologyMappingEndpointsE2ESpec extends E2EZSpec {

  override def rdfDataObjects: List[RdfDataObject] = anythingRdfOntologyAndData

  // -------------------------------------------------------------------------
  // Test IRIs — API v2 complex schema (what clients send).
  // -------------------------------------------------------------------------

  private val anythingOntIri    = "http://0.0.0.0:3333/ontology/0001/anything/v2"
  private val thingClassIri     = s"$anythingOntIri#Thing"
  private val hasIntegerPropIri = s"$anythingOntIri#hasInteger"

  private val extSchemaOrg     = "https://schema.org/Thing"
  private val extSchemaOrgName = "https://schema.org/name"

  private val unknownOntIri   = "http://0.0.0.0:3333/ontology/0001/nonexistent/v2"
  private val unknownClassIri = s"$anythingOntIri#NonExistentClass"
  private val unknownPropIri  = s"$anythingOntIri#nonExistentProperty"

  // -------------------------------------------------------------------------
  // Request helpers — sttp uri interpolation encodes the IRI path segments.
  // -------------------------------------------------------------------------

  private def putClassMappingUri(ontIri: String, classIri: String) =
    uri"/v3/ontologies/$ontIri/classes/$classIri/mapping"

  private def deleteClassMappingUri(ontIri: String, classIri: String, extIri: String) =
    uri"/v3/ontologies/$ontIri/classes/$classIri/mapping?mapping=$extIri"

  private def putPropertyMappingUri(ontIri: String, propIri: String) =
    uri"/v3/ontologies/$ontIri/properties/$propIri/mapping"

  private def deletePropertyMappingUri(ontIri: String, propIri: String, extIri: String) =
    uri"/v3/ontologies/$ontIri/properties/$propIri/mapping?mapping=$extIri"

  // -------------------------------------------------------------------------

  override val e2eSpec: Spec[env, Any] = suite("OntologyMapping E2E")(
    // -----------------------------------------------------------------------
    // F1 — PUT class mapping
    // -----------------------------------------------------------------------
    suite("F1 PUT class mapping")(
      test("C-1 happy path: adds a mapping and returns updated subClassOf") {
        for {
          response <- TestApiClient.putJson[ClassMappingResponse, AddClassMappingsRequest](
                        putClassMappingUri(anythingOntIri, thingClassIri),
                        AddClassMappingsRequest(List(extSchemaOrg)),
                        anythingAdminUser,
                      )
          result <- response.assert200
          // Clean up
          _ <- TestApiClient.deleteJson[ClassMappingResponse](
                 deleteClassMappingUri(anythingOntIri, thingClassIri, extSchemaOrg),
                 anythingAdminUser,
               )
        } yield assertTrue(
          result.subClassOf.contains(extSchemaOrg),
          result.classIri.nonEmpty,
          result.ontologyIri.nonEmpty,
        )
      },
      test("C-2 idempotent: applying the same mapping twice returns 200 both times") {
        for {
          r1 <- TestApiClient.putJson[ClassMappingResponse, AddClassMappingsRequest](
                  putClassMappingUri(anythingOntIri, thingClassIri),
                  AddClassMappingsRequest(List(extSchemaOrg)),
                  anythingAdminUser,
                )
          _  <- r1.assert200
          r2 <- TestApiClient.putJson[ClassMappingResponse, AddClassMappingsRequest](
                  putClassMappingUri(anythingOntIri, thingClassIri),
                  AddClassMappingsRequest(List(extSchemaOrg)),
                  anythingAdminUser,
                )
          _ <- r2.assert200
          // Clean up
          _ <- TestApiClient.deleteJson[ClassMappingResponse](
                 deleteClassMappingUri(anythingOntIri, thingClassIri, extSchemaOrg),
                 anythingAdminUser,
               )
        } yield assertTrue(r1.code == StatusCode.Ok, r2.code == StatusCode.Ok)
      },
      test("C-3 empty mappings list returns 400") {
        TestApiClient
          .putJson[ClassMappingResponse, AddClassMappingsRequest](
            putClassMappingUri(anythingOntIri, thingClassIri),
            AddClassMappingsRequest(List.empty),
            anythingAdminUser,
          )
          .flatMap(_.assert400)
          .map(body => assertTrue(body.contains("at least one")))
      },
      test("C-4 Knora IRI in mappings returns 400") {
        TestApiClient
          .putJson[ClassMappingResponse, AddClassMappingsRequest](
            putClassMappingUri(anythingOntIri, thingClassIri),
            AddClassMappingsRequest(List("http://www.knora.org/ontology/0001/anything#Thing")),
            anythingAdminUser,
          )
          .flatMap(_.assert400)
          .map(body => assertTrue(body.contains("external IRI")))
      },
      test("C-5 ontology not found returns 404") {
        TestApiClient
          .putJson[ClassMappingResponse, AddClassMappingsRequest](
            putClassMappingUri(unknownOntIri, s"$unknownOntIri#Thing"),
            AddClassMappingsRequest(List(extSchemaOrg)),
            anythingAdminUser,
          )
          .flatMap(_.assert404)
          .map(body => assertTrue(body.nonEmpty))
      },
      test("C-6 class not found in ontology returns 404") {
        TestApiClient
          .putJson[ClassMappingResponse, AddClassMappingsRequest](
            putClassMappingUri(anythingOntIri, unknownClassIri),
            AddClassMappingsRequest(List(extSchemaOrg)),
            anythingAdminUser,
          )
          .flatMap(_.assert404)
          .map(body => assertTrue(body.nonEmpty))
      },
      test("C-7 user without project admin rights returns 403") {
        TestApiClient
          .putJson[ClassMappingResponse, AddClassMappingsRequest](
            putClassMappingUri(anythingOntIri, thingClassIri),
            AddClassMappingsRequest(List(extSchemaOrg)),
            normalUser,
          )
          .map(r => assertTrue(r.code == StatusCode.Forbidden))
      },
    ),

    // -----------------------------------------------------------------------
    // F2 — DELETE class mapping
    // -----------------------------------------------------------------------
    suite("F2 DELETE class mapping")(
      test("C-30 happy path: removes a mapping and it disappears from subClassOf") {
        for {
          _ <- TestApiClient
                 .putJson[ClassMappingResponse, AddClassMappingsRequest](
                   putClassMappingUri(anythingOntIri, thingClassIri),
                   AddClassMappingsRequest(List(extSchemaOrg)),
                   anythingAdminUser,
                 )
                 .flatMap(_.assert200)
          deleteResp <- TestApiClient.deleteJson[ClassMappingResponse](
                          deleteClassMappingUri(anythingOntIri, thingClassIri, extSchemaOrg),
                          anythingAdminUser,
                        )
          result <- deleteResp.assert200
        } yield assertTrue(!result.subClassOf.contains(extSchemaOrg))
      },
      test("C-31 idempotent: deleting an absent mapping returns 200") {
        TestApiClient
          .deleteJson[ClassMappingResponse](
            deleteClassMappingUri(anythingOntIri, thingClassIri, extSchemaOrg),
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .as(assertCompletes)
      },
      test("C-32 missing 'mapping' query param returns 400") {
        TestApiClient
          .deleteJson[ClassMappingResponse](
            uri"/v3/ontologies/$anythingOntIri/classes/$thingClassIri/mapping",
            anythingAdminUser,
          )
          .flatMap(_.assert400)
          .map(body => assertTrue(body.contains("mapping")))
      },
      test("C-33 Knora IRI as mapping param returns 400") {
        TestApiClient
          .deleteJson[ClassMappingResponse](
            deleteClassMappingUri(anythingOntIri, thingClassIri, "http://www.knora.org/ontology/0001/anything#Thing"),
            anythingAdminUser,
          )
          .flatMap(_.assert400)
          .map(body => assertTrue(body.contains("external IRI")))
      },
      test("C-34 ontology not found returns 404") {
        TestApiClient
          .deleteJson[ClassMappingResponse](
            deleteClassMappingUri(unknownOntIri, s"$unknownOntIri#Thing", extSchemaOrg),
            anythingAdminUser,
          )
          .flatMap(_.assert404)
          .map(body => assertTrue(body.nonEmpty))
      },
      test("C-35 class not found in ontology returns 404") {
        TestApiClient
          .deleteJson[ClassMappingResponse](
            deleteClassMappingUri(anythingOntIri, unknownClassIri, extSchemaOrg),
            anythingAdminUser,
          )
          .flatMap(_.assert404)
          .map(body => assertTrue(body.nonEmpty))
      },
      test("C-36 user without project admin rights returns 403") {
        TestApiClient
          .deleteJson[ClassMappingResponse](
            deleteClassMappingUri(anythingOntIri, thingClassIri, extSchemaOrg),
            normalUser,
          )
          .map(r => assertTrue(r.code == StatusCode.Forbidden))
      },
    ),

    // -----------------------------------------------------------------------
    // F3 — PUT property mapping
    // -----------------------------------------------------------------------
    suite("F3 PUT property mapping")(
      test("P-1 happy path: adds a mapping and returns updated subPropertyOf") {
        for {
          response <- TestApiClient.putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
                        putPropertyMappingUri(anythingOntIri, hasIntegerPropIri),
                        AddPropertyMappingsRequest(List(extSchemaOrgName)),
                        anythingAdminUser,
                      )
          result <- response.assert200
          // Clean up
          _ <- TestApiClient.deleteJson[PropertyMappingResponse](
                 deletePropertyMappingUri(anythingOntIri, hasIntegerPropIri, extSchemaOrgName),
                 anythingAdminUser,
               )
        } yield assertTrue(
          result.subPropertyOf.contains(extSchemaOrgName),
          result.propertyIri.nonEmpty,
          result.ontologyIri.nonEmpty,
        )
      },
      test("P-2 empty mappings list returns 400") {
        TestApiClient
          .putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
            putPropertyMappingUri(anythingOntIri, hasIntegerPropIri),
            AddPropertyMappingsRequest(List.empty),
            anythingAdminUser,
          )
          .flatMap(_.assert400)
          .map(body => assertTrue(body.contains("at least one")))
      },
      test("P-3 Knora IRI in mappings returns 400") {
        TestApiClient
          .putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
            putPropertyMappingUri(anythingOntIri, hasIntegerPropIri),
            AddPropertyMappingsRequest(List("http://www.knora.org/ontology/0001/anything#hasInteger")),
            anythingAdminUser,
          )
          .flatMap(_.assert400)
          .map(body => assertTrue(body.contains("external IRI")))
      },
      test("P-4 ontology not found returns 404") {
        TestApiClient
          .putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
            putPropertyMappingUri(unknownOntIri, s"$unknownOntIri#hasInteger"),
            AddPropertyMappingsRequest(List(extSchemaOrgName)),
            anythingAdminUser,
          )
          .flatMap(_.assert404)
          .map(body => assertTrue(body.nonEmpty))
      },
      test("P-5 property not found returns 404") {
        TestApiClient
          .putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
            putPropertyMappingUri(anythingOntIri, unknownPropIri),
            AddPropertyMappingsRequest(List(extSchemaOrgName)),
            anythingAdminUser,
          )
          .flatMap(_.assert404)
          .map(body => assertTrue(body.nonEmpty))
      },
      test("P-6 user without project admin rights returns 403") {
        TestApiClient
          .putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
            putPropertyMappingUri(anythingOntIri, hasIntegerPropIri),
            AddPropertyMappingsRequest(List(extSchemaOrgName)),
            normalUser,
          )
          .map(r => assertTrue(r.code == StatusCode.Forbidden))
      },
      test("P-7 PUT property mapping is idempotent") {
        for {
          r1     <- TestApiClient.putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
                      putPropertyMappingUri(anythingOntIri, hasIntegerPropIri),
                      AddPropertyMappingsRequest(List(extSchemaOrgName)),
                      anythingAdminUser,
                    )
          result1 <- r1.assert200
          r2      <- TestApiClient.putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
                       putPropertyMappingUri(anythingOntIri, hasIntegerPropIri),
                       AddPropertyMappingsRequest(List(extSchemaOrgName)),
                       anythingAdminUser,
                     )
          result2 <- r2.assert200
          // Clean up
          _       <- TestApiClient.deleteJson[PropertyMappingResponse](
                       deletePropertyMappingUri(anythingOntIri, hasIntegerPropIri, extSchemaOrgName),
                       anythingAdminUser,
                     )
        } yield assertTrue(
          r1.code == StatusCode.Ok,
          r2.code == StatusCode.Ok,
          // State is identical after two calls — no duplicate accumulation
          result1.subPropertyOf == result2.subPropertyOf,
        )
      },
    ),

    // -----------------------------------------------------------------------
    // F4 — DELETE property mapping
    // -----------------------------------------------------------------------
    suite("F4 DELETE property mapping")(
      test("P-20 happy path: removes a mapping and it disappears from subPropertyOf") {
        for {
          _ <- TestApiClient
                 .putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
                   putPropertyMappingUri(anythingOntIri, hasIntegerPropIri),
                   AddPropertyMappingsRequest(List(extSchemaOrgName)),
                   anythingAdminUser,
                 )
                 .flatMap(_.assert200)
          deleteResp <- TestApiClient.deleteJson[PropertyMappingResponse](
                          deletePropertyMappingUri(anythingOntIri, hasIntegerPropIri, extSchemaOrgName),
                          anythingAdminUser,
                        )
          result <- deleteResp.assert200
        } yield assertTrue(!result.subPropertyOf.contains(extSchemaOrgName))
      },
      test("P-21 idempotent: deleting an absent mapping returns 200") {
        TestApiClient
          .deleteJson[PropertyMappingResponse](
            deletePropertyMappingUri(anythingOntIri, hasIntegerPropIri, extSchemaOrgName),
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .as(assertCompletes)
      },
      test("P-22 missing 'mapping' query param returns 400") {
        TestApiClient
          .deleteJson[PropertyMappingResponse](
            uri"/v3/ontologies/$anythingOntIri/properties/$hasIntegerPropIri/mapping",
            anythingAdminUser,
          )
          .flatMap(_.assert400)
          .map(body => assertTrue(body.contains("mapping")))
      },
      test("P-23 Knora IRI as mapping param returns 400") {
        TestApiClient
          .deleteJson[PropertyMappingResponse](
            deletePropertyMappingUri(
              anythingOntIri,
              hasIntegerPropIri,
              "http://www.knora.org/ontology/0001/anything#hasInteger",
            ),
            anythingAdminUser,
          )
          .flatMap(_.assert400)
          .map(body => assertTrue(body.contains("external IRI")))
      },
      test("P-24 ontology not found returns 404") {
        TestApiClient
          .deleteJson[PropertyMappingResponse](
            deletePropertyMappingUri(unknownOntIri, s"$unknownOntIri#hasInteger", extSchemaOrgName),
            anythingAdminUser,
          )
          .flatMap(_.assert404)
          .map(body => assertTrue(body.nonEmpty))
      },
      test("P-25 property not found returns 404") {
        TestApiClient
          .deleteJson[PropertyMappingResponse](
            deletePropertyMappingUri(anythingOntIri, unknownPropIri, extSchemaOrgName),
            anythingAdminUser,
          )
          .flatMap(_.assert404)
          .map(body => assertTrue(body.nonEmpty))
      },
      test("P-26 user without project admin rights returns 403") {
        TestApiClient
          .deleteJson[PropertyMappingResponse](
            deletePropertyMappingUri(anythingOntIri, hasIntegerPropIri, extSchemaOrgName),
            normalUser,
          )
          .map(r => assertTrue(r.code == StatusCode.Forbidden))
      },
    ),

    // -----------------------------------------------------------------------
    // Authentication — unauthenticated requests must return 401 for all endpoints.
    // Using ZIO.succeed(List(...).map(...)) to keep these DRY per the pattern in
    // AuthenticationEndpointsV2E2ESpec. Test IRIs are non-alphabetical to avoid
    // false positives from accidental ordering matches.
    // -----------------------------------------------------------------------
    suite("all mutation endpoints require authentication")(
      ZIO.succeed(
        List(
          "PUT class mapping"       -> TestApiClient.putJson[ClassMappingResponse, AddClassMappingsRequest](
                                         putClassMappingUri(anythingOntIri, thingClassIri),
                                         AddClassMappingsRequest(List(extSchemaOrg)),
                                       ),
          "DELETE class mapping"    -> TestApiClient.deleteJson[ClassMappingResponse](
                                         deleteClassMappingUri(anythingOntIri, thingClassIri, extSchemaOrg),
                                         identity,
                                       ),
          "PUT property mapping"    -> TestApiClient.putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
                                         putPropertyMappingUri(anythingOntIri, hasIntegerPropIri),
                                         AddPropertyMappingsRequest(List(extSchemaOrgName)),
                                       ),
          "DELETE property mapping" -> TestApiClient.deleteJson[PropertyMappingResponse](
                                         deletePropertyMappingUri(anythingOntIri, hasIntegerPropIri, extSchemaOrgName),
                                         identity,
                                       ),
        ).map { case (label, request) =>
          test(s"$label without credentials returns 401") {
            request.map(r => assertTrue(r.code == StatusCode.Unauthorized))
          }
        },
      ),
    ),

    // -----------------------------------------------------------------------
    // Authorization — admin of a different project must be denied (403).
    // incunabulaProjectAdminUser is an admin of incunabula (0803), not anything (0001).
    // -----------------------------------------------------------------------
    suite("authorization — cross-project admin is forbidden")(
      test("PUT class mapping by admin of a different project returns 403") {
        TestApiClient
          .putJson[ClassMappingResponse, AddClassMappingsRequest](
            putClassMappingUri(anythingOntIri, thingClassIri),
            AddClassMappingsRequest(List(extSchemaOrg)),
            incunabulaProjectAdminUser,
          )
          .map(r => assertTrue(r.code == StatusCode.Forbidden))
      },
      test("DELETE class mapping by admin of a different project returns 403") {
        TestApiClient
          .deleteJson[ClassMappingResponse](
            deleteClassMappingUri(anythingOntIri, thingClassIri, extSchemaOrg),
            incunabulaProjectAdminUser,
          )
          .map(r => assertTrue(r.code == StatusCode.Forbidden))
      },
      test("PUT property mapping by admin of a different project returns 403") {
        TestApiClient
          .putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
            putPropertyMappingUri(anythingOntIri, hasIntegerPropIri),
            AddPropertyMappingsRequest(List(extSchemaOrgName)),
            incunabulaProjectAdminUser,
          )
          .map(r => assertTrue(r.code == StatusCode.Forbidden))
      },
      test("DELETE property mapping by admin of a different project returns 403") {
        TestApiClient
          .deleteJson[PropertyMappingResponse](
            deletePropertyMappingUri(anythingOntIri, hasIntegerPropIri, extSchemaOrgName),
            incunabulaProjectAdminUser,
          )
          .map(r => assertTrue(r.code == StatusCode.Forbidden))
      },
    ),

    // -----------------------------------------------------------------------
    // Roundtrip visibility — v3 PUT must be visible via v2 GET allentities.
    // Verifies cache invalidation and cross-version integration.
    // -----------------------------------------------------------------------
    suite("roundtrip visibility in v2 allentities")(
      test("C-roundtrip: PUT class mapping appears in GET /v2/ontologies/allentities") {
        for {
          _           <- TestApiClient
                           .putJson[ClassMappingResponse, AddClassMappingsRequest](
                             putClassMappingUri(anythingOntIri, thingClassIri),
                             AddClassMappingsRequest(List(extSchemaOrg)),
                             anythingAdminUser,
                           )
                           .flatMap(_.assert200)
          allEntities <- TestApiClient
                           .getJsonLd(uri"/v2/ontologies/allentities/$anythingOntIri", anythingAdminUser)
                           .flatMap(_.assert200)
          // Clean up
          _           <- TestApiClient.deleteJson[ClassMappingResponse](
                           deleteClassMappingUri(anythingOntIri, thingClassIri, extSchemaOrg),
                           anythingAdminUser,
                         )
        } yield assertTrue(allEntities.contains(extSchemaOrg))
      },
      test("C-roundtrip: DELETE class mapping is absent from GET /v2/ontologies/allentities") {
        for {
          _           <- TestApiClient
                           .putJson[ClassMappingResponse, AddClassMappingsRequest](
                             putClassMappingUri(anythingOntIri, thingClassIri),
                             AddClassMappingsRequest(List(extSchemaOrg)),
                             anythingAdminUser,
                           )
                           .flatMap(_.assert200)
          _           <- TestApiClient
                           .deleteJson[ClassMappingResponse](
                             deleteClassMappingUri(anythingOntIri, thingClassIri, extSchemaOrg),
                             anythingAdminUser,
                           )
                           .flatMap(_.assert200)
          allEntities <- TestApiClient
                           .getJsonLd(uri"/v2/ontologies/allentities/$anythingOntIri", anythingAdminUser)
                           .flatMap(_.assert200)
        } yield assertTrue(!allEntities.contains(extSchemaOrg))
      },
      test("P-roundtrip: PUT property mapping appears in GET /v2/ontologies/allentities") {
        for {
          _           <- TestApiClient
                           .putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
                             putPropertyMappingUri(anythingOntIri, hasIntegerPropIri),
                             AddPropertyMappingsRequest(List(extSchemaOrgName)),
                             anythingAdminUser,
                           )
                           .flatMap(_.assert200)
          allEntities <- TestApiClient
                           .getJsonLd(uri"/v2/ontologies/allentities/$anythingOntIri", anythingAdminUser)
                           .flatMap(_.assert200)
          // Clean up
          _           <- TestApiClient.deleteJson[PropertyMappingResponse](
                           deletePropertyMappingUri(anythingOntIri, hasIntegerPropIri, extSchemaOrgName),
                           anythingAdminUser,
                         )
        } yield assertTrue(allEntities.contains(extSchemaOrgName))
      },
      test("P-roundtrip: DELETE property mapping is absent from GET /v2/ontologies/allentities") {
        for {
          _           <- TestApiClient
                           .putJson[PropertyMappingResponse, AddPropertyMappingsRequest](
                             putPropertyMappingUri(anythingOntIri, hasIntegerPropIri),
                             AddPropertyMappingsRequest(List(extSchemaOrgName)),
                             anythingAdminUser,
                           )
                           .flatMap(_.assert200)
          _           <- TestApiClient
                           .deleteJson[PropertyMappingResponse](
                             deletePropertyMappingUri(anythingOntIri, hasIntegerPropIri, extSchemaOrgName),
                             anythingAdminUser,
                           )
                           .flatMap(_.assert200)
          allEntities <- TestApiClient
                           .getJsonLd(uri"/v2/ontologies/allentities/$anythingOntIri", anythingAdminUser)
                           .flatMap(_.assert200)
        } yield assertTrue(!allEntities.contains(extSchemaOrgName))
      },
    ),
  )
}
