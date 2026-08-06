package wtf.oraculus.utility.data.serializer;

import com.google.gson.*;
import com.ibm.icu.impl.Pair;


import java.lang.reflect.Type;


public final class PairSerializer implements JsonSerializer<Pair<Float, Float>>, JsonDeserializer<Pair<Float, Float>> {

    @Override
    public JsonElement serialize(Pair<Float, Float> src, Type typeOfSrc, JsonSerializationContext context) {
        final JsonObject object = new JsonObject();
        object.addProperty("x", src.first);
        object.addProperty("y", src.second);
        return object;
    }

    @Override
    public Pair<Float, Float> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        final JsonObject object = json.getAsJsonObject();
        return Pair.of(
                object.get("x").getAsFloat(),
                object.get("y").getAsFloat()
        );
    }

}
