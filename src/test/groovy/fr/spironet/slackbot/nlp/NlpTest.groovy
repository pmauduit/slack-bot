package fr.spironet.slackbot.nlp


import edu.stanford.nlp.ling.CoreAnnotations
import edu.stanford.nlp.pipeline.Annotation
import edu.stanford.nlp.pipeline.StanfordCoreNLP
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations
import edu.stanford.nlp.simple.Sentence
import fr.spironet.slackbot.jira.IssueDetailsResolver
import org.junit.Test

import static org.junit.Assert.assertTrue

class NlpTest {


    @Test
    void testNlpSentenceAnalysis() {
        Sentence sentence = new Sentence("@georchestracicd book me a meeting at 12:30 for 1 hour to 'treat the JIRA issue GEO-4724'")

        def tagsToLemma = []
        sentence.nerTags().eachWithIndex {
            it, idx -> tagsToLemma << [nerTag: it, lemma: sentence.lemma(idx)]
        }

        /**
         * nerTags[0] = "HANDLE", lemmas[0] = "@georchestracicd"
         * nerTags[6] = "TIME", lemmas[6] = "12:30"
         * nerTags[8|9] = "DURATION", lemmas[8..9] = ["1", "hour"]
         */

        sentence = new Sentence("@georchestracicd book me a meeting next monday at 23:30 for 15 minutes to speak about the weather with my neighbor")
        tagsToLemma = []
        sentence.nerTags().eachWithIndex {
            it, idx -> tagsToLemma << [nerTag: it, lemma: sentence.lemma(idx)]
        }
        println tagsToLemma
        /**
         *
         */

        sentence = new Sentence("@georchestracicd book me a meeting on 2021-07-08 at 16:30 for 3 hours to deal with a very long meeting on GEO-4724")
        tagsToLemma = []
        sentence.nerTags().eachWithIndex {
            it, idx -> tagsToLemma << [nerTag: it, lemma: sentence.lemma(idx)]
        }
        println tagsToLemma

        sentence = new Sentence("@georchestracicd create a gcal event tomorrow at 12h30 to work on ABC-456")
        tagsToLemma = []
        sentence.nerTags().eachWithIndex {
            it, idx -> tagsToLemma << [nerTag: it, lemma: sentence.lemma(idx)]
        }
        println tagsToLemma
        /**
         * nerTags[5] = "DATE", sentence.lemma(5) = "tomorrow"
         * nerTags[7] = "NUMBER, sentence.lemma(7) = "12h30"
         * sentence.lemma()[8:-1]
         */


    }

    @Test
    void testNlpSentimentAnalysisOnAJiraIssue() {
        def ISSUE_KEY = "GEO-4741"
        Properties props = new Properties()
        props.setProperty("annotators", "tokenize, ssplit, pos, parse, sentiment")
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props)

        /** Requires to set several env variables to be able to interact with Jira / confluence, see env.dist at the root of the repo */
        def issueReslv = new IssueDetailsResolver()
        def issue = issueReslv.resolve(ISSUE_KEY)
        def corpus = issue.description + "\n" + issue.comments.comments.collect { it.body }.join("\n")

        Annotation annotation = pipeline.process(corpus)

        def sentencesAnnotationKey = annotation.keySet().find {it.getName() == CoreAnnotations.SentencesAnnotation.getName() }
        def sentencesAnnotation = annotation.get(sentencesAnnotationKey)?[0]
        def sentimentClassKey = sentencesAnnotation.keySet().find { it.getName() == SentimentCoreAnnotations.SentimentClass.getName() }
        def sentimentClass = sentencesAnnotation.get(sentimentClassKey)

        println sentimentClass
    }

    @Test
    void testNlpSentimentAnalysis() {
        /** online sentiment analysis: http://nlp.stanford.edu:8080/sentiment/rntnDemo.html */
        Properties props = new Properties()
        props.setProperty("annotators", "tokenize, ssplit, pos, parse, sentiment")
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props)
        Annotation annotation = pipeline.process("I am truly disappointed by WorldCompany's treatment of the issue BLAH-456.")

        def sentencesAnnotationKey = annotation.keySet().find {it.getName() == CoreAnnotations.SentencesAnnotation.getName() }
        def sentencesAnnotation = annotation.get(sentencesAnnotationKey)?[0]
        def sentimentClassKey = sentencesAnnotation.keySet().find { it.getName() == SentimentCoreAnnotations.SentimentClass.getName() }
        def sentimentClass = sentencesAnnotation.get(sentimentClassKey)


        assertTrue(sentimentClass == "Negative")

        annotation = pipeline.process("It is already great this way !")
        sentencesAnnotationKey = annotation.keySet().find {it.getName() == CoreAnnotations.SentencesAnnotation.getName() }
        sentencesAnnotation = annotation.get(sentencesAnnotationKey)?[0]
        sentimentClassKey = sentencesAnnotation.keySet().find { it.getName() == SentimentCoreAnnotations.SentimentClass.getName() }
        sentimentClass = sentencesAnnotation.get(sentimentClassKey)

        assert (sentimentClass == "Positive")

        annotation = pipeline.process("It is already great this way ! But let's try to go a bit further though.")
        sentencesAnnotationKey = annotation.keySet().find {it.getName() == CoreAnnotations.SentencesAnnotation.getName() }
        sentencesAnnotation = annotation.get(sentencesAnnotationKey)?[0]
        sentimentClassKey = sentencesAnnotation.keySet().find { it.getName() == SentimentCoreAnnotations.SentimentClass.getName() }
        sentimentClass = sentencesAnnotation.get(sentimentClassKey)

        assert (sentimentClass == "Positive")

        annotation = pipeline.process("I'm not convinced yet and I'm not sure we will go this way.")
        sentencesAnnotationKey = annotation.keySet().find {it.getName() == CoreAnnotations.SentencesAnnotation.getName() }
        sentencesAnnotation = annotation.get(sentencesAnnotationKey)?[0]
        sentimentClassKey = sentencesAnnotation.keySet().find { it.getName() == SentimentCoreAnnotations.SentimentClass.getName() }
        sentimentClass = sentencesAnnotation.get(sentimentClassKey)

        assert (sentimentClass == "Negative")

        annotation = pipeline.process("Il semble que sur une phrase en français, l'analyse ne fonctionne pas correctement." +
                "Ce qui est somme toute un peu dommage.")
        sentencesAnnotationKey = annotation.keySet().find {it.getName() == CoreAnnotations.SentencesAnnotation.getName() }
        sentencesAnnotation = annotation.get(sentencesAnnotationKey)?[0]
        sentimentClassKey = sentencesAnnotation.keySet().find { it.getName() == SentimentCoreAnnotations.SentimentClass.getName() }
        sentimentClass = sentencesAnnotation.get(sentimentClassKey)

        assert (sentimentClass == "Negative")


    }
}
