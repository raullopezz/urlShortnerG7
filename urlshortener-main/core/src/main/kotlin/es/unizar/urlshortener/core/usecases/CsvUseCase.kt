@file:Suppress("WildcardImport", "MagicNumber", "EmptyDefaultConstructor", "TooGenericExceptionCaught",
        "ReturnCount", "UnusedPrivateProperty")
package es.unizar.urlshortener.core.usecases
import es.unizar.urlshortener.core.*
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.net.MalformedURLException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future


/**
 * Given CSV data (divided by , specifically), converts it and returns the modified CSV data.
 *
 */
interface CsvUseCase {
    fun convert(csvData: String): String
    fun convertFast(csvData: String): String
}


/**
 * Implementation of [ConvertCsvUseCase].
 */
class CsvUseCaseImpl(
        // We need a numeric parameter to work as a selector
        //private val temp: Int
) : CsvUseCase {

    /**
     * @brief Shorten all the URIs in a csv file
     * @param csvData The string representing the csv file
     * @param url The shortened URL's String
     * @return All the shortened URIs in a string representing a csv file
     */
    override fun convert(csvData: String): String {
        // Case 1: empty csvData
        if (csvData.isEmpty() || csvData.isBlank()) {
            return ""
        }
        // Case 2: invaid csvData
        // We check if csv has at least 2 , per \n
        val semicolonCount = csvData.count { it == ',' }
        if (semicolonCount % 2 != 0 || semicolonCount == 0) {
            // Handle the case where the semicolon count is not a multiple of 2
            return "Invalid CSV: missing commas, the amount of commas must be 2 per line"
        }
        // We check if csv has the correct number of \n per ,
        val endlnCount = csvData.count { it == '\n' }
        if (endlnCount + 1 < semicolonCount / 2) { // Plus one becaus the last line may not have a \n
            // Too many , per \n
            return "Invalid CSV: too many commas in a line, should be 2 per line"
        }

        // We convert selector to int
        val rows = csvData.split("\n")

        //println("Rows: " + rows)
        val modifiedData = rows.joinToString("") { row ->
            if (row.isBlank()) {
                ""
            } else {
                val cells = row.split(",")
                val cell1 = cells.getOrNull(0) ?: ""
                val cell2 = cells.getOrNull(1) ?: ""
                val cell3 = cells.getOrNull(2) ?: ""

                try {
                    var newCell = shortenUri(cell1,cell2,cell3)
                    "$cell1,$newCell\n"
                } catch (e: MalformedURLException) {
                    // Handle invalid URL
                    "invalid_url"
                } catch (e: Exception) {
                    // Handle other errors
                    println("Error: " + e)
                    "conversion_error\n"
                }


            }

        }

        return modifiedData
    }

    /**
     * @brief Shorten all the URIs in a csv file in a faster manner
     * @param csvData The string representing the csv file
     * @param url The shortened URL's String
     * @return All the shortened URIs in a string representing a csv file
     */
    override fun convertFast(csvData: String): String {
        // Case 1: empty csvData
        if (csvData.isEmpty() || csvData.isBlank()) {
            return ""
        }

        // Case 2: invaid csvData
        // We check if csv has at least 2 , per \n
        val semicolonCount = csvData.count { it == ',' }
        if (semicolonCount % 2 != 0 || semicolonCount == 0) {
            // Handle the case where the semicolon count is not a multiple of 2
            return "Invalid CSV: missing commas, the amount of commas must be 2 per line"
        }
        // We check if csv has the correct number of \n per ,
        val endlnCount = csvData.count { it == '\n' }
        if (endlnCount + 1 < semicolonCount / 2) { // Plus one becaus the last line may not have a \n
            // Too many , per \n
            return "Invalid CSV: too many commas in a line, should be 2 per line"
        }

        // Readies the executor
        val executor = Executors.newFixedThreadPool(semicolonCount/2)

        // Here's the different rows
        val rows = csvData.split("\n")

        // Preparing for future responses
        val convertedUris = mutableListOf<Future<String>>()



        // We use threads to process the rows rapidly
        for (row in rows) {
            val future: Future<String> = executor.submit(Callable {shortenRow(row)})
            convertedUris.add(future)
        }

        val csvReturn = StringBuilder()
        for (future in convertedUris) {
            csvReturn.append(future.get()) // Blocking call to get the result
        }

        executor.shutdown()

        return csvReturn.toString()
    }

    @Suppress("SwallowedException")
    private fun shortenRow(csvRow: String): String {
        val joinedString = StringBuilder()
        var rowString = csvRow.toString()

        if (rowString.isBlank()) {
            joinedString.append("")
        } else {
            val cells = rowString.split(",")
            val cell1 = cells.getOrNull(0) ?: ""
            val cell2 = cells.getOrNull(1) ?: ""
            val cell3 = cells.getOrNull(2) ?: ""

            try {
                val newCell = shortenUri(cell1, cell2, cell3)
                joinedString.append("$cell1,$newCell\n")
            } catch (e: MalformedURLException) {
                // Handle invalid URL
                println("Invalid URL: $e")
                joinedString.append("invalid_url\n")
            } catch (e: Exception) {
                // Handle other errors
                joinedString.append("conversion_error\n")
            }


        }

        return joinedString.toString()
    }

    /**
     * @brief Shortens a URI
     * @param originalUri The URI to be shortened
     * @param customWord The custom word to be used (can be blank)
     * @param isQR Wether a qr link should be created
     * @return The shortened URI and the link to the qr if isQR states so
     */
    private fun shortenUri(originalUri: String, customWord: String, isQr: String): String {
        // Modify it so it use ajax or it's gonna be a mess (use kotlin/js)
        val apiUrl = "http://localhost:8080/api/link"
        val prefix = "http://localhost:8080"


        var newUrl = ""
        val url = URL(apiUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.setRequestProperty("Accept", "application/json")
        connection.doOutput = true

        // Prepare the data for the API request
        val postData = "url=${URLEncoder.encode(originalUri, "UTF-8")}&alias=${URLEncoder.encode(customWord, 
                "UTF-8")}&qrBool=${URLEncoder.encode(isQr, "UTF-8")}"
        val input = postData.toByteArray(Charsets.UTF_8)

        // Send the data in the body of the request
        connection.outputStream.use { it.write(input, 0, input.size) }

        // Read the response code
        val responseCode = connection.responseCode

        // If the request was successful, get the short URL from the 'Location' header
        if (responseCode == HttpURLConnection.HTTP_CREATED) {
            newUrl = connection.getHeaderField("Location")
        }

        // Read the response from the server
        val response = connection.inputStream.bufferedReader().use { it.readText() }


        // Remove curly braces and split by comma
        response
            .replace("\",\"", ";")
            .replace("{\"", "")
            .replace("\"}", "")
            .replace("\":\"", ",")
            .replace("\":{", ",")
            .replace("}}","")
            .replace("\"","")
            .split(";")

        var qr = ""
        if (isQr.trim().equals("true", ignoreCase = true)){
            /*
            // Extract and print key-value pairs
            keyValuePairs.forEach { pair ->
                val keyValue = pair.trim().split(",")
                //println(keyValue)
                if (true) {
                    val key = keyValue[0].trim().removeSurrounding("\"")
                    val value = keyValue[1].trim().removeSurrounding("\"")
                    //qr = keyValue[2].trim().removeSurrounding("\"")
                }
            }

            var splitkeys = keyValuePairs[1].split(",")


            qr = "," + splitkeys.getOrNull(1)?.removeSuffix("}") ?: ""

            */
            qr = "," + prefix + newUrl + "/qr"
        }

        //println("Full response: " + response)
        return prefix + newUrl + qr
    }
}
