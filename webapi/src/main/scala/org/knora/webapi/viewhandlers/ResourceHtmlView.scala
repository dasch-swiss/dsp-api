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

package org.knora.webapi.viewhandlers

import akka.actor.ActorSelection
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import org.knora.webapi.OntologyConstants
import org.knora.webapi.messages.v1.responder.listmessages.{NodePathGetRequestV1, NodePathGetResponseV1}
import org.knora.webapi.messages.v1.responder.permissionmessages.PermissionDataV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceFullResponseV1
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.valuemessages.{DateValueV1, HierarchicalListValueV1, LinkV1, TextValueV1}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration._


/**
  * Provides an HTML view of a resource.
  */
object ResourceHtmlView {

    private implicit val timeout: Timeout = Duration(5, SECONDS)
    val log = Logger(LoggerFactory.getLogger("org.knora.webapi.viewhandlers.ResourceHtmlView"))

    /**
      * A user representing the Knora API server, used in those cases where a user is required.
      */
    private val systemUser = UserProfileV1(
        userData = UserDataV1(lang = "en"),
        isSystemUser = true,
        permissionData = PermissionDataV1(anonymousUser = false)
    )

    def propertiesHtmlView(response: ResourceFullResponseV1, responderManager: ActorSelection): String = {

        val properties = response.props.get.properties

        val propMap = properties.foldLeft(Map.empty[String, String]) {
            case (acc, propertyV1) =>
                log.debug(s"${propertyV1.toString}")

                val label = propertyV1.label match {
                    case Some(value) => value
                    case None => propertyV1.pid
                }

                val values: Seq[String] = propertyV1.valuetype_id.get match {
                    case OntologyConstants.KnoraBase.TextValue =>
                        propertyV1.values.map(literal => textValue2String(literal.asInstanceOf[TextValueV1]))

                    case OntologyConstants.KnoraBase.DateValue =>
                        propertyV1.values.map(literal => dateValue2String(literal.asInstanceOf[DateValueV1]))

                    case OntologyConstants.KnoraBase.ListValue =>
                        propertyV1.values.map(literal => listValue2String(literal.asInstanceOf[HierarchicalListValueV1], responderManager))

                    case OntologyConstants.KnoraBase.Resource => // TODO: This could actually be a subclass of knora-base:Resource.
                        propertyV1.values.map(literal => resourceValue2String(literal.asInstanceOf[LinkV1]))

                    case _ => Vector()
                }

                if (values.nonEmpty) {
                    acc + (label -> values.mkString(","))
                } else {
                    acc
                }
        }

        val imgpath = properties.find(_.locations.nonEmpty).map(_.locations.head.path).getOrElse("")
        log.debug(s"non-empty locations: ${properties.find(_.locations.nonEmpty)}")
        log.debug(s"imgpath: $imgpath , nonEmpty: ${imgpath.nonEmpty}")

        val content: play.twirl.api.Html = views.html.resource.properties(propMap, imgpath)
        content.toString
    }

    private def textValue2String(text: TextValueV1): String = {
        text.utf8str
    }

    private def dateValue2String(date: DateValueV1): String = {

        if (date.dateval1 == date.dateval2) {
            date.dateval1.toString + ", " + date.calendar.toString
        } else {
            date.dateval1.toString + ", " + date.dateval2 + ", " + date.calendar.toString
        }
    }

    private def listValue2String(list: HierarchicalListValueV1, responderManager: ActorSelection): String = {


        val resultFuture = responderManager ? NodePathGetRequestV1(list.hierarchicalListIri, systemUser)
        val nodePath = Await.result(resultFuture, Duration(3, SECONDS)).asInstanceOf[NodePathGetResponseV1]

        nodePath.nodelist.foldLeft("") { (z, i) =>
            z + i.label.get + " / "
        }
    }

    private def resourceValue2String(resource: LinkV1): String = {
        resource.valueLabel.get
    }

}
