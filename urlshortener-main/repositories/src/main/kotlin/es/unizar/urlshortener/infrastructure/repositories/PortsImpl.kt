package es.unizar.urlshortener.infrastructure.repositories

import es.unizar.urlshortener.core.Click
import es.unizar.urlshortener.core.ClickRepositoryService
import es.unizar.urlshortener.core.ShortUrl
import es.unizar.urlshortener.core.ShortUrlRepositoryService

/**
 * Implementation of the port [ClickRepositoryService].
 */
class ClickRepositoryServiceImpl(
    private val clickEntityRepository: ClickEntityRepository
) : ClickRepositoryService {
    override fun save(cl: Click): Click = clickEntityRepository.save(cl.toEntity()).toDomain()
    override fun findByUrlHash(id: String): List<Click> {
        val clickEntities: List<ClickEntity> = clickEntityRepository.findAllByHash(hash = id)
        return clickEntities.map { it.toDomain() }
    }
}

/**
 * Implementation of the port [ShortUrlRepositoryService].
 */
class ShortUrlRepositoryServiceImpl(
    private val shortUrlEntityRepository: ShortUrlEntityRepository
) : ShortUrlRepositoryService {
    override fun findByKey(id: String): ShortUrl? = shortUrlEntityRepository.findByHash(id)?.toDomain()

    override fun save(su: ShortUrl): ShortUrl = shortUrlEntityRepository.save(su.toEntity()).toDomain()

    override fun updateReachabilityCode (hash: String, newCode: Int) : Unit {
        val existingKey = shortUrlEntityRepository.findByHash(hash)
        if (existingKey != null) {
            existingKey.reachabilityStatus = newCode
            shortUrlEntityRepository.save(existingKey).toDomain()
        }
    }

    override fun findReachabilityCodeByKey(key: String): Int? {
        val shortUrl = shortUrlEntityRepository.findByHash(key)
        return shortUrl?.reachabilityStatus
    }

    override fun delete(su: ShortUrl): Boolean {
        val deletedEntity = shortUrlEntityRepository.delete(su.toEntity())
        return deletedEntity != null
    }
}

