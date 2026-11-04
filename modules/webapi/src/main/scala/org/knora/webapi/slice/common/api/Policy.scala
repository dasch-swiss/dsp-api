/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.tapir.AttributeKey

/**
 * The function-level authorization an endpoint declares, carried as the tapir attribute [[Policy.key]] (ADR-0013).
 * Every authenticated caller must be active: authentication refuses inactive users, and the factories for
 * `Authenticated`, `SystemAdmin` and `ProjectAdmin` check it again. `ObjectAccess` also admits anonymous callers.
 */
enum Policy {

  /** No credentials needed; the reason is reviewed because every public route is an exposure decision. */
  case Public(reason: String)

  /** Any active, non-anonymous user. The reason is reviewed because "logged in" alone is rarely the real rule. */
  case Authenticated(reason: String)

  case SystemAdmin

  /** SystemAdmin, or ProjectAdmin of the project named by `projectFrom` (e.g. "path projectIri", "list node"). */
  case ProjectAdmin(projectFrom: String)

  /** SystemAdmin, ProjectAdmin or member of the project named by `projectFrom`. */
  case ProjectMember(projectFrom: String)

  /**
   * Per-object permissions, checked where the owning domain loads the object; `enforcedIn` names that place. A write
   * sets `admitsAnonymous` to false, so an anonymous caller is refused before the body is decoded.
   */
  case ObjectAccess(enforcedIn: String, admitsAnonymous: Boolean)

  /** The sentence the endpoint description must contain, so the prose cannot drift from the declared policy. */
  def requirement: String = this match {
    case Public(_)                 => "Does not require authentication."
    case Authenticated(_)          => "Requires authentication."
    case SystemAdmin               => "Requires SystemAdmin permissions."
    case ProjectAdmin(projectFrom) =>
      s"Requires SystemAdmin or ProjectAdmin permissions for the project given by the $projectFrom."
    case ProjectMember(projectFrom) =>
      s"Requires SystemAdmin, ProjectAdmin or ProjectMember permissions for the project given by the $projectFrom."
    case ObjectAccess(_, true)  => "Requires appropriate object access permissions."
    case ObjectAccess(_, false) => "Requires authentication and appropriate object access permissions."
  }
}

object Policy {
  val key: AttributeKey[Policy] = AttributeKey[Policy]
}
