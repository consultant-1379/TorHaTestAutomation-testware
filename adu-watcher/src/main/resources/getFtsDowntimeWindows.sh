#!/bin/bash

enm_namespace=$1
start_str="generating.new.host.keys:"
stop_str="Ended.executing.poststartscripts|post.start.scripts.end"
log_file=$(find /var/adu/data/HighAvailability_* | head -n 1)

echo "" >> "${log_file}"
echo "=========================" >> "${log_file}"
echo "Getting FTS downtime windows Using Namspace: ${enm_namespace}" >> "${log_file}" 2>&1

if [[ -z "$enm_namespace" ]]; then
  echo "Failed to read all input parameters." >> "${log_file}"
  exit 1
fi

file_transfer_pods=$(/usr/local/bin/kubectl get pods -n  ${enm_namespace} | grep filetransfer | awk -F ' ' '{print $1}' | tr '\n' ' ' 2>&1)
file_transfer_pods=$(echo ${file_transfer_pods} | sed 's/ *$//g')

for fts_pod in ${file_transfer_pods}
  do
    echo "Using pod : ${fts_pod}" >> "${log_file}"

    startDate=$(/usr/local/bin/kubectl exec -i -n "${enm_namespace}" "${fts_pod}" -c filetransferservice -- sh -c "/bin/egrep -h '${start_str}' /var/log/messages | cut -b -15 | tail -n 1")
    echo "startDate : ${startDate}" >> "${log_file}"
    if [[ -z "${startDate}" ]]; then
      echo "Off String not found!" >> "${log_file}"
    else
      offTime=$(/usr/local/bin/kubectl exec -i -n "${enm_namespace}" "${fts_pod}" -c filetransferservice -- sh -c "date --date='${startDate}' +%Y%m%d%H%M%S")
    fi

    stopDate=$(/usr/local/bin/kubectl exec -i -n "${enm_namespace}" "${fts_pod}" -c filetransferservice -- sh -c "/bin/egrep -h '${stop_str}' /var/log/messages | cut -b -15 | tail -n 1")
    echo "stopDate  : ${stopDate}" >> "${log_file}"
    echo "" >> "${log_file}"
    if [[ -z "${stopDate}" ]]; then
      echo "On String not found!" >> "${log_file}"
    else
      onTime=$(/usr/local/bin/kubectl exec -i -n "${enm_namespace}" "${fts_pod}" -c filetransferservice -- sh -c "date --date='${stopDate}' +%Y%m%d%H%M%S")
    fi

    if [[ "${startDate}" && "${stopDate}" ]]; then
      echo "${offTime}"
      echo "${onTime}"
    else
      echo "No matching Off/On Strings found for pod : ${fts_pod}" >> "${log_file}"
    fi
  done

echo "=========================" >> "${log_file}"

