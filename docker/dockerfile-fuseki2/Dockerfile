FROM java:8-jre

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

# Update base installation
RUN apt-get update && \
    apt-get install -y --no-install-recommends

# Add Fuseki
COPY ../../webapi/_fuseki /fuseki

EXPOSE 3030
CMD ["/fuseki/fuseki-server"]