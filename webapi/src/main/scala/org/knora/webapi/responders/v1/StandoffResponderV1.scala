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

import akka.actor.Status
import akka.stream.ActorMaterializer
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.{EntityInfoGetRequestV1, EntityInfoGetResponseV1}
import org.knora.webapi.messages.v1.responder.standoffmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.standoff.{StandoffTag, StandoffUtil, TextWithStandoff}

import scala.concurrent.Future
import scala.xml.NodeSeq

/**
  * Responds to requests for information about binary representations of resources, and returns responses in Knora API
  * v1 format.
  */
class StandoffResponderV1 extends ResponderV1 {

    implicit val materializer = ActorMaterializer()

    // Converts SPARQL query results to ApiValueV1 objects.
    val valueUtilV1 = new ValueUtilV1(settings)

    /**
      * Receives a message of type [[StandoffResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case CreateStandoffRequestV1(xml, userProfile) => future2Message(sender(), createStandoff(xml, userProfile), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * Maps the XML tag names to standoff classes.
      *
      * @param standoff the standoff representation of the XML.
      * @param mapping the provided mapping.
      * @return a Set of standoff class Iris.
      */
    private def mapXMLTags2StandoffTags(standoff: Seq[StandoffTag], mapping: Map[String, IRI]): Set[IRI] = {

        standoff.map {
            case (standoffTagFromXML: StandoffTag) =>

                mapping.getOrElse(standoffTagFromXML.tagName, throw BadRequestException(s"the standoff class for $standoffTagFromXML.tagName could not be found in the provided mapping"))

            }.toSet

    }

    /**
      * Creates standoff from a given XML.
      *
      * @param xml
      * @param userProfile
      * @return
      */
    def createStandoff(xml: NodeSeq, userProfile: UserProfileV1): Future[CreateStandoffResponseV1] = {

        val mappingXMLTags2StandoffTags: Map[String, IRI] = Map(
            "text" -> OntologyConstants.KnoraBase.StandoffRootTag,
            "p" -> OntologyConstants.KnoraBase.StandoffParagraphTag,
            "i" -> OntologyConstants.KnoraBase.StandoffItalicTag
        )

        val standoffUtil = new StandoffUtil()

        val textWithStandoff: TextWithStandoff = standoffUtil.xml2TextWithStandoff(xml.toString())

        val standoffTagIris = mapXMLTags2StandoffTags(textWithStandoff.standoff, mappingXMLTags2StandoffTags)

        for {
            standoffTagEntities: EntityInfoGetResponseV1 <- (responderManager ? EntityInfoGetRequestV1(resourceClassIris = standoffTagIris, userProfile = userProfile)).mapTo[EntityInfoGetResponseV1]
            _ = println(standoffTagEntities)
        } yield standoffTagEntities

        Future(CreateStandoffResponseV1(userdata = userProfile.userData))

    }

}