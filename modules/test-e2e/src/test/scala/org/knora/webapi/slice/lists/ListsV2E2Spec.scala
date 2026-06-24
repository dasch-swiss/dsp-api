/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists
import sttp.client4.UriContext
import sttp.model.StatusCode
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDArray
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.messages.util.rdf.JsonLDValue
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestApiClient

object ListsV2E2Spec extends E2EZSpec {

  private def v2listsListIri(iri: ListIri) = uri"/v2/lists/${iri.value}"
  private def v2nodeListIri(iri: ListIri)  = uri"/v2/node/${iri.value}"

  /** Recursively checks whether any nested [[JsonLDObject]] contains `key` as one of its predicates. */
  private def containsKeyAnywhere(value: JsonLDValue, key: String): Boolean = value match {
    case obj: JsonLDObject => obj.value.contains(key) || obj.value.values.exists(containsKeyAnywhere(_, key))
    case arr: JsonLDArray  => arr.value.exists(containsKeyAnywhere(_, key))
    case _                 => false
  }

  /** Reads the `rdfs:label` of the top-level object as a plain [[JsonLDString]] (legacy single-language mode). */
  private def rootLabel(doc: JsonLDDocument): Option[JsonLDString] =
    doc.body.value.get(OntologyConstants.Rdfs.Label).collect { case s: JsonLDString => s }

  private val knownRootNode    = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
  private val knownSubNode     = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
  private val treeListRoot     = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList")
  private val otherTreeListIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/otherTreeList")

  override def rdfDataObjects: List[RdfDataObject] =
    List(
      RdfDataObject(
        path = "test_data/project_data/anything-data.ttl",
        name = "http://www.knora.org/data/0001/anything",
      ),
    )

