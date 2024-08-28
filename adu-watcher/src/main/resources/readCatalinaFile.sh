#!/bin/bash


file_name=$1
separator='=============================================================='
data_dir="/var/adu/data"
log_file=$(find /var/adu/data/HighAvailability_* | head -n 1)

if [[ -f "${data_dir}/${file_name}" ]]; then
  rm -rf "${data_dir}/${file_name}"
fi

copy_file=$(find /opt/tomcat/latest/logs/ -type f -name catalina*.log | tail -1)

echo "" >> "${log_file}"
echo "Copying .. ${copy_file}" >> "${log_file}"

cp -f ${copy_file} "${data_dir}/"

echo "File ${data_dir}/${log_file} copied." >> "${log_file}"

echo "${separator}" >> "${log_file}"
