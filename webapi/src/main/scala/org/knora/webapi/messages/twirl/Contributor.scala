/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.twirl

/**
 * Represents a contributor to Knora. Used by `src/main/twirl/queries/util/generateContributorsMarkdown.scala.txt`.
 *
 * @param login         the contributor's GitHub username.
 * @param apiUrl        the contributor's GitHub API URL.
 * @param htmlUrl       the contributor's GitHub HTML URL.
 * @param contributions the number of contributions the contributor has maed.
 * @param name          the contributor's name.
 */
case class Contributor(login: String, apiUrl: String, htmlUrl: String, contributions: Int, name: Option[String] = None)
