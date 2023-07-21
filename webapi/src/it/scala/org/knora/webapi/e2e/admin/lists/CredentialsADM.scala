package org.knora.webapi.e2e.admin.lists

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

/**
 * Representing user's credentials
 *
 * @param user the user's information.
 */
case class CredentialsADM(user: UserADM, password: String) {

  def iri = user.id

  def urlEncodedIri = java.net.URLEncoder.encode(iri, "utf-8")

  def email = user.email

  def urlEncodedEmail = java.net.URLEncoder.encode(email, "utf-8")

  def basicHttpCredentials = BasicHttpCredentials(email, password)
}
