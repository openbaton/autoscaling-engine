#!/bin/sh
ASE_SERVICE_KEY=$(grep ase.service.key /etc/openbaton/openbaton-ase.properties|cut -d'=' -f 2)

if [ -z "$ASE_SERVICE_KEY" ];then
    until curl -sSf http://nfvo:8080;do sleep 10;done

    USER=admin
    PASS=openbaton
    NFVO_IP=nfvo
    NFVO_PORT=8080
    PID=$(openbaton -pid none -u "$USER" -p "$PASS" -ip "$NFVO_IP" --nfvo-port "$NFVO_PORT" project list|grep default|awk '{print $2}')
    SERVICE_KEY=$(openbaton -pid "$PID" -u "$USER" -p "$PASS" -ip "$NFVO_IP" --nfvo-port "$NFVO_PORT" service create '{"name":"autoscaling-engine", "roles":["*"]}')

    export ASE_SERVICE_KEY="$SERVICE_KEY"
    sed -i "s/ase.service.key =/ase.service.key=$SERVICE_KEY/g" /etc/openbaton/openbaton-ase.properties
fi

exec java -jar /ase.jar --spring.config.location=file:/etc/openbaton/openbaton-ase.properties
