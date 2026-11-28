/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.access

import org.junit.runner.RunWith
import zio.test.*

import dsp.errors.NotFoundException
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.v2.responder.resourcemessages.ReadResourcesSequenceV2
import org.knora.webapi.slice.common.ResourceIri

@RunWith(classOf[DspZTestJUnitRunner])
class ObjectLoaderSpec extends ZIOSpecDefault {

  val spec: Spec[Any, Any] = suite("ObjectLoader")(
    test("finds a missing resource by its name in the read services' NotFoundException") {
      val missing  = ResourceIri.unsafeFrom("http://rdfh.ch/0001/missing-resource")
      val sequence = ReadResourcesSequenceV2(Seq.empty)
      val names    = sequence.checkResourceIris(Set(missing), sequence) match {
        case Some(e: NotFoundException) => e.getMessage.contains(s"<${missing.value}>")
        case _                          => false
      }
      assertTrue(names)
    },
  )
}
