#!/bin/bash

function main() {
    case "${command}" in
        read_grace_period)
            echo $(read_grace_period)
            ;;
        read_psv)
            echo $(read_psv)
            ;;
    esac
}

function read_grace_period() {
    pod_name=$(/usr/local/bin/kubectl get pods -n "${namespace}" | grep filetransferservice | awk '{print $1}' | head -n 1)
    grace_period=$(/usr/local/bin/kubectl get pods -n "${namespace}" "${pod_name}"  -o jsonpath={.spec.terminationGracePeriodSeconds})
    echo "${grace_period}"
}

function read_psv(){
    psv=$(/usr/local/bin/kubectl get cm eric-enm-version-configmap -n "${namespace}" -o jsonpath={.metadata.annotations."ericsson\.com/product-set-version"})
    echo "${psv}"
}

command=$1
namespace=$2

main
