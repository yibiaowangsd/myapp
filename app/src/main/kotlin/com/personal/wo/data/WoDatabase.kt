package com.personal.wo.data

import androidx.room.*

@Entity(tableName = "app_meta") data class MetaRow(@PrimaryKey val key: String, val value: String)
@Entity(tableName = "recording_policy") data class PolicyRow(@PrimaryKey val singletonId: Int = 1, val json: String)
@Entity(tableName = "source_state") data class SourceRow(@PrimaryKey val sourceId: String, val json: String)
@Entity(tableName = "observation", indices = [Index(value = ["sourceId", "sourceKey"], unique = true), Index(value = ["startMs", "endMs"])])
data class ObservationRow(@PrimaryKey val id: String, val sourceId: String, val sourceKey: String, val kind: String, val startMs: Long, val endMs: Long, val receivedMs: Long, val json: String)
@Entity(tableName = "exclusion", indices = [Index(value = ["startMs", "endMs"])])
data class ExclusionRow(@PrimaryKey val id: String, val startMs: Long, val endMs: Long?, val json: String)
@Entity(tableName = "place") data class PlaceRow(@PrimaryKey val id: String, val json: String)
@Entity(tableName = "episode", indices = [Index(value = ["startMs", "endMs"]), Index("kind")])
data class EpisodeRow(@PrimaryKey val id: String, val revision: Long, val startMs: Long, val endMs: Long, val kind: String, val title: String, val searchText: String, val json: String)
@Entity(tableName = "episode_evidence", primaryKeys = ["episodeId", "evidenceKey"], indices = [Index("episodeId")], foreignKeys = [ForeignKey(entity = EpisodeRow::class, parentColumns = ["id"], childColumns = ["episodeId"], onDelete = ForeignKey.CASCADE)])
data class EvidenceRow(val episodeId: String, val evidenceKey: String, val snapshotJson: String)
@Entity(tableName = "episode_relation", primaryKeys = ["parentId", "childId", "relation"]) data class RelationRow(val parentId: String, val childId: String, val relation: String)
@Entity(tableName = "annotation", indices = [Index("episodeId")]) data class AnnotationRow(@PrimaryKey val id: String, val episodeId: String, val createdMs: Long, val json: String)
@Entity(tableName = "media_ref") data class MediaRow(@PrimaryKey val id: String, val sourceKey: String, val uri: String, val startMs: Long)
@Entity(tableName = "daily_metric", primaryKeys = ["date", "zone", "metricKey"]) data class MetricRow(val date: String, val zone: String, val metricKey: String, val json: String)
@Entity(tableName = "daily_summary", primaryKeys = ["date", "zone"]) data class SummaryRow(val date: String, val zone: String, val revision: Long, val asOfMs: Long, val json: String)
@Entity(tableName = "source_coverage", indices = [Index(value = ["startMs", "endMs"])]) data class CoverageRow(@PrimaryKey val id: String, val sourceId: String, val startMs: Long, val endMs: Long, val json: String)
@Fts4 @Entity(tableName = "event_search") data class SearchRow(@PrimaryKey @ColumnInfo(name = "rowid") val rowid: Int = 0, val episodeId: String, val tokens: String)
@Entity(tableName = "operation_log") data class OperationRow(@PrimaryKey val id: String, val timeMs: Long, val code: String, val count: Long = 0)
@Entity(tableName = "dirty_window") data class DirtyRow(@PrimaryKey val id: String, val startMs: Long, val endMs: Long, val generation: Long, val reason: String)

