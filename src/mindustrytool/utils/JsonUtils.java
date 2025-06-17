package mindustrytool.utils;

import java.io.IOException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import mindustrytool.type.NotMessageException;

public class JsonUtils {
    public static ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    public static String toJsonString(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (IOException e) {
            throw new RuntimeException("Can not parse to json: " + e.getMessage(), e);
        }
    }

    public static <T> T readJsonAsClass(String data, Class<T> clazz) {
        try {
            return objectMapper.readValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Can not parse to json: " + e.getMessage(), e);
        }
    }
    public static <T> T readJsonAsClass(String data, TypeReference<T> clazz) {
        try {
            return objectMapper.readValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Can not parse to json: " + e.getMessage(), e);
        }
    }

    public static JsonNode readJson(String data) {
        try {
            return objectMapper.readTree(data);
        } catch (Exception e) {
            throw new RuntimeException("Can not parse to json: " + e.getMessage(), e);
        }
    }

    public static JsonNode readMessage(String data) {
        try {
            var node = objectMapper.readTree(data);

            if (!node.hasNonNull("id") || !node.hasNonNull("type") || !node.hasNonNull("method")) {
                throw new NotMessageException();
            }

            return node;
        } catch (Exception e) {
            throw new NotMessageException();
        }
    }
}
