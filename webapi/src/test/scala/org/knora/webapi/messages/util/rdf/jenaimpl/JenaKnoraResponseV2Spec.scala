/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.rdf.jenaimpl

import org.knora.webapi.feature.ToggleStateOn
import org.knora.webapi.util.rdf.KnoraResponseV2Spec

/**
 * Tests [[org.knora.webapi.messages.v2.responder.KnoraResponseV2]] with the Jena API.
 */
class JenaKnoraResponseV2Spec extends KnoraResponseV2Spec(FeatureToggle("jena-rdf-library", ToggleStateOn(1)))
