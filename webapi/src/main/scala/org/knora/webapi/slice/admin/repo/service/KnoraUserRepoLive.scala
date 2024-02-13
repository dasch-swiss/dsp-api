/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.repo.service

import org.eclipse.rdf4j.model.vocabulary.*
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.`var` as variable
import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.tp
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.stream.ZStream

import dsp.valueobjects.LanguageCode
import org.knora.webapi.messages.OntologyConstants.KnoraAdmin
import org.knora.webapi.slice.admin.AdminConstants.adminDataNamedGraph
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.PasswordHash
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.repo.rdf.RdfConversions.*
import org.knora.webapi.slice.admin.repo.rdf.Vocabulary
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive.UserQueries
import org.knora.webapi.slice.common.repo.rdf.Errors.ConversionError
import org.knora.webapi.slice.common.repo.rdf.RdfResource
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException

final case class KnoraUserRepoLive(triplestore: TriplestoreService) extends KnoraUserRepo {

  override def findById(id: UserIri): Task[Option[KnoraUser]] = {
    val construct = UserQueries.findById(id)
    for {
      model    <- triplestore.queryRdfModel(construct)
      resource <- model.getResource(id.value).option
      user     <- ZIO.foreach(resource)(toUser)
    } yield user
  }
  override def findByEmail(email: Email): Task[Option[KnoraUser]] =
    findOneByQuery(UserQueries.findByEmail(email))

  override def findByUsername(username: Username): Task[Option[KnoraUser]] =
    findOneByQuery(UserQueries.findByUsername(username))

  private def findOneByQuery(construct: Construct) =
    for {
      model <- triplestore.queryRdfModel(construct)
      resource <- model
                    .getResourcesRdfType(KnoraAdmin.User)
                    .orElseFail(TriplestoreResponseException("Error while querying the triplestore"))
      user <- ZIO.foreach(resource.nextOption())(toUser)
    } yield user

  private def toUser(resource: RdfResource) = {
    def getObjectIrisConvert[A](r: RdfResource, prop: String)(implicit f: String => Either[String, A]) = for {
      iris <- r.getObjectIris(prop)
      as <- ZIO.foreach(iris)(it =>
              ZIO.fromEither(f(it.value)).mapError(err => ConversionError(s"Unable to parse $it: $err"))
            )
    } yield as

    for {
      userIri <-
        resource.iri.flatMap(it => ZIO.fromEither(UserIri.from(it.value))).mapError(TriplestoreResponseException.apply)
      username                  <- resource.getStringLiteralOrFail[Username](KnoraAdmin.Username)
      email                     <- resource.getStringLiteralOrFail[Email](KnoraAdmin.Email)
      familyName                <- resource.getStringLiteralOrFail[FamilyName](KnoraAdmin.FamilyName)
      givenName                 <- resource.getStringLiteralOrFail[GivenName](KnoraAdmin.GivenName)
      passwordHash              <- resource.getStringLiteralOrFail[PasswordHash](KnoraAdmin.Password)
      preferredLanguage         <- resource.getStringLiteralOrFail[LanguageCode](KnoraAdmin.PreferredLanguage)
      status                    <- resource.getBooleanLiteralOrFail[UserStatus](KnoraAdmin.StatusProp)
      isInProjectIris           <- getObjectIrisConvert[ProjectIri](resource, KnoraAdmin.IsInProject)
      isInGroupIris             <- getObjectIrisConvert[GroupIri](resource, KnoraAdmin.IsInGroup)
      isInSystemAdminGroup      <- resource.getBooleanLiteralOrFail[SystemAdmin](KnoraAdmin.IsInSystemAdminGroup)
      isInProjectAdminGroupIris <- getObjectIrisConvert[ProjectIri](resource, KnoraAdmin.IsInProjectAdminGroup)
    } yield KnoraUser(
      userIri,
      username,
      email,
      familyName,
      givenName,
      passwordHash,
      preferredLanguage,
      status,
      isInProjectIris,
      isInGroupIris,
      isInSystemAdminGroup,
      isInProjectAdminGroupIris
    )
  }.mapError(e => TriplestoreResponseException(e.toString))

  /**
   * Returns all instances of the type.
   *
   * @return all instances of the type.
   */
  override def findAll(): Task[List[KnoraUser]] =
    for {
      model     <- triplestore.queryRdfModel(UserQueries.findAll)
      resources <- model.getResourcesRdfType(KnoraAdmin.User).option.map(_.getOrElse(Iterator.empty))
      users     <- ZStream.fromIterator(resources).mapZIO(toUser).runCollect
    } yield users.toList

  override def save(user: KnoraUser): Task[KnoraUser] = for {
    query <- findById(user.id).map {
               case Some(_) => UserQueries.update(user)
               case None    => UserQueries.create(user)
             }
    _ <- triplestore.query(query)
  } yield user
}

object KnoraUserRepoLive {
  private object UserQueries {

