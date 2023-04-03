/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.twirl

import zio.UIO
import zio.ZIO

import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v1.responder.listmessages.NodePathGetRequestV1
import org.knora.webapi.messages.v1.responder.listmessages.NodePathGetResponseV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceFullResponseV1
import org.knora.webapi.messages.v1.responder.valuemessages.DateValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.HierarchicalListValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.LinkV1
import org.knora.webapi.messages.v1.responder.valuemessages.TextValueV1

/**
 * Provides an HTML view of a resource.
 */
object ResourceHtmlView {

  def propertiesHtmlView(response: ResourceFullResponseV1): ZIO[MessageRelay, Throwable, String] = {
    val responseProps                                              = response.props.get.properties
    val initial: ZIO[MessageRelay, Throwable, Map[String, String]] = ZIO.succeed(Map.empty[String, String])
    val labelValues = responseProps.foldLeft(initial) { case (acc, propertyV1) =>
      val label = propertyV1.label.getOrElse(propertyV1.pid)
      val values: ZIO[MessageRelay, Throwable, Seq[String]] = propertyV1.valuetype_id.get match {
        case KnoraBase.TextValue =>
          ZIO.foreach(propertyV1.values)(it => textValue2String(it.asInstanceOf[TextValueV1]))
        case KnoraBase.DateValue =>
          ZIO.foreach(propertyV1.values)(it => dateValue2String(it.asInstanceOf[DateValueV1]))
        case KnoraBase.ListValue =>
          ZIO.foreach(propertyV1.values)(it => listValue2String(it.asInstanceOf[HierarchicalListValueV1]))
        case KnoraBase.Resource =>
          ZIO.foreach(propertyV1.values)(it => resourceValue2String(it.asInstanceOf[LinkV1]))
        case _ => ZIO.succeed(List.empty)
      }
      values.flatMap(v =>
        if (v.nonEmpty) { acc.map(it => it + (label -> v.mkString(","))) }
        else { acc }
      )
    }
    val imgPath = responseProps.find(_.locations.nonEmpty).map(_.locations.head.path).getOrElse("")
    labelValues.map { props =>
      val content: play.twirl.api.Html = org.knora.webapi.messages.twirl.views.html.resource.properties(props, imgPath)
      content.toString
    }
  }

  private def textValue2String(text: TextValueV1): UIO[String] = ZIO.succeed(text.utf8str)

  private def dateValue2String(date: DateValueV1): UIO[String] = ZIO.succeed {
    if (date.dateval1 == date.dateval2) {
      date.dateval1 + " " + date.era1 + ", " + date.calendar.toString + " " + date.era2
    } else {
      date.dateval1 + " " + date.era1 + ", " + date.dateval2 + ", " + date.calendar.toString + " " + date.era2
    }
  }

  private def listValue2String(list: HierarchicalListValueV1): ZIO[MessageRelay, Throwable, String] = {
    val req = NodePathGetRequestV1(list.hierarchicalListIri, KnoraSystemInstances.Users.SystemUser.asUserProfileV1)
    ZIO
      .serviceWithZIO[MessageRelay](_.ask[NodePathGetResponseV1](req))
      .map(nodePath => nodePath.nodelist.foldLeft("")((z, i) => z + i.label.get + " / "))
  }

  private def resourceValue2String(resource: LinkV1): UIO[String] = ZIO.succeed(resource.valueLabel.get)
}
