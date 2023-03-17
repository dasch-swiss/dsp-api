package org.knora.webapi.slice.admin.domain.model
import dsp.valueobjects.V2.StringLiteralV2
import org.knora.webapi.slice.resourceinfo.domain.InternalIri

case class DspProject(
  id: InternalIri,
  shortname: String,
  shortcode: String,
  longname: Option[String],
  description: Seq[StringLiteralV2],
  keywords: Seq[String],
  logo: Option[String],
  status: Boolean,
  selfjoin: Boolean
)
