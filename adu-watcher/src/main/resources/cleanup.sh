#!/bin/bash

tomcat_pid=$(ps -ef | grep tomcat | grep -v grep | awk '{print $2}')
if [[ ! -z ${tomcat_pid} ]]; then
    echo "Removing tomcat .."
    /opt/tomcat/latest/bin/shutdown.sh
    sleep 2s
fi

#remove watcher-init
init_pid=$(ps -ef | grep watcherInit | grep -v grep | awk '{print $2}')
if [[ ! -z ${init_pid} ]]; then
    kill -9 ${init_pid}
fi

adu_count=$(ps -ef | grep adu | grep -v grep | grep -v cleanup | awk '{print $2}' | wc -l)
if [[ ${adu_count} -gt 0 ]]; then
    echo "Removing adu processes .."
    ps -ef | grep adu | grep -v grep | grep -v cleanup | awk '{print $2}' | xargs kill -9 > /dev/null
fi

rm -rf /tmp/adu /opt/tomcat/
rm -rf /var/adu/
rm -rf /usr/local/adu/
rm -rf /usr/local/bin/kubectl
rm -rf /usr/local/bin/helm

#Remove pkg.
pip3 uninstall kubernetes -y
pip3 uninstall jproperties -y
yum erase jq -y
yum erase python3-pip -y
yum erase openldap-clients -y

echo "Cleanup completed."