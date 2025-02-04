package edu.upc.handler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import edu.upc.entity.Annotation;
import edu.upc.entity.LlmMessage;
import edu.upc.exception.InvalidAnnotationException;
import edu.upc.llm.LlmClient;

public class AtdpFromLlmHandler {
	private LlmClient llmClient;
	private String fileName;

	public AtdpFromLlmHandler(LlmClient llmClient, String fileName) {
		this.llmClient = llmClient;
		this.fileName = fileName;
	}

	public StringBuilder extractAndAnnotate(String textToParse, boolean useCache,float temperature) throws Exception {
		Map<String, Annotation> spans = null;
		String textWithBbCodeAndIds = null;

		int max_iterations = 4;
		int num_retries = 0;
		for (int i = 0; i < max_iterations; i++) {
			System.out.println("Step 1: Annotating Text");
			JSONObject annotationsResult = callLlm(llmClient, "atdp_extractor_system", "atdp_extractor_annotations",
					Map.of("USER_TEXT", textToParse), "atdp_extractor_annotations", useCache,temperature);

			String textWithBbcode = (String) annotationsResult.get("bbcode");
			System.out.println("\n---------- Text with BBCode Without IDs ----------");
			System.out.println(textWithBbcode);
			System.out.println("------------------------------------------\n");

			textWithBbCodeAndIds = BbCodeHandler.annotateWithIds(textWithBbcode);
			System.out.println("\n--------- Text with BBCode and IDs ------------");
			System.out.println(textWithBbCodeAndIds);
			System.out.println("------------------------------------------\n");
			// Parse the spans
			try {
				spans = BbCodeHandler.findSpans(textWithBbCodeAndIds, textToParse);
				break;
			} catch (InvalidAnnotationException e) {
				System.out.println("LLM did not produce the original text. Retrying...");
			}
			num_retries++;
		}

		if (num_retries + 1 >= max_iterations) {
			throw new Exception("Could not get the original text after " + max_iterations + " iterations.");
		} else {
			System.out.println("Succeeded with " + num_retries + " retries.");
		}

		if (textWithBbCodeAndIds == null || spans == null) {
			throw new Exception("Could not get the BBCode with IDs.");
		}
		System.out.println("Step 2: Finding Relations");
		JSONObject relationsResult = callLlm(llmClient, "atdp_extractor_system", "atdp_extractor_relations",
				Map.of("ANNOTATED_TEXT", textWithBbCodeAndIds), "atdp_extractor_relations", useCache,temperature);
		System.out.println("Step 2 finished");
		System.out.println("Step 3: Building ATDP File");
		StringBuilder atdpFile = buildAtdpFile(textWithBbCodeAndIds, relationsResult, spans,textToParse);
		System.out.println("Step 3 finished");
		return atdpFile;
	}

	private StringBuilder buildAtdpFile(String textWithBbCodeAndIds, JSONObject relationsResult, Map<String, Annotation> spans, String textToParse) {
		JSONArray agents = (JSONArray) relationsResult.get("agents");
		JSONArray patients = (JSONArray) relationsResult.get("patients");
		JSONArray sequences = (JSONArray) relationsResult.get("sequences");
		JSONArray conflicts = (JSONArray) relationsResult.get("conflicts");

		StringBuilder atdpFile = new StringBuilder();
		//Map<String, Annotation> spans = BbCodeHandler.findSpans(textWithBbCodeAndIds, textToParse);
		for (Map.Entry<String, Annotation> span : spans.entrySet()) {
			String id = span.getKey();
			Annotation ann = span.getValue();
			atdpFile.append(id + "\t" + ann.type.name() + " " + ann.span.getLeft() + " " + (ann.span.getRight() + 1)
					+ "\t" + ann.coveredText + "\n");
		}

		int relationIndex = 0;
		relationIndex = appendRelations(atdpFile, agents, "Agent", relationIndex);
		relationIndex = appendRelations(atdpFile, patients, "Patient", relationIndex);
		relationIndex = appendRelations(atdpFile, sequences, "Sequence", relationIndex);
		appendRelations(atdpFile, conflicts, "Conflict", relationIndex);

		return atdpFile;
	}

	private int appendRelations(StringBuilder atdpFile, JSONArray relations, String relationType, int relationIndex) {
		for (Object relation : relations.toArray()) {
			JSONArray relationObj = (JSONArray) relation;
			String id = "R" + relationIndex;
			String arg1 = "Arg1:" + relationObj.get(0);
			String arg2 = "Arg2:" + relationObj.get(1);
			atdpFile.append(id + "\t" + relationType + " " + arg1 + " " + arg2 + "\t" + "\n");
			relationIndex++;
		}
		return relationIndex;
	}

	public JSONObject callLlm(LlmClient llmClient, String systemPrompt, String userPrompt,
			Map<String, String> promptArgs, String jsonSchema, boolean useCache,float temperature) throws Exception {

		if (useCache) {
			return getCachedJson(fileName, userPrompt);
		} else {
			JSONObject result = llmClient.callLlmJson(
					List.of(new LlmMessage("system", ResourcesHandler.getPromptByName(systemPrompt)),
							new LlmMessage("user", ResourcesHandler.getPromptByName(userPrompt, promptArgs))),
					ResourcesHandler.getJsonSchemaByName(jsonSchema),temperature);

			// Write JSON output to file
			File cacheDir = new File("cached");
			if (!cacheDir.exists())
				cacheDir.mkdir();

			try (FileWriter file = new FileWriter(cacheDir.getPath() + "/" + fileName + "_" + userPrompt + ".json")) {
				file.write(result.toJSONString());
				file.flush();
				file.close();
			}

			return result;
		}
	}

	private JSONObject getCachedJson(String fileName2, String userPrompt) throws ParseException, IOException {
		JSONParser parser = new JSONParser();
		return (JSONObject) parser.parse(Files.readString(Path.of("cached/" + fileName + "_" + userPrompt + ".json")));
	}

}
