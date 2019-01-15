/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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
import java.util.UUID

import akka.actor.{ActorRef, Props}
import akka.testkit.{ImplicitSender, TestActorRef}
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetMappingRequestV2, GetMappingResponseV2, MappingXMLtoStandoff, StandoffDataTypeClasses}
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.responders.SIPI_ROUTER_V2_ACTOR_NAME
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.twirl.{StandoffTagIriAttributeV2, StandoffTagV2}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.date.{CalendarNameGregorian, DatePrecisionYear}
import org.knora.webapi.util.{KnoraIdUtil, PermissionUtilADM, SmartIri, StringFormatter}
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.Diff

import scala.concurrent.duration._

object ResourcesResponderV2Spec {
    private val incunabulaUserProfile = SharedTestDataADM.incunabulaProjectAdminUser

    private val anythingUserProfile = SharedTestDataADM.anythingUser2

    private val defaultAnythingResourcePermissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"
    private val defaultAnythingValuePermissions = defaultAnythingResourcePermissions
    private val defaultStillImageFileValuePermissions = "M knora-base:Creator,knora-base:ProjectMember|V knora-base:KnownUser|RV knora-base:UnknownUser"

    private val zeitglöckleinIri = "http://rdfh.ch/c5058f3a"

    private val aThingIri = "http://rdfh.ch/0001/a-thing"
    private var aThingLastModificationDate = Instant.now

    private val sampleStandoff: Vector[StandoffTagV2] = Vector(
        StandoffTagV2(
            standoffTagClassIri = OntologyConstants.Standoff.StandoffRootTag,
            startPosition = 0,
            endPosition = 26,
            uuid = UUID.randomUUID().toString,
            originalXMLID = None,
            startIndex = 0
        ),
        StandoffTagV2(
            standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag,
            startPosition = 0,
            endPosition = 12,
            uuid = UUID.randomUUID().toString,
            originalXMLID = None,
            startIndex = 1,
            startParentIndex = Some(0)
        ),
        StandoffTagV2(
            standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag,
            startPosition = 0,
            endPosition = 7,
            uuid = UUID.randomUUID().toString,
            originalXMLID = None,
            startIndex = 2,
            startParentIndex = Some(1)
        )
    )
}

