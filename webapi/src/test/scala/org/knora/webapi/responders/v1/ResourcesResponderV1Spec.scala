/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.responders.v1

import java.util.UUID

import akka.actor.Props
import akka.testkit.{ImplicitSender, TestActorRef}
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.SharedOntologyTestDataADM._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.permissionsmessages.{ObjectAccessPermissionADM, ObjectAccessPermissionsForResourceGetADM, PermissionADM}
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.sipimessages.SipiResponderConversionFileRequestV1
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v1.responder.valuemessages._
import org.knora.webapi.responders._
import org.knora.webapi.store._
import org.knora.webapi.twirl.{StandoffTagIriAttributeV1, StandoffTagV1}
import org.knora.webapi.util._

import scala.concurrent.duration._

/**
  * Static data for testing [[ResourcesResponderV1]].
  */
object ResourcesResponderV1Spec {

    val config: Config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)

    val ReiseInsHeiligelandThreeValues = ResourceSearchResponseV1(
        resources = Vector(ResourceSearchResultRowV1(
            id = "http://data.knora.org/2a6221216701",
            value = Vector("Reise ins Heilige Land", "Reysen und wanderschafften durch das Gelobte Land", "Itinerarius"),
            rights = Some(6)
        ))
    )

    val ReiseInsHeiligelandOneValueRestrictedToBook = ResourceSearchResponseV1(
        resources = Vector(ResourceSearchResultRowV1(
            id = "http://data.knora.org/2a6221216701",
            value = Vector("Reise ins Heilige Land"),
            rights = Some(6)
        ))
    )

    private val propertiesGetResponseV1Region = PropertiesGetResponseV1(
        PropsGetV1(
            Vector(
                PropertyGetV1(
                    "http://www.knora.org/ontology/knora-base#hasComment",
                    Some("Kommentar"),
                    Some("http://www.knora.org/ontology/knora-base#TextValue"),
                    Some("textval"),
                    Some("richtext"),
                    "",
                    "0",
                    Vector(
                        PropertyGetValueV1(
                            None,
                            None,
                            "Siehe Seite c5v",
                            TextValueSimpleV1("Siehe Seite c5v"),
                            "http://data.knora.org/021ec18f1735/values/8a96c303338201",
                            None,
                            None))),
                PropertyGetV1(
                    "http://www.knora.org/ontology/knora-base#hasColor",
                    Some("Farbe"),
                    Some(
                        "http://www.knora.org/ontology/knora-base#ColorValue"),
                    Some("textval"),
                    Some("colorpicker"),
                    "ncolors=8",
                    "0",
                    Vector(
                        PropertyGetValueV1(
                            None,
                            None,
                            "#ff3333",
                            ColorValueV1("#ff3333"),
                            "http://data.knora.org/021ec18f1735/values/10ea6976338201",
                            None,
                            None))),
                PropertyGetV1(
                    "http://www.knora.org/ontology/knora-base#hasGeometry",
                    Some("Geometrie"),
                    Some("http://www.knora.org/ontology/knora-base#GeomValue"),
                    Some("textval"),
                    Some("geometry"),
                    "",
                    "0",
                    Vector(
                        PropertyGetValueV1(
                            None,
                            None,
                            "{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.08098591549295775,\"y\":0.16741071428571427},{\"x\":0.7394366197183099,\"y\":0.7299107142857143}],\"type\":\"rectangle\",\"original_index\":0}",
                            GeomValueV1("{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.08098591549295775,\"y\":0.16741071428571427},{\"x\":0.7394366197183099,\"y\":0.7299107142857143}],\"type\":\"rectangle\",\"original_index\":0}"),
                            "http://data.knora.org/021ec18f1735/values/4dc0163d338201",
                            None,
                            None))),
                PropertyGetV1(
                    "http://www.knora.org/ontology/knora-base#isRegionOf",
                    Some("is Region von"),
                    Some("http://www.knora.org/ontology/knora-base#LinkValue"),
                    Some("textval"),
                    None,
                    "restypeid=http://www.knora.org/ontology/knora-base#Representation",
                    "0",
                    Vector(
                        PropertyGetValueV1(
                            None,
                            None,
                            "http://data.knora.org/9d626dc76c03",
                            LinkV1(
                                "http://data.knora.org/9d626dc76c03",
                                Some("u1r"),
                                Some(
                                    "http://www.knora.org/ontology/0803/incunabula#page"),
                                None,
                                None),
                            "http://data.knora.org/021ec18f1735/values/fbcb88bf-cd16-4b7b-b843-51e17c0669d7",
                            None,
                            None))))))

    private val hasOtherThingIncomingLink = IncomingV1(
        value = Some("A thing that only project members can see"),
        resinfo = ResourceInfoV1(
            regions = None,
            firstproperty = Some("A thing that only project members can see"),
            locdata = None,
            locations = None,
            preview = None,
            restype_iconsrc = Some("http://localhost:3335/project-icons/anything/thing.png"),
            restype_description = Some("'The whole world is full of things, which means there's a real need for someone to go searching for them. And that's exactly what a thing-searcher does.' --Pippi Longstocking"),
            restype_label = Some("Ding"),
            restype_name = Some("http://www.knora.org/ontology/0001/anything#Thing"),
            restype_id = "http://www.knora.org/ontology/0001/anything#Thing",
            person_id = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
            project_id = "http://rdfh.ch/projects/0001"
        ),
        ext_res_id = ExternalResourceIDV1(
            pid = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
            id = "http://data.knora.org/project-thing-1"
        )
    )

    private val hasStandoffLinkToIncomingLink = IncomingV1(
        value = Some("A thing that only project members can see"),
        resinfo = ResourceInfoV1(
            regions = None,
            firstproperty = Some("A thing that only project members can see"),
            locdata = None,
            locations = None,
            preview = None,
            restype_iconsrc = Some("http://localhost:3335/project-icons/anything/thing.png"),
            restype_description = Some("'The whole world is full of things, which means there's a real need for someone to go searching for them. And that's exactly what a thing-searcher does.' --Pippi Longstocking"),
            restype_label = Some("Ding"),
            restype_name = Some("http://www.knora.org/ontology/0001/anything#Thing"),
            restype_id = "http://www.knora.org/ontology/0001/anything#Thing",
            person_id = "http://rdfh.ch/users/9XBCrDV3SRa7kS1WwynB4Q",
            project_id = "http://rdfh.ch/projects/0001"
        ),
        ext_res_id = ExternalResourceIDV1(
            pid = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo",
            id = "http://data.knora.org/project-thing-1"
        )
    )

    private val hasOtherThingOutgoingLink = PropertyV1(
        locations = Nil,
        value_rights = Vector(Some(8)),
        value_firstprops = Vector(Some("Another thing that only project members can see")),
        value_iconsrcs = Vector(Some("http://localhost:3335/project-icons/anything/thing.png")),
        value_restype = Vector(Some("Ding")),
        comments = Vector(None),
        value_ids = Vector("http://data.knora.org/project-thing-1/values/0"),
        values = Vector(LinkV1(
            valueResourceClassIcon = Some("http://localhost:3335/project-icons/anything/thing.png"),
            valueResourceClassLabel = Some("Ding"),
            valueResourceClass = Some("http://www.knora.org/ontology/0001/anything#Thing"),
            valueLabel = Some("Another thing that only project members can see"),
            targetResourceIri = "http://data.knora.org/project-thing-2"
        )),
        occurrence = Some("0-n"),
        attributes = "restypeid=http://www.knora.org/ontology/0001/anything#Thing",
        label = Some("Ein anderes Ding"),
        guielement = Some("searchbox"),
        guiorder = Some(1),
        valuetype_id = Some("http://www.knora.org/ontology/knora-base#LinkValue"),
        pid = "http://www.knora.org/ontology/0001/anything#hasOtherThing"
    )

    private val hasStandoffLinkToOutgoingLink = PropertyV1(
        locations = Nil,
        value_rights = Vector(Some(2)),
        value_firstprops = Vector(Some("Another thing that only project members can see")),
        value_iconsrcs = Vector(Some("http://localhost:3335/project-icons/anything/thing.png")),
        value_restype = Vector(Some("Ding")),
        comments = Vector(None),
        value_ids = Vector("http://data.knora.org/project-thing-1/values/1"),
        values = Vector(LinkV1(
            valueResourceClassIcon = Some("http://localhost:3335/project-icons/anything/thing.png"),
            valueResourceClassLabel = Some("Ding"),
            valueResourceClass = Some("http://www.knora.org/ontology/0001/anything#Thing"),
            valueLabel = Some("Another thing that only project members can see"),
            targetResourceIri = "http://data.knora.org/project-thing-2"
        )),
        occurrence = Some("0-n"),
        attributes = "restypeid=http://www.knora.org/ontology/knora-base#Resource",
        label = Some("hat Standoff Link zu"),
        guielement = None,
        guiorder = None,
        valuetype_id = Some("http://www.knora.org/ontology/knora-base#LinkValue"),
        pid = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo"
    )

    private val graphForAnythingUser1 = GraphDataGetResponseV1(
        edges = Vector(
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/tPfZeNMvRVujCQqbIbvO0A",
                source = "http://data.knora.org/nResNuvARcWYUdWyo0GWGw"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/cmfk1DMHRBiR4-_6HXpEFA",
                source = "http://data.knora.org/5IEswyQFQp2bxXDrOyEfEA"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/7uuGcnFcQJq08dMOralyCQ",
                source = "http://data.knora.org/sHCLAGg-R5qJ6oPZPV-zOQ"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/nResNuvARcWYUdWyo0GWGw",
                source = "http://data.knora.org/MiBwAFcxQZGHNL-WfgFAPQ"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/0C-0L1kORryKzJAJxxRyRQ",
                source = "http://data.knora.org/anything/start"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/iqW_PBiHRdyTFzik8tuSog",
                source = "http://data.knora.org/L5xU7Qe5QUu6Wz3cDaCxbA"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/anything/start",
                source = "http://data.knora.org/Lz7WEqJETJqqsUZQYexBQg"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/sHCLAGg-R5qJ6oPZPV-zOQ",
                source = "http://data.knora.org/Lz7WEqJETJqqsUZQYexBQg"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/WLSHxQUgTOmG1T0lBU2r5w",
                source = "http://data.knora.org/anything/start"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/A67ka6UQRHWf313tbhQBjw",
                source = "http://data.knora.org/WLSHxQUgTOmG1T0lBU2r5w"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/MiBwAFcxQZGHNL-WfgFAPQ",
                source = "http://data.knora.org/LOV-6aLYQFW15jwdyS51Yw"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/anything/start",
                source = "http://data.knora.org/iqW_PBiHRdyTFzik8tuSog"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/Lz7WEqJETJqqsUZQYexBQg",
                source = "http://data.knora.org/tPfZeNMvRVujCQqbIbvO0A"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/tPfZeNMvRVujCQqbIbvO0A",
                source = "http://data.knora.org/anything/start"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/L5xU7Qe5QUu6Wz3cDaCxbA",
                source = "http://data.knora.org/cmfk1DMHRBiR4-_6HXpEFA"
            )
        ),
        nodes = Vector(
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Tango",
                resourceIri = "http://data.knora.org/WLSHxQUgTOmG1T0lBU2r5w"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Foxtrot",
                resourceIri = "http://data.knora.org/Lz7WEqJETJqqsUZQYexBQg"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Echo",
                resourceIri = "http://data.knora.org/tPfZeNMvRVujCQqbIbvO0A"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Golf",
                resourceIri = "http://data.knora.org/sHCLAGg-R5qJ6oPZPV-zOQ"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Whiskey",
                resourceIri = "http://data.knora.org/MiBwAFcxQZGHNL-WfgFAPQ"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Mike",
                resourceIri = "http://data.knora.org/cmfk1DMHRBiR4-_6HXpEFA"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "X-ray",
                resourceIri = "http://data.knora.org/nResNuvARcWYUdWyo0GWGw"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Uniform",
                resourceIri = "http://data.knora.org/LOV-6aLYQFW15jwdyS51Yw"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Sierra",
                resourceIri = "http://data.knora.org/0C-0L1kORryKzJAJxxRyRQ"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Romeo",
                resourceIri = "http://data.knora.org/anything/start"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Quebec",
                resourceIri = "http://data.knora.org/iqW_PBiHRdyTFzik8tuSog"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Hotel",
                resourceIri = "http://data.knora.org/7uuGcnFcQJq08dMOralyCQ"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Papa",
                resourceIri = "http://data.knora.org/L5xU7Qe5QUu6Wz3cDaCxbA"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Victor",
                resourceIri = "http://data.knora.org/A67ka6UQRHWf313tbhQBjw"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Delta",
                resourceIri = "http://data.knora.org/5IEswyQFQp2bxXDrOyEfEA"
            )
        )
    )

    private val graphForIncunabulaUser = GraphDataGetResponseV1(
        edges = Vector(
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/tPfZeNMvRVujCQqbIbvO0A",
                source = "http://data.knora.org/nResNuvARcWYUdWyo0GWGw"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/nResNuvARcWYUdWyo0GWGw",
                source = "http://data.knora.org/MiBwAFcxQZGHNL-WfgFAPQ"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/0C-0L1kORryKzJAJxxRyRQ",
                source = "http://data.knora.org/anything/start"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/iqW_PBiHRdyTFzik8tuSog",
                source = "http://data.knora.org/L5xU7Qe5QUu6Wz3cDaCxbA"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/anything/start",
                source = "http://data.knora.org/Lz7WEqJETJqqsUZQYexBQg"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/sHCLAGg-R5qJ6oPZPV-zOQ",
                source = "http://data.knora.org/Lz7WEqJETJqqsUZQYexBQg"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/WLSHxQUgTOmG1T0lBU2r5w",
                source = "http://data.knora.org/anything/start"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/A67ka6UQRHWf313tbhQBjw",
                source = "http://data.knora.org/WLSHxQUgTOmG1T0lBU2r5w"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/MiBwAFcxQZGHNL-WfgFAPQ",
                source = "http://data.knora.org/LOV-6aLYQFW15jwdyS51Yw"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/anything/start",
                source = "http://data.knora.org/iqW_PBiHRdyTFzik8tuSog"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/Lz7WEqJETJqqsUZQYexBQg",
                source = "http://data.knora.org/tPfZeNMvRVujCQqbIbvO0A"
            ),
            GraphEdgeV1(
                propertyLabel = "Ein anderes Ding",
                propertyIri = "http://www.knora.org/ontology/0001/anything#hasOtherThing",
                target = "http://data.knora.org/tPfZeNMvRVujCQqbIbvO0A",
                source = "http://data.knora.org/anything/start"
            )
        ),
        nodes = Vector(
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Tango",
                resourceIri = "http://data.knora.org/WLSHxQUgTOmG1T0lBU2r5w"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Foxtrot",
                resourceIri = "http://data.knora.org/Lz7WEqJETJqqsUZQYexBQg"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Echo",
                resourceIri = "http://data.knora.org/tPfZeNMvRVujCQqbIbvO0A"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Golf",
                resourceIri = "http://data.knora.org/sHCLAGg-R5qJ6oPZPV-zOQ"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Whiskey",
                resourceIri = "http://data.knora.org/MiBwAFcxQZGHNL-WfgFAPQ"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "X-ray",
                resourceIri = "http://data.knora.org/nResNuvARcWYUdWyo0GWGw"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Uniform",
                resourceIri = "http://data.knora.org/LOV-6aLYQFW15jwdyS51Yw"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Sierra",
                resourceIri = "http://data.knora.org/0C-0L1kORryKzJAJxxRyRQ"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Romeo",
                resourceIri = "http://data.knora.org/anything/start"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Quebec",
                resourceIri = "http://data.knora.org/iqW_PBiHRdyTFzik8tuSog"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Papa",
                resourceIri = "http://data.knora.org/L5xU7Qe5QUu6Wz3cDaCxbA"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Victor",
                resourceIri = "http://data.knora.org/A67ka6UQRHWf313tbhQBjw"
            )
        )
    )

    private val graphWithStandoffLink = GraphDataGetResponseV1(
        edges = Vector(GraphEdgeV1(
            propertyLabel = "hat Standoff Link zu",
            propertyIri = "http://www.knora.org/ontology/knora-base#hasStandoffLinkTo",
            target = "http://data.knora.org/a-thing",
            source = "http://data.knora.org/a-thing-with-text-values"
        )),
        nodes = Vector(
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "Ein Ding f\u00FCr jemanden, dem die Dinge gefallen",
                resourceIri = "http://data.knora.org/a-thing-with-text-values"
            ),
            GraphNodeV1(
                resourceClassLabel = "Ding",
                resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
                resourceLabel = "A thing",
                resourceIri = "http://data.knora.org/a-thing"
            )
        )
    )

    private val graphWithOneNode = GraphDataGetResponseV1(
        edges = Nil,
        nodes = Vector(GraphNodeV1(
            resourceClassLabel = "Ding",
            resourceClassIri = "http://www.knora.org/ontology/0001/anything#Thing",
            resourceLabel = "Another thing",
            resourceIri = "http://data.knora.org/another-thing"
        ))
    )
}


