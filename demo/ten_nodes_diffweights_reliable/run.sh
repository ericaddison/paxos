#!/bin/bash

sbin_dir="../../sbin"

# kill any running PretendApp processes
jps | grep PretendApp | cut -f1 -d' ' | xargs kill

# remove existing pids files
rm -rf ./pids_*

# make sure directories exist
mkdir -p logs
mkdir -p states

# run launch new nodes
python ${sbin_dir}/launchNPaxos.py tenNodes_reliable_diffweights.txt