class GraphTestData {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val graphForAnythingUser1 = GraphDataGetResponseV2(
        edges = Vector(
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/7uuGcnFcQJq08dMOralyCQ",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/sHCLAGg-R5qJ6oPZPV-zOQ"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/MiBwAFcxQZGHNL-WfgFAPQ"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/start"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/L5xU7Qe5QUu6Wz3cDaCxbA"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/start",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/sHCLAGg-R5qJ6oPZPV-zOQ",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/start"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/A67ka6UQRHWf313tbhQBjw",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/MiBwAFcxQZGHNL-WfgFAPQ",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/LOV-6aLYQFW15jwdyS51Yw"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/start",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/start"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/L5xU7Qe5QUu6Wz3cDaCxbA",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
            )
        ),
        nodes = Vector(
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Tango",
                resourceIri = "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Foxtrot",
                resourceIri = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Echo",
                resourceIri = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Golf",
                resourceIri = "http://rdfh.ch/0001/sHCLAGg-R5qJ6oPZPV-zOQ"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Whiskey",
                resourceIri = "http://rdfh.ch/0001/MiBwAFcxQZGHNL-WfgFAPQ"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Mike",
                resourceIri = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "X-ray",
                resourceIri = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Uniform",
                resourceIri = "http://rdfh.ch/0001/LOV-6aLYQFW15jwdyS51Yw"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Sierra",
                resourceIri = "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Romeo",
                resourceIri = "http://rdfh.ch/0001/start"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Quebec",
                resourceIri = "http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Hotel",
                resourceIri = "http://rdfh.ch/0001/7uuGcnFcQJq08dMOralyCQ"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Papa",
                resourceIri = "http://rdfh.ch/0001/L5xU7Qe5QUu6Wz3cDaCxbA"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Victor",
                resourceIri = "http://rdfh.ch/0001/A67ka6UQRHWf313tbhQBjw"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Delta",
                resourceIri = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"
            )
        ),
        ontologySchema = InternalSchema
    )

    val graphForIncunabulaUser = GraphDataGetResponseV2(
        edges = Vector(
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/MiBwAFcxQZGHNL-WfgFAPQ"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/start"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/L5xU7Qe5QUu6Wz3cDaCxbA"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/start",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/sHCLAGg-R5qJ6oPZPV-zOQ",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/start"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/A67ka6UQRHWf313tbhQBjw",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/MiBwAFcxQZGHNL-WfgFAPQ",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/LOV-6aLYQFW15jwdyS51Yw"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/start",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A"
            ),
            GraphEdgeV2(
                target = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
                source = "http://rdfh.ch/0001/start"
            )
        ),
        nodes = Vector(
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Tango",
                resourceIri = "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Foxtrot",
                resourceIri = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Echo",
                resourceIri = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Golf",
                resourceIri = "http://rdfh.ch/0001/sHCLAGg-R5qJ6oPZPV-zOQ"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Whiskey",
                resourceIri = "http://rdfh.ch/0001/MiBwAFcxQZGHNL-WfgFAPQ"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "X-ray",
                resourceIri = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Uniform",
                resourceIri = "http://rdfh.ch/0001/LOV-6aLYQFW15jwdyS51Yw"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Sierra",
                resourceIri = "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Romeo",
                resourceIri = "http://rdfh.ch/0001/start"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Quebec",
                resourceIri = "http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Papa",
                resourceIri = "http://rdfh.ch/0001/L5xU7Qe5QUu6Wz3cDaCxbA"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Victor",
                resourceIri = "http://rdfh.ch/0001/A67ka6UQRHWf313tbhQBjw"
            )
        ),
        ontologySchema = InternalSchema
    )

    val graphWithStandoffLink = GraphDataGetResponseV2(
        edges = Vector(GraphEdgeV2(
            target = "http://rdfh.ch/0001/a-thing",
            propertyIri = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo".toSmartIri,
            source = "http://rdfh.ch/0001/a-thing-with-text-values"
        )),
        nodes = Vector(
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "Ein Ding f\u00FCr jemanden, dem die Dinge gefallen",
                resourceIri = "http://rdfh.ch/0001/a-thing-with-text-values"
            ),
            GraphNodeV2(
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
                resourceLabel = "A thing",
                resourceIri = "http://rdfh.ch/0001/a-thing"
            )
        ),
        ontologySchema = InternalSchema
    )

    val graphWithOneNode = GraphDataGetResponseV2(
        edges = Nil,
        nodes = Vector(GraphNodeV2(
            resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
            resourceLabel = "Another thing",
            resourceIri = "http://rdfh.ch/0001/another-thing"
        )),
        ontologySchema = InternalSchema
    )
}

/**
  * Tests [[ResourcesResponderV2]].
  */
class ResourcesResponderV2Spec extends CoreSpec() with ImplicitSender {

    import ResourcesResponderV2Spec._

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[ResourcesResponderV2]
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
    private val resourcesResponderV2SpecFullData = new ResourcesResponderV2SpecFullData
    private val knoraIdUtil = new KnoraIdUtil

    private var standardMapping: Option[MappingXMLtoStandoff] = None

    private val graphTestData = new GraphTestData

    override lazy val mockResponders: Map[String, ActorRef] = Map(SIPI_ROUTER_V2_ACTOR_NAME -> system.actorOf(Props(new MockSipiResponderV2)))

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    private def getResource(resourceIri: IRI, requestingUser: UserADM): ReadResourceV2 = {
        actorUnderTest ! ResourcesGetRequestV2(resourceIris = Seq(resourceIri), requestingUser = anythingUserProfile)

        expectMsgPF(timeout) {
            case response: ReadResourcesSequenceV2 =>
                resourcesSequenceToResource(
                    requestedresourceIri = resourceIri,
                    readResourcesSequence = response,
                    requestingUser = anythingUserProfile
                )
        }
    }

