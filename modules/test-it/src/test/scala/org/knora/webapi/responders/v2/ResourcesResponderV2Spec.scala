/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff
import zio.*
import zio.test.*
import zio.test.Assertion.*

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.UUID.*

import dsp.errors.*
import dsp.valueobjects.UuidUtil
import org.knora.webapi.*
import org.knora.webapi.E2EZSpec.failsWithMessageEqualTo
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.CalendarNameGregorian
import org.knora.webapi.messages.util.DatePrecisionYear
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages.*
import org.knora.webapi.messages.v2.responder.standoffmessages.*
import org.knora.webapi.messages.v2.responder.valuemessages.*
import org.knora.webapi.models.filemodels.*
import org.knora.webapi.responders.v2.ResourcesResponseCheckerV2.compareReadResourcesSequenceV2Response
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.KnoraIris.ResourceIri
import org.knora.webapi.slice.common.KnoraIris.ValueIri
import org.knora.webapi.slice.resources.IiifImageRequestUrl
import org.knora.webapi.slice.resources.api.model.GraphDirection
import org.knora.webapi.slice.resources.api.model.VersionDate
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.util.*

object GraphTestData {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  val graphForAnythingUser1: GraphDataGetResponseV2 = GraphDataGetResponseV2(
    edges = Vector(
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/7uuGcnFcQJq08dMOralyCQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/sHCLAGg-R5qJ6oPZPV-zOQ",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/MiBwAFcxQZGHNL-WfgFAPQ",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/start",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/L5xU7Qe5QUu6Wz3cDaCxbA",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/start",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/sHCLAGg-R5qJ6oPZPV-zOQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/start",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/A67ka6UQRHWf313tbhQBjw",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/MiBwAFcxQZGHNL-WfgFAPQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/LOV-6aLYQFW15jwdyS51Yw",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/start",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/start",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/L5xU7Qe5QUu6Wz3cDaCxbA",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA",
      ),
    ),
    nodes = Vector(
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Tango",
        resourceIri = "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Foxtrot",
        resourceIri = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Echo",
        resourceIri = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Golf",
        resourceIri = "http://rdfh.ch/0001/sHCLAGg-R5qJ6oPZPV-zOQ",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Whiskey",
        resourceIri = "http://rdfh.ch/0001/MiBwAFcxQZGHNL-WfgFAPQ",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Mike",
        resourceIri = "http://rdfh.ch/0001/cmfk1DMHRBiR4-_6HXpEFA",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "X-ray",
        resourceIri = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Uniform",
        resourceIri = "http://rdfh.ch/0001/LOV-6aLYQFW15jwdyS51Yw",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Sierra",
        resourceIri = "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Romeo",
        resourceIri = "http://rdfh.ch/0001/start",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Quebec",
        resourceIri = "http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Hotel",
        resourceIri = "http://rdfh.ch/0001/7uuGcnFcQJq08dMOralyCQ",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Papa",
        resourceIri = "http://rdfh.ch/0001/L5xU7Qe5QUu6Wz3cDaCxbA",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Victor",
        resourceIri = "http://rdfh.ch/0001/A67ka6UQRHWf313tbhQBjw",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Delta",
        resourceIri = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA",
      ),
    ),
    ontologySchema = InternalSchema,
  )

  val graphForIncunabulaUser: GraphDataGetResponseV2 = GraphDataGetResponseV2(
    edges = Vector(
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/MiBwAFcxQZGHNL-WfgFAPQ",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/start",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/L5xU7Qe5QUu6Wz3cDaCxbA",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/start",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/sHCLAGg-R5qJ6oPZPV-zOQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/start",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/A67ka6UQRHWf313tbhQBjw",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/MiBwAFcxQZGHNL-WfgFAPQ",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/LOV-6aLYQFW15jwdyS51Yw",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/start",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
      ),
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
        propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
        source = "http://rdfh.ch/0001/start",
      ),
    ),
    nodes = Vector(
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Tango",
        resourceIri = "http://rdfh.ch/0001/WLSHxQUgTOmG1T0lBU2r5w",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Foxtrot",
        resourceIri = "http://rdfh.ch/0001/Lz7WEqJETJqqsUZQYexBQg",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Echo",
        resourceIri = "http://rdfh.ch/0001/tPfZeNMvRVujCQqbIbvO0A",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Golf",
        resourceIri = "http://rdfh.ch/0001/sHCLAGg-R5qJ6oPZPV-zOQ",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Whiskey",
        resourceIri = "http://rdfh.ch/0001/MiBwAFcxQZGHNL-WfgFAPQ",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "X-ray",
        resourceIri = "http://rdfh.ch/0001/nResNuvARcWYUdWyo0GWGw",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Uniform",
        resourceIri = "http://rdfh.ch/0001/LOV-6aLYQFW15jwdyS51Yw",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Sierra",
        resourceIri = "http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Romeo",
        resourceIri = "http://rdfh.ch/0001/start",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Quebec",
        resourceIri = "http://rdfh.ch/0001/iqW_PBiHRdyTFzik8tuSog",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Papa",
        resourceIri = "http://rdfh.ch/0001/L5xU7Qe5QUu6Wz3cDaCxbA",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Victor",
        resourceIri = "http://rdfh.ch/0001/A67ka6UQRHWf313tbhQBjw",
      ),
    ),
    ontologySchema = InternalSchema,
  )

  val graphWithStandoffLink: GraphDataGetResponseV2 = GraphDataGetResponseV2(
    edges = Vector(
      GraphEdgeV2(
        target = "http://rdfh.ch/0001/a-thing",
        propertyIri = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo".toSmartIri,
        source = "http://rdfh.ch/0001/a-thing-with-text-values",
      ),
    ),
    nodes = Vector(
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Ein Ding f\u00FCr jemanden, dem die Dinge gefallen",
        resourceIri = "http://rdfh.ch/0001/a-thing-with-text-values",
      ),
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "A thing",
        resourceIri = "http://rdfh.ch/0001/a-thing",
      ),
    ),
    ontologySchema = InternalSchema,
  )

  val graphWithOneNode: GraphDataGetResponseV2 = GraphDataGetResponseV2(
    edges = Nil,
    nodes = Vector(
      GraphNodeV2(
        resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
        resourceLabel = "Another thing",
        resourceIri = "http://rdfh.ch/0001/another-thing",
      ),
    ),
    ontologySchema = InternalSchema,
  )
}

object ResourcesResponderV2Spec extends E2EZSpec { self =>

  private val resourceResponder = ZIO.serviceWithZIO[ResourcesResponderV2]

  private val defaultAnythingResourcePermissions =
    "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:KnownUser|RV knora-admin:UnknownUser"
  private val defaultAnythingValuePermissions       = defaultAnythingResourcePermissions
  private val defaultStillImageFileValuePermissions = defaultAnythingResourcePermissions
  private val zeitgloeckleinIri                     = "http://rdfh.ch/0803/c5058f3a"
  private val aThingIri                             = "http://rdfh.ch/0001/a-thing"
  private var aThingLastModificationDate            = Instant.now

  private val resourceIriToErase                            = new MutableTestIri
  private val firstValueIriToErase                          = new MutableTestIri
  private val secondValueIriToErase                         = new MutableTestIri
  private val standoffTagIrisToErase                        = collection.mutable.Set.empty[IRI]
  private var resourceToEraseLastModificationDate           = Instant.now
  private var standardMapping: Option[MappingXMLtoStandoff] = None

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(path = "test_data/project_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
  )

