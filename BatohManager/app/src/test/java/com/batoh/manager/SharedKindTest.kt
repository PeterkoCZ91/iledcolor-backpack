package com.batoh.manager

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedKindTest {
    @Test fun videoTypesGoToVideoFlow() {
        assertEquals(SharedKind.Video, SharedKind.of("video/mp4"))
        assertEquals(SharedKind.Video, SharedKind.of("VIDEO/webm"))
    }

    @Test fun pngAndJpegGoToImageFlow() {
        assertEquals(SharedKind.Image, SharedKind.of("image/png"))
        assertEquals(SharedKind.Image, SharedKind.of("image/jpeg; charset=binary"))
    }

    @Test fun gifAndUnknownKeepGifPath() {
        assertEquals(SharedKind.Gif, SharedKind.of("image/gif"))
        assertEquals(SharedKind.Gif, SharedKind.of("image/webp"))
        assertEquals(SharedKind.Gif, SharedKind.of(null))
    }
}
