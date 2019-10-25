package org.knora.webapi.responders

package object admin {

    // Global lock IRI used for project creation and update
    val PROJECTS_GLOBAL_LOCK_IRI = "http://rdfh.ch/projects"

    // Global lock IRI used for permission creation and update
    val PERMISSIONS_GLOBAL_LOCK_IRI = "http://rdfh.ch/permissions"
}
