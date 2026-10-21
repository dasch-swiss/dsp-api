/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import org.knora.webapi.messages.util.search.QueryPattern
import org.knora.webapi.slice.common.domain.InternalIri

/**
 * Lets [[AbstractPrequeryGenerator]] wrap the Lucene statement of a lookup anchored on the main resource in
 * `GRAPH <dataGraph> { }` (`docs/adr/0012-graph-scoped-lucene-text-lookups.md`). Set only when the query is restricted
 * to a project whose data graph is known; the caller keeps the main resource's `attachedToProject` either way.
 *
 * @param dataGraph  the data graph of the project the query is restricted to.
 * @param inputWhere the input WHERE patterns, type annotations removed, from which the generator decides which
 *                   lookups are anchored on the main resource.
 */
final case class LuceneScope(dataGraph: InternalIri, inputWhere: Seq[QueryPattern])
