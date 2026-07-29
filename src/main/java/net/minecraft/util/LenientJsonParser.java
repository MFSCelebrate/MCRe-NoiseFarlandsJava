package net.minecraft.util;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.Reader;

public class LenientJsonParser {
    public static JsonElement parse(final Reader reader) throws JsonIOException, JsonSyntaxException {
        return JsonParser.parseReader(reader);
    }

    public static JsonElement parse(final String json) throws JsonSyntaxException {
        return JsonParser.parseString(json);
    }
}