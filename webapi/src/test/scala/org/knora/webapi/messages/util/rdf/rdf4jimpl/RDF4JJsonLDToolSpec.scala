/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util.rdf.rdf4jimpl

import org.knora.webapi.feature.{FeatureToggle, ToggleStateOff}
import org.knora.webapi.util.rdf.JsonLDToolSpec

/**
 * Tests [[org.knora.webapi.messages.util.rdf.JsonLDTool]] using the RDF4J API.
 */
class RDF4JJsonLDToolSpec extends JsonLDToolSpec(FeatureToggle("jena-rdf-library", ToggleStateOff))
