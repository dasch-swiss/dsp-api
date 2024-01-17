/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.*

import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsGetADM
import org.knora.webapi.messages.admin.responder.sipimessages.SipiFileInfoGetResponseADM
import org.knora.webapi.messages.store.triplestoremessages.IriSubjectV2
import org.knora.webapi.messages.store.triplestoremessages.LiteralV2
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

/**
 * Responds to requests for information about binary representations of resources, and returns responses in Knora API
 * ADM format.
 */
final case class SipiResponderADM(
  private val messageRelay: MessageRelay,
  private val triplestoreService: TriplestoreService,
  private implicit val sf: StringFormatter
) {

  /**
   * Returns a [[SipiFileInfoGetResponseADM]] containing the permissions and path for a file.
   *
   * @return a [[SipiFileInfoGetResponseADM]].
   */
  def getFileInfoForSipiADM(
    shortcode: KnoraProject.Shortcode,
    filename: String,
    requestingUser: User
  ): Task[SipiFileInfoGetResponseADM] =
    for {

      queryResponse <-
        triplestoreService
          .query(Construct(sparql.admin.txt.getFileValue(filename)))
          .flatMap(_.asExtended(StringFormatter.getGeneralInstance))

      _ <- ZIO
             .fail(NotFoundException(s"No file value was found for filename ${filename}"))
             .when(queryResponse.statements.isEmpty)

      fileValueIriSubject = queryResponse.statements.keys.head match {
                              case iriSubject: IriSubjectV2 => iriSubject
                              case _ =>
                                throw InconsistentRepositoryDataException(
                                  s"The subject of the file value with filename ${filename} is not an IRI"
                                )
                            }

      assertions = queryResponse.statements(fileValueIriSubject).toSeq.flatMap {
                     case (predicate: SmartIri, values: Seq[LiteralV2]) =>
                       values.map { value =>
                         predicate.toString -> value.toString
                       }
                   }

      maybeEntityPermission = PermissionUtilADM.getUserPermissionFromAssertionsADM(
                                entityIri = fileValueIriSubject.toString,
                                assertions = assertions,
                                requestingUser = requestingUser
                              )

      permissionCode: Int = maybeEntityPermission
                              .map(_.toInt)
                              .getOrElse(0) // Sipi expects a permission code from 0 to 8

      response <- permissionCode match {
                    case 1 =>
                      for {
                        maybeRVSettings <-
                          messageRelay
                            .ask[Option[ProjectRestrictedViewSettingsADM]](
                              ProjectRestrictedViewSettingsGetADM(ShortcodeIdentifier.from(shortcode))
                            )
                      } yield SipiFileInfoGetResponseADM(permissionCode = permissionCode, maybeRVSettings)

                    case _ =>
                      ZIO.succeed(
                        SipiFileInfoGetResponseADM(permissionCode = permissionCode, restrictedViewSettings = None)
                      )
                  }

    } yield response
}

object SipiResponderADM {
  def getFileInfoForSipiADM(shortcode: KnoraProject.Shortcode, filename: String, user: User) =
    ZIO.serviceWithZIO[SipiResponderADM](_.getFileInfoForSipiADM(shortcode, filename, user))

  val layer = ZLayer.derive[SipiResponderADM]
}
