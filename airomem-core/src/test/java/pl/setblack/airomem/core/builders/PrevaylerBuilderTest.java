/*
 *  Copyright (c) Jarek Ratajski, Licensed under the Apache License, Version 2.0
 *  http://www.apache.org/licenses/LICENSE-2.0
 */
package pl.setblack.airomem.core.builders;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import pl.setblack.airomem.core.PersistenceController;
import pl.setblack.airomem.core.RestoreException;
import pl.setblack.airomem.core.StorableObject;
import pl.setblack.airomem.core.VoidCommand;
import pl.setblack.airomem.core.disk.PersistenceDiskHelper;
import pl.setblack.badass.Politician;

/**
 *
 * @author jarek ratajski
 */
public class PrevaylerBuilderTest {

    private static final AtomicReference<Boolean> failureMarker = new AtomicReference<>(Boolean.FALSE);

    private static boolean isFailureNeeded() {
        return failureMarker.get();
    }

    public PrevaylerBuilderTest() {
    }

    @Before
    public void setUp() {
        deletePrevaylerFolder();
        failureMarker.set(Boolean.FALSE);

    }

    private void deletePrevaylerFolder() {
        PersistenceDiskHelper.deletePrevaylerFolder();
    }

    @After
    public void tearDown() {
        deletePrevaylerFolder();
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailIfInitialSystemNotGiven() {
        //WHEN
        try (final PersistenceController ctrl
                = PrevaylerBuilder.newBuilder().build()) {
        }
    }

    @Test
    public void shouldCreateInitialSimpleSystemWhenSupplierGiven() {
        //WHEN
        try (
                final PersistenceController ctrl = PrevaylerBuilder.newBuilder().useSupplier(() -> StorableObject.createTestObject()).build();) {
            //THEN
            Assert.assertNotNull(ctrl);
        }
    }

    @Test
    public void shouldReallyExecuteValues() {
        //WHEN
        try (
                final PersistenceController<StorableObject, Map<String, String>> ctrl = PrevaylerBuilder.newBuilder().useSupplier(() -> StorableObject.createTestObject()).build();) {
            ctrl.execute((x) -> x.internalMap.put("myKey", "myVal"));
            //THEN
            assertEquals("myVal", ctrl.query((x) -> x.get("myKey")));
        }
    }

    @Test
    public void shouldUseGivenFolder() {
        //GIVEN
        final PrevaylerBuilder<StorableObject, Map<String, String>> builder = PrevaylerBuilder.newBuilder()
                .useSupplier(StorableObject::createTestObject)
                .withFolder("test1");

        //WHEN
        try (
                final PersistenceController<StorableObject, Map<String, String>> ctrl = builder.build();) {
            ctrl.execute((x) -> x.internalMap.put("myKey", "myVal"));
            //THEN
            assertTrue(new PersistenceFactory().exists("test1"));
        }
    }

    @Test
    public void shouldOverweriteSystem() {
        //GIVEN
        final PrevaylerBuilder<StorableObject, Map<String, String>> builder = PrevaylerBuilder.newBuilder()
                .useSupplier(StorableObject::createTestObject)
                .forceOverwrite(true);
        //WHEN
        try (
                final PersistenceController<StorableObject, Map<String, String>> ctrl = builder.build();) {
            ctrl.execute((x) -> {
                x.internalMap.put("key:1", "myVal");
            });

        }

        Politician.beatAroundTheBush(() -> Thread.sleep(1000));
        //THEN
        try (
                final PersistenceController<StorableObject, Map<String, String>> ctrl = builder.build();) {
            final String val = ctrl.query(s -> s.get("key:1"));
            assertEquals("val:1", val);
        }
    }

    @Test
    public void shouldUseJavaSerializerForJournaling() {
        //GIVEN
        final PrevaylerBuilder<StorableObject, Map<String, String>> builder = PrevaylerBuilder.newBuilder()
                .useSupplier(StorableObject::createTestObject)
                .withJournalFastSerialization(false);
        StrangeTransaction.counter = 0;
        //WHEN
        try (
                final PersistenceController<StorableObject, Map<String, String>> ctrl = builder.build();) {
            ctrl.execute(new StrangeTransaction());
        }
        assertTrue(StrangeTransaction.counter > 0);
    }

    @Test
    public void shouldUseFastSerializerForJournaling() {
        //GIVEN
        final PrevaylerBuilder<StorableObject, Map<String, String>> builder = PrevaylerBuilder.newBuilder()
                .useSupplier(StorableObject::createTestObject)
                .withJournalFastSerialization(true);
        StrangeTransaction.counter = 0;
        //WHEN
        try (
                final PersistenceController<StorableObject, Map<String, String>> ctrl = builder.build();) {
            ctrl.execute(new StrangeTransaction());
        }
        assertTrue(StrangeTransaction.counter == 0);
    }

    @Test(expected = RestoreException.class)
    public void shouldThrowRestoreExceptionWhenLoadFails() {
        //GIVEN
        final PrevaylerBuilder<StorableObject, Map<String, String>> builder = PrevaylerBuilder.newBuilder()
                .useSupplier(StorableObject::createTestObject)
                .withJournalFastSerialization(false);
        try (
                final PersistenceController<StorableObject, Map<String, String>> ctrl = builder.build();) {
            ctrl.execute(new UnstableTransaction());
            ctrl.execute((x) -> x.internalMap.put("key:2", "dzikc"));
            ctrl.shut();
        }

        failureMarker.set(Boolean.TRUE);
        try (
                PersistenceController<StorableObject, Map<String, String>> controller2 = builder.build();) {
        }

    }

    private static final class StrangeTransaction implements VoidCommand<StorableObject>, Serializable {

        private static int counter = 0;

        @Override
        public void executeVoid(StorableObject system) {
            system.internalMap.put("key:2", "dzikb");
        }

        private void writeObject(ObjectOutputStream ois) throws IOException {
            ois.defaultWriteObject();
            counter++;
        }

    }

    private static final class UnstableTransaction implements VoidCommand<StorableObject>, Serializable {

        @Override
        public void executeVoid(StorableObject system) {
            system.internalMap.put("key:2", "dzikb");
        }

        private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
            ois.defaultReadObject();
            if (isFailureNeeded()) {
                throw new IOException("failure on demand");
            }
        }

    }
}
