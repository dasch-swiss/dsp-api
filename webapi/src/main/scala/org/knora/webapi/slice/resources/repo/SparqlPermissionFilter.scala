/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.repo
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expression
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions
import org.eclipse.rdf4j.sparqlbuilder.core.Variable

import scala.util.matching.Regex
import org.knora.webapi.slice.admin.domain.model.KnoraGroup
import org.knora.webapi.slice.admin.domain.model.Permission.ObjectAccess

object SparqlPermissionFilter {

  /**
   * Builds a regex to match permission tokens and group IRIs.
   * @param perm the permission token to match
   * @param group the group whose IRI to match
   * @return
   */
  def buildRegEx(perm: ObjectAccess, group: KnoraGroup): Regex = {
    val groupStr =
      if (group.id.isBuiltInGroupIri) s"knora-admin:${group.groupName.value}"
      else group.id.value

    // Escape special regex characters for use in SPARQL/XSD regex (no \Q...\E support)
    val escapedGroupStr = groupStr.replaceAll("([\\\\.*+?^${}()|\\[\\]])", "\\\\$1")

    s".*(?:^|\\|)${perm.token} (?:.*,)?$escapedGroupStr(?:,.*)?(?:\\||$$).*".r
  }

  /**
   * Builds a SPARQL regex expression to match permission tokens and group IRIs.
   * @param variable the SPARQL variable to apply the filter to (without the '?' prefix)
   * @param perm the permission token to match
   * @param group the group whose IRI to match
   * @return
   */
  def buildSparqlRegex(variable: Variable, perm: ObjectAccess, group: KnoraGroup): Expression[?] =
    Expressions.regex(variable, buildRegEx(perm, group).toString)
}
