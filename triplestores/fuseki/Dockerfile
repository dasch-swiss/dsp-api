FROM java:8-jre

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

# Update base installation
RUN apt-get update && \
    apt-get install -y --no-install-recommends

# Add Fuseki
COPY . /fuseki
WORKDIR /fuseki

EXPOSE 3030
CMD ["./fuseki-server"]