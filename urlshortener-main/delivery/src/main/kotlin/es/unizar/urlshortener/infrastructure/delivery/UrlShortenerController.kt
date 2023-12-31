@file:Suppress("LongParameterList", "WildcardImport")

package es.unizar.urlshortener.infrastructure.delivery

import es.unizar.urlshortener.core.ClickProperties
import es.unizar.urlshortener.core.InvalidUrlException
import es.unizar.urlshortener.core.ShortUrlProperties
import es.unizar.urlshortener.core.usecases.*
import eu.bitwalker.useragentutils.UserAgent
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.io.ByteArrayResource
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.MediaType.IMAGE_PNG_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.concurrent.BlockingQueue
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Qualifier


/**
 * The specification of the controller.
 */
interface UrlShortenerController {

    /**
     * Redirects and logs a short url identified by its [id].
     *
     * **Note**: Delivery of use cases [RedirectUseCase] and [LogClickUseCase].
     */
    fun redirectTo(id: String, request: HttpServletRequest): ResponseEntity<Unit>

    /**
     * Creates a short url from details provided in [data].
     *
     * **Note**: Delivery of use case [CreateShortUrlUseCase].
     */
    fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut>

    /**
     * Converts CSV data provided in [data].
     *
     * **Note**: Delivery of use case [CsvUseCase].
     */
    fun csvHandler(data: CsvDataIn, request: HttpServletRequest): ResponseEntity<CsvDataOut>

    /**
     * Converts CSV data provided in [data] with asyncronous functions.
     *
     * **Note**: Delivery of use case [CsvUseCase].
     */
    fun csvHandlerFast(data: CsvDataIn, request: HttpServletRequest): ResponseEntity<CsvDataOut>

    /**
     * Obtains the QR data giving the URL id in [id].
     *
     * **Note**: Delivery of use case [QRUseCase].
     */
    fun getQR(id: String, request: HttpServletRequest): ResponseEntity<ByteArrayResource>

    /**
     * Retrieves header information for a given ID from the HTTP request.
     * @param id The ID representing the hash where the user is redirected
     * @param request The HttpServletRequest containing the header information
     * @return ResponseEntity containing the header information of all clicks to a given hash
     */
    fun returnInfoHeader(id: String, request: HttpServletRequest): ResponseEntity<Any>

}

/**
 * Data required to create a short url.
 */
data class ShortUrlDataIn(
    val url: String,
    val sponsor: String? = null,
    val alias: String = "",
    val qrBool: Boolean? = null

)

/**
 * Data returned after the creation of a short url.
 */
data class ShortUrlDataOut(
    val url: URI? = null,
    val properties: Map<String, Any> = emptyMap()
)

/**
 * Data required to process CSV data.
 */
 data class CsvDataIn(
    val csv: String,
 )

/**
 * Data returned after the processing of a CSV file.
 */
data class CsvDataOut(
    val csv: String
)

/**
 * The implementation of the controller.
 *
 * **Note**: Spring Boot is able to discover this [RestController] without further configuration.
 */

