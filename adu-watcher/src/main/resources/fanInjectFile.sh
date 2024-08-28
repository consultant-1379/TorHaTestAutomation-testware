#!/bin/bash

action=$1
file_name=$2
enm_namespace=$3
path="/var/www/html/file/v1/files/ericsson/pmic1/"

pod_name=$(/usr/local/bin/kubectl get pods -n "$enm_namespace" | grep fileaccessnbi | awk '{print $1}' | head -n 1)

if [[ "$pod_name" =~ "fileaccessnbi" ]]; then
  if [[ "$action" = 'create' ]]; then
    kubectl -n "$enm_namespace" exec deploy/fileaccessnbi -- fallocate -l 1M "$path""$file_name"
    output=$(kubectl -n "$enm_namespace" exec "$pod_name" -- ls "$path" | grep "$file_name")
    if [[ "${output}" = "$file_name" ]]; then
      echo "success"
    else
      echo "file not created : ${output}"
    fi
  else
    kubectl -n "$enm_namespace" exec deploy/fileaccessnbi -- rm "$path""$file_name"
    output=$(kubectl -n "$enm_namespace" exec "$pod_name" -- ls "$path" | grep "$file_name")
    if [[ "$output" = "$file_name" ]]; then
      echo "file not deleted : ${output}"
    else
      echo "success"
    fi
  fi
else
  echo "FAN not found: ${pod_name}"
fi