/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.time;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.index.mapper.RootObjectMapper;
import org.elasticsearch.test.ESTestCase;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

public class DateFormattersTests extends ESTestCase {

    // this is not in the duelling tests, because the epoch millis parser in joda time drops the milliseconds after the comma
    // but is able to parse the rest
    // as this feature is supported it also makes sense to make it exact
    public void testEpochMillisParser() {
        DateFormatter formatter = DateFormatters.forPattern("epoch_millis");
        {
            Instant instant = Instant.from(formatter.parse("12345.6789"));
            assertThat(instant.getEpochSecond(), is(12L));
            assertThat(instant.getNano(), is(345_678_900));
        }
        {
            Instant instant = Instant.from(formatter.parse("12345"));
            assertThat(instant.getEpochSecond(), is(12L));
            assertThat(instant.getNano(), is(345_000_000));
        }
        {
            Instant instant = Instant.from(formatter.parse("12345."));
            assertThat(instant.getEpochSecond(), is(12L));
            assertThat(instant.getNano(), is(345_000_000));
        }
        {
            Instant instant = Instant.from(formatter.parse("-12345.6789"));
            assertThat(instant.getEpochSecond(), is(-13L));
            assertThat(instant.getNano(), is(1_000_000_000 - 345_678_900));
        }
        {
            Instant instant = Instant.from(formatter.parse("-436134.241272"));
            assertThat(instant.getEpochSecond(), is(-437L));
            assertThat(instant.getNano(), is(1_000_000_000 - 134_241_272));
        }
        {
            Instant instant = Instant.from(formatter.parse("-12345"));
            assertThat(instant.getEpochSecond(), is(-13L));
            assertThat(instant.getNano(), is(1_000_000_000 - 345_000_000));
        }
        {
            Instant instant = Instant.from(formatter.parse("0"));
            assertThat(instant.getEpochSecond(), is(0L));
            assertThat(instant.getNano(), is(0));
        }
    }

    public void testInvalidEpochMilliParser() {
        DateFormatter formatter = DateFormatters.forPattern("epoch_millis");
        ElasticsearchParseException e = expectThrows(ElasticsearchParseException.class, () -> formatter.parse("invalid"));
        assertThat(e.getMessage(), is("invalid number [invalid]"));

        e = expectThrows(ElasticsearchParseException.class, () -> formatter.parse("123.1234567"));
        assertThat(e.getMessage(), containsString("too much granularity after dot [123.1234567]"));
    }

    // this is not in the duelling tests, because the epoch second parser in joda time drops the milliseconds after the comma
    // but is able to parse the rest
    // as this feature is supported it also makes sense to make it exact
    public void testEpochSecondParser() {
        DateFormatter formatter = DateFormatters.forPattern("epoch_second");

        assertThat(Instant.from(formatter.parse("1234.567")).toEpochMilli(), is(1234567L));
        assertThat(Instant.from(formatter.parse("1234.")).getNano(), is(0));
        assertThat(Instant.from(formatter.parse("1234.")).getEpochSecond(), is(1234L));
        assertThat(Instant.from(formatter.parse("1234.1")).getNano(), is(100_000_000));
        assertThat(Instant.from(formatter.parse("1234.12")).getNano(), is(120_000_000));
        assertThat(Instant.from(formatter.parse("1234.123")).getNano(), is(123_000_000));
        assertThat(Instant.from(formatter.parse("1234.1234")).getNano(), is(123_400_000));
        assertThat(Instant.from(formatter.parse("1234.12345")).getNano(), is(123_450_000));
        assertThat(Instant.from(formatter.parse("1234.123456")).getNano(), is(123_456_000));
        assertThat(Instant.from(formatter.parse("1234.1234567")).getNano(), is(123_456_700));
        assertThat(Instant.from(formatter.parse("1234.12345678")).getNano(), is(123_456_780));
        assertThat(Instant.from(formatter.parse("1234.123456789")).getNano(), is(123_456_789));

        assertThat(Instant.from(formatter.parse("-1234.567")).toEpochMilli(), is(-1234567L));
        assertThat(Instant.from(formatter.parse("-1234")).getNano(), is(0));

        ElasticsearchParseException e = expectThrows(ElasticsearchParseException.class, () -> formatter.parse("1234.1234567890"));
        assertThat(e.getMessage(), is("too much granularity after dot [1234.1234567890]"));
        e = expectThrows(ElasticsearchParseException.class, () -> formatter.parse("1234.123456789013221"));
        assertThat(e.getMessage(), is("too much granularity after dot [1234.123456789013221]"));
        e = expectThrows(ElasticsearchParseException.class, () -> formatter.parse("abc"));
        assertThat(e.getMessage(), is("invalid number [abc]"));
        e = expectThrows(ElasticsearchParseException.class, () -> formatter.parse("1234.abc"));
        assertThat(e.getMessage(), is("invalid number [1234.abc]"));
    }

    public void testEpochMilliParsersWithDifferentFormatters() {
        DateFormatter formatter = DateFormatters.forPattern("strict_date_optional_time||epoch_millis");
        TemporalAccessor accessor = formatter.parse("123");
        assertThat(DateFormatters.toZonedDateTime(accessor).toInstant().toEpochMilli(), is(123L));
        assertThat(formatter.pattern(), is("strict_date_optional_time||epoch_millis"));
    }

