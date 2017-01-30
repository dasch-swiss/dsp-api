FROM ubuntu:16.04

MAINTAINER Ivan Subotic "ivan.subotic@unibas.ch"

# Silence debconf messages
RUN echo 'debconf debconf/frontend select Noninteractive' | debconf-set-selections

# Install.
RUN \
  sed -i 's/# \(.*multiverse$\)/\1/g' /etc/apt/sources.list && \
  apt-get update && \
  apt-get -y upgrade && \
  apt-get update && \
  apt-get -y install openjdk-8-jdk && \
  apt-get -y install byobu curl git htop man vim wget && \
  apt-get -y install unzip && \
  rm -rf /var/lib/apt/lists/*

# Set environment variables
ENV JAVA_HOME="/usr/lib/jvm/java-8-openjdk-amd64"

COPY ./graphdb-se-7.1.0-dist.zip /

RUN unzip /graphdb-se-7.1.0-dist.zip && \
    mv /graphdb-se-7.1.0 /graphdb && \
    rm -rf /graphdb-se-7.1.0-dist.zip

# Add and set license
# Notice: License needs to be acquired separately and supplied before build
COPY ./graphdb.license /localdata/graphdb/conf
COPY ./KnoraRules.pie /localdata/graphdb

EXPOSE 7200

# Set GraphDB Max and Min Heap size
ENV GDB_HEAP_SIZE="2g"

CMD ["/graphdb/bin/graphdb", "-Dgraphdb.home=/localdata/graphdb"]