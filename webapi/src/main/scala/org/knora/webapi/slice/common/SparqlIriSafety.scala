/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

/**
 * Single source of truth for the SPARQL 1.1 IRIREF character-safety check.
 *
 * The SPARQL 1.1 IRIREF production (§19.8) forbids:
 *   - ASCII control characters: U+0000–U+0020 (NUL through SPACE inclusive)
 *   - The printable set: < > " { } | \ ^ `
 *
 * Used by:
 *  - [[QueryBuilderHelper.isSparqlIriRefUnsafe]] / [[QueryBuilderHelper.requireSafeIriEffect]]
 *    (Gate-3 defence-in-depth in query builders)
 *  - [[org.knora.webapi.slice.api.v3.ontology.OntologyMappingRestService]] validateExternalIri
 *    (Gate-1 primary check)
 */
object SparqlIriSafety:

  private val forbiddenChars: Set[Char] = Set('{', '}', '"', '<', '>', '\\', '^', '`', '|')

  /** Returns true when `iriStr` contains a character forbidden in a SPARQL IRIREF position. */
  def isSparqlIriRefUnsafe(iriStr: String): Boolean =
    iriStr.exists(c => c <= '\u0020' || forbiddenChars.contains(c))

  /**
   * Returns the first character in `iriStr` that is forbidden in a SPARQL IRIREF position,
   * or [[None]] if all characters are safe.
   */
  def findForbiddenChar(iriStr: String): Option[Char] =
    iriStr.find(c => c <= '\u0020' || forbiddenChars.contains(c))
