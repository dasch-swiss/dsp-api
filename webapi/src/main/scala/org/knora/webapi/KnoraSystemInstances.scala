/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi

import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.ontologiesmessages.OntologyInfoShortADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

/**
  * This object represents built-in User and Project instances.
  */
object KnoraSystemInstances {

    object Users {

        /**
          * Represents the anonymous user.
          */
        val AnonymousUser = UserADM(
            id = OntologyConstants.KnoraBase.AnonymousUser,
            email = "anonymous@localhost",
            password = None,
            token = None,
            givenName = "Knora",
            familyName = "Anonymous",
            status = true,
            lang = "en",
            groups = Seq.empty[GroupADM],
            projects = Seq.empty[ProjectADM],
            sessionId = None,
            permissions = PermissionsDataADM()
        )

        /**
          * Represents the system user used internally.
          */
        val SystemUser = UserADM(
            id = OntologyConstants.KnoraBase.SystemUser,
            email = "system@localhost",
            password = None,
            token  = None,
            givenName  = "Knora",
            familyName  = "System",
            status = true,
            lang = "en",
            groups = Seq.empty[GroupADM],
            projects = Seq.empty[ProjectADM],
            sessionId = None,
            permissions = PermissionsDataADM()
        )
    }

    object Projects {

        /**
          * Represents the system project.
          */
        val SystemProject = ProjectADM(
            id = OntologyConstants.KnoraBase.SystemProject,
            shortname = "SystemProject",
            shortcode = Some("FFFF"),
            longname = Some("Knora System Project"),
            description = Some("Knora System Project"),
            keywords = None,
            logo = None,
            institution = None,
            ontologies = Seq(OntologyInfoShortADM(
                ontologyIri = "http://www.knora.org/ontology/knora-base",
                ontologyName = "knora-base")),
            status = true,
            selfjoin = false
        )

    }

}
