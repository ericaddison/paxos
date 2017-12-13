#!/bin/python

# kill the paxos nodes from the given processes
import subprocess
import sys
import os
import signal

if len(sys.argv) < 2:
	print("Usage: {0} <pids>".format(sys.argv[0]))
	print("\tExample: {0} 12345 12348 12360".format(sys.argv[0]))
	exit(1)

for pid in sys.argv[1:]:
	print("Killing pid {0} and all child pids".format(pid))
	p = subprocess.Popen(['ps', '-o', 'pid', '--ppid', str(pid)], stdout=subprocess.PIPE)
	child_pids = p.stdout.read().split('\n')[1:-1]
	for child_pid in child_pids:
		try:
			os.kill(int(child_pid), signal.SIGTERM)
		except OSError:
			pass
	try:
		os.kill(int(pid), signal.SIGTERM)
	except OSError:
		pass
	
