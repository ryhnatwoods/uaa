package org.cloudfoundry.identity.uaa.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.util.StopWatch;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;


class TestTimerExtensions implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.create(context.getRequiredTestMethod()));
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        store.put("stopWatch", stopWatch);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        ExtensionContext.Store store = context.getStore(ExtensionContext.Namespace.create(context.getRequiredTestMethod()));
        StopWatch stopWatch = store.get("stopWatch", StopWatch.class);
        stopWatch.stop();
        System.out.println(context.getRequiredTestMethod().getName() + ": " + stopWatch.getLastTaskTimeMillis() + " ms");
    }

}

@ExtendWith(TestTimerExtensions.class)
@TestMethodOrder(MethodOrderer.Alphanumeric.class)
class UaaRandomizerTest {

    private UaaRandomizer uaaRandomizer;

    @BeforeEach
    void setUp() throws NoSuchProviderException, NoSuchAlgorithmException {
        uaaRandomizer = new UaaRandomizer();
    }

    @Test
    void aaaaaaFirst() {
        
    }

    @Test
    void emptyTest() {
        assertTrue(true);
    }

    @Test
    void insecureRandom() {
        RandomValueStringGenerator generator = new RandomValueStringGenerator(10);

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        IntStream.range(0, 100).forEach((i) -> generator.generate());
        stopWatch.stop();
        System.out.println("insecure: " + stopWatch.getLastTaskTimeMillis());
    }

    @Test
    void secureRandom() throws NoSuchProviderException, NoSuchAlgorithmException {
//        java.security.SecureRandom
//        org.apache.commons.rng.UniformRandomProvider rng = org.apache.commons.rng.simple.RandomSource.create();
//
//        org.apache.commons.text.RandomStringGenerator randomStringGenerator = new RandomStringGenerator.Builder().usingRandom();

//        SecureRandom secureRandom = new SecureRandom();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        IntConsumer intConsumer = (int i) -> uaaRandomizer.getSecureRandom(10);
        IntStream.range(0, 100).forEach(intConsumer);
        stopWatch.stop();
        System.out.println("secure: " + stopWatch.getLastTaskTimeMillis());
//        assertNotNull(iAmASecureState);
//        assertEquals(10, iAmASecureState.length());
    }

}
