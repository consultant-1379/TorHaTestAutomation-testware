#!/bin/bash
initPath=$1
rm $initPath/aduTimes/ -R; \
rm -rf $initPath/ERICTAFtorhatestautomation_CXP9030905*; \
mkdir $initPath/aduTimes; \
cd $initPath/aduTimes; \
nohup consul watch -type=service -service=jms "consul watch -type=service -service=jms | egrep '\"Status\": ' >> jmsTimes.txt; $initPath/data/date.sh >>jmsTimes.txt" & \
nohup consul watch -type=service -service=postgres "consul watch -type=service -service=postgres | egrep '\"Status\": ' >> postgresTimes.txt; $initPath/data/date.sh >> postgresTimes.txt" & \
nohup consul watch -type=service -service=visinamingnb "consul watch -type=service -service=visinamingnb | egrep '\"Status\": ' >> visinamingnbTimes.txt; $initPath/data/date.sh >> visinamingnbTimes.txt" & \
nohup consul watch -type=service -service=eshistory "consul watch -type=service -service=eshistory | egrep '\"Status\": ' >> eshistoryTimes.txt; $initPath/data/date.sh >> eshistoryTimes.txt" & \
nohup consul watch -type=service -service=openidm "consul watch -type=service -service=openidm | egrep '\"Status\": ' >> openidmTimes.txt; $initPath/data/date.sh >> openidmTimes.txt" & \
nohup consul watch -type=service -service=haproxy "consul watch -type=service -service=haproxy | egrep '\"Status\": ' >> haproxyTimes.txt; $initPath/data/date.sh >> haproxyTimes.txt" & \
nohup consul watch -type=service -service=elasticsearch "consul watch -type=service -service=elasticsearch | egrep '\"Status\": ' >> elasticsearchTimes.txt; $initPath/data/date.sh >> elasticsearchTimes.txt" & \
nohup consul watch -type=service -service=nfspmlinks "consul watch -type=service -service=nfspmlinks| egrep '\"Status\": ' >> nfspmlinksTimes.txt; $initPath/data/date.sh >> nfspmlinksTimes.txt" & \
nohup consul watch -type=service -service=nfssmrs "consul watch -type=service -service=nfssmrs | egrep '\"Status\": ' >> nfssmrsTimes.txt; $initPath/data/date.sh >> nfssmrsTimes.txt" & \
nohup consul watch -type=service -service=nfsdata "consul watch -type=service -service=nfsdata | egrep '\"Status\": ' >> nfsdataTimes.txt; $initPath/data/date.sh >> nfsdataTimes.txt" & \
nohup consul watch -type=service -service=nfspm1 "consul watch -type=service -service=nfspm1 | egrep '\"Status\": ' >> nfspm1Times.txt; $initPath/data/date.sh >> nfspm1Times.txt" & \
nohup consul watch -type=service -service=nfspm2 "consul watch -type=service -service=nfspm2 | egrep '\"Status\": ' >> nfspm2Times.txt; $initPath/data/date.sh >> nfspm2Times.txt" & \
nohup consul watch -type=service -service=nfsmdt "consul watch -type=service -service=nfsmdt | egrep '\"Status\": ' >> nfsmdtTimes.txt; $initPath/data/date.sh >> nfsmdtTimes.txt" & \
nohup consul watch -type=service -service=models "consul watch -type=service -service=models | egrep '\"Status\": ' >> modelsTimes.txt; $initPath/data/date.sh >> modelsTimes.txt" & \
nohup consul watch -type=service -service=nfsbatch "consul watch -type=service -service=nfsbatch | egrep '\"Status\": ' >> nfsbatchTimes.txt; $initPath/data/date.sh >> nfsbatchTimes.txt" & \
nohup consul watch -type=service -service=nfsddcdata "consul watch -type=service -service=nfsddcdata | egrep '\"Status\": ' >> nfsddcdataTimes.txt; $initPath/data/date.sh >> nfsddcdataTimes.txt" & \
nohup consul watch -type=service -service=nfscustom "consul watch -type=service -service=nfscustom | egrep '\"Status\": ' >> nfscustomTimes.txt; $initPath/data/date.sh >> nfscustomTimes.txt" & \
nohup consul watch -type=service -service=nfsamos "consul watch -type=service -service=nfsamos | egrep '\"Status\": ' >> nfsamosTimes.txt; $initPath/data/date.sh >> nfsamosTimes.txt" & \
nohup consul watch -type=service -service=nfshcdumps "consul watch -type=service -service=nfshcdumps | egrep '\"Status\": ' >> nfshcdumpsTimes.txt; $initPath/data/date.sh >> nfshcdumpsTimes.txt" & \
nohup consul watch -type=service -service=nfshome "consul watch -type=service -service=nfshome | egrep '\"Status\": ' >> nfshomeTimes.txt; $initPath/data/date.sh >> nfshomeTimes.txt" & \
nohup consul watch -type=service -service=nfsnorollback "consul watch -type=service -service=nfsnorollback | egrep '\"Status\": ' >> nfsnorollbackTimes.txt; $initPath/data/date.sh >>nfsnorollbackTimes.txt" & \
nohup consul watch -type=service -service=nfsconfigmgt "consul watch -type=service -service=nfsconfigmgt | egrep '\"Status\": ' >> nfsconfigmgtTimes.txt; $initPath/data/date.sh >>nfsconfigmgtTimes.txt" & \
sleep 1; \
echo