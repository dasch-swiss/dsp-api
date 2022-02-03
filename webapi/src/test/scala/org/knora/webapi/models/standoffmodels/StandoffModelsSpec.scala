/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.models.standoffmodels

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.StringFormatter

class StandoffModelsSpec extends CoreSpec {
  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  "StandoffModelsUtil," when {
    "XXX" should {
      "YYY" in {
        StandoffModelsUtil.do_stuff()
        XXX.make().toJsonLd()
        // TODO: write tests
      }
    }
  }

  "StandoffModels," when {
    "XXX" should {
      "YYY" in {
        // TODO: write tests
      }
    }
  }
}
