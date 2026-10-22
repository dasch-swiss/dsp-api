/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

/**
 * The Lucene lookup a Gravsearch `matchFulltext` call expands to, as the fulltext breadth guard must measure it.
 *
 * @param searchTerm the raw term, exactly as the lookup sends it to the fulltext index.
 * @param dataGraph  the data graph the lookup is scoped to, `None` for an unscoped lookup.
 */
final case class MatchFulltextLookup(searchTerm: LuceneQueryString, dataGraph: Option[InternalIri])
