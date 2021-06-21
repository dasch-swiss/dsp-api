/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2.ontology

import akka.actor.ActorRef
import akka.util.Timeout
import org.knora.webapi.InternalSchema
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.messages.v2.responder.ontologymessages.{
  ClassInfoContentV2,
  DeleteCardinalitiesFromClassRequestV2,
  ReadOntologyV2
}
import org.knora.webapi.settings.KnoraSettingsImpl

import org.knora.webapi.messages.IriConversions._

import scala.concurrent.{ExecutionContext, Future}

/**
  * Contains methods used for deleting cardinalities from a class
  */
object DeleteCardinalitiesFromClass {

  /**
    *
    * @param internalClassIri
    * @param internalOntologyIri
    * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
    */
  def deleteCardinalitiesFromClassTaskFuture(
      settings: KnoraSettingsImpl,
      storeManager: ActorRef,
      deleteCardinalitiesFromClassRequest: DeleteCardinalitiesFromClassRequestV2,
      internalClassIri: SmartIri,
      internalOntologyIri: SmartIri
  )(implicit ec: ExecutionContext, stringFormatter: StringFormatter, timeout: Timeout): Future[ReadOntologyV2] = {
    for {
      cacheData <- Cache.getCacheData
      internalClassDef: ClassInfoContentV2 = deleteCardinalitiesFromClassRequest.classInfoContent.toOntologySchema(
        InternalSchema)

      // Check that the ontology exists and has not been updated by another user since the client last read it.
      _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
        settings,
        storeManager,
        internalOntologyIri = internalOntologyIri,
        expectedLastModificationDate = deleteCardinalitiesFromClassRequest.lastModificationDate,
        featureFactoryConfig = deleteCardinalitiesFromClassRequest.featureFactoryConfig
      )

      // Check that the class's rdf:type is owl:Class.

      rdfType: SmartIri = internalClassDef.requireIriObject(OntologyConstants.Rdf.Type.toSmartIri,
                                                            throw BadRequestException(s"No rdf:type specified"))

      _ = if (rdfType != OntologyConstants.Owl.Class.toSmartIri) {
        throw BadRequestException(s"Invalid rdf:type for property: $rdfType")
      }

      // Check that cardinalities were submitted.

      _ = if (internalClassDef.directCardinalities.isEmpty) {
        throw BadRequestException("No cardinalities specified")
      }

      // Check that the class exists

      ontology = cacheData.ontologies(internalOntologyIri)

      existingClassDef: ClassInfoContentV2 = ontology.classes
        .getOrElse(internalClassIri,
                   throw BadRequestException(
                     s"Class ${deleteCardinalitiesFromClassRequest.classInfoContent.classIri} does not exist"))
        .entityInfoContent

      // that it's a Knora resource class, and
      // that the submitted cardinalities aren't for properties that already have cardinalities directly defined on the class.

      // Check that the class isn't used in data, and that it has no subclasses.
      // TODO: If class is used in data, check additionally if the property(ies) being removed is(are) truly used and if not, then allow.

    } yield ???
  }
}