    def findAll: Construct = {
      val (s, p, o) = (variable("s"), variable("p"), variable("o"))
      val query = Queries
        .CONSTRUCT(tp(s, p, o))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS))
        .where(
          s
            .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
            .and(s.has(p, o))
            .from(Vocabulary.NamedGraphs.knoraAdminIri)
        )
      Construct(query.getQueryString)
    }

    def findById(id: UserIri): Construct = {
      val s      = Rdf.iri(id.value)
      val (p, o) = (variable("p"), variable("o"))
      val query: ConstructQuery = Queries
        .CONSTRUCT(tp(s, p, o))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS))
        .where(
          s
            .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
            .and(tp(s, p, o))
            .from(Vocabulary.NamedGraphs.knoraAdminIri)
        )
      Construct(query)
    }

    def findByEmail(email: Email): Construct =
      findByProperty(Vocabulary.KnoraAdmin.email, email.value)

    def findByUsername(username: Username): Construct =
      findByProperty(Vocabulary.KnoraAdmin.username, username.value)

    private def findByProperty(property: Iri, propertyValue: String) = {
      val (s, p, o) = (variable("s"), variable("p"), variable("o"))
      val query: ConstructQuery = Queries
        .CONSTRUCT(tp(s, p, o))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS))
        .where(
          s
            .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
            .andHas(property, Rdf.literalOf(propertyValue))
            .andHas(p, o)
            .from(Vocabulary.NamedGraphs.knoraAdminIri)
        )
      Construct(query)
    }

    def create(u: KnoraUser): Update = {
      val query = Queries
        .INSERT_DATA(toTriples(u))
        .into(Rdf.iri(adminDataNamedGraph.value))
        .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS), prefix(XSD.NS))
      Update(query)
    }

    def update(u: KnoraUser): Update = {
      val userIri = Rdf
        .iri(u.id.value)
      def commonUserPattern = userIri
        .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
        .andHas(Vocabulary.KnoraAdmin.username, variable("previousUsername"))
        .andHas(Vocabulary.KnoraAdmin.email, variable("previousEmail"))
        .andHas(Vocabulary.KnoraAdmin.givenName, variable("previousGivenName"))
        .andHas(Vocabulary.KnoraAdmin.familyName, variable("previousFamilyName"))
        .andHas(Vocabulary.KnoraAdmin.status, variable("previousStatus"))
        .andHas(Vocabulary.KnoraAdmin.preferredLanguage, variable("previousPreferredLanguage"))
        .andHas(Vocabulary.KnoraAdmin.password, variable("previousPassword"))
        .andHas(Vocabulary.KnoraAdmin.isInSystemAdminGroup, variable("previousIsInSystemAdminGroup"))

      val deletePattern = commonUserPattern
        .andHas(Vocabulary.KnoraAdmin.isInProject, variable("previousIsInProject"))
        .andHas(Vocabulary.KnoraAdmin.isInGroup, variable("previousIsInGroup"))
        .andHas(Vocabulary.KnoraAdmin.isInProjectAdminGroup, variable("previousIsInProjectAdminGroup"))
      val wherePattern = commonUserPattern
        .and(userIri.has(Vocabulary.KnoraAdmin.isInProject, variable("previousIsInProject")).optional())
        .and(userIri.has(Vocabulary.KnoraAdmin.isInGroup, variable("previousIsInGroup")).optional())
        .and(
          userIri.has(Vocabulary.KnoraAdmin.isInProjectAdminGroup, variable("previousIsInProjectAdminGroup")).optional()
        )
      val query: ModifyQuery =
        Queries
          .MODIFY()
          .prefix(prefix(RDF.NS), prefix(Vocabulary.KnoraAdmin.NS), prefix(XSD.NS))
          .`with`(Rdf.iri(adminDataNamedGraph.value))
          .insert(toTriples(u))
          .delete(deletePattern)
          .where(wherePattern)
      Update(query)
    }

    private def toTriples(u: KnoraUser) = {
      val triples =
        Rdf
          .iri(u.id.value)
          .has(RDF.TYPE, Vocabulary.KnoraAdmin.User)
          .andHas(Vocabulary.KnoraAdmin.username, Rdf.literalOf(u.username.value))
          .andHas(Vocabulary.KnoraAdmin.email, Rdf.literalOf(u.email.value))
          .andHas(Vocabulary.KnoraAdmin.givenName, Rdf.literalOf(u.givenName.value))
          .andHas(Vocabulary.KnoraAdmin.familyName, Rdf.literalOf(u.familyName.value))
          .andHas(Vocabulary.KnoraAdmin.preferredLanguage, Rdf.literalOf(u.preferredLanguage.value))
          .andHas(Vocabulary.KnoraAdmin.status, Rdf.literalOf(u.status.value))
          .andHas(Vocabulary.KnoraAdmin.password, Rdf.literalOf(u.password.value))
          .andHas(Vocabulary.KnoraAdmin.isInSystemAdminGroup, Rdf.literalOf(u.isInSystemAdminGroup.value))

      u.isInProject.foreach(project => triples.andHas(Vocabulary.KnoraAdmin.isInProject, Rdf.iri(project.value)))
      u.isInGroup.foreach(group => triples.andHas(Vocabulary.KnoraAdmin.isInGroup, Rdf.iri(group.value)))
      u.isInProjectAdminGroup.foreach(projectAdminGroup =>
        triples.andHas(Vocabulary.KnoraAdmin.isInProjectAdminGroup, Rdf.iri(projectAdminGroup.value))
      )
      triples
    }
  }
  val layer = ZLayer.derive[KnoraUserRepoLive]
}
