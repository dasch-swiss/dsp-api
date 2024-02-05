/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.model

import sttp.tapir.Codec
import sttp.tapir.CodecFormat
import sttp.tapir.EndpointInput
import sttp.tapir.query

import org.knora.webapi.slice.admin.api.Codecs.TapirCodec
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

object AdminQueryVariables {

  private implicit val projectIriOptionCodec: Codec[List[String], Option[ProjectIri], CodecFormat.TextPlain] =
    Codec.listHeadOption(TapirCodec.projectIri)

  val projectIriOption: EndpointInput.Query[Option[ProjectIri]] = query[Option[ProjectIri]]("projectIri")
    .description("The (optional) IRI of the project.")
    .example(Some(ProjectIri.unsafeFrom("http://rdfh.ch/projects/0042")))
}
