/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.rdf.jenaimpl

import org.knora.webapi.feature.{FeatureToggle, ToggleStateOn}
import org.knora.webapi.util.rdf.JsonLDUtilSpec

/**
 * Tests [[org.knora.webapi.messages.util.rdf.JsonLDUtil]] using the Jena RDF API.
 */
class JenaJsonLDUtilSpec extends JsonLDUtilSpec(FeatureToggle("jena-rdf-library", ToggleStateOn(1)))