/**
  * Tests [[ResourcesResponderV1]].
  */
class ResourcesResponderV1Spec extends CoreSpec(ResourcesResponderV1Spec.config) with ImplicitSender {
    import ResourcesResponderV1Spec._

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[ResourcesResponderV1]

    private val responderManager = system.actorOf(Props(new TestResponderManager(Map(SIPI_ROUTER_V1_ACTOR_NAME -> system.actorOf(Props(new MockSipiResponderV1))))), name = RESPONDER_MANAGER_ACTOR_NAME)

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 60.seconds

    private val newBookResourceIri = new MutableTestIri
    private val newPageResourceIri = new MutableTestIri

    private def compareResourceFullResponses(received: ResourceFullResponseV1, expected: ResourceFullResponseV1): Unit = {
        // println(MessageUtil.toSource(received))

        assert(received.access == expected.access, "access does not match")
        assert(received.resinfo == expected.resinfo, "resinfo does not match")
        assert(received.resdata == expected.resdata, "resdata does not match")
        assert(received.incoming == expected.incoming, "incoming does not match")

        val sortedReceivedProps = received.props.get.properties.sortBy(_.pid)
        val sortedExpectedProps = expected.props.get.properties.sortBy(_.pid)

        assert(sortedReceivedProps.length == sortedExpectedProps.length, s"\n********** expected these properties:\n${MessageUtil.toSource(sortedExpectedProps)}\n********** received these properties:\n${MessageUtil.toSource(sortedReceivedProps)}")

        sortedExpectedProps.zip(sortedReceivedProps).foreach {
            case (expectedProp: PropertyV1, receivedProp: PropertyV1) =>

                // sort property attributes
                val expectedPropWithSortedAttr = expectedProp.copy(
                    attributes = expectedProp.attributes.sorted
                )

                val receivedPropWithSortedAttr = receivedProp.copy(
                    attributes = receivedProp.attributes.sorted
                )

                assert(receivedPropWithSortedAttr == expectedPropWithSortedAttr, s"These props do not match:\n********** Expected:\n${MessageUtil.toSource(expectedProp)}\n********** Received:\n${MessageUtil.toSource(receivedProp)}")
        }
    }

