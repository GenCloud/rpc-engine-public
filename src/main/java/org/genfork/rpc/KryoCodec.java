package org.genfork.rpc;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import com.esotericsoftware.kryo.util.Pool;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.io.ByteArrayInputStream;

/**
 * @author: GenCloud
 * @date: 2022/03
 */
@UtilityClass
@Slf4j
public class KryoCodec {
    private final Pool<Kryo> kryoPool;
    private final Pool<Input> inputPool;
    private final Pool<Output> outputPool;

    static {
        kryoPool = new Pool<>(true, false, 1024) {
            @Override
            protected Kryo create() {
                final Kryo kryo = new Kryo();
                kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
                kryo.setRegistrationRequired(false);
                kryo.setReferences(false);
                kryo.addDefaultSerializer(Throwable.class, new JavaSerializer());
                return kryo;
            }
        };

        inputPool = new Pool<>(true, false, 512) {
            @Override
            protected Input create() {
                return new Input(8192);
            }
        };

        outputPool = new Pool<>(true, false, 512) {
            @Override
            protected Output create() {
                return new Output(8192, -1);
            }
        };
    }

    public <T> byte[] write(T in) {
        final Kryo kryo = kryoPool.obtain();
        final Output output = outputPool.obtain();

        try {
            kryo.writeClassAndObject(output, in);
            output.flush();

            return output.toBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            kryoPool.free(kryo);
            outputPool.free(output);
        }
    }

    public <T> T read(byte[] value, Class<T> type) {
        final Kryo kryo = kryoPool.obtain();
        final Input input = inputPool.obtain();
        try {
            input.setInputStream(new ByteArrayInputStream(value));

            final Object object = kryo.readClassAndObject(input);
            return type.cast(object);
        } finally {
            kryoPool.free(kryo);
            inputPool.free(input);
        }
    }
}
