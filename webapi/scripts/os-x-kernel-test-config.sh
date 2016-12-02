#!/usr/bin/env bash

set -e

sysctl -w kern.ipc.somaxconn=12000
sysctl -w net.inet.tcp.msl=1000
sysctl -w net.inet.ip.portrange.first=32768
