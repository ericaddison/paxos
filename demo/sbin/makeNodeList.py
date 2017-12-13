#!/bin/python
# make a file containing a list of nodes for input to paxos program
# nodes are all of the form "127.0.0.1:PORT", where port = (starting port) + 5i 
#
# inputs: 
#		Nprocs -- number of nodes in the list
#		starting port -- port to start with

import sys
import subprocess

# get number of nodes to create
if len(sys.argv) != 3:
	print("Usage: {0} <Nprocs> <starting port>".format(sys.argv[0]))
	exit(1)

nprocs = int(sys.argv[1])
port0 = int(sys.argv[2])

# create a node list file
with open('./inputs/nodeList.txt', 'w') as f:
	for i in range(nprocs):
		weight = 1.0/nprocs
		dl = "1" if i < 3 else "0"
		dp = "1" if i == 0 else "0"
		avg_delay = 0
		unreliability = 0
		next_line = '127.0.0.1 {0} {1} {2} {3} {4} {5}\n'.format( (port0+i*5), weight, avg_delay, unreliability, dl, dp )
		f.write(next_line)
