/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

import akka.actor.{ActorRef, Props}
import akka.testkit.ImplicitSender
import org.knora.webapi._
import org.knora.webapi.app.ApplicationActor
import org.knora.webapi.exceptions._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.{
  CalendarNameGregorian,
  DatePrecisionYear,
  KnoraSystemInstances,
  PermissionUtilADM
}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages._
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.settings.{KnoraDispatchers, _}
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util._
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.Diff

import scala.concurrent.duration._

object ResourcesResponderV2Spec {
  private val incunabulaUserProfile = SharedTestDataADM.incunabulaProjectAdminUser

  private val anythingUserProfile = SharedTestDataADM.anythingUser2

  private val defaultAnythingResourcePermissions =
    "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"
  private val defaultAnythingValuePermissions = defaultAnythingResourcePermissions
  private val defaultStillImageFileValuePermissions =
    "M knora-admin:Creator,knora-admin:ProjectMember|V knora-admin:KnownUser,knora-admin:UnknownUser"

  private val zeitglöckleinIri = "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw"

  private val aThingIri = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA"
  private var aThingLastModificationDate = Instant.now

  private val resourceIriToErase = new MutableTestIri
  private val firstValueIriToErase = new MutableTestIri
  private val secondValueIriToErase = new MutableTestIri
  private val standoffTagIrisToErase = collection.mutable.Set.empty[IRI]
  private var resourceToEraseLastModificationDate = Instant.now
}

class GraphTestData {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val graphForAnythingUser1: GraphDataGetResponseV2 = GraphDataGetResponseV2(
    edges = Vector(
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/tPfZeNMvRVujCQqbIbvO0A",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/nResNuvARcWYUdWyo0GWGw"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/cmfk1DMHRBiR4-_6HXpEFA",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/5IEswyQFQp2bxXDrOyEfEA"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/7uuGcnFcQJq08dMOralyCQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/sHCLAGg-R5qJ6oPZPV-zOQ"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/nResNuvARcWYUdWyo0GWGw",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/MiBwAFcxQZGHNL-WfgFAPQ"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/0C-0L1kORryKzJAJxxRyRQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/iqW_PBiHRdyTFzik8tuSog",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/L5xU7Qe5QUu6Wz3cDaCxbA"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/Lz7WEqJETJqqsUZQYexBQg"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/sHCLAGg-R5qJ6oPZPV-zOQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/Lz7WEqJETJqqsUZQYexBQg"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/WLSHxQUgTOmG1T0lBU2r5w",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/A67ka6UQRHWf313tbhQBjw",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/WLSHxQUgTOmG1T0lBU2r5w"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/MiBwAFcxQZGHNL-WfgFAPQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/LOV-6aLYQFW15jwdyS51Yw"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/iqW_PBiHRdyTFzik8tuSog"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/Lz7WEqJETJqqsUZQYexBQg",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/tPfZeNMvRVujCQqbIbvO0A"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/tPfZeNMvRVujCQqbIbvO0A",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/L5xU7Qe5QUu6Wz3cDaCxbA",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/cmfk1DMHRBiR4-_6HXpEFA"
      )
    ),
    nodes = Vector(
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Tango",
        resourceIri = "http://rdfh.ch/resources/WLSHxQUgTOmG1T0lBU2r5w"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Foxtrot",
        resourceIri = "http://rdfh.ch/resources/Lz7WEqJETJqqsUZQYexBQg"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Echo",
        resourceIri = "http://rdfh.ch/resources/tPfZeNMvRVujCQqbIbvO0A"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Golf",
        resourceIri = "http://rdfh.ch/resources/sHCLAGg-R5qJ6oPZPV-zOQ"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Whiskey",
        resourceIri = "http://rdfh.ch/resources/MiBwAFcxQZGHNL-WfgFAPQ"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Mike",
        resourceIri = "http://rdfh.ch/resources/cmfk1DMHRBiR4-_6HXpEFA"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "X-ray",
        resourceIri = "http://rdfh.ch/resources/nResNuvARcWYUdWyo0GWGw"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Uniform",
        resourceIri = "http://rdfh.ch/resources/LOV-6aLYQFW15jwdyS51Yw"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Sierra",
        resourceIri = "http://rdfh.ch/resources/0C-0L1kORryKzJAJxxRyRQ"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Romeo",
        resourceIri = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Quebec",
        resourceIri = "http://rdfh.ch/resources/iqW_PBiHRdyTFzik8tuSog"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Hotel",
        resourceIri = "http://rdfh.ch/resources/7uuGcnFcQJq08dMOralyCQ"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Papa",
        resourceIri = "http://rdfh.ch/resources/L5xU7Qe5QUu6Wz3cDaCxbA"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Victor",
        resourceIri = "http://rdfh.ch/resources/A67ka6UQRHWf313tbhQBjw"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Delta",
        resourceIri = "http://rdfh.ch/resources/5IEswyQFQp2bxXDrOyEfEA"
      )
    ),
    ontologySchema = InternalSchema
  )

  val graphForIncunabulaUser: GraphDataGetResponseV2 = GraphDataGetResponseV2(
    edges = Vector(
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/tPfZeNMvRVujCQqbIbvO0A",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/nResNuvARcWYUdWyo0GWGw"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/nResNuvARcWYUdWyo0GWGw",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/MiBwAFcxQZGHNL-WfgFAPQ"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/0C-0L1kORryKzJAJxxRyRQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/iqW_PBiHRdyTFzik8tuSog",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/L5xU7Qe5QUu6Wz3cDaCxbA"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/Lz7WEqJETJqqsUZQYexBQg"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/sHCLAGg-R5qJ6oPZPV-zOQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/Lz7WEqJETJqqsUZQYexBQg"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/WLSHxQUgTOmG1T0lBU2r5w",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/A67ka6UQRHWf313tbhQBjw",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/WLSHxQUgTOmG1T0lBU2r5w"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/MiBwAFcxQZGHNL-WfgFAPQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/LOV-6aLYQFW15jwdyS51Yw"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/iqW_PBiHRdyTFzik8tuSog"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/Lz7WEqJETJqqsUZQYexBQg",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/tPfZeNMvRVujCQqbIbvO0A"
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/tPfZeNMvRVujCQqbIbvO0A",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw"
      )
    ),
    nodes = Vector(
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Tango",
        resourceIri = "http://rdfh.ch/resources/WLSHxQUgTOmG1T0lBU2r5w"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Foxtrot",
        resourceIri = "http://rdfh.ch/resources/Lz7WEqJETJqqsUZQYexBQg"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Echo",
        resourceIri = "http://rdfh.ch/resources/tPfZeNMvRVujCQqbIbvO0A"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Golf",
        resourceIri = "http://rdfh.ch/resources/sHCLAGg-R5qJ6oPZPV-zOQ"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Whiskey",
        resourceIri = "http://rdfh.ch/resources/MiBwAFcxQZGHNL-WfgFAPQ"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "X-ray",
        resourceIri = "http://rdfh.ch/resources/nResNuvARcWYUdWyo0GWGw"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Uniform",
        resourceIri = "http://rdfh.ch/resources/LOV-6aLYQFW15jwdyS51Yw"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Sierra",
        resourceIri = "http://rdfh.ch/resources/0C-0L1kORryKzJAJxxRyRQ"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Romeo",
        resourceIri = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Quebec",
        resourceIri = "http://rdfh.ch/resources/iqW_PBiHRdyTFzik8tuSog"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Papa",
        resourceIri = "http://rdfh.ch/resources/L5xU7Qe5QUu6Wz3cDaCxbA"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Victor",
        resourceIri = "http://rdfh.ch/resources/A67ka6UQRHWf313tbhQBjw"
      )
    ),
    ontologySchema = InternalSchema
  )

  val graphWithStandoffLink: GraphDataGetResponseV2 = GraphDataGetResponseV2(
    edges = Vector(
      GraphEdgeV2(
        target = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA",
        propertyIri = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo".toSmartIri,
        source = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg"
      )),
    nodes = Vector(
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Ein Ding f\u00FCr jemanden, dem die Dinge gefallen",
        resourceIri = "http://rdfh.ch/resources/jT0UHG9_wtaX23VoYydmGg"
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "A thing",
        resourceIri = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA"
      )
    ),
    ontologySchema = InternalSchema
  )

  val graphWithOneNode: GraphDataGetResponseV2 = GraphDataGetResponseV2(
    edges = Nil,
    nodes = Vector(
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Another thing",
        resourceIri = "http://rdfh.ch/resources/4ExjVniQehNpL3hQsU3Dgw"
      )),
    ontologySchema = InternalSchema
  )
}