    private def compareResourcePartOfContextResponses(received: ResourceContextResponseV1, expected: ResourceContextResponseV1): Unit = {
        val receivedContext = received.resource_context
        val expectedContext = expected.resource_context

        assert(receivedContext.resinfo == expectedContext.resinfo, "resinfo does not match")
        assert(receivedContext.parent_res_id == expectedContext.parent_res_id, "parent_res_id does not match")
        assert(receivedContext.context == expectedContext.context, "context does not match")
        assert(receivedContext.canonical_res_id == expectedContext.canonical_res_id, "canonical_res_id does not match")
        assert(receivedContext.parent_resinfo == expectedContext.parent_resinfo, "parent_resinfo does not match")
    }


    private def compareResourceSearchResults(received: ResourceSearchResponseV1, expected: ResourceSearchResponseV1): Unit = {

        assert(received.resources == expected.resources, "resources did not match")
    }

    private def checkResourceCreation(received: ResourceCreateResponseV1, expected: Map[IRI, Seq[ApiValueV1]]): Unit = {
        // sort values by their string representation
        val sortedValuesReceived: Map[IRI, Seq[ResourceCreateValueResponseV1]] = received.results.map {
            case (propIri, propValues: Seq[ResourceCreateValueResponseV1]) => (propIri, propValues.sortBy {
                valueObject: ResourceCreateValueResponseV1 =>
                    val stringValue = valueObject.value.textval.map {
                        case (valType: LiteralValueType.Value, value: String) => value // get string and ignore value type
                    }.head // each value is represented by a map consisting of only one item (e.g. string -> "book title")
                    stringValue
            })
        }

        // sort values by their string representation
        val sortedValuesExpected: Map[IRI, Seq[ResourceCreateValueResponseV1]] = expected.map {
            case (propIri, propValues) => (propIri, propValues.sortBy(_.toString))
        }.map {
            // turn the expected ApiValueV1s in ResourceCreateValueResponseV1 (these are returned by the actor).
            case (propIri: IRI, propValues: Seq[ApiValueV1]) =>
                (propIri, propValues.map {
                    case (propValue: ApiValueV1) =>
                        val valueResponse = CreateValueResponseV1(
                            value = propValue,
                            rights = 6,
                            id = "http://www.knora.org/test/values/test"
                        )

                        // convert CreateValueResponseV1 to a ResourceCreateValueResponseV1
                        MessageUtil.convertCreateValueResponseV1ToResourceCreateValueResponseV1(
                            resourceIri = "http://www.knora.org/test",
                            creatorIri = "http://rdfh.ch/users/b83acc5f05",
                            propertyIri = propIri,
                            valueResponse = valueResponse
                        )
                })
        }

        // compare expected and received values
        sortedValuesExpected.foreach {
            case (propIri, propValuesExpected) =>
                (propValuesExpected, sortedValuesReceived(propIri)).zipped.foreach {
                    case (expected: ResourceCreateValueResponseV1, received: ResourceCreateValueResponseV1) =>
                        assert(received.value.textval == expected.value.textval, "textval did not match")
                        assert(received.value.ival == expected.value.ival, "ival did not match")
                        assert(received.value.dval == expected.value.dval, "dval did not match")
                        assert(received.value.dateval1 == expected.value.dateval1, "dateval1 did not match")
                        assert(received.value.dateval2 == expected.value.dateval2, "dateval2 did not match")
                        assert(received.value.calendar == expected.value.calendar, "calendar did not match")
                        assert(received.value.dateprecision1 == expected.value.dateprecision1, "dateprecision1 did not match")
                        assert(received.value.dateprecision2 == expected.value.dateprecision2, "dateprecision2 did not match")
                        assert(received.value.timeval1 == expected.value.timeval1, "timeval1 did not match")
                        assert(received.value.timeval2 == expected.value.timeval2, "timeval2 did not match")
                }
        }

    }

