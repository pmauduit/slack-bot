package fr.spironet.slackbot.terraform

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ListVersionsRequest
import com.google.common.collect.Maps
import groovy.json.JsonSlurper

class TerraformState {

    def s3
    def bucket

    /**
     * Constructor.
     *
     * In my team, our terraform states are stored onto a s3 bucket.
     *
     * See https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html to
     *  configure your AWS profile. this requires to have a ~/.aws/credentials file, and
     *  set the `AWS_PROFILE` accordingly.
     */
    def TerraformState(def s3client, def bucket) {
        s3 = s3client
        /**
         * I might need several different s3 clients to be able to spy on
         * different buckets with different credentials ...
         * And I am also interested in managing the way the credentials are
         * passed by myself. This can be done this way:
         *
         * ```
         *  def credentials = new BasicAWSCredentials("accessKey", "secretKey")
         *  def credsProvider = new StaticCredentialsProvider(credentials)
         *  s3 = AmazonS3ClientBuilder.standard().withCredentials(credsProvider)
         *        .withRegion(Regions.EU_WEST_1)
         *        .build()
         * ```
         */
        this.bucket = bucket
    }

    /**
     * Loads a state from S3 in memory.
     *
     * @param key the object key (identifier) on the bucket.
     * @param version the version in which the object has to be retrieved (optional).
     * if not provided, the latest version of the object is retrieved.
     *
     * @return The state as a groovy object.
     */
    def getState(def key, def version = null) {
        def o
        if (version == null) {
            o = s3.getObject(this.bucket, key)
        } else {
            def or = new GetObjectRequest(this.bucket, key, version)
            o = s3.getObject(or)
        }

        def s3is = o.getObjectContent()
        def os = new ByteArrayOutputStream()
        byte[] read_buf = new byte[1024]
        int read_len = 0;
        while ((read_len = s3is.read(read_buf)) > 0) {
            os.write(read_buf, 0, read_len)
        }
        s3is.close()
        os.close()
        return new JsonSlurper().parseText(new String(os.toByteArray()))
    }

    /**
     * List object versions.
     *
     * @param key the key (object identifier) on the bucket.
     * @return an array of map containing an id to identify the version, and the
     * underlying modification date.
     */
    def listObjectVersions(def key) {
        def req = new ListVersionsRequest().withBucketName(this.bucket)
                .withPrefix(key)
                // limits to the last 3 versions (n & n-1 should be the same version anyway)
                //.withMaxResults(3)
        def result
        def ret = []

        result = s3.listVersions(req)
        // lastest is the same as the first previous one, considering keeping 2 before
        result.versionSummaries.collect {
           [ id: it.versionId, date: it.lastModified ]
        }
    }

    /**
     * This method aims to "flatten" the terraform structures in the state,
     * to make the comparison of the resources simpler to parse with Guava.
     *
     * @param a hashmap of resources indexed by name.
     */
    def normalizeState(def state) {
        def ret = [:]
        state.modules.collect { mod ->
            mod.resources.collect { k,v ->
                v.remove("primary").each { v[it.key] = it.value }
                v.remove("attributes").each { v[it.key] = it.value }
                ret[k] = v
            }
        }
        ret
    }

    /**
     * Compares two terraform states. The order of the arguments
     * is important, as the result is likely to be passed to the
     * prettyPrint() method above, which expects the left value
     * to be the less recent one.
     *
     * @param state1 the less recent state.
     * @param state2 the most recent state.
     *
     * @return a map indexed by the resource name, having the difference as value.
     */
    def compareStates(def state1, def state2) {
        def diffs = [:]

        try {
            /**
             * In which cases can we have more than one module ?
             * Let's begin with considering that we only have one module.
             * Reading:
             * https://www.terraform.io/docs/language/modules/syntax.html
             * I don't think we are using them.
             *
             * For now, we will take the resources of each modules as a
             * single map.
             */
            def resourcesState1 = normalizeState(state1)
            def resourcesState2 = normalizeState(state2)

            resourcesState1.each { k, v ->
                def resState2 = resourcesState2[k] ?: [:]
                def diff = Maps.difference(v, resState2)
                if (! diff.areEqual()) {
                    diffs[k] = diff
                }
            }
            /**
             * Also compares the resources that are only present in resourcesState2 with an empty map
             */
            def onlyInState2 = resourcesState2.keySet() - resourcesState1.keySet()
            onlyInState2.each {
                diffs[it] = Maps.difference([:], resourcesState2[it])
            }
            return diffs
        } catch (Exception e) {
            throw new TerraformStateComparisonException("Error occured when trying to compare states", e)
        }
    }

    /**
     * Pretty prints a diff calculated by Guava Maps.difference().
     * @param diffs
     * @return a string describing the modifications from the state.
     */
    def prettyPrintDiff(def diffs) {
        def ret = ""
        diffs.each { k, v ->
            if (v.onlyOnLeft.size() > 0) {
                ret += "• Resource ${k} removed\n"
            } else if (v.onlyOnRight.size() > 0) {
                ret += "• Resource ${k} added\n"
            } else {
                ret += "• Resource ${k} modified\n"
            }
        }
        ret
    }
}
