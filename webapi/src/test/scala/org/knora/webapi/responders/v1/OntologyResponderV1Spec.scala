/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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


import akka.actor.Props
import akka.testkit._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.store.triplestoremessages.{ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.responders._
import org.knora.webapi.store._
import org.knora.webapi.util.MessageUtil

import scala.concurrent.duration._

/**
  * Static data for testing [[OntologyResponderV1]].
  */
object OntologyResponderV1Spec {

    // A test user that prefers responses in German.
    private val userProfileWithGerman = SharedAdminTestData.incunabulaProjectAdminUser

    // A test user that prefers responses in French.
    private val userProfileWithFrench = userProfileWithGerman.copy(userData = userProfileWithGerman.userData.copy(lang = "fr"))

    // A test user that prefers responses in English.
    private val userProfileWithEnglish = userProfileWithGerman.copy(userData = userProfileWithGerman.userData.copy(lang = "en"))

}


/**
  * Tests [[OntologyResponderV1]].
  */
class OntologyResponderV1Spec extends CoreSpec() with ImplicitSender {

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[OntologyResponderV1]
    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List()

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    private val page = ResourceTypeResponseV1(
        restype_info = ResTypeInfoV1(
            properties = Vector(
                PropertyDefinitionV1(
                    guiorder = None,
                    gui_name = Some("fileupload"),
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#StillImageFileValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Connects a Representation to an image file"),
                    label = Some("hat Bilddatei"),
                    name = "http://www.knora.org/ontology/knora-base#hasStillImageFileValue",
                    id = "http://www.knora.org/ontology/knora-base#hasStillImageFileValue"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(1),
                    gui_name = Some("text"),
                    attributes = Some("max=8;min=4"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Eine eindeutige numerische Bezeichnung einer Buchseite"),
                    label = Some("Seitenbezeichnung"),
                    name = "http://www.knora.org/ontology/incunabula#pagenum",
                    id = "http://www.knora.org/ontology/incunabula#pagenum"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(2),
                    gui_name = Some("searchbox"),
                    attributes = Some("restypeid=http://www.knora.org/ontology/incunabula#book"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Diese Property bezeichnet eine Verbindung zu einer anderen Resource, in dem ausgesagt wird, dass die vorliegende Resource ein integraler Teil der anderen Resource ist. Zum Beispiel ist eine Buchseite ein integraler Bestandteil genau eines Buches."),
                    label = Some("ist ein Teil von"),
                    name = "http://www.knora.org/ontology/incunabula#partOf",
                    id = "http://www.knora.org/ontology/incunabula#partOf"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(2),
                    gui_name = Some("richtext"),
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Beschreibung"),
                    label = Some("Beschreibung (Richtext)"),
                    name = "http://www.knora.org/ontology/incunabula#description",
                    id = "http://www.knora.org/ontology/incunabula#description"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(3),
                    gui_name = Some("spinbox"),
                    attributes = Some("max=-1;min=0"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#IntValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Diese Property bezeichnet die Position in einer geordneten Reihenfolge"),
                    label = Some("Sequenznummer"),
                    name = "http://www.knora.org/ontology/incunabula#seqnum",
                    id = "http://www.knora.org/ontology/incunabula#seqnum"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(5),
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;rows=3;wrap=soft"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Stellt einen Verweis dar."),
                    label = Some("Verweis"),
                    name = "http://www.knora.org/ontology/incunabula#citation",
                    id = "http://www.knora.org/ontology/incunabula#citation"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(6),
                    gui_name = Some("textarea"),
                    attributes = Some("rows=7;width=95%;wrap=soft"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Unstrukturierte Bemerkungen zu einem Objekt"),
                    label = Some("Kommentar"),
                    name = "http://www.knora.org/ontology/incunabula#page_comment",
                    id = "http://www.knora.org/ontology/incunabula#page_comment"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(7),
                    gui_name = Some("text"),
                    attributes = Some("maxlength=128;size=54"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Der originale Dateiname"),
                    label = Some("Urspr\u00FCnglicher Dateiname"),
                    name = "http://www.knora.org/ontology/incunabula#origname",
                    id = "http://www.knora.org/ontology/incunabula#origname"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(10),
                    gui_name = Some("searchbox"),
                    attributes = Some("numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Verweis auf einen Randleistentyp"),
                    label = Some("Randleistentyp links"),
                    name = "http://www.knora.org/ontology/incunabula#hasLeftSideband",
                    id = "http://www.knora.org/ontology/incunabula#hasLeftSideband"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(11),
                    gui_name = Some("searchbox"),
                    attributes = Some("numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Verweis auf einen Randleistentyp"),
                    label = Some("Randleistentyp rechts"),
                    name = "http://www.knora.org/ontology/incunabula#hasRightSideband",
                    id = "http://www.knora.org/ontology/incunabula#hasRightSideband"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(12),
                    gui_name = Some("pulldown"),
                    attributes = Some("hlist=<http://data.knora.org/lists/4b6d86ce03>"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Transkription"),
                    label = Some("Transkription"),
                    name = "http://www.knora.org/ontology/incunabula#transcription",
                    id = "http://www.knora.org/ontology/incunabula#transcription"
                )
            ),
            iconsrc = Some("page.gif"),
            description = Some("Eine Seite ist ein Teil eines Buchs"),
            label = Some("Seite"),
            name = "http://www.knora.org/ontology/incunabula#page"
        )
    )

    private val book = ResourceTypeResponseV1(
        restype_info = ResTypeInfoV1(
            properties = Vector(
                PropertyDefinitionV1(
                    guiorder = Some(1),
                    gui_name = Some("text"),
                    attributes = Some("maxlength=255;size=80"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Titel"),
                    label = Some("Titel"),
                    name = "http://www.knora.org/ontology/incunabula#title",
                    id = "http://www.knora.org/ontology/incunabula#title"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(2),
                    gui_name = Some("text"),
                    attributes = Some("maxlength=255;size=60"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Erzeuger/Autor"),
                    label = Some("Creator"),
                    name = "http://www.knora.org/ontology/incunabula#hasAuthor",
                    id = "http://www.knora.org/ontology/incunabula#hasAuthor"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(2),
                    gui_name = Some("richtext"),
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Beschreibung"),
                    label = Some("Beschreibung (Richtext)"),
                    name = "http://www.knora.org/ontology/incunabula#description",
                    id = "http://www.knora.org/ontology/incunabula#description"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(3),
                    gui_name = Some("text"),
                    attributes = Some("maxlength=255;size=60"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Ein Verlag ist ein Medienunternehmen, das Werke der Literatur, Kunst, Musik oder Wissenschaft vervielf\u00E4ltigt und verbreitet. Der Verkauf kann \u00FCber den Handel (Kunst-, Buchhandel etc.) oder durch den Verlag selbst erfolgen. Das Wort \u201Everlegen\u201C bedeutet im Mittelhochdeutschen \u201EGeld ausgeben\u201C oder \u201Eetwas auf seine Rechnung nehmen\u201C. (Wikipedia http://de.wikipedia.org/wiki/Verlag)"),
                    label = Some("Verleger"),
                    name = "http://www.knora.org/ontology/incunabula#publisher",
                    id = "http://www.knora.org/ontology/incunabula#publisher"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(4),
                    gui_name = Some("text"),
                    attributes = Some("maxlength=100;size=60"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Ort der Herausgabe"),
                    label = Some("Ort der Herausgabe"),
                    name = "http://www.knora.org/ontology/incunabula#publoc",
                    id = "http://www.knora.org/ontology/incunabula#publoc"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(5),
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;rows=3;wrap=soft"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Stellt einen Verweis dar."),
                    label = Some("Verweis"),
                    name = "http://www.knora.org/ontology/incunabula#citation",
                    id = "http://www.knora.org/ontology/incunabula#citation"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(5),
                    gui_name = Some("date"),
                    attributes = Some("maxlength=32;size=16"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#DateValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Datum der Herausgabe"),
                    label = Some("Datum der Herausgabe"),
                    name = "http://www.knora.org/ontology/incunabula#pubdate",
                    id = "http://www.knora.org/ontology/incunabula#pubdate"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(6),
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;rows=4;wrap=soft"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Der Ort wo sich das physische Original befindet"),
                    label = Some("Standort"),
                    name = "http://www.knora.org/ontology/incunabula#location",
                    id = "http://www.knora.org/ontology/incunabula#location"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(7),
                    gui_name = Some("text"),
                    attributes = Some("maxlength=200;size=60"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Uniform Resource Identifier"),
                    label = Some("URI"),
                    name = "http://www.knora.org/ontology/incunabula#url",
                    id = "http://www.knora.org/ontology/incunabula#url"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(9),
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;rows=3;wrap=soft"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Generelle physische Beschreibung des Objektes wie Material, Gr\u00F6sse etc."),
                    label = Some("Physische Beschreibung"),
                    name = "http://www.knora.org/ontology/incunabula#physical_desc",
                    id = "http://www.knora.org/ontology/incunabula#physical_desc"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(10),
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;rows=3;wrap=soft"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Eine Anmerkung zum Objekt"),
                    label = Some("Anmerkung"),
                    name = "http://www.knora.org/ontology/incunabula#note",
                    id = "http://www.knora.org/ontology/incunabula#note"
                ),
                PropertyDefinitionV1(
                    guiorder = Some(12),
                    gui_name = Some("textarea"),
                    attributes = Some("rows=7;width=95%;wrap=soft"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Unstrukturierte Bemerkungen zu einem Objekt"),
                    label = Some("Kommentar"),
                    name = "http://www.knora.org/ontology/incunabula#book_comment",
                    id = "http://www.knora.org/ontology/incunabula#book_comment"
                )
            ),
            iconsrc = Some("book.gif"),
            description = Some("Diese Resource-Klasse beschreibt ein Buch"),
            label = Some("Buch"),
            name = "http://www.knora.org/ontology/incunabula#book"
        )
    )

    private val region = ResourceTypeResponseV1(
        restype_info = ResTypeInfoV1(
            properties = Vector(
                PropertyDefinitionV1(
                    guiorder = None,
                    gui_name = Some("richtext"),
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Represents a comment on a resource as a knora-base:TextValue"),
                    label = Some("Kommentar"),
                    name = "http://www.knora.org/ontology/knora-base#hasComment",
                    id = "http://www.knora.org/ontology/knora-base#hasComment"
                ),
                PropertyDefinitionV1(
                    guiorder = None,
                    gui_name = Some("colorpicker"),
                    attributes = Some("ncolors=8"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#ColorValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Represents a color."),
                    label = Some("Farbe"),
                    name = "http://www.knora.org/ontology/knora-base#hasColor",
                    id = "http://www.knora.org/ontology/knora-base#hasColor"
                ),
                PropertyDefinitionV1(
                    guiorder = None,
                    gui_name = None,
                    attributes = Some("restypeid=http://www.knora.org/ontology/knora-base#Representation"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Region of interest within a digital object (e.g. an image)"),
                    label = Some("is Region von"),
                    name = "http://www.knora.org/ontology/knora-base#isRegionOf",
                    id = "http://www.knora.org/ontology/knora-base#isRegionOf"
                ),
                PropertyDefinitionV1(
                    guiorder = None,
                    gui_name = Some("geometry"),
                    attributes = Some("width=95%;rows=4;wrap=soft"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#GeomValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Represents a geometrical shape."),
                    label = Some("Geometrie"),
                    name = "http://www.knora.org/ontology/knora-base#hasGeometry",
                    id = "http://www.knora.org/ontology/knora-base#hasGeometry"
                )
            ),
            iconsrc = Some("region.gif"),
            description = Some("Represents a geometric region of a resource. The geometry is represented currently as JSON string."),
            label = Some("Region"),
            name = "http://www.knora.org/ontology/knora-base#Region"
        )
    )

    private val linkObject = ResourceTypeResponseV1(
        restype_info = ResTypeInfoV1(
            properties = Vector(
                PropertyDefinitionV1(
                    guiorder = None,
                    gui_name = None,
                    attributes = Some("restypeid=http://www.knora.org/ontology/knora-base#Resource"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Represents a direct connection between two resources"),
                    label = Some("hat Link zu"),
                    name = "http://www.knora.org/ontology/knora-base#hasLinkTo",
                    id = "http://www.knora.org/ontology/knora-base#hasLinkTo"
                ),
                PropertyDefinitionV1(
                    guiorder = None,
                    gui_name = Some("richtext"),
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Represents a comment on a resource as a knora-base:TextValue"),
                    label = Some("Kommentar"),
                    name = "http://www.knora.org/ontology/knora-base#hasComment",
                    id = "http://www.knora.org/ontology/knora-base#hasComment"
                )
            ),
            iconsrc = Some("link.gif"),
            description = Some("Verkn\u00FCpfung mehrerer Resourcen"),
            label = Some("Verkn\u00FCpfungsobjekt"),
            name = "http://www.knora.org/ontology/knora-base#LinkObj"
        )
    )

    private def checkResourceTypeResponseV1(received: ResourceTypeResponseV1, expected: ResourceTypeResponseV1): Unit = {
        val sortedReceivedProperties = received.restype_info.properties.toList.sortBy(_.id)
        val sortedExpectedProperties: Seq[PropertyDefinitionV1] = expected.restype_info.properties.toList.sortBy(_.id)

        assert(sortedReceivedProperties.size == sortedExpectedProperties.size,
            s"\n********** received these properties:\n${MessageUtil.toSource(sortedReceivedProperties)}\n********** expected these properties:\n${MessageUtil.toSource(sortedExpectedProperties)}")

        sortedReceivedProperties.zip(sortedExpectedProperties).foreach {
            case (receivedProp: PropertyDefinitionV1, expectedProp: PropertyDefinitionV1) =>
                assert(receivedProp == expectedProp, s"These props do not match:\n*** Received:\n${MessageUtil.toSource(receivedProp)}\n*** Expected:\n${MessageUtil.toSource(expectedProp)}")
        }
    }

    private val resourceTypesForNamedGraphIncunabula = ResourceTypesForNamedGraphResponseV1(
        resourcetypes = Vector(
            ResourceTypeV1(
                properties = Vector(
                    PropertyTypeV1(
                        label = "Publication location",
                        id = "http://www.knora.org/ontology/incunabula#publoc"
                    ),
                    PropertyTypeV1(
                        label = "Creator",
                        id = "http://www.knora.org/ontology/incunabula#hasAuthor"
                    ),
                    PropertyTypeV1(
                        label = "Location",
                        id = "http://www.knora.org/ontology/incunabula#location"
                    ),
                    PropertyTypeV1(
                        label = "Datum der Herausgabe",
                        id = "http://www.knora.org/ontology/incunabula#pubdate"
                    ),
                    PropertyTypeV1(
                        label = "Phyiscal description",
                        id = "http://www.knora.org/ontology/incunabula#physical_desc"
                    ),
                    PropertyTypeV1(
                        label = "Comment",
                        id = "http://www.knora.org/ontology/incunabula#book_comment"
                    ),
                    PropertyTypeV1(
                        label = "Note",
                        id = "http://www.knora.org/ontology/incunabula#note"
                    ),
                    PropertyTypeV1(
                        label = "URI",
                        id = "http://www.knora.org/ontology/incunabula#url"
                    ),
                    PropertyTypeV1(
                        label = "Citation/reference",
                        id = "http://www.knora.org/ontology/incunabula#citation"
                    ),
                    PropertyTypeV1(
                        label = "Publisher",
                        id = "http://www.knora.org/ontology/incunabula#publisher"
                    ),
                    PropertyTypeV1(
                        label = "Title",
                        id = "http://www.knora.org/ontology/incunabula#title"
                    ),
                    PropertyTypeV1(
                        label = "Description",
                        id = "http://www.knora.org/ontology/incunabula#description"
                    )
                ),
                label = "Book",
                id = "http://www.knora.org/ontology/incunabula#book"
            ),
            ResourceTypeV1(
                properties = Vector(
                    PropertyTypeV1(
                        label = "Randleistentyp rechts",
                        id = "http://www.knora.org/ontology/incunabula#hasRightSideband"
                    ),
                    PropertyTypeV1(
                        label = "Comment",
                        id = "http://www.knora.org/ontology/incunabula#page_comment"
                    ),
                    PropertyTypeV1(
                        label = "Original filename",
                        id = "http://www.knora.org/ontology/incunabula#origname"
                    ),
                    PropertyTypeV1(
                        label = "Randleistentyp links",
                        id = "http://www.knora.org/ontology/incunabula#hasLeftSideband"
                    ),
                    PropertyTypeV1(
                        label = "Transkription",
                        id = "http://www.knora.org/ontology/incunabula#transcription"
                    ),
                    PropertyTypeV1(
                        label = "Page identifier",
                        id = "http://www.knora.org/ontology/incunabula#pagenum"
                    ),
                    PropertyTypeV1(
                        "http://www.knora.org/ontology/knora-base#hasStillImageFileValue",
                        "has image file"),
                    PropertyTypeV1(
                        label = "Citation/reference",
                        id = "http://www.knora.org/ontology/incunabula#citation"
                    ),
                    PropertyTypeV1(
                        label = "is a part of",
                        id = "http://www.knora.org/ontology/incunabula#partOf"
                    ),
                    PropertyTypeV1(
                        label = "Sequence number",
                        id = "http://www.knora.org/ontology/incunabula#seqnum"
                    ),
                    PropertyTypeV1(
                        label = "Description",
                        id = "http://www.knora.org/ontology/incunabula#description"
                    )
                ),
                label = "Page",
                id = "http://www.knora.org/ontology/incunabula#page"
            ),
            ResourceTypeV1(
                properties = Vector(
                    PropertyTypeV1(
                        label = "Kommentar (Richtext)",
                        id = "http://www.knora.org/ontology/incunabula#sideband_comment"
                    ),
                    PropertyTypeV1(
                        label = "Title",
                        id = "http://www.knora.org/ontology/incunabula#sbTitle"
                    ),
                    PropertyTypeV1(
                        "http://www.knora.org/ontology/knora-base#hasStillImageFileValue",
                        "has image file"),
                    PropertyTypeV1(
                        label = "Description",
                        id = "http://www.knora.org/ontology/incunabula#description"
                    )
                ),
                label = "Randleiste",
                id = "http://www.knora.org/ontology/incunabula#Sideband"
            ),
            ResourceTypeV1(
                properties = Vector(
                    PropertyTypeV1(
                        label = "Farbe",
                        id = "http://www.knora.org/ontology/incunabula#miscHasColor"
                    ),
                    PropertyTypeV1(
                        label = "Geometrie",
                        id = "http://www.knora.org/ontology/incunabula#miscHasGeometry"
                    )
                ),
                label = "Sonstiges",
                id = "http://www.knora.org/ontology/incunabula#misc"
            )
        )
    )

    private val vocabulariesResponseV1 = NamedGraphsResponseV1(
        vocabularies = Vector(
            NamedGraphV1( // SystemProject
                active = true,
                uri = SharedAdminTestData.systemProjectInfo.ontologyNamedGraph,
                project_id = SharedAdminTestData.systemProjectInfo.id,
                description = SharedAdminTestData.systemProjectInfo.description.get,
                longname = SharedAdminTestData.systemProjectInfo.longname.get,
                shortname = SharedAdminTestData.systemProjectInfo.shortname,
                id = SharedAdminTestData.systemProjectInfo.ontologyNamedGraph
            ),
            NamedGraphV1( // Incunabula
                active = true,
                uri = SharedAdminTestData.incunabulaProjectInfo.ontologyNamedGraph,
                project_id = SharedAdminTestData.incunabulaProjectInfo.id,
                description = SharedAdminTestData.incunabulaProjectInfo.description.get,
                longname = SharedAdminTestData.incunabulaProjectInfo.longname.get,
                shortname = SharedAdminTestData.incunabulaProjectInfo.shortname,
                id = SharedAdminTestData.incunabulaProjectInfo.ontologyNamedGraph
            ),
            NamedGraphV1( // BEOL
                active = true,
                uri = SharedAdminTestData.beolProjectInfo.ontologyNamedGraph,
                project_id = SharedAdminTestData.beolProjectInfo.id,
                description = SharedAdminTestData.beolProjectInfo.description.get,
                longname = SharedAdminTestData.beolProjectInfo.longname.get,
                shortname = SharedAdminTestData.beolProjectInfo.shortname,
                id = SharedAdminTestData.beolProjectInfo.ontologyNamedGraph
            ),
            NamedGraphV1( // BIBLIO
                active = true,
                uri = SharedAdminTestData.biblioProjectInfo.ontologyNamedGraph,
                project_id = SharedAdminTestData.biblioProjectInfo.id,
                description = SharedAdminTestData.biblioProjectInfo.description.get,
                longname = SharedAdminTestData.biblioProjectInfo.longname.get,
                shortname = SharedAdminTestData.biblioProjectInfo.shortname,
                id = SharedAdminTestData.biblioProjectInfo.ontologyNamedGraph
            ),
            NamedGraphV1( // Images
                active = true,
                uri = SharedAdminTestData.imagesProjectInfo.ontologyNamedGraph,
                project_id = SharedAdminTestData.imagesProjectInfo.id,
                description = SharedAdminTestData.imagesProjectInfo.description.get,
                longname = SharedAdminTestData.imagesProjectInfo.longname.get,
                shortname = SharedAdminTestData.imagesProjectInfo.shortname,
                id = SharedAdminTestData.imagesProjectInfo.ontologyNamedGraph
            ),
            NamedGraphV1( // Anything
                active = true,
                uri = SharedAdminTestData.anythingProjectInfo.ontologyNamedGraph,
                project_id = SharedAdminTestData.anythingProjectInfo.id,
                description = SharedAdminTestData.anythingProjectInfo.description.get,
                longname = SharedAdminTestData.anythingProjectInfo.longname.get,
                shortname = SharedAdminTestData.anythingProjectInfo.shortname,
                id = SharedAdminTestData.anythingProjectInfo.ontologyNamedGraph
            ),
            NamedGraphV1( // Dokubib
                active = false,
                uri = SharedAdminTestData.dokubibProjectInfo.ontologyNamedGraph,
                project_id = SharedAdminTestData.dokubibProjectInfo.id,
                description = SharedAdminTestData.dokubibProjectInfo.description.get,
                longname = SharedAdminTestData.dokubibProjectInfo.longname.get,
                shortname = SharedAdminTestData.dokubibProjectInfo.shortname,
                id = SharedAdminTestData.dokubibProjectInfo.ontologyNamedGraph
            )
        )
    )

    private val propertyTypesForNamedGraphIncunabula = PropertyTypesForNamedGraphResponseV1(
        properties = Vector(
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("maxlength=255;size=60"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Erzeuger/Autor"),
                label = Some("Creator"),
                name = "http://www.knora.org/ontology/incunabula#hasAuthor",
                id = "http://www.knora.org/ontology/incunabula#hasAuthor"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("geometry"),
                attributes = None,
                valuetype_id = "http://www.knora.org/ontology/knora-base#GeomValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = None,
                label = Some("Geometrie"),
                name = "http://www.knora.org/ontology/incunabula#miscHasGeometry",
                id = "http://www.knora.org/ontology/incunabula#miscHasGeometry"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("maxlength=255;size=80"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Titel"),
                label = Some("Title"),
                name = "http://www.knora.org/ontology/incunabula#title",
                id = "http://www.knora.org/ontology/incunabula#title"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("textarea"),
                attributes = Some("cols=60;rows=3;wrap=soft"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Stellt einen Verweis dar."),
                label = Some("Citation/reference"),
                name = "http://www.knora.org/ontology/incunabula#citation",
                id = "http://www.knora.org/ontology/incunabula#citation"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("richtext"),
                attributes = None,
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Unstrukturierte Bemerkungen zu einem Objekt"),
                label = Some("Kommentar (Richtext)"),
                name = "http://www.knora.org/ontology/incunabula#sideband_comment",
                id = "http://www.knora.org/ontology/incunabula#sideband_comment"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("searchbox"),
                attributes = Some("restypeid=http://www.knora.org/ontology/incunabula#book"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Diese Property bezeichnet eine Verbindung zu einer anderen Resource, in dem ausgesagt wird, dass die vorliegende Resource ein integraler Teil der anderen Resource ist. Zum Beispiel ist eine Buchseite ein integraler Bestandteil genau eines Buches."),
                label = Some("is a part of"),
                name = "http://www.knora.org/ontology/incunabula#partOf",
                id = "http://www.knora.org/ontology/incunabula#partOf"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("textarea"),
                attributes = Some("cols=60;rows=4;wrap=soft"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Der Ort wo sich das physische Original befindet"),
                label = Some("Location"),
                name = "http://www.knora.org/ontology/incunabula#location",
                id = "http://www.knora.org/ontology/incunabula#location"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("colorpicker"),
                attributes = None,
                valuetype_id = "http://www.knora.org/ontology/knora-base#ColorValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = None,
                label = Some("Farbe"),
                name = "http://www.knora.org/ontology/incunabula#miscHasColor",
                id = "http://www.knora.org/ontology/incunabula#miscHasColor"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("maxlength=255;size=80"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = None,
                label = Some("Title"),
                name = "http://www.knora.org/ontology/incunabula#sbTitle",
                id = "http://www.knora.org/ontology/incunabula#sbTitle"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("max=8;min=4"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("A distinct identification of a book page"),
                label = Some("Page identifier"),
                name = "http://www.knora.org/ontology/incunabula#pagenum",
                id = "http://www.knora.org/ontology/incunabula#pagenum"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("pulldown"),
                attributes = Some("hlist=<http://data.knora.org/lists/4b6d86ce03>"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Transkription"),
                label = Some("Transkription"),
                name = "http://www.knora.org/ontology/incunabula#transcription",
                id = "http://www.knora.org/ontology/incunabula#transcription"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("maxlength=200;size=60"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Uniform Resource Identifier"),
                label = Some("URI"),
                name = "http://www.knora.org/ontology/incunabula#url",
                id = "http://www.knora.org/ontology/incunabula#url"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("searchbox"),
                attributes = Some("restypeid=http://www.knora.org/ontology/incunabula#book"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = None,
                label = Some("Verbindung mit einem Buch"),
                name = "http://www.knora.org/ontology/incunabula#miscHasBook",
                id = "http://www.knora.org/ontology/incunabula#miscHasBook"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("searchbox"),
                attributes = Some("numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Verweis auf einen Randleistentyp"),
                label = Some("Randleistentyp rechts"),
                name = "http://www.knora.org/ontology/incunabula#hasRightSideband",
                id = "http://www.knora.org/ontology/incunabula#hasRightSideband"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("textarea"),
                attributes = Some("cols=60;rows=3;wrap=soft"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Generelle physische Beschreibung des Objektes wie Material, Gr\u00F6sse etc."),
                label = Some("Phyiscal description"),
                name = "http://www.knora.org/ontology/incunabula#physical_desc",
                id = "http://www.knora.org/ontology/incunabula#physical_desc"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("searchbox"),
                attributes = Some("numprops=1;restypeid=http://www.knora.org/ontology/incunabula#Sideband"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#LinkValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Verweis auf einen Randleistentyp"),
                label = Some("Randleistentyp links"),
                name = "http://www.knora.org/ontology/incunabula#hasLeftSideband",
                id = "http://www.knora.org/ontology/incunabula#hasLeftSideband"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("textarea"),
                attributes = Some("rows=7;width=95%;wrap=soft"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Unstrukturierte Bemerkungen zu einem Objekt"),
                label = Some("Comment"),
                name = "http://www.knora.org/ontology/incunabula#book_comment",
                id = "http://www.knora.org/ontology/incunabula#book_comment"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("richtext"),
                attributes = None,
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Beschreibung"),
                label = Some("Beschreibung (Richtext)"),
                name = "http://www.knora.org/ontology/incunabula#description",
                id = "http://www.knora.org/ontology/incunabula#description"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("textarea"),
                attributes = Some("rows=7;width=95%;wrap=soft"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Unstrukturierte Bemerkungen zu einem Objekt"),
                label = Some("Comment"),
                name = "http://www.knora.org/ontology/incunabula#page_comment",
                id = "http://www.knora.org/ontology/incunabula#page_comment"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("maxlength=100;size=60"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Ort der Herausgabe"),
                label = Some("Publication location"),
                name = "http://www.knora.org/ontology/incunabula#publoc",
                id = "http://www.knora.org/ontology/incunabula#publoc"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("date"),
                attributes = Some("maxlength=32;size=16"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#DateValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Datum der Herausgabe"),
                label = Some("Datum der Herausgabe"),
                name = "http://www.knora.org/ontology/incunabula#pubdate",
                id = "http://www.knora.org/ontology/incunabula#pubdate"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("maxlength=128;size=54"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Der originale Dateiname"),
                label = Some("Urspr\u00FCnglicher Dateiname"),
                name = "http://www.knora.org/ontology/incunabula#origname",
                id = "http://www.knora.org/ontology/incunabula#origname"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("text"),
                attributes = Some("maxlength=255;size=60"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("Publishing is the process of production and dissemination of literature or information \u2013 the activity of making information available for public view. In some cases authors may be their own publishers, meaning: originators and developers of content also provide media to deliver and display the content. (Wikipedia http://en.wikipedia.org/wiki/Publisher)"),
                label = Some("Publisher"),
                name = "http://www.knora.org/ontology/incunabula#publisher",
                id = "http://www.knora.org/ontology/incunabula#publisher"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("spinbox"),
                attributes = Some("max=-1;min=0"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#IntValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("This property stands for the position within a set of rdered items (resoucres)"),
                label = Some("Sequence number"),
                name = "http://www.knora.org/ontology/incunabula#seqnum",
                id = "http://www.knora.org/ontology/incunabula#seqnum"
            ),
            PropertyDefinitionInNamedGraphV1(
                gui_name = Some("textarea"),
                attributes = Some("cols=60;rows=3;wrap=soft"),
                valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                vocabulary = "http://www.knora.org/ontology/incunabula",
                description = Some("A note concerning the object"),
                label = Some("Note"),
                name = "http://www.knora.org/ontology/incunabula#note",
                id = "http://www.knora.org/ontology/incunabula#note"
            )
        )
    )

    private def checkVocabularies(received: NamedGraphsResponseV1, expected: NamedGraphsResponseV1) = {

        assert(received.vocabularies.size == expected.vocabularies.size, "Vocabularies' sizes did not match.")

        received.vocabularies.sortBy(_.uri).zip(expected.vocabularies.sortBy(_.uri)).foreach {
            case (receivedVoc, expectedVoc) =>
                assert(receivedVoc.uri == expectedVoc.uri, "IRIs of vocabularies did not match")
                assert(receivedVoc.longname == expectedVoc.longname, "Names of vocabularies did not match")
        }
    }

    private def checkResourceTypesForNamedGraphResponseV1(received: ResourceTypesForNamedGraphResponseV1, expected: ResourceTypesForNamedGraphResponseV1) = {
        assert(received.resourcetypes.size == expected.resourcetypes.size, s"${expected.resourcetypes.size} were expected, but ${received.resourcetypes.size} given.")

        received.resourcetypes.sortBy(_.id).zip(expected.resourcetypes.sortBy(_.id)).foreach {
            case (receivedResType, expectedResType) =>
                assert(receivedResType.id == expectedResType.id, s"IRIs of restypes did not match.")
                assert(receivedResType.label == expectedResType.label, s"Labels of restypes did not match.")

                receivedResType.properties.sortBy(_.id).zip(expectedResType.properties.sortBy(_.id)).foreach {
                    case (receivedProp, expectedProp) =>
                        assert(receivedProp.id == expectedProp.id, "IRIs of properties did not match.")
                        assert(receivedProp.label == expectedProp.label, "Labels of properties did not match.")
                }
        }

    }

    private def checkPropertyTypesForNamedGraphIncunabula(received: PropertyTypesForNamedGraphResponseV1, expected: PropertyTypesForNamedGraphResponseV1) = {
        assert(received.properties.size == expected.properties.size, s"Sizes of properties did not match")

        val sortedReceivedProperties = received.properties.sortBy(_.id)
        val sortedExpectedProperties = expected.properties.sortBy(_.id)

        sortedReceivedProperties.zip(sortedExpectedProperties).foreach {
            case (receivedProp, expectedProp) =>
                assert(receivedProp.id == expectedProp.id, "The properties' IRIs did not match.")
                assert(receivedProp.valuetype_id == expectedProp.valuetype_id, "The properties' valuetypes did not match.")
                assert(receivedProp.attributes == expectedProp.attributes, "The properties' attributes did not match.")
        }

    }


    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())

        responderManager ! LoadOntologiesRequest(OntologyResponderV1Spec.userProfileWithGerman)
        expectMsg(10.seconds, LoadOntologiesResponse())
    }

    "The ontology responder" should {
        "return the ontology information for a incunabula:page" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page
            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/incunabula#page"
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypeResponseV1 =>
                    checkResourceTypeResponseV1(received = msg, expected = page)
            }
        }

        "return the ontology information for a incunabula:book" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book
            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/incunabula#book"
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypeResponseV1 =>
                    checkResourceTypeResponseV1(received = msg, expected = book)
            }
        }

        "return the ontology information for a knora-base:Region" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fknora-base%23Region
            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/knora-base#Region"
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypeResponseV1 =>
                    checkResourceTypeResponseV1(received = msg, expected = region)
            }
        }

        "return the ontology information for a knora-base:LinkObj" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fknora-base%23LinkObj
            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/knora-base#LinkObj"
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypeResponseV1 =>
                    checkResourceTypeResponseV1(received = msg, expected = linkObject)
            }
        }

        "return an appropriate error message if a resource class is not found" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23image

            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/incunabula#image"
            )

            expectMsgPF(timeout) {
                case msg: akka.actor.Status.Failure => msg.cause.isInstanceOf[NotFoundException] should ===(true)
            }
        }

        "return labels in the user's preferred language" in {
            actorUnderTest ! EntityInfoGetRequestV1(
                propertyIris = Set("http://www.knora.org/ontology/incunabula#title"),
                userProfile = OntologyResponderV1Spec.userProfileWithGerman // irrelevant
            )

            expectMsgPF(timeout) {
                case msg: EntityInfoGetResponseV1 =>
                    val titleContent = msg.propertyInfoMap("http://www.knora.org/ontology/incunabula#title")
                    titleContent.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(("de", "en"))) should ===(Some("Titel"))
                    titleContent.getPredicateObject(predicateIri = OntologyConstants.Rdfs.Label, preferredLangs = Some(("fr", "en"))) should ===(Some("Titre"))
            }
        }

        "get all the vocabularies" in {
            actorUnderTest ! NamedGraphsGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithEnglish
            )

            expectMsgPF(timeout) {
                case receivedMsg: NamedGraphsResponseV1 => {
                    checkVocabularies(received = receivedMsg, expected = vocabulariesResponseV1)
                    receivedMsg.vocabularies should contain allElementsOf vocabulariesResponseV1.vocabularies
                }
            }

        }

        "get all the resource classes with their property types for incunabula named graph" in {
            actorUnderTest ! ResourceTypesForNamedGraphGetRequestV1(
                namedGraph = Some("http://www.knora.org/ontology/incunabula"),
                userProfile = OntologyResponderV1Spec.userProfileWithEnglish
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypesForNamedGraphResponseV1 =>
                    checkResourceTypesForNamedGraphResponseV1(received = msg, expected = resourceTypesForNamedGraphIncunabula)
            }

        }

        "get all the properties for the named graph incunabula" in {
            actorUnderTest ! PropertyTypesForNamedGraphGetRequestV1(
                namedGraph = Some("http://www.knora.org/ontology/incunabula"),
                userProfile = OntologyResponderV1Spec.userProfileWithEnglish
            )

            expectMsgPF(timeout) {
                case msg: PropertyTypesForNamedGraphResponseV1 =>
                    checkPropertyTypesForNamedGraphIncunabula(received = msg, expected = propertyTypesForNamedGraphIncunabula)
            }
        }

        "get all the properties for all vocabularies" in {
            actorUnderTest ! PropertyTypesForNamedGraphGetRequestV1(
                namedGraph = None,
                userProfile = OntologyResponderV1Spec.userProfileWithEnglish
            )

            expectMsgPF(timeout) {
                case msg: PropertyTypesForNamedGraphResponseV1 =>
                    // simply checks that no error occurred when getting the property definitions for all vocabularies
                    ()
            }
        }
    }
}
