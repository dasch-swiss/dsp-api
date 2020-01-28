FROM @GRAPHDB_IMAGE@

COPY stage/scripts/KnoraRules.pie /graphdb/KnoraRules.pie
COPY stage/scripts/graphdb.license /graphdb/graphdb.license

LABEL MAINTAINER="400790+subotic@users.noreply.github.com"
