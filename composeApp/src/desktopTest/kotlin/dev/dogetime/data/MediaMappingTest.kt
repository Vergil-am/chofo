package dev.dogetime.data

import dev.dogetime.domain.MediaType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The TMDB and Kitsu DTO → domain mapping in `MediaRepository.kt`, fed with API-shaped JSON. */
class MediaMappingTest {
    private val json = Json { ignoreUnknownKeys = true }
    private inline fun <reified T> decode(text: String): T = json.decodeFromString(text)

    @Test
    fun movieDetails() {
        val details = decode<TmdbDetails>(
            """
            {
              "id": 603, "title": "The Matrix", "overview": "A hacker learns the truth.",
              "tagline": "", "status": "Released", "runtime": 136,
              "genres": [{"id": 28, "name": "Action"}, {"id": 878, "name": "Science Fiction"}],
              "poster_path": "/poster.jpg", "backdrop_path": "/backdrop.jpg",
              "release_date": "1999-03-31", "vote_average": 8.2, "vote_count": 25000,
              "imdb_id": "tt0133093",
              "credits": {"cast": [{"name": "Keanu Reeves", "character": "Neo", "profile_path": "/keanu.jpg"},
                                   {"name": "Extra", "profile_path": null}]},
              "recommendations": {"page": 1, "total_pages": 1, "results": [
                {"id": 604, "title": "The Matrix Reloaded", "media_type": "movie"},
                {"id": 1399, "name": "Some Show", "media_type": "tv"}
              ]},
              "similar": {"page": 1, "total_pages": 1, "results": [
                {"id": 604, "title": "The Matrix Reloaded"},
                {"id": 605, "title": "The Matrix Revolutions"}
              ]},
              "images": {"logos": [
                {"file_path": "/fr.png", "iso_639_1": "fr", "width": 2000},
                {"file_path": "/en-small.png", "iso_639_1": "en", "width": 300},
                {"file_path": "/en-big.png", "iso_639_1": "en", "width": 900}
              ]}
            }
            """,
        ).toDomain(MediaType.Movie)

        with(details.item) {
            assertEquals(603, id)
            assertEquals(MediaType.Movie, type)
            assertEquals("The Matrix", title)
            assertEquals("https://image.tmdb.org/t/p/w342/poster.jpg", posterUrl)
            assertEquals("https://image.tmdb.org/t/p/w780/backdrop.jpg", backdropUrl)
            assertEquals("1999", year)
            assertEquals(8.2, rating)
        }
        assertNull(details.tagline, "a blank tagline is dropped")
        assertEquals("136 min", details.runtime)
        assertEquals(listOf("Action", "Science Fiction"), details.genres)
        assertNull(details.seasonCount)
        assertEquals(emptyList(), details.seasons)
        assertEquals("tt0133093", details.imdbId)
        assertEquals("https://image.tmdb.org/t/p/w500/en-big.png", details.logoUrl)

        assertEquals("Neo", details.cast[0].character)
        assertEquals("https://image.tmdb.org/t/p/w185/keanu.jpg", details.cast[0].photoUrl)
        assertEquals("", details.cast[1].character)
        assertNull(details.cast[1].photoUrl)

        assertEquals(listOf(MediaType.Movie, MediaType.Tv), details.recommendations.map { it.type })
        // Similar titles skip what is already recommended, and take the parent's type.
        assertEquals(listOf(605), details.similar.map { it.id })
        assertEquals(MediaType.Movie, details.similar.single().type)
    }

    @Test
    fun tvDetails() {
        val details = decode<TmdbDetails>(
            """
            {
              "id": 1399, "name": "Some Show", "first_air_date": "2011-04-17",
              "episode_run_time": [55, 60], "number_of_seasons": 3, "number_of_episodes": 20,
              "external_ids": {"imdb_id": "tt0944947"},
              "seasons": [
                {"id": 10, "season_number": 0, "name": "Specials", "episode_count": 4},
                {"id": 12, "season_number": 2, "episode_count": 10, "air_date": ""},
                {"id": 11, "season_number": 1, "name": "Season 1", "episode_count": 10, "air_date": "2011-04-17"},
                {"id": 13, "season_number": 3, "episode_count": 0}
              ]
            }
            """,
        ).toDomain(MediaType.Tv)

        assertEquals("Some Show", details.item.title)
        assertEquals("2011", details.item.year)
        assertEquals("", details.item.overview)
        assertNull(details.item.posterUrl)
        assertEquals("55 min per episode", details.runtime)
        assertEquals(3, details.seasonCount)
        assertEquals(20, details.episodeCount)
        assertEquals("tt0944947", details.imdbId)
        // Empty seasons are dropped and Specials go last.
        assertEquals(listOf(1, 2, 0), details.seasons.map { it.number })
        assertEquals("Season 2", details.seasons[1].name)
        assertNull(details.seasons[1].airDate, "a blank air date is dropped")
        assertNull(details.logoUrl)
    }

    @Test
    fun untitledMediaAndMissingDates() {
        val item = decode<TmdbMedia>("""{"id": 1, "release_date": ""}""").toMediaItem(MediaType.Movie)
        assertEquals("Untitled", item.title)
        assertNull(item.year)
    }

    @Test
    fun episode() {
        val episode = decode<TmdbEpisode>(
            """{"id": 99, "episode_number": 4, "season_number": 2, "still_path": "/still.jpg", "runtime": 42, "vote_average": 7.5}""",
        ).toDomain()
        assertEquals(4, episode.number)
        assertEquals(2, episode.seasonNumber)
        assertEquals("Episode 4", episode.name)
        assertEquals("https://image.tmdb.org/t/p/w300/still.jpg", episode.stillUrl)
        assertEquals("42 min", episode.runtime)
        assertEquals(7.5, episode.rating)
    }

    @Test
    fun kitsuAnime() {
        val item = decode<KitsuAnimeResource>(
            """
            {
              "id": "7442",
              "attributes": {
                "canonicalTitle": "Attack on Titan", "synopsis": "Walls.",
                "averageRating": "84.5", "startDate": "2013-04-07",
                "posterImage": {"medium": "m.jpg", "large": "l.jpg"},
                "coverImage": null
              }
            }
            """,
        ).toMediaItem()!!

        assertEquals(7442, item.id)
        assertEquals(MediaType.Anime, item.type)
        assertEquals("Attack on Titan", item.title)
        assertEquals(8.45, item.rating, 1e-9)
        assertEquals("2013", item.year)
        assertEquals("l.jpg", item.posterUrl)
        assertEquals("l.jpg", item.backdropUrl, "the poster stands in for a missing cover")
    }

    @Test
    fun kitsuFallbacks() {
        val fromTitles = decode<KitsuAnimeResource>(
            """{"id": "1", "attributes": {"titles": {"en": null, "en_jp": "Shingeki"}, "description": "D"}}""",
        ).toMediaItem()!!
        assertEquals("Shingeki", fromTitles.title)
        assertEquals("D", fromTitles.overview)
        assertEquals(0.0, fromTitles.rating)

        assertNull(decode<KitsuAnimeResource>("""{"id": "x", "attributes": {"canonicalTitle": "A"}}""").toMediaItem())
        assertNull(decode<KitsuAnimeResource>("""{"id": "2", "attributes": {}}""").toMediaItem())
    }
}
