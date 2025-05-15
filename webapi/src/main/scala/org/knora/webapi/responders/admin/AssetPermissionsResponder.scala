/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.*
import dsp.errors.InconsistentRepositoryDataException
import dsp.errors.NotFoundException
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.IriSubjectV2
import org.knora.webapi.messages.store.triplestoremessages.LiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.PermissionUtilADM
import org.knora.webapi.slice.admin.api.model.PermissionCodeAndProjectRestrictedViewSettings
import org.knora.webapi.slice.admin.api.model.ProjectRestrictedViewSettingsADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.domain.SparqlEncodedString
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct

/**
 * Responds to requests for information about binary representations of resources, and returns responses in Knora API
 * ADM format.
 */
final case class AssetPermissionsResponder(
  private val knoraProjectService: KnoraProjectService,
  private val triplestoreService: TriplestoreService,
)(private implicit val sf: StringFormatter) {

  def getPermissionCodeAndProjectRestrictedViewSettings(
    user: User,
  )(shortcode: Shortcode, filename: SparqlEncodedString): Task[PermissionCodeAndProjectRestrictedViewSettings] =
    for {
      queryResponse  <- queryForFileValue(filename.value)
      permissionCode <- getPermissionCode(queryResponse, filename.value, user)
      response       <- buildResponse(shortcode, permissionCode)
    } yield response

  private def queryForFileValue(filename: String): Task[SparqlExtendedConstructResponse] =
    for {
      response <- triplestoreService.query(Construct(sparql.admin.txt.getFileValue(filename))).flatMap(_.asExtended)
      _ <- ZIO
             .fail(NotFoundException(s"No file value was found for filename $filename"))
             .when(response.statements.isEmpty)
    } yield response

  private def getPermissionCode(
    queryResponse: SparqlExtendedConstructResponse,
    filename: String,
    requestingUser: User,
  ): Task[Int] =
    ZIO.attempt {
      val fileValueIriSubject = queryResponse.statements.keys.head match {
        case iriSubject: IriSubjectV2 => iriSubject
        case _ =>
          throw InconsistentRepositoryDataException(
            s"The subject of the file value with filename $filename is not an IRI",
          )
      }

      val assertions = queryResponse.statements(fileValueIriSubject).toSeq.flatMap {
        case (predicate: SmartIri, values: Seq[LiteralV2]) => values.map(value => predicate.toString -> value.toString)
      }

      PermissionUtilADM
        .getUserPermissionFromAssertionsADM(fileValueIriSubject.toString, assertions, requestingUser)
        .map(_.code)
        .getOrElse(0)
    }

  private def buildResponse(
    shortcode: Shortcode,
    permissionCode: Int,
  ): Task[PermissionCodeAndProjectRestrictedViewSettings] =
    knoraProjectService
      .findByShortcode(shortcode)
      .someOrFail(NotFoundException(s"No project found for shortcode ${shortcode.value}"))
      .map(project =>
        permissionCode match {
          case 1 =>
            PermissionCodeAndProjectRestrictedViewSettings(
              permissionCode,
              restrictedViewSettings = Some(ProjectRestrictedViewSettingsADM.from(project.restrictedView)),
            )
          case _ => PermissionCodeAndProjectRestrictedViewSettings(permissionCode, restrictedViewSettings = None)
        },
      )
}

object AssetPermissionsResponder {
  val layer = ZLayer.derive[AssetPermissionsResponder]
}
