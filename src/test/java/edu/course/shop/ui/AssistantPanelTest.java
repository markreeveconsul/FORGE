package edu.course.shop.ui;

import edu.course.shop.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssistantPanelTest {
    @Test void temporaryProviderFailuresShowBrandedRecoveryCopy() {
        for(int status:new int[]{500,502,503,504}) {
            String message=AssistantPanel.friendly(new IllegalStateException("Gemini could not answer (HTTP "+status+"). Try again later."));
            assertTrue(message.contains("FORGE AI"));
            assertTrue(message.contains("temporarily unavailable"));
            assertFalse(message.contains("Gemini"));
            assertFalse(message.contains("HTTP"));
        }
    }

    @Test void setupAndQuotaFailuresDoNotExposeProviderConfiguration() {
        for(String failure:new String[]{"Set GEMINI_API_KEY", "Check FORGE_AI_MODEL", "Gemini rejected access", "Gemini's quota or rate limit was reached"}) {
            String message=AssistantPanel.friendly(new IllegalStateException(failure));
            assertTrue(message.contains("FORGE AI"));
            assertFalse(message.contains("Gemini"));
            assertFalse(message.contains("API_KEY"));
            assertFalse(message.contains("MODEL"));
        }
    }

    @Test void localValidationKeepsItsActionableMessage() {
        String message="Choose two products to compare.";
        assertEquals(message,AssistantPanel.friendly(new ValidationException(message)));
    }
}
