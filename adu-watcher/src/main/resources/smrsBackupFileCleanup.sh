#!/bin/bash

enm_namespace=$1
node_type=$2
node_name=$3
log_file=$(find /var/adu/data/HighAvailability_* | head -n 1)

echo "" >> "${log_file}"
echo "=========================" >> "${log_file}"
echo "Cleaning SMRS Backup files Using Namspace: ${enm_namespace}, Node_type: ${node_type}, Node_name: ${node_name}" >> "${log_file}" 2>&1

if [[ -z "$enm_namespace" || -z "$node_type" || -z "$node_name" ]]; then
  echo "Failed to read all input parameters." >> "${log_file}"
  exit 1
fi

file_transfer_pod=$(/usr/local/bin/kubectl get pods -n  ${enm_namespace} | grep filetransfer | head -n 1 | awk -F ' ' '{print $1}' 2>&1)
echo "Using pod : ${file_transfer_pod}" >> "${log_file}"

list_command="ls -l /home/smrs/smrsroot/backup/${node_type}/${node_name}/"
remove_command="rm -rf /home/smrs/smrsroot/backup/${node_type}/${node_name}/*Hatest_backup_file_*"

echo "Remove command : ${remove_command}" >> "${log_file}"

echo "=========================" >> "${log_file}"
echo "File list before cleanup:" >> "${log_file}"
echo "=========================" >> "${log_file}"
/usr/local/bin/kubectl exec -i -n "${enm_namespace}"  "${file_transfer_pod}" -c filetransferservice -- sh -c "${list_command}" >> "${log_file}" 2>&1
echo "=========================" >> "${log_file}"

/usr/local/bin/kubectl exec -i -n "${enm_namespace}"  "${file_transfer_pod}" -c filetransferservice -- sh -c "${remove_command}" >> "${log_file}" 2>&1

echo "File list after cleanup:" >> "${log_file}"
echo "=========================" >> "${log_file}"
/usr/local/bin/kubectl exec -i -n "${enm_namespace}"  "${file_transfer_pod}" -c filetransferservice -- sh -c "${list_command}" >> "${log_file}" 2>&1
echo "=========================" >> "${log_file}"
