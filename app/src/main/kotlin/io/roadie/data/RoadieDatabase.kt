package io.roadie.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import io.roadie.innertube.VideoItem
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channel: String,
    val durationText: String,
    val viewCountText: String,
    val thumbnailUrl: String,
    val watchedAtMs: Long,
    val lastPositionMs: Long = 0L
) {
    fun toVideoItem(): VideoItem = VideoItem(
        id = videoId,
        title = title,
        channel = channel,
        durationText = durationText,
        viewCountText = viewCountText,
        thumbnailUrl = thumbnailUrl
    )
}

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelName: String,
    val subscribedAtMs: Long
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channel: String,
    val durationText: String,
    val viewCountText: String,
    val thumbnailUrl: String,
    val savedAtMs: Long
) {
    fun toVideoItem(): VideoItem = VideoItem(
        id = videoId,
        title = title,
        channel = channel,
        durationText = durationText,
        viewCountText = viewCountText,
        thumbnailUrl = thumbnailUrl
    )
}

@Dao
interface RoadieDao {
    @Query("SELECT * FROM watch_history ORDER BY watchedAtMs DESC LIMIT 60")
    fun observeHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT lastPositionMs FROM watch_history WHERE videoId = :videoId LIMIT 1")
    suspend fun getLastPosition(videoId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: WatchHistoryEntity)

    @Query("UPDATE watch_history SET lastPositionMs = :positionMs, watchedAtMs = :watchedAtMs WHERE videoId = :videoId")
    suspend fun updatePosition(videoId: String, positionMs: Long, watchedAtMs: Long)

    @Query("DELETE FROM watch_history")
    suspend fun clearHistory()

    @Query("SELECT * FROM subscriptions ORDER BY subscribedAtMs DESC")
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun subscribe(entry: SubscriptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun subscribeAll(entries: List<SubscriptionEntity>)

    @Query("DELETE FROM subscriptions WHERE channelName = :channelName")
    suspend fun unsubscribe(channelName: String)

    @Query("SELECT * FROM bookmarks ORDER BY savedAtMs DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(entry: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE videoId = :videoId")
    suspend fun deleteBookmark(videoId: String)
}

@Database(
    entities = [WatchHistoryEntity::class, SubscriptionEntity::class, BookmarkEntity::class],
    version = 3,
    exportSchema = false
)
abstract class RoadieDatabase : RoomDatabase() {
    abstract fun dao(): RoadieDao

    companion object {
        @Volatile
        private var instance: RoadieDatabase? = null

        fun get(context: Context): RoadieDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RoadieDatabase::class.java,
                    "roadie.db"
                ).fallbackToDestructiveMigration(true).build().also { instance = it }
            }
    }
}
