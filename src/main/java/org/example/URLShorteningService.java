package org.example;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;


class URLShorteningService {
    private static final String BASE_URL = "http://short.url/";
    private static final int SHORT_KEY_LENGTH = 6;
    private static final long EXPIRY_TIME_MILLIS = 10; // 24 hours

    private final Map<String, URLMapping> urlStore = new HashMap<>();

    private static final SecureRandom secureRandom = new SecureRandom();

    private static final String ALPHANUMERIC_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";


    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public URLShorteningService() {
        // Schedule a task to clean up expired entries in the map
        scheduler.scheduleAtFixedRate(this::cleanupExpiredEntries, 0, 1, TimeUnit.SECONDS);
    }

    public String shortenURL(String originalURL) {
        Callable<String> task = () -> {
            String shortKey = generateShortKey();
            String shortURL = BASE_URL + shortKey;

            // Store the mapping with expiry time in the in-memory store
            urlStore.put(shortKey, new URLMapping(originalURL, System.currentTimeMillis() + EXPIRY_TIME_MILLIS));

            return shortURL;
        };

        try {
            Future<String> future = executorService.submit(task);
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace(); // Handle exceptions appropriately in a production system
            return null;
        }
    }

    public String resolveURL(String shortURL) {
        Callable<String> task = () -> {
            String shortKey = extractShortKey(shortURL);
            URLMapping urlMapping = urlStore.get(shortKey);

            // Check if the entry is not expired
            if (urlMapping != null && urlMapping.getExpiryTime() > System.currentTimeMillis()) {
                return urlMapping.getOriginalURL();
            } else {
                // Remove expired entry
                urlStore.remove(shortKey);
                return null;
            }
        };

        try {
            Future<String> future = executorService.submit(task);
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace(); // Handle exceptions appropriately in a production system
            return null;
        }
    }

    private void cleanupExpiredEntries() {
        // Remove entries with expired URLs
        urlStore.entrySet().removeIf(
                entry -> {
                    boolean isExpired = entry.getValue().getExpiryTime() <= System.currentTimeMillis();
                    if (isExpired) {
                        System.out.println("Expired entry removed: ShortKey=" + entry.getKey());
                    }
                    return isExpired;
                }
        );
    }


    private String generateShortKey() {
        // In a real-world scenario, use a more robust method to generate unique short keys
        StringBuilder shortKey = new StringBuilder();
        for (int i = 0; i < SHORT_KEY_LENGTH; i++) {
            int randomIndex = secureRandom.nextInt(ALPHANUMERIC_CHARS.length());
            shortKey.append(ALPHANUMERIC_CHARS.charAt(randomIndex));
        }
        return shortKey.toString();
    }


    private String extractShortKey(String shortURL) {
        // Extract the short key from the short URL
        return shortURL.replace(BASE_URL, "");
    }

    private static class URLMapping {
        private final String originalURL;
        private final long expiryTime;

        public URLMapping(String originalURL, long expiryTime) {
            this.originalURL = originalURL;
            this.expiryTime = expiryTime;
        }

        public String getOriginalURL() {
            return originalURL;
        }

        public long getExpiryTime() {
            return expiryTime;
        }
    }

    public static void main(String[] args) {
        URLShorteningService urlShorteningService = new URLShorteningService();

        // Example usage
        String originalURL = "https://www.example.com";
        String shortURL = urlShorteningService.shortenURL(originalURL);
        System.out.println("Shortened URL: " + shortURL);

        // Resolving the short URL
        String resolvedURL = urlShorteningService.resolveURL(shortURL);
        System.out.println("Resolved URL: " + resolvedURL);
    }
}
