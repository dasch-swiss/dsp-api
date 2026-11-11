/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import org.junit.runner.RunWith
import zio.test.*

import java.time.Instant

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.slice.api.v2.ServingProbe
import org.knora.webapi.slice.api.v2.ServingProbe.options
import org.knora.webapi.slice.api.v2.ServingProbe.servingTests
import org.knora.webapi.slice.api.v2.StoredXmlServing
import org.knora.webapi.slice.api.v2.ValueUuid
import org.knora.webapi.slice.api.v2.VersionDate

@RunWith(classOf[DspZTestJUnitRunner])
class ValuesRestServiceSpec extends ZIOSpecDefault {

  private given StringFormatter = StringFormatter.getInitializedTestInstance

  private def restService(probe: ServingProbe, serving: StoredXmlServing) =
    ValuesRestService(
      valuesService = null,
      readResources = probe.readResources,
      requestParser = null,
      renderer = probe.renderer,
      knoraProjectService = null,
      resourceUtilV2 = null,
      valueRepo = null,
      storedXmlServing = serving,
    )

  private val user        = KnoraSystemInstances.Users.AnonymousUser
  private val resourceIri = "http://rdfh.ch/0001/a-thing"
  private val valueUuid   = ValueUuid.from("IN4R19yYR0ygi3K2VEHpUQ").getOrElse(throw new AssertionError)
  private val version     = VersionDate.fromInstant(Instant.parse("2024-01-01T00:00:00Z"))

  override def spec: Spec[TestEnvironment, Any] = suite("ValuesRestService stored XML serving")(
    servingTests("getValue")((p, s) => restService(p, s).getValue(user)(resourceIri, valueUuid, None, options)),
    servingTests("getValue of a version")((p, s) =>
      restService(p, s).getValue(user)(resourceIri, valueUuid, Some(version), options),
    ),
  )
}
