#!/usr/bin/python3

from jproperties import Properties
from aduLoggerConfig import initLogger
import sys
import os

logger = initLogger('getEndpointJson.py')

properties = Properties()
with open("/var/adu/data/adu.properties", "rb") as p_file:
    properties.load(p_file, "utf-8")

namespace = properties.get("namespace").data

timeout_seconds = 10
ep_command = "/usr/local/bin/kubectl get ep -n " + namespace + " -o json"

try:
    logger.info("Printing endpoint json ....")
    ep_output = os.popen(ep_command)
    api_response = ep_output.read()
    file = open("/var/adu/data/endpoint.json", "w")
    file.write(str(api_response))
    file.close

except:
    exc_type, exc_obj, exc_tb = sys.exc_info()
    logger.warning("GetEndpointJson -- Exception : %s, %s, @Line : %s", exc_type, exc_obj, exc_tb.tb_lineno)


