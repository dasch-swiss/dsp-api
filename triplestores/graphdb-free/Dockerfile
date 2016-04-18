FROM tomcat:8-jre8

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

# Update base installation
RUN apt-get update && \
    apt-get install -y --no-install-recommends

# Download GraohDB-Free, copy war files to tomcat directory, clean-up
RUN curl -sS -o /tmp/graphdb.zip -L http://download.ontotext.com/owlim/35e2ce74-fa36-11e5-97f4-06278a02ff7a/graphdb-free-6.6.4-dist.zip && \
    unzip /tmp/graphdb.zip -d /opt && \
    cp /opt/graphdb-free-6.6.4/sesame_graphdb/graphdb-workbench-free.war /usr/local/tomcat/webapps/. && \
    rm -Rf /tmp/graphdb.zip && \
    rm -Rf /opt/graphdb-free-6.6.4

CMD ["catalina.sh", "run"]