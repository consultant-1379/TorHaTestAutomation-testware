#!/usr/bin/python3

from kubernetes import client, config
from kubernetes.client.rest import ApiException
from aduLoggerConfig import initLogger
from datetime import datetime
from jproperties import Properties
import subprocess
import time
import sys
import os

logger = initLogger('aduWatcher.py')

properties = Properties()
with open("/var/adu/data/adu.properties", "rb") as p_file:
    properties.load(p_file, "utf-8")

namespace = properties.get("namespace").data
logger.info("Using Namespace: %s", namespace)

listOfDependencies = properties.get("dependencies").data.split(";")
logger.info("listOfDependencies : %s", listOfDependencies)
dep_map = {}
for dep in listOfDependencies:
    dep_data = dep.split(":")
    dep_name = dep_data[0]
    instance_list = dep_data[1].split(",")
    dep_map[dep_name] = instance_list

listOfMultiDependencies = properties.get("multi.dependencies").data.split(";")
logger.info("Multi listOfDependencies : %s", listOfMultiDependencies)
dep_multi_map = {}
for dep in listOfMultiDependencies:
    dep_data = dep.split(":")
    dep_name = dep_data[0]
    instance_multi_list = dep_data[1].split(",")
    dep_multi_map[dep_name] = instance_multi_list

# Check if "eric-oss-ingress-controller-nx" deployment is available.
ingress_command = "/usr/local/bin/kubectl get deployment eric-oss-ingress-controller-nx -n " + namespace + " | grep eric-oss-ingress" \
                                                                                            "-controller-nx | wc -l "
ingress_output = os.popen(ingress_command)
ingress_status = ingress_output.read()
logger.info("ingress_output : " + ingress_status)

if ingress_status.rstrip() == "1":
    logger.info("Ingress eric-oss-ingress-controller-nx is available.")
    ingress_value = "true"
else:
    del dep_map["ingress"]
    logger.info("Ingress eric-oss-ingress-controller-nx is NOT available.")
    ingress_value = "false"

properties["ingress.deployment.available"] = ingress_value
with open("/var/adu/data/adu.properties", "wb") as w_file:
    properties.store(w_file, "utf-8")

logger.info("dep_map : %s", str(dep_map))
config.load_kube_config()
api_instance = client.CoreV1Api()
timeout_seconds = 5
fmt = "%Y%m%d%H%M%S"
counter = 0


def upgrade_running():
    return True


