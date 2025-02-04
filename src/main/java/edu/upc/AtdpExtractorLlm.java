package edu.upc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import edu.upc.handler.AtdpFromLlmHandler;
import edu.upc.handler.ResourcesHandler;
import edu.upc.llm.LlmClient;

public class AtdpExtractorLlm {

	@SuppressWarnings("unused")
	public static void main(String[] args) throws Exception {
		String folder = getFolderPathFromResource("texts").toString();
		File[] filePaths = new File[1];
		String fileName = null;
		// fileName = folder + "/" + "7-1_calling_leads" + ".txt"; // This is the file
		// that will be parsed. comment this
		// line to parse all files
		boolean useCache = true; // set to true to use cached data for parsing (faster) or false to fetch data
									// from LLM (slower)
		float temperature = 0f; // set the temperature for the LLM model (1 is the default for API ChatGPT)
		if (fileName == null)
			filePaths = new File(folder).listFiles();
		else
			filePaths[0] = new File(fileName);
		Arrays.sort(filePaths);
		for (File path : filePaths) {
			LlmClient llmClient = new LlmClient();
			fileName = getFileNameWithoutExtension(path);
			String textToParse = ResourcesHandler.getTextByName(fileName);
			System.out.println("------------------------------------------------------------------");
			System.out.println("Parsing file: " + fileName);
			System.out.println("------------------------------------------------------------------");
			AtdpFromLlmHandler extractor = new AtdpFromLlmHandler(llmClient, fileName);
			StringBuilder atdpFile = extractor.extractAndAnnotate(textToParse, useCache, temperature);
			writeToFile(fileName + ".ann", atdpFile.toString());
		}
		System.out.println("Done!");

	}

	private static Path getFolderPathFromResource(String folderName) {
		return Paths.get("src", "main", "resources", folderName).toAbsolutePath();
	}

	private static String getFileNameWithoutExtension(File file) {
		String fileName = "";
		try {
			if (file != null && file.exists()) {
				String name = file.getName();
				fileName = name.replaceFirst("[.][^.]+$", "");
			}
		} catch (Exception e) {
			e.printStackTrace();
			fileName = "";
		}
		return fileName;

	}

	private static void writeToFile(String fileName, String content) throws IOException {
		File outputDir = new File("outputs");
		if (!outputDir.exists())
			outputDir.mkdir();

		String path = outputDir.getAbsolutePath() + "/" + fileName;
		try (FileWriter file = new FileWriter(path)) {
			file.write(content);
			System.out.println("Step 4: ATDP File created");
			System.out.println("ATDP File path: " + path);
			file.flush();
			file.close();
		}
	}

}
