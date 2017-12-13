#! /bin/bash
id=$1
file=$2
restart=$3

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/.."

dist_classpath=${DIR}/dist/lib
lib_classpath=${DIR}/libs
java -classpath $dist_classpath/Paxos.jar:$lib_classpath/gson-2.6.2.jar:. paxos.application.PretendApp $id $file $restart
