#!/bin/bash
initPath=$6
if [ "$3" = "cloud" ]; then
    Path="$initPath/UG"
else
    Path="$initPath/UG"
fi

Ip=$1
CsvFile=$2
Host_version=$5

while [ -f $Path ]
do
    echo `date +%Y%m%d%H%M%S` | tr '\n' ','>>$CsvFile
    echo $Ip | tr '\n' ',' >>$CsvFile
    if  [[ ($Host_version == 2) ]] ; then
#        if/then branch
        /usr/bin/curl -s --user neo4j:Neo4jadmin123 "http://$Ip:7474/db/manage/server/causalclustering/writable" >>$CsvFile
    elif [[ ($Host_version == 0) || ($Host_version == 1) ]]; then
#        else branch
        /usr/bin/curl -s --user neo4j:Neo4jadmin123 "http://$Ip:7474/db/dps/cluster/writable" >>$CsvFile
    fi
    status=$?
    if [ $status -ne 0 ]; then
        echo $status >>$CsvFile
        echo $status
    else
        printf '%s\n'  >>$CsvFile
    fi
    sleep 2
done