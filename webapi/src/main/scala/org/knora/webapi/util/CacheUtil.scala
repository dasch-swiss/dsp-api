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

package org.knora.webapi.util

import com.typesafe.scalalogging.Logger
import net.sf.ehcache.event.CacheEventListener
import net.sf.ehcache.{Cache, CacheManager, Ehcache, Element}
import org.knora.webapi.ApplicationCacheException
import org.slf4j.LoggerFactory

/**
  * Maintains in-memory caches, and caches values, using Ehcache (http://ehcache.org/). Each cache is accessible
  * by name from any function running in the same JVM.
  */
object CacheUtil {

    val log = Logger(LoggerFactory.getLogger("org.knora.webapi.util.cache"))

    /**
      * Represents the configuration of a Knora application cache.
      *
      * @param cacheName           the name of the cache.
      * @param maxElementsInMemory the maximum number of elements in memory, before they are evicted (0 == no limit).
      * @param overflowToDisk      whether to use the disk store.
      * @param eternal             whether the elements in the cache are eternal, i.e. never expire.
      * @param timeToLiveSeconds   the default amount of time to live for an element from its creation date.
      * @param timeToIdleSeconds   the default amount of time to live for an element from its last accessed or modified date.
      */
    case class KnoraCacheConfig(cacheName: String,
                                maxElementsInMemory: Int,
                                overflowToDisk: Boolean,
                                eternal: Boolean,
                                timeToLiveSeconds: Int,
                                timeToIdleSeconds: Int)

    /**
      * Creates application caches.
      *
      * @param cacheConfigs Maps containing the keys `cacheName`, `maxElementsInMemory`,
      *                     `overflowToDisk`, `eternal`, `timeToLiveSeconds`,  and `timeToIdleSeconds`,
      *                     representing configuration options for Ehcache caches.
      */
    def createCaches(cacheConfigs: Seq[KnoraCacheConfig]) = {
        val cacheManager = CacheManager.getInstance()
        cacheConfigs.foreach {
            cacheConfig =>
                val cache = new Cache(cacheConfig.cacheName,
                    cacheConfig.maxElementsInMemory,
                    cacheConfig.overflowToDisk,
                    cacheConfig.eternal,
                    cacheConfig.timeToLiveSeconds,
                    cacheConfig.timeToIdleSeconds)
                cacheManager.addCache(cache)
                cache.getCacheEventNotificationService.registerListener(new LoggingCacheEventListener(log))
                log.debug(s"CacheUtil: Created application cache '${cacheConfig.cacheName}'")
        }
    }

    /**
      * Removes all caches.
      */
    def removeAllCaches() = {
        val cacheManager = CacheManager.getInstance()
        cacheManager.removeAllCaches()
        // println("CacheUtil: Removed all application caches")
    }

    /**
      * Clears a cache.
      *
      * @param cacheName the name of the cache to be cleared.
      */
    def clearCache(cacheName: String): Unit = {
        val cacheManager = CacheManager.getInstance()
        Option(cacheManager.getCache(cacheName)) match {
            case Some(cache) =>
                cache.removeAll()
            // println(s"CacheUtil: cleared application cache '$cacheName'")
            case None =>
                throw ApplicationCacheException(s"Application cache '$cacheName' not found")
        }
    }

    /**
      * Adds a value to a cache.
      *
      * @param cacheName the name of the cache.
      * @param key       the cache key as a [[String]].
      * @param value     the value we want to cache.
      * @tparam V the type of the value we want to cache.
      */
    def put[V](cacheName: String, key: String, value: V): Unit = {
        val cacheManager = CacheManager.getInstance()
        val cacheOption = Option(cacheManager.getCache(cacheName))

        cacheOption match {
            case Some(cache) =>
                cache.put(new Element(key, value))
            case None =>
                throw ApplicationCacheException(s"Can't find application cache '$cacheName'")
        }
    }

    /**
      * Tries to ge a value from a cache.
      *
      * @param cacheName the name of the cache.
      * @param key       the cache key as a [[String]].
      * @tparam V the type of the item we try to get from the cache.
      * @return an [[Option[V]]].
      */
    def get[V](cacheName: String, key: String): Option[V] = {
        val cacheManager = CacheManager.getInstance()
        val cacheOption = Option(cacheManager.getCache(cacheName))

        cacheOption match {
            case Some(cache) =>
                Option(cache.get(key)) match {
                    case Some(element) =>
                        log.debug(s"got value: ${element.toString} from cache: $cacheName")
                        Some(element.getObjectValue.asInstanceOf[V])
                    case None =>
                        log.debug(s"no value for key: $key found in cache: $cacheName")
                        None
                }
            case None =>
                throw ApplicationCacheException(s"Can't find application cache '$cacheName'. Please check configuration of 'app.caches' in 'application.conf'")
        }

    }

    /**
      * Tries to remove a value from a cache.
      *
      * @param cacheName the name of the cache.
      * @param key       the cache key as a [[String]]
      */
    def remove(cacheName: String, key: String) {
        val cacheManager = CacheManager.getInstance()
        val cacheOption = Option(cacheManager.getCache(cacheName))

        cacheOption match {
            case Some(cache) => cache.remove(key)
            case None =>
                throw ApplicationCacheException(s"Can't find application cache '$cacheName'")
        }
    }


}

class LoggingCacheEventListener(log: Logger) extends CacheEventListener {

    def notifyElementRemoved(cache: Ehcache, element: Element): Unit = {
        log.debug("notifyElementRemoved " + cache.getName + element.toString)
    }

    def notifyElementPut(cache: Ehcache, element: Element): Unit = {
        log.debug("notifyElementPut " + cache.getName + element.toString)
    }

    def notifyElementUpdated(cache: Ehcache, element: Element): Unit = {
        log.debug("notifyElementUpdated " + cache.getName + element.toString)
    }

    def notifyElementExpired(cache: Ehcache, element: Element): Unit = {
        log.debug("notifyElementExpired " + cache.getName + element.toString)
    }

    def notifyElementEvicted(cache: Ehcache, element: Element): Unit = {
        log.debug("notifyElementEvicted " + cache.getName + element.toString)
    }

    def notifyRemoveAll(cache: Ehcache): Unit = {
        log.debug("notifyRemoveAll " + cache.getName)
    }

    def dispose(): Unit = {

    }

}