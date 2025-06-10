/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import zio.ZIO

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.routing.UnsafeZioRun

/**
 * This spec is used to test [[CardinalityHandler]].
 */
class CardinalitiesSpec extends E2ESpec {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val freetestOntologyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri

  "Cardinalities.isPropertyUsedInResources()" should {
    "detect that property is in use, when used in a resource" in {
      val internalPropertyIri = freetestOntologyIri.makeEntityIri("hasText").toInternalIri
      val internalClassIri    = freetestOntologyIri.makeEntityIri("FreeTest").toInternalIri

      val resF =
        UnsafeZioRun.runToFuture(
          ZIO.serviceWithZIO[CardinalityHandler](
            _.isPropertyUsedInResources(internalClassIri, internalPropertyIri),
          ),
        )
      resF map { res =>
        assert(res, "property is used in resource (instance of that resource class)")
      }
    }

    "detect that property is not in use, when not used in a resource" in {
      val internalPropertyIri = freetestOntologyIri.makeEntityIri("hasText").toInternalIri
      val internalClassIri    = freetestOntologyIri.makeEntityIri("FreeTestResourceClass").toInternalIri

      val resF =
        UnsafeZioRun.runToFuture(
          ZIO.serviceWithZIO[CardinalityHandler](
            _.isPropertyUsedInResources(internalClassIri, internalPropertyIri),
          ),
        )
      resF map { res =>
        assert(!res, "property is not used in resource (instance of that resource class)")
      }
    }

    "detect that property is not in use, when not used in a resource of that class (even when used in another class)" in {
      val internalPropertyIri = freetestOntologyIri.makeEntityIri("hasIntegerProperty").toInternalIri
      val internalClassIri    = freetestOntologyIri.makeEntityIri("FreeTest").toInternalIri

      val resF =
        UnsafeZioRun.runToFuture(
          ZIO.serviceWithZIO[CardinalityHandler](
            _.isPropertyUsedInResources(internalClassIri, internalPropertyIri),
          ),
        )
      resF map { res =>
        assert(!res, "property is not used in resource (instance of that resource class)")
      }
    }

    "detect that link property is in use, when used in a resource" in {
      val anythingOntologyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri
      val internalPropertyIri = anythingOntologyIri.makeEntityIri("isPartOfOtherThing").toInternalIri
      val internalClassIri    = anythingOntologyIri.makeEntityIri("Thing").toInternalIri

      val resF =
        UnsafeZioRun.runToFuture(
          ZIO.serviceWithZIO[CardinalityHandler](
            _.isPropertyUsedInResources(internalClassIri, internalPropertyIri),
          ),
        )
      resF map { res =>
        assert(res, "property is used in resource (instance of resource class)")
      }
    }

    "detect that property is in use, when used in a resource of a subclass" in {
      val internalPropertyIri = freetestOntologyIri.makeEntityIri("hasDecimal").toInternalIri
      val internalClassIri    = freetestOntologyIri.makeEntityIri("FreeTest").toInternalIri

      val resF =
        UnsafeZioRun.runToFuture(
          ZIO.serviceWithZIO[CardinalityHandler](_.isPropertyUsedInResources(internalClassIri, internalPropertyIri)),
        )
      resF map { res =>
        assert(res, "property is used in a resource of subclass")
      }
    }
  }
}
