FROM openjdk:8

ENV SBT_VERSION 0.13.16

RUN \
  curl -L -o sbt-$SBT_VERSION.deb http://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb &&\
  dpkg -i sbt-$SBT_VERSION.deb &&\
  rm sbt-$SBT_VERSION.deb &&\
  apt-get update &&\
  apt-get install sbt &&\
  sbt sbtVersion

ENV WORK /opt/scalaServer
WORKDIR $WORK
ADD . $WORK

EXPOSE 80

CMD export JAVA_OPTS="-Dhttp.server.threads=$(sed -n '3p' /etc/httpd.conf | awk '{print $2}') -Dhttp.server.fileRoot=$(sed -n '4p' /etc/httpd.conf | awk '{print $2}') -Dhttp.server.port=$(sed -n '1p' /etc/httpd.conf | awk '{print $2}')" &&\
sbt run