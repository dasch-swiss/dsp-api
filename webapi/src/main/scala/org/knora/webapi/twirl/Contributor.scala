package org.knora.webapi.twirl

case class Contributor(login: String,
                       apiUrl: String,
                       htmlUrl: String,
                       contributions: Int,
                       name: Option[String] = None)