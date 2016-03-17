/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try

/**
  * Maintains in-memory caches, and caches values, using Ehcache (http://ehcache.org/). Each cache is accessible
  * by name from any function running in the same JVM.
  *
  * The implementation provided here is optimised for the case in which it is most efficient to load a whole set of
  * not-yet-cached items as a single operation (e.g. when we can get them from a single SPARQL query).
  *
  * Cache key classes must extend the [[Ordered]] trait, because they have to be sortable so that cache locks can
  * be acquired in a consistent order, to prevent deadlocks.
  *
  * Important: Don't use case classes for keys. Use ordinary classes, and override `equals` and `hashCode`
  * yourself, otherwise your keys won't work with Ehcache (because it's implemented in Java). It's a good idea to use
  * [[org.apache.commons.lang3.builder.HashCodeBuilder]] to generate hash codes.
  *
  */
object CacheUtil {

    val log = Logger(LoggerFactory.getLogger("org.knora.webapi.util.cache"))

    /**
      * Represents the configuration of a Knora application cache.
      * @param cacheName the name of the cache.
      * @param maxElementsInMemory the maximum number of elements in memory, before they are evicted (0 == no limit).
      * @param overflowToDisk whether to use the disk store.
      * @param eternal whether the elements in the cache are eternal, i.e. never expire.
      * @param timeToLiveSeconds the default amount of time to live for an element from its creation date.
      * @param timeToIdleSeconds the default amount of time to live for an element from its last accessed or modified date.
      */
    case class KnoraCacheConfig(cacheName: String,
                                maxElementsInMemory: Int,
                                overflowToDisk: Boolean,
                                eternal: Boolean,
                                timeToLiveSeconds: Int,
                                timeToIdleSeconds: Int)

    /**
      * Creates application caches.
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
                cache.getCacheEventNotificationService.registerListener(new MyCacheEventListener(log))
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
      * @param cacheName the name of the cache to be cleared.
      */
    def clearCache(cacheName: String): Unit = {
        val cacheManager = CacheManager.getInstance()
        Option(cacheManager.getCache(cacheName)) match {
            case Some(cache) =>
                cache.removeAll()
            // println(s"CacheUtil: cleared application cache '$cacheName'")
            case None =>
                throw new ApplicationCacheException(s"Application cache '$cacheName' not found")
        }
    }

    /**
      * Tries to get one or more values from a cache, and adds them if they don't exist. Any missing values are loaded by
      * calling the supplied query function, which takes as an argument the set of keys of the missing items. This approach
      * is designed for the case in which it is most efficient to load the values as a set (e.g. in a single SPARQL query)
      * rather than one by one.
      *
      * @param cacheName the name of the cache.
      * @param cacheKeys the cache keys, which must be instances of a class implementing [[Ordered]].
      * @param queryFun a function that will be called to get the values that are not in the cache.
      * @return a [[Map]] of keys and values from the cache or from the query function.
      */
    def getOrCacheItems[K <: Ordered[K], V](cacheName: String, cacheKeys: Set[K], queryFun: Set[K] => Future[Map[K, V]])(implicit executionContext: ExecutionContext): Future[Map[K, V]] = Future.fromTry {
        Try {
            def getItemsFromCache(cache: Cache, sortedKeys: Vector[K], useReadLocks: Boolean): Map[K, V] = {
                sortedKeys.foldLeft(Map.empty[K, V]) {
                    case (acc, key) =>
                        if (useReadLocks) {
                            cache.acquireReadLockOnKey(key)
                        }

                        try {
                            Option(cache.get(key)) match {
                                case Some(element) => acc + (key -> element.getObjectValue.asInstanceOf[V])
                                case None => acc
                            }
                        } finally {
                            if (useReadLocks) {
                                cache.releaseReadLockOnKey(key)
                            }
                        }
                }
            }

            val cacheManager = CacheManager.getInstance()
            val cacheOption = Option(cacheManager.getCache(cacheName))

            cacheOption match {
                case Some(cache) =>
                    // println("CacheUtil: These keys were requested:")
                    // println(cacheKeys)

                    // Sort the keys so that we always acquire locks in the same order, to prevent deadlocks.
                    val sortedKeys = cacheKeys.toVector.sorted

                    // See if all the elements we need are in the cache, using read locks.
                    val cachedItemsFoundWithReadLocks: Map[K, V] = getItemsFromCache(cache, sortedKeys, useReadLocks = true)
                    val cachedKeysFoundWithReadLocks = cachedItemsFoundWithReadLocks.keySet

                    // println("CacheUtil: With read locks, got the items with these keys from the cache:")
                    // println(cachedItemsFoundWithReadLocks.keySet)

                    if (cachedKeysFoundWithReadLocks == cacheKeys) {
                        // They're all in the cache, so return them.
                        // println("CacheUtil: Found all requested items in the cache with read locks")
                        cachedItemsFoundWithReadLocks
                    } else {
                        // Not all of them are in the cache, so try again with write locks.
                        val keysToTryWithWriteLocks = (cacheKeys -- cachedKeysFoundWithReadLocks).toVector.sorted
                        keysToTryWithWriteLocks.foreach(key => cache.acquireWriteLockOnKey(key))

                        try {
                            val cachedItemsFoundWithWriteLocks = getItemsFromCache(cache, keysToTryWithWriteLocks, useReadLocks = false)
                            val allCachedItems = cachedItemsFoundWithReadLocks ++ cachedItemsFoundWithWriteLocks
                            val allCachedKeys = allCachedItems.keySet

                            if (allCachedKeys == cacheKeys) {
                                // Now we've found all the items we need in the cache, so return them.
                                // println("CacheUtil: Found all requested items in the cache with a write lock")
                                allCachedItems
                            } else {
                                // There are still some items missing from the cache, so get them from the query function.
                                val keysToQuery = cacheKeys -- allCachedKeys

                                // Given the way Ehcache implements locks, there seems to be no way to avoid blocking here,
                                // because once we acquire locks in a given thread, we have to access the cache and release
                                // the locks from the same thread, otherwise we get a deadlock.
                                // TODO: See if there's another cache library that will let us run this Future asynchronously.
                                // Perhaps it would make sense to enhance spray-caching to do this.
                                val queriedItems = Await.result(queryFun(keysToQuery), 20.seconds)

                                // println("CacheUtil: Queried missing elements")

                                // Cache the queried items.
                                queriedItems.foreach {
                                    case (key, value) =>
                                        cache.put(new Element(key, value))
                                    // println(s"CacheUtil: Cached an element with key $key and value $value")
                                }

                                allCachedItems ++ queriedItems
                            }
                        } finally {
                            keysToTryWithWriteLocks.foreach(key => cache.releaseWriteLockOnKey(key))
                        }
                    }

                case None =>
                    throw new ApplicationCacheException(s"Can't find application cache '$cacheName'")
            }
        }
    }


