#!/usr/bin/env bash

set -e

sysctl kern.ipc.somaxconn=12000
sysctl net.inet.tcp.msl=1000
sysctl net.inet.ip.portrange.first=32768
