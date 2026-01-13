public class SearchResult {
    private boolean hasError;
    private String errorMessage;
    private String imageUrl;
    private String caption;

    public SearchResult() {
        this.hasError = false;
    }

    // Costruttore per errore
    public static SearchResult error(String errorMessage) {
        SearchResult result = new SearchResult();
        result.hasError = true;
        result.errorMessage = errorMessage;
        return result;
    }

    // Costruttore per successo con immagine
    public static SearchResult successWithImage(String imageUrl, String caption) {
        SearchResult result = new SearchResult();
        result.imageUrl = imageUrl;
        result.caption = caption;
        return result;
    }

    // Costruttore per successo senza immagine
    public static SearchResult success(String caption) {
        SearchResult result = new SearchResult();
        result.caption = caption;
        return result;
    }

    // Getters
    public boolean hasError() { return hasError; }
    public String getErrorMessage() { return errorMessage; }
    public boolean hasImage() { return imageUrl != null && !imageUrl.isEmpty(); }
    public String getImageUrl() { return imageUrl; }
    public String getCaption() { return caption; }
}
