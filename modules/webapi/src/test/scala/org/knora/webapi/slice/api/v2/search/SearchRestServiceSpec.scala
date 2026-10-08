/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.search

import org.junit.runner.RunWith
import zio.*
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.test.*

import dsp.errors.BadRequestException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.IRI
import org.knora.webapi.SchemaRendering
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.search.ConstructQuery
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.responders.v2.ResourceCountV2
import org.knora.webapi.responders.v2.SearchResponderV2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.search.SearchTimeoutException
import org.knora.webapi.store.triplestore.errors.TriplestoreTimeoutException

@RunWith(classOf[DspZTestJUnitRunner])
class SearchRestServiceSpec extends ZIOSpecDefault {

  // Fails both Gravsearch entry points with `error`. Nothing else is reached, because a failed search never gets as
  // far as the renderer.
  private def failingResponder(error: Throwable): SearchResponderV2 = new SearchResponderV2 {
    protected def tracing: Tracing = null

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

    private def unused = ZIO.dieMessage("not used by this spec")
    def gravsearchV2(
      query: ConstructQuery,
      schemaAndOptions: SchemaRendering,
      user: User,
      limitToProject: Option[ProjectIri],
    ): Task[ReadResourcesSequenceV2] = unused
    def gravsearchCountV2(
      query: ConstructQuery,
      user: User,
      limitToProject: Option[ProjectIri],
    ): Task[ResourceCountV2] = unused
    def searchIncomingLinksV2(
      resourceIri: ResourceIri,
      offset: Int,
      rendering: SchemaRendering,
      user: User,
      limitToProject: Option[ProjectIri],
    ): Task[ReadResourcesSequenceV2] = unused
    def searchStillImageRepresentationsV2(
      resourceIri: ResourceIri,
      offset: Int,
      rendering: SchemaRendering,
      user: User,
      limitToProject: Option[ProjectIri],
    ): Task[ReadResourcesSequenceV2] = unused
    def searchStillImageRepresentationsCountV2(
      resourceIri: ResourceIri,
      user: User,
      limitToProject: Option[ProjectIri],
    ): Task[ResourceCountV2] = unused
    def searchIncomingRegionsV2(
      resourceIri: ResourceIri,
      offset: Int,
      rendering: SchemaRendering,
      user: User,
      limitToProject: Option[ProjectIri],
    ): Task[ReadResourcesSequenceV2] = unused
    def fulltextSearchCountV2(
      searchValue: IRI,
      limitToProject: Option[ProjectIri],
      limitToResourceClass: Option[ResourceClassIri],
      limitToStandoffClass: Option[SmartIri],
    ): Task[ResourceCountV2] = unused
    def fulltextSearchV2(
      searchValue: IRI,
      offset: Int,
      limitToProject: Option[ProjectIri],
      limitToResourceClass: Option[ResourceClassIri],
      limitToStandoffClass: Option[SmartIri],
      returnFiles: Boolean,
      schemaAndOptions: SchemaRendering,
      requestingUser: User,
    ): Task[ReadResourcesSequenceV2] = unused
    def searchResourcesByLabelCountV2(
      searchValue: String,
      limitToProject: Option[ProjectIri],
      limitToResourceClass: Option[ResourceClassIri],
    ): Task[ResourceCountV2] = unused
    def searchResourcesByLabelV2(
      searchValue: String,
      offset: Int,
      limitToProject: Option[ProjectIri],
      limitToResourceClass: Option[ResourceClassIri],
      targetSchema: ApiV2Schema,
      requestingUser: User,
    ): Task[ReadResourcesSequenceV2] = unused
    def searchResourcesByProjectAndClassV2(
      projectIri: ProjectIri,
      resourceClass: SmartIri,
      orderByProperty: Option[SmartIri],
      page: Int,
      schemaAndOptions: SchemaRendering,
      requestingUser: User,
    ): Task[ReadResourcesSequenceV2] = unused
  }

  // The renderer and IRI converter are never reached on a failed search.
  private def restService(error: Throwable) = SearchRestService(failingResponder(error), null, null)

  private val user  = KnoraSystemInstances.Users.AnonymousUser
  private val query = "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }"
  private val opts  = FormatOptions.default

  private val timeout = TriplestoreTimeoutException("Triplestore timed out.")

  override def spec: Spec[TestEnvironment, Any] = suite("SearchRestService Gravsearch timeout translation")(
    test("a triplestore timeout from a Gravsearch page query becomes a 503-mapped SearchTimeoutException") {
      for {
        exit <- restService(timeout).gravsearch(user)(query, opts, None).exit
      } yield assertTrue(exit == Exit.fail(SearchTimeoutException(SearchTimeoutException.gravsearchMessage)))
    },
    test("a triplestore timeout from a Gravsearch count query becomes a 503-mapped SearchTimeoutException") {
      for {
        exit <- restService(timeout).gravsearchCount(user)(query, opts, None).exit
      } yield assertTrue(exit == Exit.fail(SearchTimeoutException(SearchTimeoutException.gravsearchMessage)))
    },
    test("any other Gravsearch failure passes through unchanged") {
      val badRequest = BadRequestException("Invalid Lucene query.")
      for {
        page  <- restService(badRequest).gravsearch(user)(query, opts, None).exit
        count <- restService(badRequest).gravsearchCount(user)(query, opts, None).exit
      } yield assertTrue(page == Exit.fail(badRequest), count == Exit.fail(badRequest))
    },
  )
}
