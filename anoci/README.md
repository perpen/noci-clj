Make a basic version of the yaml - then add critical stuff only.

Use cases - as functions?
- mvn/lein/npm build
- compilers (git, python, etc)
- puppet job. Could it work by running docker remotely via other jobs?
- docker image build
- ansible-deployer
- helm deploy

Difference between CI job and a function or script?
- Runs in controlled version and environment
- Runs for short duration
- Runs remotely [req, b/c of secrets]
- Runs asynchronously
  Required, can be long or require lots resources]
  Not always?
- Status, logs, history are visible to whole team
- Has access to specific resources (eg lots ram, docker)
- Has access to specific secrets
- Triggered by events, not humans
- Triggered by POST or other events
- Typically works from an scm and artifacts repo
- For all of the above, which are traditional vs real features?

Difference between anoci and TC/jenkins?
- not centered on ui
 
Difference between anoci and buildrunner?
- buildrunner centered around traditional build jobs, anoci is about functions

Extreme idea
- Each job is a service like any other
- Service discovery required?

POST/GET/etc may return 301

https://bundle-builder.hsbc/
https://noci.hsbc/bundle-builder
curl -F app=jira -F version=1.2 /bundle-builder/
GET /bundle-builder/ -> list of builds
GET /bundle-builder/1 -> summary
GET /bundle-builder/1/log?start=-10
DELETE /bundle-builder/1 -> stops build

https://noci.hsbc/jira-closer/
curl -F app=jira -F version=1.2 URL
GET /jira-closer/ -> list of builds
GET /jira-closer/1 -> summary
GET /jira-closer/1/log?start=-10
DELETE /jira-closer/1 -> stops build

Difference between job service and a standard service?
- Used by devs, not business

Requirements
- easy/fast deployment

Misc
- How to factor out the common stuff?
  - By having 1 single service :(
- Manage jobs releases by having 1 yaml per job? But then need another config to
  point to all the yamls.
- With bundle-builder, delegating to each bundle's build.sh implies duplication?
- How to decide between working directly from git branch or commit, or compiled
  artifact?
  - Criticality, ie how likely we'd have to run w/o stash?
  - In one case dependent on stash, in other on nexus. What difference?
  - Choice between configuring job against master branch, or specific commit.
  - If building lib (eg jar, npm, docker image) then we make artifact.
- Framework responsibilities:
  - Common API for starting, log, etc
  - Alerting
- How to check been run in uat before prd? For bundles, and for jobs.
- prd vs uat
- docker images can only be pushed to hosted repo if pass the nexus iq test
- should be runnable locally, for testing both noci and new jobs

MISSING
- Integration with JIRA/Stash. How difficult to implement?
