#!/bin/bash
initPath=$6
log="$initPath/Neo4j-DT/StartNeo4j.log"
date=$(eval "date '+%Y-%m-%d %H:%M:%S'")
echo "$date, environment is : $3" >>$log;
if [ "$3" = "cloud" ]; then
    Path="$initPath/UG"
    Cmd="/usr/bin/touch"
    if [ "$4" = "W" ]; then
        ScrPath=$initPath/data/Neo4j-DT/Eng-Neo4jDT-0.sh
    else
        ScrPath=$initPath/data/Neo4j-DT/Eng-Neo4jDT-1.sh
    fi
else
    Path="$initPath/UG"
    Cmd="/bin/touch"
    if [ "$4" = "W" ]; then
        ScrPath=$initPath/data/Neo4j-DT/Eng-Neo4jDT-0.sh
    else
        ScrPath=$initPath/data/Neo4j-DT/Eng-Neo4jDT-1.sh
    fi
fi
if [ -f  $Path ]; then
    echo "$date, UG File present" >>$log
else
    echo "$date, creating UG file" >>$log
    $(eval "$Cmd $Path")
fi

CsvFile=$2
psCmd="ps -ef | grep $ScrPath"
echo "$date, going to execute ps command : [$psCmd]" >>$log
output=$(eval "$psCmd")
echo "$date, ps command output : " >>$log
echo "$output" >>$log

if [[ "$output" == *"$CsvFile"* ]]; then
    echo "$date, $ScrPath is already running with file name : $CsvFile" >>$log
else
    nohup $ScrPath $1 $2 $3 $4 $5 $6 &
    echo "$date, starting $ScrPath with file name : $CsvFile" >>$log
fi