  override def e2eSpec: Spec[env, Any] = suite("the lists API v2 for")(
    suite("/v2/lists/:listIri should")(
      test("return 404 for an unknown list") {
        for {
          response <- TestApiClient.getJsonLd(v2listsListIri(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/unknown")))
        } yield assertTrue(response.code == StatusCode.NotFound)
      },
      test("return 404 for an existing sub node") {
        for {
          response <- TestApiClient.getJsonLd(v2listsListIri(knownSubNode))
        } yield assertTrue(response.code == StatusCode.NotFound)
      },
      test("return a known list") {
        for {
          bodyStr <- TestApiClient.getJsonLd(v2listsListIri(knownRootNode)).flatMap(_.assert200)
        } yield {
          assertTrue(
            JsonLDUtil.parseJsonLD(bodyStr) == JsonLDUtil.parseJsonLD("""
               {
                 "rdfs:label": "a list that is not used",
                 "knora-api:attachedToProject": {
                   "@id": "http://rdfh.ch/projects/0001"
                 },
                 "knora-api:hasSubListNode": [
                   {
                     "rdfs:label": "node 1",
                     "knora-api:hasRootNode": {
                       "@id": "http://rdfh.ch/lists/0001/notUsedList"
                     },
                     "knora-api:hasSubListNode": [
                       {
                         "rdfs:label": "child of node 1",
                         "knora-api:hasRootNode": {
                           "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         },
                         "knora-api:listNodePosition": 0,
                         "@type": "knora-api:ListNode",
                         "@id": "http://rdfh.ch/lists/0001/notUsedList011"
                       },
                       {
                         "rdfs:label": "List012",
                         "knora-api:hasRootNode": {
                           "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         },
                         "knora-api:listNodePosition": 1,
                         "@type": "knora-api:ListNode",
                         "@id": "http://rdfh.ch/lists/0001/notUsedList012"
                       },
                       {
                         "rdfs:label": "List013",
                         "knora-api:hasRootNode": {
                           "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         },
                         "knora-api:listNodePosition": 2,
                         "@type": "knora-api:ListNode",
                         "@id": "http://rdfh.ch/lists/0001/notUsedList013"
                       },
                       {
                         "rdfs:label": "List014",
                         "knora-api:hasRootNode": {
                           "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         },
                         "knora-api:hasSubListNode": [
                           {
                             "rdfs:label": "first child of node 014",
                             "knora-api:hasRootNode": {
                               "@id": "http://rdfh.ch/lists/0001/notUsedList"
                             },
                             "knora-api:listNodePosition": 0,
                             "@type": "knora-api:ListNode",
                             "@id": "http://rdfh.ch/lists/0001/notUsedList0141"
                           },
                           {
                             "rdfs:label": "second child of node 014",
                             "knora-api:hasRootNode": {
                               "@id": "http://rdfh.ch/lists/0001/notUsedList"
                             },
                             "knora-api:listNodePosition": 0,
                             "@type": "knora-api:ListNode",
                             "@id": "http://rdfh.ch/lists/0001/notUsedList0142"
                           }
                         ],
                         "knora-api:listNodePosition": 3,
                         "@type": "knora-api:ListNode",
                         "@id": "http://rdfh.ch/lists/0001/notUsedList014"
                       },
                       {
                         "rdfs:label": "List015",
                         "knora-api:hasRootNode": {
                           "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         },
                         "knora-api:listNodePosition": 4,
                         "@type": "knora-api:ListNode",
                         "@id": "http://rdfh.ch/lists/0001/notUsedList015"
                       }
                     ],
                     "knora-api:listNodePosition": 0,
                     "@type": "knora-api:ListNode",
                     "@id": "http://rdfh.ch/lists/0001/notUsedList01"
                   },
                   {
                     "rdfs:label": "node 2",
                     "knora-api:hasRootNode": {
                       "@id": "http://rdfh.ch/lists/0001/notUsedList"
                     },
                     "knora-api:listNodePosition": 1,
                     "@type": "knora-api:ListNode",
                     "@id": "http://rdfh.ch/lists/0001/notUsedList02"
                   },
                   {
                     "rdfs:label": "node 3",
                     "knora-api:hasRootNode": {
                       "@id": "http://rdfh.ch/lists/0001/notUsedList"
                     },
                     "knora-api:hasSubListNode": {
                       "rdfs:label": "child of node 3",
                       "knora-api:hasRootNode": {
                         "@id": "http://rdfh.ch/lists/0001/notUsedList"
                       },
                       "knora-api:listNodePosition": 0,
                       "@type": "knora-api:ListNode",
                       "@id": "http://rdfh.ch/lists/0001/notUsedList031"
                     },
                     "knora-api:listNodePosition": 2,
                     "@type": "knora-api:ListNode",
                     "@id": "http://rdfh.ch/lists/0001/notUsedList03"
                   }
                 ],
                 "knora-api:isRootNode": true,
                 "rdfs:comment": "a list that is not in used in ontology or data",
                 "@type": "knora-api:ListNode",
                 "@id": "http://rdfh.ch/lists/0001/notUsedList",
                 "@context": {
                   "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                   "owl": "http://www.w3.org/2002/07/owl#",
                   "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                   "xsd": "http://www.w3.org/2001/XMLSchema#"
                 }
               }"""),
          )
        }
      },
    ),
    suite("/v2/lists/:listIri should")(
      test("return 404 for an unknown list") {
        for {
          response <-
            TestApiClient.getJsonLd(v2nodeListIri(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/unknown")))
        } yield assertTrue(response.code == StatusCode.NotFound)
      },
      test("return 404 for an existing root node") {
        for {
          response <- TestApiClient.getJsonLd(v2nodeListIri(knownRootNode))
        } yield assertTrue(response.code == StatusCode.NotFound)
      },
      test("return 200 for an existing sub node") {
        for {
          bodyStr <- TestApiClient.getJsonLd(v2nodeListIri(knownSubNode)).flatMap(_.assert200)
        } yield assertTrue(
          JsonLDUtil.parseJsonLD(bodyStr) == JsonLDUtil.parseJsonLD("""
               {
                 "rdfs:label": "node 1",
                 "knora-api:hasRootNode": {
                   "@id": "http://rdfh.ch/lists/0001/notUsedList"
                 },
                 "knora-api:listNodePosition": 0,
                 "@type": "knora-api:ListNode",
                 "@id": "http://rdfh.ch/lists/0001/notUsedList01",
                 "@context": {
                   "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                   "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                   "owl": "http://www.w3.org/2002/07/owl#",
                   "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                   "xsd": "http://www.w3.org/2001/XMLSchema#"
                 }
              }"""),
        )
      },
    ),
    suite("when ?allLanguages=true")(
      test(
        "returns the same JSON-LD response regardless of the caller's profile language (REQ-1.5, REQ-2.3)",
      ) {
        // anythingUser1.lang = "de", beolUser.lang = "en", anonymous = no Accept-Language -> JWT-driven user lang.
        // With allLanguages=true, userLang/fallbackLang must NOT affect the emitted label/comment arrays.
        val uri = uri"/v2/lists/${treeListRoot.value}?allLanguages=true"
        for {
          asAnonymous <- TestApiClient.getJsonLd(uri).flatMap(_.assert200)
          asGerman    <- TestApiClient.getJsonLd(uri, SharedTestDataADM.anythingUser1).flatMap(_.assert200)
          asEnglish   <- TestApiClient.getJsonLd(uri, SharedTestDataADM.beolUser).flatMap(_.assert200)
        } yield assertTrue(
          JsonLDUtil.parseJsonLD(asAnonymous) == JsonLDUtil.parseJsonLD(asGerman),
          JsonLDUtil.parseJsonLD(asGerman) == JsonLDUtil.parseJsonLD(asEnglish),
        )
      },
      test("omits rdfs:comment when no comments are present anywhere in the list (REQ-2.4)") {
        // otherTreeList has no rdfs:comment on any node, so allLanguages mode must omit the key entirely (D2 omission).
        for {
          bodyStr <- TestApiClient
                       .getJsonLd(uri"/v2/lists/${otherTreeListIri.value}?allLanguages=true")
                       .flatMap(_.assert200)
          doc = JsonLDUtil.parseJsonLD(bodyStr)
        } yield assertTrue(!containsKeyAnywhere(doc.body, OntologyConstants.Rdfs.Comment))
      },
    ),
    suite("when allLanguages is omitted (default mode)")(
      test("returns the user-profile language's label for the anything treelist root (REQ-2.5)") {
        // anythingUser1 has lang = "de" - must receive the German label.
        for {
          bodyStr <- TestApiClient
                       .getJsonLd(v2listsListIri(treeListRoot), SharedTestDataADM.anythingUser1)
                       .flatMap(_.assert200)
          doc = JsonLDUtil.parseJsonLD(bodyStr)
        } yield assertTrue(rootLabel(doc).contains(JsonLDString("Listenwurzel")))
      },
      test("returns the user-profile language's label for an English-speaking user (REQ-2.5)") {
        // beolUser has lang = "en" - must receive the English label.
        for {
          bodyStr <- TestApiClient
                       .getJsonLd(v2listsListIri(treeListRoot), SharedTestDataADM.beolUser)
                       .flatMap(_.assert200)
          doc = JsonLDUtil.parseJsonLD(bodyStr)
        } yield assertTrue(rootLabel(doc).contains(JsonLDString("Tree list root")))
      },
    ),
  )
}
