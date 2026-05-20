package nus.edu.u.common.jackson.desensitize;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import java.io.IOException;
import java.util.Objects;

/** Jackson serializer for desensitization */
public class DesensitizeSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private DesensitizeType type;

    public DesensitizeSerializer() {}

    public DesensitizeSerializer(DesensitizeType type) {
        this.type = type;
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value == null || type == null) {
            gen.writeString(value);
            return;
        }
        gen.writeString(type.desensitize(value));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
            throws JsonMappingException {
        if (property == null) {
            return prov.findNullValueSerializer(null);
        }
        if (Objects.equals(property.getType().getRawClass(), String.class)) {
            Desensitize desensitize = property.getAnnotation(Desensitize.class);
            if (desensitize == null) {
                desensitize = property.getContextAnnotation(Desensitize.class);
            }
            if (desensitize != null) {
                return new DesensitizeSerializer(desensitize.type());
            }
        }
        return prov.findValueSerializer(property.getType(), property);
    }
}
