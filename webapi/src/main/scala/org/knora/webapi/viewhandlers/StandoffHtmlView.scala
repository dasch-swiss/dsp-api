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

package org.knora.webapi.viewhandlers

import java.io.{StringReader, StringWriter}
import javax.xml.transform.stream.StreamSource

import akka.actor.ActorSelection
import org.knora.webapi.StandoffConversionException
import org.knora.webapi.messages.v1.responder.standoffmessages.GetXSLTransformationResponseV1
import org.knora.webapi.messages.v1.responder.valuemessages.TextValueWithStandoffV1

/**
  * Provides an HTML view of a [[TextValueWithStandoffV1]].
  */
object StandoffHtmlView {

    /**
      * Applies an XSL transformation to an XML in order to produce HTML.
      *
      * @param xmlAndXSLT       the XML to be transformed and the XSL transformation.
      * @param responderManager the responder manager.
      * @return the HTML to be sent to the client.
      */
    def standoffAsHtml(xmlAndXSLT: GetXSLTransformationResponseV1, responderManager: ActorSelection): String = {

        // apply the XSL transformation to xmlStr
        val proc = new net.sf.saxon.s9api.Processor(false)
        val comp = proc.newXsltCompiler()

        val exp = comp.compile(new StreamSource(new StringReader(xmlAndXSLT.xslt)))

        val source = try {
            proc.newDocumentBuilder().build(new StreamSource(new StringReader(xmlAndXSLT.xml)))
        } catch {
            case e: Exception => throw StandoffConversionException(s"The provided XML could not be parsed: ${e.getMessage}")
        }

        val xmlTransformedStr: StringWriter = new StringWriter()
        val out = proc.newSerializer(xmlTransformedStr)

        val trans = exp.load()
        trans.setInitialContextNode(source)
        trans.setDestination(out)
        trans.transform()

        xmlTransformedStr.toString

    }

}