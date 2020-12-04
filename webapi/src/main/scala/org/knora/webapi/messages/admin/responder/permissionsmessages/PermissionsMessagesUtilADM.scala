/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.messages.admin.responder.permissionsmessages

import org.knora.webapi.IRI
import org.knora.webapi.exceptions.ApplicationCacheException
import org.knora.webapi.util.cache.CacheUtil

/**
  * Providing helper methods.
  */
object PermissionsMessagesUtilADM {

  val PermissionsCacheName = "permissionsCache"

  ////////////////////
  // Helper Methods //
  ////////////////////

  /**
    * Creates a key representing the supplied parameters.
    *
    * @param projectIri       the project IRI
    * @param groupIri         the group IRI
    * @param resourceClassIri the resource class IRI
    * @param propertyIri      the property IRI
    * @return a string.
    */
  def getDefaultObjectAccessPermissionADMKey(projectIri: IRI,
                                             groupIri: Option[IRI],
                                             resourceClassIri: Option[IRI],
                                             propertyIri: Option[IRI]): String = {

    projectIri.toString + " | " + groupIri.toString + " | " + resourceClassIri.toString + " | " + propertyIri.toString
  }

  /**
    * Writes a [[DefaultObjectAccessPermissionADM]] object to cache.
    *
    * @param doap a [[DefaultObjectAccessPermissionADM]].
    * @return true if writing was successful.
    * @throws ApplicationCacheException when there is a problem with writing to cache.
    */
  def writeDefaultObjectAccessPermissionADMToCache(doap: DefaultObjectAccessPermissionADM): Boolean = {

    val key = doap.cacheKey

    CacheUtil.put(PermissionsCacheName, key, doap)

    if (CacheUtil.get(PermissionsCacheName, key).isEmpty) {
      throw ApplicationCacheException("Writing the permission to cache was not successful.")
    }

    true
  }

  /**
    * Removes a [[DefaultObjectAccessPermissionADM]] object from cache.
    *
    * @param projectIri       the project IRI
    * @param groupIri         the group IRI
    * @param resourceClassIri the resource class IRI
    * @param propertyIri      the property IRI
    */
  def invalidateCachedDefaultObjectAccessPermissionADM(projectIri: IRI,
                                                       groupIri: Option[IRI],
                                                       resourceClassIri: Option[IRI],
                                                       propertyIri: Option[IRI]): Unit = {

    val key = getDefaultObjectAccessPermissionADMKey(projectIri, groupIri, resourceClassIri, propertyIri)

    CacheUtil.remove(PermissionsCacheName, key)
  }
}
