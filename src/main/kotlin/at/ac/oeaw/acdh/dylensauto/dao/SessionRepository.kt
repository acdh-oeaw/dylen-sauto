package at.ac.oeaw.acdh.dylensauto.dao

import at.ac.oeaw.acdh.dylensauto.entity.Session
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.mongodb.repository.Query
import java.util.*

interface SessionRepository : MongoRepository<Session, String> {
    //longer than 3 minutes
    //latest app version
    @Query("{ 'duration' : { \$gt: ?0 },  'appVersion' : ?1, 'endTime' : {\$lt: ?2} }")
    fun findSuitableSessions(minDuration: Long, appVersion: Double, testEndDate: Date): List<Session?>?
}