    /**
      * Tries to get a value from a cache, and adds it if it doesn't exist, using the supplied query function.
      *
      * @param cacheName the name of the cache.
      * @param cacheKey the cache key, which must be an instance of a class implementing [[Ordered]].
      * @param queryFun a function that will be called to get the value if it's not in the cache.
      * @return a value from the cache or from the query function.
      */
    def getOrCacheItem[K <: Ordered[K], V](cacheName: String, cacheKey: K, queryFun: () => Future[V])(implicit executionContext: ExecutionContext): Future[V] = {
        def queryItem(keySet: Set[K]): Future[Map[K, V]] = {
            for {
                value <- queryFun() // TODO: refactor this so queryFun accepts a parameter (see Ontology Responder's method getNamedGraphEntityInfoV1ForNamedGraph)
            } yield Map(keySet.head -> value)
        }

        getOrCacheItems(cacheName, Set(cacheKey), queryItem).map(_ (cacheKey))
    }

    /**
      * Adds a value to a cache.
      * @param cacheName the name of the cache.
      * @param key the cache key as a [[String]].
      * @param value the value we want to cache.
      * @tparam V the type of the value we want to cache.
      */
    def put[V](cacheName: String, key: String, value: V): Unit = {
        val cacheManager = CacheManager.getInstance()
        val cacheOption = Option(cacheManager.getCache(cacheName))

        cacheOption match {
            case Some(cache) =>
                cache.put(new Element(key, value))
                println()
            case None =>
                throw new ApplicationCacheException(s"Can't find application cache '$cacheName'")
        }
    }

    /**
      * Tries to ge a value from a cache.
      * @param cacheName the name of the cache.
      * @param key the cache key as a [[String]].
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
                throw new ApplicationCacheException(s"Can't find application cache '$cacheName'")
        }

    }

    /**
      * Tries to remove a value from a cache.
      * @param cacheName the name of the cache.
      * @param key the cache key as a [[String]]
      */
    def remove(cacheName: String, key: String) {
        val cacheManager = CacheManager.getInstance()
        val cacheOption = Option(cacheManager.getCache(cacheName))

        cacheOption match {
            case Some(cache) => cache.remove(key)
            case None =>
                throw new ApplicationCacheException(s"Can't find application cache '$cacheName'")
        }
    }


}

class MyCacheEventListener(log: Logger) extends CacheEventListener {

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