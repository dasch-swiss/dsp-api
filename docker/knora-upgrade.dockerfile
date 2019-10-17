FROM python:3.7-stretch

COPY stage/upgrade /upgrade
RUN pip install -r /upgrade/requirements.txt
ENTRYPOINT /upgrade/update-repository.py
CMD -h

LABEL MAINTAINER="400790+subotic@users.noreply.github.com"
