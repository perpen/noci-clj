#!/bin/bash
echo $(date) - RUNNING $0 $*
echo "TO STDERR" 1>&2

# trap "echo killed; exit 11" SIGINT SIGTERM

trap_with_arg() {
    func="$1" ; shift
    for sig ; do
        trap "$func $sig" "$sig"
    done
}

func_trap() {
    echo Trapped: $1 | tee oo-signal
    exit 11
}

# trap_with_arg func_trap INT TERM

for i in $(seq 3); do
    echo $i
    sleep 3
done
exit 0
