#!/bin/bash

sbin_dir="../../sbin"

# kill any running PretendApp processes
jps | grep PretendApp | cut -f1 -d' ' | xargs kill
sleep 0.5

# remove existing pids files
rm -rf ./pids_*

# make sure directories exist
mkdir -p logs
mkdir -p states

# run launch new nodes
python ${sbin_dir}/launchNPaxos.py tenNodes_unreliable_diffweights_twoDP.txt

