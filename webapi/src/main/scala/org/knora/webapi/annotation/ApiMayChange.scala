/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.annotation

/**
  * Creates the ApiMayChange annotation.
  *
  * Marks APIs that are meant to evolve towards becoming stable APIs, but are not stable APIs yet.
  *
  * <p>Evolving interfaces MAY change from one patch release to another (i.e. 2.4.10 to 2.4.11)
  * without up-front notice. A best-effort approach is taken to not cause more breakage than really
  * necessary, and usual deprecation techniques are utilised while evolving these APIs, however there
  * is NO strong guarantee regarding the source or binary compatibility of APIs marked using this
  * annotation.
  *
  * <p>It MAY also change when promoting the API to stable, for example such changes may include
  * removal of deprecated methods that were introduced during the evolution and final refactoring
  * that were deferred because they would have introduced to much breaking changes during the
  * evolution phase.
  *
  * <p>Promoting the API to stable MAY happen in a patch release.
  *
  * <p>It is encouraged to document in ScalaDoc how exactly this API is expected to evolve.
  *
  */
class ApiMayChange() extends scala.annotation.StaticAnnotation