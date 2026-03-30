/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.vocabulary.RDF

import scala.jdk.CollectionConverters.*

import org.knora.webapi.messages.OntologyConstants.KnoraAdmin

/**
 * Removes cross-project membership triples from an admin data model.
 *
 * After the AdminDataQuery CONSTRUCT runs, user triples may include isInProject, isInGroup,
 * and isInProjectAdminGroup references to projects/groups other than the exported project.
 * This utility strips those cross-project references while retaining all other user triples.
 */
object AdminModelScoping {

  // Shared Jena property/resource constants for admin model operations.
  // Package-private so rewriteExistingUserTriples / rewriteNewUserTriples can reuse them.
  private[domain] val isInProjectProp           = ResourceFactory.createProperty(KnoraAdmin.IsInProject)
  private[domain] val isInGroupProp             = ResourceFactory.createProperty(KnoraAdmin.IsInGroup)
  private[domain] val isInProjectAdminGroupProp = ResourceFactory.createProperty(KnoraAdmin.IsInProjectAdminGroup)
  private[domain] val isInSystemAdminGroupProp  = ResourceFactory.createProperty(KnoraAdmin.IsInSystemAdminGroup)
  private[domain] val belongsToProjectProp      = ResourceFactory.createProperty(KnoraAdmin.BelongsToProject)
  private[domain] val userGroupType             = ResourceFactory.createResource(KnoraAdmin.UserGroup)
  private[domain] val userType                  = ResourceFactory.createResource(KnoraAdmin.User)
  private[domain] val usernameProp              = ResourceFactory.createProperty(KnoraAdmin.Username)

  /** Finds the IRI of the root user (username "root") in the model, if present. */
  def findRootUserIri(model: Model): Option[String] =
    model
      .listSubjectsWithProperty(RDF.`type`, userType)
      .asScala
      .find { user =>
        val stmt = user.getProperty(usernameProp)
        stmt != null && stmt.getObject.isLiteral && stmt.getLiteral.getString == "root"
      }
      .map(_.getURI)

  /**
   * Removes cross-project membership triples from the model in-place.
   *
   * For each user in the model:
   * - isInProject triples referencing projects other than projectIri are removed
   * - isInGroup triples referencing groups not belonging to the exported project are removed
   * - isInProjectAdminGroup triples referencing projects other than projectIri are removed
   *
   * @param model      the admin data Jena model (mutated in-place)
   * @param projectIri the IRI of the exported project
   */
  def removeNonProjectMemberships(model: Model, projectIri: String): Unit = {
    val projectResource = ResourceFactory.createResource(projectIri)

    // Collect group IRIs that belong to the exported project
    val projectGroupIris = model
      .listSubjectsWithProperty(RDF.`type`, userGroupType)
      .asScala
      .filter(_.hasProperty(belongsToProjectProp, projectResource))
      .map(_.getURI)
      .toSet

    // Find all users in the model
    val users = model.listSubjectsWithProperty(RDF.`type`, userType).asScala.toList

    val statementsToRemove = users.flatMap { user =>
      val crossProjectStatements =
        // isInProject for other projects
        user
          .listProperties(isInProjectProp)
          .asScala
          .filter(stmt => stmt.getObject.isResource && stmt.getObject.asResource().getURI != projectIri)
          .toList ++
          // isInGroup for groups not belonging to this project
          user
            .listProperties(isInGroupProp)
            .asScala
            .filter(stmt => stmt.getObject.isResource && !projectGroupIris.contains(stmt.getObject.asResource().getURI))
            .toList ++
          // isInProjectAdminGroup for other projects
          user
            .listProperties(isInProjectAdminGroupProp)
            .asScala
            .filter(stmt => stmt.getObject.isResource && stmt.getObject.asResource().getURI != projectIri)
            .toList
      crossProjectStatements
    }

    statementsToRemove.foreach(model.remove)
  }
}
