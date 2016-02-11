/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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
import org.knora.webapi.messages.v1respondermessages.ontologymessages._
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v1respondermessages.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.responders._
import org.knora.webapi.store._
import org.knora.webapi.util.MessageUtil

import scala.concurrent.duration._

/**
  * Static data for testing [[OntologyResponderV1]].
  */
object OntologyResponderV1Spec {

    // A test user that prefers responses in German.
    private val userProfileWithGerman = UserProfileV1(
        projects = Vector("http://data.knora.org/projects/77275339"),
        groups = Nil,
        userData = UserDataV1(
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        )
    )

    // A test user that prefers responses in French.
    private val userProfileWithFrench = userProfileWithGerman.copy(userData = userProfileWithGerman.userData.copy(lang = "fr"))
}


/**
  * Tests [[OntologyResponderV1]].
  */
class OntologyResponderV1Spec extends CoreSpec() with ImplicitSender {

    // Construct the actors needed for this test.
    private val actorUnderTest = TestActorRef[OntologyResponderV1]
    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula")
    )

    // The default timeout for receiving reply messages from actors.
    private val timeout = 10.seconds

    private val page = ResourceTypeResponseV1(
        userdata = UserDataV1(
            password = None,
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        ),
        restype_info = ResTypeInfoV1(
            properties = Set(
                PropertyDefinitionV1(
                    gui_name = Some("searchbox"),
                    attributes = Some("restypeid=53;numprops=1"),
                    valuetype_id = "http://www.knora.org/ontology/incunabula#Sideband",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Verweis auf einen Randleistentyp"),
                    label = Some("Randleistentyp links"),
                    name = "http://www.knora.org/ontology/incunabula#hasLeftSideband",
                    id = "http://www.knora.org/ontology/incunabula#hasLeftSideband"
                ),
                PropertyDefinitionV1(
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
                    gui_name = Some("textarea"),
                    attributes = Some("wrap=soft;width=95%;rows=7"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Unstrukturierte Bemerkungen zu einem Objekt"),
                    label = Some("Kommentar"),
                    name = "http://www.knora.org/ontology/incunabula#page_comment",
                    id = "http://www.knora.org/ontology/incunabula#page_comment"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("searchbox"),
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/incunabula#book",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Diese Property bezeichnet eine Verbindung zu einer anderen Resource, in dem ausgesagt wird, dass die vorliegende Resource ein integraler Teil der anderen Resource ist. Zum Beispiel ist eine Buchseite ein integraler Bestandteil genau eines Buches."),
                    label = Some("ist ein Teil von"),
                    name = "http://www.knora.org/ontology/incunabula#partOf",
                    id = "http://www.knora.org/ontology/incunabula#partOf"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("spinbox"),
                    attributes = Some("min=0;max=-1"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#IntValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Diese Property bezeichnet die Position in einer geordneten Reihenfolge"),
                    label = Some("Sequenznummer"),
                    name = "http://www.knora.org/ontology/incunabula#seqnum",
                    id = "http://www.knora.org/ontology/incunabula#seqnum"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("size=54;maxlength=128"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Der originale Dateiname"),
                    label = Some("Urspr\u00FCnglicher Dateiname"),
                    name = "http://www.knora.org/ontology/incunabula#origname",
                    id = "http://www.knora.org/ontology/incunabula#origname"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("min=4;max=8"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Eine eindeutige numerische Bezeichnung einer Buchseite"),
                    label = Some("Seitenbezeichnung"),
                    name = "http://www.knora.org/ontology/incunabula#pagenum",
                    id = "http://www.knora.org/ontology/incunabula#pagenum"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("searchbox"),
                    attributes = Some("restypeid=53;numprops=1"),
                    valuetype_id = "http://www.knora.org/ontology/incunabula#Sideband",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Verweis auf einen Randleistentyp"),
                    label = Some("Randleistentyp rechts"),
                    name = "http://www.knora.org/ontology/incunabula#hasRightSideband",
                    id = "http://www.knora.org/ontology/incunabula#hasRightSideband"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;wrap=soft;rows=3"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = None,
                    label = Some("Verweis"),
                    name = "http://www.knora.org/ontology/incunabula#citation",
                    id = "http://www.knora.org/ontology/incunabula#citation"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("pulldown"),
                    attributes = Some("hlist=<http://data.knora.org/lists/4b6d86ce03>"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Transkription"),
                    label = Some("Transkription"),
                    name = "http://www.knora.org/ontology/incunabula#transcription",
                    id = "http://www.knora.org/ontology/incunabula#transcription"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#StillImageFileValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = None,
                    label = None,
                    name = "http://www.knora.org/ontology/knora-base#hasStillImageFileValue",
                    id = "http://www.knora.org/ontology/knora-base#hasStillImageFileValue"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#Representation",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("References an instance of a Representation. A Representation contains the metadata of a digital object (= file) which represents some physical entity such as an image, a sound, an encoded text etc."),
                    label = None,
                    name = "http://www.knora.org/ontology/knora-base#hasRepresentation",
                    id = "http://www.knora.org/ontology/knora-base#hasRepresentation"
                )

            ),
            iconsrc = Some("page.gif"),
            description = Some("Eine Seite ist ein Teil eines Buchs"),
            label = Some("Seite"),
            name = "http://www.knora.org/ontology/incunabula#page"
        )
    )

    private val book = ResourceTypeResponseV1(
        userdata = OntologyResponderV1Spec.userProfileWithGerman.userData,
        restype_info = ResTypeInfoV1(
            properties = Set(
                PropertyDefinitionV1(
                    gui_name = Some("textarea"),
                    attributes = Some("wrap=soft;width=95%;rows=7"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Unstrukturierte Bemerkungen zu einem Objekt"),
                    label = Some("Kommentar"),
                    name = "http://www.knora.org/ontology/incunabula#book_comment",
                    id = "http://www.knora.org/ontology/incunabula#book_comment"
                ),
                PropertyDefinitionV1(
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
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;wrap=soft;rows=3"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Eine Anmerkung zum Objekt"),
                    label = Some("Anmerkung"),
                    name = "http://www.knora.org/ontology/incunabula#note",
                    id = "http://www.knora.org/ontology/incunabula#note"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;wrap=soft;rows=3"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Generelle physische Beschreibung des Objektes wie Material, Gr\u00F6sse etc."),
                    label = Some("Physische Beschreibung"),
                    name = "http://www.knora.org/ontology/incunabula#physical_desc",
                    id = "http://www.knora.org/ontology/incunabula#physical_desc"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("maxlength=255;size=60"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = None,
                    label = Some("Creator"),
                    name = "http://www.knora.org/ontology/incunabula#hasAuthor",
                    id = "http://www.knora.org/ontology/incunabula#hasAuthor"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("size=60;maxlength=200"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Uniform Resource Identifier"),
                    label = Some("URI"),
                    name = "http://www.knora.org/ontology/incunabula#url",
                    id = "http://www.knora.org/ontology/incunabula#url"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("date"),
                    attributes = Some("size=16;maxlength=32"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#DateValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = None,
                    label = Some("Datum der Herausgabe"),
                    name = "http://www.knora.org/ontology/incunabula#pubdate",
                    id = "http://www.knora.org/ontology/incunabula#pubdate"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("size=80;maxlength=255"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = None,
                    label = Some("Titel"),
                    name = "http://www.knora.org/ontology/incunabula#title",
                    id = "http://www.knora.org/ontology/incunabula#title"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("textarea"),
                    attributes = Some("cols=60;wrap=soft;rows=3"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = None,
                    label = Some("Verweis"),
                    name = "http://www.knora.org/ontology/incunabula#citation",
                    id = "http://www.knora.org/ontology/incunabula#citation"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("maxlength=255;size=60"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Ein Verlag ist ein Medienunternehmen, das Werke der Literatur, Kunst, Musik oder Wissenschaft vervielf\u00E4ltigt und verbreitet. Der Verkauf kann \u00FCber den Handel (Kunst-, Buchhandel etc.) oder durch den Verlag selbst erfolgen. Das Wort \u201Everlegen\u201C bedeutet im Mittelhochdeutschen \u201EGeld ausgeben\u201C oder \u201Eetwas auf seine Rechnung nehmen\u201C. (Wikipedia http://de.wikipedia.org/wiki/Verlag)"),
                    label = Some("Verleger"),
                    name = "http://www.knora.org/ontology/incunabula#publisher",
                    id = "http://www.knora.org/ontology/incunabula#publisher"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("text"),
                    attributes = Some("size=60;maxlength=100"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/incunabula",
                    description = Some("Ort der Herausgabe"),
                    label = Some("Ort der Herausgabe"),
                    name = "http://www.knora.org/ontology/incunabula#publoc",
                    id = "http://www.knora.org/ontology/incunabula#publoc"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#Representation",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("References an instance of a Representation. A Representation contains the metadata of a digital object (= file) which represents some physical entity such as an image, a sound, an encoded text etc."),
                    label = None,
                    name = "http://www.knora.org/ontology/knora-base#hasRepresentation",
                    id = "http://www.knora.org/ontology/knora-base#hasRepresentation"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#IntValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = None,
                    label = Some("Sequenznummer"),
                    name = "http://www.knora.org/ontology/knora-base#seqnum",
                    id = "http://www.knora.org/ontology/knora-base#seqnum"
                )

            ),
            iconsrc = Some("book.gif"),
            description = Some("Diese Resource-Klasse beschreibt ein Buch"),
            label = Some("Buch"),
            name = "http://www.knora.org/ontology/incunabula#book"
        )
    )

    private val region = ResourceTypeResponseV1(
        userdata = OntologyResponderV1Spec.userProfileWithGerman.userData,
        restype_info = ResTypeInfoV1(
            properties = Set(
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Any commentary to a resource"),
                    label = Some("Kommentar"),
                    name = "http://www.knora.org/ontology/knora-base#hasComment",
                    id = "http://www.knora.org/ontology/knora-base#hasComment"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("colorpicker"),
                    attributes = Some("ncolors=8"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#ColorValue",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = None,
                    label = Some("Farbe"),
                    name = "http://www.knora.org/ontology/knora-base#hasColor",
                    id = "http://www.knora.org/ontology/knora-base#hasColor"
                ),
                PropertyDefinitionV1(
                    gui_name = Some("geometry"),
                    attributes = Some("width=95%;rows=4;wrap=soft"),
                    valuetype_id = "http://www.knora.org/ontology/knora-base#GeomValue",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = None,
                    label = Some("Geometrie"),
                    name = "http://www.knora.org/ontology/knora-base#hasGeometry",
                    id = "http://www.knora.org/ontology/knora-base#hasGeometry"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#Representation",
                    occurrence = "1",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Region of interest within a digital object (e.g. an image)"),
                    label = None,
                    name = "http://www.knora.org/ontology/knora-base#isRegionOf",
                    id = "http://www.knora.org/ontology/knora-base#isRegionOf"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#Representation",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("References an instance of a Representation. A Representation contains the metadata of a digital object (= file) which represents some physical entity such as an image, a sound, an encoded text etc."),
                    label = None,
                    name = "http://www.knora.org/ontology/knora-base#hasRepresentation",
                    id = "http://www.knora.org/ontology/knora-base#hasRepresentation"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#IntValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = None,
                    label = Some("Sequenznummer"),
                    name = "http://www.knora.org/ontology/knora-base#seqnum",
                    id = "http://www.knora.org/ontology/knora-base#seqnum"
                )

            ),
            iconsrc = Some("region.gif"),
            description = Some("This Resource represents a geometric region of a resource. The geometry is represented currently as JSON string."),
            label = Some("Region"),
            name = "http://www.knora.org/ontology/knora-base#Region"
        )
    )

    private val linkObject = ResourceTypeResponseV1(
        userdata = UserDataV1(
            password = None,
            email = Some("test@test.ch"),
            lastname = Some("test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        ),
        restype_info = ResTypeInfoV1(
            properties = Set(
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#Resource",
                    occurrence = "1-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("This property (and subproperties thereof) connect directly 2 resources. It's always augmented by a hasLinkToValue property connecting to the reification node."),
                    label = None,
                    name = "http://www.knora.org/ontology/knora-base#hasLinkTo",
                    id = "http://www.knora.org/ontology/knora-base#hasLinkTo"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#TextValue",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("Any commentary to a resource"),
                    label = Some("Kommentar"),
                    name = "http://www.knora.org/ontology/knora-base#hasComment",
                    id = "http://www.knora.org/ontology/knora-base#hasComment"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#Representation",
                    occurrence = "0-n",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = Some("References an instance of a Representation. A Representation contains the metadata of a digital object (= file) which represents some physical entity such as an image, a sound, an encoded text etc."),
                    label = None,
                    name = "http://www.knora.org/ontology/knora-base#hasRepresentation",
                    id = "http://www.knora.org/ontology/knora-base#hasRepresentation"
                ),
                PropertyDefinitionV1(
                    gui_name = None,
                    attributes = None,
                    valuetype_id = "http://www.knora.org/ontology/knora-base#IntValue",
                    occurrence = "0-1",
                    vocabulary = "http://www.knora.org/ontology/knora-base",
                    description = None,
                    label = Some("Sequenznummer"),
                    name = "http://www.knora.org/ontology/knora-base#seqnum",
                    id = "http://www.knora.org/ontology/knora-base#seqnum"
                )

            ),
            iconsrc = Some("link.gif"),
            description = Some("Verkn\u00FCpfung mehrerer Resourcen (Systemobject)"),
            label = Some("Verkn\u00FCpfungsobjekt"),
            name = "http://www.knora.org/ontology/knora-base#LinkObj"
        )
    )

    private def checkResourceTypeResponseV1(expected: ResourceTypeResponseV1, received: ResourceTypeResponseV1): Boolean = {
        val sortedExpectedProperties: Seq[PropertyDefinitionV1] = expected.restype_info.properties.toList.sortBy(_.id)
        val sortedReceivedProperties = received.restype_info.properties.toList.sortBy(_.id)

        assert(sortedReceivedProperties.size == sortedExpectedProperties.size,
            s"\n********** expected these properties:\n${MessageUtil.toSource(sortedExpectedProperties)}\n********** received these properties:\n${MessageUtil.toSource(sortedReceivedProperties)}")


        sortedExpectedProperties.zip(sortedReceivedProperties).foreach {
            case (expectedProp: PropertyDefinitionV1, receivedProp: PropertyDefinitionV1) =>

                // sort property attributes
                val expectedPropWithSortedAttr = expectedProp.copy(
                    attributes = expectedProp.attributes match {
                        case Some(attr: String) => Some(attr.sorted)
                        case None => None
                    }
                )

                val receivedPropWithSortedAttr = receivedProp.copy(
                    attributes = receivedProp.attributes match {
                        case Some(attr: String) => Some(attr.sorted)
                        case None => None
                    }
                )

                assert(expectedPropWithSortedAttr == receivedPropWithSortedAttr, s"These props do not match:\n*** Expected:\n${MessageUtil.toSource(expectedProp)}\n*** Received:\n${MessageUtil.toSource(receivedProp)}")
        }

        true
    }

    "Load test data" in {
        storeManager ! ResetTriplestoreContent(rdfDataObjects)
        expectMsg(300.seconds, ResetTriplestoreContentACK())
    }

    "The ontology responder" should {
        "return the ontology information for a incunabula:page" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page
            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/incunabula#page"
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypeResponseV1 if checkResourceTypeResponseV1(page, msg) => ()
            }
        }

        "return the ontology information for a incunabula:book" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23book
            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/incunabula#book"
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypeResponseV1 if checkResourceTypeResponseV1(book, msg) => ()
            }
        }

        "return the ontology information for a knora-base:Region" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fknora-base%23Region
            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/knora-base#Region"
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypeResponseV1 if checkResourceTypeResponseV1(region, msg) => ()
            }
        }

        "return the ontology information for a knora-base:LinkObj" in {
            // http://localhost:3333/v1/resourcetypes/http%3A%2F%2Fwww.knora.org%2Fontology%2Fknora-base%23LinkObj
            actorUnderTest ! ResourceTypeGetRequestV1(
                userProfile = OntologyResponderV1Spec.userProfileWithGerman,
                resourceTypeIri = "http://www.knora.org/ontology/knora-base#LinkObj"
            )

            expectMsgPF(timeout) {
                case msg: ResourceTypeResponseV1 if checkResourceTypeResponseV1(linkObject, msg) => ()
            }
        }

        "return labels in the user's preferred language" in {
            actorUnderTest ! EntityInfoGetRequestV1(
                propertyIris = Set("http://www.knora.org/ontology/incunabula#title"),
                userProfile = OntologyResponderV1Spec.userProfileWithGerman
            )

            expectMsgPF(timeout) {
                case msg: EntityInfoGetResponseV1 =>
                    msg.propertyEntityInfoMap("http://www.knora.org/ontology/incunabula#title").getPredicateObject(OntologyConstants.Rdfs.Label) should ===(Some("Titel"))
            }

            actorUnderTest ! EntityInfoGetRequestV1(
                propertyIris = Set("http://www.knora.org/ontology/incunabula#title"),
                userProfile = OntologyResponderV1Spec.userProfileWithFrench
            )

            expectMsgPF(timeout) {
                case msg: EntityInfoGetResponseV1 =>
                    msg.propertyEntityInfoMap("http://www.knora.org/ontology/incunabula#title").getPredicateObject(OntologyConstants.Rdfs.Label) should ===(Some("Titre"))
            }
        }
    }
}
