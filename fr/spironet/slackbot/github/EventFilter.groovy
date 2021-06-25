package fr.spironet.slackbot.github

interface EventFilter {
    /**
     * Decides whether the event should be filtered or not.
     * @param the event to filter
     * @return false if the event is accepted / has to be kept, true if it has to be filtered.
     */
    public boolean doFilter(def event)
}
