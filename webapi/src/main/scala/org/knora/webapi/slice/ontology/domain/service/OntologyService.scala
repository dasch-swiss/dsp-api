/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

object OntologyServiceLive {
  def isBuiltInOntology(ontologyIri: InternalIri): Boolean =
    OntologyConstants.BuiltInOntologyLabels.contains(ontologyIri.value.split("/").last)

  def isSharedOntology(ontologyIri: InternalIri): Boolean =
    ontologyIri.value.split("/")(4) == "shared"

  def isBuiltInOrSharedOntology(ontologyIri: InternalIri): Boolean =
    isBuiltInOntology(ontologyIri) || isSharedOntology(ontologyIri)
}
