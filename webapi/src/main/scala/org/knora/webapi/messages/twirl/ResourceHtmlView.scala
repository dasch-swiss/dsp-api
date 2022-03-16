/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.twirl

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v1.responder.listmessages.{NodePathGetRequestV1, NodePathGetResponseV1}
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceFullResponseV1
import org.knora.webapi.messages.v1.responder.valuemessages.{DateValueV1, HierarchicalListValueV1, LinkV1, TextValueV1}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, SECONDS}

/**
 * Provides an HTML view of a resource.
 */
object ResourceHtmlView {

  private implicit val timeout: Timeout = Duration(5, SECONDS)
  val log = Logger(LoggerFactory.getLogger("org.knora.webapi.viewhandlers.ResourceHtmlView"))

  /**
   * A user representing the Knora API server, used in those cases where a user is required.
   */
  private val systemUser = KnoraSystemInstances.Users.SystemUser.asUserProfileV1

  def propertiesHtmlView(response: ResourceFullResponseV1, responderManager: ActorRef): String = {

    val properties = response.props.get.properties

    val propMap = properties.foldLeft(Map.empty[String, String]) { case (acc, propertyV1) =>
      log.debug(s"${propertyV1.toString}")

      val label = propertyV1.label match {
        case Some(value) => value
        case None        => propertyV1.pid
      }

      val values: Seq[String] = propertyV1.valuetype_id.get match {
        case OntologyConstants.KnoraBase.TextValue =>
          propertyV1.values.map(literal => textValue2String(literal.asInstanceOf[TextValueV1]))

        case OntologyConstants.KnoraBase.DateValue =>
          propertyV1.values.map(literal => dateValue2String(literal.asInstanceOf[DateValueV1]))

        case OntologyConstants.KnoraBase.ListValue =>
          propertyV1.values.map(literal =>
            listValue2String(literal.asInstanceOf[HierarchicalListValueV1], responderManager)
          )

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

  private def textValue2String(text: TextValueV1): String =
    text.utf8str

  private def dateValue2String(date: DateValueV1): String =
    if (date.dateval1 == date.dateval2) {
      date.dateval1.toString + " " + date.era1 + ", " + date.calendar.toString + " " + date.era2
    } else {
      date.dateval1.toString + " " + date.era1 + ", " + date.dateval2 + ", " + date.calendar.toString + " " + date.era2
    }

  private def listValue2String(list: HierarchicalListValueV1, responderManager: ActorRef): String = {

    val resultFuture = responderManager ? NodePathGetRequestV1(list.hierarchicalListIri, systemUser)
    val nodePath = Await.result(resultFuture, Duration(3, SECONDS)).asInstanceOf[NodePathGetResponseV1]

    nodePath.nodelist.foldLeft("") { (z, i) =>
      z + i.label.get + " / "
    }
  }

  private def resourceValue2String(resource: LinkV1): String =
    resource.valueLabel.get

}
