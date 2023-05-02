package org.knora.webapi.slice.admin.domain.service

import zio.Ref
import zio.Task
import zio.UIO
import zio.ULayer
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.common.service.CrudRepository
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
final case class KnoraProjectRepoInMemory(projects: Ref[List[KnoraProject]])
    extends KnoraProjectRepo
    with CrudRepository[KnoraProject, InternalIri] {

  /**
   * Retrieves an entity by its id.
   *
   * @param id The identifier of type [[ProjectIdentifierADM]].
   * @return the entity with the given id or [[None]] if none found.
   */
  override def findById(id: ProjectIdentifierADM): Task[Option[KnoraProject]] = projects.get.map(
    _.find(
      id match {
        case ProjectIdentifierADM.ShortcodeIdentifier(shortcode) => _.shortcode == shortcode.value
        case ProjectIdentifierADM.ShortnameIdentifier(shortname) => _.shortname == shortname.value
        case ProjectIdentifierADM.IriIdentifier(iri)             => _.id.value == iri.value
      }
    )
  )

  /**
   * Retrieves an entity by its id.
   *
   * @param id The identifier of type [[InternalIri]].
   * @return the entity with the given id or [[None]] if none found.
   */
  override def findById(id: InternalIri): Task[Option[KnoraProject]] = projects.get.map(_.find(_.id == id))

  /**
   * Returns all instances of the type.
   *
   * @return all instances of the type.
   */
  override def findAll(): Task[List[KnoraProject]] = projects.get

  /**
   * Saves a given entity. Use the returned instance for further operations as the save operation might have changed the entity instance completely.
   *
   * @param entity The entity to be saved.
   * @return the saved entity.
   */
  override def save(entity: KnoraProject): Task[KnoraProject] = projects.update(entity :: _).as(entity)

  /**
   * Deletes a given entity.
   *
   * @param entity The entity to be deleted
   */
  override def delete(entity: KnoraProject): Task[Unit] = deleteById(entity.id)

  /**
   * Deletes the entity with the given id.
   * If the entity is not found in the persistence store it is silently ignored.
   *
   * @param id The identifier to the entity to be deleted
   */
  override def deleteById(id: InternalIri): Task[Unit] = projects.update(_.filterNot(_.id == id)).unit
}

object KnoraProjectRepoInMemory {
  val layer: ULayer[KnoraProjectRepoInMemory] =
    ZLayer.fromZIO(Ref.make(List.empty[KnoraProject]).map(KnoraProjectRepoInMemory(_)))
}
