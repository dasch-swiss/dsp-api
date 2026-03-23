/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo

import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import java.util.UUID

import dsp.valueobjects.UuidUtil
import org.knora.webapi.slice.common.QueryBuilderHelper
import org.knora.webapi.slice.common.repo.rdf.Vocabulary.KnoraBase

object GetStandoffTagByUUIDQuery extends QueryBuilderHelper {
  def build(uuid: UUID): SelectQuery = {
    val standoffTag = variable("standoffTag")
    Queries
      .SELECT(standoffTag)
      .distinct()
      .prefix(KnoraBase.NS)
      .where(standoffTag.has(KnoraBase.standoffTagHasUUID, Rdf.literalOf(UuidUtil.base64Encode(uuid))))
  }
}
