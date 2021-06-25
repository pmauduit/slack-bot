package fr.spironet.slackbot.github

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * My own event filter class.
 * To be customized later on.
 */
class PmauduitEventFilter implements EventFilter {

    private final static Logger logger = LoggerFactory.getLogger(PmauduitEventFilter.class)

    @Override
    boolean doFilter(Object event) {
        try {
            if (event.actor.login == "sbrunner") {
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
            logger.error("Don't know whether to filter out the event or not, keeping it.", e)
            return false
        }
    }

    public static void main(String[] args) {
        def filterCls = Class.forName("fr.spironet.slackbot.github.PmauduitEventFilter")
        def filter = filterCls.newInstance()
        def result = filter.doFilter([actor: [login: "sbrunner"]])
        assert result == true
        result = filter.doFilter([:])
        assert result == false
    }
}
