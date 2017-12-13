#!/bin/bash

sbin_dir="../../sbin"

# kill any running processes
jps | grep PretendApp | cut -f1 -d' ' | xargs kill

# remove existing pids files
rm -rf ./pids_*

# run launch new nodes
python ${sbin_dir}/launchNPaxos.py tenNodes_plain.txt

