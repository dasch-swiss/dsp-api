/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.search

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import dsp.errors.BadRequestException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.IRI
import org.knora.webapi.SchemaRendering
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.responders.v2.ResourceCountV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v2.ProbeSearchResponder
import org.knora.webapi.slice.api.v2.ServingProbe
import org.knora.webapi.slice.api.v2.ServingProbe.options
import org.knora.webapi.slice.api.v2.ServingProbe.servingTests
import org.knora.webapi.slice.api.v2.StoredXmlServing
import org.knora.webapi.slice.api.v2.search.SearchEndpointsInputs.InputIri
import org.knora.webapi.slice.api.v2.search.SearchEndpointsInputs.Offset
import org.knora.webapi.slice.search.SearchTimeoutException
import org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException

@RunWith(classOf[DspZTestJUnitRunner])
class SearchRestServiceSpec extends ZIOSpecDefault {

  // Both Gravsearch entry points fail with `error`; the renderer and IRI converter are never reached.
  private def failingRestService(error: Throwable) = {
    val probe     = ServingProbe()
    val responder = new ProbeSearchResponder(probe) {
      override def gravsearchV2(
        query: IRI,
        rendering: SchemaRendering,
        user: User,
        limitToProject: Option[ProjectIri],
      ): Task[ReadResourcesSequenceV2] = ZIO.fail(error)
      override def gravsearchCountV2(
        query: IRI,
        user: User,
        limitToProject: Option[ProjectIri],
      ): Task[ResourceCountV2] = ZIO.fail(error)
    }
    SearchRestService(responder, null, null, StoredXmlServing.forTest(false))
  }

  private def restService(probe: ServingProbe, serving: StoredXmlServing) =
    SearchRestService(probe.searchResponder, probe.renderer, null, serving)

  private val user     = KnoraSystemInstances.Users.AnonymousUser
  private val query    = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }"
  private val opts     = options
  private val resource = InputIri.unsafeFrom("http://rdfh.ch/0001/a-thing")

  private val timeout = TriplestoreTimeoutException("Triplestore timed out.")

  override def spec: Spec[TestEnvironment, Any] = suite("SearchRestService")(
    test("a triplestore timeout from a Gravsearch page query becomes a 503-mapped SearchTimeoutException") {
      for {
        exit <- failingRestService(timeout).gravsearch(user)(query, opts, None).exit
      } yield assertTrue(exit == Exit.fail(SearchTimeoutException(SearchTimeoutException.gravsearchMessage)))
    },
    test("a triplestore timeout from a Gravsearch count query becomes a 503-mapped SearchTimeoutException") {
      for {
        exit <- failingRestService(timeout).gravsearchCount(user)(query, opts, None).exit
      } yield assertTrue(exit == Exit.fail(SearchTimeoutException(SearchTimeoutException.gravsearchMessage)))
    },
    test("any other Gravsearch failure passes through unchanged") {
      val badRequest = BadRequestException("Invalid Lucene query.")
      for {
        page  <- failingRestService(badRequest).gravsearch(user)(query, opts, None).exit
        count <- failingRestService(badRequest).gravsearchCount(user)(query, opts, None).exit
      } yield assertTrue(page == Exit.fail(badRequest), count == Exit.fail(badRequest))
    },
    suite("stored XML serving")(
      servingTests("gravsearch")((p, s) => restService(p, s).gravsearch(user)(query, opts, None)),
      servingTests("fullTextSearch")((p, s) =>
        restService(p, s).fullTextSearch(user)("text", opts, Offset.default, None, None, None, false),
      ),
      servingTests("searchIncomingLinks")((p, s) =>
        restService(p, s).searchIncomingLinks(user)(resource, Offset.default, opts, None),
      ),
      servingTests("getSearchStillImageRepresentations")((p, s) =>
        restService(p, s).getSearchStillImageRepresentations(user)(resource, Offset.default, opts, None),
      ),
      servingTests("searchIncomingRegions")((p, s) =>
        restService(p, s).searchIncomingRegions(user)(resource, Offset.default, opts, None),
      ),
    ),
  )
}
