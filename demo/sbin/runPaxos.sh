#! /bin/bash
id=$1
file=$2
restart=$3

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/../../paxos/build"

classpath=`cat ${DIR}/resources/main/classpath.txt`

java -classpath ${classpath} paxos.application.PretendApp $id $file $restart
