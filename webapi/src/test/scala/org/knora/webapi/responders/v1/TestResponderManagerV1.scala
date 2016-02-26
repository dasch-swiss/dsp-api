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

import akka.actor.ActorDSL._
import akka.actor._
import akka.event.LoggingReceive
import org.knora.webapi.messages.v1respondermessages.ckanmessages.CkanResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.graphdatamessages.GraphDataResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.listmessages.ListsResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.ontologymessages.OntologyResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.projectmessages.ProjectsResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.resourcemessages.ResourcesResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.searchmessages.SearchResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.sipimessages._
import org.knora.webapi.messages.v1respondermessages.usermessages.UsersResponderRequestV1
import org.knora.webapi.messages.v1respondermessages.valuemessages.{StillImageFileValueV1, ValuesResponderRequestV1}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.InputValidation
import org.knora.webapi.{ActorMaker, LiveActorMaker, UnexpectedMessageException}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestResponderManagerV1(resourcesRouterMock: Option[ActorRef] = None,
                             valuesRouterMock: Option[ActorRef] = None,
                             sipiRouterMock: Option[ActorRef] = None,
                             usersRouterMock: Option[ActorRef] = None,
                             listsRouterMock: Option[ActorRef] = None,
                             searchRouterMock: Option[ActorRef] = None,
                             ontologyRouterMock: Option[ActorRef] = None,
                             graphdataRouterMock: Option[ActorRef] = None,
                             projectsRouterMock: Option[ActorRef] = None,
                             ckanRouterMock: Option[ActorRef] = None) extends ResponderManagerV1 with LiveActorMaker {
    this: ActorMaker =>

    // TODO: this is temporary! The mock responder should be passes as an ActorRef.
    /**
      * Imitates the Sipi server by returning a [[SipiResponderConversionResponseV1]] representing an image conversion request.
      *
      * @param conversionRequest the conversion request to be handled.
      * @return a [[SipiResponderConversionResponseV1]] imitating the answer from Sipi.
      */
    private def imageConversionResponse(conversionRequest: SipiResponderConversionRequestV1): Future[SipiResponderConversionResponseV1] = {

        // delete tmp file (depending on the kind of request given: only necessary if Knora stored the file - non GUI-case)
        def deleteTmpFile(conversionRequest: SipiResponderConversionRequestV1): Unit = {
            conversionRequest match {
                case (conversionPathRequest: SipiResponderConversionPathRequestV1) =>
                    // a tmp file has been created by the resources route (non GUI-case), delete it
                    InputValidation.deleteFileFromTmpLocation(conversionPathRequest.source)
                case _ => ()
            }
        }

        val originalFilename = conversionRequest.originalFilename
        val originalMimeType: String = conversionRequest.originalMimeType

        val fileValuesV1 = Vector(StillImageFileValueV1(// full representation
            internalMimeType = "image/jp2",
            originalFilename = originalFilename,
            originalMimeType = Some(originalMimeType),
            dimX = 800,
            dimY = 800,
            internalFilename = "full.jp2",
            qualityLevel = 100,
            qualityName = Some("full")
        ),
            StillImageFileValueV1(// thumbnail representation
                internalMimeType = "image/jpeg",
                originalFilename = originalFilename,
                originalMimeType = Some(originalMimeType),
                dimX = 80,
                dimY = 80,
                internalFilename = "thumb.jpg",
                qualityLevel = 10,
                qualityName = Some("thumbnail"),
                isPreview = true
            ))

        deleteTmpFile(conversionRequest)

        Future(SipiResponderConversionResponseV1(fileValuesV1, file_type = SipiConstants.FileType.IMAGE))
    }

    override val sipiRouter = actor("mocksipi")(new Act {
        become {
            case sipiResponderConversionFileRequest: SipiResponderConversionFileRequestV1 => future2Message(sender(), imageConversionResponse(sipiResponderConversionFileRequest), log)
            case sipiResponderConversionPathRequest: SipiResponderConversionPathRequestV1 => future2Message(sender(), imageConversionResponse(sipiResponderConversionPathRequest), log)
        }
    })

    override def receive = LoggingReceive {
        case resourcesResponderRequestV1: ResourcesResponderRequestV1 => resourcesRouter.forward(resourcesResponderRequestV1)
        case valuesResponderRequest: ValuesResponderRequestV1 => valuesRouter.forward(valuesResponderRequest)
        case sipiResponderRequest: SipiResponderRequestV1 => sipiRouter.forward(sipiResponderRequest)
        case usersResponderRequest: UsersResponderRequestV1 => usersRouter forward usersResponderRequest
        case listsResponderRequest: ListsResponderRequestV1 => listsRouter.forward(listsResponderRequest)
        case searchResponderRequest: SearchResponderRequestV1 => searchRouter.forward(searchResponderRequest)
        case ontologyResponderRequest: OntologyResponderRequestV1 => ontologyRouter.forward(ontologyResponderRequest)
        case graphdataResponderRequest: GraphDataResponderRequestV1 => resourcesRouter.forward(graphdataResponderRequest)
        case projectsResponderRequest: ProjectsResponderRequestV1 => projectsRouter forward projectsResponderRequest
        case ckanResponderRequest: CkanResponderRequestV1 => ckanRouter forward ckanResponderRequest
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }
}
