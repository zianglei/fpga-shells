# See LICENSE for license details.

# Optimize the netlist
opt_design -directive Explore

# Checkpoint the current design
write_checkpoint -force [file join $wrkdir post_opt]

read_checkpoint -incremental [file join $wrkdir post_opt.dcp]
