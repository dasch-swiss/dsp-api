package org.knora.rdflib.rdf4j.repository

import java.io.IOException

import org.eclipse.rdf4j.http.protocol.UnauthorizedException
import org.eclipse.rdf4j.repository.{Repository, RepositoryException}
import org.eclipse.rdf4j.repository.manager.{RemoteRepositoryManager, RepositoryManager}

object Repository {

    def connect(url: String, username: String, password: String): RemoteRepositoryManager = {
        try {
            val manager: RemoteRepositoryManager = new RemoteRepositoryManager(url);
            manager.setUsernameAndPassword(username, password)
            manager
        } catch {
            case e: UnauthorizedException =>
                throw new Exception("Authentication for user failed", e);
            case e: IOException =>
                throw new Exception("Failed to access the server", e)
            case e: RepositoryException =>
                throw new Exception("Failed to access the server", e)
        }
    }

    def create(url: String): Repository = {

        import org.eclipse.rdf4j.repository.config.RepositoryConfig;

        val repositoryId: String = "test-db";
        val repConfig: RepositoryConfig = new RepositoryConfig(repositoryId, repositoryTypeSpec);
        manager.addRepositoryConfig(repConfig);

        Repository repository = manager.getRepository(repositoryId);

    }


}
