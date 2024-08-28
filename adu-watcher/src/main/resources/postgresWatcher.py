#!/usr/bin/python3

from aduLoggerConfig import initLogger
from datetime import datetime
import subprocess
import time
import sys
import os

logger = initLogger('postgresWatcher.py')
fmt = "%Y%m%d%H%M%S"
namespace = sys.argv[1]

# Read pg-pass
pwd = os.popen("/usr/local/bin/kubectl -it exec postgres-0 -n {ns} -- cat /tmp/uscrt/super-pwd".format(ns=namespace)).read()
logger.info("postgres password : " + pwd)

scripting_pod = "general-scripting-0"


# check if 'pg_isready' already available.
def is_pg_client(vm_name):
    try:
        pg_cmd = "/usr/local/bin/kubectl exec -i {pod} -c general-scripting -n {ns} -- sh -c 'which pg_isready'".format(pod=vm_name, ns=namespace)
        out_put = os.system(pg_cmd)
        if out_put == 0:
            logger.info("pg_found")
            return True
        else:
            logger.info("pg_not_found")
            return False
    except:
        exc_type, exc_obj, exc_tb = sys.exc_info()
        logger.warning("Exception in is_pg_client() : %s, %s, @Line : %s", exc_type, exc_obj, exc_tb.tb_lineno)


def install_pg_client(name):
    if not is_pg_client(name):
        logger.info("Installing pg-client on pod : {pod}".format(pod=name))
        install_cmd = "/usr/local/bin/kubectl exec -i {pod} -c general-scripting -n {ns} -- sh -c 'zypper install -y postgresql15'".format(pod=name,ns=namespace)
        op = os.popen(install_cmd)
        stat = op.read()
        logger.info("PG install status: " + stat)


def get_available_scripting_pod(current_pod):
    logger.info("current pod : {pod}".format(pod=current_pod))
    ep_status = subprocess.check_output("/usr/local/bin/kubectl get endpoints {pod} -n {ns}  -o jsonpath={{.subsets[*].addresses[*].ip}}".format(pod=current_pod, ns=namespace), stderr=subprocess.STDOUT, shell=True)
    ep_status = str(ep_status, 'utf-8')
    if ep_status.strip():
        logger.info("using current pod : {pod}".format(pod=current_pod))
        return current_pod
    else:
        another_pod = subprocess.check_output("/usr/local/bin/kubectl get pod -n {ns} | grep general-scripting | grep -v credm | grep -v {pod} | awk '{{print $1}}'".format(ns=namespace, pod=current_pod), stderr=subprocess.STDOUT, shell=True)
        another_pod = str(another_pod.rstrip(), 'utf-8')
        logger.info("another pod : {pod}".format(pod=another_pod))
        ep_status = subprocess.check_output("/usr/local/bin/kubectl get endpoints {pod} -n {ns} -o jsonpath={{.subsets[*].addresses[*].ip}}".format(pod=another_pod, ns=namespace), stderr=subprocess.STDOUT, shell=True)
        ep_status = str(ep_status.strip(), 'utf-8')
        if ep_status:
            logger.info("using another pod : {pod}".format(pod=another_pod))
            install_pg_client(another_pod)
            return another_pod
        else:
            logger.info("using current pod : {pod}".format(pod=current_pod))
            return current_pod


def upgrade_running():
    return True


# configure pg on scripting vm
install_pg_client('general-scripting-0')
install_pg_client('general-scripting-1')

while upgrade_running():
    try:
        online = False
        command = "/usr/local/bin/kubectl exec -i {pod} -c general-scripting -n {ns} -- sh -c '/usr/bin/pg_isready -h postgres -t 2'".format(pod=scripting_pod, ns=namespace)
        output = os.popen(command)
        status = output.read()
        if "accepting" in status:
            recovery_check = "/usr/local/bin/kubectl exec -i {pod} -c general-scripting -n {ns} -- sh -c \"export PGPASSWORD={pwd};psql -h postgres -U postgres -c 'select pg_is_in_recovery();'\"".format(pod=scripting_pod, ns=namespace, pwd=pwd)
            recovery_output = os.popen(recovery_check)
            recovery_status = recovery_output.read()
            if " f" in recovery_status:
                online = True
            else:
                new_pod = get_available_scripting_pod(scripting_pod)
                if scripting_pod != new_pod:
                    scripting_pod = new_pod
                    continue
                else:
                    logger.info("postgres is in recovery, status: {stat}".format(stat=recovery_status))
                    online = False
        else:
            new_pod = get_available_scripting_pod(scripting_pod)
            if scripting_pod != new_pod:
                scripting_pod = new_pod
                continue
            else:
                logger.warning("postgres error: " + status)

        if online:
            printCmd = "echo \"{time}, ONLINE\" >> /var/adu/data/postgres.csv".format(time=datetime.now().strftime(fmt))
            subprocess.call(printCmd, stderr=subprocess.STDOUT, shell=True)
        else:
            printCmd = "echo \"{time}, OFFLINE\" >> /var/adu/data/postgres.csv".format(time=datetime.now().strftime(fmt))
            subprocess.call(printCmd, stderr=subprocess.STDOUT, shell=True)
    except:
        exc_type, exc_obj, exc_tb = sys.exc_info()
        logger.warning("Generic Exception : %s, %s, @Line : %s", exc_type, exc_obj, exc_tb.tb_lineno)
    time.sleep(1)