    private def getLastModificationDate(resourceIri: IRI): Option[String] = {
        val lastModSparqlQuery = queries.sparql.v1.txt.getLastModificationDate(
            triplestore = settings.triplestoreType,
            resourceIri = resourceIri
        ).toString()

        storeManager ! SparqlSelectRequest(lastModSparqlQuery)

        expectMsgPF(timeout) {
            case response: SparqlSelectResponse =>
                val rows = response.results.bindings
                assert(rows.size <= 1, s"Resource $resourceIri has more than one instance of knora-base:lastModificationDate")

                if (rows.size == 1) {
                    Some(rows.head.rowMap("lastModificationDate"))
                } else {
                    None
                }
        }
    }

    private def comparePropertiesGetResponse(received: PropertiesGetResponseV1, expected: PropertiesGetResponseV1): Unit = {

        assert(received.properties.properties.length == expected.properties.properties.length, "The length of given properties is not correct.")

        expected.properties.properties.sortBy {
            // sort by property Iri
            prop => prop.pid
        }.zip(received.properties.properties.sortBy {
            prop => prop.pid
        }).foreach {
            case (expectedProp: PropertyGetV1, receivedProp: PropertyGetV1) =>

                // sort the values of each property
                val expectedPropValuesSorted = expectedProp.values.sortBy(values => values.textval)

                val receivedPropValuesSorted = receivedProp.values.sortBy(values => values.textval)

                // create PropertyGetV1 with sorted values
                val expectedPropSorted = expectedProp.copy(
                    values = expectedPropValuesSorted
                )

                val receivedPropSorted = receivedProp.copy(
                    values = receivedPropValuesSorted
                )

                assert(receivedPropSorted == expectedPropSorted, "Property did not match")
        }
    }

    private def comparePageContextRegionResponse(received: ResourceContextResponseV1): Unit = {

        assert(received.resource_context.resinfo.nonEmpty)

        assert(received.resource_context.resinfo.get.regions.nonEmpty)

        assert(received.resource_context.resinfo.get.regions.get.length == 2, "Number of given regions is not correct.")

        val regions: Seq[PropsGetForRegionV1] = received.resource_context.resinfo.get.regions.get

        val region1 = regions.filter {
            region => region.res_id == "http://data.knora.org/021ec18f1735"
        }

        val region2 = regions.filter {
            region => region.res_id == "http://data.knora.org/b6b64a62b006"
        }

        assert(region1.length == 1, "No region found with Iri 'http://data.knora.org/021ec18f1735'")

        assert(region2.length == 1, "No region found with Iri 'http://data.knora.org/b6b64a62b006'")

    }

    private def compareNewPageContextResponse(received: ResourceContextResponseV1): Unit = {

        assert(received.resource_context.resinfo.nonEmpty)

        // check that there is a preview
        assert(received.resource_context.resinfo.get.preview.nonEmpty)

        assert(received.resource_context.resinfo.get.locations.nonEmpty)

        // check that there are 7 locations
        assert(received.resource_context.resinfo.get.locations.get.length == 7)

    }

    private def checkPermissionsOnResource(resourceIri: IRI): Unit = {

        val expected = Set(
            PermissionADM.changeRightsPermission("http://www.knora.org/ontology/knora-base#Creator"),
            PermissionADM.modifyPermission("http://www.knora.org/ontology/knora-base#ProjectMember"),
            PermissionADM.viewPermission("http://www.knora.org/ontology/knora-base#KnownUser"),
            PermissionADM.restrictedViewPermission("http://www.knora.org/ontology/knora-base#UnknownUser")
        )

        responderManager ! ObjectAccessPermissionsForResourceGetADM(resourceIri = newBookResourceIri.get, requestingUser = KnoraSystemInstances.Users.SystemUser)
        expectMsgPF(timeout) {
            case Some(permission) => {
                val perms = permission.asInstanceOf[ObjectAccessPermissionADM].hasPermissions
                perms should contain allElementsOf expected
                perms.size should equal(expected.size)
            }
            case _ => fail("No ObjectAccessPermission returned!")
        }


    }

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(SharedTestDataADM.rootUser)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The resources responder" should {
        "return a full description of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {
            // http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a
            actorUnderTest ! ResourceFullGetRequestV1(iri = "http://data.knora.org/c5058f3a", userProfile = SharedTestDataADM.incunabulaMemberUser)

