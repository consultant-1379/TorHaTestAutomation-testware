#!/usr/bin/python3

import os
import sys
import time
import subprocess
from datetime import datetime
from jproperties import Properties
from aduLoggerConfig import initLogger

logger = initLogger('upgradeStatus.py')
properties = Properties()
with open("/var/adu/data/adu.properties", "rb") as p_file:
    properties.load(p_file, "utf-8")

namespace = properties.get("namespace").data
logger.info("Using Namespace: %s", namespace)

upgrade_map = {}
upgrade_status = ""
upgrade_started = False
stateless_started = False
format = "%Y%m%d%H%M%S"
grace_run_count = 0

integration_deployments = properties.get("charts").data.split(";")
logger.info("Chart list to Upgrade: %s", integration_deployments)


def check_status():
    global upgrade_map
    global upgrade_status
    global upgrade_started
    global grace_run_count
    for key, value in list(upgrade_map.items()):
        new_value = value.rstrip()
        if new_value == "deployed":
            if not upgrade_started:
                upgrade_status = "waiting"
            # wait for 6 minutes to check the chart state change during upgrade.
            elif grace_run_count <= 360:
                grace_run_count += 1
                upgrade_status = "running"
            else:
                # if stateless chart is not updated upgrade will not be marked as finished.
                if stateless_started:
                    upgrade_status = "finished"
                elif "stateless" in key:
                    print_status = "echo \"{date}#  Stateless Chart not upgraded yet.:  {status}\" >> " \
                                   "/var/adu/data/upgrade_status.log".format(date=datetime.now(),
                                                                             status=stateless_started)
                    subprocess.call(print_status, stderr=subprocess.STDOUT, shell=True)
                    grace_run_count = 0
            continue
        elif new_value == "failed":
            upgrade_status = "failed"
            break
        elif new_value == "unknown":
            upgrade_status = "unknown"
            break
        else:
            upgrade_status = "running"
            grace_run_count = 0
            if not upgrade_started:
                upgrade_started = True
            break

    print_status = "echo \"{date}#  Upgrade status:  {status}\" >> /var/adu/data/upgrade_status.log".format(date=datetime.now(), status=upgrade_status)
    subprocess.call(print_status, stderr=subprocess.STDOUT, shell=True)

    print_status = "echo \"{date},{status}\" > /var/adu/data/upgrade_status".format(date=datetime.now().strftime(format), status=upgrade_status)
    subprocess.call(print_status, stderr=subprocess.STDOUT, shell=True)

    print_status = "echo \"{date}#  {status}\" >> /var/adu/data/upgrade_status.log".format(date=datetime.now(), status=upgrade_map)
    subprocess.call(print_status, stderr=subprocess.STDOUT, shell=True)


while True:
    try:
        if not namespace:
            raise TypeError("Namespace is empty!")
        for deployment in integration_deployments:
            name = deployment + "-" + namespace.rstrip()
            status_command = "/usr/local/bin/helm status " + name + " -n " + namespace.rstrip() + " | grep STATUS"
            stream_status = os.popen(status_command)
            helm_status = stream_status.read()
            upgrade_map[name] = helm_status.split(": ")[1]
            # Check if stateless chart upgrade started.
            if "stateless" in name and not stateless_started and not "deployed" in helm_status:
                stateless_started = True
                print_status = "echo \"{date}#  Stateless Chart: {name}, status: {status}\" >> " \
                               "/var/adu/data/upgrade_status.log".format(date=datetime.now(), name=name,
                                                                         status=helm_status)
                subprocess.call(print_status, stderr=subprocess.STDOUT, shell=True)
        check_status()
    except:
        e = sys.exc_info()
        exc_type, exc_obj, exc_tb = sys.exc_info()
        print_exc = "echo \"{date}#  Exception - {e}, @Line: {line}\" >> /var/adu/data/upgrade_status.log".format(date=datetime.now(), e=e, line=exc_tb.tb_lineno)
        subprocess.call(print_exc, stderr=subprocess.STDOUT, shell=True)

    time.sleep(2)
