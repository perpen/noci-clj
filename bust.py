#!/usr/bin/env python

import sys
import os
import json
import argparse
import requests
import time
import pprint
from colorama import Fore, Style, init


def fatal(msg):
    sys.stderr.write(msg + "\n")
    sys.exit(1)


def c(style, s):
    # If needs to be customisable, store in ~/.bust.json?
    styles = {
        'key': [Style.BRIGHT],
        'status': [Style.RESET_ALL],
        'time': [Fore.GREEN],
        'user': [Fore.CYAN],
        'actions': [Fore.RED, Style.BRIGHT],
        'default': [Style.RESET_ALL],
        'stdout': [Fore.BLUE],
        'stderr': [Fore.RED],
    }
    return "".join(styles[style]) + s + Style.RESET_ALL


def rag_symbol(s):
    return {
        'red': '💥',
        'green': ' ✔',
        'blue': '🏃',
        'amber': '🔥',
    }.get(s, s)


class Config:
    def __init__(self):
        self.path = '%s/.bust.json' % os.environ['HOME']
        self.data = None
        try:
            with open(self.path, 'r') as f:
                self.data = json.load(f)
        except FileNotFoundError:
            self.data = {'instances': {}}

    def get_instance(self, name):
        d = self.data
        return d['instances'].get(name)

    def get_instances(self):
        d = self.data
        return d['instances']

    def get_instance_url(self, name):
        return self.data['instances'][name]['url']

    def set_default_instance(self, name):
        self.data['default_instance'] = name

    def get_default_instance(self):
        return self.data.get('default_instance')

    def set_last_job(self, job):
        self.data['last_job'] = job

    def get_last_job(self):
        return self.data.get('last_job')

    def set_last_trigger(self, trigger):
        self.data['last_trigger'] = trigger

    def clear_last_trigger(self):
        del self.data['last_trigger']

    def get_last_trigger(self):
        return self.data.get('last_trigger')

    def add_instance(self, url, name):
        self.data['instances'][name] = {'url': url}

    def remove_instance(self, name):
        del self.data['instances'][name]

    def get_instance_token(self, name):
        return self.data['instances'][name].get('token')

    def set_instance_token(self, name, token):
        self.data['instances'][name]['token'] = token

    def save(self):
        with open(self.path, 'w') as f:
            json.dump(self.data, f)


class Api:
    def __init__(self, config):
        self.config = config

    def _job_repr(self, job, instance, full=False):
        actions_s = ''
        if job['actions']:
            actions_s = '<- ' + c('actions', ', '.join(job['actions']))
        params_s = ''
        if full:
            params_s = '\n' + pprint.pformat(job['params'])
        return ''.join([
            rag_symbol(job['rag']),
            ' ',
            c('key', job['key']),
            ' ',
            c('status', job['status-message']),
            ' ',
            actions_s,
            params_s,
        ])

    def _get(self, instance, path, params={}):
        url = "%s/api%s" % (self.config.get_instance_url(instance), path)
        resp = requests.get(
            url, params=params, headers=self._headers(instance))
        self._die_on_error_status(resp)
        return resp.json()

    def _post(self, instance, path, payload, query_params={}):
        url = "%s/api%s" % (self.config.get_instance_url(instance), path)
        resp = requests.post(
            url,
            json=payload,
            params=query_params,
            headers=self._headers(instance))
        self._die_on_error_status(resp)
        return resp.json()

    def _put(self, instance, path, payload):
        url = "%s/api%s" % (self.config.get_instance_url(instance), path)
        resp = requests.put(url, json=payload, headers=self._headers(instance))
        self._die_on_error_status(resp)
        return resp.json()

    def _delete(self, instance, path):
        url = "%s/api%s" % (self.config.get_instance_url(instance), path)
        resp = requests.delete(url, headers=self._headers(instance))
        self._die_on_error_status(resp)

    def _headers(self, instance):
        return {
            'auth-token': self.config.get_instance_token(instance),
            'Accept': 'application/json',
            'Content-type': 'application/json',
        }

    def _die_on_error_status(self, resp):
        if resp.status_code >= 400:
            msg = {
                401: "Unauthorised",
                404: "Unknown entity",
                409: "Conflict",
                422: "Invalid",
            }.get(resp.status_code, "Oops")
            print("Error: " + msg + " - " + resp.text, file=sys.stderr)
            sys.exit(1)


