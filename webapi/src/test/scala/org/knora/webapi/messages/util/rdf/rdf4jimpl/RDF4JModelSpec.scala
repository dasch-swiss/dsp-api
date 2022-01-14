/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.rdf.rdf4jimpl

import org.knora.webapi.feature._
import org.knora.webapi.util.rdf.RdfModelSpec

/**
 * Tests [[org.knora.webapi.messages.util.rdf.rdf4jimpl.RDF4JModel]].
 */
class RDF4JModelSpec extends RdfModelSpec(FeatureToggle("jena-rdf-library", ToggleStateOff))
