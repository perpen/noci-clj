FIXME
=====
- Read vault yaml from resources.

Ideas
=====
- Write a trivial shell script for login, create trigger, trigger, start job.
  This to show how easy it is to invoke.
- Create a build-bundle job, to tighten the specific job.
- Use the timeout option for all jobs. Use conch/proc :timeout.
- Having to use a local copy of noci to build (b/c not build script) may be a
  feature? Possibility of continous run on save?
- RAG should be blue when process is running, or orange when pending approvals.

Why async approval?
- Allows external approval
- Allows reuse of CR??
Cons:
- Code complexity?

Servers API
===========
```
# Simply adds noci instance to our local config
$ bust servers register https://somewhere:123 uat
# Store auth token for instance
# The server will return the ttl, which may be 0 for uat and 1h for prd
$ bust servers login uat [--ttl-mn=15]
$ bust servers logout uat
# Sets the default instance
$ bust servers select uat
```

Jobs API
========
```
$ bust jobs start '{"type": "build", "builder": "lein", "git-url": "http://stash", "branch": "master"}'
$ bust job list --ongoing --filter name=tooling*
$ bust job log <id>
$ bust job log --follow <id>
$ bust job action <id> [--params ...]
```

Triggers API
============
```
# Create trigger to be called like POST /api/trigger/utilities?branch=master
# Or POST /api/trigger/generic?type=build&git-url=http://stash&branch=master
$ bust triggers create build '{"type": "build", "builder": "lein", "git-url": "%s", "branch": "%s"}'
       --realms=<realm>,...
$ bust triggers trigger build --params git-url=git@stash.hk.barbapapa/project.git branch=master
$ bust triggers list
$ bust triggers trigger <name>
$ bust triggers delete <name>
```
