#!/usr/bin/python3

from aduLoggerConfig import initLogger
from jproperties import Properties
import time
import sys
import os

logger = initLogger('getImagePullTime.py')

properties = Properties()
with open("/var/adu/data/adu.properties", "rb") as p_file:
    properties.load(p_file, "utf-8")

namespace = properties.get("namespace").data
logger.info("Using Namespace: %s", namespace)

image_properties = Properties()
with open("/var/adu/data/image.properties", "rb") as p_file:
    image_properties.load(p_file, "utf-8")

listOfSgImages = image_properties.get("sg.image.list").data.split(";")
logger.info("listOfSgImages : %s", listOfSgImages)

listOfAppImages = image_properties.get("app.image.list").data.split(";")
logger.info("listOfAppImages : %s", listOfAppImages)

fmt = "%Y%m%d%H%M%S"
get_image_start_pull_command = "/usr/local/bin/kubectl get events -n " + namespace + " -o custom-columns=FirstSeen:.firstTimestamp,Message:.message " \
                            "--field-selector involvedObject.kind=Pod,involvedObject.name={pod} | grep \"Pulling image\" | grep \"{image}:\""
get_image_stop_pull_command = "/usr/local/bin/kubectl get events -n " + namespace + " -o custom-columns=FirstSeen:.firstTimestamp,Message:.message " \
                            "--field-selector involvedObject.kind=Pod,involvedObject.name={pod} | grep \"Successfully pulled image\" | grep \"{image}:\""


def upgrade_running():
    return True


def get_pull_time(list_of_images):
    try:
        for sg_list in list_of_images:
            list = sg_list.split(":")
            pod_init = list[0]
            sg_images = list[1].split(",")
            get_pod_command = "/usr/local/bin/kubectl get pods --no-headers -o custom-columns=\":metadata.name\" -n " + namespace + " | grep " + pod_init + "-"
            get_status = os.popen(get_pod_command)
            status_read = get_status.read().rstrip()
            pods = status_read.split("\n")
            for image in sg_images:
                for pod in pods:
                    if pod:
                        try:
                            command_pull_start = get_image_start_pull_command.format(pod=pod, image=image)
                            command_pull_stop = get_image_stop_pull_command.format(pod=pod, image=image)
                            event_exec_start = os.popen(command_pull_start)
                            event_read_start = event_exec_start.read().rstrip()
                            if not event_read_start:
                                continue
                            event_exec_stop = os.popen(command_pull_stop)
                            event_read_stop = event_exec_stop.read().rstrip()
                            if not event_read_stop:
                                continue
                            image_output_start = event_read_start.split(" ")
                            image_output_stop = event_read_stop.split(" ")
                            start_pull_time = image_output_start[0]
                            stop_pull_time = image_output_stop[0]
                            total_pull_time = image_output_stop[-1]
                            if total_pull_time:
                                with open("/var/adu/data/image.properties", "rb") as p_file:
                                    image_properties.load(p_file, "utf-8")
                                image_value = image_properties.get(image).data
                                put_str = pod + "," + start_pull_time + "," + stop_pull_time + "," + total_pull_time
                                if put_str in image_value:
                                    pass
                                else:
                                    logger.info("pods: %s", pods)
                                    logger.info("event_read_start : %s", event_read_start)
                                    logger.info("event_read_stop : %s", event_read_stop)
                                    logger.info("get image pull start command : %s", command_pull_start)
                                    logger.info("get image pull stop command : %s", command_pull_stop)
                                    logger.info("put_str : %s", put_str)
                                    if image_value:
                                        image_properties[image] = image_value + ";" + put_str
                                    else:
                                        image_properties[image] = put_str
                                    with open("/var/adu/data/image.properties", "wb") as w_file:
                                        image_properties.store(w_file, "utf-8")
                        except:
                            ex_type, ex_obj, ex_tb = sys.exc_info()
                            logger.warning("Image-Pull Exception : %s, %s, @Line : %s", ex_type, ex_obj, ex_tb.tb_lineno)
    except:
        exc_type, exc_obj, exc_tb = sys.exc_info()
        logger.warning("Image-Pull-Time Exception : %s, %s, @Line : %s", exc_type, exc_obj, exc_tb.tb_lineno)


while upgrade_running():
    get_pull_time(listOfSgImages)
    get_pull_time(listOfAppImages)
    time.sleep(60)