/**
  * Tests [[ResourcesResponderV2]].
  */
class ResourcesResponderV2Spec extends CoreSpec() with ImplicitSender {

  import ResourcesResponderV2Spec._

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private val resourcesResponderV2SpecFullData = new ResourcesResponderV2SpecFullData

  private var standardMapping: Option[MappingXMLtoStandoff] = None

  private val graphTestData = new GraphTestData

  /* we need to run our app with the mocked sipi actor */
  override lazy val appActor: ActorRef = system.actorOf(
    Props(new ApplicationActor with ManagersWithMockedSipi).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    name = APPLICATION_MANAGER_ACTOR_NAME)

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  private val sampleStandoff: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffRootTag.toSmartIri,
      startPosition = 0,
      endPosition = 26,
      uuid = UUID.randomUUID(),
      originalXMLID = None,
      startIndex = 0
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 12,
      uuid = UUID.randomUUID(),
      originalXMLID = None,
      startIndex = 1,
      startParentIndex = Some(0)
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
      startPosition = 0,
      endPosition = 7,
      uuid = UUID.randomUUID(),
      originalXMLID = None,
      startIndex = 2,
      startParentIndex = Some(1)
    )
  )

  private val sampleStandoffForErasingResource: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffRootTag.toSmartIri,
      startPosition = 0,
      endPosition = 26,
      uuid = UUID.randomUUID(),
      originalXMLID = None,
      startIndex = 0
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 12,
      uuid = UUID.randomUUID(),
      originalXMLID = None,
      startIndex = 1,
      startParentIndex = Some(0)
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
      startPosition = 0,
      endPosition = 7,
      uuid = UUID.randomUUID(),
      originalXMLID = None,
      startIndex = 2,
      startParentIndex = Some(1)
    )
  )

  private def getResource(resourceIri: IRI, requestingUser: UserADM): ReadResourceV2 = {
    responderManager ! ResourcesGetRequestV2(
      resourceIris = Seq(resourceIri),
      targetSchema = ApiV2Complex,
      featureFactoryConfig = defaultFeatureFactoryConfig,
      requestingUser = anythingUserProfile
    )

    expectMsgPF(timeout) {
      case response: ReadResourcesSequenceV2 => response.toResource(resourceIri).toOntologySchema(ApiV2Complex)
    }
  }

  private def checkCreateResource(inputResourceIri: IRI,
                                  inputResource: CreateResourceV2,
                                  outputResource: ReadResourceV2,
                                  defaultResourcePermissions: String,
                                  defaultValuePermissions: String,
                                  requestingUser: UserADM): Unit = {
    assert(outputResource.resourceIri == inputResourceIri)
    assert(outputResource.resourceClassIri == inputResource.resourceClassIri)
    assert(outputResource.label == inputResource.label)
    assert(outputResource.attachedToUser == requestingUser.id)
    assert(outputResource.projectADM.id == inputResource.projectADM.id)

    val expectedPermissions = inputResource.permissions.getOrElse(defaultResourcePermissions)
    assert(outputResource.permissions == expectedPermissions)

    assert(outputResource.values.keySet == inputResource.values.keySet)

    inputResource.values.foreach {
      case (propertyIri: SmartIri, propertyInputValues: Seq[CreateValueInNewResourceV2]) =>
        val propertyOutputValues = outputResource.values(propertyIri)

        assert(propertyOutputValues.size == propertyInputValues.size)

        propertyInputValues.zip(propertyOutputValues).foreach {
          case (inputValue: CreateValueInNewResourceV2, outputValue: ReadValueV2) =>
            val expectedPermissions = inputValue.permissions.getOrElse(defaultValuePermissions)
            assert(outputValue.permissions == expectedPermissions)
            assert(inputValue.valueContent.wouldDuplicateCurrentVersion(outputValue.valueContent))
        }
    }
  }

  private def getStandoffTagByUUID(uuid: UUID): Set[IRI] = {
    val sparqlQuery = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
      .getStandoffTagByUUID(
        triplestore = settings.triplestoreType,
        uuid = uuid,
        stringFormatter = stringFormatter
      )
      .toString()

    storeManager ! SparqlSelectRequest(sparqlQuery)

    expectMsgPF(timeout) {
      case sparqlSelectResponse: SparqlSelectResult =>
        sparqlSelectResponse.results.bindings.map { row =>
          row.rowMap("standoffTag")
        }.toSet
    }
  }

  private def getDeleteDate(resourceIri: IRI): Instant = {
    val sparqlQuery: String = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
      .getDeleteDate(
        triplestore = settings.triplestoreType,
        entityIri = resourceIri
      )
      .toString()

    storeManager ! SparqlSelectRequest(sparqlQuery)

    expectMsgPF(timeout) {
      case sparqlSelectResponse: SparqlSelectResult =>
        val savedDeleteDateStr = sparqlSelectResponse.getFirstRow.rowMap("deleteDate")

        stringFormatter.xsdDateTimeStampToInstant(
          savedDeleteDateStr,
          throw AssertionException(s"Couldn't parse delete date from triplestore: $savedDeleteDateStr")
        )
    }
  }

  // The default timeout for receiving reply messages from actors.
  private val timeout = 30.seconds

  "Load test data" in {
    responderManager ! GetMappingRequestV2(
      mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
      featureFactoryConfig = defaultFeatureFactoryConfig,
      requestingUser = KnoraSystemInstances.Users.SystemUser
    )

    expectMsgPF(timeout) {
      case mappingResponse: GetMappingResponseV2 =>
        standardMapping = Some(mappingResponse.mapping)
    }
  }

  "The resources responder v2" should {
    "return a full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {

      responderManager ! ResourcesGetRequestV2(
        resourceIris = Seq("http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw"),
        versionDate = None,
        targetSchema = ApiV2Complex,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile
      )

      expectMsgPF(timeout) {
        case response: ReadResourcesSequenceV2 =>
          compareReadResourcesSequenceV2Response(
            expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
            received = response)
      }

    }

    "return a preview descriptions of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {

      responderManager ! ResourcesPreviewGetRequestV2(
        resourceIris = Seq("http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw"),
        targetSchema = ApiV2Complex,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile
      )

      expectMsgPF(timeout) {
        case response: ReadResourcesSequenceV2 =>
          compareReadResourcesSequenceV2Response(
            expected = resourcesResponderV2SpecFullData.expectedPreviewResourceResponseForZeitgloecklein,
            received = response)
      }

    }

    "return a full description of the book 'Reise ins Heilige Land' in the Incunabula test data" in {

      responderManager ! ResourcesGetRequestV2(
        resourceIris = Seq("http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw"),
        versionDate = None,
        targetSchema = ApiV2Complex,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile
      )

      expectMsgPF(timeout) {
        case response: ReadResourcesSequenceV2 =>
          compareReadResourcesSequenceV2Response(
            expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForReise,
            received = response)
      }

    }

    "return two full descriptions of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data" in {

      responderManager ! ResourcesGetRequestV2(
        resourceIris =
          Seq("http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw", "http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw"),
        versionDate = None,
        targetSchema = ApiV2Complex,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile
      )

      expectMsgPF(timeout) {
        case response: ReadResourcesSequenceV2 =>
          compareReadResourcesSequenceV2Response(
            expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloeckleinAndReise,
            received = response)
      }

    }

    "return two preview descriptions of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data" in {

      responderManager ! ResourcesPreviewGetRequestV2(
        resourceIris =
          Seq("http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw", "http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw"),
        targetSchema = ApiV2Complex,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile
      )

      expectMsgPF(timeout) {
        case response: ReadResourcesSequenceV2 =>
          compareReadResourcesSequenceV2Response(
            expected = resourcesResponderV2SpecFullData.expectedPreviewResourceResponseForZeitgloeckleinAndReise,
            received = response)
      }

    }

    "return two full descriptions of the 'Reise ins Heilige Land' and the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data (inversed order)" in {

      responderManager ! ResourcesGetRequestV2(
        resourceIris =
          Seq("http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw", "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw"),
        versionDate = None,
        targetSchema = ApiV2Complex,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile
      )

      expectMsgPF(timeout) {
        case response: ReadResourcesSequenceV2 =>
          compareReadResourcesSequenceV2Response(
            expected =
              resourcesResponderV2SpecFullData.expectedFullResourceResponseForReiseAndZeitgloeckleinInversedOrder,
            received = response)
      }

    }

    "return two full descriptions of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data providing redundant resource Iris" in {

      responderManager ! ResourcesGetRequestV2(
        resourceIris = Seq("http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw",
                           "http://rdfh.ch/resources/7dGkt1CLKdZbrxVj324eaw",
                           "http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw"),
        versionDate = None,
        targetSchema = ApiV2Complex,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile
      )

      // the redundant Iri should be ignored (distinct)
      expectMsgPF(timeout) {
        case response: ReadResourcesSequenceV2 =>
          compareReadResourcesSequenceV2Response(
            expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloeckleinAndReise,
            received = response)
      }

    }

    "return a resource of type thing with text as TEI/XML" in {

      responderManager ! ResourceTEIGetRequestV2(
        resourceIri = "http://rdfh.ch/resources/bTeEHKqnAHeqfwWBQWLbkA",
        textProperty = "http://www.knora.org/ontology/0001/anything#hasRichtext".toSmartIri,
        mappingIri = None,
        gravsearchTemplateIri = None,
        headerXSLTIri = None,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )

      expectMsgPF(timeout) {
        case response: ResourceTEIGetResponseV2 =>
          val expectedBody =
            """<text><body><p>This is a test that contains marked up elements. This is <hi rend="italic">interesting text</hi> in italics. This is <hi rend="italic">boring text</hi> in italics.</p></body></text>""".stripMargin

          // Compare the original XML with the regenerated XML.
          val xmlDiff: Diff =
            DiffBuilder.compare(Input.fromString(response.body.toXML)).withTest(Input.fromString(expectedBody)).build()

          xmlDiff.hasDifferences should be(false)
      }

    }

    "return a resource of type Something with text with standoff as TEI/XML" in {

      responderManager ! ResourceTEIGetRequestV2(
        resourceIri = "http://rdfh.ch/resources/qN1igiDRSAemBBktbRHn6g",
        textProperty = "http://www.knora.org/ontology/0001/anything#hasRichtext".toSmartIri,
        mappingIri = None,
        gravsearchTemplateIri = None,
        headerXSLTIri = None,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )

      expectMsgPF(timeout) {
        case response: ResourceTEIGetResponseV2 =>
          val expectedBody =
            """<text><body><p><hi rend="bold">Something</hi> <hi rend="italic">with</hi> a <del>lot</del> of <hi rend="underline">different</hi> <hi rend="sup">markup</hi>. And more <ref target="http://www.google.ch">markup</ref>.</p></body></text>""".stripMargin

          // Compare the original XML with the regenerated XML.
          val xmlDiff: Diff =
            DiffBuilder.compare(Input.fromString(response.body.toXML)).withTest(Input.fromString(expectedBody)).build()

          xmlDiff.hasDifferences should be(false)
      }

    }

    "return a past version of a resource" in {
      val resourceIri = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw"
      val versionDate = Instant.parse("2019-02-12T08:05:10Z")

      responderManager ! ResourcesGetRequestV2(
        resourceIris = Seq(resourceIri),
        versionDate = Some(versionDate),
        targetSchema = ApiV2Complex,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )

      expectMsgPF(timeout) {
        case response: ReadResourcesSequenceV2 =>
          compareReadResourcesSequenceV2Response(
            expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForThingWithHistory,
            received = response)
      }

    }

    "return the complete version history of a resource" in {
      val resourceIri = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw"

      responderManager ! ResourceVersionHistoryGetRequestV2(
        resourceIri = resourceIri,
        startDate = None,
        endDate = None,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )

      expectMsgPF(timeout) {
        case response: ResourceVersionHistoryResponseV2 =>
          assert(response == resourcesResponderV2SpecFullData.expectedCompleteVersionHistoryResponse)
      }
    }

    "return the version history of a resource within a date range" in {
      val resourceIri = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw"
      val startDate = Instant.parse("2019-02-08T15:05:11Z")
      val endDate = Instant.parse("2019-02-13T09:05:10Z")

      responderManager ! ResourceVersionHistoryGetRequestV2(
        resourceIri = resourceIri,
        startDate = Some(startDate),
        endDate = Some(endDate),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )

      expectMsgPF(timeout) {
        case response: ResourceVersionHistoryResponseV2 =>
          assert(response == resourcesResponderV2SpecFullData.expectedPartialVersionHistoryResponse)
      }
    }

    "get the latest version of a value, given its UUID" in {
      responderManager ! ResourcesGetRequestV2(
        resourceIris = Seq("http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw"),
        valueUuid = Some(stringFormatter.decodeUuid("pLlW4ODASumZfZFbJdpw1g")),
        targetSchema = ApiV2Complex,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )

      expectMsgPF(timeout) {
        case response: ReadResourcesSequenceV2 =>
          compareReadResourcesSequenceV2Response(
            expected = resourcesResponderV2SpecFullData.expectedFullResponseResponseForThingWithValueByUuid,
            received = response)
      }
    }

    "get a past version of a value, given its UUID and a timestamp" in {
      responderManager ! ResourcesGetRequestV2(
        resourceIris = Seq("http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw"),
        valueUuid = Some(stringFormatter.decodeUuid("pLlW4ODASumZfZFbJdpw1g")),
        versionDate = Some(Instant.parse("2019-02-12T09:05:10Z")),
        targetSchema = ApiV2Complex,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )

      expectMsgPF(timeout) {
        case response: ReadResourcesSequenceV2 =>
          compareReadResourcesSequenceV2Response(
            expected =
              resourcesResponderV2SpecFullData.expectedFullResponseResponseForThingWithValueByUuidAndVersionDate,
            received = response)
      }
    }

    "return a graph of resources reachable via links from/to a given resource" in {
      responderManager ! GraphDataGetRequestV2(
        resourceIri = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw",
        depth = 6,
        inbound = true,
        outbound = true,
        excludeProperty = Some(OntologyConstants.KnoraApiV2Complex.IsPartOf.toSmartIri),
        requestingUser = SharedTestDataADM.anythingUser1
      )

      val response = expectMsgType[GraphDataGetResponseV2](timeout)
      val edges = response.edges
      val nodes = response.nodes

      edges should contain theSameElementsAs graphTestData.graphForAnythingUser1.edges
      nodes should contain theSameElementsAs graphTestData.graphForAnythingUser1.nodes
    }

    "return a graph of resources reachable via links from/to a given resource, filtering the results according to the user's permissions" in {
      responderManager ! GraphDataGetRequestV2(
        resourceIri = "http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw",
        depth = 6,
        inbound = true,
        outbound = true,
        excludeProperty = Some(OntologyConstants.KnoraApiV2Complex.IsPartOf.toSmartIri),
        requestingUser = SharedTestDataADM.incunabulaProjectAdminUser
      )

      val response = expectMsgType[GraphDataGetResponseV2](timeout)
      val edges = response.edges
      val nodes = response.nodes

      edges should contain theSameElementsAs graphTestData.graphForIncunabulaUser.edges
      nodes should contain theSameElementsAs graphTestData.graphForIncunabulaUser.nodes
    }

    "return a graph containing a standoff link" in {
      responderManager ! GraphDataGetRequestV2(
        resourceIri = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA",
        depth = 4,
        inbound = true,
        outbound = true,
        excludeProperty = Some(OntologyConstants.KnoraApiV2Complex.IsPartOf.toSmartIri),
        requestingUser = SharedTestDataADM.anythingUser1
      )

      expectMsgPF(timeout) {
        case response: GraphDataGetResponseV2 => response should ===(graphTestData.graphWithStandoffLink)
      }
    }

    "return a graph containing just one node" in {
      responderManager ! GraphDataGetRequestV2(
        resourceIri = "http://rdfh.ch/resources/4ExjVniQehNpL3hQsU3Dgw",
        depth = 4,
        inbound = true,
        outbound = true,
        excludeProperty = Some(OntologyConstants.KnoraApiV2Complex.IsPartOf.toSmartIri),
        requestingUser = SharedTestDataADM.anythingUser1
      )

      expectMsgPF(timeout) {
        case response: GraphDataGetResponseV2 => response should ===(graphTestData.graphWithOneNode)
      }
    }

    "create a resource with no values" in {
      // Create the resource.

      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "test thing",
        values = Map.empty,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      // Check that the response contains the correct metadata.

      expectMsgPF(timeout) {
        case response: ReadResourcesSequenceV2 =>
          val outputResource: ReadResourceV2 = response.toResource(resourceIri).toOntologySchema(ApiV2Complex)

          checkCreateResource(
            inputResourceIri = resourceIri,
            inputResource = inputResource,
            outputResource = outputResource,
            defaultResourcePermissions = defaultAnythingResourcePermissions,
            defaultValuePermissions = defaultAnythingValuePermissions,
            requestingUser = anythingUserProfile
          )
      }

      // Get the resource from the triplestore and check it again.

      val outputResource = getResource(resourceIri, anythingUserProfile)

      checkCreateResource(
        inputResourceIri = resourceIri,
        inputResource = inputResource,
        outputResource = outputResource,
        defaultResourcePermissions = defaultAnythingResourcePermissions,
        defaultValuePermissions = defaultAnythingValuePermissions,
        requestingUser = anythingUserProfile
      )
    }

    "create a resource with no values and custom permissions" in {
      // Create the resource.

      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "test thing",
        values = Map.empty,
        projectADM = SharedTestDataADM.anythingProject,
        permissions = Some("CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher")
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[ReadResourcesSequenceV2](timeout)

      // Get the resource from the triplestore and check it.

      val outputResource = getResource(resourceIri, anythingUserProfile)

      checkCreateResource(
        inputResourceIri = resourceIri,
        inputResource = inputResource,
        outputResource = outputResource,
        defaultResourcePermissions = defaultAnythingResourcePermissions,
        defaultValuePermissions = defaultAnythingValuePermissions,
        requestingUser = anythingUserProfile
      )
    }

    "create a resource with values" in {
      // Create the resource.

      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = IntegerValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasInteger = 5,
              comment = Some("this is the number five")
            ),
            permissions = Some("CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher")
          ),
          CreateValueInNewResourceV2(
            valueContent = IntegerValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasInteger = 6
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("this is text without standoff")
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("this is text with standoff"),
              standoff = sampleStandoff,
              mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
              mapping = standardMapping
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = DecimalValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasDecimal = BigDecimal("100000000000000.000000000000001")
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = DateValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasCalendar = CalendarNameGregorian,
              valueHasStartJDN = 2264907,
              valueHasStartPrecision = DatePrecisionYear,
              valueHasEndJDN = 2265271,
              valueHasEndPrecision = DatePrecisionYear
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = BooleanValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasBoolean = true
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = GeomValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasGeometry =
                """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = IntervalValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasIntervalStart = BigDecimal("1.2"),
              valueHasIntervalEnd = BigDecimal("3.4")
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = HierarchicalListValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasListNode = "http://rdfh.ch/lists/0001/treeList03"
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = ColorValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasColor = "#ff3333"
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = UriValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasUri = "https://www.knora.org"
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = GeonameValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasGeonameCode = "2661604"
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = LinkValueContentV2(
              ontologySchema = ApiV2Complex,
              referredResourceIri = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA"
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "test thing",
        values = inputValues,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[ReadResourcesSequenceV2](timeout)

      // Get the resource from the triplestore and check it.

      val outputResource = getResource(resourceIri, anythingUserProfile)

      checkCreateResource(
        inputResourceIri = resourceIri,
        inputResource = inputResource,
        outputResource = outputResource,
        defaultResourcePermissions = defaultAnythingResourcePermissions,
        defaultValuePermissions = defaultAnythingValuePermissions,
        requestingUser = anythingUserProfile
      )
    }

    "create a resource with a still image file value" in {
      // Create the resource.

      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        OntologyConstants.KnoraApiV2Complex.HasStillImageFileValue.toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = StillImageFileValueContentV2(
              ontologySchema = ApiV2Complex,
              fileValue = FileValueV2(
                internalFilename = "IQUO3t1AABm-FSLC0vNvVpr.jp2",
                internalMimeType = "image/jp2",
                originalFilename = Some("test.tiff"),
                originalMimeType = Some("image/tiff")
              ),
              dimX = 512,
              dimY = 256
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture".toSmartIri,
        label = "test thing picture",
        values = inputValues,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[ReadResourcesSequenceV2](timeout)

      // Get the resource from the triplestore and check it.

      val outputResource = getResource(resourceIri, anythingUserProfile)

      checkCreateResource(
        inputResourceIri = resourceIri,
        inputResource = inputResource,
        outputResource = outputResource,
        defaultResourcePermissions = defaultAnythingResourcePermissions,
        defaultValuePermissions = defaultStillImageFileValuePermissions,
        requestingUser = anythingUserProfile
      )
    }

    "not create a resource with missing required values" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        label = "invalid book",
        values = Map.empty,
        projectADM = SharedTestDataADM.incunabulaProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
      }
    }

    "not create a resource with too many values for the cardinality of a property" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("test title")
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0803/incunabula/v2#publoc".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("test publoc 1")
            )
          ),
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("test publoc 2")
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        label = "invalid book",
        values = inputValues,
        projectADM = SharedTestDataADM.incunabulaProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
      }
    }

    "not create a resource with a property for which there is no cardinality in the resource class" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("test title")
            )
          )
        ),
        "http://0.0.0.0:3333/ontology/0803/incunabula/v2#pagenum".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("test pagenum")
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        label = "invalid book",
        values = inputValues,
        projectADM = SharedTestDataADM.incunabulaProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
      }
    }

    "not create a resource with duplicate values" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("test title 1")
            )
          ),
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("test title 2")
            )
          ),
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("test title 1")
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        label = "invalid book",
        values = inputValues,
        projectADM = SharedTestDataADM.incunabulaProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
      }
    }

    "not create a resource if the user doesn't have permission to create resources in the project" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("test title")
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        label = "invalid book",
        values = inputValues,
        projectADM = SharedTestDataADM.incunabulaProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }
    }

    "not create a resource with a link to a nonexistent other resource" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = LinkValueContentV2(
              ontologySchema = ApiV2Complex,
              referredResourceIri = "http://rdfh.ch/resources/rJvywsW7e4FDZ9KEptTtMg"
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "invalid thing",
        values = inputValues,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
      }
    }

    "not create a resource with a standoff link to a nonexistent other resource" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val standoffWithInvalidLink: Vector[StandoffTagV2] = Vector(
        StandoffTagV2(
          standoffTagClassIri = OntologyConstants.Standoff.StandoffRootTag.toSmartIri,
          startPosition = 0,
          endPosition = 26,
          uuid = UUID.randomUUID(),
          originalXMLID = None,
          startIndex = 0
        ),
        StandoffTagV2(
          standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag.toSmartIri,
          dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
          startPosition = 0,
          endPosition = 12,
          uuid = UUID.randomUUID(),
          originalXMLID = None,
          startIndex = 1,
          attributes = Vector(
            StandoffTagIriAttributeV2(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri,
                                      value = "http://rdfh.ch/resources/rJvywsW7e4FDZ9KEptTtMg")),
          startParentIndex = Some(0)
        ),
        StandoffTagV2(
          standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
          startPosition = 0,
          endPosition = 7,
          uuid = UUID.randomUUID(),
          originalXMLID = None,
          startIndex = 2,
          startParentIndex = Some(1)
        )
      )

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("this is text with standoff"),
              standoff = standoffWithInvalidLink,
              mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
              mapping = standardMapping
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "invalid thing",
        values = inputValues,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
      }
    }

    "not create a resource with a list value referring to a nonexistent list node" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = HierarchicalListValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasListNode = "http://rdfh.ch/lists/0001/nonexistent-list-node"
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "invalid thing",
        values = inputValues,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
      }
    }

    "not create a resource with a value that's the wrong type for the property" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("invalid text value")
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "invalid thing",
        values = inputValues,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
      }
    }

    "not create a resource with a link to a resource of the wrong class for the link property" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = LinkValueContentV2(
              ontologySchema = ApiV2Complex,
              referredResourceIri = zeitglöckleinIri
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "invalid thing",
        values = inputValues,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
      }
    }

    "not create a resource with invalid custom permissions" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "invalid thing",
        values = Map.empty,
        projectADM = SharedTestDataADM.anythingProject,
        permissions = Some("M knora-admin:Creator,V knora-admin:KnownUser")
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a resource with a value that has invalid custom permissions" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val values: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = IntegerValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasInteger = 5,
              comment = Some("this is the number five")
            ),
            permissions = Some("M knora-admin:Creator,V knora-admin:KnownUser")
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "invalid thing",
        values = values,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not create a resource that uses a class from another non-shared project" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "test thing",
        values = Map.empty,
        projectADM = SharedTestDataADM.incunabulaProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "not update a resource's metadata if the user does not have permission to update the resource" in {
      val updateRequest = UpdateResourceMetadataRequestV2(
        resourceIri = aThingIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeLabel = Some("new test label"),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = incunabulaUserProfile,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }
    }

    "not update a resource's metadata if the user does not supply the correct resource class" in {
      val updateRequest = UpdateResourceMetadataRequestV2(
        resourceIri = aThingIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#BlueThing".toSmartIri,
        maybeLabel = Some("new test label"),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "update a resource's metadata when it doesn't have a knora-base:lastModificationDate" in {
      val dateTimeStampBeforeUpdate = Instant.now
      val newLabel = "new test label"
      val newPermissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:ProjectMember"

      val updateRequest = UpdateResourceMetadataRequestV2(
        resourceIri = aThingIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeLabel = Some(newLabel),
        maybePermissions = Some(newPermissions),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateRequest

      expectMsgType[SuccessResponseV2](timeout)

      // Get the resource from the triplestore and check it.

      val outputResource: ReadResourceV2 = getResource(aThingIri, anythingUserProfile)
      assert(outputResource.label == newLabel)
      assert(
        PermissionUtilADM.parsePermissions(outputResource.permissions) == PermissionUtilADM.parsePermissions(
          newPermissions))
      aThingLastModificationDate = outputResource.lastModificationDate.get
      assert(aThingLastModificationDate.isAfter(dateTimeStampBeforeUpdate))
    }

    "not update a resource's metadata if its knora-base:lastModificationDate exists but is not submitted" in {
      val updateRequest = UpdateResourceMetadataRequestV2(
        resourceIri = aThingIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeLabel = Some("another new test label"),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[EditConflictException] should ===(true)
      }
    }

    "not update a resource's metadata if the wrong knora-base:lastModificationDate is submitted" in {
      val updateRequest = UpdateResourceMetadataRequestV2(
        resourceIri = aThingIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeLastModificationDate = Some(Instant.MIN),
        maybeLabel = Some("another new test label"),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[EditConflictException] should ===(true)
      }
    }

    "update a resource's metadata when it has a knora-base:lastModificationDate" in {
      val newLabel = "another new test label"

      val updateRequest = UpdateResourceMetadataRequestV2(
        resourceIri = aThingIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeLastModificationDate = Some(aThingLastModificationDate),
        maybeLabel = Some(newLabel),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateRequest

      expectMsgType[SuccessResponseV2](timeout)

      // Get the resource from the triplestore and check it.

      val outputResource: ReadResourceV2 = getResource(aThingIri, anythingUserProfile)
      assert(outputResource.label == newLabel)
      val updatedLastModificationDate = outputResource.lastModificationDate.get
      assert(updatedLastModificationDate.isAfter(aThingLastModificationDate))
      aThingLastModificationDate = updatedLastModificationDate
    }

    "not update a resource's knora-base:lastModificationDate with a value that's earlier than the current value" in {
      val updateRequest = UpdateResourceMetadataRequestV2(
        resourceIri = aThingIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeLastModificationDate = Some(aThingLastModificationDate),
        maybeNewModificationDate = Some(Instant.MIN),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "update a resource's knora-base:lastModificationDate" in {
      val newModificationDate = Instant.now.plus(java.time.Duration.ofDays(1))

      val updateRequest = UpdateResourceMetadataRequestV2(
        resourceIri = aThingIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeLastModificationDate = Some(aThingLastModificationDate),
        maybeNewModificationDate = Some(newModificationDate),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! updateRequest

      expectMsgType[SuccessResponseV2](timeout)

      // Get the resource from the triplestore and check it.

      val outputResource: ReadResourceV2 = getResource(aThingIri, anythingUserProfile)
      val updatedLastModificationDate = outputResource.lastModificationDate.get
      assert(updatedLastModificationDate == newModificationDate)
      aThingLastModificationDate = updatedLastModificationDate
    }

    "not mark a resource as deleted with a custom delete date that is earlier than the resource's last modification date" in {
      val deleteDate: Instant = aThingLastModificationDate.minus(1, ChronoUnit.DAYS)

      val deleteRequest = DeleteOrEraseResourceRequestV2(
        resourceIri = aThingIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeDeleteComment = Some("This resource is too boring."),
        maybeDeleteDate = Some(deleteDate),
        maybeLastModificationDate = Some(aThingLastModificationDate),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! deleteRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }
    }

    "mark a resource as deleted" in {
      val deleteRequest = DeleteOrEraseResourceRequestV2(
        resourceIri = aThingIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeDeleteComment = Some("This resource is too boring."),
        maybeLastModificationDate = Some(aThingLastModificationDate),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.anythingUser1,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! deleteRequest

      expectMsgType[SuccessResponseV2](timeout)

      // We should now be unable to request the resource.

      responderManager ! ResourcesGetRequestV2(
        resourceIris = Seq(aThingIri),
        targetSchema = ApiV2Complex,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.anythingUser1
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
      }
    }

    "mark a resource as deleted, supplying a custom delete date" in {
      val resourceIri = "http://rdfh.ch/resources/5IEswyQFQp2bxXDrOyEfEA"
      val deleteDate: Instant = Instant.now

      val deleteRequest = DeleteOrEraseResourceRequestV2(
        resourceIri = resourceIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeDeleteComment = Some("This resource is too boring."),
        maybeDeleteDate = Some(deleteDate),
        maybeLastModificationDate = None,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.superUser,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! deleteRequest

      expectMsgType[SuccessResponseV2](timeout)

      // We should now be unable to request the resource.

      responderManager ! ResourcesGetRequestV2(
        resourceIris = Seq(resourceIri),
        targetSchema = ApiV2Complex,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.anythingUser1
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
      }

      val savedDeleteDate: Instant = getDeleteDate(resourceIri)
      assert(savedDeleteDate == deleteDate)
    }

    "not accept custom resource permissions that would give the requesting user a higher permission on a resource than the default" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
        label = "test bildformat",
        values = Map.empty,
        projectADM = SharedTestDataADM.imagesProject,
        permissions = Some("CR knora-admin:Creator")
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.imagesReviewerUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure =>
          msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }
    }

    "accept custom resource permissions that would give the requesting user a higher permission on a resource than the default if the user is a system admin" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
        label = "test bildformat",
        values = Map.empty,
        projectADM = SharedTestDataADM.imagesProject,
        permissions = Some("CR knora-admin:Creator")
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.rootUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgClass(classOf[ReadResourcesSequenceV2])
    }

    "accept custom resource permissions that would give the requesting user a higher permission on a resource than the default if the user is a project admin" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
        label = "test bildformat",
        values = Map.empty,
        projectADM = SharedTestDataADM.imagesProject,
        permissions = Some("CR knora-admin:Creator")
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.imagesUser01,
        apiRequestID = UUID.randomUUID
      )

      expectMsgClass(classOf[ReadResourcesSequenceV2])
    }

    "not accept custom value permissions that would give the requesting user a higher permission on a value than the default" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = IntegerValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasInteger = 5,
              comment = Some("this is the number five")
            ),
            permissions = Some("CR knora-admin:Creator")
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
        label = "test bildformat",
        values = inputValues,
        projectADM = SharedTestDataADM.imagesProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.imagesReviewerUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure =>
          msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }
    }

    "accept custom value permissions that would give the requesting user a higher permission on a value than the default if the user is a system admin" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = IntegerValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasInteger = 5,
              comment = Some("this is the number five")
            ),
            permissions = Some("CR knora-admin:Creator")
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
        label = "test bildformat",
        values = inputValues,
        projectADM = SharedTestDataADM.imagesProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.rootUser,
        apiRequestID = UUID.randomUUID
      )

      expectMsgClass(timeout, (classOf[ReadResourcesSequenceV2]))
    }

    "accept custom value permissions that would give the requesting user a higher permission on a value than the default if the user is a project admin" in {
      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = IntegerValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasInteger = 5,
              comment = Some("this is the number five")
            ),
            permissions = Some("CR knora-admin:Creator")
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
        label = "test bildformat",
        values = inputValues,
        projectADM = SharedTestDataADM.imagesProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.imagesUser01,
        apiRequestID = UUID.randomUUID
      )

      expectMsgClass(classOf[ReadResourcesSequenceV2])
    }

    "create a resource with version history so we can test erasing it" in {
      // Create the resource.

      val resourceIri: IRI = stringFormatter.makeResourceIri()
      resourceIriToErase.set(resourceIri)
      val resourceClassIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri
      val propertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri
      val standoffTagUUIDsToErase = collection.mutable.Set.empty[UUID]

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        propertyIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = TextValueContentV2(
              ontologySchema = ApiV2Complex,
              maybeValueHasString = Some("this is text with standoff"),
              standoff = sampleStandoffForErasingResource,
              mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
              mapping = standardMapping
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = resourceClassIri,
        label = "test thing",
        values = inputValues,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[ReadResourcesSequenceV2](timeout)
      val outputResource: ReadResourceV2 = getResource(resourceIri = resourceIri, requestingUser = anythingUserProfile)
      val firstTextValue: ReadTextValueV2 = outputResource.values(propertyIri).head.asInstanceOf[ReadTextValueV2]
      firstValueIriToErase.set(firstTextValue.valueIri)

      for (standoffTag <- firstTextValue.valueContent.standoff) {
        standoffTagUUIDsToErase.add(standoffTag.uuid)
      }

      // Update the value.

      responderManager ! UpdateValueRequestV2(
        UpdateValueContentV2(
          resourceIri = resourceIri,
          resourceClassIri = resourceClassIri,
          propertyIri = propertyIri,
          valueIri = firstValueIriToErase.get,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some("this is some other text with standoff"),
            standoff = Vector(sampleStandoffForErasingResource.head),
            mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
            mapping = standardMapping
          )
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgPF(timeout) {
        case updateValueResponse: UpdateValueResponseV2 =>
          secondValueIriToErase.set(updateValueResponse.valueIri)
      }

      val updatedResource = getResource(resourceIri = resourceIri, requestingUser = anythingUserProfile)
      val secondTextValue: ReadTextValueV2 = updatedResource.values(propertyIri).head.asInstanceOf[ReadTextValueV2]
      secondValueIriToErase.set(secondTextValue.valueIri)

      for (standoffTag <- firstTextValue.valueContent.standoff) {
        standoffTagUUIDsToErase.add(standoffTag.uuid)
      }

      assert(standoffTagUUIDsToErase.size == 3)
      resourceToEraseLastModificationDate = updatedResource.lastModificationDate.get

      // Get the IRIs of the standoff tags.

      for (uuid <- standoffTagUUIDsToErase) {
        val standoffTagIris: Set[IRI] = getStandoffTagByUUID(uuid)
        standoffTagIrisToErase ++= standoffTagIris
      }

      assert(standoffTagIrisToErase.size == 4)
    }

    "not erase a resource if the user is not a system/project admin" in {
      val eraseRequest = DeleteOrEraseResourceRequestV2(
        resourceIri = resourceIriToErase.get,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeLastModificationDate = Some(resourceToEraseLastModificationDate),
        erase = true,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! eraseRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure =>
          // println(msg.cause)
          msg.cause.isInstanceOf[ForbiddenException] should ===(true)
      }
    }

    "not erase a resource if another resource has a link to it" in {
      // Create a resource with a link to the resource that is to be deleted.

      val resourceWithLinkIri: IRI = stringFormatter.makeResourceIri()
      val resourceClassIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri
      val linkValuePropertyIri: SmartIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        linkValuePropertyIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = LinkValueContentV2(
              ontologySchema = ApiV2Complex,
              referredResourceIri = resourceIriToErase.get
            )
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceWithLinkIri.toSmartIri),
        resourceClassIri = resourceClassIri,
        label = "thing with link",
        values = inputValues,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[ReadResourcesSequenceV2](timeout)
      val outputResource: ReadResourceV2 =
        getResource(resourceIri = resourceWithLinkIri, requestingUser = anythingUserProfile)
      val linkValue: ReadLinkValueV2 = outputResource.values(linkValuePropertyIri).head.asInstanceOf[ReadLinkValueV2]

      // Try to erase the first resource.

      val eraseRequest = DeleteOrEraseResourceRequestV2(
        resourceIri = resourceIriToErase.get,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeLastModificationDate = Some(resourceToEraseLastModificationDate),
        erase = true,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.anythingAdminUser,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! eraseRequest

      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure =>
          // println(msg.cause)
          msg.cause.isInstanceOf[BadRequestException] should ===(true)
      }

      // Delete the link.

      responderManager ! DeleteValueRequestV2(
        resourceIri = resourceWithLinkIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        propertyIri = linkValuePropertyIri,
        valueIri = linkValue.valueIri,
        valueTypeIri = OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[SuccessResponseV2](timeout)
    }

    "erase a resource" in {
      // Erase the resource.

      val eraseRequest = DeleteOrEraseResourceRequestV2(
        resourceIri = resourceIriToErase.get,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeLastModificationDate = Some(resourceToEraseLastModificationDate),
        erase = true,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.anythingAdminUser,
        apiRequestID = UUID.randomUUID
      )

      responderManager ! eraseRequest
      expectMsgType[SuccessResponseV2](timeout)

      // Check that all parts of the resource were erased.

      val erasedIrisToCheck: Set[SmartIri] = (
        standoffTagIrisToErase.toSet +
          resourceIriToErase.get +
          firstValueIriToErase.get +
          secondValueIriToErase.get
      ).map(_.toSmartIri)

      for (erasedIriToCheck <- erasedIrisToCheck) {
        val sparqlQuery = org.knora.webapi.messages.twirl.queries.sparql.admin.txt
          .checkIriExists(
            iri = erasedIriToCheck.toString
          )
          .toString()

        storeManager ! SparqlAskRequest(sparqlQuery)

        expectMsgPF(timeout) {
          case entityExistsResponse: SparqlAskResponse =>
            entityExistsResponse.result should be(false)
        }
      }

      // Check that the deleted link value that pointed to the resource has also been erased.

      val isEntityUsedSparql: String = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
        .isEntityUsed(
          triplestore = settings.triplestoreType,
          entityIri = resourceIriToErase.get.toSmartIri,
          ignoreKnoraConstraints = true
        )
        .toString()

      storeManager ! SparqlSelectRequest(isEntityUsedSparql)

      expectMsgPF(timeout) {
        case entityUsedResponse: SparqlSelectResult =>
          assert(entityUsedResponse.results.bindings.isEmpty, s"Link value was not erased")
      }
    }
  }

  "When given a custom IRI" should {

    "create a resource with no values but a custom IRI" in {
      // Create the resource.

      val resourceIri: IRI = "http://rdfh.ch/resources/L9WNFBTMUzyFhsEmIndcAQ"

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "thing with a custom IRI",
        values = Map.empty,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      // Check that the response contains the correct metadata.

      expectMsgPF(timeout) {
        case response: ReadResourcesSequenceV2 =>
          val outputResource: ReadResourceV2 = response.toResource(resourceIri).toOntologySchema(ApiV2Complex)

          checkCreateResource(
            inputResourceIri = resourceIri,
            inputResource = inputResource,
            outputResource = outputResource,
            defaultResourcePermissions = defaultAnythingResourcePermissions,
            defaultValuePermissions = defaultAnythingValuePermissions,
            requestingUser = anythingUserProfile
          )

          assert(stringFormatter.base64EncodeUuid(outputResource.resourceUUID) == "L9WNFBTMUzyFhsEmIndcAQ")
      }

      // Get the resource from the triplestore and check it again.

      val outputResource = getResource(resourceIri, anythingUserProfile)

      checkCreateResource(
        inputResourceIri = resourceIri,
        inputResource = inputResource,
        outputResource = outputResource,
        defaultResourcePermissions = defaultAnythingResourcePermissions,
        defaultValuePermissions = defaultAnythingValuePermissions,
        requestingUser = anythingUserProfile
      )
    }

    "create a resource with a value that has custom UUID" in {
      // Create the resource.

      val resourceIri: IRI = stringFormatter.makeResourceIri()

      val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
        "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri -> Seq(
          CreateValueInNewResourceV2(
            valueContent = IntegerValueContentV2(
              ontologySchema = ApiV2Complex,
              valueHasInteger = 5,
              comment = Some("this is the number five")
            ),
            permissions = Some("CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"),
            customValueUUID = Some(stringFormatter.base64DecodeUuid("IN4R19yYR0ygi3K2VEHpUQ"))
          )
        )
      )

      val inputResource = CreateResourceV2(
        resourceIri = Some(resourceIri.toSmartIri),
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        label = "thing with custom value UUID",
        values = inputValues,
        projectADM = SharedTestDataADM.anythingProject
      )

      responderManager ! CreateResourceRequestV2(
        createResource = inputResource,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[ReadResourcesSequenceV2](timeout)

      // Get the resource from the triplestore and check it.

      val outputResource = getResource(resourceIri, anythingUserProfile)

      checkCreateResource(
        inputResourceIri = resourceIri,
        inputResource = inputResource,
        outputResource = outputResource,
        defaultResourcePermissions = defaultAnythingResourcePermissions,
        defaultValuePermissions = defaultAnythingValuePermissions,
        requestingUser = anythingUserProfile
      )
    }
  }
  "When asked for events" should {
    "return full history of a-thing-picture resource" in {
      val resourceIri = "http://rdfh.ch/resources/h2TEa725XPK7CfG15Bk0bA"

      responderManager ! ResourceHistoryEventsGetRequestV2(
        resourceIri = resourceIri,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )
      val response: ResourceAndValueVersionHistoryResponseV2 =
        expectMsgType[ResourceAndValueVersionHistoryResponseV2](timeout)
      val events: Seq[ResourceAndValueHistoryEvent] = response.historyEvents
      events.size shouldEqual (3)
      val createResourceEvents =
        events.filter(historyEvent => historyEvent.eventType == ResourceAndValueEventsUtil.CREATE_RESOURCE_EVENT)
      createResourceEvents.size should be(1)
      val createValueEvents =
        events.filter(historyEvent => historyEvent.eventType == ResourceAndValueEventsUtil.CREATE_VALUE_EVENT)
      createValueEvents.size should be(1)
      val updateValueEvents =
        events.filter(historyEvent => historyEvent.eventType == ResourceAndValueEventsUtil.UPDATE_VALUE_CONTENT_EVENT)
      updateValueEvents.size should be(1)
    }

    "return full history of a resource as events" in {
      val resourceIri = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw"

      responderManager ! ResourceHistoryEventsGetRequestV2(
        resourceIri = resourceIri,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )
      val response: ResourceAndValueVersionHistoryResponseV2 =
        expectMsgType[ResourceAndValueVersionHistoryResponseV2](timeout)
      val events: Seq[ResourceAndValueHistoryEvent] = response.historyEvents
      events.size should be(9)
    }

    "update value permission to test update permission event" in {
      val resourceIri = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw"
      // Update the value permission.

      responderManager ! UpdateValueRequestV2(
        UpdateValuePermissionsV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri,
          valueIri = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw/values/1c",
          valueType = OntologyConstants.KnoraApiV2Complex.IntValue.toSmartIri,
          permissions = "CR knora-admin:Creator|V knora-admin:KnownUser"
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      val updateValuePermissionResponse = expectMsgType[UpdateValueResponseV2](timeout)

      responderManager ! ResourceHistoryEventsGetRequestV2(
        resourceIri = resourceIri,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )
      val response: ResourceAndValueVersionHistoryResponseV2 =
        expectMsgType[ResourceAndValueVersionHistoryResponseV2](timeout)
      val events: Seq[ResourceAndValueHistoryEvent] = response.historyEvents
      events.size should be(10)
      val updatePermissionEvent: Option[ResourceAndValueHistoryEvent] =
        events.find(event => event.eventType == ResourceAndValueEventsUtil.UPDATE_VALUE_PERMISSION_EVENT)
      assert(updatePermissionEvent.isDefined)
      val updatePermissionPayload = updatePermissionEvent.get.eventBody
        .asInstanceOf[ValueEventBody]
      updatePermissionPayload.valueIri shouldEqual (updateValuePermissionResponse.valueIri)
    }

    "create a new value to test create value history event" in {
      val resourceIri = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw"
      val newValueIri = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw/values/xZisRC3jPkcplt1hQQdb-A"
      val testValue = "a test value"
      // create new value.

      responderManager ! CreateValueRequestV2(
        CreateValueV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri,
          valueContent = TextValueContentV2(
            ontologySchema = ApiV2Complex,
            maybeValueHasString = Some(testValue)
          ),
          valueIri = Some(newValueIri.toSmartIri),
          permissions = Some("CR knora-admin:Creator|V knora-admin:KnownUser")
        ),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[CreateValueResponseV2](timeout)

      responderManager ! ResourceHistoryEventsGetRequestV2(
        resourceIri = resourceIri,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )
      val response: ResourceAndValueVersionHistoryResponseV2 =
        expectMsgType[ResourceAndValueVersionHistoryResponseV2](timeout)
      val events: Seq[ResourceAndValueHistoryEvent] = response.historyEvents
      events.size should be(11)
      val createValueEvent: Option[ResourceAndValueHistoryEvent] =
        events.find(
          event =>
            event.eventType == ResourceAndValueEventsUtil.CREATE_VALUE_EVENT && event.eventBody
              .asInstanceOf[ValueEventBody]
              .valueIri == newValueIri)
      assert(createValueEvent.isDefined)
      val createValuePayloadContent = createValueEvent.get.eventBody
        .asInstanceOf[ValueEventBody]
        .valueContent
      assert(createValuePayloadContent.isDefined)
      val payloadContent = createValuePayloadContent.get
      assert(payloadContent.valueType == OntologyConstants.KnoraBase.TextValue.toSmartIri)
      assert(payloadContent.valueHasString == testValue)
    }

    "delete the newly created value to check the delete value event of resource history" in {
      val resourceIri = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw"
      val valueToDelete = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw/values/xZisRC3jPkcplt1hQQdb-A"
      val deleteComment = "delete value test"
      // delete the new value.

      responderManager ! DeleteValueRequestV2(
        resourceIri = resourceIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri,
        valueIri = valueToDelete,
        valueTypeIri = OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri,
        deleteComment = Some(deleteComment),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )
      expectMsgType[SuccessResponseV2](timeout)

      responderManager ! ResourceHistoryEventsGetRequestV2(
        resourceIri = resourceIri,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )
      val response: ResourceAndValueVersionHistoryResponseV2 =
        expectMsgType[ResourceAndValueVersionHistoryResponseV2](timeout)
      val events: Seq[ResourceAndValueHistoryEvent] = response.historyEvents
      events.size should be(12)
      val deleteValueEvent: Option[ResourceAndValueHistoryEvent] =
        events.find(
          event =>
            event.eventType == ResourceAndValueEventsUtil.DELETE_VALUE_EVENT && event.eventBody
              .asInstanceOf[ValueEventBody]
              .valueIri == valueToDelete)
      assert(deleteValueEvent.isDefined)
    }

    "return full history of a deleted resource" in {
      val resourceIri = "http://rdfh.ch/resources/PHbbrEsVR32q5D_ioKt6pA"

      responderManager ! ResourceHistoryEventsGetRequestV2(
        resourceIri = resourceIri,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )

      val response: ResourceAndValueVersionHistoryResponseV2 =
        expectMsgType[ResourceAndValueVersionHistoryResponseV2](timeout)
      val events: Seq[ResourceAndValueHistoryEvent] = response.historyEvents
      events.size should be(2)
      val deleteResourceEvent: Option[ResourceAndValueHistoryEvent] =
        events.find(event => event.eventType == ResourceAndValueEventsUtil.DELETE_RESOURCE_EVENT)
      assert(deleteResourceEvent.isDefined)
      val deletionInfo = deleteResourceEvent.get.eventBody.asInstanceOf[ResourceEventBody].deletionInfo.get
      deletionInfo.maybeDeleteComment should be(Some("a comment for the deleted thing."))
    }

    "update resource's metadata to test update resource metadata event" in {
      val resourceIri = "http://rdfh.ch/resources/qpW9FkcgiCEC_AabCrYDYQ"
      responderManager ! UpdateResourceMetadataRequestV2(
        resourceIri = resourceIri,
        resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        maybeLabel = Some("a new label"),
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile,
        apiRequestID = UUID.randomUUID
      )

      expectMsgType[SuccessResponseV2](timeout)

      responderManager ! ResourceHistoryEventsGetRequestV2(
        resourceIri = resourceIri,
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = anythingUserProfile
      )
      val response: ResourceAndValueVersionHistoryResponseV2 =
        expectMsgType[ResourceAndValueVersionHistoryResponseV2](timeout)
      val events: Seq[ResourceAndValueHistoryEvent] = response.historyEvents
      events.size should be(2)
      val updateMetadataEvent: Option[ResourceAndValueHistoryEvent] =
        events.find(event => event.eventType == ResourceAndValueEventsUtil.UPDATE_RESOURCE_METADATA_EVENT)
      assert(updateMetadataEvent.isDefined)
    }

    "not return resources of a project which does not exist" in {

      responderManager ! ProjectResourcesWithHistoryGetRequestV2(
        projectIri = "http://rdfh.ch/projects/1111",
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.anythingAdminUser
      )
      expectMsgPF(timeout) {
        case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
      }
    }

    "return seq of full history events for each resource of a project" in {
      responderManager ! ProjectResourcesWithHistoryGetRequestV2(
        projectIri = "http://rdfh.ch/projects/0001",
        featureFactoryConfig = defaultFeatureFactoryConfig,
        requestingUser = SharedTestDataADM.anythingAdminUser
      )
      val response: ResourceAndValueVersionHistoryResponseV2 =
        expectMsgType[ResourceAndValueVersionHistoryResponseV2](timeout)
      response.historyEvents.size should be > 1

    }
  }
}
