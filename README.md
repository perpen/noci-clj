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
  feature? Possibility of continous run on save? But why build locally?
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
$ noci servers register https://somewhere:123 uat
# Store auth token for instance
# The server will return the ttl, which may be 0 for uat and 1h for prd
$ noci servers login uat [--ttl-mn=15]
$ noci servers logout uat
# Sets the default instance
$ noci servers select uat
```

Jobs API
========
```
$ noci jobs start '{"type": "build", "builder": "lein", "git-url": "http://stash", "branch": "master"}'
$ noci job list --ongoing --filter name=tooling*
$ noci job log <id>
$ noci job log --follow <id>
$ noci job action <id> [--params ...]
```

Triggers API
============
```
# Create trigger to be called like POST /api/trigger/utilities?branch=master
# Or POST /api/trigger/generic?type=build&git-url=http://stash&branch=master
$ noci triggers create build '{"type": "build", "builder": "lein", "git-url": "%s", "branch": "%s"}'
       --realms=<realm>,...
$ noci triggers trigger build --params git-url=git@stash.hk.barbapapa/project.git branch=master
$ noci triggers list
$ noci triggers trigger <name>
$ noci triggers delete <name>
```