    public void testLocales() {
        assertThat(DateFormatters.forPattern("strict_date_optional_time").locale(), is(Locale.ROOT));
        Locale locale = randomLocale(random());
        assertThat(DateFormatters.forPattern("strict_date_optional_time").withLocale(locale).locale(), is(locale));
        if (locale.equals(Locale.ROOT)) {
            DateFormatter millisFormatter = DateFormatters.forPattern("epoch_millis");
            assertThat(millisFormatter.withLocale(locale), is(millisFormatter));
            DateFormatter secondFormatter = DateFormatters.forPattern("epoch_second");
            assertThat(secondFormatter.withLocale(locale), is(secondFormatter));
        } else {
            IllegalArgumentException e =
                expectThrows(IllegalArgumentException.class, () -> DateFormatters.forPattern("epoch_millis").withLocale(locale));
            assertThat(e.getMessage(), is("epoch_millis date formatter can only be in locale ROOT"));
            e = expectThrows(IllegalArgumentException.class, () -> DateFormatters.forPattern("epoch_second").withLocale(locale));
            assertThat(e.getMessage(), is("epoch_second date formatter can only be in locale ROOT"));
        }
    }

    public void testTimeZones() {
        // zone is null by default due to different behaviours between java8 and above
        assertThat(DateFormatters.forPattern("strict_date_optional_time").zone(), is(nullValue()));
        ZoneId zoneId = randomZone();
        assertThat(DateFormatters.forPattern("strict_date_optional_time").withZone(zoneId).zone(), is(zoneId));
        if (zoneId.equals(ZoneOffset.UTC)) {
            DateFormatter millisFormatter = DateFormatters.forPattern("epoch_millis");
            assertThat(millisFormatter.withZone(zoneId), is(millisFormatter));
            DateFormatter secondFormatter = DateFormatters.forPattern("epoch_second");
            assertThat(secondFormatter.withZone(zoneId), is(secondFormatter));
        } else {
            IllegalArgumentException e =
                expectThrows(IllegalArgumentException.class, () -> DateFormatters.forPattern("epoch_millis").withZone(zoneId));
            assertThat(e.getMessage(), is("epoch_millis date formatter can only be in zone offset UTC"));
            e = expectThrows(IllegalArgumentException.class, () -> DateFormatters.forPattern("epoch_second").withZone(zoneId));
            assertThat(e.getMessage(), is("epoch_second date formatter can only be in zone offset UTC"));
        }
    }

    public void testEqualsAndHashcode() {
        assertThat(DateFormatters.forPattern("strict_date_optional_time"),
            sameInstance(DateFormatters.forPattern("strict_date_optional_time")));
        assertThat(DateFormatters.forPattern("YYYY"), equalTo(DateFormatters.forPattern("YYYY")));
        assertThat(DateFormatters.forPattern("YYYY").hashCode(),
            is(DateFormatters.forPattern("YYYY").hashCode()));

        // different timezone, thus not equals
        assertThat(DateFormatters.forPattern("YYYY").withZone(ZoneId.of("CET")), not(equalTo(DateFormatters.forPattern("YYYY"))));

        // different locale, thus not equals
        DateFormatter f1 = DateFormatters.forPattern("YYYY").withLocale(Locale.CANADA);
        DateFormatter f2 = f1.withLocale(Locale.FRENCH);
        assertThat(f1, not(equalTo(f2)));

        // different pattern, thus not equals
        assertThat(DateFormatters.forPattern("YYYY"), not(equalTo(DateFormatters.forPattern("YY"))));

        DateFormatter epochSecondFormatter = DateFormatters.forPattern("epoch_second");
        assertThat(epochSecondFormatter, sameInstance(DateFormatters.forPattern("epoch_second")));
        assertThat(epochSecondFormatter, equalTo(DateFormatters.forPattern("epoch_second")));
        assertThat(epochSecondFormatter.hashCode(), is(DateFormatters.forPattern("epoch_second").hashCode()));

        DateFormatter epochMillisFormatter = DateFormatters.forPattern("epoch_millis");
        assertThat(epochMillisFormatter.hashCode(), is(DateFormatters.forPattern("epoch_millis").hashCode()));
        assertThat(epochMillisFormatter, sameInstance(DateFormatters.forPattern("epoch_millis")));
        assertThat(epochMillisFormatter, equalTo(DateFormatters.forPattern("epoch_millis")));
    }

    public void testThatRootObjectParsingIsStrict() {
        String[] datesThatWork = new String[] { "2014/10/10", "2014/10/10 12:12:12", "2014-05-05",  "2014-05-05T12:12:12.123Z" };
        String[] datesThatShouldNotWork = new String[]{ "5-05-05", "2014-5-05", "2014-05-5",
                "2014-05-05T1:12:12.123Z", "2014-05-05T12:1:12.123Z", "2014-05-05T12:12:1.123Z",
                "4/10/10", "2014/1/10", "2014/10/1",
                "2014/10/10 1:12:12", "2014/10/10 12:1:12", "2014/10/10 12:12:1"
        };

        // good case
        for (String date : datesThatWork) {
            boolean dateParsingSuccessful = false;
            for (DateFormatter dateTimeFormatter : RootObjectMapper.Defaults.DYNAMIC_DATE_TIME_FORMATTERS) {
                try {
                    dateTimeFormatter.parse(date);
                    dateParsingSuccessful = true;
                    break;
                } catch (Exception e) {}
            }
            if (!dateParsingSuccessful) {
                fail("Parsing for date " + date + " in root object mapper failed, but shouldnt");
            }
        }

        // bad case
        for (String date : datesThatShouldNotWork) {
            for (DateFormatter dateTimeFormatter : RootObjectMapper.Defaults.DYNAMIC_DATE_TIME_FORMATTERS) {
                expectThrows(Exception.class, () -> dateTimeFormatter.parse(date));
            }
        }
    }
}