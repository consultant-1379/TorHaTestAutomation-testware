#!/bin/bash
id=$(ps -ef | grep "jmsTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "postgresTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "visinamingnbTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "eshistoryTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "openidmTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "haproxyTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "elasticsearchTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfspmlinksTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfssmrsTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfsdataTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfspm1Times.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfspm2Times.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfsmdtTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "modelsTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfsbatchTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfsddcdataTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfscustomTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfsamosTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfshcdumpsTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfshomeTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfsnorollbackTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
id=$(ps -ef | grep "nfsconfigmgtTimes.txt" | grep  "consul watch -type=service" | awk '{print $2}'); kill -9 $id; \
sleep 1; \
echo