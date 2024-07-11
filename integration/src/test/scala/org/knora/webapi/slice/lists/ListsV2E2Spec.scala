package org.knora.webapi.slice.lists
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri

object ListsV2E2Spec extends E2EZSpec {

  private def v2listsListIri(iri: ListIri) = s"/v2/lists/${urlEncode(iri.value)}"
  private def v2nodeListIri(iri: ListIri)  = s"/v2/node/${urlEncode(iri.value)}"

  private val knownRootNode = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
  private val knownSubNode  = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")

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
          response <- sendGetRequest(v2listsListIri(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/unknown")))
        } yield assertTrue(response.status.code == 404)
      },
      test("return 404 for an existing sub node") {
        for {
          response <- sendGetRequest(v2listsListIri(knownSubNode))
        } yield assertTrue(response.status.code == 404)
      },
      test("return a known list") {
        for {
          response <- sendGetRequest(v2listsListIri(knownRootNode))
          bodyStr  <- response.body.asString
        } yield {
          assertTrue(
            response.status.code == 200,
            bodyStr == """{
                         |    "rdfs:label": "a list that is not used",
                         |    "knora-api:attachedToProject": {
                         |        "@id": "http://rdfh.ch/projects/0001"
                         |    },
                         |    "knora-api:hasSubListNode": [
                         |        {
                         |            "rdfs:label": "node 1",
                         |            "knora-api:hasRootNode": {
                         |                "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         |            },
                         |            "knora-api:hasSubListNode": [
                         |                {
                         |                    "rdfs:label": "child of node 1",
                         |                    "knora-api:hasRootNode": {
                         |                        "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         |                    },
                         |                    "knora-api:listNodePosition": 0,
                         |                    "@type": "knora-api:ListNode",
                         |                    "@id": "http://rdfh.ch/lists/0001/notUsedList011"
                         |                },
                         |                {
                         |                    "rdfs:label": "List012",
                         |                    "knora-api:hasRootNode": {
                         |                        "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         |                    },
                         |                    "knora-api:listNodePosition": 1,
                         |                    "@type": "knora-api:ListNode",
                         |                    "@id": "http://rdfh.ch/lists/0001/notUsedList012"
                         |                },
                         |                {
                         |                    "rdfs:label": "List013",
                         |                    "knora-api:hasRootNode": {
                         |                        "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         |                    },
                         |                    "knora-api:listNodePosition": 2,
                         |                    "@type": "knora-api:ListNode",
                         |                    "@id": "http://rdfh.ch/lists/0001/notUsedList013"
                         |                },
                         |                {
                         |                    "rdfs:label": "List014",
                         |                    "knora-api:hasRootNode": {
                         |                        "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         |                    },
                         |                    "knora-api:hasSubListNode": [
                         |                        {
                         |                            "rdfs:label": "first child of node 014",
                         |                            "knora-api:hasRootNode": {
                         |                                "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         |                            },
                         |                            "knora-api:listNodePosition": 0,
                         |                            "@type": "knora-api:ListNode",
                         |                            "@id": "http://rdfh.ch/lists/0001/notUsedList0141"
                         |                        },
                         |                        {
                         |                            "rdfs:label": "second child of node 014",
                         |                            "knora-api:hasRootNode": {
                         |                                "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         |                            },
                         |                            "knora-api:listNodePosition": 0,
                         |                            "@type": "knora-api:ListNode",
                         |                            "@id": "http://rdfh.ch/lists/0001/notUsedList0142"
                         |                        }
                         |                    ],
                         |                    "knora-api:listNodePosition": 3,
                         |                    "@type": "knora-api:ListNode",
                         |                    "@id": "http://rdfh.ch/lists/0001/notUsedList014"
                         |                },
                         |                {
                         |                    "rdfs:label": "List015",
                         |                    "knora-api:hasRootNode": {
                         |                        "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         |                    },
                         |                    "knora-api:listNodePosition": 4,
                         |                    "@type": "knora-api:ListNode",
                         |                    "@id": "http://rdfh.ch/lists/0001/notUsedList015"
                         |                }
                         |            ],
                         |            "knora-api:listNodePosition": 0,
                         |            "@type": "knora-api:ListNode",
                         |            "@id": "http://rdfh.ch/lists/0001/notUsedList01"
                         |        },
                         |        {
                         |            "rdfs:label": "node 2",
                         |            "knora-api:hasRootNode": {
                         |                "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         |            },
                         |            "knora-api:listNodePosition": 1,
                         |            "@type": "knora-api:ListNode",
                         |            "@id": "http://rdfh.ch/lists/0001/notUsedList02"
                         |        },
                         |        {
                         |            "rdfs:label": "node 3",
                         |            "knora-api:hasRootNode": {
                         |                "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         |            },
                         |            "knora-api:hasSubListNode": {
                         |                "rdfs:label": "child of node 3",
                         |                "knora-api:hasRootNode": {
                         |                    "@id": "http://rdfh.ch/lists/0001/notUsedList"
                         |                },
                         |                "knora-api:listNodePosition": 0,
                         |                "@type": "knora-api:ListNode",
                         |                "@id": "http://rdfh.ch/lists/0001/notUsedList031"
                         |            },
                         |            "knora-api:listNodePosition": 2,
                         |            "@type": "knora-api:ListNode",
                         |            "@id": "http://rdfh.ch/lists/0001/notUsedList03"
                         |        }
                         |    ],
                         |    "knora-api:isRootNode": true,
                         |    "rdfs:comment": "a list that is not in used in ontology or data",
                         |    "@type": "knora-api:ListNode",
                         |    "@id": "http://rdfh.ch/lists/0001/notUsedList",
                         |    "@context": {
                         |        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                         |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                         |        "owl": "http://www.w3.org/2002/07/owl#",
                         |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                         |        "xsd": "http://www.w3.org/2001/XMLSchema#"
                         |    }
                         |}""".stripMargin,
          )
        }
      },
    ),
    suite("/v2/lists/:listIri should")(
      test("return 404 for an unknown list") {
        for {
          response <- sendGetRequest(v2nodeListIri(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/unknown")))
        } yield assertTrue(response.status.code == 404)
      },
      test("return 404 for an existing root node") {
        for {
          response <- sendGetRequest(v2nodeListIri(knownRootNode))
        } yield assertTrue(response.status.code == 404)
      },
      test("return 200 for an existing sub node") {
        for {
          response <- sendGetRequest(v2nodeListIri(knownSubNode))
          bodyStr  <- response.body.asString
        } yield assertTrue(
          response.status.code == 200,
          bodyStr == """{
                       |    "rdfs:label": "node 1",
                       |    "knora-api:hasRootNode": {
                       |        "@id": "http://rdfh.ch/lists/0001/notUsedList"
                       |    },
                       |    "knora-api:listNodePosition": 0,
                       |    "@type": "knora-api:ListNode",
                       |    "@id": "http://rdfh.ch/lists/0001/notUsedList01",
                       |    "@context": {
                       |        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                       |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
                       |        "owl": "http://www.w3.org/2002/07/owl#",
                       |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                       |        "xsd": "http://www.w3.org/2001/XMLSchema#"
                       |    }
                       |}""".stripMargin,
        )
      },
    ),
  )

}
