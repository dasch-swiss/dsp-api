/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.twirl

/**
  * Represents a contributor to Knora. Used by `src/main/twirl/queries/util/generateContributorsMarkdown.scala.txt`.
  *
  * @param login         the contributor's GitHub username.
  * @param apiUrl        the contributor's GitHub API URL.
  * @param htmlUrl       the contributor's GitHub HTML URL.
  * @param contributions the number of contributions the contributor has maed.
  * @param name          the contributor's name.
  */
case class Contributor(login: String,
                       apiUrl: String,
                       htmlUrl: String,
                       contributions: Int,
                       name: Option[String] = None)