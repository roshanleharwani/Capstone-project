package com.nexus.ingress.pii;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.util.Span;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Pass 3 Transformer NER Scanner: Context-aware Named Entity Recognition.
 * Real ML implementation using Apache OpenNLP Maximum Entropy models.
 */
@Component
public class TransformerNERScanner implements PIIScanner {

    private NameFinderME personFinder;
    private NameFinderME orgFinder;
    private final SimpleTokenizer tokenizer = SimpleTokenizer.INSTANCE;

    @PostConstruct
    public void initModels() {
        try {
            personFinder = new NameFinderME(loadModel("en-ner-person.bin", "https://opennlp.sourceforge.net/models-1.5/en-ner-person.bin"));
            orgFinder = new NameFinderME(loadModel("en-ner-organization.bin", "https://opennlp.sourceforge.net/models-1.5/en-ner-organization.bin"));
        } catch (Exception e) {
            System.err.println("Failed to load OpenNLP models. NER will be degraded: " + e.getMessage());
        }
    }

    private TokenNameFinderModel loadModel(String filename, String url) throws Exception {
        Path tempFile = Path.of(System.getProperty("java.io.tmpdir"), filename);
        if (!Files.exists(tempFile)) {
            System.out.println("Downloading OpenNLP model: " + filename);
            try (InputStream in = new URL(url).openStream()) {
                Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        try (InputStream in = new FileInputStream(tempFile.toFile())) {
            return new TokenNameFinderModel(in);
        }
    }

    @Override
    public List<PIIMatch> scan(String text) {
        List<PIIMatch> matches = new ArrayList<>();
        if (text == null || text.isBlank() || personFinder == null || orgFinder == null) {
            return matches;
        }

        // 1. Tokenize using OpenNLP SimpleTokenizer
        Span[] tokenSpans = tokenizer.tokenizePos(text);
        String[] tokens = Span.spansToStrings(tokenSpans, text);

        // 2. Scan Persons
        synchronized (personFinder) { // NameFinderME is not thread-safe
            Span[] personSpans = personFinder.find(tokens);
            matches.addAll(mapSpans(personSpans, tokenSpans, text, "PERSON"));
            personFinder.clearAdaptiveData();
        }

        // 3. Scan Organizations
        synchronized (orgFinder) {
            Span[] orgSpans = orgFinder.find(tokens);
            matches.addAll(mapSpans(orgSpans, tokenSpans, text, "ORGANIZATION"));
            orgFinder.clearAdaptiveData();
        }

        return matches;
    }

    private List<PIIMatch> mapSpans(Span[] nameSpans, Span[] tokenSpans, String originalText, String type) {
        List<PIIMatch> matches = new ArrayList<>();
        for (Span s : nameSpans) {
            int startChar = tokenSpans[s.getStart()].getStart();
            int endChar = tokenSpans[s.getEnd() - 1].getEnd();
            String matchedString = originalText.substring(startChar, endChar);
            
            // Map the probability returned by OpenNLP MaxEnt model
            matches.add(new PIIMatch(matchedString, type, startChar, endChar, s.getProb()));
        }
        return matches;
    }

    @Override
    public String getPassName() {
        return "PASS_3_TRANSFORMER_NER";
    }
}
