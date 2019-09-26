# This dockerfile allows to send messages from a docker container,
# useful in a CICD process.

FROM groovy:2.5.8-jre12

USER root

ADD slack.groovy /

# Expected env variables
ENV BOT_TOKEN       notset
ENV MESSAGE         sample message

WORKDIR /
# Just to source out grapes

USER groovy
RUN groovy -Dgrape.root=/home/groovy/slack-grapes /slack.groovy


CMD ["groovy", "-Dgrape.root=/home/groovy/slack-grapes", "/slack.groovy"]