class ServersApi(Api):
    def list(self, args):
        for name, instance in self.config.get_instances().items():
            print(name, instance['url'])

    def register(self, args):
        print('Registering %s as "%s"' % (args.url, args.name))
        self.config.add_instance(args.url, args.name)
        if args.make_default:
            print('Setting default instance to %s' % args.name)
            self.config.set_default_instance(args.name)

    def unregister(self, args):
        print('Unregistering "%s"' % args.name)
        self.config.remove_instance(args.name)

    def login(self, args):
        print('login to %s as %s' % (args.instance, args.username))
        password = "pa55word"
        payload = {'username': args.username, 'password': password}
        auth = self._post(args.instance, "/auth", payload)
        self.config.set_instance_token(args.instance, auth['token'])

    def select(self, args):
        print('Setting default instance to %s' % args.instance)
        self.config.set_default_instance(args.instance)


class JobsApi(Api):
    def list(self, args):
        print('Listing jobs on instance %s, full=%s' % (args.instance,
                                                        args.full))
        jobs = self._get(args.instance, "/jobs", params={"limit": args.limit})
        for job in jobs:
            print(self._job_repr(job, args.instance, full=args.full))

    def status(self, args):
        job = self._get(args.instance, "/jobs/%s" % args.job, {'full': True})
        print(self._job_repr(job, args.instance, full=True))

    def log(self, args):
        print('Showing log for job %s on instance %s, follow=%s, start=%s' %
              (args.job, args.instance, args.follow, args.start))
        self.config.set_last_job(args.job)
        start = args.start or 0
        while True:
            job = self._get(args.instance, "/jobs/%s" % args.job,
                            {'start': start})
            # print(job)
            for line in job['log']:
                username = line['user'] or "self    "
                t1 = time.strptime(line['time'], "%Y-%m-%dT%H:%M:%SZ")
                t2 = time.strftime('%H:%M:%S', t1)
                style = line['style-hint'] or 'default'
                print(
                    c('time', t2), c('user', username),
                    c(style, line['message']))
            if not args.follow or job.get('dead'):
                break
            start += len(job['log'])
            time.sleep(1)
        print(self._job_repr(job, args.instance))

    def action(self, args):
        print(('Performing action %s for job %s on instance %s, ' +
               'value=%s, comment=%s') % (args.action, args.job, args.instance,
                                          args.value, args.comment))
        path = "/jobs/%s/actions/%s" % (args.job, args.action)
        payload = {"value": args.value, "comment": args.comment}
        job = self._put(args.instance, path, payload)
        print(self._job_repr(job, args.instance))

    def start(self, args):
        payload_parsed = json.loads(args.payload)
        print(payload_parsed)
        self._start(args.instance, payload_parsed)

    def _start(self, instance, payload_parsed):
        job = self._post(instance, "/jobs", payload_parsed)
        print(self._job_repr(job, instance))
        job_key = job['key']
        self.config.set_last_job(job_key)


class TriggersApi(Api):
    def info(self, args):
        trigger = self._get(args.instance, "/triggers/%s" % args.name)
        print(self._trigger_description(args.instance, args.name, trigger))

    def list(self, args):
        print('Listing triggers on instance %s' % (args.instance))
        triggers = self._get(args.instance, "/triggers")
        for name, trigger in triggers.items():
            print(
                self._trigger_description(
                    args.instance, name, trigger, full=False))

    def create(self, args):
        name = args.name
        try:
            payload_parsed = json.loads(args.payload)
        except json.decoder.JSONDecodeError:
            print("payload json not valid: %s" % args.payload)
            sys.exit(1)
        trigger = self._post(args.instance, "/triggers/%s" % name,
                             payload_parsed)
        print(self._trigger_description(args.instance, trigger, name))
        self.config.set_last_trigger(name)

    def delete(self, args):
        self._delete(args.instance, "/triggers/%s" % args.name)
        self.config.clear_last_trigger()

    def trigger(self, args):
        query_params = {}
        if args.params:
            for param in args.params:
                k, v = param.split("=")
                query_params[k] = v
        job = self._post(
            args.instance,
            "/triggers/%s/job" % args.name, {},
            query_params=query_params)
        print(self._job_repr(job, args.instance))
        job_key = job['key']
        self.config.set_last_job(job_key)

    def _trigger_description(self, instance, name, trigger, full=False):
        return "%s\t%s" % (name, trigger)


