/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.searchmessages

import org.knora.webapi.ApiV2Schema
import org.knora.webapi.SchemaOption
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest.KnoraRequestV2
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDInt
import org.knora.webapi.messages.util.rdf.JsonLDObject
import org.knora.webapi.messages.util.rdf.JsonLDString
import org.knora.webapi.messages.v2.responder.*

sealed trait SearchResponderRequestV2 extends KnoraRequestV2 with RelayedMessage

/**
 * Represents the number of resources found by a search query.
 */
case class ResourceCountV2(numberOfResources: Int) extends KnoraJsonLDResponseV2 {
  override def toJsonLDDocument(
    targetSchema: ApiV2Schema,
    appConfig: AppConfig,
    schemaOptions: Set[SchemaOption]
  ): JsonLDDocument =
    JsonLDDocument(
      body = JsonLDObject(
        Map(
          OntologyConstants.SchemaOrg.NumberOfItems -> JsonLDInt(numberOfResources)
        )
      ),
      context = JsonLDObject(
        Map(
          "schema" -> JsonLDString(OntologyConstants.SchemaOrg.SchemaOrgPrefixExpansion)
        )
      )
    )
}

/**
 * Requests resources of the specified class from the specified project.
 *
 * @param projectIri           the IRI of the project.
 * @param resourceClass        the IRI of the resource class, in the complex schema.
 * @param orderByProperty      the IRI of the property that the resources are to be ordered by, in the complex schema.
 * @param page                 the page number of the results page to be returned.
 * @param targetSchema         the schema of the response.
 * @param schemaOptions        the schema options submitted with the request.
 * @param requestingUser       the user making the request.
 */
case class SearchResourcesByProjectAndClassRequestV2(
  projectIri: SmartIri,
  resourceClass: SmartIri,
  orderByProperty: Option[SmartIri],
  page: Int,
  targetSchema: ApiV2Schema,
  schemaOptions: Set[SchemaOption],
  requestingUser: UserADM
) extends SearchResponderRequestV2
