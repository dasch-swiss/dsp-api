/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.rdf.jenaimpl

import org.knora.webapi.feature._
import org.knora.webapi.util.rdf.RdfModelSpec

/**
 * Tests [[org.knora.webapi.messages.util.rdf.jenaimpl.JenaModel]].
 */
class JenaModelSpec extends RdfModelSpec(FeatureToggle("jena-rdf-library", ToggleStateOn(1)))
