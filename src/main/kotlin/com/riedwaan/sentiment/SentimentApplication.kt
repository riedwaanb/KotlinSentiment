package com.riedwaan.sentiment

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.atomic.AtomicLong
import java.net.InetAddress

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient

import org.springframework.beans.factory.annotation.Value;

data class Greeting(val id: Long, val content: String)

// Enumerate Sentiments - error used if occured
enum class Sentiment {
    VERY_NEGATIVE,
    NEGATIVE,
    NEUTRAL,
    POSITIVE,
    VERY_POSITIVE,
    ERROR,
}

// These data classes are used to submit REST calls to Azure AI API  
data class DetectedLanguage(val name: String, val iso6391Name: String, val score: Int)

data class Documents(val documents: List<Document>)
    data class Document(val id: Int,
                        val text: String? = null,
                        val score: Float? = null,
                        val language: String? = null,
                        val detectedLanguages: List<DetectedLanguage>? = null)

// Simple function to return sentiment, accepts azure api key and endpoint
fun getSentiment(key: String, endpoint: String, text: String) : Sentiment {
    // Simple web client to make our requests
    val webClient = WebClient.builder()
            .baseUrl(endpoint)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Ocp-Apim-Subscription-Key", key)
            .build()

    // Detect the language
    println("Detecting For | $text")
    val languageDocuments = Documents(listOf(Document(1, text)))
    val languageResponse = webClient.post()
        .uri("/languages").body(BodyInserters.fromObject(languageDocuments))
        .exchange().block()?.bodyToMono(Documents::class.java)?.block()
        ?: throw RuntimeException("Exception: Could not detect language")
    val languageName = languageResponse.documents[0].detectedLanguages!![0].iso6391Name
    println("Detected [$languageName] language")

    // Detect the sentiment 
    val sentimentDocuments = Documents(listOf(Document(1, text, language = languageName)))
    val sentimentResponse = webClient.post()
            .uri("/sentiment").body(BodyInserters.fromObject(sentimentDocuments))
            .exchange().block()?.bodyToMono(Documents::class.java)?.block()
            ?: throw RuntimeException("Exception: Could not detect sentiment")

    // return the score translated into a friendly name
    val sentimentScore = sentimentResponse.documents[0].score!!
    val Sentiment = sentimentScore.toSentiment()
    print("Sentiment [$sentimentScore] | [$Sentiment]")

    return Sentiment
}

// Translate float to a Sentiment enum
fun Float.toSentiment(): Sentiment {
    if (this < 0.2) {
        return Sentiment.VERY_NEGATIVE
    }
    if (this < 0.4) {
        return Sentiment.NEGATIVE
    }
    if (this < 0.6) {
        return Sentiment.NEUTRAL
    }
    if (this < 0.8) {
        return Sentiment.POSITIVE
    }
    return Sentiment.VERY_POSITIVE
}

// This is the Rest Controller that Says Hello or Return the Sentiment of text passed
@RestController
class SentimentController {
    val counter = AtomicLong()
    val hostname = InetAddress.getLocalHost().getHostName()

    @Value("\${textanalytics.key}")
    lateinit var key: String
    @Value("\${textanalytics.endpoint}")
    lateinit var endpoint: String    
    data class SentimentResponse(val sentiment: Sentiment) 
    
    @GetMapping("/")
    fun greeting(@RequestParam(value = "name", defaultValue = "World") name: String) =
        Greeting(counter.incrementAndGet(), "Hello from $hostname, $name")

    @GetMapping("/sentiment")
    @ResponseBody
    fun getAISentiment(@RequestParam("text", required = false) text: String?): SentimentResponse {
        if (text.isNullOrEmpty()) {
            println("Its isNullOrEmpty")
            return SentimentResponse(Sentiment.ERROR)
        }
        return SentimentResponse(getSentiment(key, endpoint, text!!))
    }
} 

@SpringBootApplication
class SentimentApplication

fun main(args: Array<String>) {
	runApplication<SentimentApplication>(*args)
}

