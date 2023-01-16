/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.repo

import zio.test.ZIOSpecDefault
import zio.test._

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.slice.resourceinfo.domain.IriConverter

object OntologyCacheFakeSpec extends ZIOSpecDefault {
  val spec: Spec[Any, Throwable] = suite("OntologyCacheFake")(
    suite("with empty cache")(test("should return empty") {
      for {
        actual <- OntologyCache.get
      } yield assertTrue(actual == OntologyCacheFake.emptyData)
    }).provide(OntologyCacheFake.emptyCache),
    suite("with empty cache when setting new data")(test("should return set cache") {
      val somePropertyIri = InternalIri("http://www.knora.org/ontology/knora-base#mappingHasXMLAttribute")
      for {
        someIri <- IriConverter.asInternalSmartIri(somePropertyIri)
        newData  = OntologyCacheFake.emptyData.copy(standoffProperties = Set(someIri))
        _       <- OntologyCacheFake.set(newData)
        actual  <- OntologyCache.get
      } yield assertTrue(actual == newData)
    }).provide(OntologyCacheFake.emptyCache, IriConverter.layer, StringFormatter.test)
  )
}
