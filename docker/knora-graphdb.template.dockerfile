FROM @GRAPHDB_IMAGE@

ADD stage/scripts/KnoraRules.pie /graphdb/KnoraRules.pie

LABEL MAINTAINER="400790+subotic@users.noreply.github.com"
