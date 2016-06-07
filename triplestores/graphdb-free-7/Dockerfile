FROM tomcat:8-jre8

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

# Update base installation
RUN apt-get update && \
    apt-get install -y --no-install-recommends

# Add license and war
ADD 
ADD war/*.war /usr/local/tomcat/webapps/

CMD ["catalina.sh", "run"]