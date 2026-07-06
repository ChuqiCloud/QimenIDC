#!/bin/bash

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <vmid> <vnc_ticket>"
  exit 1
fi

vmid="$1"
password="$2"

export LC_PVE_TICKET="$password"

exec qm vncproxy "$vmid"
