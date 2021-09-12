package fr.spironet.slackbot.jira
/**
 * A simple container which basically:
 * - receives some json-to-groovy objets from the APIs
 * - pre-calculates some fields for easier re-use
 *
 * This class is more of a data / container class, all the
 * calculation / fetching external resource is made by the
 * IssueDetailsResolver class.
 *
 */
class IssueDetails {
    IssueDetails(def key) {
        this.issueKey = key
    }
    def issueKey

    /** internal numeric identifier.
     *
     * Generally the issue key can be used instead, but some
     * extra JIRA plugins expect this identifier instead. this
     * is the case for the dev-status plugin (which manages the
     * github <-> JIRA link).
     */
    def issueId

    /** The issue author (reporter)
     */
    def issueAuthor

    /** The issue assignee
     */
    def assignee

    /**
     * The issue labels (e.g. tags)
     */
    def labels

    /**
     * The possible labels for the issue.
     * This field can be computed once the issue description and its comments are available.
     * @See IssueDetailsResolver.computePossibleLabels()
     */
    def possibleLabels

    /**
     * The processed worklogs. We are interested in having the infos parsed to be able to produce
     * something like:
     *
     * "- <user> worked on the issue from <date> to <date> for a total time of <time>"
     * @See IssueDetailsResolver.analyzeWorklogs(def issue)
     */
    def worklogsPerUser = [:]
    /**
     * The issue description
     */
    def description

    /** from https://jira/rest/api/2/issue/<issueKey>
     * @See IssueDetailsResolver.loadIssue( )
     */
    def rawIssueInfo

    /** from https://jira/rest/api/2/issue/<issueKey>/worklog
     * @See IssueDetailsResolver.loadIssueWorklog( )
     */
    def worklogs

    /** from https://jira/rest/api/2/issue/<issueKey>/watchers
     * @See IssueDetailsResolver.loadIssueWatchers( )
     */
    def watchers

    /** from https://jira/rest/api/2/issue/<issueKey>/comment
     * @See IssueDetailsResolver.loadIssueComments( )
     */
    def comments

    /** from https://jira/rest/api/2/issue/<issueKey>/remotelink
     * @See IssueDetailsResolver.loadIssueRemoteLinks( )
     */
    def remoteLinks

    /** from /rest/dev-status/1.0/issue/detail?issueId=<issue id>&applicationType=github&dataType=pullrequest
     * @See IssueDetailsResolver.loadGithubPullRequests( )
     */
    def githubPullRequests

    /** from /rest/dev-status/1.0/issue/detail?issueId=<issue id>&applicationType=github&dataType=repository
     * @See IssueDetailsResolver.loadGithubActivity( )
     */
    def githubActivity

    /**
     * The related organization the issue is about.
     *
     * For support projects (GEO-*), it can
     * be guessed studying the appropriate custom field (customfield_10900).
     * For more classic projects, the info is stored elsewhere.
     */
    def organization

    /**
     * Related confluence pages (found using the same labels as the issue's organization)
     */
    def confluencePages

    /**
     * The related github repositories.
     *
     * There are 2 main strategies to guess them:
     *
     * - studying the githubPullRequests / githubActivity variables
     * - searching for labels on github using the organization field
     *
     */
    def githubRepositories

    /**
     * This is used to calculate the organization the issue refers to
     * (@See #organization).
     *
     * To set up this list, you first need to list every JIRA projects:
     * (@See IssueDetailsResolver.loadProjects()).
     *
     * Then, check the 220+ projects for suffixes that can be removed.
     *
     * if you have several suffixes which begins with the same character sequence,
     * put the longest ones before (e.g. "_georchestra2020" vs "_georchestra").
     *
     * You can test the calculation using the following groovy code:
     * <code>
     *   def meaninglessSuffixes = [ ...]
     *   def allPrjs = [ ... ]
     *   def orgs = allPrjs.collect {
     *     sanitizedPrjName = it
     *     meaninglessSuffixes.each { sfx ->
     *         sanitizedPrjName -= sfx
     *     }
     *     return sanitizedPrjName
     *   }
     *   orgs.sort(false).unique().each {
     *        println "\"${it}\","
     *   }
     * </code>
     * And make sure the printed lines suits.
     */
    static def meaninglessSuffixes = [
            "_24",
            " - Archived",
            "_assistance",
            "_code_review",
            "_evolutions2019",
            "_form052019_gdr",
            "_form062019_ggs",
            "_form092019_ggs",
            "_form102019_gms",
            "_form092019_gqgs",
            "_form112019_gdr",
            "_form122019_qgs",
            "_form102020_ggeor",
            "_geoadmin",
            "_geocat",
            "_geocommunes",
            "_geomaintenance",
            "_geonetwork",
            "_geoportal",
            "_georchestra2019",
            "_georchestra2021",
            "_georchestra",
            "_geoserver",
            "_geosupport",
            " Geosupport",
            " GeoMapFish",
            "_gmf",
            "_gmf_24",
            "_gn3",
            "_madd",
            "_mapproxy",
            "_migrationgmf2 -2",
            "_MFP",
            "_ngm",
            "_odoo",
            "_openlayers",
            "_openlayer",
            "_prestation",
            " Projects",
            "_postgresql",
            "_programmation_odoo",
            "_qgispoc",
            "_routing",
            "_sextant2019",
            "_subs_odoo",
            "_subs",
            "_thinkhazard2020",
            "_verkehrsnetz",
            "_webmap"
    ]