@RestController
class UrlShortenerControllerImpl(
    val redirectUseCase: RedirectUseCase,
    val logClickUseCase: LogClickUseCase,
    val createShortUrlUseCase: CreateShortUrlUseCase,
    val csvUseCase: CsvUseCase,
    val qrUseCase: QRUseCase,
    val qrQueue: BlockingQueue<Pair<String, String>>,
    @Qualifier("reachabilityQueue") val reachableQueue: BlockingQueue<Pair<String,String>>,
    val identifyInfoClientUseCase: IdentifyInfoClientUseCase

) : UrlShortenerController {

    /**
     * Redirects and logs a short url identified by its [id].
     * @param id The ID representing the hash where the user is redirected
     * @param request The HttpServletRequest containing the header information
     * @return ResponseEntity containing the header information of all clicks to a given hash
     * Use the coroutines library to make the call to the use case asynchronous
     */
    @Operation(
            summary = "Redirect to URL identified by its id",
            description = "Given an id, redirects if it's possible to the web associated",
            responses = [
                ApiResponse(
                        responseCode = "200",
                        description = "Successful redirection"),
                ApiResponse(
                        responseCode = "400",
                        description = "Bad Request"),
                ApiResponse(
                        responseCode = "403",
                        description = "Forbidden"),

                ApiResponse(
                        responseCode = "404",
                        description = "ID doesn't exists"),
            ]
    )
    @GetMapping("/{id:(?!api|index).*}")
    override fun redirectTo(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<Unit> =
        runBlocking{
                val result = coroutineScope{
                    async(Dispatchers.IO){
                        redirectUseCase.redirectTo(id).let {
                            val header = request.getHeader("User-Agent")
                            val userAgent = header?.let { userAgentHeader ->
                                UserAgent.parseUserAgentString(userAgentHeader) }
                            val browser = userAgent?.browser?.getName()
                            val platform = userAgent?.operatingSystem?.getName()
                            logClickUseCase.logClick(id, ClickProperties(ip = request.remoteAddr, browser = browser,
                                    platform = platform))
                            val h = HttpHeaders()
                            h.location = URI.create(it.target)
                            ResponseEntity<Unit>(h, HttpStatus.valueOf(it.mode))
                        }
                    }
                }
    result.await()
    }

    /**
     * Creates a short Url.
     * @param data The ShortUrlDataIn required to create a short url.
     * @param request The HttpServletRequest containing the header information
     * @return ResponseEntity containing ShortUrlDataOut for the html
     * Use the coroutines library to make the call to the use case asynchronous
     */
    @Operation(
            summary = "Creates a short Url",
            description = "Creates a shortened URL based on provided long Url.",
            responses = [
                ApiResponse(
                    responseCode = "201",
                    description = "Created",
                    content =  [Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ShortUrlDataOut::class)
                    )]),
                ApiResponse(
                    responseCode = "400",
                    description = "Bad Request",
                    content =  [Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = InvalidUrlException::class)
                    )]),

            ]
    )
    @PostMapping("/api/link", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    override fun shortener(data: ShortUrlDataIn, request: HttpServletRequest): ResponseEntity<ShortUrlDataOut> =
            runBlocking {
            val result = coroutineScope {
                async(Dispatchers.IO){
                    createShortUrlUseCase.create(
                        url = data.url,
                        data = ShortUrlProperties(
                            ip = request.remoteAddr,
                            sponsor = data.sponsor,
                            alias = data.alias,
                            qrBool = data.qrBool
                        )
                    ).let {
                        // Meter a la cola
                        val value: String = if(it.properties.alias == ""){
                            it.hash
                        } else {
                            it.properties.alias
                        }
                        reachableQueue.put(Pair(data.url, value))

                        val h = HttpHeaders()
                        val url = linkTo<UrlShortenerControllerImpl> { redirectTo(it.hash, request) }.toUri()
                        h.location = url


                        val properties = mutableMapOf<String, Any>()

                        if(data.qrBool == true){
                            // Meter a la cola
                            qrQueue.put(Pair(it.hash, url.toString()))
                            val qrUrl = linkTo<UrlShortenerControllerImpl> { getQR(it.hash, request) }.toUri()
                            properties["qr"] = qrUrl
                        }

                        val response = ShortUrlDataOut(
                            url = url,
                            properties = properties
                        )

                        ResponseEntity<ShortUrlDataOut>(response, h, HttpStatus.CREATED)
                    }
                }
            }
            result.await()
    }



    @Operation(
        summary = "Handle CSV data in bulk",
        description = "Processes CSV data in bulk and provides processing status." ,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Ok",
                content =  [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CsvDataOut::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Bad Request",
                content =  [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CsvDataOut::class)
                )]
            ),
            ApiResponse(
                responseCode = "201",
                description = "Created",
                content =  [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CsvDataOut::class)
                )]
            )]
    )
    @PostMapping("/api/bulk", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE]) 
    override fun csvHandler(data: CsvDataIn, request: HttpServletRequest): ResponseEntity<CsvDataOut> =
        csvUseCase.convert(data.csv).let { processedData ->
            
            val response = CsvDataOut(
                csv = processedData
            )
            when (processedData) {
                "" -> {
                    ResponseEntity<CsvDataOut>(response, HttpStatus.OK)
                }
                "Invalid CSV: missing commas, the amount of commas must be 2 per line" -> {
                    ResponseEntity<CsvDataOut>(response, HttpStatus.BAD_REQUEST)
                }
                "Invalid CSV: too many commas in a line, should be 2 per line" -> {
                    ResponseEntity<CsvDataOut>(response, HttpStatus.BAD_REQUEST)
                }
                else -> {
                    ResponseEntity<CsvDataOut>(response, HttpStatus.CREATED)
                }
            }            
        }


    @Operation(
        summary = "Handle CSV data in bulk handled asinchronously",
        description = "Processes CSV data in bulk and provides processing status." ,
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Ok",
                content =  [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CsvDataOut::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Bad Request",
                content =  [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CsvDataOut::class)
                )]
            ),
            ApiResponse(
                responseCode = "201",
                description = "Created",
                content =  [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = CsvDataOut::class)
                )]
            )]
    )
    @PostMapping("/api/fast-bulk", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    override fun csvHandlerFast(data: CsvDataIn, request: HttpServletRequest): ResponseEntity<CsvDataOut> =
            csvUseCase.convertFast(data.csv).let { processedData ->

                val response = CsvDataOut(
                        csv = processedData
                )
                when (processedData) {
                    "" -> {
                        ResponseEntity<CsvDataOut>(response, HttpStatus.OK)
                    }
                    "Invalid CSV: missing commas, the amount of commas must be 2 per line" -> {
                        ResponseEntity<CsvDataOut>(response, HttpStatus.BAD_REQUEST)
                    }
                    "Invalid CSV: too many commas in a line, should be 2 per line" -> {
                        ResponseEntity<CsvDataOut>(response, HttpStatus.BAD_REQUEST)
                    }
                    else -> {
                        ResponseEntity<CsvDataOut>(response, HttpStatus.CREATED)
                    }
                }
            }


    /**
     * Creates a short Url.
     * @param id The ID representing the hash of the URI
     * @param request The HttpServletRequest containing the header information
     * @return ResponseEntity containing ByteArrayResource of the QR
     */
    @Operation(
        summary = "Obtain a QR",
        description = "Given an id, returns a QR Code in PNG format",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Returns the QR code",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = ByteArrayResource::class)
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Bad Request"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Not found"
            )
        ]
    )
        @GetMapping("/{id:(?!api|index).*}/qr")
    override fun getQR(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<ByteArrayResource> =
        qrUseCase.getQRUseCase(id).let { qr ->
            val h = HttpHeaders()
            h.set(HttpHeaders.CONTENT_TYPE, IMAGE_PNG_VALUE)
            ResponseEntity<ByteArrayResource>(ByteArrayResource(qr, IMAGE_PNG_VALUE), h, HttpStatus.OK)
        }

    /**
     * Creates a short Url.
     * @param id The ID representing the hash of the URI
     * @param request The HttpServletRequest containing the header information
     * @return ResponseEntity
     */
    @Operation(
            summary = "Obtain click information for a short url"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Information succesfully obtained",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = Map::class, subTypes = [String::class, Int::class])
        )]
    )
    @GetMapping("/api/link/{id}")
    override fun returnInfoHeader(@PathVariable id: String, request: HttpServletRequest): ResponseEntity<Any> {
        return ResponseEntity(identifyInfoClientUseCase.returnInfoShortUrl(id), HttpStatus.OK)
    }

}
