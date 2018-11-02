### STAGE 1: Build ###

# We label our stage as 'builder'
FROM node:8-stretch as builder

LABEL maintainer="ivan.subotic@unibas.ch"

# Install.
RUN \
  sed -i 's/# \(.*multiverse$\)/\1/g' /etc/apt/sources.list && \
  apt-get -qq update && \
  apt-get install -y --no-install-recommends git=1:2.11.0-3+deb9u4

RUN git clone -b master --single-branch --depth=1 https://github.com/dhlab-basel/Salsah.git /salsah

RUN cp /salsah/package.json /salsah/yarn.lock ./

RUN yarn config set no-progress && yarn cache clean

## Storing node modules on a separate layer will prevent unnecessary npm installs at each build
RUN yarn && mkdir /ng-app && cp -R ./node_modules ./ng-app

WORKDIR /ng-app

## Copy the source
RUN cp -R /salsah/. .

## Copy our custom environment.ts
## COPY ./environments/environment.prod.ts ./src/environments/environment.prod.ts

## Build the angular app in production mode and store the artifacts in dist folder
RUN "$(npm bin)"/ng build --prod --build-optimizer


### STAGE 2: Setup ###

FROM nginx:1.15-alpine

LABEL maintainer="ivan.subotic@unibas.ch"

## Copy our default nginx config
COPY nginx/default.conf /etc/nginx/conf.d/

## Remove default nginx website
RUN rm -rf /usr/share/nginx/html/*

## From 'builder' stage copy over the artifacts in dist folder to default nginx public folder
COPY --from=builder /ng-app/dist /usr/share/nginx/html

EXPOSE 4200

CMD ["nginx", "-g", "daemon off;"]
