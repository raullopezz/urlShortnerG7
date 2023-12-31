package es.unizar.urlshortener.core


/**
 * [ClickRepositoryService] is the port to the repository that provides persistence to [Clicks][Click].
 */
interface ClickRepositoryService {
    fun save(cl: Click): Click
    fun findByUrlHash(id: String): List<Click>
}

/**
 * [ShortUrlRepositoryService] is the port to the repository that provides management to [ShortUrl][ShortUrl].
 */
interface ShortUrlRepositoryService {
    fun findByKey(id: String): ShortUrl?
    fun save(su: ShortUrl): ShortUrl
    fun delete(su: ShortUrl): Boolean
    fun updateReachabilityCode(hash: String, newCode: Int): Unit
    fun findReachabilityCodeByKey(key: String): Int?
}


/**
 * [ValidatorService] is the port to the service that validates if an url can be shortened.
 *
 * **Note**: It is a design decision to create this port. It could be part of the core .
 */
interface ValidatorService {
    fun isValid(url: String): Boolean

    /**
     * Checks if the alias contains a slash
     * @param alias
     * @return true if the alias is valid
     */
    fun withoutSlash(alias: String?): Boolean{
        return alias != null && !alias.contains("/")
    }
}

/**
 * [HashService] is the port to the service that creates a hash from a URL.
 *
 * **Note**: It is a design decision to create this port. It could be part of the core .
 */
interface HashService {
    fun hasUrl(url: String): String
}
