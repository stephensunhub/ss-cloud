package men.sbb8.web.starter.redis;

import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 写入前把最外层的 JDK 内部集合（{@code Map.of}、{@code List.of}、{@code Set.of}、{@code Collections.singletonMap} 等）
 * 换成普通集合。
 *
 * <p>类型信息只写在非 final 类型上，而这些 JDK 内部类大多是 final 的，最外层的值写出来没有 {@code @class}，读回来必然失败
 * （{@code missing type id property '@class'}）；{@code Collections.unmodifiableMap} 则会读成带 {@code @class} 键的错误数据。
 * 嵌套在对象或集合里的不受影响（声明类型是 Object，会写类型信息），所以只处理最外层。
 *
 * @author Stephen
 */
final class RootCollectionNormalizingSerializer implements RedisSerializer<Object> {

    private final RedisSerializer<Object> delegate;

    RootCollectionNormalizingSerializer(RedisSerializer<Object> delegate) {
        this.delegate = delegate;
    }

    @Override
    public byte @Nullable [] serialize(@Nullable Object value) throws SerializationException {
        return delegate.serialize(normalize(value));
    }

    @Override
    public @Nullable Object deserialize(byte @Nullable [] bytes) throws SerializationException {
        return delegate.deserialize(bytes);
    }

    static @Nullable Object normalize(@Nullable Object value) {
        if (value == null || !isJdkInternal(value.getClass())) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(map);
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Set<?> set) {
            return new LinkedHashSet<>(set);
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return value;
    }

    private static boolean isJdkInternal(Class<?> type) {
        String name = type.getName();
        return name.startsWith("java.util.ImmutableCollections$") || name.startsWith("java.util.Collections$");
    }
}
