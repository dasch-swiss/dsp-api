/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.testkit.ImplicitSender

import scala.concurrent.duration._

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.CoreSpec
import org.knora.webapi.SchemaOptions
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.searchmessages._
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageFileValueContentV2
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * Tests [[SearchResponderV2]].
 */
class SearchResponderV2Spec extends CoreSpec with ImplicitSender {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(path = "test_data/ontologies/books-onto.ttl", name = "http://www.knora.org/ontology/0001/books"),
    RdfDataObject(path = "test_data/all_data/books-data.ttl", name = "http://www.knora.org/data/0001/books")
  )
  private val searchResponderV2SpecFullData = new SearchResponderV2SpecFullData
  // The default timeout for receiving reply messages from actors.
  private val timeout = 10.seconds

  "The search responder v2" should {

    "perform a fulltext search for 'Narr'" in {

      appActor ! FulltextSearchRequestV2(
        searchValue = "Narr",
        offset = 0,
        limitToProject = None,
        limitToResourceClass = None,
        limitToStandoffClass = None,
        returnFiles = false,
        targetSchema = ApiV2Complex,
        schemaOptions = SchemaOptions.ForStandoffWithTextValues,
        requestingUser = SharedTestDataADM.anonymousUser
      )

      expectMsgPF(timeout) { case response: ReadResourcesSequenceV2 =>
        assert(response.resources.size == 25)
      }
    }

    "perform a fulltext search for 'Dinge'" in {

      appActor ! FulltextSearchRequestV2(
        searchValue = "Dinge",
        offset = 0,
        limitToProject = None,
        limitToResourceClass = None,
        limitToStandoffClass = None,
        returnFiles = false,
        targetSchema = ApiV2Complex,
        schemaOptions = SchemaOptions.ForStandoffWithTextValues,
        requestingUser = SharedTestDataADM.anythingUser1
      )

      expectMsgPF(timeout) { case response: ReadResourcesSequenceV2 =>
        assert(response.resources.size == 1)
      }

    }

    "return files attached to full-text search results" in {

      appActor ! FulltextSearchRequestV2(
        searchValue = "p7v",
        offset = 0,
        limitToProject = None,
        limitToResourceClass = None,
        limitToStandoffClass = None,
        returnFiles = true,
        targetSchema = ApiV2Complex,
        schemaOptions = SchemaOptions.ForStandoffWithTextValues,
        requestingUser = SharedTestDataADM.anythingUser1
      )

      expectMsgPF(timeout) { case response: ReadResourcesSequenceV2 =>
        val hasImageFileValues: Boolean =
          response.resources.flatMap(_.values.values.flatten).exists { readValueV2: ReadValueV2 =>
            readValueV2.valueContent match {
              case _: StillImageFileValueContentV2 => true
              case _                               => false
            }
          }

        assert(hasImageFileValues)
      }

    }

    "perform an extended search for books that have the title 'Zeitglöcklein des Lebens'" in {

      appActor ! GravsearchRequestV2(
        constructQuery = searchResponderV2SpecFullData.constructQueryForBooksWithTitleZeitgloecklein,
        targetSchema = ApiV2Complex,
        schemaOptions = SchemaOptions.ForStandoffWithTextValues,
        requestingUser = SharedTestDataADM.anonymousUser
      )

      // extended search sort by resource Iri by default if no order criterion is indicated
      expectMsgPF(timeout) { case response: ReadResourcesSequenceV2 =>
        compareReadResourcesSequenceV2Response(
          expected = searchResponderV2SpecFullData.booksWithTitleZeitgloeckleinResponse,
          received = response
        )
      }

    }

    "perform an extended search for books that do not have the title 'Zeitglöcklein des Lebens'" in {

      appActor ! GravsearchRequestV2(
        constructQuery = searchResponderV2SpecFullData.constructQueryForBooksWithoutTitleZeitgloecklein,
        targetSchema = ApiV2Complex,
        schemaOptions = SchemaOptions.ForStandoffWithTextValues,
        requestingUser = SharedTestDataADM.anonymousUser
      )

      // extended search sort by resource Iri by default if no order criterion is indicated
      expectMsgPF(timeout) { case response: ReadResourcesSequenceV2 =>
        // TODO: do better testing once JSON-LD can be converted back into case classes
        assert(response.resources.size == 18)
      }

    }

    "perform a search by label for incunabula:book that contain 'Narrenschiff'" in {

      appActor ! SearchResourceByLabelRequestV2(
        searchValue = "Narrenschiff",
        offset = 0,
        limitToProject = None,
        limitToResourceClass = Some("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri), // internal Iri!
        targetSchema = ApiV2Complex,
        requestingUser = SharedTestDataADM.anonymousUser
      )

      expectMsgPF(timeout) { case response: ReadResourcesSequenceV2 =>
        assert(response.resources.size == 3)
      }

    }

    "perform a search by label for incunabula:book that contain 'Das Narrenschiff'" in {

      appActor ! SearchResourceByLabelRequestV2(
        searchValue = "Narrenschiff",
        offset = 0,
        limitToProject = None,
        limitToResourceClass = Some("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri), // internal Iri!
        targetSchema = ApiV2Complex,
        requestingUser = SharedTestDataADM.anonymousUser
      )

      expectMsgPF(timeout) { case response: ReadResourcesSequenceV2 =>
        assert(response.resources.size == 3)
      }

    }

    "perform a count search query by label for incunabula:book that contain 'Narrenschiff'" in {

      appActor ! SearchResourceByLabelCountRequestV2(
        searchValue = "Narrenschiff",
        limitToProject = None,
        limitToResourceClass = Some("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri), // internal Iri!
        requestingUser = SharedTestDataADM.anonymousUser
      )

      expectMsgPF(timeout) { case response: ResourceCountV2 =>
        assert(response.numberOfResources == 3)
      }

    }

    "perform a a count search query by label for incunabula:book that contain 'Passio sancti Meynrhadi martyris et heremite'" in {

      appActor ! SearchResourceByLabelCountRequestV2(
        searchValue = "Passio sancti Meynrhadi martyris et heremite",
        limitToProject = None,
        limitToResourceClass = Some("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri), // internal Iri!
        requestingUser = SharedTestDataADM.anonymousUser
      )

      expectMsgPF(timeout) { case response: ResourceCountV2 =>
        assert(response.numberOfResources == 1)
      }

    }

    "search by project and resource class" in {
      appActor ! SearchResourcesByProjectAndClassRequestV2(
        projectIri = SharedTestDataADM.incunabulaProject.id.toSmartIri,
        resourceClass = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        orderByProperty = Some("http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri),
        schemaOptions = SchemaOptions.ForStandoffWithTextValues,
        page = 0,
        targetSchema = ApiV2Complex,
        requestingUser = SharedTestDataADM.incunabulaProjectAdminUser
      )

      expectMsgPF(timeout) { case response: ReadResourcesSequenceV2 =>
        response.resources.size should ===(19)
      }
    }

    "search for list label" in {

      appActor ! FulltextSearchRequestV2(
        searchValue = "non fiction",
        offset = 0,
        limitToProject = None,
        limitToResourceClass = None,
        limitToStandoffClass = None,
        returnFiles = false,
        targetSchema = ApiV2Complex,
        schemaOptions = SchemaOptions.ForStandoffWithTextValues,
        requestingUser = SharedTestDataADM.anythingUser1
      )

      expectMsgPF(timeout) { case response: ReadResourcesSequenceV2 =>
        compareReadResourcesSequenceV2Response(
          expected = searchResponderV2SpecFullData.expectedResultFulltextSearchForListNodeLabel,
          received = response
        )
      }
    }

    "search for list label and find sub-nodes" in {

      appActor ! FulltextSearchRequestV2(
        searchValue = "novel",
        offset = 0,
        limitToProject = None,
        limitToResourceClass = None,
        limitToStandoffClass = None,
        returnFiles = false,
        targetSchema = ApiV2Complex,
        schemaOptions = SchemaOptions.ForStandoffWithTextValues,
        requestingUser = SharedTestDataADM.anythingUser1
      )

      expectMsgPF(timeout) { case response: ReadResourcesSequenceV2 =>
        compareReadResourcesSequenceV2Response(
          expected = searchResponderV2SpecFullData.expectedResultFulltextSearchForListNodeLabelWithSubnodes,
          received = response
        )
      }
    }

    "perform an extended search for a particular compound object (book)" in {

      val query = searchResponderV2SpecFullData.constructQueryForIncunabulaCompundObject

      appActor ! GravsearchRequestV2(
        constructQuery = query,
        targetSchema = ApiV2Complex,
        schemaOptions = SchemaOptions.ForStandoffWithTextValues,
        requestingUser = SharedTestDataADM.anonymousUser
      )

      expectMsgPF(timeout) { case response: ReadResourcesSequenceV2 =>
        response.resources.length should equal(25)
      }
    }

  }

}
