package fr.spironet.slackbot.github

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * My own event filter class.
 * To be customized later on.
 */
class PmauduitEventFilter implements EventFilter {

    private final static Logger logger = LoggerFactory.getLogger(PmauduitEventFilter.class)

    private def filteredGithubAccount = [
            "sbrunner",
            "nbessi",
            "Camille0907",
            "faselm"
    ]

    @Override
    boolean doFilter(def event) {
        try {
            if (event.actor.login in this.filteredGithubAccount) {
                return true
            }
            if (event.repo.name == "geonetwork/core-geonetwork") {
                return true
            }
            if (event.repo.name.contains("odoo")) {
                return true
            }
            return false
        } catch (Exception e) {
            logger.warn("Don't know whether to filter out the event or not, keeping it.", e)
            return false
        }
    }
}
