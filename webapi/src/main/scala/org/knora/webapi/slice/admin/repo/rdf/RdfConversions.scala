package org.knora.webapi.slice.admin.repo.rdf

import dsp.valueobjects.V2
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.common.repo.rdf.LangString

object RdfConversions {
  implicit val shortcodeConverter: String => Either[String, Shortcode] =
    Shortcode.from

  implicit val shortnameConverter: String => Either[String, Shortname] =
    Shortname.from

  implicit val longnameConverter: String => Either[String, Longname] =
    Longname.from

  implicit val descriptionConverter: LangString => Either[String, Description] = langString =>
    Description.from(V2.StringLiteralV2(langString.value, langString.lang))

  implicit val keywordConverter: String => Either[String, Keyword] =
    Keyword.from

  implicit val logoConverter: String => Either[String, Logo] =
    Logo.from

  implicit val statusConverter: Boolean => Either[String, Status] =
    value => Right(Status.from(value))

  implicit val selfjoinConverter: Boolean => Either[String, SelfJoin] =
    value => Right(SelfJoin.from(value))
}
