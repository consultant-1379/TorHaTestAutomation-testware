#!/bin/bash

PASSWORD="eric@1234"
nexus_url="https://arm1s11-eiffel004.eiffel.gic.ericsson.se:8443/nexus"
cluster_id=$1
host_type=$2
tmp_dir="/tmp/adu"
adu_dir="/usr/local/adu"

function gateway_config() {
    if [[ ! -f "/usr/bin/jq" ]]; then
      yum install jq -y 2> /dev/null
    fi
    echo `curl https://atvdit.athtem.eei.ericsson.se/api/deployments?q=name=${cluster_id} 2>/dev/null` > ${tmp_dir}/1.log
    doc_id=`jq -r '.[].documents[] | select(.schema_name=="cloud_native_enm_kube_config") | .document_id' ${tmp_dir}/1.log`
    echo "doc_id=${doc_id}"
    doc_uri="https://atvdit.athtem.eei.ericsson.se/api/documents/${doc_id}"
    echo `curl ${doc_uri} 2>/dev/null` > ${tmp_dir}/2.log
    jq '.content' ${tmp_dir}/2.log > "${tmp_dir}/${cluster_id}_config"
    id; pwd; mkdir -p ~/.kube
    cp -rf ${tmp_dir}/${cluster_id}_config .kube/config
    rm -rf ${tmp_dir}/1.log ${tmp_dir}/2.log ${tmp_dir}/${cluster_id}_config
}

tomcat_pid=$(ps -ef | grep tomcat | grep -v grep | awk '{print $2}')
if [[ ! -z ${tomcat_pid} ]]; then
    echo "Removing tomcat ..."
    kill -9 ${tomcat_pid}
fi
rm -rf ${tmp_dir} /opt/tomcat
mkdir -p ${tmp_dir}
cd ${tmp_dir}

if [[ ${host_type} == "gateway" ]]; then
    gateway_config
fi

# download tomcat
wget -c ${nexus_url}/service/local/repositories/central/content/org/apache/tomcat/tomcat/9.0.36/tomcat-9.0.36.tar.gz
mkdir -p /opt/tomcat
tar xf tomcat-9.0.36.tar.gz -C /opt/tomcat
ln -s /opt/tomcat/apache-tomcat-9.0.36/ /opt/tomcat/latest
sh -c 'chmod +x /opt/tomcat/latest/bin/*.sh'

# All watcher scripts can also be copied after web deployment from watcher file (AP).
sh -c 'chmod +x /usr/local/adu/*.sh'
sh -c 'chmod +x /usr/local/adu/*.py'

# configure keystore:
/usr/bin/keytool -genkey -alias tomcat -keyalg RSA -keystore /opt/tomcat/latest/conf/.keystore -keypass ${PASSWORD} -storepass ${PASSWORD} -dname "OU=ericssonOAM, O=Ericsson" 2> /dev/null

# patch server.xml:
#TODO port 8080 already used on gateway
sed -i~ '/<Service name="Catalina">/a <Connector\n    protocol="org.apache.coyote.http11.Http11NioProtocol"\n    port="8443" maxThreads="200"\n    maxParameterCount="1000"\n    scheme="https" secure="true" SSLEnabled="true"\n    keystoreFile="\/opt\/tomcat\/latest\/conf\/.keystore" keystorePass="eric@1234"\n    clientAuth="false" sslProtocol="TLS"\/>' /opt/tomcat/latest/conf/server.xml

# copy war file.
rm -rf /var/adu/data/
mkdir -p /var/adu/data/
cp -f ${adu_dir}/watcher.war /opt/tomcat/latest/webapps/
cp -f ${adu_dir}/*.properties /var/adu/data/
cd

if [[ ! $(command -v python3) > /dev/null ]]; then
    yum install python3-pip -y
    yum install openldap-clients -y
fi

if [[ ! $(command -v kubectl) > /dev/null ]]; then
    curl -LO https://dl.k8s.io/release/v1.29.0/bin/linux/amd64/kubectl --insecure
    chmod +x kubectl
    mv kubectl /usr/local/bin/
fi

if [[ ! $(command -v helm) > /dev/null ]]; then
    curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3
    chmod +x get_helm.sh
    ./get_helm.sh
fi

pip3 install --trusted-host files.pythonhosted.org --trusted-host pypi.org --trusted-host pypi.python.org kubernetes
pip3 install --trusted-host files.pythonhosted.org --trusted-host pypi.org --trusted-host pypi.python.org jproperties

rm -rf ${tmp_dir}

# start server
/opt/tomcat/latest/bin/startup.sh

sleep 10s

nohup ${adu_dir}/watcherInit.py 2>/dev/null &

