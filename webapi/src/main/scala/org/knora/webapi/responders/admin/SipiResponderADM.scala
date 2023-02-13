/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import zio._

import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsGetADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetRequestADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiResponderRequestADM
import org.knora.webapi.messages.store.triplestoremessages.IriSubjectV2
import org.knora.webapi.messages.store.triplestoremessages.LiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.PermissionUtilADM.EntityPermission
import org.knora.webapi.messages.ResponderRequest

trait SipiResponderADM {
  def getFileInfoForSipiADM(request: SipiFileInfoGetRequestADM): Task[SipiFileInfoGetResponseADM]

}

/**
 * Responds to requests for information about binary representations of resources, and returns responses in Knora API
 * ADM format.
 */
case class SipiResponderADMLive(messageRelay: MessageRelay) extends MessageHandler with SipiResponderADM {

  override def handle(message: ResponderRequest): Task[Any] =
    this.receive(message.asInstanceOf[SipiResponderRequestADM])

  override def isResponsibleFor(message: ResponderRequest): Boolean = message match {
    case _: SipiResponderRequestADM => true
    case _                          => false
  }

  /**
   * Receives a message of type [[SipiResponderRequestADM]], and returns an appropriate response message, or
   * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
   * method first returns `Failure` to the sender, then throws an exception.
   */
  def receive(msg: SipiResponderRequestADM): Task[Any] = msg match {
    case sipiFileInfoGetRequestADM: SipiFileInfoGetRequestADM => getFileInfoForSipiADM(sipiFileInfoGetRequestADM)
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for generating complete responses.

  /**
   * Returns a [[SipiFileInfoGetResponseADM]] containing the permissions and path for a file.
   *
   * @param request the request.
   * @return a [[SipiFileInfoGetResponseADM]].
   */
  def getFileInfoForSipiADM(request: SipiFileInfoGetRequestADM): Task[SipiFileInfoGetResponseADM] =
    for {
      _ <-
        ZIO.logDebug(
          s"SipiResponderADM - getFileInfoForSipiADM: projectID: ${request.projectID}, filename: ${request.filename}, user: ${request.requestingUser.username}"
        )
      query = org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                .getFileValue(request.filename)
                .toString()
      queryResponse <-
        messageRelay
          .ask(SparqlExtendedConstructRequest(query))
          .map(any => any.asInstanceOf[SparqlExtendedConstructResponse])

      _ = if (queryResponse.statements.isEmpty)
            throw NotFoundException(s"No file value was found for filename ${request.filename}")
      _ = if (queryResponse.statements.size > 1)
            throw InconsistentRepositoryDataException(
              s"Filename ${request.filename} is used in more than one file value"
            )

      fileValueIriSubject: IriSubjectV2 = queryResponse.statements.keys.head match {
                                            case iriSubject: IriSubjectV2 => iriSubject
                                            case _ =>
                                              throw InconsistentRepositoryDataException(
                                                s"The subject of the file value with filename ${request.filename} is not an IRI"
                                              )
                                          }

      assertions: Seq[(String, String)] = queryResponse.statements(fileValueIriSubject).toSeq.flatMap {
                                            case (predicate: SmartIri, values: Seq[LiteralV2]) =>
                                              values.map { value =>
                                                predicate.toString -> value.toString
                                              }
                                          }

      maybeEntityPermission: Option[EntityPermission] = PermissionUtilADM.getUserPermissionFromAssertionsADM(
                                                          entityIri = fileValueIriSubject.toString,
                                                          assertions = assertions,
                                                          requestingUser = request.requestingUser
                                                        )

      _ <-
        ZIO.logDebug(
          s"SipiResponderADM - getFileInfoForSipiADM - maybePermissionCode: $maybeEntityPermission, requestingUser: ${request.requestingUser.username}"
        )

      permissionCode: Int = maybeEntityPermission
                              .map(_.toInt)
                              .getOrElse(0) // Sipi expects a permission code from 0 to 8

      response <- permissionCode match {
                    case 1 =>
                      for {
                        maybeRVSettings <-
                          messageRelay
                            .ask(
                              ProjectRestrictedViewSettingsGetADM(
                                identifier = ShortcodeIdentifier
                                  .fromString(request.projectID)
                                  .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
                              )
                            )
                            .map(any => any.asInstanceOf[Option[ProjectRestrictedViewSettingsADM]])
                      } yield SipiFileInfoGetResponseADM(permissionCode = permissionCode, maybeRVSettings)

                    case _ => ZIO.succeed(SipiFileInfoGetResponseADM(permissionCode, None))
                  }

      _ <- ZIO.logInfo(s"filename ${request.filename}, permission code: $permissionCode")
    } yield response
}

object SipiResponderADMLive {
  val layer: URLayer[MessageRelay, SipiResponderADM] = ZLayer.fromZIO {
    for {
      relay    <- ZIO.service[MessageRelay]
      responder = SipiResponderADMLive(relay)
      _         = relay.subscribe(responder)
    } yield responder
  }
}
