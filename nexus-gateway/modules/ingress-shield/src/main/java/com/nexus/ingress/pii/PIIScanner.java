package com.nexus.ingress.pii;

import java.util.List;

/**
 * Strategy interface for PII entity scanner passes.
 */
public interface PIIScanner {

    /**
     * Scans text and returns list of detected sensitive matches.
     */
    List<PIIMatch> scan(String text);

    /**
     * Describes the pass name (e.g., REGEX_PASS, SECRET_PASS, TRANSFORMER_NER_PASS).
     */
    String getPassName();

    class PIIMatch {
        private final String originalText;
        private final String entityType;
        private final int start;
        private final int end;
        private final double confidence;

        public PIIMatch(String originalText, String entityType, int start, int end, double confidence) {
            this.originalText = originalText;
            this.entityType = entityType;
            this.start = start;
            this.end = end;
            this.confidence = confidence;
        }

        public String getOriginalText() {
            return originalText;
        }

        public String getEntityType() {
            return entityType;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }

        public double getConfidence() {
            return confidence;
        }

        @Override
        public String toString() {
            return "PIIMatch{" +
                   "originalText='" + originalText + '\'' +
                   ", entityType='" + entityType + '\'' +
                   ", start=" + start +
                   ", end=" + end +
                   ", confidence=" + confidence +
                   '}';
        }
    }
}
