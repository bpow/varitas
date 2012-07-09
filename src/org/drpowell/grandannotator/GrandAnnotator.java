package org.drpowell.grandannotator;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

public class GrandAnnotator {
	private ArrayList<Annotator> annotators = new ArrayList<Annotator>();
	private static Logger logger = Logger.getLogger("org.drpowell.grandannotator.GrandAnnotator");
	private final File configFile;
	private final File configParent;

	public GrandAnnotator(String configFileName) throws IOException, ScriptException {
		configFile = new File(configFileName);
		configParent = configFile.getParentFile();
		annotators = readConfigFromJS(new BufferedReader(new FileReader(configFile)));
	}

	private ArrayList<Annotator> readConfigFromJS(Reader jsReader) throws ScriptException {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
		engine.eval("importPackage(" + this.getClass().getPackage().getName() + ");");
		engine.put("ga", this);
		engine.eval("addGeneAnnotator = function(id, file) { return ga.addGeneAnnotator(id, file); }");
		engine.eval("addSnpEffSplitter = function() { return ga.addSnpEffAnnotationSplitter(); }");
		engine.eval("addVCFAnnotator = function(file, fields) { return ga.addVCFAnnotator(file, fields); }");
		engine.eval("addTSVAnnotator = function(file, fields) { return ga.addTSVAnnotator(file, fields); }");
		engine.eval(jsReader);
		return annotators;
	}
	
	private String findExistingFile(String f) {
		if (new File(f).exists()) return f;
		File attempt = new File(configParent, f);
		logger.config("Trying " + attempt.getPath());
		if (attempt.exists()) return attempt.getPath();
		attempt = new File(System.getProperty("user.dir"), f);
		logger.config("Trying " + attempt.getPath());
		if (attempt.exists()) return attempt.getPath();
		attempt = new File(System.getProperty("user.home"), f);
		logger.config("Trying " + attempt.getPath());
		if (attempt.exists()) return attempt.getPath();
		
		// give up and return f-- this will probably error later
		return f;
	}
	
	public GeneAnnotator addGeneAnnotator(String id, String fileName) {
		fileName = findExistingFile(fileName);
		try {
			GeneAnnotator annotator = new GeneAnnotator(id, fileName);
			annotators.add(annotator);
			return annotator;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			logger.severe(ioe.toString());
		}
		return null;
	}
	
	public SnpEffAnnotationSplitter addSnpEffAnnotationSplitter() {
		// FIXME -- we really only need one of these
		SnpEffAnnotationSplitter a = new SnpEffAnnotationSplitter();
		annotators.add(0, a);
		return a;
	}

	public TabixVCFAnnotator addVCFAnnotator(String fileName, String fieldString) {
		fileName = findExistingFile(fileName);
		try {
			TabixVCFAnnotator annotator = new TabixVCFAnnotator(new TabixReader(fileName), fieldString);
			annotators.add(annotator);
			return annotator;
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe(e.toString());
		}
		return null;
	}

	public TabixTSVAnnotator addTSVAnnotator(String fileName, String fieldString) {
		fileName = findExistingFile(fileName);
		try {
			TabixTSVAnnotator annotator = new TabixTSVAnnotator(new TabixReader(fileName), fieldString);
			annotators.add(annotator);
			return annotator;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			logger.severe(ioe.toString());
		}
		return null;
	}
	
	public void annotateVCFFile(BufferedReader input) throws IOException {
		VCFParser parser = new VCFParser(input);
		System.out.print(parser.getMetaHeaders());
		// add additional info lines
		for (Annotator annotator: annotators) {
			for (String infoLine: annotator.infoLines()) {
				System.out.println(infoLine);
			}
		}
		System.out.println(parser.getColHeaders());

		for (VCFVariant variant: parser) {
			for (Annotator annotator: annotators) {
				annotator.annotate(variant);
			}
			System.out.println(variant);
		}
		System.out.flush();
	}

	public static void main(String[] args) throws Exception {
		GrandAnnotator annotator = new GrandAnnotator(args[0]);

		FileOutputStream fdout = new FileOutputStream(FileDescriptor.out);
		BufferedOutputStream bos = new BufferedOutputStream(fdout, 1024);
		PrintStream ps = new PrintStream(bos, false);
		System.setOut(ps);
		
		BufferedReader input;
		if (args.length > 1) {
			if (args[1].endsWith(".gz")) {
				input = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(args[1]))));
			} else {
				input = new BufferedReader(new FileReader(args[1]));
			}
		} else {
			input = new BufferedReader(new InputStreamReader(System.in));
		}
		annotator.annotateVCFFile(input);
		ps.close();
	}


}
