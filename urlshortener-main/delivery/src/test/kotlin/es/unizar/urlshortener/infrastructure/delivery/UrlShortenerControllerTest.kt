@file:Suppress("WildcardImport", "UnusedPrivateProperty")

package es.unizar.urlshortener.infrastructure.delivery

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import es.unizar.urlshortener.core.*

import es.unizar.urlshortener.core.usecases.*

import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.BDDMockito.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import java.util.concurrent.BlockingQueue

@WebMvcTest
@ContextConfiguration(
    classes = [
        UrlShortenerControllerImpl::class,
        RestResponseEntityExceptionHandler::class
    ]
)
class UrlShortenerControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var redirectUseCase: RedirectUseCase

    @MockBean
    private lateinit var logClickUseCase: LogClickUseCase

    @MockBean
    private lateinit var createShortUrlUseCase: CreateShortUrlUseCase

    @MockBean
    private lateinit var csvUseCase: CsvUseCase

    @MockBean
    private lateinit var qrUseCase: QRUseCase

    @MockBean(name="qrQueue")
    private lateinit var qrQueue: BlockingQueue<Pair<String, String>>

    @MockBean(name="reachabilityQueue")
    private lateinit var reachableQueue: BlockingQueue<Pair<String, String>>

    @MockBean
    private lateinit var identifyInfoClientUseCase: IdentifyInfoClientUseCase


    @Test
    fun `redirectTo returns a redirect when the key exists`() {
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection("http://example.com/"))

        mockMvc.perform(get("/{id}", "key"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(redirectedUrl("http://example.com/"))

        verify(logClickUseCase).logClick("key", ClickProperties(ip = "127.0.0.1"))
    }

    @Test
    fun `redirectTo returns a not found when the key does not exist`() {
        given(redirectUseCase.redirectTo("key"))
            .willAnswer { throw RedirectionNotFound("key") }

        mockMvc.perform(get("/{id}", "key"))
            .andDo(print())
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.statusCode").value(404))

        verify(logClickUseCase, never()).logClick("key", ClickProperties(ip = "127.0.0.1"))
    }

    @Test
    fun `creates returns a basic redirect if it can compute a hash`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(ip = "127.0.0.1", qrBool = false, alias = "")
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/")))

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .param("qrBool", "false")
                .param("alias", "")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("/f684a3c4"))
            .andExpect(jsonPath("$.url").value("/f684a3c4"))
    }

    @Test
    fun `creates returns a basic redirect if it can compute a hash with a qr`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(ip = "127.0.0.1", qrBool = true)
            )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://example.com/")))

        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .param("qrBool", "true")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("/f684a3c4"))
            .andExpect(jsonPath("$.url").value("/f684a3c4"))
            .andExpect(jsonPath("$.properties.qr").value("/f684a3c4/qr"))
    }

    /*
    @Test
    fun `Create returns a 400 response if the uri to shorten is not reachable`() {
        val urlToShorten = "http://url-unreachable.com"
        val hash = "sdfjhjsdf"
        given(isReachableUseCase.isReachable(urlToShorten, hash)).willReturn(false)
        given(
                createShortUrlUseCase.create(
                        url = urlToShorten,
                        data = ShortUrlProperties(ip = "127.0.0.1")
                )
        ).willAnswer { throw UrlToShortNotReachable(urlToShorten) }

        mockMvc.perform(
                post("/api/link")
                        .param("url", urlToShorten)
                        .contentType((MediaType.APPLICATION_FORM_URLENCODED_VALUE))
        ).andExpect(status().isBadRequest)
    }
     */


    /*
    @Test
    fun `Create returns a 200 response if the uri to shorten is reachable`() {
        val urlToShorten = "http://url-reachable.com"
        given(isReachableUseCase.isReachable(urlToShorten)).willReturn(true)
        given(
                createShortUrlUseCase.create(
                        url = urlToShorten,
                        data = ShortUrlProperties(ip = "127.0.0.1")
                )
        ).willReturn(ShortUrl("f684a3c4", Redirection("http://url-reachable.com")))

        mockMvc.perform(
                post("/api/link")
                        .param("url", urlToShorten)
                        .contentType((MediaType.APPLICATION_FORM_URLENCODED_VALUE))
        ).andExpect(status().isCreated)
    }

     */

    /*
    THIS TEST WORKS BUT IS REDUNDANT WITHOUT SCALABILITY
    @Test
    fun `Redirect to returns a 403 response if the id is registered but the uri is not reachable`() {
        val urlToRedirect = "https://url-unreachable.com/"
        val id = "existing-hash"

        given(isReachableUseCase.isReachable(urlToRedirect)).willReturn(false)
        given(redirectUseCase.redirectTo(id))
            .willAnswer { throw UrlRegisteredButNotReachable(id) }


        mockMvc.perform(
            get("/{id}", id)
        )
            .andExpect(status().isForbidden)
    }
     */

    @Test
    fun `creates returns bad request if it can compute a hash`() {
        given(
            createShortUrlUseCase.create(
                url = "ftp://example.com/",
                data = ShortUrlProperties(ip = "127.0.0.1")
            )
        ).willAnswer { throw InvalidUrlException("ftp://example.com/") }

        mockMvc.perform(
            post("/api/link")
                .param("url", "ftp://example.com/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andExpect(status().isBadRequest)
    }


    @Test
    fun `returnInfoShortUrl returns information if short url has been clicked`() {

        val userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_6_8) AppleWebKit/535.19 (KHTML, like Gecko) " +
                "Chrome/11.0.696.68 Safari/535.19"
        val data: MutableMap<String, Int> = mutableMapOf()
        data["Mac OS X - Chrome 11"] = 1

        given(redirectUseCase.redirectTo("key")).willReturn(Redirection("http://example.com/"))
        given(identifyInfoClientUseCase.returnInfoShortUrl("key")).willReturn(data)

        mockMvc.perform(get("/{id}", "key")
            .header("User-Agent", userAgent))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(redirectedUrl("http://example.com/"))

        mockMvc.perform(get("/api/link/{id}", "key"))
            .andExpect(status().isOk)
            .andExpect(content().json(jacksonObjectMapper().writeValueAsString(data)))
    }

    @Test
    fun `redirectTo doesn't return a 4xx error if there are no headers`() {
        given(redirectUseCase.redirectTo("key")).willReturn(Redirection("http://example.com/"))

        // Si envio cabeceras vacias no genera ningun error de usuario
        mockMvc.perform(get("/{id}", "key"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(redirectedUrl("http://example.com/"))

    }

    @Test
    fun `if the key doesn't exist, qr will return a not found (404)`() {
        given(qrUseCase.getQRUseCase("key"))
            .willAnswer { throw RedirectionNotFound("key") }

        mockMvc.perform(get("/{id}/qr", "key"))
            .andDo(print())
    }

    @Test
    fun `if the key exists but doesn't exist a qr for that key, qr returns a bad request`() {
        given(qrUseCase.getQRUseCase("key"))
            .willAnswer { throw QRNotAvailable("key") }

        mockMvc.perform(get("/{id}/qr", "key"))
            .andDo(print())
    }

    @Test
    fun `if the key exists, qr will return an image `() {
        given(qrUseCase.getQRUseCase("key")).willReturn("Testing".toByteArray())

        mockMvc.perform(get("/{id}/qr", "key"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.IMAGE_PNG))
            .andExpect(content().bytes("Testing".toByteArray()))
    }


    /**
     * Test that the controller returns a 400 error if the key already exists
     */
    @Test
    fun `if the key already exists, returns a 400 error`() {
        val existingKey = "existing-key"
        val existingUrl = "http://example.com/"

        given(createShortUrlUseCase.create(url = existingUrl, data = ShortUrlProperties(ip = "127.0.0.1")))
            .willAnswer { throw KeyAlreadyExists(existingKey) }

        mockMvc.perform(
            post("/api/link")
                .param("url", existingUrl)
                .contentType((MediaType.APPLICATION_FORM_URLENCODED_VALUE))
        ).andExpect(status().isBadRequest)
    }




    /**
     * Test that the returned redirect contains the alias in the URL
     */
    @Test
    fun `creates returns a basic redirect if it can compute a hash with an alias`() {
        given(
            createShortUrlUseCase.create(
                url = "http://example.com/",
                data = ShortUrlProperties(ip = "127.0.0.1", alias = "alias")
            )
        ).willReturn(ShortUrl("alias", Redirection("http://example.com/")))
        mockMvc.perform(
            post("/api/link")
                .param("url", "http://example.com/")
                .param("alias", "alias")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(redirectedUrl("/alias"))
            .andExpect(jsonPath("$.url").value("/alias"))

    }

    /**
     * Test that the controller returns a 400 error if the alias is already in use
     */
    @Test
    fun `returns a 400 error if the alias is already in use`() {
        val usedAlias = "usedAlias"
        val existingUrl = "http://example.com/"

        // Set up mock behavior to simulate the alias already existing
        given(
            createShortUrlUseCase.create(
                url = existingUrl,
                data = ShortUrlProperties(ip = "127.0.0.1", alias = usedAlias)
            )
        )
            .willAnswer { throw AliasAlreadyExists(usedAlias) }
        mockMvc.perform(
            post("/api/link")
                .param("url", existingUrl)
                .param("alias", usedAlias)
                .contentType((MediaType.APPLICATION_FORM_URLENCODED_VALUE))
        ).andExpect(status().isBadRequest)

    }

    /**
     * Test that the controller returns a 400 error if the alias contains a slash
     */

    @Test
    fun `returns a 400 error if the alias contains a slash`() {
        val slashAlias = "slash/alias"
        val existingUrl = "http://example.com/"

        // Set up mock behavior to simulate the alias contains a slash
        given(
            createShortUrlUseCase.create(
                url = existingUrl,
                data = ShortUrlProperties(ip = "127.0.0.1", alias = slashAlias)
            )
        ).willAnswer { throw AliasContainsSlash(slashAlias) }

        mockMvc.perform(
            post("/api/link")
                .param("url", existingUrl)
                .param("alias", slashAlias)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        ).andExpect(status().isBadRequest)
    }




    /*
    INTENTO DE  TEST DE LA BLOCKING QUEUE -> COMPROBAR QUE SE EJECUTA EXECUTOR DE LA QRBLOCKINGQUEUE
    @Test
    fun `executor processes qrQueue items`() {

        val request = MockHttpServletRequest()
        request.remoteAddr = "127.0.0.1"

        val data = ShortUrlDataIn(
                url = "http://example.com/",
                qrBool = true
        )

        mockMvc.perform(
                post("/api/link")
                        .param("url", data.url)
                        .param("qrBool", data.qrBool.toString())
        )
                .andExpect(MockMvcResultMatchers.status().isCreated)

        verify(qrBlockingQueue).executor()
        //verify(qrBlockingQueue).add(qrQueueCaptor.capture())

        val capturedPair = qrQueueCaptor.value
        val expectedPair = Pair("hashValue", "http://example.com/") // Valores esperados
        assert(capturedPair == expectedPair) // Verificar si la captura coincide con los valores esperados
    }*/

   
    @Test
    fun `csvHandler returns ok status when given an empty file`() {
        val emptyString = ""
        given(csvUseCase.convert(""))
            .willReturn(emptyString)

        mockMvc.perform(
            post("/api/bulk")
                .param("csv", emptyString)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.csv").value(""))
    }


    @Test
    fun `csvHandler returns 400 when recieved an invalid csv`() {
        val csvData = "a34df"
        given(csvUseCase.convert(csvData))
            .willReturn(
                "Invalid CSV: missing commas, the amount of commas must be 2 per line"
            )

        mockMvc.perform(
            post("/api/bulk")
                .param("csv", csvData)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.csv").value(
                "Invalid CSV: missing commas, the amount of commas must be 2 per line"
            ))
    }



    @Test
    fun `csvHandler returns a single line csv with the shortened urls, qr and custom words`() {
        val csvData = "https://www.unizar.es/,universidad,true"
        given(csvUseCase.convert(csvData))
            .willReturn(
                "https://www.unizar.es/,http://localhost:8080/universidad,http://localhost:8080/universidad/qr"
            )

        mockMvc.perform(
            post("/api/bulk")
                .param("csv", csvData)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.csv").value(
                "https://www.unizar.es/,http://localhost:8080/universidad,http://localhost:8080/universidad/qr"
            ))
    }

    @Test
    fun `csvHandler returns a single line csv with the shortened urls, no qr and no custom word`() {
        val csvData = "https://www.unizar.es/,,false"
        given(csvUseCase.convert(csvData))
            .willReturn(
                "https://www.unizar.es/,http://localhost:8080"
            )

        mockMvc.perform(
            post("/api/bulk")
                .param("csv", csvData)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.csv").value(
                "https://www.unizar.es/,http://localhost:8080"
            ))
    }

    @Test
    fun `csvHandler returns a csv with the shortened urls, and different custom word & qr options`() {
        val csvData = "https://www.unizar.es/,,false\n" +
                      "https://www.youtube.com/,videos,false\n" +
                      "https://github.com/,trabajo,true"
        given(csvUseCase.convert(csvData))
            .willReturn(
                "https://www.unizar.es/,http://localhost:8080,," +
                "https://www.youtube.com/,http://localhost:8080/videos" +
                "https://github.com/,http://localhost:8080/trabajo,http://localhost:8080/trabajo/qr"
            )

        mockMvc.perform(
            post("/api/bulk")
                .param("csv", csvData)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        )
            .andDo(print())
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.csv").value(
                "https://www.unizar.es/,http://localhost:8080,," +
                "https://www.youtube.com/,http://localhost:8080/videos" +
                "https://github.com/,http://localhost:8080/trabajo,http://localhost:8080/trabajo/qr"
            ))
    }




}

