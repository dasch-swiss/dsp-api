/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain

import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri

object OntologyIriOps {

  extension (iri: OntologyIri) {

    /**
     * This method is unsafe as it does not fully check the validity of `name` and might throw an exception.
     */
    def makeResourceClassIri(name: String): ResourceClassIri =
      if (name.isEmpty) throw IllegalArgumentException("Resource class name cannot be empty")
      if (name.head.isLower) throw IllegalArgumentException("Resource class name must start with an uppercase letter")
      else ResourceClassIri.unsafeFrom(iri.smartIri.makeEntityIri(name))

    /**
     * This method is unsafe as it does not fully check the validity of `name` and might throw an exception.
     */
    def makePropertyIri(name: String): PropertyIri =
      if (name.isEmpty) throw IllegalArgumentException("Resource class name cannot be empty")
      if (name.head.isUpper) throw IllegalArgumentException("Resource class name must start with an uppercase letter")
      else PropertyIri.unsafeFrom(iri.smartIri.makeEntityIri(name))
  }
}
