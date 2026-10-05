package com.chikere.futureai.configuration;

import org.junit.jupiter.api.Test;
import org.mockito.internal.configuration.plugins.Plugins;

import static org.assertj.core.api.Assertions.assertThat;

class MockitoMockMakerConfigurationTest {

    @Test
    void subclassMockMakerIsActive() {
        assertThat(Plugins.getMockMaker().getClass().getName())
                .isEqualTo("org.mockito.internal.creation.bytebuddy.ByteBuddyMockMaker");
        assertThat(Plugins.getMemberAccessor().getClass().getName())
                .isEqualTo("org.mockito.internal.util.reflection.ReflectionMemberAccessor");
    }
}
