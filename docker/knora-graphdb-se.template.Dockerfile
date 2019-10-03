FROM ontotext/graphdb:@GRAPHDB_SE_VERSION@-se

ADD stage/scripts/KnoraRules.pie /graphdb/KnoraRules.pie

LABEL MAINTAINER="400790+subotic@users.noreply.github.com"