class SecretsApi(Api):
    def status(self, args):
        resp = self._get(args.instance, "/unseal")
        if resp['status']:
            print("unsealed")
        else:
            print("sealed")

    def unseal(self, args):
        resp = self._post(args.instance, "/unseal", {"secret": args.secret})
        if resp['status']:
            print("unsealed")
        else:
            print("unsealing failed")


class CommandLineParser:
    def __init__(self, config):
        self.default_instance = config.get_default_instance()
        self.last_job = config.get_last_job()
        self.last_trigger = config.get_last_trigger()
        self.servers_api = ServersApi(config)
        self.jobs_api = JobsApi(config)
        self.triggers_api = TriggersApi(config)
        self.secrets_api = SecretsApi(config)

    def _usage(self):
        s = 'Usage: %s {servers,jobs,triggers} ... [--help,-h]' % sys.argv[0]
        print(s, file=sys.stderr)
        sys.exit(2)

    def parse(self, argv):
        prog = argv[0]

        # servers ####################

        servers_parser = argparse.ArgumentParser(prog=prog + ' servers')
        servers_subparsers = servers_parser.add_subparsers()

        # "servers list"
        parser_servers_list = servers_subparsers.add_parser('list')
        parser_servers_list.set_defaults(func=self.servers_api.list)

        # "servers register"
        parser_servers_register = servers_subparsers.add_parser('register')
        parser_servers_register.add_argument(
            '--make-default', action='store_true')
        parser_servers_register.add_argument('url')
        parser_servers_register.add_argument('name')
        parser_servers_register.set_defaults(func=self.servers_api.register)

        # "servers unregister"
        parser_servers_unregister = servers_subparsers.add_parser('unregister')
        parser_servers_unregister.add_argument('name')
        parser_servers_unregister.set_defaults(
            func=self.servers_api.unregister)

        # "servers login"
        parser_servers_login = servers_subparsers.add_parser('login')
        parser_servers_login.add_argument('username')
        parser_servers_login.add_argument(
            '--instance', default=self.default_instance)
        parser_servers_login.set_defaults(func=self.servers_api.login)

        # "servers select"
        parser_servers_select = servers_subparsers.add_parser('select')
        parser_servers_select.add_argument('instance')
        parser_servers_select.set_defaults(func=self.servers_api.select)

        # jobs ####################

        jobs_parser = argparse.ArgumentParser(prog=prog + ' jobs')
        jobs_subparsers = jobs_parser.add_subparsers()

        # "jobs list"
        parser_jobs_list = jobs_subparsers.add_parser('list')
        parser_jobs_list.add_argument('--full', action='store_true')
        parser_jobs_list.add_argument(
            '--instance', default=self.default_instance)
        parser_jobs_list.add_argument('--limit', default=15)
        parser_jobs_list.set_defaults(func=self.jobs_api.list)

        # "jobs status"
        parser_jobs_status = jobs_subparsers.add_parser('status')
        parser_jobs_status.add_argument(
            '--instance', default=self.default_instance)
        parser_jobs_status.add_argument(
            'job', nargs='?', default=self.last_job)
        parser_jobs_status.set_defaults(func=self.jobs_api.status)

        # "jobs log"
        parser_jobs_log = jobs_subparsers.add_parser('log')
        parser_jobs_log.add_argument('--start', type=int)
        parser_jobs_log.add_argument('--follow', action='store_true')
        parser_jobs_log.add_argument(
            '--instance', default=self.default_instance)
        parser_jobs_log.add_argument('job', nargs='?', default=self.last_job)
        parser_jobs_log.set_defaults(func=self.jobs_api.log)

        # "jobs start"
        parser_jobs_start = jobs_subparsers.add_parser('start')
        parser_jobs_start.add_argument(
            '--instance', default=self.default_instance)
        parser_jobs_start.add_argument('payload')
        parser_jobs_start.set_defaults(func=self.jobs_api.start)

        # "jobs action"
        parser_jobs_action = jobs_subparsers.add_parser('action')
        parser_jobs_action.add_argument(
            '--instance', default=self.default_instance)
        parser_jobs_action.add_argument(
            'job', nargs='?', default=self.last_job)
        parser_jobs_action.add_argument('action')
        parser_jobs_action.add_argument('--value')
        parser_jobs_action.add_argument('--comment')
        parser_jobs_action.set_defaults(func=self.jobs_api.action)

        # triggers ####################

        triggers_parser = argparse.ArgumentParser(prog=prog + ' triggers')
        triggers_subparsers = triggers_parser.add_subparsers()

        # "triggers list"
        parser_triggers_list = triggers_subparsers.add_parser('list')
        parser_triggers_list.add_argument('--ongoing', action='store_true')
        parser_triggers_list.add_argument(
            '--instance', default=self.default_instance)
        parser_triggers_list.add_argument('--filter')
        parser_triggers_list.set_defaults(func=self.triggers_api.list)

        # "triggers info"
        parser_triggers_info = triggers_subparsers.add_parser('info')
        parser_triggers_info.add_argument(
            '--instance', default=self.default_instance)
        parser_triggers_info.add_argument(
            'name', nargs='?', default=self.last_trigger)
        parser_triggers_info.set_defaults(func=self.triggers_api.info)

        # "triggers create"
        parser_triggers_create = triggers_subparsers.add_parser('create')
        parser_triggers_create.add_argument(
            '--instance', default=self.default_instance)
        parser_triggers_create.add_argument('name')
        parser_triggers_create.add_argument('payload')
        parser_triggers_create.set_defaults(func=self.triggers_api.create)

        # "triggers delete"
        parser_triggers_info = triggers_subparsers.add_parser('delete')
        parser_triggers_info.add_argument(
            '--instance', default=self.default_instance)
        parser_triggers_info.add_argument(
            'name', nargs='?', default=self.last_trigger)
        parser_triggers_info.set_defaults(func=self.triggers_api.delete)

        # "triggers trigger"
        parser_triggers_info = triggers_subparsers.add_parser('trigger')
        parser_triggers_info.add_argument(
            '--instance', default=self.default_instance)
        parser_triggers_info.add_argument('--params', nargs='*')
        parser_triggers_info.add_argument(
            'name', nargs='?', default=self.last_trigger)
        parser_triggers_info.set_defaults(func=self.triggers_api.trigger)

        # unseal ####################

        secrets_parser = argparse.ArgumentParser(prog=prog + ' secrets')
        secrets_subparsers = secrets_parser.add_subparsers()

        # "secrets status"
        parser_secrets_info = secrets_subparsers.add_parser('status')
        parser_secrets_info.add_argument(
            '--instance', default=self.default_instance)
        parser_secrets_info.set_defaults(func=self.secrets_api.status)

        # "secrets unseal"
        parser_secrets_create = secrets_subparsers.add_parser('unseal')
        parser_secrets_create.add_argument(
            '--instance', default=self.default_instance)
        parser_secrets_create.add_argument('secret')
        parser_secrets_create.set_defaults(func=self.secrets_api.unseal)

        # Invoke relevant parser
        if len(sys.argv) == 1:
            self._usage()
        argv.pop(0)
        parser = {
            'j': jobs_parser,
            'jobs': jobs_parser,
            'servers': servers_parser,
            's': servers_parser,
            'triggers': triggers_parser,
            't': triggers_parser,
            'secrets': secrets_parser,
        }.get(sys.argv[0])
        if not parser:
            self._usage()

        if len(argv) == 1:
            argv.append("-h")

        argv.pop(0)
        args = parser.parse_args(argv)
        args.func(args)


init()  # colorama
config = Config()
try:
    CommandLineParser(config).parse(sys.argv)
except KeyboardInterrupt:
    print("\nInterrupted")
    sys.exit(1)
except requests.exceptions.ConnectionError as e:
    print("Cannot connect:", e)
    sys.exit(1)
config.save()
