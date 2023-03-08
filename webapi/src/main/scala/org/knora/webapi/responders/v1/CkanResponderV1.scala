/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v1

import com.typesafe.scalalogging.LazyLogging
import zio._

import java.net.URLEncoder

import org.knora.webapi.IRI
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.v1.responder.ckanmessages._
import org.knora.webapi.messages.v1.responder.listmessages.NodePathGetRequestV1
import org.knora.webapi.messages.v1.responder.listmessages.NodePathGetResponseV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoByShortnameGetRequestV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoResponseV1
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectInfoV1
import org.knora.webapi.messages.v1.responder.resourcemessages._
import org.knora.webapi.messages.v1.responder.valuemessages.DateValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.HierarchicalListValueV1
import org.knora.webapi.messages.v1.responder.valuemessages.LinkV1
import org.knora.webapi.messages.v1.responder.valuemessages.TextValueV1
import org.knora.webapi.responders.Responder
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.util.ZioHelper

/**
 * This responder is used by the Ckan route, for serving data to the Ckan harverster, which is published
 * under http://data.humanities.ch
 */
trait CkanResponderV1 {}

final case class CkanResponderV1Live(
  messageRelay: MessageRelay,
  triplestoreService: TriplestoreService
) extends CkanResponderV1
    with MessageHandler
    with LazyLogging {

  /**
   * A user representing the Knora API server, used in those cases where a user is required.
   */
  private val systemUser = KnoraSystemInstances.Users.SystemUser.asUserProfileV1

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[CkanResponderRequestV1]

  /**
   * Receives a message extending [[CkanResponderRequestV1]], and returns an appropriate response message.
   */
  override def handle(msg: ResponderRequest): Task[CkanResponseV1] = msg match {
    case CkanRequestV1(projects, limit, info, userProfile) =>
      getCkanResponseV1(projects, limit, info, userProfile)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  private def getCkanResponseV1(
    project: Option[Seq[String]],
    limit: Option[Int],
    info: Boolean,
    userProfile: UserADM
  ): Task[CkanResponseV1] = {

    logger.debug("Ckan Endpoint:")
    logger.debug(s"Project: $project")
    logger.debug(s"Limit: $limit")
    logger.debug(s"Info: $info")

    val defaultProjectList: Seq[String] = Vector("dokubib", "incunabula")

    val selectedProjectsFuture = project match {
      case Some(projectList) =>
        // look up resources only for these projects if allowed
        val allowedProjects = projectList.filter(defaultProjectList.contains(_))
        getProjectInfos(
          projectNames = allowedProjects,
          userProfile = userProfile
        )

      case None =>
        // return our default project map, containing all projects that we want to serve over the Ckan endpoint
        getProjectInfos(
          projectNames = defaultProjectList,
          userProfile = userProfile
        )
    }

    for {
      projects <- selectedProjectsFuture
      ckanProjects: Seq[Task[CkanProjectV1]] = projects flatMap {
                                                 case ("dokubib", projectFullInfo) =>
                                                   Some(
                                                     getDokubibCkanProject(
                                                       pinfo = projectFullInfo,
                                                       limit = limit,
                                                       userProfile = userProfile
                                                     )
                                                   )

                                                 case ("incunabula", projectFullInfo) =>
                                                   Some(
                                                     getIncunabulaCkanProject(
                                                       pinfo = projectFullInfo,
                                                       limit = limit,
                                                       userProfile = userProfile
                                                     )
                                                   )

                                                 case _ => None
                                               }
      result  <- ZioHelper.sequence(ckanProjects)
      response = CkanResponseV1(projects = result)
    } yield response
  }

  ///////////////////////////////////////////////////////////////////////////////////////////
  // DOKUBIB
  ///////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Dokubib specific Ckan export stuff
   */
  private def getDokubibCkanProject(
    pinfo: ProjectInfoV1,
    limit: Option[Int],
    userProfile: UserADM
  ): Task[CkanProjectV1] = {

    /*
         - datasets
            - bild 1
                - files
                    - bild 1
            - bild 2
     */

    val pIri = pinfo.id

    val ckanPInfo =
      CkanProjectInfoV1(
        shortname = pinfo.shortname,
        longname = pinfo.longname.getOrElse(pinfo.shortname),
        ckan_tags = Vector("Kulturanthropologie"),
        ckan_license_id = "CC-BY-NC-SA-4.0"
      )

    val datasetsFuture: Task[Seq[CkanProjectDatasetV1]] = for {
      bilder <- getDokubibBilderIRIs(pIri, limit)

      bilderMitPropsFuture = getResources(
                               iris = bilder,
                               userProfile = userProfile
                             )

      bilderMitProps <- bilderMitPropsFuture
      dataset <- ZioHelper.sequence(bilderMitProps.map { case (iri, info, props) =>
                   flattenInfo(info)
                   flattenProps(props).map { propsMap =>
                     CkanProjectDatasetV1(
                       ckan_title = propsMap.getOrElse("Description", ""),
                       ckan_tags = propsMap.getOrElse("Title", "").split("/").toIndexedSeq.map(_.trim),
                       files = Vector(
                         CkanProjectDatasetFileV1(
                           ckan_title = propsMap.getOrElse("preview_loc_origname", ""),
                           data_url = "http://localhost:3333/v1/assets/" + URLEncoder.encode(iri, "UTF-8"),
                           data_mimetype = "",
                           source_url = "http://salsah.org/resources/" + URLEncoder.encode(iri, "UTF-8"),
                           source_mimetype = ""
                         )
                       ),
                       other_props = propsMap
                     )
                   }
                 })
    } yield dataset

    for {
      datasets <- datasetsFuture
      result    = CkanProjectV1(project_info = ckanPInfo, project_datasets = Some(datasets))
    } yield result

  }

  /**
   * Get all Bilder IRIs for Dokubib
   */
  private def getDokubibBilderIRIs(projectIri: IRI, limit: Option[Int]): Task[Seq[IRI]] =
    for {
      sparqlQuery <- ZIO.attempt(
                       org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                         .ckanDokubib(projectIri, limit)
                         .toString()
                     )
      response                             <- triplestoreService.sparqlHttpSelect(sparqlQuery)
      responseRows: Seq[VariableResultsRow] = response.results.bindings

      bilder: Seq[String] = responseRows.groupBy(_.rowMap("bild")).keys.toVector

      result = limit match {
                 case Some(n) if n > 0 => bilder.take(n)
                 case _                => bilder
               }

    } yield result

  ///////////////////////////////////////////////////////////////////////////////////////////
  // INCUNABULA
  ///////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Incunabula specific Ckan stuff
   */
  private def getIncunabulaCkanProject(
    pinfo: ProjectInfoV1,
    limit: Option[Int],
    userProfile: UserADM
  ): Task[CkanProjectV1] = {

    /*
         - datasets
            - book 1
                - files
                    - page 1
                    - page 2
                    ...
            - book 2
     */

    val pIri = pinfo.id

    val ckanPInfo =
      CkanProjectInfoV1(
        shortname = pinfo.shortname,
        longname = pinfo.longname.getOrElse(pinfo.shortname),
        ckan_tags = Vector("Kunstgeschichte"),
        ckan_license_id = "CC-BY-4.0"
      )

    // get book and page IRIs in project
    val booksWithPagesFuture = getIncunabulaBooksWithPagesIRIs(pIri, limit)

    val bookDatasetsFuture = booksWithPagesFuture.flatMap { singleBook =>
      val bookDataset = singleBook map { case (bookIri: IRI, pageIris: Seq[IRI]) =>
        val bookResourceFuture = getResource(bookIri, userProfile)

        bookResourceFuture flatMap { case (_, _, bProps) =>
          flattenProps(bProps).flatMap { bPropsMap =>
            val files = pageIris map { pageIri =>
              getResource(
                iri = pageIri,
                userProfile = userProfile
              ) flatMap { case (pIri, _, pProps) =>
                flattenProps(pProps).map { pPropsMap =>
                  CkanProjectDatasetFileV1(
                    ckan_title = pPropsMap.getOrElse("Page identifier", ""),
                    ckan_description = Some(pPropsMap.getOrElse("Beschreibung (Richtext)", "")),
                    data_url = "http://localhost:3333/v1/assets/" + URLEncoder.encode(pIri, "UTF-8"),
                    data_mimetype = "",
                    source_url = "http://salsah.org/resources/" + URLEncoder.encode(pIri, "UTF-8"),
                    source_mimetype = "",
                    other_props = Some(pPropsMap)
                  )
                }
              }
            }
            ZioHelper.sequence(files) map { filesList =>
              CkanProjectDatasetV1(
                ckan_title = bPropsMap.getOrElse("Title", ""),
                ckan_tags = Vector("Kunstgeschichte"),
                files = filesList,
                other_props = bPropsMap
              )
            }
          }
        }
      }
      ZioHelper.sequence(bookDataset.toVector)
    }

    for {
      bookDatasets <- bookDatasetsFuture
      result        = CkanProjectV1(project_info = ckanPInfo, project_datasets = Some(bookDatasets))
    } yield result
  }

  /**
   * Get all book IRIs for Incunabula
   */
  private def getIncunabulaBooksWithPagesIRIs(projectIri: IRI, limit: Option[Int]): Task[Map[IRI, Seq[IRI]]] =
    for {
      sparqlQuery <- ZIO.attempt(
                       org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                         .ckanIncunabula(projectIri, limit)
                         .toString()
                     )
      response                             <- triplestoreService.sparqlHttpSelect(sparqlQuery)
      responseRows: Seq[VariableResultsRow] = response.results.bindings

      booksWithPages: Map[String, Seq[String]] =
        responseRows.groupBy(_.rowMap("book")).map { case (bookIri: String, rows: Seq[VariableResultsRow]) =>
          (
            bookIri,
            rows.map(row => row.rowMap("page"))
          )
        }

      result = limit match {
                 case Some(n) if n > 0 => booksWithPages.take(n)
                 case _                => booksWithPages
               }

    } yield result

  ///////////////////////////////////////////////////////////////////////////////////////////
  // GENERAL
  ///////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Get detailed information about the projects
   */
  private def getProjectInfos(
    projectNames: Seq[String],
    userProfile: UserADM
  ): Task[Seq[(String, ProjectInfoV1)]] =
    ZioHelper.sequence {
      for {
        pName <- projectNames

        projectInfoResponseFuture = messageRelay
                                      .ask[ProjectInfoResponseV1](
                                        ProjectInfoByShortnameGetRequestV1(
                                          shortname = pName,
                                          userProfileV1 = Some(userProfile.asUserProfileV1)
                                        )
                                      )

        result = projectInfoResponseFuture.map(_.project_info) map { pInfo =>
                   (pName, pInfo)
                 }
      } yield result
    }

  /**
   * Get all information there is about these resources
   */
  private def getResources(
    iris: Seq[IRI],
    userProfile: UserADM
  ): Task[Seq[(String, Option[ResourceInfoV1], Option[PropsV1])]] =
    ZioHelper.sequence {
      for {
        iri <- iris

        resource = getResource(
                     iri = iri,
                     userProfile = userProfile
                   )
      } yield resource
    }

  /**
   * Get all information there is about this one resource
   */
  private def getResource(
    iri: IRI,
    userProfile: UserADM
  ): Task[(String, Option[ResourceInfoV1], Option[PropsV1])] = {

    val resourceFullResponseFuture =
      messageRelay
        .ask[ResourceFullResponseV1](
          ResourceFullGetRequestV1(
            iri = iri,
            userADM = userProfile
          )
        )

    resourceFullResponseFuture map { case ResourceFullResponseV1(resInfo, _, props, _, _) =>
      (iri, resInfo, props)
    }
  }

  private def flattenInfo(maybeInfo: Option[ResourceInfoV1]): Map[String, String] = {
    def maybeTuple(key: String, maybeValue: Option[String]): Option[(String, String)] =
      maybeValue.map(value => (key, value))

    maybeInfo match {
      case None => Map()
      case Some(info) =>
        val listOfOptions = Vector(
          maybeTuple("project_id", Some(info.project_id)),
          maybeTuple("person_id", Some(info.person_id)),
          maybeTuple("restype_id", Some(info.restype_id)),
          maybeTuple("restype_label", info.restype_label),
          maybeTuple("restype_description", info.restype_description),
          maybeTuple("restype_iconsrc", info.restype_iconsrc),
          maybeTuple("firstproperty", info.firstproperty)
        ) ++ flattenLocation(info.preview)
        listOfOptions.flatten.toMap
    }
  }

  private def flattenLocation(location: Option[LocationV1]): Seq[Option[(String, String)]] =
    location match {
      case None => Vector(None)
      case Some(loc) =>
        Vector(Some(("preview_loc_origname", loc.origname.getOrElse(""))))
    }

  private def flattenProps(props: Option[PropsV1]): Task[Map[String, String]] =
    if (props.nonEmpty) {
      val properties: Seq[PropertyV1] = props.get.properties

      val propMap: Task[Map[IRI, IRI]] = properties.foldLeft(ZIO.attempt(Map.empty[String, String])) {
        case (acc, propertyV1: PropertyV1) =>
          val label = propertyV1.label.getOrElse("")

          val values: Task[Seq[String]] = propertyV1.valuetype_id.get match {
            case OntologyConstants.KnoraBase.TextValue =>
              ZIO.succeed(propertyV1.values.map(literal => textValue2String(literal.asInstanceOf[TextValueV1])))

            case OntologyConstants.KnoraBase.DateValue =>
              ZIO.succeed(propertyV1.values.map(literal => dateValue2String(literal.asInstanceOf[DateValueV1])))

            case OntologyConstants.KnoraBase.ListValue =>
              ZioHelper
                .sequence(
                  propertyV1.values.map(literal => listValue2String(literal.asInstanceOf[HierarchicalListValueV1]))
                )

            case OntologyConstants.KnoraBase.Resource =>
              ZIO.succeed(propertyV1.values.map(literal => resourceValue2String(literal.asInstanceOf[LinkV1])))

            case _ => ZIO.succeed(Vector())
          }

          values.flatMap { values =>
            if (label.nonEmpty && values.nonEmpty) {
              acc.map(_ + (label -> values.mkString(",")))
            } else {
              acc
            }
          }
      }

      propMap
    } else {
      ZIO.succeed(Map.empty[String, String])
    }

  private def textValue2String(text: TextValueV1): String = text.utf8str

  private def dateValue2String(date: DateValueV1): String =
    if (date.dateval1 == date.dateval2) {
      date.dateval1 + " " + date.era1 + ", " + date.calendar.toString + " " + date.era2
    } else {
      date.dateval1 + " " + date.era1 + ", " + date.dateval2 + ", " + date.calendar.toString + " " + date.era2
    }

  private def listValue2String(list: HierarchicalListValueV1): Task[String] =
    for {
      nodePath <- messageRelay.ask[NodePathGetResponseV1](NodePathGetRequestV1(list.hierarchicalListIri, systemUser))
      labels    = nodePath.nodelist map (element => element.label.getOrElse(""))
    } yield labels.mkString(" / ")

  private def resourceValue2String(resource: LinkV1): String = resource.valueLabel.get

}
object CkanResponderV1Live {
  val layer: URLayer[TriplestoreService with MessageRelay, CkanResponderV1] =
    ZLayer.fromZIO {
      for {
        mr      <- ZIO.service[MessageRelay]
        ts      <- ZIO.service[TriplestoreService]
        handler <- mr.subscribe(CkanResponderV1Live(mr, ts))
      } yield handler
    }
}
