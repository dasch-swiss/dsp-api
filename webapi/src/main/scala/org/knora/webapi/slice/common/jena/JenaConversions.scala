/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.jena

import org.apache.jena.rdf.model.*

object JenaConversions {

  given Conversion[String, Property] with
    override def apply(x: String): Property = ResourceFactory.createProperty(x)

  given Conversion[String, Resource] with
    override def apply(x: String): Resource = ResourceFactory.createResource(x)

}
