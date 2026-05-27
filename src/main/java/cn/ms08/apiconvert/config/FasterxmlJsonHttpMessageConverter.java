package cn.ms08.apiconvert.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.AbstractJsonHttpMessageConverter;

import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Type;

/**
 * JSON converter backed by the project's existing Jackson 2 ObjectMapper.
 */
public class FasterxmlJsonHttpMessageConverter extends AbstractJsonHttpMessageConverter {

    private final ObjectMapper objectMapper;

    public FasterxmlJsonHttpMessageConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean canRead(Class<?> clazz, MediaType mediaType) {
        return !isBinaryType(clazz) && super.canRead(clazz, mediaType);
    }

    @Override
    public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
        return !isBinaryType(type) && super.canRead(type, contextClass, mediaType);
    }

    @Override
    public boolean canWrite(Class<?> clazz, MediaType mediaType) {
        return !isNativeScalarType(clazz) && super.canWrite(clazz, mediaType);
    }

    @Override
    public boolean canWrite(Type type, Class<?> clazz, MediaType mediaType) {
        return !isNativeScalarType(type) && super.canWrite(type, clazz, mediaType);
    }

    @Override
    protected Object readInternal(Type resolvedType, Reader reader) throws Exception {
        if (isStringType(resolvedType)) {
            return readRawBody(reader);
        }
        return objectMapper.readerFor(javaType(resolvedType)).readValue(reader);
    }

    @Override
    protected void writeInternal(Object object, Type resolvedType, Writer writer) throws Exception {
        JavaType javaType = javaType(resolvedType);
        objectMapper.writerFor(javaType).writeValue(writer, object);
    }

    private JavaType javaType(Type type) {
        return objectMapper.constructType(type != null ? type : Object.class);
    }

    private static String readRawBody(Reader reader) throws Exception {
        StringWriter writer = new StringWriter();
        reader.transferTo(writer);
        return writer.toString();
    }

    private static boolean isBinaryType(Type type) {
        return type instanceof Class<?> clazz && isBinaryType(clazz);
    }

    private static boolean isBinaryType(Class<?> clazz) {
        return byte[].class == clazz;
    }

    private static boolean isNativeScalarType(Class<?> clazz) {
        return CharSequence.class.isAssignableFrom(clazz) || byte[].class == clazz;
    }

    private static boolean isNativeScalarType(Type type) {
        return type instanceof Class<?> clazz && isNativeScalarType(clazz);
    }

    private static boolean isStringType(Type type) {
        return type instanceof Class<?> clazz && CharSequence.class.isAssignableFrom(clazz);
    }
}
