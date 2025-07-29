package com.example.modelload;

import android.util.Log;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import java.text.Normalizer;
import java.io.FileInputStream;

public class BPEToken {
    private Map<Integer, String> byteEncoder;
    private Map<String, Integer> byteDecoder;
    private Map<String, Integer> encoder;
    private Map<Integer, String> decoder;
    private Map<List<String>, Integer> bpeRanks;
    private Map<String, String> cache;
    private Pattern pat;

    public BPEToken(String bpeFileName) throws IOException {
        // Initialize byte encoder/decoder
        this.byteEncoder = bytesToUnicode();
        this.byteDecoder = new HashMap<>();
        for (Map.Entry<Integer, String> entry : byteEncoder.entrySet()) {
            byteDecoder.put(entry.getValue(), entry.getKey());
        }


        // Read and process merges from classpath
        List<String[]> merges = new ArrayList<>();
        int lineNum = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(bpeFileName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Process each line here
                lineNum++;
                if (lineNum >= 2 && lineNum <= 49152 - 256 - 2 + 1) {
                    String[] parts = line.split(" ");
                    if (parts.length == 2) {
                        merges.add(parts);
                    }
                }
            }
        }

        // Build vocabulary
        List<String> vocab = new ArrayList<>(byteEncoder.values());
        for (String v : new ArrayList<>(vocab)) {
            vocab.add(v + "</w>");
        }
        for (String[] merge : merges) {
            vocab.add(merge[0] + merge[1]);
        }
        vocab.add("<|startoftext|>");
        vocab.add("<|endoftext|>");

        // Initialize encoder/decoder
        this.encoder = new HashMap<>();
        this.decoder = new HashMap<>();
        for (int i = 0; i < vocab.size(); i++) {
            encoder.put(vocab.get(i), i);
            decoder.put(i, vocab.get(i));
        }

        // Initialize BPE ranks
        this.bpeRanks = new HashMap<>();
        for (int i = 0; i < merges.size(); i++) {
            bpeRanks.put(Arrays.asList(merges.get(i)), i);
        }

        // Initialize cache
        this.cache = new HashMap<>();
        cache.put("<|startoftext|>", "<|startoftext|>");
        cache.put("<|endoftext|>", "<|endoftext|>");

