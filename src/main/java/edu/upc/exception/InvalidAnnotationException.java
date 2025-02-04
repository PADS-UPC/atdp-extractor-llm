package edu.upc.exception;

public class InvalidAnnotationException extends RuntimeException {
	private static final long serialVersionUID = 1L;
	private String originalText;
	private String unannotatedText;

	public InvalidAnnotationException(String originalText, String unanotatedText) {
		super("The original text does not match the unannotated text.");
		this.originalText = originalText;
		this.unannotatedText = unanotatedText;
	}

	public String getOriginalText() {
		return originalText;
	}

	public String getUnannotatedText() {
		return unannotatedText;
	}
}