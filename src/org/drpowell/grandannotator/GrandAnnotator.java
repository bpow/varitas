package org.drpowell.grandannotator;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.broad.tribble.readers.TabixReader;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

public class GrandAnnotator {
	private ArrayList<TabixVCFAnnotator> annotators = new ArrayList<TabixVCFAnnotator>();
	private static Logger logger = Logger.getLogger("org.drpowell.grandannotator.GrandAnnotator");
	
	public GrandAnnotator(Reader config) throws IOException, ParseException, ScriptException {
		annotators = readConfigFromJS(config);
	}
	
	private ArrayList<TabixVCFAnnotator> readConfigFromJS(Reader jsReader) throws ScriptException {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
		engine.eval("importPackage(" + this.getClass().getPackage().getName() + ");");
		engine.put("ga", this);
		engine.eval("createAnnotator = function(file, fields) { return ga.createAnnotator(file, fields); }");
		engine.eval(jsReader);
		return annotators;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private ArrayList<TabixVCFAnnotator> readConfigFromJSON(Reader jsonReader) throws IOException, ParseException {
		JSONObject config = (JSONObject) JSONValue.parseWithException(jsonReader);
		for (Map.Entry<String, Map> configEntry : ((Map<String, Map>) config).entrySet()) {
			annotators.add(createAnnotatorFromConfig(configEntry.getKey(), configEntry.getValue()));
		}
		return annotators;
	}
	
	public TabixVCFAnnotator createAnnotator(String filename, String fieldString) {
		String [] fields = fieldString.split(",");
		Map<String, String> fieldMap = new LinkedHashMap<String, String>();
		for (String field : fields) {
			int eq = field.indexOf("=");
			if (eq < 0) {
				fieldMap.put(field, field);
			} else {
				fieldMap.put(field.substring(0, eq), field.substring(eq+1));
			}
		}
		try {
			TabixVCFAnnotator annotator =new TabixVCFAnnotator(new TabixReader(filename), fieldMap);
			annotators.add(annotator);
			return annotator;
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe(e.toString());
			return null;
		}
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private TabixVCFAnnotator createAnnotatorFromConfig(String tabixFile, Map config) throws IOException {
		LinkedHashMap<String, String> fields = new LinkedHashMap<String, String>();
		if (config.containsKey("fields")) {
			List unchangedFields = (List) config.get("fields");
			for (Object s : unchangedFields) {
				fields.put(s.toString(), s.toString());
			}
		}
		if (config.containsKey("changedFields")) {
			fields.putAll((Map<String, String>) config.get("changedFields"));
		}
		TabixVCFAnnotator annotator = null;
		annotator = new TabixVCFAnnotator(new TabixReader(tabixFile), fields);
		if (config.containsKey("addChr")) {
			annotator.setAddChr(true);
		}
		if (config.containsKey("requirePass")) {
			annotator.setRequirePass(true);
		}
		// TODO handle rsIDs, INFO headers
		return annotator;
	}

	public void annotateVCFFile(BufferedReader input) throws IOException {
		String line;
		while ((line = input.readLine()) != null) {
			if (line.startsWith("#")) {
				System.out.println(line);
				// FIXME -- will need to add INFO header lines
			} else {
				VCFVariant variant = new VCFVariant(line);
				
				for (TabixVCFAnnotator annotator: annotators) {
					annotator.annotate(variant);
				}
				System.out.println(variant);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		GrandAnnotator annotator = new GrandAnnotator(new BufferedReader(new FileReader(args[0])));
		BufferedReader input = new BufferedReader(new FileReader(args[1]));
		annotator.annotateVCFFile(input);
	}


}