        // Compile pattern
        this.pat = Pattern.compile("<\\|startoftext\\|>|<\\|endoftext\\|>|'s|'t|'re|'ve|'m|'ll|'d|[\\p{L}]+|[\\p{N}]|[^\\s\\p{L}\\p{N}]+", Pattern.CASE_INSENSITIVE);
    }

    private Map<Integer, String> bytesToUnicode() {
        Map<Integer, String> byteToUnicode = new LinkedHashMap<>();

        List<Integer> bs = new ArrayList<>();
        for (int b = 33; b <= 126; b++) bs.add(b);
        for (int b = 161; b <= 172; b++) bs.add(b);
        for (int b = 174; b <= 255; b++) bs.add(b);

        List<Integer> cs = new ArrayList<>(bs);
        int n = 0;
        for (int b = 0; b < 256; b++) {
            if (!bs.contains(b)) {
                bs.add(b);
                cs.add(256 + n);
                n++;
            }
        }

        for (int i = 0; i < bs.size(); i++) {
            byteToUnicode.put(bs.get(i), new String(Character.toChars(cs.get(i))));
        }

        return byteToUnicode;
    }

    private String basicClean(String text) {
        // Step 1: Fix common encoding issues (simplified)
        text = fixCommonEncodingProblems(text);

        // Step 2: HTML unescape (basic implementation)
        text = unescapeHtml(text);
        text = unescapeHtml(text);  // unescape twice as in original

        // Step 3: Normalize Unicode and trim
        text = Normalizer.normalize(text, Normalizer.Form.NFKC);
        return text.trim();
    }

    private String whitespaceClean(String text) {
        // Replace any whitespace (including newlines, tabs) with single space
        return text.replaceAll("\\s+", " ").trim();
    }

    private String fixCommonEncodingProblems(String text) {
        // Handle common mojibake cases
        return text.replace("â€œ", "\"")   // left double quote
                .replace("â€�", "\"")   // right double quote
                .replace("â€˜", "'")    // left single quote
                .replace("â€™", "'")    // right single quote
                .replace("â€¦", "...")  // ellipsis
                .replace("â€", "-")     // dash
                .replace("â€“", "-")    // en dash
                .replace("â€”", "-");   // em dash
    }

    private String unescapeHtml(String text) {
        // Basic HTML entity unescaping
        return text.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&#39;", "'")
                .replace("&#34;", "\"");
    }

    // Basic implementation of text encoding fixing (simplified ftfy functionality)
    private String fixTextEncoding(String text) {
        // This is a simplified version - consider using ICU4J for more robust handling
        try {
            // Detect and fix common encoding issues
            if (text.matches(".*[â€¦â€œâ€�â€˜â€™].*")) {
                text = new String(text.getBytes("Windows-1252"), "UTF-8");
            }
            return text;
        } catch (Exception e) {
            return text; // fallback to original if encoding detection fails
        }
    }

    public String bpe(String token) {
        if (cache.containsKey(token)) {
            return cache.get(token);
        }

        // Create word tuple with </w> suffix on last character
        List<String> word = new ArrayList<>();
        if (token.length() > 0) {
            for (int i = 0; i < token.length() - 1; i++) {
                word.add(String.valueOf(token.charAt(i)));
            }
            word.add(token.charAt(token.length() - 1) + "</w>");
        }

        Set<List<String>> pairs = getPairs(word);

        if (pairs.isEmpty()) {
            return token + "</w>";
        }

        while (true) {
            // Find the lowest rank bigram
            List<String> bigram = null;
            int minRank = Integer.MAX_VALUE;

            for (List<String> pair : pairs) {
                int rank = bpeRanks.getOrDefault(pair, Integer.MAX_VALUE);
                if (rank < minRank) {
                    minRank = rank;
                    bigram = pair;
                }
            }

            if (bigram == null || !bpeRanks.containsKey(bigram)) {
                break;
            }

            String first = bigram.get(0);
            String second = bigram.get(1);
            List<String> newWord = new ArrayList<>();
            int i = 0;

            while (i < word.size()) {
                // Find next occurrence of first in word starting from i
                int j = -1;
                for (int k = i; k < word.size(); k++) {
                    if (word.get(k).equals(first)) {
                        j = k;
                        break;
                    }
                }

                if (j == -1) {
                    newWord.addAll(word.subList(i, word.size()));
                    break;
                }

                newWord.addAll(word.subList(i, j));
                i = j;

                if (i < word.size() - 1 && word.get(i).equals(first) && word.get(i + 1).equals(second)) {
                    newWord.add(first + second);
                    i += 2;
                } else {
                    newWord.add(word.get(i));
                    i += 1;
                }
            }

            word = newWord;

            if (word.size() == 1) {
                break;
            } else {
                pairs = getPairs(word);
            }
        }

        String result = String.join(" ", word);
        cache.put(token, result);
        return result;
    }

    private Set<List<String>> getPairs(List<String> word) {
        Set<List<String>> pairs = new HashSet<>();
        if (word.size() < 2) {
            return pairs;
        }

        for (int i = 0; i < word.size() - 1; i++) {
            pairs.add(Arrays.asList(word.get(i), word.get(i + 1)));
        }

        return pairs;
    }

    public List<Integer> encode(String text) {
        List<Integer> bpeTokens = new ArrayList<>();

        // Clean the text
        text = whitespaceClean(basicClean(text)).toLowerCase();

        // Process each token found by the pattern
        Matcher matcher = pat.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();

            // Convert token bytes to unicode characters
            StringBuilder encodedToken = new StringBuilder();
            try {
                byte[] bytes = token.getBytes("UTF-8");
                for (byte b : bytes) {
                    encodedToken.append(byteEncoder.get((int) b & 0xff));
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException("UTF-8 encoding not supported", e);
            }

            // Apply BPE and convert to token ids
            String[] bpeTokenized = bpe(encodedToken.toString()).split(" ");
            for (String bpeToken : bpeTokenized) {
                if (encoder.containsKey(bpeToken)) {
                    bpeTokens.add(encoder.get(bpeToken));
                } else {
                    // Handle unknown tokens (you might want to throw an exception or use a special token)
                    throw new RuntimeException("Unknown token in vocabulary: " + bpeToken);
                }
            }
        }

        return bpeTokens;
    }
}
