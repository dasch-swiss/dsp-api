/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.vocabulary.RDF
import zio.test.*

import scala.jdk.CollectionConverters.*

import org.knora.webapi.messages.OntologyConstants.KnoraAdmin

object AdminModelScopingSpec extends ZIOSpecDefault {

  private val projectIri      = "http://rdfh.ch/projects/0001"
  private val otherProjectIri = "http://rdfh.ch/projects/9999"
  private val userIri         = "http://rdfh.ch/users/user001"
  private val projectGroupIri = "http://rdfh.ch/groups/0001/group1"
  private val otherGroupIri   = "http://rdfh.ch/groups/9999/group2"

  private def createModel() = {
    val model = ModelFactory.createDefaultModel()
    val user  = model.createResource(userIri)
    user.addProperty(RDF.`type`, model.createResource(KnoraAdmin.User))
    user.addProperty(
      ResourceFactory.createProperty(KnoraAdmin.IsInProject),
      model.createResource(projectIri),
    )
    user.addProperty(
      ResourceFactory.createProperty(KnoraAdmin.IsInProject),
      model.createResource(otherProjectIri),
    )
    user.addProperty(
      ResourceFactory.createProperty(KnoraAdmin.IsInGroup),
      model.createResource(projectGroupIri),
    )
    user.addProperty(
      ResourceFactory.createProperty(KnoraAdmin.IsInGroup),
      model.createResource(otherGroupIri),
    )
    user.addProperty(
      ResourceFactory.createProperty(KnoraAdmin.IsInProjectAdminGroup),
      model.createResource(projectIri),
    )
    user.addProperty(
      ResourceFactory.createProperty(KnoraAdmin.IsInProjectAdminGroup),
      model.createResource(otherProjectIri),
    )

    // Add groups with belongsToProject
    val group1 = model.createResource(projectGroupIri)
    group1.addProperty(RDF.`type`, model.createResource(KnoraAdmin.UserGroup))
    group1.addProperty(ResourceFactory.createProperty(KnoraAdmin.BelongsToProject), model.createResource(projectIri))

    val group2 = model.createResource(otherGroupIri)
    group2.addProperty(RDF.`type`, model.createResource(KnoraAdmin.UserGroup))
    group2.addProperty(
      ResourceFactory.createProperty(KnoraAdmin.BelongsToProject),
      model.createResource(otherProjectIri),
    )

    model
  }

  override def spec: Spec[TestEnvironment, Any] = suite("AdminModelScopingSpec")(
    test("retains isInProject for the exported project") {
      val model = createModel()
      AdminModelScoping.removeNonProjectMemberships(model, projectIri)
      val user = model.getResource(userIri)
      val prop = ResourceFactory.createProperty(KnoraAdmin.IsInProject)
      assertTrue(user.hasProperty(prop, model.createResource(projectIri)))
    },
    test("strips isInProject for other projects") {
      val model = createModel()
      AdminModelScoping.removeNonProjectMemberships(model, projectIri)
      val user = model.getResource(userIri)
      val prop = ResourceFactory.createProperty(KnoraAdmin.IsInProject)
      assertTrue(!user.hasProperty(prop, model.createResource(otherProjectIri)))
    },
    test("retains isInGroup for groups belonging to exported project") {
      val model = createModel()
      AdminModelScoping.removeNonProjectMemberships(model, projectIri)
      val user = model.getResource(userIri)
      val prop = ResourceFactory.createProperty(KnoraAdmin.IsInGroup)
      assertTrue(user.hasProperty(prop, model.createResource(projectGroupIri)))
    },
    test("strips isInGroup for groups belonging to other projects") {
      val model = createModel()
      AdminModelScoping.removeNonProjectMemberships(model, projectIri)
      val user = model.getResource(userIri)
      val prop = ResourceFactory.createProperty(KnoraAdmin.IsInGroup)
      assertTrue(!user.hasProperty(prop, model.createResource(otherGroupIri)))
    },
    test("retains isInProjectAdminGroup for the exported project") {
      val model = createModel()
      AdminModelScoping.removeNonProjectMemberships(model, projectIri)
      val user = model.getResource(userIri)
      val prop = ResourceFactory.createProperty(KnoraAdmin.IsInProjectAdminGroup)
      assertTrue(user.hasProperty(prop, model.createResource(projectIri)))
    },
    test("strips isInProjectAdminGroup for other projects") {
      val model = createModel()
      AdminModelScoping.removeNonProjectMemberships(model, projectIri)
      val user = model.getResource(userIri)
      val prop = ResourceFactory.createProperty(KnoraAdmin.IsInProjectAdminGroup)
      assertTrue(!user.hasProperty(prop, model.createResource(otherProjectIri)))
    },
    test("replaces isInSystemAdminGroup true with false and reports the demoted user") {
      val model        = createModel()
      val sysAdminProp = ResourceFactory.createProperty(KnoraAdmin.IsInSystemAdminGroup)
      val user         = model.getResource(userIri)
      user.addProperty(sysAdminProp, ResourceFactory.createTypedLiteral(true))
      val demoted = AdminModelScoping.clearSystemAdminFlag(model)
      val stmts   = user.listProperties(sysAdminProp).asScala.toList
      assertTrue(
        stmts.size == 1,
        stmts.forall(s => s.getObject.isLiteral && !s.getLiteral.getBoolean),
        demoted == List(userIri),
      )
    },
    test("keeps existing false flag as false and does not report a demotion") {
      val model        = createModel()
      val sysAdminProp = ResourceFactory.createProperty(KnoraAdmin.IsInSystemAdminGroup)
      val user         = model.getResource(userIri)
      user.addProperty(sysAdminProp, ResourceFactory.createTypedLiteral(false))
      val demoted = AdminModelScoping.clearSystemAdminFlag(model)
      val stmts   = user.listProperties(sysAdminProp).asScala.toList
      assertTrue(
        stmts.size == 1,
        stmts.forall(s => s.getObject.isLiteral && !s.getLiteral.getBoolean),
        demoted.isEmpty,
      )
    },
    test("does nothing when isInSystemAdminGroup is absent") {
      val model        = createModel()
      val demoted      = AdminModelScoping.clearSystemAdminFlag(model)
      val user         = model.getResource(userIri)
      val sysAdminProp = ResourceFactory.createProperty(KnoraAdmin.IsInSystemAdminGroup)
      assertTrue(
        user.listProperties(sysAdminProp).asScala.isEmpty,
        demoted.isEmpty,
      )
    },
    test("tolerates malformed literals by overwriting them with false") {
      val model        = createModel()
      val sysAdminProp = ResourceFactory.createProperty(KnoraAdmin.IsInSystemAdminGroup)
      val user         = model.getResource(userIri)
      user.addProperty(sysAdminProp, model.createLiteral("not a boolean"))
      val demoted = AdminModelScoping.clearSystemAdminFlag(model)
      val stmts   = user.listProperties(sysAdminProp).asScala.toList
      assertTrue(
        stmts.size == 1,
        stmts.forall(s => s.getObject.isLiteral && !s.getLiteral.getBoolean),
        demoted.isEmpty,
      )
    },
  )
}
