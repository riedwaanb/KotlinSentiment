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
// Sentiment enumeration
enum class Sentiment {
    VERY_NEGATIVE,
    NEGATIVE,
    NEUTRAL,
    POSITIVE,
    VERY_POSITIVE,
    ERROR,
}

data class DetectedLanguage(val name: String, val iso6391Name: String, val score: Int)

data class Documents(val documents: List<Document>)
    data class Document(val id: Int,
                        val text: String? = null,
                        val score: Float? = null,
                        val language: String? = null,
                        val detectedLanguages: List<DetectedLanguage>? = null)

fun getSentiment(key: String, endpoint: String, text: String) : Sentiment {
    println("getSentiment [$key] | [$endpoint]")

    // Build a common web client for our requests.
    val webClient = WebClient.builder()
            .baseUrl(endpoint)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Ocp-Apim-Subscription-Key", key)
            .build()

    // We first need to guess the language for the input text:
    // the sentiment score will be better if the language is known.

    println("Detecting language from: $text")
    val langDocs = Documents(listOf(Document(1, text)))
    val langResp = webClient.post()
            .uri("/languages").body(BodyInserters.fromObject(langDocs))
            .exchange().block()?.bodyToMono(Documents::class.java)?.block()
            ?: throw RuntimeException("Failed to detect language")

    val lang = langResp.documents[0].detectedLanguages!![0].iso6391Name
    println("Detecting sentiment with language [$lang] | $text")

    val sentimentDocs = Documents(listOf(Document(1, text, language = lang)))
    val sentimentResp = webClient.post()
            .uri("/sentiment").body(BodyInserters.fromObject(sentimentDocs))
            .exchange().block()?.bodyToMono(Documents::class.java)?.block()
            ?: throw RuntimeException("Failed to detect sentiment score")

    val sentimentScore = sentimentResp.documents[0].score!!
    val Sentiment = sentimentScore.toSentiment()
    print("Detected sentiment score: $text -> $sentimentScore -> $Sentiment")

    return Sentiment
}

/**
  Convert a sentiment score to a [Sentiment] instance.
     */
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

