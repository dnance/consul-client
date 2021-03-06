package com.orbitz.consul;

import com.google.common.base.Optional;
import com.orbitz.consul.async.ConsulResponseCallback;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.model.session.ImmutableSession;
import com.orbitz.consul.model.session.SessionCreatedResponse;
import com.orbitz.consul.option.QueryOptions;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeyValueTests {

    @Test
    public void shouldPutAndReceiveString() throws UnknownHostException {
        Consul client = Consul.builder().build();
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();

        assertTrue(keyValueClient.putValue(key, value));
        assertEquals(value, keyValueClient.getValueAsString(key).get());
    }

    @Test
    public void shouldPutAndReceiveValue() throws UnknownHostException {
        Consul client = Consul.builder().build();
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();

        assertTrue(keyValueClient.putValue(key, value));
        Value received = keyValueClient.getValue(key).get();
        assertEquals(value, received.getValueAsString().get());
        assertEquals(0L, received.getFlags());
        keyValueClient.deleteKey(key);

    }

    @Test
    public void shouldPutAndReceiveWithFlags() throws UnknownHostException {
        Consul client = Consul.builder().build();
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        long flags = UUID.randomUUID().getMostSignificantBits();

        assertTrue(keyValueClient.putValue(key, value, flags));
        Value received = keyValueClient.getValue(key).get();
        assertEquals(value, received.getValueAsString().get());
        assertEquals(flags, received.getFlags());
        keyValueClient.deleteKey(key);

    }

    @Test
    public void putNullValue() {

        Consul client = Consul.builder().build();
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = UUID.randomUUID().toString();

        assertTrue(keyValueClient.putValue(key));

        Value received = keyValueClient.getValue(key).get();
        assertFalse(received.getValue().isPresent());

        keyValueClient.deleteKey(key);
    }

    @Test
    public void shouldPutAndReceiveStrings() throws UnknownHostException {
        Consul client = Consul.builder().build();
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = UUID.randomUUID().toString();
        String key2 = key + "/" + UUID.randomUUID().toString();
        final String value = UUID.randomUUID().toString();
        final String value2 = UUID.randomUUID().toString();

        assertTrue(keyValueClient.putValue(key, value));
        assertTrue(keyValueClient.putValue(key2, value2));
        assertEquals(new HashSet<String>() {
            {
                add(value);
                add(value2);
            }
        }, new HashSet<String>(keyValueClient.getValuesAsString(key)));

        keyValueClient.deleteKey(key);
        keyValueClient.deleteKey(key2);

    }

    @Test
    public void shouldDelete() throws Exception {
        Consul client = Consul.builder().build();
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = UUID.randomUUID().toString();
        final String value = UUID.randomUUID().toString();

        keyValueClient.putValue(key, value);

        assertTrue(keyValueClient.getValueAsString(key).isPresent());

        keyValueClient.deleteKey(key);

        assertFalse(keyValueClient.getValueAsString(key).isPresent());
    }


    @Test
    public void shouldDeleteRecursively() throws Exception {

        Consul client = Consul.builder().build();
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = UUID.randomUUID().toString();
        String childKEY = key + "/" + UUID.randomUUID().toString();

        final String value = UUID.randomUUID().toString();

        keyValueClient.putValue(key);
        keyValueClient.putValue(childKEY, value);

        assertTrue(keyValueClient.getValueAsString(childKEY).isPresent());

        keyValueClient.deleteKeys(key);

        assertFalse(keyValueClient.getValueAsString(key).isPresent());
        assertFalse(keyValueClient.getValueAsString(childKEY).isPresent());

    }

    @Test
    public void acquireAndReleaseLock() throws Exception {
        Consul client = Consul.builder().build();
        KeyValueClient keyValueClient = client.keyValueClient();
        SessionClient sessionClient = client.sessionClient();
        String key = UUID.randomUUID().toString();
        String value = "session_" + UUID.randomUUID().toString();
        SessionCreatedResponse response = sessionClient.createSession(ImmutableSession.builder().name(value).build());

        String sessionId = response.getId();

        assertTrue(keyValueClient.acquireLock(key, value, sessionId));
        assertFalse(keyValueClient.acquireLock(key, value, sessionId));

        assertTrue("SessionId must be present.", keyValueClient.getValue(key).get().getSession().isPresent());
        assertTrue(keyValueClient.releaseLock(key, sessionId));
        assertFalse("SessionId in the key value should be absent.", keyValueClient.getValue(key).get().getSession().isPresent());
        keyValueClient.deleteKey(key);

    }

    @Test
    public void testGetSession() throws Exception {
        Consul client = Consul.builder().build();
        KeyValueClient keyValueClient = client.keyValueClient();
        SessionClient sessionClient = client.sessionClient();

        String key = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        keyValueClient.putValue(key, value);

        assertEquals(false, keyValueClient.getSession(key).isPresent());

        String sessionValue = "session_" + UUID.randomUUID().toString();
        SessionCreatedResponse response = sessionClient.createSession(ImmutableSession.builder().name(sessionValue).build());

        String sessionId = response.getId();

        assertTrue(keyValueClient.acquireLock(key, sessionValue, sessionId));
        assertFalse(keyValueClient.acquireLock(key, sessionValue, sessionId));
        assertEquals(sessionId, keyValueClient.getSession(key).get());
        keyValueClient.deleteKey(key);

    }

    @Test
    public void testGetValuesAsync() throws InterruptedException {
        Consul client = Consul.builder().build();
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = UUID.randomUUID().toString();
        String value = UUID.randomUUID().toString();
        keyValueClient.putValue(key, value);

        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        keyValueClient.getValues(key, QueryOptions.BLANK, new ConsulResponseCallback<List<Value>>() {
            @Override
            public void onComplete(ConsulResponse<List<Value>> consulResponse) {
                success.set(true);
                completed.countDown();
            }

            @Override
            public void onFailure(Throwable throwable) {
                throwable.printStackTrace();
                completed.countDown();
            }
        });
        completed.await(3, TimeUnit.SECONDS);
        keyValueClient.deleteKey(key);
        assertTrue(success.get());
    }

    @Test
    public void testGetValueNotFoundAsync() throws InterruptedException {
        Consul client = Consul.builder().build();
        KeyValueClient keyValueClient = client.keyValueClient();
        String key = UUID.randomUUID().toString();


        final CountDownLatch completed = new CountDownLatch(1);
        final AtomicBoolean success = new AtomicBoolean(false);

        keyValueClient.getValue(key, QueryOptions.BLANK, new ConsulResponseCallback<Optional<Value>>() {

            @Override
            public void onComplete(ConsulResponse<Optional<Value>> consulResponse) {

                success.set(!consulResponse.getResponse().isPresent());
                completed.countDown();
            }

            @Override
            public void onFailure(Throwable throwable) {
                throwable.printStackTrace();
                completed.countDown();
            }
        });

        completed.await(3, TimeUnit.SECONDS);
        keyValueClient.deleteKey(key);
        assertTrue(success.get());

    }
}
