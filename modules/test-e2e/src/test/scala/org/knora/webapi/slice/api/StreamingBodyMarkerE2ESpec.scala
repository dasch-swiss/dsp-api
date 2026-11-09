/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api

import org.junit.runner.RunWith
import sttp.tapir.AnyEndpoint
import sttp.tapir.EndpointIO
import sttp.tapir.internal.RichEndpointInput
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.slice.api.admin.SparqlPassthroughEndpoints
import org.knora.webapi.slice.api.v3.projects.ClosesOnUnreadBody

/**
 * A route whose server logic can reject a streamed request body without reading it must carry [[ClosesOnUnreadBody]]
 * (or the SPARQL passthrough's own marker), or the server cannot close the connection on such a rejection.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class StreamingBodyMarkerE2ESpec extends E2EZSpec {

  import EndpointPolicyE2ESpec.key

  private def streamsRequestBody(ep: AnyEndpoint): Boolean =
    ep.input.asVectorOfBasicInputs().exists(_.isInstanceOf[EndpointIO.StreamBodyWrapper[?, ?]])

  override def e2eSpec = suite("Streaming request body marker")(
    test("every endpoint with a streaming request body carries a marker") {
      ZIO.serviceWith[Endpoints](_.serverEndpoints.map(_.endpoint)).map { all =>
        val streaming = all.filter(streamsRequestBody)
        val unmarked  = streaming
          .filterNot(ep => ClosesOnUnreadBody.isMarked(ep) || SparqlPassthroughEndpoints.isPassthroughRoute(ep))
          .map(key)
        assertTrue(streaming.nonEmpty) &&
        (assertTrue(unmarked.isEmpty) ??
          s"""These endpoints stream their request body but carry no marker. Add
             |.attribute(${ClosesOnUnreadBody.getClass.getSimpleName.stripSuffix("$")}.key, ClosesOnUnreadBodyRoute)
             |(ClosesOnUnreadBody.key) so a rejection closes the connection:
             |${unmarked.sorted.mkString("\n")}""".stripMargin)
      }
    },
  )
}