            expectMsgPF(timeout) {
                case response: ResourceFullResponseV1 => compareResourceFullResponses(received = response, expected = ResourcesResponderV1SpecFullData.expectedBookResourceFullResponse)
            }
        }

        "return a full description of the first page of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {
            // http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2F8a0b1e75
            actorUnderTest ! ResourceFullGetRequestV1(iri = "http://data.knora.org/8a0b1e75", userProfile = SharedTestDataADM.incunabulaMemberUser)

            expectMsgPF(timeout) {
                case response: ResourceFullResponseV1 => compareResourceFullResponses(received = response, expected = ResourcesResponderV1SpecFullData.expectedPageResourceFullResponse)
            }
        }

        "return a region with a comment containing standoff information" in {
            // http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2F047db418ae06
            actorUnderTest ! ResourceFullGetRequestV1(iri = "http://data.knora.org/047db418ae06", userProfile = SharedTestDataADM.incunabulaMemberUser)

            expectMsgPF(timeout) {
                case response: ResourceFullResponseV1 =>
                // compareResourceFullResponses(received = response, expected = ResourcesResponderV1SpecFullData.expectedRegionFullResource)
            }
        }

        "return the context (describing 402 pages) of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {
            // http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?reqtype=context&resinfo=true
            actorUnderTest ! ResourceContextGetRequestV1(iri = "http://data.knora.org/c5058f3a", resinfo = true, userProfile = SharedTestDataADM.incunabulaProjectAdminUser)

            expectMsgPF(timeout) {
                case response: ResourceContextResponseV1 =>
                    val responseAsJson = response.toJsValue
                    assert(responseAsJson == ResourcesResponderV1SpecContextData.expectedBookResourceContextResponse, "book context response did not match")
            }
        }

        "return the context of a page of the book 'Zeitglöcklein des Lebens und Leidens Christi' in the Incunabula test data" in {
            // http://localhost:3333/v1/resources/http%3A%2F%2Fdata.knora.org%2F8a0b1e75?reqtype=context&resinfo=true
            actorUnderTest ! ResourceContextGetRequestV1(iri = "http://data.knora.org/8a0b1e75", resinfo = true, userProfile = SharedTestDataADM.incunabulaProjectAdminUser)

            expectMsgPF(timeout) {
                case response: ResourceContextResponseV1 => compareResourcePartOfContextResponses(received = response, expected = ResourcesResponderV1SpecContextData.expectedPageResourceContextResponse)
            }
        }

        "return 1 resource containing 'Reise in' in its label with three of its values" in {
            // http://localhost:3333/v1/resources?searchstr=Reise+in&numprops=3&limit=11&restype_id=-1
            actorUnderTest ! ResourceSearchGetRequestV1(
                searchString = "Reise in",
                numberOfProps = 3,
                limitOfResults = 11,
                resourceTypeIri = None,
                userProfile = SharedTestDataADM.incunabulaMemberUser
            )

            expectMsgPF(timeout) {
                case response: ResourceSearchResponseV1 => compareResourceSearchResults(received = response, expected = ReiseInsHeiligelandThreeValues)
            }
        }

        "return 1 resource of type incunabula:book containing 'Reis' in its label with its label (first property)" in {
            // http://localhost:3333/v1/resources?searchstr=Reis&numprops=1&limit=11&restype_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book
            actorUnderTest ! ResourceSearchGetRequestV1(
                searchString = "Reis",
                numberOfProps = 1,
                limitOfResults = 11,
                resourceTypeIri = Some("http://www.knora.org/ontology/0803/incunabula#book"),
                userProfile = SharedTestDataADM.incunabulaMemberUser
            )

            expectMsgPF(timeout) {
                case response: ResourceSearchResponseV1 => compareResourceSearchResults(received = response, expected = ReiseInsHeiligelandOneValueRestrictedToBook)
            }
        }

        "return 27 resources containing 'Narrenschiff' in their label" in {
            //http://localhost:3333/v1/resources?searchstr=Narrenschiff&numprops=4&limit=100&restype_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book

            // This query is going to return also resources of knora-baseLinkObj with a knora-base:hasComment.
            // Because this resource is directly defined in knora-base, its property knora-base:hasComment
            // has no guiOrder (normally, the guiOrder is defined in project specific ontologies) which used to cause problems in the SPARQL query.
            // Now, the guiOrder was made optional in the SPARQL query, and this test ensures that the query works as expected.

            actorUnderTest ! ResourceSearchGetRequestV1(
                searchString = "Narrenschiff",
                numberOfProps = 4,
                limitOfResults = 100,
                userProfile = SharedTestDataADM.incunabulaProjectAdminUser,
                resourceTypeIri = None
            )

            expectMsgPF(timeout) {
                case response: ResourceSearchResponseV1 =>
                    assert(response.resources.size == 27, s"expected 27 resources")
            }
        }

        "return 3 resources containing 'Narrenschiff' in their label of type incunabula:book" in {
            //http://localhost:3333/v1/resources?searchstr=Narrenschiff&numprops=3&limit=100&restype_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book

            actorUnderTest ! ResourceSearchGetRequestV1(
                searchString = "Narrenschiff",
                numberOfProps = 3,
                limitOfResults = 100,
                userProfile = SharedTestDataADM.incunabulaProjectAdminUser,
                resourceTypeIri = Some("http://www.knora.org/ontology/0803/incunabula#book")
            )

            expectMsgPF(timeout) {
                case response: ResourceSearchResponseV1 =>
                    assert(response.resources.size == 3, s"expected 3 resources")
            }
        }

        "return 19 resources containing 'a1r' in their label of type incunabula:page" in {
            //http://localhost:3333/v1/resources?searchstr=a1r&numprops=3&limit=100&restype_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page

            actorUnderTest ! ResourceSearchGetRequestV1(
                searchString = "a1r",
                numberOfProps = 3,
                limitOfResults = 100,
                userProfile = SharedTestDataADM.incunabulaMemberUser,
                resourceTypeIri = Some("http://www.knora.org/ontology/0803/incunabula#page")
            )

            expectMsgPF(timeout) {
                case response: ResourceSearchResponseV1 =>
                    assert(response.resources.size == 19, s"expected 19 resources")
            }
        }

        "return 19 resources containing 'a1r' in their label of type knora-base:Representation" in {
            //http://localhost:3333/v1/resources?searchstr=a1r&numprops=3&limit=100&restype_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fknora-base%23Representation

            actorUnderTest ! ResourceSearchGetRequestV1(
                searchString = "a1r",
                numberOfProps = 3,
                limitOfResults = 100,
                userProfile = SharedTestDataADM.incunabulaMemberUser,
                resourceTypeIri = Some("http://www.knora.org/ontology/knora-base#Representation")
            )

            expectMsgPF(timeout) {
                case response: ResourceSearchResponseV1 =>
                    assert(response.resources.size == 19, s"expected 19 resources")
            }
        }

        "not create a resource when too many values are submitted for a property" in {
            // An incunabula:misc allows at most one color value.
            val valuesToBeCreated = Map(
                "http://www.knora.org/ontology/0803/incunabula#miscHasColor" -> Vector(
                    CreateValueV1WithComment(ColorValueV1("#000000")),
                    CreateValueV1WithComment(ColorValueV1("#FFFFFF"))
                )
            )

            val resourceCreateRequest = ResourceCreateRequestV1(
                resourceTypeIri = "http://www.knora.org/ontology/0803/incunabula#misc",
                label = "Test-Misc",
                projectIri = "http://rdfh.ch/projects/0803",
                values = valuesToBeCreated,
                userProfile = SharedTestDataADM.incunabulaProjectAdminUser,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! resourceCreateRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "not create a resource when a required value is not submitted" in {
            // Title and publoc are required but missing

            val author = Vector(
                CreateValueV1WithComment(TextValueSimpleV1(utf8str = "Franciscus de Retza"), None)
            )

            val pubdate = Vector(
                DateValueV1(
                    dateval1 = "1487",
                    dateval2 = "1490",
                    era1 = "CE",
                    era2 = "CE",
                    calendar = KnoraCalendarV1.JULIAN
                )
            )

            val valuesToBeCreated = Map(
                "http://www.knora.org/ontology/0803/incunabula#hasAuthor" -> author,
                "http://www.knora.org/ontology/0803/incunabula#pubdate" -> pubdate.map(date => CreateValueV1WithComment(DateUtilV1.dateValueV1ToJulianDayNumberValueV1(date), None))
            )

            val resourceCreateRequest = ResourceCreateRequestV1(
                resourceTypeIri = "http://www.knora.org/ontology/0803/incunabula#book",
                label = "Test-Book",
                projectIri = "http://rdfh.ch/projects/0803",
                values = valuesToBeCreated,
                userProfile = SharedTestDataADM.incunabulaProjectAdminUser,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! resourceCreateRequest

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "not create a resource containing a text value with a standoff reference to a nonexistent resource" in {
            val nonexistentIri = "http://data.knora.org/nonexistent"

            val title1 = TextValueSimpleV1("A beautiful book")

            val citation1 = TextValueWithStandoffV1(
                utf8str = "This comment refers to another resource",
                standoff = Vector(
                    StandoffTagV1(
                        standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag,
                        dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                        startPosition = 31,
                        endPosition = 39,
                        startIndex = 0,
                        attributes = Vector(StandoffTagIriAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink, value = nonexistentIri)),
                        uuid = UUID.randomUUID().toString,
                        originalXMLID = None
                    )
                ),
                resource_reference = Set(nonexistentIri),
                mapping = ResourcesResponderV1SpecFullData.dummyMapping,
                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping"
            )

            val publoc = TextValueSimpleV1("Entenhausen")

            val pubdate = DateUtilV1.createJDNValueV1FromDateString("GREGORIAN:2015-12-03")

            val valuesToBeCreated: Map[IRI, Seq[CreateValueV1WithComment]] = Map(
                "http://www.knora.org/ontology/0803/incunabula#title" -> Vector(CreateValueV1WithComment(title1)),
                "http://www.knora.org/ontology/0803/incunabula#pubdate" -> Vector(CreateValueV1WithComment(pubdate)),
                "http://www.knora.org/ontology/0803/incunabula#citation" -> Vector(
                    CreateValueV1WithComment(citation1, None)
                ),
                "http://www.knora.org/ontology/0803/incunabula#publoc" -> Vector(CreateValueV1WithComment(publoc))
            )

            actorUnderTest ! ResourceCreateRequestV1(
                resourceTypeIri = "http://www.knora.org/ontology/0803/incunabula#book",
                label = "Book with reference to nonexistent resource",
                projectIri = "http://rdfh.ch/projects/0803",
                values = valuesToBeCreated,
                userProfile = SharedTestDataADM.incunabulaProjectAdminUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "create a new resource of type incunabula:book with values" in {

            val title1 = TextValueSimpleV1("A beautiful book")

            val citation1 = TextValueSimpleV1("ein Zitat")
            val citation2 = TextValueWithStandoffV1(
                utf8str = "This citation refers to another resource",
                standoff = Vector(
                    StandoffTagV1(
                        standoffTagClassIri = OntologyConstants.Standoff.StandoffBoldTag,
                        startPosition = 5,
                        endPosition = 13,
                        uuid = UUID.randomUUID().toString,
                        originalXMLID = None,
                        startIndex = 0
                    ),
                    StandoffTagV1(
                        standoffTagClassIri = OntologyConstants.KnoraBase.StandoffLinkTag,
                        dataType = Some(StandoffDataTypeClasses.StandoffLinkTag),
                        startPosition = 32,
                        endPosition = 40,
                        attributes = Vector(StandoffTagIriAttributeV1(standoffPropertyIri = OntologyConstants.KnoraBase.StandoffTagHasLink, value = "http://data.knora.org/c5058f3a")),
                        uuid = UUID.randomUUID().toString,
                        originalXMLID = None,
                        startIndex = 0
                    )
                ),
                mapping = ResourcesResponderV1SpecFullData.dummyMapping,
                mappingIri = "http://rdfh.ch/standoff/mappings/StandardMapping",
                resource_reference = Set("http://data.knora.org/c5058f3a")
            )
            val citation3 = TextValueSimpleV1("und noch eines")
            val citation4 = TextValueSimpleV1("noch ein letztes")

            val publoc = TextValueSimpleV1("Entenhausen")

            val pubdateRequest = DateUtilV1.createJDNValueV1FromDateString("GREGORIAN:2015-12-03")
            val pubdateResponse = DateValueV1(dateval1 = "2015-12-03", dateval2 = "2015-12-03", era1="CE",era2="CE", calendar = KnoraCalendarV1.GREGORIAN)

            val valuesToBeCreated: Map[IRI, Seq[CreateValueV1WithComment]] = Map(
                "http://www.knora.org/ontology/0803/incunabula#title" -> Vector(CreateValueV1WithComment(title1)),
                "http://www.knora.org/ontology/0803/incunabula#pubdate" -> Vector(CreateValueV1WithComment(pubdateRequest)),
                "http://www.knora.org/ontology/0803/incunabula#citation" -> Vector(
                    CreateValueV1WithComment(citation4, None),
                    CreateValueV1WithComment(citation1, None),
                    CreateValueV1WithComment(citation3, None),
                    CreateValueV1WithComment(citation2, None)
                ),
                "http://www.knora.org/ontology/0803/incunabula#publoc" -> Vector(CreateValueV1WithComment(publoc))
            )

            val valuesExpected = Map(
                "http://www.knora.org/ontology/0803/incunabula#title" -> Vector(title1),
                "http://www.knora.org/ontology/0803/incunabula#pubdate" -> Vector(pubdateResponse),
                "http://www.knora.org/ontology/0803/incunabula#citation" -> Vector(citation3, citation1, citation4, citation2),
                "http://www.knora.org/ontology/0803/incunabula#publoc" -> Vector(publoc)
            )


            actorUnderTest ! ResourceCreateRequestV1(
                resourceTypeIri = INCUNABULA_BOOK_RESOURCE_CLASS,
                label = "Test-Book",
                projectIri = SharedTestDataADM.INCUNABULA_PROJECT_IRI,
                values = valuesToBeCreated,
                userProfile = SharedTestDataADM.incunabulaProjectAdminUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case response: ResourceCreateResponseV1 =>
                    newBookResourceIri.set(response.res_id)
                    checkResourceCreation(received = response, expected = valuesExpected)
            }

            // Check that the resource doesn't have more than one lastModificationDate.
            getLastModificationDate(newBookResourceIri.get)


            /* Check the permissions on the resource */
            checkPermissionsOnResource(newBookResourceIri.get)


            // See if we can query the resource.
            actorUnderTest ! ResourceFullGetRequestV1(iri = newBookResourceIri.get, userProfile = SharedTestDataADM.incunabulaProjectAdminUser)
            expectMsgPF(timeout) {
                case response: ResourceFullResponseV1 => () // If we got a ResourceFullResponseV1, the operation succeeded.
            }
        }

        "create an incunabula:page with a resource pointer" in {
            val recto = TextValueSimpleV1("recto")
            val origname = TextValueSimpleV1("Blatt")
            val seqnum = IntegerValueV1(1)

            val fileValueFull = StillImageFileValueV1(
                internalMimeType = "image/jp2",
                internalFilename = "gaga.jpg",
                originalFilename = "test.jpg",
                originalMimeType = Some("image/jpg"),
                dimX = 1000,
                dimY = 1000,
                qualityLevel = 100,
                qualityName = Some("full"),
                isPreview = false
            )

            val fileValueThumb = StillImageFileValueV1(
                internalMimeType = "image/jpeg",
                internalFilename = "gaga.jpg",
                originalFilename = "test.jpg",
                originalMimeType = Some("image/jpg"),
                dimX = 100,
                dimY = 100,
                qualityLevel = 10,
                qualityName = Some("thumbnail"),
                isPreview = true
            )

            val book = newBookResourceIri.get

            val valuesToBeCreated = Map(
                "http://www.knora.org/ontology/0803/incunabula#hasRightSideband" -> Vector(CreateValueV1WithComment(LinkUpdateV1(targetResourceIri = "http://data.knora.org/482a33d65c36"))),
                "http://www.knora.org/ontology/0803/incunabula#pagenum" -> Vector(CreateValueV1WithComment(recto)),
                "http://www.knora.org/ontology/0803/incunabula#partOf" -> Vector(CreateValueV1WithComment(LinkUpdateV1(book))),
                "http://www.knora.org/ontology/0803/incunabula#origname" -> Vector(CreateValueV1WithComment(origname)),
                "http://www.knora.org/ontology/0803/incunabula#seqnum" -> Vector(CreateValueV1WithComment(seqnum))
            )

            val expected = Map(
                "http://www.knora.org/ontology/0803/incunabula#hasRightSideband" -> Vector(LinkV1(targetResourceIri = "http://data.knora.org/482a33d65c36", valueResourceClass = Some("http://www.knora.org/ontology/0803/incunabula#Sideband"))),
                "http://www.knora.org/ontology/0803/incunabula#pagenum" -> Vector(recto),
                "http://www.knora.org/ontology/0803/incunabula#partOf" -> Vector(LinkV1(book)),
                "http://www.knora.org/ontology/0803/incunabula#origname" -> Vector(origname),
                "http://www.knora.org/ontology/0803/incunabula#seqnum" -> Vector(seqnum),
                OntologyConstants.KnoraBase.HasStillImageFileValue -> Vector(fileValueFull, fileValueThumb)
            )

            actorUnderTest ! ResourceCreateRequestV1(
                resourceTypeIri = INCUNABULA_PAGE_RESOURCE_CLASS,
                label = "Test-Page",
                projectIri = SharedTestDataADM.INCUNABULA_PROJECT_IRI,
                values = valuesToBeCreated,
                file = Some(SipiResponderConversionFileRequestV1(
                    originalFilename = "test.jpg",
                    originalMimeType = "image/jpeg",
                    filename = "./test_server/images/Chlaus.jpg",
                    userProfile = SharedTestDataADM.incunabulaProjectAdminUser.asUserProfileV1
                )),
                userProfile = SharedTestDataADM.incunabulaProjectAdminUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case response: ResourceCreateResponseV1 =>
                    newPageResourceIri.set(response.res_id)
                    checkResourceCreation(received = response, expected = expected)
            }

            /* Check the permissions on the resource */
            checkPermissionsOnResource(newPageResourceIri.get)

        }

        "get the context of a newly created incunabula:page and check its locations" in {

            val resIri: IRI = newPageResourceIri.get

            val pageGetContext = ResourceContextGetRequestV1(
                iri = resIri,
                resinfo = true,
                userProfile = SharedTestDataADM.incunabulaProjectAdminUser
            )

            actorUnderTest ! pageGetContext

            expectMsgPF(timeout) {
                case response: ResourceContextResponseV1 =>
                    compareNewPageContextResponse(received = response)
            }
        }

        "mark a resource as deleted" in {
            val lastModBeforeUpdate = getLastModificationDate(newPageResourceIri.get)

            val resourceDeleteRequest = ResourceDeleteRequestV1(
                resourceIri = newPageResourceIri.get,
                deleteComment = Some("This page was deleted as a test"),
                userProfile = SharedTestDataADM.incunabulaProjectAdminUser,
                apiRequestID = UUID.randomUUID
            )

            actorUnderTest ! resourceDeleteRequest

            expectMsg(timeout, ResourceDeleteResponseV1(id = newPageResourceIri.get))

            // Check that the resource is marked as deleted.
            actorUnderTest ! ResourceInfoGetRequestV1(iri = newPageResourceIri.get, userProfile = SharedTestDataADM.incunabulaProjectAdminUser)

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }

            // Check that the resource's last modification date got updated.
            val lastModAfterUpdate = getLastModificationDate(newPageResourceIri.get)
            lastModBeforeUpdate != lastModAfterUpdate should ===(true)
        }

        "get the properties of a resource" in {

            val propertiesGetRequest = PropertiesGetRequestV1(
                "http://data.knora.org/021ec18f1735",
                SharedTestDataADM.incunabulaProjectAdminUser
            )

            actorUnderTest ! propertiesGetRequest

            expectMsgPF(timeout) {
                case response: PropertiesGetResponseV1 => comparePropertiesGetResponse(received = response, expected = propertiesGetResponseV1Region)
            }
        }

        "get the regions of a page pointed to by regions" in {

            val resourceContextPage = ResourceContextGetRequestV1(iri = "http://data.knora.org/9d626dc76c03", resinfo = true, userProfile = SharedTestDataADM.incunabulaProjectAdminUser)

            actorUnderTest ! resourceContextPage

            expectMsgPF(timeout) {
                case response: ResourceContextResponseV1 => comparePageContextRegionResponse(received = response)
            }
        }

        "show incoming standoff links if the user has view permission on both resources, but show other incoming links only if the user also has view permission on the link" in {
            // The link's owner, anythingUser1, should see the hasOtherThing link as well as the hasStandoffLinkTo link.

            actorUnderTest ! ResourceFullGetRequestV1(iri = "http://data.knora.org/project-thing-2", userProfile = SharedTestDataADM.anythingUser1)

            expectMsgPF(timeout) {
                case response: ResourceFullResponseV1 =>
                    response.incoming.size should ===(2)
                    response.incoming.contains(hasStandoffLinkToIncomingLink) should ===(true)
                    response.incoming.contains(hasOtherThingIncomingLink) should ===(true)
            }

            // But another user should see only the hasStandoffLinkTo link.

            actorUnderTest ! ResourceFullGetRequestV1(iri = "http://data.knora.org/project-thing-2", userProfile = SharedTestDataADM.anythingUser2)

            expectMsgPF(timeout) {
                case response: ResourceFullResponseV1 =>
                    response.incoming.contains(hasStandoffLinkToIncomingLink) should ===(true)
                    response.incoming.contains(hasOtherThingIncomingLink) should ===(false)
            }
        }

        "show outgoing standoff links if the user has view permission on both resources, but show other outgoing links only if the user also has view permission on the link" in {
            // The link's owner, anythingUser1, should see the hasOtherThing link as well as the hasStandoffLinkTo link.

            actorUnderTest ! ResourceFullGetRequestV1(iri = "http://data.knora.org/project-thing-1", userProfile = SharedTestDataADM.anythingUser1)

            expectMsgPF(timeout) {
                case response: ResourceFullResponseV1 =>
                    val linkProps = response.props.get.properties.filter {
                        prop => prop.values.nonEmpty && prop.valuetype_id.get != OntologyConstants.KnoraBase.TextValue
                    }

                    linkProps.size should ===(2)

                    linkProps.contains(hasStandoffLinkToOutgoingLink) should ===(true)
                    linkProps.contains(hasOtherThingOutgoingLink) should ===(true)
            }

            // But another user should see only the hasStandoffLinkTo link.

            actorUnderTest ! ResourceFullGetRequestV1(iri = "http://data.knora.org/project-thing-1", userProfile = SharedTestDataADM.anythingUser2)

            expectMsgPF(timeout) {
                case response: ResourceFullResponseV1 =>
                    val linkProps = response.props.get.properties.filter {
                        prop => prop.values.nonEmpty && prop.valuetype_id.get != OntologyConstants.KnoraBase.TextValue
                    }

                    linkProps.size should ===(1)
                    linkProps.contains(hasStandoffLinkToOutgoingLink) should ===(true)
                    linkProps.contains(hasOtherThingOutgoingLink) should ===(false)
            }
        }

        "show a contained resource in a context request only if the user has permission to see the containing resource, the contained resource, and the link value" in {
            // The owner of the resources and the link should see two contained resources.

            actorUnderTest ! ResourceContextGetRequestV1(iri = "http://data.knora.org/containing-thing", resinfo = true, userProfile = SharedTestDataADM.anythingUser1)

            expectMsgPF(timeout) {
                case response: ResourceContextResponseV1 =>
                    response.resource_context.res_id should ===(Some(Vector(
                        "http://data.knora.org/contained-thing-1",
                        "http://data.knora.org/contained-thing-2"
                    )))
            }

            // Another user in the project, who doesn't have permission to see the second link, should see only one contained resource.

            actorUnderTest ! ResourceContextGetRequestV1(iri = "http://data.knora.org/containing-thing", resinfo = true, userProfile = SharedTestDataADM.anythingUser2)

            expectMsgPF(timeout) {
                case response: ResourceContextResponseV1 =>
                    response.resource_context.res_id should ===(Some(Vector("http://data.knora.org/contained-thing-1")))
            }

            // A user who's not in the project shouldn't see any contained resources.

            actorUnderTest ! ResourceContextGetRequestV1(iri = "http://data.knora.org/containing-thing", resinfo = true, userProfile = SharedTestDataADM.incunabulaProjectAdminUser)

            expectMsgPF(timeout) {
                case response: ResourceContextResponseV1 =>
                    response.resource_context.res_id should ===(None)
            }
        }

        "not create an instance of knora-base:Resource" in {
            actorUnderTest ! ResourceCreateRequestV1(
                resourceTypeIri = "http://www.knora.org/ontology/knora-base#Resource",
                label = "Test Resource",
                projectIri = "http://rdfh.ch/projects/0803",
                values = Map.empty[IRI, Seq[CreateValueV1WithComment]],
                file = None,
                userProfile = SharedTestDataADM.incunabulaProjectAdminUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[BadRequestException] should ===(true)
            }
        }

        "change a resource's label" in {
            val myNewLabel = "my new beautiful label"

            actorUnderTest ! ChangeResourceLabelRequestV1(
                resourceIri = "http://data.knora.org/c5058f3a",
                label = myNewLabel,
                userProfile = SharedTestDataADM.incunabulaProjectAdminUser,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case response: ChangeResourceLabelResponseV1 =>
                    response.label should ===(myNewLabel)
            }

        }

        "not create an anything:Thing with property anything:hasBlueThing pointing to an anything:Thing" in {
            val valuesToBeCreated = Map(
                "http://www.knora.org/ontology/0001/anything#hasBlueThing" -> Vector(CreateValueV1WithComment(LinkUpdateV1(targetResourceIri = "http://data.knora.org/a-thing")))
            )

            actorUnderTest ! ResourceCreateRequestV1(
                resourceTypeIri = "http://www.knora.org/ontology/0001/anything#Thing",
                label = "Test Thing",
                projectIri = "http://rdfh.ch/projects/0001",
                values = valuesToBeCreated,
                file = None,
                userProfile = SharedTestDataADM.anythingUser1,
                apiRequestID = UUID.randomUUID
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure =>
                    msg.cause.isInstanceOf[OntologyConstraintException] should ===(true)
            }
        }

        "return a graph of resources reachable via links from/to a given resource" in {
            actorUnderTest ! GraphDataGetRequestV1(
                resourceIri = "http://data.knora.org/anything/start",
                depth = 6,
                userProfile = SharedTestDataADM.anythingUser1
            )

            val response = expectMsgType[GraphDataGetResponseV1](timeout)
            val edges = response.edges
            val nodes = response.nodes

            edges should contain theSameElementsAs graphForAnythingUser1.edges
            nodes should contain theSameElementsAs graphForAnythingUser1.nodes
        }

        "return a graph of resources reachable via links from/to a given resource, filtering the results according to the user's permissions" in {
            actorUnderTest ! GraphDataGetRequestV1(
                resourceIri = "http://data.knora.org/anything/start",
                depth = 6,
                userProfile = SharedTestDataADM.incunabulaProjectAdminUser
            )

            val response = expectMsgType[GraphDataGetResponseV1](timeout)
            val edges = response.edges
            val nodes = response.nodes

            edges should contain theSameElementsAs graphForIncunabulaUser.edges
            nodes should contain theSameElementsAs graphForIncunabulaUser.nodes
        }

        "return a graph containing a standoff link" in {
            actorUnderTest ! GraphDataGetRequestV1(
                resourceIri = "http://data.knora.org/a-thing",
                depth = 4,
                userProfile = SharedTestDataADM.anythingUser1
            )

            expectMsgPF(timeout) {
                case response: GraphDataGetResponseV1 => response should ===(graphWithStandoffLink)
            }
        }

        "return a graph containing just one node" in {
            actorUnderTest ! GraphDataGetRequestV1(
                resourceIri = "http://data.knora.org/another-thing",
                depth = 4,
                userProfile = SharedTestDataADM.anythingUser1
            )

            expectMsgPF(timeout) {
                case response: GraphDataGetResponseV1 => response should ===(graphWithOneNode)
            }
        }
    }
}
