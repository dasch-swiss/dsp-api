/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ontology.domain.service
import zio.Task
import zio.macros.accessible

import org.knora.webapi.messages.v2.responder.ontologymessages.ReadClassInfoV2
import org.knora.webapi.messages.v2.responder.ontologymessages.ReadOntologyV2
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

@accessible
trait OntologyRepo {
  def findOntologyBy(iri: InternalIri): Task[Option[ReadOntologyV2]]
  def findClassBy(iri: InternalIri): Task[Option[ReadClassInfoV2]]
}
