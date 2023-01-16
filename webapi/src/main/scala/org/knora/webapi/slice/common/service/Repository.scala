/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.service

import zio._

/**
 * Trait for generic readonly operations on a repository for a specific type.
 *
 * @tparam Entity the type of the entity.
 * @tparam Id the type of the id of the entities.
 */
trait Repository[Entity, Id] {

  /**
   * Retrieves an entity by its id.
   *
   * @param id The identifier of type [[Id]].
   * @return the entity with the given id or [[None]] if none found.
   */
  def findById(id: Id): Task[Option[Entity]]

  /**
   * Returns all instances of the type [[Entity]] with the given IDs.
   * If some or all ids are not found, no entities are returned for these IDs.
   * Note that the order of elements in the result is not guaranteed.
   *
   * @param ids A sequence of identifiers of type [[Id]].
   * @return All found entities. The size can be equal or less than the number of given ids.
   */
  def findAllById(ids: Seq[Id]): Task[List[Entity]] = ZIO.foreach(ids)(findById).map(_.flatten.toList)

  /**
   * Checks whether an entity with the given id exists.
   *
   * @param id The identifier of type [[Id]].
   * @return true if an entity with the given id exists, false otherwise.
   */
  def existsById(id: Id): Task[Boolean] = findById(id).map(_.isDefined)

  /**
   * Returns the number of entities available.
   *
   * @return the number of entities.
   */
  def count(): Task[Long] = findAll().map(_.size)

  /**
   * Returns all instances of the type.
   *
   * @return all instances of the type.
   */
  def findAll(): Task[List[Entity]]
}

/**
 * Trait for generic CRUD (create, read, update, delete) operations on a repository for a specific type.
 *
 * @tparam Entity the type of the entity.
 * @tparam Id the type of the id of the entities.
 */
trait CrudRepository[Entity, Id] extends Repository[Entity, Id] {

  /**
   * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the entity instance completely.
   *
   * @param entity The entity to be saved.
   * @return the saved entity.
   */
  def save(entity: Entity): Task[Entity]

  /**
   * Saves all given entities.
   *
   * @param entities The entities to be saved
   * @return the saved entities. The returned [[List]] will have the same size as the entities passed as an argument.
   */
  def saveAll(entities: Seq[Entity]): Task[List[Entity]] = ZIO.foreach(entities)(save).map(_.toList)

  /**
   * Deletes a given entity.
   *
   * @param entity The entity to be deleted
   */
  def delete(entity: Entity): Task[Unit]

  /**
   * Deletes all given entities.
   *
   * @param entities The entities to be deleted
   */
  def deleteAll(entities: Seq[Entity]): Task[Unit] = ZIO.foreach(entities)(delete).unit

  /**
   * Deletes the entity with the given id.
   * If the entity is not found in the persistence store it is silently ignored.
   *
   * @param id The identifier to the entity to be deleted
   */
  def deleteById(id: Id): Task[Unit]

  /**
   *  Deletes all instances of the type [[Entity]] with the given IDs.
   *  Entities that aren't found in the persistence store are silently ignored.
   *
   * @param ids The identifiers to the entities to be deleted
   */
  def deleteAllById(ids: Seq[Id]): Task[Unit] = ZIO.foreach(ids)(deleteById).unit
}