while upgrade_running():
    try:
        api_response = api_instance.list_namespaced_endpoints(namespace.rstrip(), timeout_seconds=timeout_seconds)

        for key, dep_list in dep_map.items():
            online = False
            for dep_name in dep_list:
                if dep_name == "cnom":
                    for i in api_response.items:
                        if "eric-esm-server" in i.metadata.name:
                            if i.subsets:
                                pod = i.subsets[0]
                                if not str(pod.addresses) == "None":
                                    for j in pod.addresses:
                                        if j.ip.strip():
                                            online = True
                                            break
                    break
                if not dep_name == "cnom" and not dep_name == "postgres":
                    for i in api_response.items:
                        if dep_name == i.metadata.name:
                            if i.subsets:
                                pod = i.subsets[0]
                                # if address is None, then dependency is offline.
                                if not str(pod.addresses) == "None":
                                    for j in pod.addresses:
                                        if j.ip.strip():
                                            online = True
                                            break
                                break
                        else:
                            continue
                if not online:
                    if dep_name == "elasticsearch" or dep_name == "eshistory":  # ingest offline.
                        break
                    continue  # Check for multi-instance dependency
                else:
                    if dep_name == "elasticsearch" or dep_name == "eshistory":  # ingest online check for data pods.
                        online = False
                        continue
                    elif dep_name == "elasticsearch-transport-data" or dep_name == "eshistory-transport-data":
                        online = True
                    break
            if online:
                printCmd = "echo \"{time}, ONLINE\" >> /var/adu/data/{dependency}.csv".format(time=datetime.now().strftime(fmt), dependency=key)
                subprocess.call(printCmd, stderr=subprocess.STDOUT, shell=True)
            else:
                printCmd = "echo \"{time}, OFFLINE\" >> /var/adu/data/{dependency}.csv".format(time=datetime.now().strftime(fmt), dependency=key)
                subprocess.call(printCmd, stderr=subprocess.STDOUT, shell=True)

        for key, dep_multi_list in dep_multi_map.items():
            for dep_name in dep_multi_list:
                for i in api_response.items:
                    if dep_name == i.metadata.name:
                        if i.subsets:
                            pod = i.subsets[0]
                            if dep_name == "filetransferservice":
                                if str(pod.addresses) == "None":
                                    logger.warning("None condition for FTS!")
                                    printCmd_1 = "echo \"{time},none,none,OFFLINE\" >> /var/adu/data/filetransferservice_1.csv".format(time=datetime.now().strftime(fmt))
                                    printCmd_2 = "echo \"{time},none,none,OFFLINE\" >> /var/adu/data/filetransferservice_2.csv".format(time=datetime.now().strftime(fmt))
                                elif len(pod.addresses) == 2:
                                    num = 0
                                    ip_1_changed = False
                                    ip_2_changed = False
                                    for j in pod.addresses:
                                        num = num + 1
                                        if num == 1:
                                            ip_1 = j.ip.strip()
                                            printCmd_1 = "echo \"{time},{pod_name},{ip},ONLINE\" >> /var/adu/data/filetransferservice_1.csv".format(time=datetime.now().strftime(fmt),pod_name=j.target_ref.name,ip=j.ip.strip())
                                        else:
                                            ip_2 = j.ip.strip()
                                            printCmd_2 = "echo \"{time},{pod_name},{ip},ONLINE\" >> /var/adu/data/filetransferservice_2.csv".format(time=datetime.now().strftime(fmt),pod_name=j.target_ref.name,ip=j.ip.strip())
                                elif len(pod.addresses) == 1:
                                    j = pod.addresses[0]
                                    if ip_1 == j.ip.strip():
                                        ip_2_changed = True
                                        printCmd_1 = "echo \"{time},{pod_name},{ip},ONLINE\" >> /var/adu/data/filetransferservice_1.csv".format(time=datetime.now().strftime(fmt),pod_name=j.target_ref.name,ip=j.ip.strip())
                                        printCmd_2 = "echo \"{time},none,none,OFFLINE\" >> /var/adu/data/filetransferservice_2.csv".format(time=datetime.now().strftime(fmt))
                                    elif ip_2 == j.ip.strip():
                                        ip_1_changed = True
                                        printCmd_1 = "echo \"{time},none,none,OFFLINE\" >> /var/adu/data/filetransferservice_1.csv".format(time=datetime.now().strftime(fmt))
                                        printCmd_2 = "echo \"{time},{pod_name},{ip},ONLINE\" >> /var/adu/data/filetransferservice_2.csv".format(time=datetime.now().strftime(fmt),pod_name=j.target_ref.name,ip=j.ip.strip())
                                    else:
                                        if ip_1_changed:
                                            ip_1 = j.ip.strip()
                                            printCmd_1 = "echo \"{time},{pod_name},{ip},ONLINE\" >> /var/adu/data/filetransferservice_1.csv".format(time=datetime.now().strftime(fmt),pod_name=j.target_ref.name,ip=j.ip.strip())
                                            printCmd_2 = "echo \"{time},none,none,OFFLINE\" >> /var/adu/data/filetransferservice_2.csv".format(time=datetime.now().strftime(fmt))
                                        elif ip_2_changed:
                                            ip_2 = j.ip.strip()
                                            printCmd_1 = "echo \"{time},none,none,OFFLINE\" >> /var/adu/data/filetransferservice_1.csv".format(time=datetime.now().strftime(fmt))
                                            printCmd_2 = "echo \"{time},{pod_name},{ip},ONLINE\" >> /var/adu/data/filetransferservice_2.csv".format(time=datetime.now().strftime(fmt),pod_name=j.target_ref.name,ip=j.ip.strip())
                                        else:
                                            printCmd_1 = "echo \"{time},none,none,OFFLINE\" >> /var/adu/data/filetransferservice_1.csv".format(time=datetime.now().strftime(fmt))
                                            printCmd_2 = "echo \"{time},none,none,OFFLINE\" >> /var/adu/data/filetransferservice_2.csv".format(time=datetime.now().strftime(fmt))
                                else:
                                    printCmd_1 = "echo \"{time},none,none,OFFLINE\" >> /var/adu/data/filetransferservice_1.csv".format(time=datetime.now().strftime(fmt))
                                    printCmd_2 = "echo \"{time},none,none,OFFLINE\" >> /var/adu/data/filetransferservice_2.csv".format(time=datetime.now().strftime(fmt))
                                subprocess.call(printCmd_1, stderr=subprocess.STDOUT, shell=True)
                                subprocess.call(printCmd_2, stderr=subprocess.STDOUT, shell=True)
                            else:
                                if not str(pod.addresses) == "None":
                                    for j in pod.addresses:
                                        if j.ip.strip():
                                            if dep_name == "postgres":
                                                print_data = "postgres-0" + ", " + j.ip.strip() + ", online"
                                            elif dep_name == "postgres-replica":
                                                print_data = "postgres-1" + ", " + j.ip.strip() + ", online"
                                            else:
                                                print_data = j.target_ref.name + ", " + j.ip.strip() + ", online"
                                        else:
                                            if dep_name == "postgres":
                                                print_data = "postgres-0" + ", none, offline"
                                            elif dep_name == "postgres-replica":
                                                print_data = "postgres-1" + ", none, offline"
                                            else:
                                                print_data = j.target_ref.name + ", none, offline"
                                        printCmd = "echo \"{time}, {print_data}\" >> /var/adu/data/{dependency}-pod-status-data.csv".\
                                            format(time=datetime.now().strftime(fmt), print_data=print_data, dependency=dep_name)
                                        subprocess.call(printCmd, stderr=subprocess.STDOUT, shell=True)
                                else:
                                    if dep_name == "postgres":
                                        print_data = "postgres-0" + ", None, offline"
                                    elif dep_name == "postgres-replica":
                                        print_data = "postgres-1" + ", None, offline"
                                    else:
                                        print_data = dep_name + ", None, offline"
                                    printCmd = "echo \"{time}, {print_data}\" >> /var/adu/data/{dependency}-pod-status-data.csv".\
                                        format(time=datetime.now().strftime(fmt), print_data=print_data, dependency=dep_name)
                                    subprocess.call(printCmd, stderr=subprocess.STDOUT, shell=True)

    except ApiException as e:
        logger.warning("Exception when calling CoreV1Api->list_endpoints_for_all_namespaces: %s", e)
    except:
        exc_type, exc_obj, exc_tb = sys.exc_info()
        logger.warning("Generic Exception : %s, %s, @Line : %s", exc_type, exc_obj, exc_tb.tb_lineno)

    time.sleep(2)

