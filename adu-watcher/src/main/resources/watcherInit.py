#!/usr/bin/python3
import os
import sys
import time
import subprocess
from datetime import datetime
from jproperties import Properties
from aduLoggerConfig import initLogger

logger = initLogger('watcherInit.py')

properties = Properties()
fmt = "%Y%m%d%H%M%S"


# This script will start executing during pod init.
# If the Upgrade is already running, this script will check the status and auto start upgradeStatus/watcher scripts.
def check_script_status(script_list):
    for script in script_list:
        status_command = "ps -ef | grep " + script + " | grep -v grep | wc -l"
        run_output = os.popen(status_command)
        run_status = run_output.read().rstrip()
        if int(run_status) == 0:
            logger.warning("Script : %s Run status:  %s", script, run_status)
            if "aduWatcher" == script:
                start_command = "/usr/bin/curl -k -X POST https://localhost:8443/watcher/adu/script/start 2>/dev/null"
                stop_command = "/usr/bin/curl -k -X POST https://localhost:8443/watcher/adu/script/stop 2>/dev/null"
            elif "upgradeStatus" == script:
                start_command = "/usr/bin/curl -k -X POST https://localhost:8443/watcher/adu/script/upgrade/start 2>/dev/null"
                stop_command = "/usr/bin/curl -k -X POST https://localhost:8443/watcher/adu/script/upgrade/stop 2>/dev/null"
            else:
                continue

            start_output = os.popen(start_command)
            start_status = start_output.read()
            logger.warning("Script : %s Start status:  %s", script, start_status)

            if start_status == "false":
                os.popen(stop_command)
                time.sleep(1)
                start_output = os.popen(start_command)
                start_status = start_output.read()
                logger.warning("Script : %s Start status:  %s", script, start_status)
        else:
            continue


while True:
    try:
        with open("/var/adu/data/adu.properties", "rb") as p_file:
            properties.load(p_file, "utf-8")
        upgrade_type = properties.get("upgrade.type").data

        # If upgrade type is ccd, do not start upgradeStatus script.
        if upgrade_type == "ccd":
            scripts = ["aduWatcher"]
            ccd_file = open('/var/adu/data/ccd_status', 'r')
            ccd_status = ccd_file.read().rstrip()
            if "running" in ccd_status:
                print_command = "echo \"{date},running\" > /var/adu/data/upgrade_status".format(date=datetime.now().strftime(fmt))
            else:
                print_command = "echo \"{date},stopped\" > /var/adu/data/upgrade_status".format(date=datetime.now().strftime(fmt))
            subprocess.call(print_command, stderr=subprocess.STDOUT, shell=True)
        else:
            scripts = ["upgradeStatus", "aduWatcher"]

        ug_file = open('/var/adu/data/upgrade_status', 'r')
        ug_status = ug_file.read().rstrip()
        if "running" in ug_status:
            check_script_status(scripts)
    except IOError:
        #Regression won't do anything here.
        pass
    except:
        exc_type, exc_obj, exc_tb = sys.exc_info()
        logger.warning("Init-watcher Exception : %s, %s, @Line : %s", exc_type, exc_obj, exc_tb.tb_lineno)

    time.sleep(2)
