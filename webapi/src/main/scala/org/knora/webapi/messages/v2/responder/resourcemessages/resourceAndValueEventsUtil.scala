/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.v2.responder.resourcemessages

/**
 * Contains string constants for resource and value event types.
 */
object ResourceAndValueEventsUtil {

  val CREATE_RESOURCE_EVENT          = "createdResource"
  val DELETE_RESOURCE_EVENT          = "deletedResource"
  val UPDATE_RESOURCE_METADATA_EVENT = "updatedResourceMetadata"
  val CREATE_VALUE_EVENT             = "createdValue"
  val UPDATE_VALUE_CONTENT_EVENT     = "updatedValueContent"
  val UPDATE_VALUE_PERMISSION_EVENT  = "updatedValuePermission"
  val DELETE_VALUE_EVENT             = "deletedValue"
}
