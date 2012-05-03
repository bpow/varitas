package org.drpowell.grandannotator;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.logging.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class GrandAnnotator {
	private ArrayList<Annotator> annotators = new ArrayList<Annotator>();
	private static Logger logger = Logger.getLogger("org.drpowell.grandannotator.GrandAnnotator");

	public GrandAnnotator(Reader config) throws IOException, ScriptException {
		annotators = readConfigFromJS(config);
	}

	private ArrayList<Annotator> readConfigFromJS(Reader jsReader) throws ScriptException {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
		engine.eval("importPackage(" + this.getClass().getPackage().getName() + ");");
		engine.put("ga", this);
		engine.eval("addVCFAnnotator = function(file, fields) { return ga.addVCFAnnotator(file, fields); }");
		engine.eval("addTSVAnnotator = function(file, fields) { return ga.addTSVAnnotator(file, fields); }");
		engine.eval(jsReader);
		return annotators;
	}

	public TabixVCFAnnotator addVCFAnnotator(String filename, String fieldString) {
		try {
			TabixVCFAnnotator annotator = new TabixVCFAnnotator(new TabixReader(filename), fieldString);
			annotators.add(annotator);
			return annotator;
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe(e.toString());
		}
		return null;
	}

	public TabixTSVAnnotator addTSVAnnotator(String filename, String fieldString) {
		try {
			TabixTSVAnnotator annotator = new TabixTSVAnnotator(new TabixReader(filename), fieldString);
			annotators.add(annotator);
			return annotator;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			logger.severe(ioe.toString());
		}
		return null;
	}
	
	public void annotateVCFFile(BufferedReader input) throws IOException {
		String line;
		boolean inHeader = true;
		while ((line = input.readLine()) != null) {
			if (inHeader && line.startsWith("##")) {
				System.out.println(line);
			} else if (inHeader && line.startsWith("#CHROM")) {
				inHeader = false;
				// add additional info lines
				for (Annotator annotator: annotators) {
					for (String infoLine: annotator.infoLines()) {
						System.out.println(infoLine);
					}
				}
				System.out.println(line);
			} else {
				VCFVariant variant = new VCFVariant(line);
				
				for (Annotator annotator: annotators) {
					annotator.annotate(variant);
				}
				System.out.println(variant);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		GrandAnnotator annotator = new GrandAnnotator(new BufferedReader(new FileReader(args[0])));

		FileOutputStream fdout = new FileOutputStream(FileDescriptor.out);
		BufferedOutputStream bos = new BufferedOutputStream(fdout, 1024);
		PrintStream ps = new PrintStream(bos, false);
		System.setOut(ps);
		
		BufferedReader input;
		if (args.length > 1) {
			input = new BufferedReader(new FileReader(args[1]));
		} else {
			input = new BufferedReader(new InputStreamReader(System.in));
		}
		annotator.annotateVCFFile(input);
		ps.close();
	}


}
