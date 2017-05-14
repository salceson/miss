#!/usr/bin/env python
# coding: utf-8

import subprocess
import sys
from copy import copy

if __name__ == '__main__':
    argv = copy(sys.argv)
    argv[0] = './aws.py'
    for i in xrange(10):
        subprocess.call(argv)
        with open('supervisor.out') as f:
            print f.read()
