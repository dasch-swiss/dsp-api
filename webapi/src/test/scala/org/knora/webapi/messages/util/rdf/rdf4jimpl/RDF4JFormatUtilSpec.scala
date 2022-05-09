/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.rdf.rdf4jimpl

import org.knora.webapi.feature.FeatureToggle
import org.knora.webapi.feature.ToggleStateOff
import org.knora.webapi.util.rdf.RdfFormatUtilSpec

/**
 * Tests [[org.knora.webapi.messages.util.rdf.rdf4jimpl.RDF4JFormatUtil]].
 */
class RDF4JFormatUtilSpec extends RdfFormatUtilSpec(FeatureToggle("jena-rdf-library", ToggleStateOff))
