/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import zio.ZIO
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*

object CardinalityHandlerE2ESpec extends E2EZSpec {

  private val cardinalityHandler = ZIO.serviceWithZIO[CardinalityHandler]

  override val rdfDataObjects: List[RdfDataObject] = freetestRdfTestdata ++ anythingRdfTestdata

  override val e2eSpec: Spec[CardinalityHandler, Any] = suite("CardinalityHandler.isPropertyUsedInResources()")(
    test("detect that property is in use, when used in a resource") {
      val propertyIri = freetestOntologyIri.makeProperty("hasText")
      val classIri    = freetestOntologyIri.makeClass("FreeTest")
      cardinalityHandler(_.isPropertyUsedInResources(classIri, propertyIri))
        .map(isInUse => assertTrue(isInUse))
    },
    test("detect that property is not in use, when not used in a resource") {
      val propertyIri = freetestOntologyIri.makeProperty("hasText")
      val classIri    = freetestOntologyIri.makeClass("FreeTestResourceClass")
      cardinalityHandler(_.isPropertyUsedInResources(classIri, propertyIri))
        .map(isInUse => assertTrue(!isInUse))
    },
    test(
      "detect that property is not in use, " +
        "when not used in a resource of that class (even when used in another class)",
    ) {
      val propertyIri = freetestOntologyIri.makeProperty("hasIntegerProperty")
      val classIri    = freetestOntologyIri.makeClass("FreeTest")
      cardinalityHandler(_.isPropertyUsedInResources(classIri, propertyIri))
        .map(isInUse => assertTrue(!isInUse))
    },
    test("detect that link property is in use, when used in a resource") {
      val propertyIri = anythingOntologyIri.makeProperty("isPartOfOtherThing")
      val classIri    = anythingOntologyIri.makeClass("Thing")
      cardinalityHandler(_.isPropertyUsedInResources(classIri, propertyIri))
        .map(isInUse => assertTrue(isInUse))
    },
    test("detect that property is in use, when used in a resource of a subclass") {
      val propertyIri = freetestOntologyIri.makeProperty("hasDecimal")
      val classIri    = freetestOntologyIri.makeClass("FreeTest")
      cardinalityHandler(_.isPropertyUsedInResources(classIri, propertyIri))
        .map(isInUse => assertTrue(isInUse))
    },
  )
}
