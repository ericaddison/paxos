#!/bin/python
# launch a collection of paxos apps programatically

import sys
import subprocess
from os.path import isfile, dirname, realpath
import time
mydir = dirname(realpath(__file__))

# get number of nodes to create
if len(sys.argv) != 2:
	print("Usage: {0} <node list>".format(sys.argv[0]))
	exit(1)

nodefile = sys.argv[1]

# count lines in the file
with open(nodefile, 'r') as f:
	lines = f.readlines()
	nprocs = len(lines)

# launch processes



pids = []
for node_id in range(nprocs):
	print("Launching Paxos app {0}".format(node_id))
	pid = subprocess.Popen(['{0}/runPaxos.sh'.format(mydir), str(node_id), str(nodefile)]).pid
	pids.append(pid)
	time.sleep(0.5)

pidsbase = 'pids_'
cnt = 0
pidsfile = '{0}{1}.txt'.format(pidsbase, cnt)

while isfile(pidsfile):
	cnt += 1
	pidsfile = '{0}{1}.txt'.format(pidsbase, cnt)

with open(pidsfile, 'w') as f:
	for pid in pids:
		f.write('{0} '.format(pid))
