#!/bin/bash

neo_pod=$1
namespace=$2

if [[ -z ${neo_pod} || -z ${namespace} ]]; then
    echo "Usage : getNeo4jPodIp.sh <neo4j_pod> <namespace>"
    exit 1
fi

pod_ip=$(/usr/local/bin/kubectl get pod "${neo_pod}" -n "${namespace}" -o wide | grep "${neo_pod}" | awk -F' ' '{print $6}' 2>&1)

if [[ $pod_ip =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "${pod_ip}"
else
    echo "fail"
fi

