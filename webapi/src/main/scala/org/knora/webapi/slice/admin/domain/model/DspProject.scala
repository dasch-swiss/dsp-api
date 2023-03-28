package org.knora.webapi.slice.admin.domain.model
import dsp.valueobjects.V2.StringLiteralV2
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

case class DspProject(
  id: InternalIri,
  shortname: String,
  shortcode: String,
  longname: Option[String],
  description: List[StringLiteralV2],
  keywords: List[String],
  logo: Option[String],
  status: Boolean,
  selfjoin: Boolean
)
