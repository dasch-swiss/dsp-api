/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2e.v2.SearchEndpointE2ESpecHelper.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.GoldenTest

object SearchEndpointsPostGravsearchRecursiveBugE2ESpec extends E2EZSpec with GoldenTest {
  override val rewriteAll = false

  override val rdfDataObjects: List[RdfDataObject] =
    List(
      RdfDataObject(
        // manual derivation from incunabula-data.ttl
        path = "test_data/project_data/incunabula-data-SearchEndpointsPostGravsearchRecursiveBug.ttl",
        name = "http://www.knora.org/data/0803/incunabula",
      ),
    )

  override def e2eSpec = suite("SearchEndpoints POST /v2/searchextended")(
    test("do a Gravsearch query that nests resources infinitively recursively") {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |CONSTRUCT {
          |    ?region knora-api:isMainResource true .
          |    ?region knora-api:isRegionOf ?page .
          |    ?page knora-api:isPartOf ?book .
          |    ?book incunabula:title ?title .
          |} WHERE {
          |    ?region a knora-api:Resource .
          |    ?region a knora-api:Region .
          |    ?region knora-api:isRegionOf ?page .
          |
          |    ?page a knora-api:Resource .
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |    ?book a knora-api:Resource .
          |    ?book a incunabula:book .
          |    ?book incunabula:title ?title .
          |
          |    incunabula:title knora-api:objectType xsd:string .
          |    ?title a xsd:string .
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |}
          |""".stripMargin
      verifyQueryResultOrWrite(query, "SearchEndpointsPostGravsearchRecursiveBugE2ESpec.jsonld", incunabulaMemberUser)
    },
  )
}

/*
ConstructResponseUtilV2: 1 http://rdfh.ch/0803/ff17e5ef9601 -> http://rdfh.ch/0803/3f89a693a501/values/133ea4ce-b9a5-4c66-a3ab-38be07ccd201
ConstructResponseUtilV2: 1 http://rdfh.ch/0803/ff17e5ef9601 -> http://rdfh.ch/0803/6240ce899801/values/e29da7bf-bfa5-4842-a4fc-e455c72dbb0a
ConstructResponseUtilV2: 1 http://rdfh.ch/0803/ff17e5ef9601 -> http://rdfh.ch/0803/c568b7239a01/values/46951e64-8f06-47fb-a07a-c8b19c146447
ConstructResponseUtilV2: 2 http://rdfh.ch/0803/2e0252679a35/values/498d07ec-dddb-4051-bfd1-62626f282fc5 -> http://rdfh.ch/0803/3f89a693a501
ConstructResponseUtilV2: 2 http://rdfh.ch/0803/3f89a693a501/values/133ea4ce-b9a5-4c66-a3ab-38be07ccd201 -> http://rdfh.ch/0803/ff17e5ef9601
ConstructResponseUtilV2: 2 http://rdfh.ch/0803/4e2223eb204c01/values/97188bdc-292a-48f0-9373-29e67afbbd81 -> http://rdfh.ch/0803/6240ce899801
ConstructResponseUtilV2: 2 http://rdfh.ch/0803/6240ce899801/values/e29da7bf-bfa5-4842-a4fc-e455c72dbb0a -> http://rdfh.ch/0803/ff17e5ef9601
ConstructResponseUtilV2: 2 http://rdfh.ch/0803/c2807e831a4c01/values/39c732db-d191-44b9-b091-d764e6e0eff8 -> http://rdfh.ch/0803/c568b7239a01
ConstructResponseUtilV2: 2 http://rdfh.ch/0803/c4160576204c01/values/8f499fc5-2884-4876-8887-4aa83504d937 -> http://rdfh.ch/0803/6240ce899801
ConstructResponseUtilV2: 2 http://rdfh.ch/0803/c568b7239a01/values/46951e64-8f06-47fb-a07a-c8b19c146447 -> http://rdfh.ch/0803/ff17e5ef9601
*/
