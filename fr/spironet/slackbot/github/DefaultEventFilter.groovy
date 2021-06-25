package fr.spironet.slackbot.github

class DefaultEventFilter implements EventFilter {
    @Override
    boolean doFilter(Object event) {
        return false
    }
}
