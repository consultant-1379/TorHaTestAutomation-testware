#!/usr/bin/python3

from aduLoggerConfig import initLogger
from jproperties import Properties
from datetime import datetime
import subprocess
import time
import sys

logger = initLogger('ldapWatcher.py')

properties = Properties()
with open("/var/adu/data/adu.properties", "rb") as p_file:
    properties.load(p_file, "utf-8")

namespace = properties.get("namespace").data
logger.info("Using Namespace: %s", namespace)

ldap_user = properties.get("ldap.users").data
ldap_pass = properties.get("ldap.pass").data
base_dn = properties.get("ldap.base.dn").data
ldap_ipv4 = properties.get("ldap.ipv4").data
ldap_port = properties.get("ldap.port").data

logger.info("Ldap Credentials..")
logger.info("ldap_user : %s", ldap_user)
logger.info("ldap_pass : %s", ldap_pass)
logger.info("ldap_ipv4 : %s", ldap_ipv4)
logger.info("ldap_port : %s", ldap_port)


fmt = "%Y%m%d%H%M%S"
ldap_command = "env LDAPTLS_REQCERT=never /usr/bin/ldapsearch -x -D uid={user_id},ou=People,{base_dn} -w {password} " \
               "-H ldaps://{ipv4}:{port} -b {base_dn} \"(&(objectClass=*)(uid=Admin*))\" dn uid"


def upgrade_running():
    return True


def get_ldap_status():
    try:
        get_ldap_command = ldap_command.format(user_id=ldap_user, base_dn=base_dn, password=ldap_pass, ipv4=ldap_ipv4, port=ldap_port)
        try:
            get_status = subprocess.check_output(get_ldap_command, stderr=subprocess.STDOUT, shell=True)
            if "Success" in  str(get_status, 'utf-8'):
                print_command = "echo \"{time}, uid: {user}, ldap status: success\" >> /var/adu/data/com-aa-ldap.csv".format(time=datetime.now().strftime(fmt), user=ldap_user)
            else:
                logger.info("ldap_command: %s", get_ldap_command)
                logger.error("uid: %s, ldap error: %s", ldap_user, get_status)
                print_command = "echo \"{time}, uid: {user}, ldap status: failed\" >> /var/adu/data/com-aa-ldap.csv".format(time=datetime.now().strftime(fmt), user=ldap_user)
        except subprocess.CalledProcessError as c:
            logger.info("ldap_command: %s", get_ldap_command)
            logger.error("uid: %s, ldap error: %s", ldap_user, c.output)
            print_command = "echo \"{time}, uid: {user}, ldap status: failed\" >> /var/adu/data/com-aa-ldap.csv".format(time=datetime.now().strftime(fmt), user=ldap_user)

        subprocess.call(print_command, stderr=subprocess.STDOUT, shell=True)

    except Exception as e:
        exc_type, exc_obj, exc_tb = sys.exc_info()
        logger.error("Exception in ldap-test : %s, @Line: %s", e, exc_tb.tb_lineno)


while upgrade_running():
    if ldap_user and ldap_pass and base_dn and ldap_ipv4 and ldap_port:
        get_ldap_status()
    time.sleep(1)

