package org.drpowell.varitas;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.util.Scanner;
import java.util.logging.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.drpowell.tabix.TabixReader;
import org.drpowell.vcf.VCFHeaders;
import org.drpowell.vcf.VCFIterator;
import org.drpowell.vcf.VCFVariant;
import org.drpowell.vcffilters.JavascriptBooleanVCFFilter;
import org.drpowell.vcffilters.ScriptVCFFilter;

public class JavascriptConfiguredAnnotator implements VCFIterator {
	private VCFIterator variants;
	private static Logger logger = Logger.getLogger("Varitas");
	
	private File configFile;
	private File configParent;

	public JavascriptConfiguredAnnotator(VCFIterator client, String configFileName) {
		this.variants = client;
		try {
			initialize(configFileName);
		} catch (IOException ioe) {
			logger.severe(String.format("Error reading config file ( %s ), will make best effort to proceed: %s", configFileName, ioe));
			ioe.printStackTrace();
		} catch (ScriptException se) {
			logger.severe(String.format("Error running config script ( %s ), will make best effort to proceed: %s", configFileName, se));
			se.printStackTrace();
		}
	}
	
	protected void initialize(String configFileName) throws IOException, ScriptException {
		configFile = new File(configFileName);
		configParent = configFile.getParentFile();
		readConfigFromJS(new BufferedReader(new FileReader(configFile)));
	}
	
	private JavascriptConfiguredAnnotator readConfigFromJS(Reader jsReader) throws ScriptException {
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("JavaScript");
		Scanner s = new Scanner(jsReader);
		String jsString = s.useDelimiter("\\A").next();
		s.close();
		engine.put("__varitas", this);
		engine.eval("with (__varitas) {" + jsString + "}");
		return this;
	}
	
	public GeneAnnotator geneAnnotator(String id, String fileName) {
		URL url = Main.findExistingFile(fileName, configParent);
		try {
			GeneAnnotator annotator = new GeneAnnotator(id, url);
			variants = new AnnotatingIterator(variants, annotator);
			return annotator;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			logger.severe("Unable to read file '" + fileName + "':\n" + ioe.toString());
		}
		return null;
	}
	
	public SnpEffAnnotationSplitter snpEffSplitter() {
		// FIXME -- we really only need one of these, and it should go before any gene annotators
		SnpEffAnnotationSplitter a = new SnpEffAnnotationSplitter();
		variants = new AnnotatingIterator(variants, a);
		return a;
	}

	public TabixVCFAnnotator vcfAnnotator(String fileName, String fieldString) {
		URL url = Main.findExistingFile(fileName, configParent);
		if (url == null) {
			logger.severe("Unable to read file '" + fileName + "'");
			return null;
		}
		try {
			TabixVCFAnnotator annotator = new TabixVCFAnnotator(new TabixReader(url.getFile()), fieldString);
			variants = new AnnotatingIterator(variants, annotator);
			return annotator;
		} catch (IOException e) {
			e.printStackTrace();
			logger.severe("Unable to read file '" + fileName + "':\n" + e.toString());
		}
		return null;
	}

	public TabixTSVAnnotator tsvAnnotator(String fileName, String fieldString) {
		URL url = Main.findExistingFile(fileName, configParent);
		if (url == null) {
			logger.severe("Unable to read file '" + fileName + "'");
			return null;
		}
		try {
			TabixTSVAnnotator annotator = new TabixTSVAnnotator(new TabixReader(url.getFile()), fieldString);
			variants = new AnnotatingIterator(variants, annotator);
			return annotator;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			logger.severe("Unable to read file '" + fileName + "':\n" + ioe.toString());
		}
		return null;
	}
	
	public ScriptVCFFilter applyFilter(String filename) {
		try {
			ScriptVCFFilter filter = new ScriptVCFFilter(variants, new FileReader(filename));
			variants = filter;
			return filter;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			logger.severe("Unable to read file '" + e + "', will ignore this filter:\n" + e.toString());
		}
		return null;
	}
	
	public JavascriptBooleanVCFFilter jsBoolean(String filter) {
		JavascriptBooleanVCFFilter f = new JavascriptBooleanVCFFilter(variants, filter);
		variants = f;
		return f;
	}

	@Override
	public boolean hasNext() {
		return variants.hasNext();
	}

	@Override
	public VCFVariant next() {
		return variants.next();
	}

	@Override
	public void remove() {
		variants.remove();
	}

	@Override
	public VCFHeaders getHeaders() {
		return variants.getHeaders();
	}

}
