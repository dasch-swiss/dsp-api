/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio._

import dsp.errors.BadRequestException
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsGetADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetRequestADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiResponderRequestADM
import org.knora.webapi.messages.store.triplestoremessages.IriSubjectV2
import org.knora.webapi.messages.store.triplestoremessages.LiteralV2
import org.knora.webapi.messages.twirl.queries._
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.messages.util.PermissionUtilADM.EntityPermission
import org.knora.webapi.responders.Responder
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * Responds to requests for information about binary representations of resources, and returns responses in Knora API
 * ADM format.
 */
trait SipiResponderADM {

  /**
   * Returns a [[SipiFileInfoGetResponseADM]] containing the permissions and path for a file.
   *
   * @param request the request.
   * @return a [[SipiFileInfoGetResponseADM]].
   */
  def getFileInfoForSipiADM(request: SipiFileInfoGetRequestADM): Task[SipiFileInfoGetResponseADM]
}

final case class SipiResponderADMLive(
  private val messageRelay: MessageRelay,
  private val triplestoreService: TriplestoreService
) extends SipiResponderADM
    with MessageHandler {

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[SipiResponderRequestADM]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case sipiFileInfoGetRequestADM: SipiFileInfoGetRequestADM => getFileInfoForSipiADM(sipiFileInfoGetRequestADM)
    case other                                                => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Returns a [[SipiFileInfoGetResponseADM]] containing the permissions and path for a file.
   *
   * @param request the request.
   * @return a [[SipiFileInfoGetResponseADM]].
   */
  override def getFileInfoForSipiADM(request: SipiFileInfoGetRequestADM): Task[SipiFileInfoGetResponseADM] =
    for {
      _ <-
        ZIO.logDebug(
          s"SipiResponderADM - getFileInfoForSipiADM: projectID: ${request.projectID}, filename: ${request.filename}, user: ${request.requestingUser.username}"
        )
      queryResponse <- triplestoreService.sparqlHttpExtendedConstruct(sparql.admin.txt.getFileValue(request.filename))

      _ <- ZIO.when(queryResponse.statements.isEmpty)(
             ZIO.fail(NotFoundException(s"No file value was found for filename ${request.filename}"))
           )
      _ <- ZIO.when(queryResponse.statements.size > 1)(ZIO.fail {
             val msg = s"Filename ${request.filename} is used in more than one file value"
             InconsistentRepositoryDataException(msg)
           })

      fileValueIriSubject <- queryResponse.statements.keys.head match {
                               case iriSubject: IriSubjectV2 => ZIO.succeed(iriSubject)
                               case _ =>
                                 ZIO.fail {
                                   InconsistentRepositoryDataException(
                                     s"The subject of the file value with filename ${request.filename} is not an IRI"
                                   )
                                 }
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

      projectId <- ShortcodeIdentifier
                     .fromString(request.projectID)
                     .toZIO
                     .mapBoth(e => BadRequestException(e.getMessage), ProjectRestrictedViewSettingsGetADM)
      response <- permissionCode match {
                    case 1 =>
                      for {
                        maybeRVSettings <- messageRelay.ask[Option[ProjectRestrictedViewSettingsADM]](projectId)
                      } yield SipiFileInfoGetResponseADM(permissionCode = permissionCode, maybeRVSettings)

                    case _ =>
                      ZIO.succeed(
                        SipiFileInfoGetResponseADM(permissionCode = permissionCode, restrictedViewSettings = None)
                      )
                  }

      _ <- ZIO.logInfo(s"filename ${request.filename}, permission code: $permissionCode")
    } yield response
}
object SipiResponderADMLive {
  val layer: URLayer[TriplestoreService with MessageRelay, SipiResponderADM] = ZLayer.fromZIO {
    for {
      mr      <- ZIO.service[MessageRelay]
      ts      <- ZIO.service[TriplestoreService]
      handler <- mr.subscribe(SipiResponderADMLive(mr, ts))
    } yield handler
  }
}
