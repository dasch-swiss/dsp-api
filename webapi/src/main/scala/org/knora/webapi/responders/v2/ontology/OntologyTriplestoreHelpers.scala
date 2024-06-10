/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import zio.*

import java.time.Instant
import scala.collection.immutable

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.ValuesValidator
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select

trait OntologyTriplestoreHelpers {

  /**
   * Loads a class definition from the triplestore and converts it to a [[ClassInfoContentV2]].
   *
   * @param classIri the IRI of the class to be loaded.
   * @return a [[ClassInfoContentV2]] representing the class definition.
   */
  def loadClassDefinition(classIri: SmartIri): Task[ClassInfoContentV2]

  /**
   * Loads a property definition from the triplestore and converts it to a [[PropertyInfoContentV2]].
   *
   * @param propertyIri the IRI of the property to be loaded.
   * @return a [[PropertyInfoContentV2]] representing the property definition.
   */
  def loadPropertyDefinition(propertyIri: SmartIri): Task[PropertyInfoContentV2]

  /**
   * Reads an ontology's metadata.
   *
   * @param internalOntologyIri the ontology's internal IRI.
   * @return an [[OntologyMetadataV2]], or [[None]] if the ontology is not found.
   */
  def loadOntologyMetadata(internalOntologyIri: SmartIri): Task[Option[OntologyMetadataV2]]

  /**
   * Checks that the last modification date of an ontology is the same as the one we expect it to be. If not, return
   * an error message fitting for the "before update" case.
   *
   * @param internalOntologyIri          the internal IRI of the ontology.
   * @param expectedLastModificationDate the last modification date that should now be attached to the ontology.
   * @return a failed Future if the expected last modification date is not found.
   */
  def checkOntologyLastModificationDateBeforeUpdate(
    internalOntologyIri: SmartIri,
    expectedLastModificationDate: Instant,
  ): Task[Unit]

  /**
   * Checks that the last modification date of an ontology is the same as the one we expect it to be. If not, return
   * an error message fitting for the "after update" case.
   *
   * @param internalOntologyIri          the internal IRI of the ontology.
   * @param expectedLastModificationDate the last modification date that should now be attached to the ontology.
   * @return a failed Future if the expected last modification date is not found.
   */
  def checkOntologyLastModificationDateAfterUpdate(
    internalOntologyIri: SmartIri,
    expectedLastModificationDate: Instant,
  ): Task[Unit]

  /**
   * Gets the set of subjects that refer to an ontology or its entities.
   *
   * @param ontology the ontology.
   * @return the set of subjects that refer to the ontology or its entities.
   */
  def getSubjectsUsingOntology(ontology: ReadOntologyV2): Task[Set[IRI]]
}