  private val sampleStandoff: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffRootTag.toSmartIri,
      startPosition = 0,
      endPosition = 26,
      uuid = randomUUID,
      originalXMLID = None,
      startIndex = 0,
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 12,
      uuid = randomUUID,
      originalXMLID = None,
      startIndex = 1,
      startParentIndex = Some(0),
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
      startPosition = 0,
      endPosition = 7,
      uuid = randomUUID,
      originalXMLID = None,
      startIndex = 2,
      startParentIndex = Some(1),
    ),
  )

  private val sampleStandoffForErasingResource: Vector[StandoffTagV2] = Vector(
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffRootTag.toSmartIri,
      startPosition = 0,
      endPosition = 26,
      uuid = randomUUID,
      originalXMLID = None,
      startIndex = 0,
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffParagraphTag.toSmartIri,
      startPosition = 0,
      endPosition = 12,
      uuid = randomUUID,
      originalXMLID = None,
      startIndex = 1,
      startParentIndex = Some(0),
    ),
    StandoffTagV2(
      standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
      startPosition = 0,
      endPosition = 7,
      uuid = randomUUID,
      originalXMLID = None,
      startIndex = 2,
      startParentIndex = Some(1),
    ),
  )

  private def getResource(resourceIri: IRI) = resourceResponder(
    _.getResourcesWithDeletedResource(
      resourceIris = Seq(resourceIri),
      targetSchema = ApiV2Complex,
      schemaOptions = Set.empty,
      requestingUser = anythingUser2,
    ),
  ).map(_.toResource(resourceIri).toOntologySchema(ApiV2Complex))

  private def checkCreateResource(
    inputResourceIri: IRI,
    inputResource: CreateResourceV2,
    outputResource: ReadResourceV2,
    defaultResourcePermissions: String,
    defaultValuePermissions: String,
    requestingUser: User,
  ) = ZIO.fail("ResourceIri does not match").unless(outputResource.resourceIri == inputResourceIri) *>
    ZIO
      .fail("ResourceClassIri does not match")
      .unless(outputResource.resourceClassIri == inputResource.resourceClassIri) *>
    ZIO.fail("Label does not match").unless(outputResource.label == inputResource.label) *>
    ZIO.fail("AttachedToUser does not match").unless(outputResource.attachedToUser == requestingUser.id) *>
    ZIO.fail("ProjectId does not match").unless(outputResource.projectADM.id == inputResource.projectADM.id) *>
    ZIO
      .fail("Permissions does not match")
      .unless(outputResource.permissions == inputResource.permissions.getOrElse(defaultResourcePermissions)) *>
    ZIO.fail("Values  does not match").unless(outputResource.values.keySet == inputResource.values.keySet) *>
    ZIO
      .foreachDiscard(inputResource.values) {
        case (propertyIri: SmartIri, propertyInputValues: Seq[CreateValueInNewResourceV2]) =>
          val propertyOutputValues = outputResource.values(propertyIri)
          ZIO
            .fail(s"Property $propertyIri does not match")
            .unless(propertyOutputValues.size == propertyInputValues.size) *>
            ZIO
              .foreachDiscard(propertyInputValues.zip(propertyOutputValues)) {
                case (inputValue: CreateValueInNewResourceV2, outputValue: ReadValueV2) =>
                  val expectedPermissions = inputValue.permissions.getOrElse(defaultValuePermissions)
                  ZIO.fail("Value permissions do not match").unless(outputValue.permissions == expectedPermissions).unit
              }
      }
      .unit
      .mapError(msg => AssertionException(msg))

  private def getStandoffTagByUUID(uuid: UUID) = ZIO
    .serviceWithZIO[TriplestoreService](_.query(Select(sparql.v2.txt.getStandoffTagByUUID(uuid))))
    .map(_.getColOrThrow("standoffTag").toSet)

  override val e2eSpec = suite("ResourceResponserV2")(
    test("Load test data") {
      ZIO
        .serviceWithZIO[StandoffResponderV2](_.getMappingV2("http://rdfh.ch/standoff/mappings/StandardMapping"))
        .tap(r => ZIO.succeed(self.standardMapping = Some(r.mapping)))
        .as(assertCompletes)
    },
    suite("The resources responder v2")(
      test(
        "return a full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data",
      ) {
        for {
          response <- resourceResponder(
                        _.getResourcesWithDeletedResource(
                          resourceIris = Seq("http://rdfh.ch/0803/c5058f3a"),
                          versionDate = None,
                          targetSchema = ApiV2Complex,
                          schemaOptions = Set.empty,
                          requestingUser = incunabulaProjectAdminUser,
                        ),
                      )
          _ = compareReadResourcesSequenceV2Response(
                expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloecklein,
                received = response,
              )
        } yield assertCompletes
      },
      test(
        "return a preview descriptions of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data",
      ) {
        for {
          response <-
            resourceResponder(
              _.getResourcePreviewWithDeletedResource(
                resourceIris = Seq("http://rdfh.ch/0803/c5058f3a"),
                targetSchema = ApiV2Complex,
                requestingUser = incunabulaProjectAdminUser,
              ),
            )
          _ = compareReadResourcesSequenceV2Response(
                expected = ResourcesResponderV2SpecFullData.expectedPreviewResourceResponseForZeitgloecklein,
                received = response,
              )
        } yield assertCompletes
      },
      test("return a full description of the book 'Reise ins Heilige Land' in the Incunabula test data") {
        for {
          response <-
            resourceResponder(
              _.getResourcesWithDeletedResource(
                resourceIris = Seq("http://rdfh.ch/0803/2a6221216701"),
                versionDate = None,
                targetSchema = ApiV2Complex,
                schemaOptions = Set.empty,
                requestingUser = incunabulaProjectAdminUser,
              ),
            )
          _ = compareReadResourcesSequenceV2Response(
                expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForReise,
                received = response,
              )
        } yield assertCompletes
      },
      test(
        "return two full descriptions of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data",
      ) {
        for {
          response <- resourceResponder(
                        _.getResourcesWithDeletedResource(
                          resourceIris = Seq("http://rdfh.ch/0803/c5058f3a", "http://rdfh.ch/0803/2a6221216701"),
                          versionDate = None,
                          targetSchema = ApiV2Complex,
                          schemaOptions = Set.empty,
                          requestingUser = incunabulaProjectAdminUser,
                        ),
                      )
          _ = compareReadResourcesSequenceV2Response(
                expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloeckleinAndReise,
                received = response,
              )
        } yield assertCompletes
      },
      test(
        "return two preview descriptions of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data",
      ) {
        for {
          response <- resourceResponder(
                        _.getResourcePreviewWithDeletedResource(
                          resourceIris = Seq("http://rdfh.ch/0803/c5058f3a", "http://rdfh.ch/0803/2a6221216701"),
                          targetSchema = ApiV2Complex,
                          requestingUser = incunabulaProjectAdminUser,
                        ),
                      )
          _ = compareReadResourcesSequenceV2Response(
                expected = ResourcesResponderV2SpecFullData.expectedPreviewResourceResponseForZeitgloeckleinAndReise,
                received = response,
              )
        } yield assertCompletes
      },
      test(
        "return two full descriptions of the 'Reise ins Heilige Land' and the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data (inversed order)",
      ) {
        for {
          response <- resourceResponder(
                        _.getResourcesWithDeletedResource(
                          resourceIris = Seq("http://rdfh.ch/0803/2a6221216701", "http://rdfh.ch/0803/c5058f3a"),
                          versionDate = None,
                          targetSchema = ApiV2Complex,
                          schemaOptions = Set.empty,
                          requestingUser = incunabulaProjectAdminUser,
                        ),
                      )
          _ = compareReadResourcesSequenceV2Response(
                expected =
                  ResourcesResponderV2SpecFullData.expectedFullResourceResponseForReiseAndZeitgloeckleinInversedOrder,
                received = response,
              )
        } yield assertCompletes
      },
      test(
        "return two full descriptions of the book 'Zeitglöcklein des Lebens und Leidens Christi' and the book 'Reise ins Heilige Land' in the Incunabula test data providing redundant resource Iris",
      ) {
        for {
          response <- resourceResponder(
                        _.getResourcesWithDeletedResource(
                          resourceIris = Seq(
                            "http://rdfh.ch/0803/c5058f3a",
                            "http://rdfh.ch/0803/c5058f3a",
                            "http://rdfh.ch/0803/2a6221216701",
                          ),
                          versionDate = None,
                          targetSchema = ApiV2Complex,
                          schemaOptions = Set.empty,
                          requestingUser = incunabulaProjectAdminUser,
                        ),
                      )
          // the redundant Iri==  ignored (distinct)
          _ = compareReadResourcesSequenceV2Response(
                expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForZeitgloeckleinAndReise,
                received = response,
              )
        } yield assertCompletes
      },
      test("return a resource of type thing with text as TEI/XML") {
        for {
          response <- resourceResponder(
                        _.getResourceAsTeiV2(
                          resourceIri = "http://rdfh.ch/0001/thing_with_richtext_with_markup",
                          textProperty = "http://www.knora.org/ontology/0001/anything#hasRichtext".toSmartIri,
                          mappingIri = None,
                          gravsearchTemplateIri = None,
                          headerXSLTIri = None,
                          requestingUser = anythingUser2,
                        ),
                      )
          expectedBody =
            """<text><body><p>This is a test that contains marked up elements. This is <hi rend="italic">interesting text</hi> in italics. This is <hi rend="italic">boring text</hi> in italics.</p></body></text>""".stripMargin
          xmlDiff: Diff = // Compare the original XML with the regenerated XML.
            DiffBuilder.compare(Input.fromString(response.body.toXML)).withTest(Input.fromString(expectedBody)).build()
        } yield assertTrue(!xmlDiff.hasDifferences)
      },
      test("return a resource of type Something with text with standoff as TEI/XML") {
        for {
          response <- resourceResponder(
                        _.getResourceAsTeiV2(
                          resourceIri = "http://rdfh.ch/0001/qN1igiDRSAemBBktbRHn6g",
                          textProperty = "http://www.knora.org/ontology/0001/anything#hasRichtext".toSmartIri,
                          mappingIri = None,
                          gravsearchTemplateIri = None,
                          headerXSLTIri = None,
                          requestingUser = anythingUser2,
                        ),
                      )
          expectedBody =
            """<text><body><p><hi rend="bold">Something</hi> <hi rend="italic">with</hi> a <del>lot</del> of <hi rend="underline">different</hi> <hi rend="sup">markup</hi>. And more <ref target="http://www.google.ch">markup</ref>.</p></body></text>""".stripMargin
          xmlDiff: Diff = // Compare the original XML with the regenerated XML.
            DiffBuilder.compare(Input.fromString(response.body.toXML)).withTest(Input.fromString(expectedBody)).build()
        } yield assertTrue(!xmlDiff.hasDifferences)
      },
      test("return a past version of a resource") {
        val resourceIri = "http://rdfh.ch/0001/thing-with-history"
        val versionDate = Instant.parse("2019-02-12T08:05:10Z")
        for {
          response <-
            resourceResponder(
              _.getResourcesWithDeletedResource(
                resourceIris = Seq(resourceIri),
                versionDate = Some(VersionDate.fromInstant(versionDate)),
                targetSchema = ApiV2Complex,
                schemaOptions = Set.empty,
                requestingUser = anythingUser2,
              ),
            )
          _ = compareReadResourcesSequenceV2Response(
                expected = ResourcesResponderV2SpecFullData.expectedFullResourceResponseForThingWithHistory,
                received = response,
              )
        } yield assertCompletes
      },
      test("return the complete version history of a resource") {
        val resourceIri = "http://rdfh.ch/0001/thing-with-history"
        for {
          response <- resourceResponder(
                        _.getResourceHistoryV2(
                          ResourceVersionHistoryGetRequestV2(
                            resourceIri = resourceIri,
                            startDate = None,
                            endDate = None,
                            requestingUser = anythingUser2,
                          ),
                        ),
                      )
        } yield assertTrue(response == ResourcesResponderV2SpecFullData.expectedCompleteVersionHistoryResponse)
      },
      test("return the version history of a resource within a date range") {
        val resourceIri = "http://rdfh.ch/0001/thing-with-history"
        val startDate   = Instant.parse("2019-02-08T15:05:11Z")
        val endDate     = Instant.parse("2019-02-13T09:05:10Z")
        for {
          response <-
            resourceResponder(
              _.getResourceHistoryV2(
                ResourceVersionHistoryGetRequestV2(
                  resourceIri = resourceIri,
                  startDate = Some(startDate),
                  endDate = Some(endDate),
                  requestingUser = anythingUser2,
                ),
              ),
            )
        } yield assertTrue(response == ResourcesResponderV2SpecFullData.expectedPartialVersionHistoryResponse)
      },
      test("get the latest version of a value, given its UUID") {
        for {
          response <- resourceResponder(
                        _.getResourcesWithDeletedResource(
                          resourceIris = Seq("http://rdfh.ch/0001/thing-with-history"),
                          valueUuid = Some(UuidUtil.decode("pLlW4ODASumZfZFbJdpw1g")),
                          targetSchema = ApiV2Complex,
                          schemaOptions = Set.empty,
                          requestingUser = anythingUser2,
                        ),
                      )
          _ = compareReadResourcesSequenceV2Response(
                expected = ResourcesResponderV2SpecFullData.expectedFullResponseResponseForThingWithValueByUuid,
                received = response,
              )
        } yield assertCompletes
      },
      test("get a past version of a value, given its UUID and a timestamp") {
        for {
          response <- resourceResponder(
                        _.getResourcesWithDeletedResource(
                          resourceIris = Seq("http://rdfh.ch/0001/thing-with-history"),
                          valueUuid = Some(UuidUtil.decode("pLlW4ODASumZfZFbJdpw1g")),
                          versionDate = Some(VersionDate.fromInstant(Instant.parse("2019-02-12T09:05:10Z"))),
                          targetSchema = ApiV2Complex,
                          schemaOptions = Set.empty,
                          requestingUser = anythingUser2,
                        ),
                      )
          _ = compareReadResourcesSequenceV2Response(
                expected =
                  ResourcesResponderV2SpecFullData.expectedFullResponseResponseForThingWithValueByUuidAndVersionDate,
                received = response,
              )
        } yield assertCompletes
      },
      test("return a graph of resources reachable via links from/to a given resource") {
        for {
          response <- resourceResponder(
                        _.getGraphDataResponseV2(
                          resourceIri = "http://rdfh.ch/0001/start",
                          depth = 6,
                          GraphDirection.Both,
                          excludeProperty = Some(OntologyConstants.KnoraApiV2Complex.IsPartOf.toSmartIri),
                          requestingUser = anythingUser1,
                        ),
                      )
        } yield assert(response.edges)(hasSameElements(GraphTestData.graphForAnythingUser1.edges)) &&
          assert(response.nodes)(hasSameElements(GraphTestData.graphForAnythingUser1.nodes))
      },
      test(
        "return a graph of resources reachable via links from/to a given resource, filtering the results according to the user's permissions",
      ) {
        for {
          response <- resourceResponder(
                        _.getGraphDataResponseV2(
                          resourceIri = "http://rdfh.ch/0001/start",
                          depth = 6,
                          GraphDirection.Both,
                          excludeProperty = Some(OntologyConstants.KnoraApiV2Complex.IsPartOf.toSmartIri),
                          requestingUser = incunabulaProjectAdminUser,
                        ),
                      )
        } yield assert(response.edges)(hasSameElements(GraphTestData.graphForIncunabulaUser.edges)) &&
          assert(response.nodes)(hasSameElements(GraphTestData.graphForIncunabulaUser.nodes))
      },
      test("return a graph containing a standoff link") {
        for {
          response <-
            resourceResponder(
              _.getGraphDataResponseV2(
                resourceIri = "http://rdfh.ch/0001/a-thing",
                depth = 4,
                GraphDirection.Both,
                excludeProperty = Some(OntologyConstants.KnoraApiV2Complex.IsPartOf.toSmartIri),
                requestingUser = anythingUser1,
              ),
            )
        } yield assertTrue(response == GraphTestData.graphWithStandoffLink)
      },
      test("return a graph containing just one node") {
        for {
          response <- resourceResponder(
                        _.getGraphDataResponseV2(
                          resourceIri = "http://rdfh.ch/0001/another-thing",
                          depth = 4,
                          GraphDirection.Both,
                          excludeProperty = Some(OntologyConstants.KnoraApiV2Complex.IsPartOf.toSmartIri),
                          requestingUser = anythingUser1,
                        ),
                      )
        } yield assertTrue(response == GraphTestData.graphWithOneNode)
      },
      test("create a resource with no values") {
        val resourceIri   = sf.makeRandomResourceIri(anythingProject.shortcode)
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "test thing",
          values = Map.empty,
          projectADM = anythingProject,
        )

        for {
          response <-
            resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID)))
          actualFromResponse = response.toResource(resourceIri).toOntologySchema(ApiV2Complex)
          _                 <- checkCreateResource(
                 inputResourceIri = resourceIri,
                 inputResource = inputResource,
                 outputResource = actualFromResponse,
                 defaultResourcePermissions = defaultAnythingResourcePermissions,
                 defaultValuePermissions = defaultAnythingValuePermissions,
                 requestingUser = anythingUser2,
               )
          // Get the resource from the triplestore and check it again.
          actualFromDb <- getResource(resourceIri)
          _            <- checkCreateResource(
                 inputResourceIri = resourceIri,
                 inputResource = inputResource,
                 outputResource = actualFromDb,
                 defaultResourcePermissions = defaultAnythingResourcePermissions,
                 defaultValuePermissions = defaultAnythingValuePermissions,
                 requestingUser = anythingUser2,
               )
        } yield assertCompletes
      },
      test("not create a resource when empty list for required property is provided") {
        val createThis = CreateResourceV2(
          Some(sf.makeRandomResourceIri(anythingProject.shortcode).toSmartIri),
          "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
          "test book",
          Map("http://www.knora.org/ontology/0803/incunabula#title".toSmartIri -> Seq.empty),
          incunabulaProject,
        )
        val req = CreateResourceRequestV2(createThis, incunabulaProjectAdminUser, randomUUID)
        resourceResponder(_.createResource(req)).exit.map(actual =>
          assert(actual)(failsWithA[OntologyConstraintException]),
        )
      },
      test("create a resource with no values and custom permissions") {
        val resourceIri   = sf.makeRandomResourceIri(anythingProject.shortcode)
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "test thing",
          values = Map.empty,
          projectADM = anythingProject,
          permissions = Some("CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"),
        )

        for {
          _              <- resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID)))
          outputResource <- getResource(resourceIri)
          _              <- checkCreateResource(
                 inputResourceIri = resourceIri,
                 inputResource = inputResource,
                 outputResource = outputResource,
                 defaultResourcePermissions = defaultAnythingResourcePermissions,
                 defaultValuePermissions = defaultAnythingValuePermissions,
                 requestingUser = anythingUser2,
               )
        } yield assertCompletes
      },
      test("create a resource with values") {
        val resourceIri                                                 = sf.makeRandomResourceIri(anythingProject.shortcode)
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = 5,
                comment = Some("this is the number five"),
              ),
              permissions = Some("CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"),
            ),
            CreateValueInNewResourceV2(
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = 6,
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some("this is text without standoff"),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some("this is text with standoff"),
                standoff = sampleStandoff,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                mapping = standardMapping,
                textValueType = TextValueType.FormattedText,
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = DecimalValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasDecimal = BigDecimal("100000000000000.000000000000001"),
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDate".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = DateValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasCalendar = CalendarNameGregorian,
                valueHasStartJDN = 2264907,
                valueHasStartPrecision = DatePrecisionYear,
                valueHasEndJDN = 2265271,
                valueHasEndPrecision = DatePrecisionYear,
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = BooleanValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasBoolean = true,
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeometry".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = GeomValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasGeometry =
                  """{"status":"active","lineColor":"#ff3333","lineWidth":2,"points":[{"x":0.08098591549295775,"y":0.16741071428571427},{"x":0.7394366197183099,"y":0.7299107142857143}],"type":"rectangle","original_index":0}""",
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInterval".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = IntervalValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasIntervalStart = BigDecimal("1.2"),
                valueHasIntervalEnd = BigDecimal("3.4"),
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent =
                HierarchicalListValueContentV2(ApiV2Complex, "http://rdfh.ch/lists/0001/treeList03", None, None),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasColor".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = ColorValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasColor = "#ff3333",
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasUri".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = UriValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasUri = "https://www.knora.org",
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasGeoname".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = GeonameValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasGeonameCode = "2661604",
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = LinkValueContentV2(
                ontologySchema = ApiV2Complex,
                referredResourceIri = "http://rdfh.ch/0001/a-thing",
              ),
            ),
          ),
        )

        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "test thing",
          values = inputValues,
          projectADM = anythingProject,
        )

        for {
          _              <- resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID)))
          outputResource <- getResource(resourceIri)
          _              <- checkCreateResource(
                 inputResourceIri = resourceIri,
                 inputResource = inputResource,
                 outputResource = outputResource,
                 defaultResourcePermissions = defaultAnythingResourcePermissions,
                 defaultValuePermissions = defaultAnythingValuePermissions,
                 requestingUser = anythingUser2,
               )
        } yield assertCompletes
      },
      test("create a resource with a still image file value") {
        val resourceIri   = sf.makeRandomResourceIri(anythingProject.shortcode)
        val inputResource = UploadFileRequest
          .make(
            fileType = FileType.StillImageFile(
              dimX = 512,
              dimY = 256,
            ),
            internalFilename = "IQUO3t1AABm-FSLC0vNvVpr.jp2",
          )
          .toMessage(resourceIri = Some(resourceIri))
        for {
          _              <- resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID)))
          outputResource <- getResource(resourceIri)
          _              <- checkCreateResource(
                 inputResourceIri = resourceIri,
                 inputResource = inputResource,
                 outputResource = outputResource,
                 defaultResourcePermissions = defaultAnythingResourcePermissions,
                 defaultValuePermissions = defaultStillImageFileValuePermissions,
                 requestingUser = anythingUser2,
               )
        } yield assertCompletes
      },
      test("create a resource with an external still image file value") {
        val resourceIri     = sf.makeRandomResourceIri(anythingProject.shortcode)
        val imageRequestUrl = IiifImageRequestUrl.unsafeFrom(
          "https://iiif.ub.unibe.ch/image/v2.1/632664f2-20cb-43e4-8584-2fa3988c63a2/full/max/0/default.jpg",
        )
        val inputResource: CreateResourceV2 = UploadFileRequest
          .make(
            fileType = FileType.StillImageExternalFile(imageRequestUrl),
            internalFilename = "IQUO3t1AABm-TTTTTTTTTTT.jp2",
          )
          .toMessage(resourceIri = Some(resourceIri))

        for {
          _              <- resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID)))
          outputResource <- getResource(resourceIri)
          _              <- checkCreateResource(
                 inputResourceIri = resourceIri,
                 inputResource = inputResource,
                 outputResource = outputResource,
                 defaultResourcePermissions = defaultAnythingResourcePermissions,
                 defaultValuePermissions = defaultStillImageFileValuePermissions,
                 requestingUser = anythingUser2,
               )
        } yield assertCompletes
      },
      test("create a resource with document representation") {
        val resourceIri   = sf.makeRandomResourceIri(anythingProject.shortcode)
        val inputResource = UploadFileRequest
          .make(
            fileType = FileType.DocumentFile(),
            internalFilename = "IQUO3t1AABm-FSLC0vNvVpr.pdf",
          )
          .toMessage(resourceIri = Some(resourceIri))

        for {
          _              <- resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID)))
          outputResource <- getResource(resourceIri)
          _              <- checkCreateResource(
                 inputResourceIri = resourceIri,
                 inputResource = inputResource,
                 outputResource = outputResource,
                 defaultResourcePermissions = defaultAnythingResourcePermissions,
                 defaultValuePermissions = defaultStillImageFileValuePermissions,
                 requestingUser = anythingUser2,
               )
        } yield assertCompletes
      },
      test("create a resource with archive representation") {
        val resourceIri: String = sf.makeRandomResourceIri(anythingProject.shortcode)
        val inputResource       = UploadFileRequest
          .make(
            fileType = FileType.ArchiveFile,
            internalFilename = "IQUO3t1AABm-FSLC0vNvVps.zip",
          )
          .toMessage(
            resourceIri = Some(resourceIri),
            internalMimeType = Some("application/zip"),
          )
        for {
          _              <- resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID)))
          outputResource <- getResource(resourceIri)
          _              <- checkCreateResource(
                 inputResourceIri = resourceIri,
                 inputResource = inputResource,
                 outputResource = outputResource,
                 defaultResourcePermissions = defaultAnythingResourcePermissions,
                 defaultValuePermissions = defaultAnythingValuePermissions,
                 requestingUser = anythingUser2,
               )
        } yield assertCompletes
      },
      test("not create a resource with missing required values") {
        val resourceIri   = sf.makeRandomResourceIri(incunabulaProject.shortcode)
        val inputResource = CreateResourceV2(
          Some(resourceIri.toSmartIri),
          "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          "invalid book",
          Map.empty,
          incunabulaProject,
        )
        resourceResponder(
          _.createResource(CreateResourceRequestV2(inputResource, incunabulaProjectAdminUser, randomUUID)),
        ).exit.map(actual => assert(actual)(failsWithA[OntologyConstraintException]))
      },
      test("not create a resource with too many values for the cardinality of a property") {
        val resourceIri                                                 = sf.makeRandomResourceIri(incunabulaProject.shortcode)
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some("test title"),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0803/incunabula/v2#publoc".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some("test publoc 1"),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
            CreateValueInNewResourceV2(
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some("test publoc 2"),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          label = "invalid book",
          values = inputValues,
          projectADM = incunabulaProject,
        )

        resourceResponder(
          _.createResource(CreateResourceRequestV2(inputResource, incunabulaProjectAdminUser, randomUUID)),
        ).exit.map(actual => assert(actual)(failsWithA[OntologyConstraintException]))
      },
      test("not create a resource with a property for which there is no cardinality in the resource class") {
        val resourceIri                                                 = sf.makeRandomResourceIri(incunabulaProject.shortcode)
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some("test title"),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
          ),
          "http://0.0.0.0:3333/ontology/0803/incunabula/v2#pagenum".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some("test pagenum"),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          label = "invalid book",
          values = inputValues,
          projectADM = incunabulaProject,
        )
        resourceResponder(
          _.createResource(CreateResourceRequestV2(inputResource, incunabulaProjectAdminUser, randomUUID)),
        ).exit.map(actual => assert(actual)(failsWithA[OntologyConstraintException]))
      },
      test("not create a resource if the user doesn't have permission to create resources in the project") {
        val resourceIri                                                 = sf.makeRandomResourceIri(incunabulaProject.shortcode)
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/0803/incunabula/v2#title".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some("test title"),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
          label = "invalid book",
          values = inputValues,
          projectADM = incunabulaProject,
        )

        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID))).exit.map(
          actual => assert(actual)(failsWithA[ForbiddenException]),
        )
      },
      test("not create a resource with a link to a nonexistent other resource") {
        val resourceIri                                                 = sf.makeRandomResourceIri(anythingProject.shortcode)
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = LinkValueContentV2(
                ontologySchema = ApiV2Complex,
                referredResourceIri = "http://rdfh.ch/0001/nonexistent-thing",
              ),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "invalid thing",
          values = inputValues,
          projectADM = anythingProject,
        )
        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID))).exit.map(
          actual => assert(actual)(failsWithA[NotFoundException]),
        )
      },
      test("not create a resource with a standoff link to a nonexistent other resource") {
        val resourceIri                                    = sf.makeRandomResourceIri(anythingProject.shortcode)
        val standoffWithInvalidLink: Vector[StandoffTagV2] = Vector(
          StandoffTagV2(
            standoffTagClassIri = OntologyConstants.Standoff.StandoffRootTag.toSmartIri,
            startPosition = 0,
            endPosition = 26,
            uuid = randomUUID,
            originalXMLID = None,
            startIndex = 0,
          ),
          StandoffTagV2(
            standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag.toSmartIri,
            dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
            startPosition = 0,
            endPosition = 12,
            uuid = randomUUID,
            originalXMLID = None,
            startIndex = 1,
            attributes = Vector(
              StandoffTagIriAttributeV2(
                standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink.toSmartIri,
                value = "http://rdfh.ch/0001/nonexistent-thing",
              ),
            ),
            startParentIndex = Some(0),
          ),
          StandoffTagV2(
            standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag.toSmartIri,
            startPosition = 0,
            endPosition = 7,
            uuid = randomUUID,
            originalXMLID = None,
            startIndex = 2,
            startParentIndex = Some(1),
          ),
        )

        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some("this is text with standoff"),
                standoff = standoffWithInvalidLink,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                mapping = standardMapping,
                textValueType = TextValueType.FormattedText,
              ),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "invalid thing",
          values = inputValues,
          projectADM = anythingProject,
        )
        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID))).exit.map(
          actual => assert(actual)(failsWithA[NotFoundException]),
        )
      },
      test("not create a resource with a list value referring to a nonexistent list node") {
        val resourceIri                                                 = sf.makeRandomResourceIri(anythingProject.shortcode)
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = HierarchicalListValueContentV2(
                ApiV2Complex,
                "http://rdfh.ch/lists/0001/nonexistent-list-node",
                None,
                None,
              ),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "invalid thing",
          values = inputValues,
          projectADM = anythingProject,
        )

        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID))).exit.map(
          actual => assert(actual)(failsWithA[NotFoundException]),
        )
      },
      test("not create a resource with a value that's the wrong type for the property") {
        val resourceIri                                                 = sf.makeRandomResourceIri(anythingProject.shortcode)
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasListItem".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some("invalid text value"),
                textValueType = TextValueType.UnformattedText,
              ),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "invalid thing",
          values = inputValues,
          projectADM = anythingProject,
        )

        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID))).exit.map(
          actual => assert(actual)(failsWithA[OntologyConstraintException]),
        )
      },
      test("not create a resource with a link to a resource in a different project") {
        // The new resource should be created in the Anything project 0001
        val resourceIri = sf.makeRandomResourceIri(anythingProject.shortcode)
        // This resource is present in the Incunabula project 0803
        val linkedResourceIri = zeitgloeckleinIri

        val createResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "invalid thing",
          values = Map(
            "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri ->
              Seq(CreateValueInNewResourceV2(LinkValueContentV2(ApiV2Complex, linkedResourceIri))),
          ),
          projectADM = anythingProject,
        )

        resourceResponder(_.createResource(CreateResourceRequestV2(createResource, anythingUser2, randomUUID))).exit
          .map(actual =>
            assert(actual)(
              failsWithMessageEqualTo[BadRequestException](
                "Cannot create a link between resources cross projects 0001 and 0803",
              ),
            ),
          )
      },
      test("not create a resource with a link to a resource of the wrong class for the link property") {
        // the linked Resource exists in the test data and is a anything:ThingText
        val linkedResourceIri = "http://rdfh.ch/0001/MAiNrOB1Q--rzAzdkqbHOw"
        // subjectClassConstraint for hasOtherThingValue is anything:Thing
        val linkProperty = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri

        val resourceIri   = sf.makeRandomResourceIri(anythingProject.shortcode)
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "invalid thing",
          values =
            Map(linkProperty -> Seq(CreateValueInNewResourceV2(LinkValueContentV2(ApiV2Complex, linkedResourceIri)))),
          projectADM = anythingProject,
        )

        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID))).exit.map(
          actual => assert(actual)(failsWithA[OntologyConstraintException]),
        )
      },
      test("not create a resource with invalid custom permissions") {
        val resourceIri   = sf.makeRandomResourceIri(anythingProject.shortcode)
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "invalid thing",
          values = Map.empty,
          projectADM = anythingProject,
          permissions = Some("M knora-admin:Creator,V knora-admin:KnownUser"),
        )
        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID))).exit.map(
          actual => assert(actual)(failsWithA[BadRequestException]),
        )
      },
      test("not create a resource with a value that has invalid custom permissions") {
        val resourceIri                                            = sf.makeRandomResourceIri(anythingProject.shortcode)
        val values: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = 5,
                comment = Some("this is the number five"),
              ),
              permissions = Some("M knora-admin:Creator,V knora-admin:KnownUser"),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "invalid thing",
          values = values,
          projectADM = anythingProject,
        )

        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID))).exit.map(
          actual => assert(actual)(failsWithA[BadRequestException]),
        )
      },
      test("not create a resource that uses a class from another non-shared project") {
        val resourceIri   = sf.makeRandomResourceIri(incunabulaProject.shortcode)
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "test thing",
          values = Map.empty,
          projectADM = incunabulaProject,
        )
        resourceResponder(
          _.createResource(CreateResourceRequestV2(inputResource, incunabulaProjectAdminUser, randomUUID)),
        ).exit.map(actual => assert(actual)(failsWithA[BadRequestException]))
      },
      test("not update a resource's metadata if the user does not have permission to update the resource") {
        val updateRequest = UpdateResourceMetadataRequestV2(
          resourceIri = aThingIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          maybeLabel = Some("new test label"),
          requestingUser = incunabulaProjectAdminUser,
          apiRequestID = randomUUID,
        )
        resourceResponder(_.updateResourceMetadataV2(updateRequest)).exit
          .map(actual => assert(actual)(failsWithA[ForbiddenException]))
      },
      test("not update a resource's metadata if the user does not supply the correct resource class") {
        val updateRequest = UpdateResourceMetadataRequestV2(
          resourceIri = aThingIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#BlueThing".toSmartIri,
          maybeLabel = Some("new test label"),
          requestingUser = anythingUser2,
          apiRequestID = randomUUID,
        )
        resourceResponder(_.updateResourceMetadataV2(updateRequest)).exit
          .map(actual => assert(actual)(failsWithA[BadRequestException]))
      },
      test("update a resource's metadata when it doesn't have a knora-base:lastModificationDate") {
        val dateTimeStampBeforeUpdate = Instant.now
        val newLabel                  = "new test label"
        val newPermissions            = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:ProjectMember"
        val updateRequest             = UpdateResourceMetadataRequestV2(
          resourceIri = aThingIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          maybeLabel = Some(newLabel),
          maybePermissions = Some(newPermissions),
          requestingUser = anythingUser2,
          apiRequestID = randomUUID,
        )

        for {
          _              <- resourceResponder(_.updateResourceMetadataV2(updateRequest))
          outputResource <- getResource(aThingIri)
          _               = { self.aThingLastModificationDate = outputResource.lastModificationDate.get }
        } yield assertTrue(
          PermissionUtilADM.parsePermissions(outputResource.permissions) ==
            PermissionUtilADM.parsePermissions(newPermissions),
          outputResource.label == newLabel,
          aThingLastModificationDate.isAfter(dateTimeStampBeforeUpdate),
        )
      },
      test("not update a resource's metadata if its knora-base:lastModificationDate exists but is not submitted") {
        val updateRequest = UpdateResourceMetadataRequestV2(
          resourceIri = aThingIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          maybeLabel = Some("another new test label"),
          requestingUser = anythingUser2,
          apiRequestID = randomUUID,
        )
        resourceResponder(_.updateResourceMetadataV2(updateRequest)).exit
          .map(actual => assert(actual)(failsWithA[EditConflictException]))
      },
      test("not update a resource's metadata if the wrong knora-base:lastModificationDate is submitted") {
        val updateRequest = UpdateResourceMetadataRequestV2(
          resourceIri = aThingIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          maybeLastModificationDate = Some(Instant.MIN),
          maybeLabel = Some("another new test label"),
          requestingUser = anythingUser2,
          apiRequestID = randomUUID,
        )
        resourceResponder(_.updateResourceMetadataV2(updateRequest)).exit
          .map(actual => assert(actual)(failsWithA[EditConflictException]))
      },
      test("update a resource's metadata when it has a knora-base:lastModificationDate") {
        val newLabel      = "another new test label"
        val updateRequest = UpdateResourceMetadataRequestV2(
          resourceIri = aThingIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          maybeLastModificationDate = Some(aThingLastModificationDate),
          maybeLabel = Some(newLabel),
          requestingUser = anythingUser2,
          apiRequestID = randomUUID,
        )
        for {
          _                          <- resourceResponder(_.updateResourceMetadataV2(updateRequest))
          outputResource             <- getResource(aThingIri)
          updatedLastModificationDate = outputResource.lastModificationDate.get
          oldModificationDate         = self.aThingLastModificationDate
          _                           = { self.aThingLastModificationDate = updatedLastModificationDate }
        } yield assertTrue(
          outputResource.label == newLabel,
          updatedLastModificationDate.isAfter(oldModificationDate),
        )
      },
      test(
        "not update a resource's knora-base:lastModificationDate with a value that's earlier than the current value",
      ) {
        val updateRequest = UpdateResourceMetadataRequestV2(
          resourceIri = aThingIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          maybeLastModificationDate = Some(aThingLastModificationDate),
          maybeNewModificationDate = Some(Instant.MIN),
          requestingUser = anythingUser2,
          apiRequestID = randomUUID,
        )
        resourceResponder(_.updateResourceMetadataV2(updateRequest)).exit
          .map(actual => assert(actual)(failsWithA[BadRequestException]))
      },
      test("update a resource's knora-base:lastModificationDate") {
        val newModificationDate = Instant.now.plus(java.time.Duration.ofDays(1))
        val updateRequest       = UpdateResourceMetadataRequestV2(
          resourceIri = aThingIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          maybeLastModificationDate = Some(aThingLastModificationDate),
          maybeNewModificationDate = Some(newModificationDate),
          requestingUser = anythingUser2,
          apiRequestID = randomUUID,
        )
        for {
          _                          <- resourceResponder(_.updateResourceMetadataV2(updateRequest))
          outputResource             <- getResource(aThingIri)
          updatedLastModificationDate = outputResource.lastModificationDate.get
          _                           = self.aThingLastModificationDate = updatedLastModificationDate
        } yield assertTrue(updatedLastModificationDate == newModificationDate)
      },
      test(
        "not mark a resource as deleted with a custom delete date that is earlier than the resource's last modification date",
      ) {
        val deleteDate: Instant = aThingLastModificationDate.minus(1, ChronoUnit.DAYS)
        val deleteRequest       = DeleteOrEraseResourceRequestV2(
          resourceIri = aThingIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          maybeDeleteComment = Some("This resource is too boring."),
          maybeDeleteDate = Some(deleteDate),
          maybeLastModificationDate = Some(aThingLastModificationDate),
          requestingUser = anythingUser1,
          apiRequestID = randomUUID,
        )
        resourceResponder(_.markResourceAsDeletedV2(deleteRequest)).exit
          .map(actual => assert(actual)(failsWithA[BadRequestException]))
      },
      test("mark a resource as deleted") {
        val resourceIri = sf.makeRandomResourceIri(anythingProject.shortcode)
        val createReq   = CreateResourceRequestV2(
          CreateResourceV2(
            Some(resourceIri.toSmartIri),
            "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
            "test label",
            Map.empty,
            anythingProject,
          ),
          anythingUser1,
          randomUUID,
        )

        for {
          response                   <- resourceResponder(_.createResource(createReq))
          createdResourceIri          = response.resources.head.resourceIri
          createdResourceCreationDate = response.resources.head.creationDate
          deleteRequest               = DeleteOrEraseResourceRequestV2(
                            resourceIri = createdResourceIri,
                            resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                            maybeDeleteComment = Some("This resource is too boring."),
                            maybeLastModificationDate = Some(createdResourceCreationDate),
                            requestingUser = anythingUser1,
                            apiRequestID = randomUUID,
                          )
          _           <- resourceResponder(_.markResourceAsDeletedV2(deleteRequest))
          getResponse <- resourceResponder(
                           _.getResourcesWithDeletedResource(
                             resourceIris = Seq(createdResourceIri),
                             targetSchema = ApiV2Complex,
                             schemaOptions = Set.empty,
                             requestingUser = anythingUser1,
                           ),
                         )
          resource = getResponse.resources.head
        } yield assertTrue(
          getResponse.resources.size == 1,
          resource.resourceClassIri == OntologyConstants.KnoraBase.DeletedResource.toSmartIri,
          resource.deletionInfo.nonEmpty,
          resource.lastModificationDate.nonEmpty,
          resource.creationDate == createdResourceCreationDate,
        )
      },
      test("mark a resource as deleted, supplying a custom delete date") {
        val resourceIri         = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"
        val deleteDate: Instant = Instant.now
        val deleteRequest       = DeleteOrEraseResourceRequestV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          maybeDeleteComment = Some("This resource is too boring."),
          maybeDeleteDate = Some(deleteDate),
          maybeLastModificationDate = None,
          requestingUser = superUser,
          apiRequestID = randomUUID,
        )

        for {
          _        <- resourceResponder(_.markResourceAsDeletedV2(deleteRequest))
          response <- resourceResponder(
                        _.getResourcesWithDeletedResource(
                          resourceIris = Seq(resourceIri),
                          targetSchema = ApiV2Complex,
                          schemaOptions = Set.empty,
                          requestingUser = anythingUser1,
                        ),
                      )
          resource = response.resources.head
        } yield assertTrue(
          response.resources.size == 1,
          resource.resourceClassIri == OntologyConstants.KnoraBase.DeletedResource.toSmartIri,
          resource.deletionInfo.map(_.deleteDate).contains(deleteDate),
        )
      },
      test(
        "not accept custom resource permissions that would give the requesting user a higher permission on a resource than the default",
      ) {
        val resourceIri   = sf.makeRandomResourceIri(imagesProject.shortcode)
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
          label = "test bildformat",
          values = Map.empty,
          projectADM = imagesProject,
          permissions = Some("CR knora-admin:Creator"),
        )
        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, imagesReviewerUser, randomUUID))).exit
          .map(actual => assert(actual)(failsWithA[ForbiddenException]))
      },
      test(
        "accept custom resource permissions that would give the requesting user a higher permission on a resource than the default if the user is a system admin",
      ) {
        val resourceIri   = sf.makeRandomResourceIri(imagesProject.shortcode)
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
          label = "test bildformat",
          values = Map.empty,
          projectADM = imagesProject,
          permissions = Some("CR knora-admin:Creator"),
        )
        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, rootUser, randomUUID)))
          .as(assertCompletes)
      },
      test(
        "accept custom resource permissions that would give the requesting user a higher permission on a resource than the default if the user is a project admin",
      ) {
        val resourceIri   = sf.makeRandomResourceIri(imagesProject.shortcode)
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
          label = "test bildformat",
          values = Map.empty,
          projectADM = imagesProject,
          permissions = Some("CR knora-admin:Creator"),
        )
        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, imagesUser01, randomUUID)))
          .as(assertCompletes)
      },
      test(
        "not accept custom value permissions that would give the requesting user a higher permission on a value than the default",
      ) {
        val resourceIri                                                 = sf.makeRandomResourceIri(imagesProject.shortcode)
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = 5,
                comment = Some("this is the number five"),
              ),
              permissions = Some("CR knora-admin:Creator"),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
          label = "test bildformat",
          values = inputValues,
          projectADM = imagesProject,
        )

        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, imagesReviewerUser, randomUUID))).exit
          .map(actual => assert(actual)(failsWithA[ForbiddenException]))
      },
      test(
        "accept custom value permissions that would give the requesting user a higher permission on a value than the default if the user is a system admin",
      ) {
        val resourceIri                                                 = sf.makeRandomResourceIri(imagesProject.shortcode)
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = 5,
                comment = Some("this is the number five"),
              ),
              permissions = Some("CR knora-admin:Creator"),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
          label = "test bildformat",
          values = inputValues,
          projectADM = imagesProject,
        )

        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, rootUser, randomUUID)))
          .as(assertCompletes)
      },
      test(
        "accept custom value permissions that would give the requesting user a higher permission on a value than the default if the user is a project admin",
      ) {
        val resourceIri                                                 = sf.makeRandomResourceIri(imagesProject.shortcode)
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/00FF/images/v2#stueckzahl".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = 5,
                comment = Some("this is the number five"),
              ),
              permissions = Some("CR knora-admin:Creator"),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/00FF/images/v2#bildformat".toSmartIri,
          label = "test bildformat",
          values = inputValues,
          projectADM = imagesProject,
        )

        resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, imagesUser01, randomUUID)))
          .as(assertCompletes)
      },
      test("create a resource with version history so we can test erasing it") {
        val resourceIri = sf.makeRandomResourceIri(anythingProject.shortcode)
        resourceIriToErase.set(resourceIri)
        val resourceClassIri: SmartIri                                  = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri
        val propertyIri: SmartIri                                       = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext".toSmartIri
        val standoffTagUUIDsToErase                                     = collection.mutable.Set.empty[UUID]
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          propertyIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = TextValueContentV2(
                ontologySchema = ApiV2Complex,
                maybeValueHasString = Some("this is text with standoff"),
                standoff = sampleStandoffForErasingResource,
                mappingIri = Some("http://rdfh.ch/standoff/mappings/StandardMapping"),
                mapping = standardMapping,
                textValueType = TextValueType.FormattedText,
              ),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = resourceClassIri,
          label = "test thing",
          values = inputValues,
          projectADM = anythingProject,
        )

        for {
          _              <- resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID)))
          outputResource <- getResource(resourceIri)
          firstTextValue  = outputResource.values(propertyIri).head.asInstanceOf[ReadTextValueV2]
          _               = firstValueIriToErase.set(firstTextValue.valueIri)
          _               = for (standoffTag <- firstTextValue.valueContent.standoff) {
                standoffTagUUIDsToErase.add(standoffTag.uuid)
              }
          // Update the value.
          updateValueResponse <- ZIO.serviceWithZIO[ValuesResponderV2](
                                   _.updateValueV2(
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
                                         mapping = standardMapping,
                                         textValueType = TextValueType.FormattedText,
                                       ),
                                     ),
                                     anythingUser2,
                                     randomUUID,
                                   ),
                                 )
          _                = secondValueIriToErase.set(updateValueResponse.valueIri)
          updatedResource <- getResource(resourceIri)
          secondTextValue  = updatedResource.values(propertyIri).head.asInstanceOf[ReadTextValueV2]
          _                = secondValueIriToErase.set(secondTextValue.valueIri)
          _                = for (standoffTag <- firstTextValue.valueContent.standoff) {
                standoffTagUUIDsToErase.add(standoffTag.uuid)
              }
          _ = resourceToEraseLastModificationDate = updatedResource.lastModificationDate.get
          // Get the IRIs of the standoff tags.
          _ <-
            ZIO.foreachDiscard(standoffTagUUIDsToErase) { uuid =>
              getStandoffTagByUUID(uuid).tap(standoffTagIris => ZIO.succeed(standoffTagIrisToErase ++= standoffTagIris))
            }
        } yield assertTrue(standoffTagUUIDsToErase.size == 3, standoffTagIrisToErase.size == 4)
      },
      test("not erase a resource if the user is not a system/project admin") {
        val eraseRequest = DeleteOrEraseResourceRequestV2(
          resourceIri = resourceIriToErase.get,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          maybeLastModificationDate = Some(resourceToEraseLastModificationDate),
          requestingUser = anythingUser2,
          apiRequestID = randomUUID,
        )
        resourceResponder(_.eraseResourceV2(eraseRequest)).exit.map(actual =>
          assert(actual)(failsWithA[ForbiddenException]),
        )
      },
      test("not erase a resource if another resource has a link to it") {
        val resourceWithLinkIri = ResourceIri.unsafeFrom(
          sf.makeRandomResourceIri(anythingProject.shortcode).toSmartIri,
        )
        val resourceClassIri =
          ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri)
        val linkValuePropertyIri =
          PropertyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherThingValue".toSmartIri)
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          linkValuePropertyIri.smartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = LinkValueContentV2(
                ontologySchema = ApiV2Complex,
                referredResourceIri = resourceIriToErase.get,
              ),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceWithLinkIri.smartIri),
          resourceClassIri = resourceClassIri.smartIri,
          label = "thing with link",
          values = inputValues,
          projectADM = anythingProject,
        )

        for {
          _              <- resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID)))
          outputResource <- getResource(resourceWithLinkIri.toString)
          linkValue       = outputResource.findLinkValues(linkValuePropertyIri).head
          // Try to erase the first resource.
          eraseRequest = DeleteOrEraseResourceRequestV2(
                           resourceIri = resourceIriToErase.get,
                           resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                           maybeLastModificationDate = Some(resourceToEraseLastModificationDate),
                           requestingUser = anythingAdminUser,
                           apiRequestID = randomUUID,
                         )
          exit <- resourceResponder(_.eraseResourceV2(eraseRequest)).exit
          // Delete the link.
          _ <- ZIO.serviceWithZIO[ValuesResponderV2](
                 _.deleteValueV2(
                   DeleteValueV2(
                     resourceWithLinkIri,
                     ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri),
                     linkValuePropertyIri,
                     ValueIri.unsafeFrom(linkValue.valueIri.toSmartIri),
                     valueTypeIri = OntologyConstants.KnoraApiV2Complex.LinkValue.toSmartIri,
                     apiRequestId = randomUUID,
                   ),
                   anythingUser2,
                 ),
               )
        } yield assert(exit)(failsWithA[BadRequestException])
      },
      test("erase a resource") {
        // Erase the resource.
        val eraseRequest = DeleteOrEraseResourceRequestV2(
          resourceIri = resourceIriToErase.get,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          maybeLastModificationDate = Some(resourceToEraseLastModificationDate),
          requestingUser = anythingAdminUser,
          apiRequestID = randomUUID,
        )

        for {
          _ <- resourceResponder(_.eraseResourceV2(eraseRequest))
          // Check that all parts of the resource were erased.
          erasedIrisToCheck =
            (standoffTagIrisToErase.toSet + resourceIriToErase.get + firstValueIriToErase.get + secondValueIriToErase.get)
              .map(_.toSmartIri)
          _ <- ZIO.foreachDiscard(erasedIrisToCheck) { erasedIriToCheck =>
                 val query = sparql.admin.txt.checkIriExists(erasedIriToCheck.toString)
                 ZIO
                   .serviceWithZIO[TriplestoreService](_.query(Ask(query)))
                   .filterOrFail(_ == false)(new AssertionException(s"IRI $erasedIriToCheck was not erased"))
               }
        } yield assertCompletes
      },
      test("erase a deleted resource") {
        val resourceIri = sf.makeRandomResourceIri(anythingProject.shortcode)
        val createReq   = CreateResourceRequestV2(
          CreateResourceV2(
            Some(resourceIri.toSmartIri),
            "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
            "test label for resource to be deleted then erased",
            Map.empty,
            anythingProject,
          ),
          anythingUser1,
          randomUUID,
        )

        for {
          createResponse             <- resourceResponder(_.createResource(createReq))
          createdResourceIri          = createResponse.resources.head.resourceIri
          createdResourceCreationDate = createResponse.resources.head.creationDate
          // First, mark the resource as deleted.
          deleteRequest = DeleteOrEraseResourceRequestV2(
                            resourceIri = createdResourceIri,
                            resourceClassIri =
                              "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri, // External schema IRI
                            maybeDeleteComment = Some("This resource will be deleted first, then erased."),
                            maybeLastModificationDate = Some(createdResourceCreationDate),
                            requestingUser = anythingAdminUser, // Need admin user for erasure
                            apiRequestID = randomUUID,
                          )
          _ <- resourceResponder(_.markResourceAsDeletedV2(deleteRequest))
          // Verify the resource is marked as deleted.
          getDeletedResponse <- resourceResponder(
                                  _.getResourcesWithDeletedResource(
                                    resourceIris = Seq(createdResourceIri),
                                    targetSchema = ApiV2Complex,
                                    schemaOptions = Set.empty,
                                    requestingUser = anythingAdminUser,
                                  ),
                                )
          deletedResource = getDeletedResponse.resources.head
          // Now, erase the deleted resource. This should work without schema validation errors.
          eraseRequest = DeleteOrEraseResourceRequestV2(
                           resourceIri = createdResourceIri,
                           resourceClassIri =
                             "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri, // Same external schema IRI
                           maybeLastModificationDate = deletedResource.lastModificationDate,
                           requestingUser = anythingAdminUser,
                           apiRequestID = randomUUID,
                         )
          eraseResponse <- resourceResponder(_.eraseResourceV2(eraseRequest))
          // Verify the resource is completely erased.
          resourceExists <- {
            val query = sparql.admin.txt.checkIriExists(createdResourceIri)
            ZIO.serviceWithZIO[TriplestoreService](_.query(Ask(query)))
          }
        } yield assertTrue(
          eraseResponse.message == "Resource erased",
          deletedResource.deletionInfo.nonEmpty,
          !resourceExists,
        )
      },
    ),
    suite("When given a custom IRI")(
      test("create a resource with no values but a custom IRI") {
        val resourceIri   = "http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9kk"
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "thing with a custom IRI",
          values = Map.empty,
          projectADM = anythingProject,
        )

        for {
          response <-
            resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID)))
          // Check that the response contains the correct metadata.
          actualFromResponse = response.toResource(resourceIri).toOntologySchema(ApiV2Complex)
          _                 <- checkCreateResource(
                 inputResourceIri = resourceIri,
                 inputResource = inputResource,
                 outputResource = actualFromResponse,
                 defaultResourcePermissions = defaultAnythingResourcePermissions,
                 defaultValuePermissions = defaultAnythingValuePermissions,
                 requestingUser = anythingUser2,
               )

          // Get the resource from the triplestore and check it again.
          actualFromDb <- getResource(resourceIri)
          _            <- checkCreateResource(
                 inputResourceIri = resourceIri,
                 inputResource = inputResource,
                 outputResource = actualFromDb,
                 defaultResourcePermissions = defaultAnythingResourcePermissions,
                 defaultValuePermissions = defaultAnythingValuePermissions,
                 requestingUser = anythingUser2,
               )
        } yield assertCompletes
      },
      test("create a resource with a value that has custom UUID") {
        val resourceIri                                                 = sf.makeRandomResourceIri(anythingProject.shortcode)
        val inputValues: Map[SmartIri, Seq[CreateValueInNewResourceV2]] = Map(
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger".toSmartIri -> Seq(
            CreateValueInNewResourceV2(
              valueContent = IntegerValueContentV2(
                ontologySchema = ApiV2Complex,
                valueHasInteger = 5,
                comment = Some("this is the number five"),
              ),
              permissions = Some("CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher"),
              customValueUUID = Some(UuidUtil.base64Decode("IN4R19yYR0ygi3K2VEHpUQ").get),
            ),
          ),
        )
        val inputResource = CreateResourceV2(
          resourceIri = Some(resourceIri.toSmartIri),
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          label = "thing with custom value UUID",
          values = inputValues,
          projectADM = anythingProject,
        )

        for {
          _              <- resourceResponder(_.createResource(CreateResourceRequestV2(inputResource, anythingUser2, randomUUID)))
          outputResource <- getResource(resourceIri)
          _              <- checkCreateResource(
                 inputResourceIri = resourceIri,
                 inputResource = inputResource,
                 outputResource = outputResource,
                 defaultResourcePermissions = defaultAnythingResourcePermissions,
                 defaultValuePermissions = defaultAnythingValuePermissions,
                 requestingUser = anythingUser2,
               )
        } yield assertCompletes
      },
    ),
    suite("When asked for events")(
      test("return full history of a-thing-picture resource") {
        val resourceIri = "http://rdfh.ch/0001/a-thing-picture"
        for {
          events              <- resourceResponder(_.getResourceHistoryEvents(resourceIri, anythingUser2).map(_.historyEvents))
          createResourceEvents =
            events.filter(historyEvent => historyEvent.eventType == ResourceAndValueEventsUtil.CREATE_RESOURCE_EVENT)
          createValueEvents =
            events.filter(historyEvent => historyEvent.eventType == ResourceAndValueEventsUtil.CREATE_VALUE_EVENT)
          updateValueEvents = events.filter(historyEvent =>
                                historyEvent.eventType == ResourceAndValueEventsUtil.UPDATE_VALUE_CONTENT_EVENT,
                              )
        } yield assertTrue(
          createResourceEvents.size == 1,
          createValueEvents.size == 1,
          updateValueEvents.size == 1,
          events.size == 3,
        )
      },
      test("return full history of a resource as events") {
        val resourceIri = "http://rdfh.ch/0001/thing-with-history"
        resourceResponder(_.getResourceHistoryEvents(resourceIri, anythingUser2).map(_.historyEvents))
          .map(actual => assertTrue(actual.size == 9))
      },
      test("create a new value to test create value history event") {
        val resourceIri = "http://rdfh.ch/0001/thing-with-history"
        val newValueIri = "http://rdfh.ch/0001/thing-with-history/values/xZisRC3jPkcplt1hQQdb-A"
        val testValue   = "a test value"

        for {
          _ <- ZIO.serviceWithZIO[ValuesResponderV2](
                 _.createValueV2(
                   CreateValueV2(
                     resourceIri = resourceIri,
                     resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
                     propertyIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri,
                     valueContent = TextValueContentV2(
                       ontologySchema = ApiV2Complex,
                       maybeValueHasString = Some(testValue),
                       textValueType = TextValueType.UnformattedText,
                     ),
                     valueIri = Some(newValueIri.toSmartIri),
                     permissions = Some("CR knora-admin:Creator|V knora-admin:KnownUser"),
                   ),
                   requestingUser = anythingUser2,
                   apiRequestID = randomUUID,
                 ),
               )
          events                                                <- resourceResponder(_.getResourceHistoryEvents(resourceIri, anythingUser2).map(_.historyEvents))
          createValueEvent: Option[ResourceAndValueHistoryEvent] =
            events.find(event =>
              event.eventType == ResourceAndValueEventsUtil.CREATE_VALUE_EVENT && event.eventBody
                .asInstanceOf[ValueEventBody]
                .valueIri == newValueIri,
            )
          createValuePayloadContent = createValueEvent.get.eventBody
                                        .asInstanceOf[ValueEventBody]
                                        .valueContent
          payloadContent = createValuePayloadContent.get
        } yield assertTrue(
          createValuePayloadContent.isDefined,
          createValueEvent.isDefined,
          payloadContent.valueType == OntologyConstants.KnoraBase.TextValue.toSmartIri,
          payloadContent.valueHasString == testValue,
          events.size == 10,
        )
      },
      test("delete the newly created value to check the delete value event of resource history") {
        val resourceIri   = ResourceIri.unsafeFrom("http://rdfh.ch/0001/thing-with-history".toSmartIri)
        val valueToDelete =
          ValueIri.unsafeFrom("http://rdfh.ch/0001/thing-with-history/values/xZisRC3jPkcplt1hQQdb-A".toSmartIri)
        val resourceClassIri =
          ResourceClassIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri)
        val propertyIri   = PropertyIri.unsafeFrom("http://0.0.0.0:3333/ontology/0001/anything/v2#hasText".toSmartIri)
        val deleteComment = "delete value test"

        // delete the new value.
        for {
          _ <- ZIO.serviceWithZIO[ValuesResponderV2](
                 _.deleteValueV2(
                   DeleteValueV2(
                     resourceIri,
                     resourceClassIri,
                     propertyIri,
                     valueToDelete,
                     valueTypeIri = OntologyConstants.KnoraApiV2Complex.TextValue.toSmartIri,
                     deleteComment = Some(deleteComment),
                     apiRequestId = randomUUID,
                   ),
                   anythingUser2,
                 ),
               )
          events <-
            resourceResponder(_.getResourceHistoryEvents(resourceIri.toString, anythingUser2).map(_.historyEvents))
          deleteValueEvent: Option[ResourceAndValueHistoryEvent] =
            events.find(event =>
              event.eventType == ResourceAndValueEventsUtil.DELETE_VALUE_EVENT &&
                event.eventBody.asInstanceOf[ValueEventBody].valueIri == valueToDelete.toString,
            )
        } yield assertTrue(events.size == 11, deleteValueEvent.isDefined)
      },
      test("return full history of a deleted resource") {
        val resourceIri = "http://rdfh.ch/0001/PHbbrEsVR32q5D_ioKt6pA"
        for {
          events             <- resourceResponder(_.getResourceHistoryEvents(resourceIri, anythingUser2).map(_.historyEvents))
          deleteResourceEvent =
            events.find(event => event.eventType == ResourceAndValueEventsUtil.DELETE_RESOURCE_EVENT)
          deletionInfo = deleteResourceEvent.get.eventBody.asInstanceOf[ResourceEventBody].deletionInfo.get
        } yield assertTrue(
          deleteResourceEvent.isDefined,
          events.size == 2,
          deletionInfo.maybeDeleteComment.contains("a comment for the deleted thing."),
        )
      },
      test("update resource's metadata to test update resource metadata event") {
        val resourceIri   = "http://rdfh.ch/0001/thing_with_BCE_date2"
        val updateRequest = UpdateResourceMetadataRequestV2(
          resourceIri = resourceIri,
          resourceClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
          maybeLabel = Some("a new label"),
          requestingUser = anythingUser2,
          apiRequestID = randomUUID,
        )
        for {
          _                  <- resourceResponder(_.updateResourceMetadataV2(updateRequest))
          events             <- resourceResponder(_.getResourceHistoryEvents(resourceIri, anythingUser2).map(_.historyEvents))
          updateMetadataEvent =
            events.find(event => event.eventType == ResourceAndValueEventsUtil.UPDATE_RESOURCE_METADATA_EVENT)
        } yield assertTrue(events.size == 2, updateMetadataEvent.isDefined)
      },
      test("not return resources of a project which does not exist") {
        resourceResponder(
          _.getProjectResourceHistoryEvents(
            ProjectIri.unsafeFrom("http://rdfh.ch/projects/1111"),
            anythingAdminUser,
          ),
        ).exit.map(actual => assert(actual)(failsWithA[NotFoundException]))
      },
      test("return seq of full history events for each resource of a project") {
        resourceResponder(
          _.getProjectResourceHistoryEvents(
            ProjectIri.unsafeFrom("http://rdfh.ch/projects/0001"),
            anythingAdminUser,
          ),
        ).map(r => assertTrue(r.historyEvents.size > 1))
      },
    ),
  )
}
