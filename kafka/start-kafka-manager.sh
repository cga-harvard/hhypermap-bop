#!/bin/sh

cp "$KM_CONFIGFILE" conf/application.modified.conf
# local var; doesn't change global env
KM_CONFIGFILE=conf/application.modified.conf

if [[ "$KM_USERNAME" != "" && "$KM_PASSWORD" != "" ]]; then
    sed -i.bak '/^basicAuthentication/d' "$KM_CONFIGFILE"
    echo 'basicAuthentication.enabled=true' >> "$KM_CONFIGFILE"
    echo "basicAuthentication.username=${KM_USERNAME}" >> "$KM_CONFIGFILE"
    echo "basicAuthentication.password=${KM_PASSWORD}" >> "$KM_CONFIGFILE"
    echo 'basicAuthentication.realm="Kafka-Manager"' >> "$KM_CONFIGFILE"
fi
# this will allow you to customize
echo 'play.http.context = ${?KM_HTTP_CONTEXT}' >> "$KM_CONFIGFILE"

exec ./bin/kafka-manager "-Dconfig.file=${KM_CONFIGFILE}" "${KM_ARGS}" "${@}"