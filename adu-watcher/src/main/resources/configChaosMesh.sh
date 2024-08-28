#!/bin/bash


function install_chaos_mesh() {
    /usr/local/bin/helm repo add chaos-mesh https://charts.chaos-mesh.org

    /usr/local/bin/helm install chaos-mesh chaos-mesh/chaos-mesh \
    --version 2.0.6 \
    --set chaos-mesh.dashboard.create=true \
    --set chaos-mesh.dashboard.securityMode=false \
    --set chaos-mesh.chaosDaemon.runtime=containerd \
    --set chaos-mesh.chaosDaemon.socketPath=/run/containerd/containerd.sock \
    --dependency-update --namespace=${namespace}

    not_ready_count=$(/usr/local/bin/kubectl get pod -n ${namespace} | grep chaos | grep -v '1/1' | wc -l)
    count=0
    while [[ ${not_ready_count} > 0 ]]; do
       not_ready_count=$(/usr/local/bin/kubectl get pod -n ${namespace} | grep chaos | grep -v '1/1' | wc -l)
       echo "waiting to deploy chaos-mesh .. " >> "${log_file}"
       sleep 2s
       if [[ ${count} > 100 ]]; then
           break;
       fi
       ((count++))
    done

    /usr/local/bin/helm repo remove chaos-mesh

    if [[ ${not_ready_count} == 0 ]]; then
        echo "Chaos-mesh deployed successfully." >> "${log_file}"
    else
        echo "Chaos-mesh install failed." >> "${log_file}"
        exit 1
    fi
}

function remove_chaos_mesh() {

    /usr/local/bin/helm uninstall chaos-mesh -n ${namespace}

    if [[ $? == 0 ]]; then
        echo "Chaos-mesh removed successfully." >> "${log_file}"
    else
        echo "Chaos-mesh remove failed." >> "${log_file}"
        exit 1
    fi
}

action=$1
namespace=$2
log_file=$(find /var/adu/data/HighAvailability_* | head -n 1)

case "${action}" in

    install)
        echo $(install_chaos_mesh)
        ;;

    remove)
        echo $(remove_chaos_mesh)
        ;;
esac