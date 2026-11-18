package com.kuriousdragon.smartindex.source.recordings.parse

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.time.LocalDateTime
import org.junit.Test

class RecordingNameParserTest {
    private val parser = RecordingNameParser()

    private fun col(s: String): String? = s.takeIf { it != "-" }

    @Test
    fun `every row of the sample names corpus parses as specified`() {
        val file = File(System.getProperty("sidx.testdata"), "recordings/sample-names.tsv")
        val lines = file.readLines(Charsets.UTF_8).filter { it.isNotBlank() && !it.startsWith("#") }
        assertThat(lines.first()).isEqualTo("file_name\tpattern_id\tname\tnumber\tlocal_datetime\tis_copy")
        val rows = lines.drop(1)
        assertThat(rows).isNotEmpty()
        for (line in rows) {
            val c = line.split('\t')
            assertThat(c).hasSize(6)
            val fileName = c[0]
            val actual = parser.parse(fileName)
            val patternId = col(c[1])
            if (patternId == null) {
                assertWithMessage("negative: $fileName").that(actual).isNull()
            } else {
                val expected = ParsedRecordingName(
                    patternId = patternId,
                    name = col(c[2]),
                    number = col(c[3]),
                    localDateTime = LocalDateTime.parse(c[4]),
                    isCopy = c[5] == "yes",
                )
                assertWithMessage(fileName).that(actual).isEqualTo(expected)
            }
        }
    }

    @Test
    fun `name with number uses the number row not the plain row`() {
        assertThat(parser.parse("Rahul_9876543210_241103_094215.m4a")?.patternId)
            .isEqualTo("who_number_yyMMdd_HHmmss")
    }

    @Test
    fun `empty and over-long names return null`() {
        assertThat(parser.parse("")).isNull()
        assertThat(parser.parse("a".repeat(250) + "_241103_094215.m4a")).isNull()
        assertThat(parser.parse("a".repeat(256))).isNull()
    }

    @Test
    fun `a custom one-row table replaces the default table`() {
        val custom = RecordingNameParser(
            listOf(
                RecordingNamePattern(
                    id = "custom",
                    regex = Regex("""REC-(?<who>[A-Za-z]+)-(?<date>\d{8})-(?<time>\d{6})\.(?<ext>m4a)"""),
                    dateDigits = 8,
                    example = "REC-Rahul-20241103-094215.m4a",
                ),
            ),
        )
        val parsed = custom.parse("REC-Rahul-20241103-094215.m4a")
        assertThat(parsed?.patternId).isEqualTo("custom")
        assertThat(parsed?.name).isEqualTo("Rahul")
        assertThat(parsed?.localDateTime).isEqualTo(LocalDateTime.of(2024, 11, 3, 9, 42, 15))
        assertThat(custom.parse("Call recording Rahul_241103_094215.m4a")).isNull()
    }
}
