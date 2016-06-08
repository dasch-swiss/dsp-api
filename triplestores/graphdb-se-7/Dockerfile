FROM tomcat:8-jre8

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

# Update base installation
RUN apt-get update && \
    apt-get install -y --no-install-recommends

# Add GraphdSB-SE distribution (tar is automatically extracted by ADD command)
ADD ./graphdb-se-7.0.0.tar /

# Add GraphDB configuration
ADD ./graphdb.properties /graphdb-se-7.0.0/conf/

# Add license to a place where graphdb will look for it
# Notice: License needs to acquired separately and supplied before build
ADD ./graphdb.license /graphdb-se-7.0.0/

# Expose port 8080 so that it can be accessed from outside of the container
EXPOSE 8080

# Start GraphDB-SE in server mode (no workbench)
CMD ["/graphdb-se-7.0.0/graphdb". "-s"]