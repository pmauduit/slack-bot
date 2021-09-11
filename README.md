# slack-bot

A slack bot based on [com.ullink.slack:simpleslackapi](https://github.com/Itiviti/simple-slack-api/).

# Code

The bot has 2 main operating modes to interact with:

* listeners: The bot listens and reacts to some predefined commands
* services: The bot does things at a regular pace and/or at a given date

# Listeners

See the package `fr/spironet/slackbot/listeners`. Here is a list of currently implemented listeners:

* `ConfluenceListener`: reacts to `!confluence ...` commands, the purpose is to search for resources (blogs,pages) on Confluence
* `DefaultListener`: just dumps all the events (e.g. messages on channels the bot is onto) to its logs
* `GithubListener`: reacts to `!github ...` commands
* `GrafanaListener`: reacts to `!grafana monitoring` command (just dumps a screenshot of our monitoring screen onto the channel where the command has been issued)
* `JenkinsListener`: unused anymore (we do not use this CI tool anymore), but the purpose was to trigger builds
* `jiraListener`: reacts to `!jira ...` commands
* `KibanaListener`: reacts to `!kibana weather` command (a "Weather report" of the last 7 days activities on our platforms)
* `OdooListener`: reacts to `!odoo ...` commands
* `TempoListener`: reacts to the `!tempo ...` command, which allows me to fill in my timesheet, interacting with the Jira Tempo plugin

# Scheduled services

The code for these services is nested into the `fr/spironet/slackbot/scheduled`.

Depending on the frequency (e.g. "every x period of time" vs "every monday morning at 10:15"), they are implemented with 2 different libraries:

* Google guava for regular executed services (e.g. "every 5 minutes")
* Quartz Scheduler for these needing more precise scheduling (e.g. "every monday morning at ...")

Here are the currently implemented services:

* `ConfluenceRssScheduledService`: polls the RSS endpoint from Confluence and warns me if some new events are arriving
* `GithubScheduledService`: polls the Github api endpoint for events, for my user and warns me about the new coming ones
* `JiraRssScheduledService`: Basically the same as the first one, targeted onto the JIRA endpoints
* `KibanaJob`: Visits our Kibana dashboard for the last 7 days and dumps a screenshot of it on a configured channel ("weather report of our infrastructure") every monday morning
* `TerraformScheduledService`: Polls the S3 bucket containing some of our terraform states, and warns if a new version is detected, trying to analyze the change and giginv some hints on the changed resources.


