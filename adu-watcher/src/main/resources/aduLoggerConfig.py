#!/usr/bin/python3

from datetime import datetime
import logging

def initLogger(name):
     # create logger for 'adu_watcher_application'
    logger = logging.getLogger(name)
    logger.setLevel(logging.DEBUG)
    fh = logging.FileHandler(filename='/var/adu/data/HighAvailability_{today}.log'.format(today = datetime.now().strftime("%Y-%m-%d")),mode='a', encoding='utf-8', delay=False)
    fh.setLevel(logging.DEBUG)
    formatter = logging.Formatter('%(asctime)s - %(name)s - %(levelname)s - %(message)s')
    fh.setFormatter(formatter)
    logger.addHandler(fh)
    return logger