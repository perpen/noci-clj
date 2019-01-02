#!/bin/bash
set -ex

bust servers login 43880338

t_build_lein() {
    bust t delete build || true
    bust t create build \
        '{"type": "build", "builder": "lein", "git-url": "%s", "branch": "%s"}'

    bust t trigger build --params \
        git-url=git@bitbucket.org:flowgir/noci.git branch=master

    bust j log --follow
}

t_build_lein_interrupt() {
    bust t delete build || true
    bust t create build \
        '{"type": "build", "builder": "lein", "git-url": "%s", "branch": "%s"}'

    bust t trigger build --params \
        git-url=git@bitbucket.org:flowgir/noci.git branch=master
    sleep 2
    bust j action stop

    bust j log --follow
}


t_docker() {
    bust t delete docker || true
    bust t create docker \
        '{"volumes": "m2", "image": "%s", "type": "docker", "timeout-mn": 1, "args": "%a"}'

    bust t trigger docker --params \
        image=hello-world args=
    bust j action stop

    bust j log --follow
}

eval "$@"
