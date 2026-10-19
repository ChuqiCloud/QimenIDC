#!/usr/bin/expect

set vmid [lindex $argv 0]
set username [lindex $argv 1]
set password [lindex $argv 2]

set timeout 7
log_user 0

spawn qm guest passwd $vmid $username
expect {
    "New password:" { send -- "$password\r" }
    timeout { exit 1 }
    eof { exit 1 }
}
expect {
    "Retype new password:" { send -- "$password\r" }
    timeout { exit 1 }
    eof { exit 1 }
}
expect {
    eof {}
    timeout { exit 1 }
}
set result [wait]
if {[lindex $result 2] != 0} { exit 1 }
exit [lindex $result 3]