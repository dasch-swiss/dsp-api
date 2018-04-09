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

package org.knora.webapi.messages.v1.responder.ckanmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests

/**
  * An abstract trait representing a request message that can be sent to `CkanResponderV1`.
  */
sealed trait CkanResponderRequestV1 extends KnoraRequestV1

/**
  * Represents an API request payload that asks the Knora API server to return Ckan data
  *
  * @param projects
  * @param limit
  * @param info
  * @param userProfile
  */
case class CkanRequestV1(projects: Option[Seq[String]], limit: Option[Int], info: Boolean, userProfile: UserADM) extends CkanResponderRequestV1

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API response

/**
  * Represents an API response with the Ckan data
  *
  * @param projects
  */
case class CkanResponseV1(projects: Seq[CkanProjectV1]) extends KnoraResponseV1 {
    def toJsValue = CkanV1JsonProtocol.ckanResponseV1Format.write(this)
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  *
  * @param project_info
  * @param project_datasets
  */
case class CkanProjectV1(project_info: CkanProjectInfoV1, project_datasets: Option[Seq[CkanProjectDatasetV1]] = None)

case class CkanProjectInfoV1(shortname: String, longname: String, ckan_tags: Seq[String], ckan_license_id: String)

case class CkanProjectDatasetV1(ckan_title: String,
                                ckan_tags: Seq[String],
                                files: Seq[CkanProjectDatasetFileV1],
                                other_props: Map[String, String])

case class CkanProjectDatasetFileV1(ckan_title: String,
                                    ckan_description: Option[String] = None,
                                    data_url: String,
                                    data_mimetype: String,
                                    source_url: String,
                                    source_mimetype: String,
                                    other_props: Option[Map[String, String]] = None)

// dokubib structure
/*
case class DokubibCkanProjectDatasetV1(resid: IRI,
                                       resclass_name: String,
                                       resclass_label: String,
                                       resclass_description: String,
                                       resclass_iconsrc: String,
                                       image_origname: String,
                                       image_url: String,
                                       salsah_url: String,
                                       files: Seq[DokubibCkanProjectDatasetFileV1],
                                       source_url: String,
                                       dokubib_titel: String,
                                       dokubib_signatur: String,
                                       dokubib_bildnr: String,
                                       dc_description: String,
                                       dokubib_jahreszeit: String,
                                       dokubib_jahrzehnt: String,
                                       dokunin_jahr_exakt: String,
                                       dokubib_bildformat: CkanDokubibBildformatV1,
                                       dokubib_erfassungsdatum: String,
                                       dokubib_mutationsdatum: String,
                                       dokubib_bearbeiter: String,
                                       dokubib_urheber: CkanDokubibUrheberV1,
                                       dokubib_copyright: CkanDokubibCopyrightV1,
                                       ckan_title: String,
                                       ckan_tags: Seq[String])

case class DokubibCkanProjectDatasetFileV1(ckan_title: String,
                                           data_url: String,
                                           data_mimetype: String,
                                           source_url: String,
                                           source_mimetype: String)

case class CkanDokubibBildformatV1(dokubib_bildart: String,
                                   dokubib_erwerbsdatum: String,
                                   dokubib_stueckzahl: String,
                                   dokubib_zugangsart: String)

case class CkanDokubibUrheberV1(salsah_lastname: String,
                                salsah_firstname: String,
                                salsah_institution: String,
                                salsah_city: String,
                                salsah_zipcode: String)

case class CkanDokubibCopyrightV1(salsah_lastname: String,
                                  salsah_firstname: String,
                                  salsah_institution: String,
                                  salsah_address: String,
                                  salsah_city: String,
                                  salsah_zipcode: String,
                                  salsah_phone: String,
                                  salsah_email: String)
*/

// incunabula structure
/*
case class IncunabulaCkanProjectDatasetV1(resid: String,
                                          resclass_name: String,
                                          resclass_label: String,
                                          resclass_iconsrc: String,
                                          dc_title: String,
                                          dc_creator: String,
                                          dc_publisher: String,
                                          incunabula_publoc: String,
                                          incunabula_pubdate: String,
                                          incunabula_location: String,
                                          salsah_uri: String,
                                          incunabula_physical_desc: String,
                                          incunabula_citation: Seq[String],
                                          ckan_tags: Seq[String],
                                          ckan_title: String,
                                          ckan_description: String,
                                          source_url: String,
                                          files: Seq[IncunabulaCkanProjectDatasetFileV1])


case class IncunabulaCkanProjectDatasetFileV1(resid: String,
                                              resclass_name: String,
                                              resclass_label: String,
                                              resclass_description: String,
                                              resclass_iconsrc: String,
                                              image_origname: String,
                                              image_url: String,
                                              salsah_url: String,
                                              incunabula_pagenum: String,
                                              salsah_seqnum: String,
                                              dc_description: String,
                                              salsah_comment: String,
                                              salsah_origname: String,
                                              data_url: String,
                                              data_mimetype: String,
                                              source_url: String,
                                              source_mimetype: String,
                                              ckan_title: String,
                                              ckan_description: String)

*/

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON for Ckan.
  */
object CkanV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol {

    implicit val ckanProjectDatasetFileV1Format: JsonFormat[CkanProjectDatasetFileV1] = jsonFormat7(CkanProjectDatasetFileV1)
    implicit val ckanProjectDatasetV1Format: JsonFormat[CkanProjectDatasetV1] = jsonFormat4(CkanProjectDatasetV1)
    implicit val ckanProjectInfoV1Format: JsonFormat[CkanProjectInfoV1] = jsonFormat4(CkanProjectInfoV1)
    implicit val ckanProjectV1Format: JsonFormat[CkanProjectV1] = jsonFormat2(CkanProjectV1)
    implicit val ckanResponseV1Format: RootJsonFormat[CkanResponseV1] = jsonFormat1(CkanResponseV1)
}
