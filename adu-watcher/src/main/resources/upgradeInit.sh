#!/bin/bash

action=$1
log_file=$(find /var/adu/data/HighAvailability_* | head -n 1)

echo "" >> "${log_file}"
echo "Running UpgradeInit with action: ${action}" >> "${log_file}"

pid=$(ps -ef | grep upgradeStatus | grep -v grep | awk '{print $2}')
if [[ ! -z ${pid} ]]; then
    echo "Killing upgradeStatus process pid: ${pid}" >> "${log_file}"
    kill -9 ${pid}
fi

if [[ ${action} == "start" ]]; then
    echo "Starting upgradeStatus process ..." >> "${log_file}"
    nohup /usr/local/adu/upgradeStatus.py 2>/dev/null &
    pid=$(ps -ef | grep upgradeStatus | grep -v grep | awk '{print $2}')
    echo "upgradeStatus.py started with pid: ${pid}" >> "${log_file}"
fi

