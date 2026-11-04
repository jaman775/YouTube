package com.drishyopot.app.sponsorblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockParseTest {
    private val json = """
        [
          {"videoID":"dQw4w9WgXcQ","hash":"abcd","segments":[
            {"segment":[10.5,20.0],"UUID":"u1","category":"sponsor","actionType":"skip","videoDuration":212.0},
            {"segment":[0.0,5.0],"UUID":"u2","category":"intro","actionType":"skip","videoDuration":212.0},
            {"segment":[90.0,90.0],"UUID":"u3","category":"poi_highlight","actionType":"poi","videoDuration":212.0},
            {"segment":[30.0,30.0],"UUID":"bad","category":"sponsor","actionType":"skip"},
            {"segment":[1.0,2.0],"UUID":"x","category":"unknown_cat","actionType":"skip"}
          ]},
          {"videoID":"otherVideo11","hash":"abcd","segments":[
            {"segment":[1.0,2.0],"UUID":"o1","category":"sponsor","actionType":"skip"}
          ]}
        ]
    """.trimIndent()

    @Test fun parsesOnlyMatchingVideoAndValidSegments() {
        val result = SponsorBlockApi.parse(json, "dQw4w9WgXcQ")
        assertEquals(listOf("u2", "u1", "u3"), result.map { it.uuid })
        assertEquals(10_500L, result.first { it.uuid == "u1" }.startMs)
        assertEquals(20_000L, result.first { it.uuid == "u1" }.endMs)
        assertEquals(212_000L, result.first { it.uuid == "u1" }.videoDurationMs)
    }

    @Test fun poiKeepsZeroLength() {
        val poi = SponsorBlockApi.parse(json, "dQw4w9WgXcQ").first { it.uuid == "u3" }
        assertTrue(poi.isPoi)
        assertEquals(poi.startMs, poi.endMs)
    }

    @Test fun emptyArrayGivesNoSegments() {
        assertTrue(SponsorBlockApi.parse("[]", "dQw4w9WgXcQ").isEmpty())
    }

    @Test fun everyCategoryHasUniqueApiName() {
        assertEquals(SbCategory.entries.size, SbCategory.entries.map { it.api }.toSet().size)
    }
}
