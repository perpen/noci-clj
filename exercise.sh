#!/bin/bash
set -ex

noci servers login 43880338

t_build_lein() {
    noci t delete build || true
    noci t create build \
        '{"type": "build", "builder": "lein", "git-url": "%s", "branch": "%s"}'

    noci t trigger build --params \
        git-url=git@github.com:perpen/noci.git branch=master

    noci j log --follow
}

t_build_lein_timeout() {
    noci t delete build || true
    noci t create build \
        '{"type": "build", "builder": "lein", "git-url": "%s", "branch": "%s", "timeout-s": "%i"}'

    noci t trigger build --params \
        git-url=git@github.com:perpen/noci.git branch=master timeout-s=5

    noci j log --follow
}

t_build_lein_interrupt() {
    noci t delete build || true
    noci t create build \
        '{"type": "build", "builder": "lein", "git-url": "%s", "branch": "%s"}'

    noci t trigger build --params \
        git-url=git@github.com:perpen/noci.git branch=master
    sleep 2
    noci j action stop

    noci j log --follow
}


t_docker() {
    noci t delete docker || true
    noci t create docker \
        '{"volumes": "m2", "image": "%s", "type": "docker", "timeout-mn": 1, "args": "%a"}'

    noci t trigger docker --params \
        image=hello-world args=

    noci j log --follow
}

j_docker() {
    noci j start \
        '{"volumes": "m2", "image": "hello-world", "type": "docker", "timeout-mn": 1}'

    noci j log --follow
}

eval "$@"