    /**
     * Array of known used labels.
     *
     * There is unfortunately no way in the JIRA API to get a complete list of the label being
     * used to tag the issues.
     *
     * Instead, we can use the search endpoint along with the following JQL:
     * "labels is not EMPTY"
     *
     * Note: there are currently 13,848 issues with labels on them in the C2C JIRA instance. The
     * search endpoint only allows to retrieve 1k documents max at a time.
     * @see IssueDetailsResolver.loadLabels()
     *
     * Note2: some people at Camptocamp seem to use "meaningless" labels, like "serveur" ?! So a rapid
     * review & cleanup when building the list may be necessary.
     * Below is a list of useless tags, you can try to calculate on some of your issue, to be able
     * to build a list which will suit your needs.
     * ["bug", "change", "check", "custom", customize", "data", project name as label, "commercial",
     *  "github" ...]
     */
    static def knownLabels = [
            "AngularJS","CKAN","Docker",
            "ELK","FME","GeoTools","GetFeature","GetFeatureInfo","IE","IE11",
            "MapCache","NTW","OpenLayers3","PDF","Production","Puppet","Pydio","QGIS","RDS","REST","SLD",
            "SSL_certificates","SSO","SWW","TinyOWS","Training","WCS","WFS","WFS-T",
            "WMS","WMTS","account","acl","actions","activedirectory","addon","adict","admin","ag_oereb",
            "alembic","apache","apache-nas","attributes","authentication","aws",
            "backend","backgroundlayer","backups","basel-landschaft","border","browser","build","c2cgeoform",
            "c2cwsgiutils","cache","cadastre","capabilities","cas","cassandra","cgxp","chrome","cms",
            "compatibility","console","contextualdata","cors","crosshair","css","csw",
            "database","datagrid","debug","disclaimer",
            "displayquerygrid","docker-compose","documentation","draw","drupal",
            "epsg:2056","exoscale","export","extracteur","filter",
            "filterselector","firefox","firewall","floor","frontend","fts","geOrchestra","geofence","geoinfo_oereb",
            "geometrycollection","geonetwork","geoserver","geowebcache","git","github","glarus_oereb",
            "gmf-installer","gmf2","gmf2.5","gmfUser","gold","google","googlemap","grids","harvest",
            "i18n","iOS","ie11","imposm","infrastructure","interface","interlis","internal-reporter",
            "intranet","invalid_referer","isogeo","jasperstudio","kafka","kibana","label","layertree",
            "ldap","legend","less","let's_encrypt","lidar","loadbalancing","locale","logging","login",
            "logrotation","lopocs","maintenance","mako","mapfile","mapfish-print","mapnik","mapserver","measure",
            "memcached","memory","metadata","migration","mn95","mobile","moissonnage",
            "multiple_databases","mviewer","navigator","network","nextcloud","ngeo","nodejs","oauth2","oereb",
            "oereblex","ogc-proxy","ogcproxy","ogr","openjdk","openlayers2","openshift","openstreetmap","overviewmap",
            "parking","partition","password","pdfreport","performance","permalink","php","pingdom","pip","plugin",
            "postgis","postgres", "postgresql","print","profile","protected-layer","proxy","pydio","pyramid_oereb","python",
            "querier","rancher-nfs","raster","redhat","referer","registre_foncier","rennes-support","reset_password",
            "resourceproxy","restore","restricted","review","role","routing","s3","scalebar",
            "script","search","secure","security","security-proxy","securité","server","settings",
            "sftp","shorturl","silver","smtp","snappable","snapping","sos","sql","sqlalchemy","ssl","statistic",
            "streetview","substitution","support-admin","svg","symbol","talend","terraform",
            "themes","tile","tileGeneration","tilecloud","timeout","timeslider","timezone","tls","tolerance","tomcat",
            "tooltip","topics","touch_enabled_device","traefik","traitement","transifex","translations","transparency",
            "travis","trd","tsearch","update","upgrade","user","values","venv","vhost","vrt","vrt-bot","waiting",
            "webdav","webpack","wfs","win7","wms-browser","wms-t","wmts","xapi"
    ]
}
