FROM openjdk:10-jre-slim-sid

RUN \
    apt-get -qq update && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/*

RUN \
    wget https://www.yourkit.com/download/docker/YourKit-JavaProfiler-2018.04-docker.zip -P /tmp/ && \
    unzip /tmp/YourKit-JavaProfiler-2018.04-docker.zip -d /usr/local && rm /tmp/YourKit-JavaProfiler-2018.04-docker.zip

ADD stage /webapi

WORKDIR /webapi

EXPOSE 3333
EXPOSE 10001

ENTRYPOINT ["bin/webapi", "-J-agentpath:/usr/local/YourKit-JavaProfiler-2018.04/bin/linux-x86-64/libyjpagent.so=port=10001,listen=all"]

LABEL MAINTAINER="400790+subotic@users.noreply.github.com"