    private def resourcesSequenceToResource(requestedresourceIri: IRI, readResourcesSequence: ReadResourcesSequenceV2, requestingUser: UserADM): ReadResourceV2 = {
        if (readResourcesSequence.numberOfResources == 0) {
            throw AssertionException(s"Expected one resource, <$requestedresourceIri>, but no resources were returned")
        }

        if (readResourcesSequence.numberOfResources > 1) {
            throw AssertionException(s"More than one resource returned with IRI <$requestedresourceIri>")
        }

        val resourceInfo = readResourcesSequence.resources.head

        if (resourceInfo.resourceIri == SearchResponderV2Constants.forbiddenResourceIri) {
            throw ForbiddenException(s"User ${requestingUser.email} does not have permission to view resource <${resourceInfo.resourceIri}>")
        }

        resourceInfo.toOntologySchema(ApiV2WithValueObjects)
    }

    private def checkCreateResource(inputResource: CreateResourceV2,
                                    outputResource: ReadResourceV2,
                                    defaultResourcePermissions: String,
                                    defaultValuePermissions: String,
                                    requestingUser: UserADM): Unit = {
        assert(outputResource.resourceIri == inputResource.resourceIri)
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

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    "Load test data" in {
        responderManager ! GetMappingRequestV2(mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping", requestingUser = KnoraSystemInstances.Users.SystemUser)

        expectMsgPF(timeout) {
            case mappingResponse: GetMappingResponseV2 =>
                standardMapping = Some(mappingResponse.mapping)
        }
    }

    "The resources responder v2" should {
        "return a full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/c5058f3a"), incunabulaUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein, received = response)
            }

        }

        "return a preview descriptions of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {

            actorUnderTest ! ResourcesPreviewGetRequestV2(Seq("http://rdfh.ch/c5058f3a"), incunabulaUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedPreviewResourceResponseForZeitgloecklein, received = response)
            }

        }

        "return a full description of the book 'Reise ins Heilige Land' in the Incunabula test data" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/2a6221216701"), incunabulaUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForReise, received = response)
            }

        }

        "return two full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/c5058f3a", "http://rdfh.ch/2a6221216701"), incunabulaUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloeckleinAndReise, received = response)
            }

        }

        "return two preview descriptions of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data" in {

            actorUnderTest ! ResourcesPreviewGetRequestV2(Seq("http://rdfh.ch/c5058f3a", "http://rdfh.ch/2a6221216701"), incunabulaUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedPreviewResourceResponseForZeitgloeckleinAndReise, received = response)
            }

        }

        "return two full description of the 'Reise ins Heilige Land' and the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data (inversed order)" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/2a6221216701", "http://rdfh.ch/c5058f3a"), incunabulaUserProfile)

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForReiseAndZeitgloeckleinInversedOrder, received = response)
            }

        }

        "return two full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data providing redundant resource Iris" in {

            actorUnderTest ! ResourcesGetRequestV2(Seq("http://rdfh.ch/c5058f3a", "http://rdfh.ch/c5058f3a", "http://rdfh.ch/2a6221216701"), incunabulaUserProfile)

            // the redundant Iri should be ignored (distinct)
            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    compareReadResourcesSequenceV2Response(expected = resourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloeckleinAndReise, received = response)
            }

        }

        "return a resource of type thing with text as TEI/XML" in {

            actorUnderTest ! ResourceTEIGetRequestV2(resourceIri = "http://rdfh.ch/0001/thing_with_richtext_with_markup", textProperty = "http://www.knora.org/ontology/0001/anything#hasRichtext".toSmartIri, mappingIri = None, gravsearchTemplateIri = None, headerXSLTIri = None, requestingUser = anythingUserProfile)

            expectMsgPF(timeout) {
                case response: ResourceTEIGetResponseV2 =>

                    val expectedBody =
                        """<text><body><p>This is a test that contains marked up elements. This is <hi rend="italic">interesting text</hi> in italics. This is <hi rend="italic">boring text</hi> in italics.</p></body></text>""".stripMargin

                    // Compare the original XML with the regenerated XML.
                    val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(response.body.toXML)).withTest(Input.fromString(expectedBody)).build()

                    xmlDiff.hasDifferences should be(false)
            }

        }

        "return a resource of type Something with text with standoff as TEI/XML" in {

            actorUnderTest ! ResourceTEIGetRequestV2(resourceIri = "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g", textProperty = "http://www.knora.org/ontology/0001/anything#hasRichtext".toSmartIri, mappingIri = None, gravsearchTemplateIri = None, headerXSLTIri = None, requestingUser = anythingUserProfile)

            expectMsgPF(timeout) {
                case response: ResourceTEIGetResponseV2 =>

                    val expectedBody =
                        """<text><body><p><hi rend="bold">Something</hi> <hi rend="italic">with</hi> a <del>lot</del> of <hi rend="underline">different</hi> <hi rend="sup">markup</hi>. And more <ref target="http://www.google.ch">markup</ref>.</p></body></text>""".stripMargin

                    // Compare the original XML with the regenerated XML.
                    val xmlDiff: Diff = DiffBuilder.compare(Input.fromString(response.body.toXML)).withTest(Input.fromString(expectedBody)).build()

                    xmlDiff.hasDifferences should be(false)
            }

        }

        "return a graph of resources reachable via links from/to a given resource" in {
            actorUnderTest ! GraphDataGetRequestV2(
                resourceIri = "http://rdfh.ch/0001/start",
                depth = 6,
                inbound = true,
                outbound = true,
                excludeProperty = Some(OntologyConstants.KnoraApiV2WithValueObjects.IsPartOf.toSmartIri),
                requestingUser = SharedTestDataADM.anythingUser1
            )

            val response = expectMsgType[GraphDataGetResponseV2](timeout)
            val edges = response.edges
            val nodes = response.nodes

            edges should contain theSameElementsAs graphTestData.graphForAnythingUser1.edges
            nodes should contain theSameElementsAs graphTestData.graphForAnythingUser1.nodes
        }

        "return a graph of resources reachable via links from/to a given resource, filtering the results according to the user's permissions" in {
            actorUnderTest ! GraphDataGetRequestV2(
                resourceIri = "http://rdfh.ch/0001/start",
                depth = 6,
                inbound = true,
                outbound = true,
                excludeProperty = Some(OntologyConstants.KnoraApiV2WithValueObjects.IsPartOf.toSmartIri),
                requestingUser = SharedTestDataADM.incunabulaProjectAdminUser
            )

            val response = expectMsgType[GraphDataGetResponseV2](timeout)
            val edges = response.edges
            val nodes = response.nodes

            edges should contain theSameElementsAs graphTestData.graphForIncunabulaUser.edges
            nodes should contain theSameElementsAs graphTestData.graphForIncunabulaUser.nodes
        }

        "return a graph containing a standoff link" in {
            actorUnderTest ! GraphDataGetRequestV2(
                resourceIri = "http://rdfh.ch/0001/a-thing",
                depth = 4,
                inbound = true,
                outbound = true,
                excludeProperty = Some(OntologyConstants.KnoraApiV2WithValueObjects.IsPartOf.toSmartIri),
                requestingUser = SharedTestDataADM.anythingUser1
            )

            expectMsgPF(timeout) {
                case response: GraphDataGetResponseV2 => response should ===(graphTestData.graphWithStandoffLink)
            }
        }

        "return a graph containing just one node" in {
            actorUnderTest ! GraphDataGetRequestV2(
                resourceIri = "http://rdfh.ch/0001/another-thing",
                depth = 4,
                inbound = true,
                outbound = true,
                excludeProperty = Some(OntologyConstants.KnoraApiV2WithValueObjects.IsPartOf.toSmartIri),
                requestingUser = SharedTestDataADM.anythingUser1
            )

            expectMsgPF(timeout) {
                case response: GraphDataGetResponseV2 => response should ===(graphTestData.graphWithOneNode)
            }
        }

        "create a resource with no values" in {
            // Create the resource.

            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "test thing",
                values = Map.empty,
                projectADM = SharedTestDataADM.anythingProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            // Check that the response contains the correct metadata.

            expectMsgPF(timeout) {
                case response: ReadResourcesSequenceV2 =>
                    val outputResource: ReadResourceV2 = resourcesSequenceToResource(
                        requestedresourceIri = resourceIri,
                        readResourcesSequence = response,
                        requestingUser = anythingUserProfile
                    )

                    checkCreateResource(
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
                inputResource = inputResource,
                outputResource = outputResource,
                defaultResourcePermissions = defaultAnythingResourcePermissions,
                defaultValuePermissions = defaultAnythingValuePermissions,
                requestingUser = anythingUserProfile
            )
        }

        "create a resource with no values and custom permissions" in {
            // Create the resource.

            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "test thing",
                values = Map.empty,
                projectADM = SharedTestDataADM.anythingProject,
                permissions = Some("CR knora-base:Creator|V http://rdfh.ch/groups/0001/thing-searcher")
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgType[ReadResourcesSequenceV2]

            // Get the resource from the triplestore and check it.

            val outputResource = getResource(resourceIri, anythingUserProfile)

            checkCreateResource(
                inputResource = inputResource,
                outputResource = outputResource,
                defaultResourcePermissions = defaultAnythingResourcePermissions,
                defaultValuePermissions = defaultAnythingValuePermissions,
                requestingUser = anythingUserProfile
            )
        }

        "create a resource with values" in {
            // Create the resource.

            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = IntegerValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasInteger = 5,
                            comment = Some("this is the number five")
                        ),
                        permissions = Some("CR knora-base:Creator|V http://rdfh.ch/groups/0001/thing-searcher")
                    ),
                    CreateValueInNewResourceV2(
                        valueContent = IntegerValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasInteger = 6
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "this is text without standoff"
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "this is text with standoff",
                            standoffAndMapping = Some(StandoffAndMapping(
                                standoff = sampleStandoff,
                                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                                mapping = standardMapping.get
                            ))
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = DecimalValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasDecimal = BigDecimal("100000000000000.000000000000001")
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = DateValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
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
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasBoolean = true
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = GeomValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasGeometry = """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}"""
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = IntervalValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasIntervalStart = BigDecimal("1.2"),
                            valueHasIntervalEnd = BigDecimal("3.4")
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = HierarchicalListValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasListNode = "http://rdfh.ch/lists/0001/treeList03"
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = ColorValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasColor = "#ff3333"
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = UriValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasUri = "https://www.knora.org"
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = GeonameValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasGeonameCode = "2661604"
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = LinkValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            referredResourceIri = "http://rdfh.ch/0001/a-thing"
                        )
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "test thing",
                values = inputValues,
                projectADM = SharedTestDataADM.anythingProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgType[ReadResourcesSequenceV2]

            // Get the resource from the triplestore and check it.

            val outputResource = getResource(resourceIri, anythingUserProfile)

            checkCreateResource(
                inputResource = inputResource,
                outputResource = outputResource,
                defaultResourcePermissions = defaultAnythingResourcePermissions,
                defaultValuePermissions = defaultAnythingValuePermissions,
                requestingUser = anythingUserProfile
            )
        }

        "create a resource with a still image file value" in {
            // Create the resource.

            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                OntologyConstants.KnoraApiV2WithValueObjects.HasStillImageFileValue.toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = StillImageFileValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            fileValue = FileValueV2(
                                internalFilename = "IQUO3t1AABm-FSLC0vNvVpr.jp2",
                                internalMimeType = "image/jp2",
                                originalFilename = "test.tiff",
                                originalMimeType = "image/tiff"
                            ),
                            dimX = 512,
                            dimY = 256
                        )
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#ThingPicture".toSmartIri,
                label = "test thing picture",
                values = inputValues,
                projectADM = SharedTestDataADM.anythingProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgType[ReadResourcesSequenceV2]

            // Get the resource from the triplestore and check it.

            val outputResource = getResource(resourceIri, anythingUserProfile)

            checkCreateResource(
                inputResource = inputResource,
                outputResource = outputResource,
                defaultResourcePermissions = defaultAnythingResourcePermissions,
                defaultValuePermissions = defaultStillImageFileValuePermissions,
                requestingUser = anythingUserProfile
            )
        }

        "not create a resource with missing required values" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.incunabulaProject.shortcode)

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                label = "invalid book",
                values = Map.empty,
                projectADM = SharedTestDataADM.incunabulaProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = incunabulaUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "not create a resource with too many values for the cardinality of a property" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.incunabulaProject.shortcode)

            val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "test title"
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0803/incunabula/v2#publoc".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "test publoc 1"
                        )
                    ),
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "test publoc 2"
                        )
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                label = "invalid book",
                values = inputValues,
                projectADM = SharedTestDataADM.incunabulaProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = incunabulaUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "not create a resource with a property for which there is no cardinality in the resource class" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.incunabulaProject.shortcode)

            val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "test title"
                        )
                    )
                ),
                "http://0.0.0.0:3333/ontology/0803/incunabula/v2#pagenum".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "test pagenum"
                        )
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                label = "invalid book",
                values = inputValues,
                projectADM = SharedTestDataADM.incunabulaProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = incunabulaUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "not create a resource with duplicate values" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.incunabulaProject.shortcode)

            val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "test title 1"
                        )
                    ),
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "test title 2"
                        )
                    ),
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "test title 1"
                        )
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                label = "invalid book",
                values = inputValues,
                projectADM = SharedTestDataADM.incunabulaProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = incunabulaUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[DuplicateValueException] should ===(true)
            }
        }

        "not create a resource if the user doesn't have permission to create resources in the project" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.incunabulaProject.shortcode)

            val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "test title"
                        )
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
                label = "invalid book",
                values = inputValues,
                projectADM = SharedTestDataADM.incunabulaProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "not create a resource with a link to a nonexistent other resource" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = LinkValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            referredResourceIri = "http://rdfh.ch/0001/nonexistent-thing"
                        )
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "invalid thing",
                values = inputValues,
                projectADM = SharedTestDataADM.anythingProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not create a resource with a standoff link to a nonexistent other resource" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val standoffWithInvalidLink: Vector[StandoffTagV2] = Vector(
                StandoffTagV2(
                    standoffTagClassIri = OntologyConstants.Standoff.StandoffRootTag,
                    startPosition = 0,
                    endPosition = 26,
                    uuid = UUID.randomUUID().toString,
                    originalXMLID = None,
                    startIndex = 0
                ),
                StandoffTagV2(
                    standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag,
                    dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                    startPosition = 0,
                    endPosition = 12,
                    uuid = UUID.randomUUID().toString,
                    originalXMLID = None,
                    startIndex = 1,
                    attributes = Vector(StandoffTagIriAttributeV2(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink, value = "http://rdfh.ch/0001/nonexistent-thing")),
                    startParentIndex = Some(0)
                ),
                StandoffTagV2(
                    standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag,
                    startPosition = 0,
                    endPosition = 7,
                    uuid = UUID.randomUUID().toString,
                    originalXMLID = None,
                    startIndex = 2,
                    startParentIndex = Some(1)
                )
            )

            val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "this is text with standoff",
                            standoffAndMapping = Some(StandoffAndMapping(
                                standoff = standoffWithInvalidLink,
                                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                                mapping = standardMapping.get
                            ))
                        )
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "invalid thing",
                values = inputValues,
                projectADM = SharedTestDataADM.anythingProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not create a resource with a list value referring to a nonexistent list node" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = HierarchicalListValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasListNode = "http://rdfh.ch/lists/0001/nonexistent-list-node"
                        )
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "invalid thing",
                values = inputValues,
                projectADM = SharedTestDataADM.anythingProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "not create a resource with a value that's the wrong type for the property" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = TextValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasString = "invalid text value"
                        )
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "invalid thing",
                values = inputValues,
                projectADM = SharedTestDataADM.anythingProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "not create a resource with a link to a resource of the wrong class for the link property" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = LinkValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            referredResourceIri = zeitglöckleinIri
                        )
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "invalid thing",
                values = inputValues,
                projectADM = SharedTestDataADM.anythingProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "not create a resource with invalid custom permissions" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "invalid thing",
                values = Map.empty,
                projectADM = SharedTestDataADM.anythingProject,
                permissions = Some("M knora-base:Creator,V knora-base:KnownUser")
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a resource with a value that has invalid custom permissions" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.anythingProject.shortcode)

            val values: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
                "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri -> Seq(
                    CreateValueInNewResourceV2(
                        valueContent = IntegerValueContentV2(
                            ontologySchema = ApiV2WithValueObjects,
                            valueHasInteger = 5,
                            comment = Some("this is the number five")
                        ),
                        permissions = Some("M knora-base:Creator,V knora-base:KnownUser")
                    )
                )
            )

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "invalid thing",
                values = values,
                projectADM = SharedTestDataADM.anythingProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "not create a resource that uses a class from another non-shared project" in {
            val resourceIri: IRI = knoraIdUtil.makeRandomResourceIri(SharedTestDataADM.incunabulaProject.shortcode)

            val inputResource = CreateResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                label = "test thing",
                values = Map.empty,
                projectADM = SharedTestDataADM.incunabulaProject
            )

            actorUnderTest ! CreateResourceRequestV2(
                createResource = inputResource,
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
                requestingUser = incunabulaUserProfile,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! updateRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[ForbiddenException] should ===(true)
            }
        }

        "not update a resource's metadata if the user does not supply the correct resource class" in {
            val updateRequest = UpdateResourceMetadataRequestV2(
                resourceIri = aThingIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#BlueThing".toSmartIri,
                maybeLabel = Some("new test label"),
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! updateRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "update a resource's metadata when it doesn't have a knora-base:lastModificationDate" in {
            val dateTimeStampBeforeUpdate = Instant.now
            val newLabel = "new test label"
            val newPermissions = "CR knora-base:Creator|M knora-base:ProjectMember|V knora-base:ProjectMember"

            val updateRequest = UpdateResourceMetadataRequestV2(
                resourceIri = aThingIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                maybeLabel = Some(newLabel),
                maybePermissions = Some(newPermissions),
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! updateRequest

            expectMsgType[SuccessResponseV2]

            // Get the resource from the triplestore and check it.

            val outputResource: ReadResourceV2 = getResource(aThingIri, anythingUserProfile)
            assert(outputResource.label == newLabel)
            assert(PermissionUtilADM.parsePermissions(outputResource.permissions) == PermissionUtilADM.parsePermissions(newPermissions))
            aThingLastModificationDate = outputResource.lastModificationDate.get
            assert(aThingLastModificationDate.isAfter(dateTimeStampBeforeUpdate))
        }

        "not update a resource's metadata if its knora-base:lastModificationDate exists but is not submitted" in {
            val updateRequest = UpdateResourceMetadataRequestV2(
                resourceIri = aThingIri,
                resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                maybeLabel = Some("another new test label"),
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! updateRequest

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
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! updateRequest

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
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! updateRequest

            expectMsgType[SuccessResponseV2]

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
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! updateRequest

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
                requestingUser = anythingUserProfile,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! updateRequest

            expectMsgType[SuccessResponseV2]

            // Get the resource from the triplestore and check it.

            val outputResource: ReadResourceV2 = getResource(aThingIri, anythingUserProfile)
            val updatedLastModificationDate = outputResource.lastModificationDate.get
            assert(updatedLastModificationDate == newModificationDate)
            aThingLastModificationDate = updatedLastModificationDate
        }
    }
}