final case class OntologyTriplestoreHelpersLive(
  triplestore: TriplestoreService,
  stringFormatter: StringFormatter,
) extends OntologyTriplestoreHelpers {

  override def loadOntologyMetadata(internalOntologyIri: SmartIri): Task[Option[OntologyMetadataV2]] = {
    for {
      _ <- ZIO.when(!internalOntologyIri.getOntologySchema.contains(InternalSchema)) {
             ZIO.fail(AssertionException(s"Expected an internal ontology IRI: $internalOntologyIri"))
           }
      getOntologyInfoResponse <- triplestore.query(Construct(sparql.v2.txt.getOntologyInfo(internalOntologyIri)))

      metadata: Option[OntologyMetadataV2] =
        if (getOntologyInfoResponse.statements.isEmpty) {
          None
        } else {
          getOntologyInfoResponse.statements.get(
            internalOntologyIri.toString,
          ) match {
            case Some(statements: Seq[(IRI, String)]) =>
              val statementMap: Map[IRI, Seq[String]] = statements.groupBy { case (pred, _) =>
                pred
              }.map { case (pred, predStatements) =>
                pred -> predStatements.map { case (_, obj) =>
                  obj
                }
              }

              val projectIris: Seq[String] = statementMap.getOrElse(
                OntologyConstants.KnoraBase.AttachedToProject,
                throw InconsistentRepositoryDataException(
                  s"Ontology $internalOntologyIri has no knora-base:attachedToProject",
                ),
              )
              val labels: Seq[String] = statementMap.getOrElse(
                OntologyConstants.Rdfs.Label,
                Seq.empty[String],
              )
              val comments: Seq[String] = statementMap.getOrElse(
                OntologyConstants.Rdfs.Comment,
                Seq.empty[String],
              )
              val lastModDates: Seq[String] =
                statementMap.getOrElse(
                  OntologyConstants.KnoraBase.LastModificationDate,
                  Seq.empty[String],
                )

              val projectIri = if (projectIris.size > 1) {
                throw InconsistentRepositoryDataException(
                  s"Ontology $internalOntologyIri has more than one knora-base:attachedToProject",
                )
              } else {
                stringFormatter.toSmartIri(projectIris.head)
              }

              if (!internalOntologyIri.isKnoraBuiltInDefinitionIri) {
                if (projectIri.toString == KnoraProjectRepo.builtIn.SystemProject.id.value) {
                  throw InconsistentRepositoryDataException(
                    s"Ontology $internalOntologyIri cannot be in project ${KnoraProjectRepo.builtIn.SystemProject.id.value}",
                  )
                }

                if (
                  internalOntologyIri.isKnoraSharedDefinitionIri && projectIri.toString != OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject
                ) {
                  throw InconsistentRepositoryDataException(
                    s"Shared ontology $internalOntologyIri must be in project ${OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject}",
                  )
                }
              }

              val label: String = if (labels.size > 1) {
                throw InconsistentRepositoryDataException(
                  s"Ontology $internalOntologyIri has more than one rdfs:label",
                )
              } else if (labels.isEmpty) {
                internalOntologyIri.getOntologyName
              } else {
                labels.head
              }

              val comment: Option[String] = if (comments.size > 1) {
                throw InconsistentRepositoryDataException(
                  s"Ontology $internalOntologyIri has more than one rdfs:comment",
                )
              } else comments.headOption

              val lastModificationDate: Option[Instant] =
                if (lastModDates.size > 1) {
                  throw InconsistentRepositoryDataException(
                    s"Ontology $internalOntologyIri has more than one ${OntologyConstants.KnoraBase.LastModificationDate}",
                  )
                } else if (lastModDates.isEmpty) {
                  None
                } else {
                  val dateStr = lastModDates.head
                  Some(
                    ValuesValidator
                      .xsdDateTimeStampToInstant(dateStr)
                      .getOrElse(
                        throw InconsistentRepositoryDataException(
                          s"Invalid ${OntologyConstants.KnoraBase.LastModificationDate}: $dateStr",
                        ),
                      ),
                  )
                }

              Some(
                OntologyMetadataV2(
                  ontologyIri = internalOntologyIri,
                  projectIri = Some(projectIri),
                  label = Some(label),
                  comment = comment,
                  lastModificationDate = lastModificationDate,
                ),
              )

            case None => None
          }
        }
    } yield metadata
  }

  /**
   * Checks that the last modification date of an ontology is the same as the one we expect it to be.
   *
   * @param internalOntologyIri          the internal IRI of the ontology.
   * @param expectedLastModificationDate the last modification date that the ontology is expected to have.
   * @param errorFun                     a function that throws an exception. It will be called if the expected last modification date is not found.
   * @return a failed Future if the expected last modification date is not found.
   */
  private def checkOntologyLastModificationDate(
    internalOntologyIri: SmartIri,
    expectedLastModificationDate: Instant,
    errorFun: => Nothing,
  ): Task[Unit] =
    for {
      existingOntologyMetadata <- loadOntologyMetadata(internalOntologyIri)

      _ = existingOntologyMetadata match {
            case Some(metadata) =>
              metadata.lastModificationDate match {
                case Some(lastModificationDate) =>
                  if (lastModificationDate != expectedLastModificationDate) {
                    errorFun
                  }

                case None =>
                  throw InconsistentRepositoryDataException(
                    s"Ontology $internalOntologyIri has no ${OntologyConstants.KnoraBase.LastModificationDate}",
                  )
              }

            case None =>
              throw NotFoundException(
                s"Ontology $internalOntologyIri (corresponding to ${internalOntologyIri.toOntologySchema(ApiV2Complex)}) not found",
              )
          }
    } yield ()

  override def checkOntologyLastModificationDateBeforeUpdate(
    internalOntologyIri: SmartIri,
    expectedLastModificationDate: Instant,
  ): Task[Unit] =
    checkOntologyLastModificationDate(
      internalOntologyIri = internalOntologyIri,
      expectedLastModificationDate = expectedLastModificationDate,
      errorFun = throw EditConflictException(
        s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} has been modified by another user, please reload it and try again.",
      ),
    )

  override def checkOntologyLastModificationDateAfterUpdate(
    internalOntologyIri: SmartIri,
    expectedLastModificationDate: Instant,
  ): Task[Unit] =
    checkOntologyLastModificationDate(
      internalOntologyIri = internalOntologyIri,
      expectedLastModificationDate = expectedLastModificationDate,
      errorFun = throw UpdateNotPerformedException(
        s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} was not updated. Please report this as a possible bug.",
      ),
    )

  override def getSubjectsUsingOntology(ontology: ReadOntologyV2): Task[Set[IRI]] =
    for {
      isOntologyUsedSparql <- ZIO.attempt(
                                sparql.v2.txt
                                  .isOntologyUsed(
                                    ontologyNamedGraphIri = ontology.ontologyMetadata.ontologyIri,
                                    classIris = ontology.classes.keySet,
                                    propertyIris = ontology.properties.keySet,
                                  ),
                              )

      isOntologyUsedResponse <- triplestore.query(Select(isOntologyUsedSparql))

      subjects = isOntologyUsedResponse.getColOrThrow("s").toSet
    } yield subjects

  override def loadPropertyDefinition(propertyIri: SmartIri): Task[PropertyInfoContentV2] =
    triplestore
      .query(Construct(sparql.v2.txt.getPropertyDefinition(propertyIri)))
      .flatMap(_.asExtended(stringFormatter))
      .map(constructResponse =>
        OntologyHelpers.constructResponseToPropertyDefinition(propertyIri, constructResponse)(stringFormatter),
      )

  override def loadClassDefinition(classIri: SmartIri): Task[ClassInfoContentV2] =
    triplestore
      .query(Construct(sparql.v2.txt.getClassDefinition(classIri)))
      .flatMap(_.asExtended(stringFormatter))
      .map(OntologyHelpers.constructResponseToClassDefinition(classIri, _)(stringFormatter))
}

object OntologyTriplestoreHelpersLive { val layer = ZLayer.derive[OntologyTriplestoreHelpersLive] }
