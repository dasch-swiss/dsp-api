/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.store.cacheservicemessages

import org.knora.webapi.core.RelayedMessage
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.store.StoreRequest

sealed trait CacheServiceRequest extends StoreRequest with RelayedMessage

/**
 * Message equesting to write project to cache.
 */
case class CacheServicePutProjectADM(value: ProjectADM) extends CacheServiceRequest

/**
 * Message requesting to retrieve project from cache.
 */
case class CacheServiceGetProjectADM(identifier: ProjectIdentifierADM) extends CacheServiceRequest

/**
 * Queries Cache Service status.
 */
case object CacheServiceGetStatus extends CacheServiceRequest

/**
 * Represents a response for [[CacheServiceGetStatus]].
 */
sealed trait CacheServiceStatusResponse

/**
 * Represents a positive response for [[CacheServiceGetStatus]].
 */
case object CacheServiceStatusOK extends CacheServiceStatusResponse

/**
 * Represents a negative response for [[CacheServiceGetStatus]].
 */
case object CacheServiceStatusNOK extends CacheServiceStatusResponse
