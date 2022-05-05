/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import akka.actor.Props
import org.knora.webapi.IntegrationSpec
import org.knora.webapi.InternalSchema
import org.knora.webapi.TestContainerFuseki
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.store.triplestore.http.HttpTriplestoreConnector

/**
 * This spec is used to test [[org.knora.webapi.responders.v2.ontology.Cardinalities]].
 * Adding the [[TestContainerFuseki.PortConfig]] config will start the Fuseki container and make it
 * available to the test.
 */
class DeleteCardinalitiesFromClassSpec extends IntegrationSpec(TestContainerFuseki.PortConfig) {

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val additionalTestData = List(
    RdfDataObject(
      path = "test_data/ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest"
    ),
    RdfDataObject(path = "test_data/all_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest"),
    RdfDataObject(
      path = "test_data/ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything"
    ),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  // start fuseki http connector actor
  private val fusekiActor = system.actorOf(
    Props(new HttpTriplestoreConnector()).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    name = "httpTriplestoreConnector"
  )

  val freetestOntologyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2".toSmartIri

  override def beforeAll(): Unit = {
    waitForReadyTriplestore(fusekiActor)
    loadTestData(fusekiActor, additionalTestData)
  }

  // use started actor in tests instead of the store manager
  "DeleteCardinalitiesFromClass" should {
    "detect that property is in use, when used in a resource" in {
      val internalPropertyIri = freetestOntologyIri.makeEntityIri("hasText").toOntologySchema(InternalSchema)
      val internalClassIri    = freetestOntologyIri.makeEntityIri("FreeTest").toOntologySchema(InternalSchema)
      println(s"internalPropertyIri: $internalPropertyIri")

      val resF = Cardinalities.isPropertyUsedInResources(settings, fusekiActor, internalClassIri, internalPropertyIri)
      resF map { res => println(res); assert(res, "property is used in resource (instance of that resource class)") }
    }

    "detect that property is not in use, when not used in a resource" in {
      val internalPropertyIri = freetestOntologyIri.makeEntityIri("hasText").toOntologySchema(InternalSchema)
      val internalClassIri    = freetestOntologyIri.makeEntityIri("FreeTestResourceClass").toOntologySchema(InternalSchema)
      println(s"internalPropertyIri: $internalPropertyIri")

      val resF = Cardinalities.isPropertyUsedInResources(settings, fusekiActor, internalClassIri, internalPropertyIri)
      resF map { res =>
        println(res); assert(!res, "property is not used in resource (instance of that resource class)")
      }
    }

    "detect that property is not in use, when not used in a resource of that class (even when used in another class)" in {
      val internalPropertyIri = freetestOntologyIri.makeEntityIri("hasIntegerProperty").toOntologySchema(InternalSchema)
      val internalClassIri    = freetestOntologyIri.makeEntityIri("FreeTest").toOntologySchema(InternalSchema)
      println(s"internalPropertyIri: $internalPropertyIri")

      val resF = Cardinalities.isPropertyUsedInResources(settings, fusekiActor, internalClassIri, internalPropertyIri)
      resF map { res =>
        println(res); assert(!res, "property is not used in resource (instance of that resource class)")
      }
    }

    "detect that link property is in use, when used in a resource" in {
      val anythingOntologyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri
      val internalPropertyIri = anythingOntologyIri.makeEntityIri("isPartOfOtherThing").toOntologySchema(InternalSchema)
      val internalClassIri    = anythingOntologyIri.makeEntityIri("Thing").toOntologySchema(InternalSchema)
      println(s"internalPropertyIri: $internalPropertyIri")

      val resF = Cardinalities.isPropertyUsedInResources(settings, fusekiActor, internalClassIri, internalPropertyIri)
      resF map { res => println(res); assert(res, "property is used in resource (instance of resource class)") }
    }

    "detect that property is in use, when used in a resource of a subclass" in {
      val internalPropertyIri = freetestOntologyIri.makeEntityIri("hasDecimal").toOntologySchema(InternalSchema)
      val internalClassIri    = freetestOntologyIri.makeEntityIri("FreeTest").toOntologySchema(InternalSchema)
      println(s"internalPropertyIri: $internalPropertyIri")

      val resF = Cardinalities.isPropertyUsedInResources(settings, fusekiActor, internalClassIri, internalPropertyIri)
      resF map { res => println(res); assert(res, "property is used in a resource of subclass") }
    }

  }
}
