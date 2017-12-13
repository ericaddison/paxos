#!/bin/python
# restart a single paxos node

import sys
import subprocess
from os.path import isfile, dirname, realpath
import time
import json
mydir = dirname(realpath(__file__))

# check args
if len(sys.argv) != 3:
	print("Usage: {0} <node list> <state file>".format(sys.argv[0]))
	exit(1)

nodefile = sys.argv[1]
statefile = sys.argv[2]

# get id of node to launch
with open(statefile, 'r') as f:
	state = json.loads(f.read())

node_id = state['id']


# launch processes
pids = []
print("Restarting Paxos node {0}".format(node_id))
pid = subprocess.Popen(['{0}/runPaxos.sh'.format(mydir), str(node_id), str(nodefile), str(statefile)]).pid
pids.append(pid)

pidsbase = 'pids_restarted{}_'.format(node_id)
cnt = 0
pidsfile = '{0}{1}.txt'.format(pidsbase, cnt)

while isfile(pidsfile):
	cnt += 1
	pidsfile = '{0}{1}.txt'.format(pidsbase, cnt)

with open(pidsfile, 'w') as f:
	for pid in pids:
		f.write('{0} '.format(pid))
