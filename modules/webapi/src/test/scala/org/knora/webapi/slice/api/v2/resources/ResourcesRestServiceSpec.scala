/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.resources

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.v2.IriDto
import org.knora.webapi.slice.api.v2.ServingProbe
import org.knora.webapi.slice.api.v2.ServingProbe.options
import org.knora.webapi.slice.api.v2.ServingProbe.servingTests
import org.knora.webapi.slice.api.v2.StoredXmlServing
import org.knora.webapi.slice.common.service.IriConverter

@RunWith(classOf[DspZTestJUnitRunner])
class ResourcesRestServiceSpec extends ZIOSpecDefault {

  private def restService(probe: ServingProbe, serving: StoredXmlServing) =
    ResourcesRestService(
      auth = null,
      resourcesService = null,
      searchService = probe.searchResponder,
      iriConverter = IriConverter(StringFormatter.getInitializedTestInstance),
      requestParser = null,
      renderer = probe.renderer,
      readResources = probe.readResources,
      storedXmlServing = serving,
    )

  private val user          = KnoraSystemInstances.Users.AnonymousUser
  private val resourceIri   = "http://rdfh.ch/0001/a-thing"
  private val resourceClass =
    IriDto.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing")

  override def spec: Spec[TestEnvironment, Any] = suite("ResourcesRestService stored XML serving")(
    servingTests("getResources")((p, s) => restService(p, s).getResources(user)(List(resourceIri), options, None)),
    servingTests("getResourcesBatch")((p, s) =>
      restService(p, s).getResourcesBatch(user)(ResourcesBatchRequest(List(resourceIri)), options, None),
    ),
    servingTests("searchResourcesByProjectAndClass")((p, s) =>
      restService(p, s).searchResourcesByProjectAndClass(user)(
        resourceClass,
        None,
        0,
        ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
        options,
      ),
    ),
  )
}
