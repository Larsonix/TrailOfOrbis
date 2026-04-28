package io.github.larsonix.trailoforbis.lootfilter.repository;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.Instant;

/**
 * Gson TypeAdapter that serializes {@link Instant} as epoch milliseconds.
 *
 * <p>Without this adapter, Gson attempts reflective access to Instant's private
 * fields ({@code seconds}, {@code nanos}), which the Java module system blocks,
 * causing an {@code InaccessibleObjectException} on every save.
 */
final class InstantTypeAdapter extends TypeAdapter<Instant> {

    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toEpochMilli());
        }
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        return Instant.ofEpochMilli(in.nextLong());
    }
}
