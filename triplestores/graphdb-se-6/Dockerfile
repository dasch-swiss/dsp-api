FROM tomcat:8-jre8

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

# Update base installation
RUN apt-get update && \
    apt-get install -y --no-install-recommends

# Add GraphdSB-SE distribution (tar is automatically extracted by ADD command)
ADD ./graphdb-se-6.6.4.tar /

# Add and set license
# Notice: License needs to acquired separately and supplied before build
ADD ./GRAPHDB_SE.license /graphdb-se-6.6.4/
ENV GRAPHDB_LICENSE_FILE=/graphdb-se-6.4.4/graphdb.license

EXPOSE 8080
CMD ["/graphdb-se-6.6.4/startup.sh"]