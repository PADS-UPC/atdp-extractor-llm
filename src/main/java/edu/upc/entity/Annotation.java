package edu.upc.entity;

import org.apache.commons.lang3.tuple.Pair;

import edu.upc.llmUtils.AnnotationType;

public class Annotation {
	public final AnnotationType type;
	public final String id;
	public final Pair<Integer, Integer> span;
	public final String coveredText;

	public Annotation(AnnotationType type, String id, Pair<Integer, Integer> span, String coveredText) {
		this.type = type;
		this.id = id;
		this.span = span;
		this.coveredText = coveredText;
	}
}