/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.junit.runner.RunWith
import zio.*
import zio.test.*
import zio.test.Assertion.failsWithA

import dsp.errors.BadRequestException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.*
import org.knora.webapi.SchemaRendering.apiV2SchemaWithOption
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.ReadValueV2
import org.knora.webapi.messages.v2.responder.valuemessages.StillImageFileValueContentV2
import org.knora.webapi.messages.v2.responder.valuemessages.TextValueContentV2
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri

@RunWith(classOf[DspZTestJUnitRunner])
class SearchResponderV2Spec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
    RdfDataObject(
      path = "test_data/project_ontologies/books-onto.ttl",
      name = "http://www.knora.org/ontology/0001/books",
    ),
    RdfDataObject(path = "test_data/project_data/books-data.ttl", name = "http://www.knora.org/data/0001/books"),
  )

  private val searchResponderV2SpecFullData = new SearchResponderV2SpecFullData
  private val bookClassIri                  =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/0803/incunabula#book".toSmartIri.toComplexSchema)
  private val resourceBaseClassIri =
    ResourceClassIri.unsafeFrom("http://www.knora.org/ontology/knora-base#Resource".toSmartIri.toComplexSchema)
  private val searchResponder = ZIO.serviceWithZIO[SearchResponderV2]

  override val e2eSpec = suite("The search responder v2")(
    test("perform a fulltext search for 'Narr'") {
      for {
        result <- searchResponder(
                    _.fulltextSearchV2(
                      searchValue = "Narr",
                      offset = 0,
                      limitToProject = None,
                      limitToResourceClass = None,
                      limitToStandoffClass = None,
                      returnFiles = false,
                      apiV2SchemaWithOption(MarkupRendering.Xml),
                      requestingUser = anonymousUser,
                    ),
                  )
      } yield assertTrue(result.resources.size == 25)
    },
    test("perform a fulltext search for 'Dinge'") {
      for {
        result <- searchResponder(
                    _.fulltextSearchV2(
                      searchValue = "Dinge",
                      offset = 0,
                      limitToProject = None,
                      limitToResourceClass = None,
                      limitToStandoffClass = None,
                      returnFiles = false,
                      apiV2SchemaWithOption(MarkupRendering.Xml),
                      requestingUser = anythingUser1,
                    ),
                  )
      } yield assertTrue(result.resources.size == 1)
    },
    test("return a Bad Request error if fulltext search input is invalid") {
      for {
        result <- searchResponder(
                    _.fulltextSearchV2(
                      searchValue = "qin(",
                      offset = 0,
                      limitToProject = None,
                      limitToResourceClass = None,
                      limitToStandoffClass = None,
                      returnFiles = false,
                      apiV2SchemaWithOption(MarkupRendering.Xml),
                      requestingUser = anythingUser1,
                    ).exit,
                  )
      } yield assert(result)(failsWithA[BadRequestException])
    },
    test("return files attached to full-text search results") {
      for {
        result <- searchResponder(
                    _.fulltextSearchV2(
                      searchValue = "p7v",
                      offset = 0,
                      limitToProject = None,
                      limitToResourceClass = None,
                      limitToStandoffClass = None,
                      returnFiles = true,
                      apiV2SchemaWithOption(MarkupRendering.Xml),
                      requestingUser = anythingUser1,
                    ),
                  )
        hasImageFileValues: Boolean =
          result.resources.flatMap(_.values.values.flatten).exists { (readValueV2: ReadValueV2) =>
            readValueV2.valueContent match {
              case _: StillImageFileValueContentV2 => true
              case _                               => false
            }
          }
      } yield assertTrue(hasImageFileValues)
    },
    test("perform an extended search for books that have the title 'Zeitglöcklein des Lebens'") {
      for {
        searchResult <- searchResponder(
                          _.gravsearchV2(
                            searchResponderV2SpecFullData.constructQueryForBooksWithTitleZeitgloecklein,
                            apiV2SchemaWithOption(MarkupRendering.Xml),
                            anonymousUser,
                          ),
                        )
        // extended search sort by resource Iri by default if no order criterion is indicated
        _ = compareReadResourcesSequenceV2Response(
              expected = searchResponderV2SpecFullData.booksWithTitleZeitgloeckleinResponse,
              received = searchResult,
            )
      } yield assertCompletes
    },
    test("perform an extended search for books that do not have the title 'Zeitglöcklein des Lebens'") {
      for {
        searchResult <- searchResponder(
                          _.gravsearchV2(
                            searchResponderV2SpecFullData.constructQueryForBooksWithoutTitleZeitgloecklein,
                            apiV2SchemaWithOption(MarkupRendering.Xml),
                            anonymousUser,
                          ),
                        )
        // extended search sort by resource Iri by default if no order criterion is indicated
      } yield assertTrue(searchResult.resources.size == 18)
    },
    test("perform a search by label for incunabula:book that contain 'Narrenschiff'") {
      for {
        result <- searchResponder(
                    _.searchResourcesByLabelV2(
                      searchValue = "Narrenschiff",
                      offset = 0,
                      limitToProject = None,
                      limitToResourceClass = Some(bookClassIri),
                      targetSchema = ApiV2Complex,
                      requestingUser = anonymousUser,
                    ),
                  )
      } yield assertTrue(result.resources.size == 3)
    },
    test("perform a search by label for incunabula:book that contain 'Das Narrenschiff'") {
      for {
        result <- searchResponder(
                    _.searchResourcesByLabelV2(
                      searchValue = "Narrenschiff",
                      offset = 0,
                      limitToProject = None,
                      limitToResourceClass = Some(bookClassIri),
                      targetSchema = ApiV2Complex,
                      requestingUser = anonymousUser,
                    ),
                  )
      } yield assertTrue(result.resources.size == 3)
    },
    test("perform a count search query by label for incunabula:book that contain 'Narrenschiff'") {
      for {
        result <- searchResponder(
                    _.searchResourcesByLabelCountV2(
                      searchValue = "Narrenschiff",
                      limitToProject = None,
                      limitToResourceClass = Some(bookClassIri),
                    ),
                  )
      } yield assertTrue(result.numberOfResources == 3)
    },
    test(
      "perform a a count search query by label for incunabula:book that contain 'Passio sancti Meynrhadi martyris et heremite'",
    ) {
      for {
        result <- searchResponder(
                    _.searchResourcesByLabelCountV2(
                      searchValue = "Passio sancti Meynrhadi martyris et heremite",
                      limitToProject = None,
                      limitToResourceClass = Some(bookClassIri),
                    ),
                  )
      } yield assertTrue(result.numberOfResources == 1)
    },

    // DEV-6833. The class restriction must walk the whole subclass closure, not just one hop. The subclass closure is not
    // materialised and there is no query-time inference, so the previous `rdfs:subClassOf?` matched only the target class
    // and its direct subclasses. anything:BlueThing sits two hops below knora-base:Resource
    // (BlueThing -> Thing -> Resource), so these searches returned nothing at all. Every pre-existing label test above
    // restricts to incunabula:book, a *direct* subclass of knora-base:Resource, which is why none of them caught it.
    // "blue" occurs in exactly one label in anything-data, so the expectation is exact rather than a count.
    test("perform a search by label restricted to a class two hops above the matched resource's class") {
      for {
        result <- searchResponder(
                    _.searchResourcesByLabelV2(
                      searchValue = "blue",
                      offset = 0,
                      limitToProject = None,
                      limitToResourceClass = Some(resourceBaseClassIri),
                      targetSchema = ApiV2Complex,
                      requestingUser = anythingUser1,
                    ),
                  )
      } yield assertTrue(result.resources.map(_.resourceIri.value) == Seq("http://rdfh.ch/0001/a-blue-thing"))
    },
    test("perform a count search by label restricted to a class two hops above the matched resource's class") {
      for {
        result <- searchResponder(
                    _.searchResourcesByLabelCountV2(
                      searchValue = "blue",
                      limitToProject = None,
                      limitToResourceClass = Some(resourceBaseClassIri),
                    ),
                  )
      } yield assertTrue(result.numberOfResources == 1)
    },

    // DEV-6833. Standoff is excluded from the label-search payload by filtering the predicate
    // (?valueObjectProperty != knora-base:valueHasStandoff). The resource below carries two standoff-bearing text values,
    // so it exercises that filter. The assertion pins what the client actually receives: the text value is still returned
    // with its string intact, and — because search-by-label never queries standoff — without markup.
    test("perform a search by label for a resource whose text values carry standoff") {
      for {
        result <- searchResponder(
                    _.searchResourcesByLabelV2(
                      searchValue = "jemanden",
                      offset = 0,
                      limitToProject = None,
                      limitToResourceClass = None,
                      targetSchema = ApiV2Complex,
                      requestingUser = anythingUser1,
                    ),
                  )
        resource   = result.resources.head
        textValues = resource.values.values.flatten.collect { case v: ReadValueV2 =>
                       v.valueContent
                     }.collect { case tv: TextValueContentV2 => tv }.toSeq
      } yield assertTrue(
        result.resources.map(_.resourceIri.value) == Seq("http://rdfh.ch/0001/a-thing-with-text-values"),
        textValues.nonEmpty,
        textValues.forall(_.valueHasString.nonEmpty),
        textValues.forall(_.standoff.isEmpty),
      )
    },
    test("search by project and resource class") {
      for {
        result <- searchResponder(
                    _.searchResourcesByProjectAndClassV2(
                      projectIri = incunabulaProject.id,
                      resourceClass = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                      orderByProperty = Some("http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri),
                      page = 0,
                      schemaAndOptions = SchemaRendering.apiV2SchemaWithOption(MarkupRendering.Xml),
                      requestingUser = incunabulaProjectAdminUser,
                    ),
                  )
      } yield assertTrue(result.resources.size == 19)
    },
    test("search by project and resource class") {
      for {
        result <- searchResponder(
                    _.fulltextSearchV2(
                      searchValue = "non fiction",
                      offset = 0,
                      limitToProject = None,
                      limitToResourceClass = None,
                      limitToStandoffClass = None,
                      returnFiles = false,
                      apiV2SchemaWithOption(MarkupRendering.Xml),
                      requestingUser = anythingUser1,
                    ),
                  )
        _ = compareReadResourcesSequenceV2Response(
              expected = searchResponderV2SpecFullData.expectedResultFulltextSearchForListNodeLabel,
              received = result,
            )
      } yield assertCompletes
    },
    test("search for list label and find sub-nodes") {
      for {
        result <- searchResponder(
                    _.fulltextSearchV2(
                      searchValue = "novel",
                      offset = 0,
                      limitToProject = None,
                      limitToResourceClass = None,
                      limitToStandoffClass = None,
                      returnFiles = false,
                      apiV2SchemaWithOption(MarkupRendering.Xml),
                      requestingUser = anythingUser1,
                    ),
                  )
        _ = compareReadResourcesSequenceV2Response(
              expected = searchResponderV2SpecFullData.expectedResultFulltextSearchForListNodeLabelWithSubnodes,
              received = result,
            )
      } yield assertCompletes
    },
    test("perform an extended search for a particular compound object (book)") {
      for {
        searchResult <- searchResponder(
                          _.gravsearchV2(
                            searchResponderV2SpecFullData.constructQueryForIncunabulaCompundObject,
                            apiV2SchemaWithOption(MarkupRendering.Xml),
                            anonymousUser,
                          ),
                        )
      } yield assertTrue(searchResult.resources.length == 25)
    },
    test("perform an extended search ordered asc by label") {
      val queryAsc = searchResponderV2SpecFullData.constructQuerySortByLabel
      for {
        ascResult <-
          searchResponder(_.gravsearchV2(queryAsc, apiV2SchemaWithOption(MarkupRendering.Xml), anonymousUser))
      } yield assertTrue(ascResult.resources.head.label == "A blue thing")
    },
    test("perform an extended search ordered desc by label") {
      val queryDesc = searchResponderV2SpecFullData.constructQuerySortByLabelDesc
      for {
        descResult <-
          searchResponder(_.gravsearchV2(queryDesc, apiV2SchemaWithOption(MarkupRendering.Xml), anonymousUser))
      } yield assertTrue(descResult.resources.head.label == "visible thing with hidden int values")
    },
  )
}
