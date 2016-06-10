FROM subotic/tomcat:8-jdk8

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

# Update base installation
RUN apt-get update && \
    apt-get install -y --no-install-recommends

# Add GraphdSB-SE distribution (tar is automatically extracted by ADD command)
ADD ./graphdb-se-6.6.4-war/*.war /usr/local/tomcat/webapps/

# Add and set license
# Notice: License needs to be acquired separately and supplied before build
ADD ./GRAPHDB_SE.license /usr/local/tomcat/

ADD ./KnoraRules.pie /usr/local/tomcat

EXPOSE 8080
CMD ["catalina.sh", "run"]