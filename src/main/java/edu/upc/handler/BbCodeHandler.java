package edu.upc.handler;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;

import edu.upc.entity.Annotation;
import edu.upc.exception.InvalidAnnotationException;
import edu.upc.llmUtils.AnnotationType;

public class BbCodeHandler {

	/**
     * Given a text annotated with BBCode tags without IDs, this method assigns
     * sequential IDs to the tags and returns the annotated text with IDs.
     * Example input: "When a [entity]visitor[/entity]..."
     * Example output: "When a [entity id="T0"]visitor[/entity]..."
     */
    public static String annotateWithIds(String text) {
        Pattern pattern = Pattern.compile("\\[(entity|action|condition)\\](.*?)\\[\\/\\1\\]", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        int idCounter = 0;

        while (matcher.find()) {
            String tagType = matcher.group(1);
            String taggedText = matcher.group(2);
            String replacement = String.format("[%s id=\"T%d\"]%s[/%s]", tagType, idCounter++, taggedText, tagType);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Given an annotated text containing markers like:
     * [entity id="T1"]some text[/entity]
     * [action id="T0"]another text[/action]
     * [condition id="T2"]more text[/action]
     * this method returns a Map where the key is the ID (e.g. "T1") and the
     * value is a Pair<Integer, Integer> indicating the 0-based start and end
     * offsets of that annotated text in the unannotated (i.e. original) text.
     */
    public static Map<String, Annotation> findSpans(String annotatedText, String originalText) {
        // This pattern will capture three groups:
        // 1) The type of tag: "entity" or "action"
        // 2) The id attribute (e.g., T1, T2, ...)
        // 3) The text covered by that tag
        // Note the escaped slash [/...], since the text has [/entity], [/action] or [].
        Pattern pattern = Pattern.compile("\\[(entity|action|condition)\\s+id=\"(.*?)\"\\](.*?)\\[\\/\\1\\]",
                Pattern.DOTALL);

        // We'll build the unannotated text as we go, and keep track of
        // how many characters we have appended so far.
        StringBuilder plainTextBuilder = new StringBuilder();

        // This map will store (T-ID -> (startOffset, endOffset)).
        Map<String, Annotation> result = new LinkedHashMap<>();

        Matcher matcher = pattern.matcher(annotatedText);

        // 'lastEnd' will keep track of where the previous match ended,
        // so we know how much plain text was in-between tags.
        int lastEnd = 0;

        while (matcher.find()) {
            // (1) Append all text from 'lastEnd' up to the start of this match
            // (this text is outside the [entity/action] tags).
            String textBefore = annotatedText.substring(lastEnd, matcher.start());
            plainTextBuilder.append(textBefore);

            // (2) Now the annotated text portion:
            String tagType = matcher.group(1); // "entity" or "action"
            String tagId = matcher.group(2); // e.g. "T1"
            String taggedText = matcher.group(3); // the text inside the tag

            // The start of the annotated text in the unannotated text.
            int startOffset = plainTextBuilder.length();
            // Append this portion to the unannotated text
            plainTextBuilder.append(taggedText);
            // The end offset is now:
            int endOffset = plainTextBuilder.length() - 1;

            // Determine the annotation type
            AnnotationType annotationType = tagType.equals("entity") ? AnnotationType.Entity
                    : tagType.equals("action") ? AnnotationType.Action : AnnotationType.Condition;

            // Record in the map
            result.put(
                    tagId,
                    new Annotation(annotationType, tagId, Pair.of(startOffset, endOffset), taggedText));

            // Update lastEnd
            lastEnd = matcher.end();
        }

        // (3) Finally, append any text after the last match
        if (lastEnd < annotatedText.length()) {
            String textAfter = annotatedText.substring(lastEnd);
            plainTextBuilder.append(textAfter);
        }

        String plainText = plainTextBuilder.toString();
        if (!originalText.trim().equals(plainText.trim())) {
            throw new InvalidAnnotationException(originalText, plainText);
        }

        // At this point, plainTextBuilder has the unannotated text
        // and 'result' has all the spans in that unannotated text.
        return result;
    }

	// A simple test.
    public static void main(String[] args) {

        // Test the ID annotation method
        String textWithoutIds = "When a [entity]visitor[/entity] wants to [action]become a member[/action]...";
        String annotatedTextWithIds = annotateWithIds(textWithoutIds);
        System.out.println("Annotated with IDs: " + annotatedTextWithIds);

        // Test the findSpans method
        String annotatedText = "When a visitor wants to become a member of [entity id=\"T1\"]Barcelona's ZooClub[/entity], the following steps must be taken.\n\nFirst of all, [entity id=\"T2\"]the customer[/entity] must [action id=\"T0\"]decide[/action]";
        String plainText = "When a visitor wants to become a member of Barcelona's ZooClub, the following steps must be taken.\n\nFirst of all, the customer must decide";

        Map<String, Annotation> spans = findSpans(annotatedText, plainText);

        System.out.println("STARTING");

        // Print the results
        for (Map.Entry<String, Annotation> e : spans.entrySet()) {
            System.out.println("ID = " + e.getKey()
                    + ", Span = " + e.getValue().span.getLeft()
                    + " to " + e.getValue().span.getRight()
                    + ", Text = " + plainText.substring(e.getValue().span.getLeft(),
                            e.getValue().span.getRight() + 1));

        }
    }
}