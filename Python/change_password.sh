#!/usr/bin/expect

set vmid [lindex $argv 0]
set username [lindex $argv 1]
set password [lindex $argv 2]

set timeout 10

spawn qm guest passwd $vmid $username
expect "New password:"
send "$password\r"
expect "Retype new password:"
send "$password\r"
interact