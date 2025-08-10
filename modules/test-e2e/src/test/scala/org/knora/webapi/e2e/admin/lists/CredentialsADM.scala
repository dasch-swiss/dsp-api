/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin.lists

import org.apache.pekko

import org.knora.webapi.slice.admin.domain.model.User

import pekko.http.scaladsl.model.headers.BasicHttpCredentials

/**
 * Representing user's credentials
 *
 * @param user the user's information.
 */
case class CredentialsADM(user: User, password: String) {

  def iri = user.id

  def urlEncodedIri = java.net.URLEncoder.encode(iri, "utf-8")

  def email = user.email

  def urlEncodedEmail = java.net.URLEncoder.encode(email, "utf-8")

  def basicHttpCredentials = BasicHttpCredentials(email, password)
}