@Dao interface WoDao {
    @Query("SELECT * FROM recording_policy WHERE singletonId=1") suspend fun policy(): PolicyRow?
    @Upsert suspend fun putPolicy(row: PolicyRow)
    @Query("SELECT * FROM app_meta WHERE `key`=:key") suspend fun meta(key: String): MetaRow?
    @Upsert suspend fun putMeta(row: MetaRow)
    @Query("DELETE FROM app_meta WHERE `key`>=:from AND `key`<=:to") suspend fun deleteMetaRange(from:String,to:String)
    @Query("SELECT * FROM source_state") suspend fun sources(): List<SourceRow>
    @Upsert suspend fun putSource(row: SourceRow)
    @Query("SELECT * FROM observation WHERE startMs<:end AND endMs>=:start ORDER BY startMs LIMIT 10000") suspend fun observations(start: Long, end: Long): List<ObservationRow>
    @Query("SELECT * FROM observation WHERE sourceId='LOCATION' AND startMs<:end AND endMs>=:start ORDER BY startMs LIMIT 10000") suspend fun positions(start:Long,end:Long):List<ObservationRow>
    @Query("SELECT * FROM observation ORDER BY startMs LIMIT :limit OFFSET :offset") suspend fun allObservations(limit: Int, offset: Int): List<ObservationRow>
    @Upsert suspend fun putObservations(rows: List<ObservationRow>)
    @Query("SELECT * FROM observation WHERE id IN (:ids)") suspend fun observationsByIds(ids:List<String>):List<ObservationRow>
    @Query("SELECT * FROM observation WHERE sourceId=:source AND startMs<:end AND endMs>=:start") suspend fun sourceObservations(source:String,start:Long,end:Long):List<ObservationRow>
    @Query("SELECT * FROM observation WHERE sourceId='HEALTH'") suspend fun healthObservations():List<ObservationRow>
    @Query("SELECT * FROM episode WHERE kind IN ('STEPS','SLEEP','EXERCISE')") suspend fun healthEpisodes():List<EpisodeRow>
    @Query("DELETE FROM observation WHERE sourceId=:source AND startMs<:end AND endMs>=:start") suspend fun clearSourceRange(source: String, start: Long, end: Long)
    @Query("DELETE FROM observation WHERE id IN (:ids)") suspend fun deleteObservations(ids: List<String>)
    @Query("DELETE FROM observation WHERE (sourceId='NOTIFICATION' AND receivedMs<:noticeCutoff) OR receivedMs<:rawCutoff") suspend fun expireObservations(noticeCutoff: Long, rawCutoff: Long)
    @Query("SELECT COUNT(*) FROM observation WHERE startMs<:end AND endMs>=:start") suspend fun observationCount(start: Long, end: Long): Int
    @Query("SELECT * FROM episode WHERE endMs>=:start AND startMs<:end ORDER BY startMs LIMIT 10000") suspend fun episodes(start: Long, end: Long): List<EpisodeRow>
    @Query("SELECT * FROM episode WHERE id=:id") suspend fun episode(id: String): EpisodeRow?
    @Query("SELECT * FROM episode ORDER BY startMs LIMIT :limit OFFSET :offset") suspend fun allEpisodes(limit: Int, offset: Int): List<EpisodeRow>
    @Upsert suspend fun putEpisode(row: EpisodeRow)
    @Query("DELETE FROM episode WHERE id IN (:ids)") suspend fun deleteEpisodes(ids: List<String>)
    @Query("DELETE FROM event_search WHERE episodeId=:id") suspend fun removeIndex(id: String)
    @Insert suspend fun putIndex(row: SearchRow)
    @Query("SELECT episodeId FROM event_search WHERE tokens MATCH :tokens LIMIT 10000") suspend fun match(tokens: String): List<String>
    @Query("SELECT e.* FROM episode e JOIN event_search ON event_search.episodeId=e.id WHERE event_search.tokens MATCH :tokens AND e.startMs>=:start AND e.startMs<:end ORDER BY e.startMs DESC LIMIT :limit OFFSET :offset") suspend fun searchFts(tokens:String,start:Long,end:Long,limit:Int,offset:Int):List<EpisodeRow>
    @Query("SELECT * FROM episode WHERE startMs>=:start AND startMs<:end AND searchText LIKE :query ESCAPE '\\' ORDER BY startMs DESC LIMIT :limit OFFSET :offset") suspend fun search(query: String, start: Long, end: Long, limit: Int, offset: Int): List<EpisodeRow>
    @Upsert suspend fun putEvidence(rows: List<EvidenceRow>)
    @Query("DELETE FROM episode_evidence WHERE episodeId=:id") suspend fun clearEvidence(id: String)
    @Query("SELECT * FROM annotation ORDER BY createdMs") suspend fun annotations(): List<AnnotationRow>
    @Upsert suspend fun putAnnotation(row: AnnotationRow)
    @Query("DELETE FROM annotation WHERE episodeId IN (:ids)") suspend fun deleteAnnotations(ids: List<String>)
    @Query("DELETE FROM annotation WHERE id=:id") suspend fun undoAnnotation(id: String)
    @Query("SELECT * FROM place") suspend fun places(): List<PlaceRow>
    @Upsert suspend fun putPlace(row: PlaceRow)
    @Query("SELECT * FROM exclusion") suspend fun exclusions(): List<ExclusionRow>
    @Upsert suspend fun putExclusion(row: ExclusionRow)
    @Query("SELECT * FROM daily_summary WHERE date=:date AND zone=:zone") suspend fun summary(date: String, zone: String): SummaryRow?
    @Query("SELECT * FROM daily_summary ORDER BY date DESC LIMIT :limit") suspend fun summaries(limit: Int): List<SummaryRow>
    @Query("SELECT date FROM daily_summary WHERE zone=:zone ORDER BY date DESC") suspend fun summaryDates(zone:String):List<String>
    @Upsert suspend fun putSummary(row: SummaryRow)
    @Upsert suspend fun putMetrics(rows: List<MetricRow>)
    @Query("DELETE FROM daily_summary WHERE date>=:from AND date<=:to") suspend fun deleteSummaries(from: String, to: String)
    @Query("DELETE FROM daily_metric WHERE date>=:from AND date<=:to") suspend fun deleteMetrics(from: String, to: String)
    @Upsert suspend fun putCoverage(row: CoverageRow)
    @Query("SELECT * FROM source_coverage WHERE id=:id") suspend fun coverageById(id:String):CoverageRow?
    @Query("SELECT * FROM source_coverage WHERE startMs<:end AND endMs>:start") suspend fun coverage(start: Long, end: Long): List<CoverageRow>
    @Query("SELECT * FROM source_coverage") suspend fun allCoverage(): List<CoverageRow>
    @Upsert suspend fun putOperation(row: OperationRow)
    @Query("SELECT * FROM operation_log ORDER BY timeMs DESC LIMIT 30") suspend fun operations(): List<OperationRow>
    @Query("DELETE FROM operation_log WHERE timeMs<:cutoff") suspend fun expireLog(cutoff: Long)
    @Upsert suspend fun putDirty(row: DirtyRow)
    @Query("SELECT * FROM dirty_window ORDER BY startMs LIMIT 32") suspend fun dirty(): List<DirtyRow>
    @Query("DELETE FROM dirty_window WHERE id=:id") suspend fun clearDirty(id: String)
    @Query("DELETE FROM media_ref WHERE startMs<:end AND startMs>=:start") suspend fun deleteMedia(start: Long, end: Long)
    @Upsert suspend fun putMedia(row: MediaRow)
    @Query("DELETE FROM episode_relation WHERE parentId IN (:ids) OR childId IN (:ids)") suspend fun deleteRelations(ids: List<String>)
    @Upsert suspend fun putRelations(rows: List<RelationRow>)
    @Query("SELECT * FROM episode_relation") suspend fun relations(): List<RelationRow>
}

@Database(entities = [MetaRow::class, PolicyRow::class, SourceRow::class, ObservationRow::class, ExclusionRow::class, PlaceRow::class, EpisodeRow::class, EvidenceRow::class, RelationRow::class, AnnotationRow::class, MediaRow::class, MetricRow::class, SummaryRow::class, CoverageRow::class, SearchRow::class, OperationRow::class, DirtyRow::class], version = 1, exportSchema = true)
abstract class WoDatabase : RoomDatabase() { abstract fun dao(): WoDao }
