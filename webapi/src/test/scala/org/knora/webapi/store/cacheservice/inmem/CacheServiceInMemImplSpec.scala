/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
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

package org.knora.webapi.store.cacheservice.inmem

import org.knora.webapi.UnitSpec
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectIdentifierADM}
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserIdentifierADM}
import org.knora.webapi.sharedtestdata.SharedTestDataADM

/**
 * This spec is used to test [[org.knora.webapi.store.cacheservice.inmem.CacheServiceInMemImpl]].
 */
class CacheServiceInMemImplSpec extends UnitSpec() {

  implicit protected val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  private val user: UserADM = SharedTestDataADM.imagesUser01
  private val project: ProjectADM = SharedTestDataADM.imagesProject

  private val inMemCache: CacheServiceInMemImpl.type = CacheServiceInMemImpl

  "The CacheServiceInMemImpl" should {

    "successfully store a user" in {
      val resFuture = inMemCache.putUserADM(user)
      resFuture map { res => res should equal(true) }
    }

    "successfully retrieve a user by IRI" in {
      val resFuture = inMemCache.getUserADM(UserIdentifierADM(maybeIri = Some(user.id)))
      resFuture map { res => res should equal(Some(user)) }
    }

    "successfully retrieve a user by USERNAME" in {
      val resFuture = inMemCache.getUserADM(UserIdentifierADM(maybeUsername = Some(user.username)))
      resFuture map { res => res should equal(Some(user)) }
    }

    "successfully retrieve a user by EMAIL" in {
      val resFuture = inMemCache.getUserADM(UserIdentifierADM(maybeEmail = Some(user.email)))
      resFuture map { res => res should equal(Some(user)) }
    }

    "successfully store a project" in {
      val resFuture = inMemCache.putProjectADM(project)
      resFuture map { res => res should equal(true) }
    }

    "successfully retrieve a project by IRI" in {
      val resFuture = inMemCache.getProjectADM(ProjectIdentifierADM(maybeIri = Some(project.id)))
      resFuture map { res => res should equal(Some(project)) }
    }

    "successfully retrieve a project by SHORTNAME" in {
      val resFuture = inMemCache.getProjectADM(ProjectIdentifierADM(maybeShortname = Some(project.shortname)))
      resFuture map { res => res should equal(Some(project)) }
    }

    "successfully retrieve a project by SHORTCODE" in {
      val resFuture = inMemCache.getProjectADM(ProjectIdentifierADM(maybeShortcode = Some(project.shortcode)))
      resFuture map { res => res should equal(Some(project)) }
    }
  }
}
